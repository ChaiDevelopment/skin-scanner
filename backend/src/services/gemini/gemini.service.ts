import { GoogleGenAI, ApiError, type GenerateContentConfig } from '@google/genai';
import type { Logger } from 'pino';
import type { AppConfig } from '../../config/env';
import { AppError, Errors } from '../../utils/errors';
import {
  GeminiSkinAnalysisSchema,
  geminiResponseJsonSchema,
  type GeminiSkinAnalysis,
} from '../../schemas/skin-analysis.schema';
import {
  RETRY_CORRECTION_PROMPT,
  SKIN_ANALYSIS_SYSTEM_PROMPT,
  SKIN_ANALYSIS_USER_PROMPT,
} from './skin-analysis.prompt';

/** Anything that can turn a face photo into a validated analysis. Lets tests inject a mock. */
export interface SkinAnalyzer {
  readonly model: string;
  readonly configured: boolean;
  analyze(image: Buffer, mimeType: string): Promise<GeminiSkinAnalysis>;
}

/** Minimal surface of the SDK we depend on — makes the service unit-testable without network. */
export interface GenerateContentFn {
  (args: {
    model: string;
    contents: unknown;
    config: GenerateContentConfig;
  }): Promise<{ text?: string | undefined }>;
}

export class GeminiSkinAnalyzer implements SkinAnalyzer {
  readonly model: string;
  readonly configured: boolean;
  private readonly generate: GenerateContentFn | null;

  constructor(
    private readonly cfg: AppConfig['gemini'],
    private readonly logger: Logger,
    generate?: GenerateContentFn,
  ) {
    this.model = cfg.model;
    if (generate) {
      this.generate = generate;
    } else if (cfg.apiKey) {
      const client = new GoogleGenAI({ apiKey: cfg.apiKey, httpOptions: { timeout: cfg.timeoutMs } });
      this.generate = (args) => client.models.generateContent(args as Parameters<typeof client.models.generateContent>[0]);
    } else {
      this.generate = null;
    }
    this.configured = this.generate !== null;
  }

  async analyze(image: Buffer, mimeType: string): Promise<GeminiSkinAnalysis> {
    if (!this.generate) throw Errors.notConfigured('Gemini API key');

    const imagePart = { inlineData: { mimeType, data: image.toString('base64') } };
    const baseContents = [{ role: 'user', parts: [imagePart, { text: SKIN_ANALYSIS_USER_PROMPT }] }];

    // Attempt 1: normal request. Attempt 2: only if output was unparseable/invalid,
    // or a transient 5xx happened. Quota (429) and timeouts are not retried.
    let lastValidationError: unknown;
    for (let attempt = 1; attempt <= 2; attempt++) {
      const contents =
        attempt === 1 || lastValidationError === undefined
          ? baseContents
          : [{ role: 'user', parts: [imagePart, { text: `${SKIN_ANALYSIS_USER_PROMPT}\n\n${RETRY_CORRECTION_PROMPT}` }] }];

      let text: string | undefined;
      try {
        text = await this.callWithTimeout(contents);
      } catch (err) {
        const mapped = this.mapProviderError(err);
        const status = providerStatus(err);
        const transient = attempt === 1 && isTransient(err, status);
        this.logger.warn({ attempt, code: mapped.code, providerStatus: providerStatus(err) }, 'Gemini call failed');
        if (transient) {
          await delay(800);
          continue;
        }
        throw mapped;
      }

      const parsed = parseModelJson(text);
      if (parsed.ok) {
        const validated = GeminiSkinAnalysisSchema.safeParse(parsed.value);
        if (validated.success) return validated.data;
        lastValidationError = validated.error;
        this.logger.warn(
          { attempt, issues: validated.error.issues.slice(0, 5).map((i) => `${i.path.join('.')}: ${i.message}`) },
          'Gemini output failed schema validation',
        );
      } else {
        lastValidationError = parsed.error;
        this.logger.warn({ attempt, reason: parsed.error }, 'Gemini output was not valid JSON');
      }
    }
    throw Errors.aiInvalidResponse();
  }

  private async callWithTimeout(contents: unknown): Promise<string | undefined> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.cfg.timeoutMs);
    try {
      const config: GenerateContentConfig = {
        systemInstruction: SKIN_ANALYSIS_SYSTEM_PROMPT,
        responseMimeType: 'application/json',
        responseJsonSchema: geminiResponseJsonSchema,
        maxOutputTokens: this.cfg.maxOutputTokens,
        temperature: 0.2,
        abortSignal: controller.signal,
      };
      if (this.cfg.thinkingBudget !== undefined) {
        config.thinkingConfig = { thinkingBudget: this.cfg.thinkingBudget };
      }
      const res = await this.generate!({ model: this.model, contents, config });
      return res.text;
    } catch (err) {
      if (controller.signal.aborted) throw new TimeoutMarker();
      throw err;
    } finally {
      clearTimeout(timer);
    }
  }

  private mapProviderError(err: unknown): AppError {
    if (err instanceof AppError) return err;
    if (err instanceof TimeoutMarker) return Errors.aiTimeout();
    const name = (err as { name?: string } | null)?.name;
    if (name === 'AbortError' || name === 'TimeoutError') return Errors.aiTimeout();
    const status = providerStatus(err);
    if (status === 429) return Errors.aiQuota();
    if (status === 401 || status === 403) {
      this.logger.error({ providerStatus: status }, 'Gemini rejected credentials — check GEMINI_API_KEY');
      return Errors.unavailable('Layanan AI belum dapat diakses (konfigurasi kunci API).');
    }
    if (status === 400 || status === 404) {
      this.logger.error({ providerStatus: status }, 'Gemini rejected the request — check GEMINI_MODEL');
      return Errors.aiProvider();
    }
    if (status === 503) return Errors.unavailable('Layanan AI sedang sibuk. Silakan coba lagi sebentar lagi.');
    return Errors.aiProvider();
  }
}

/** Only server-side/provider hiccups are worth one retry; 4xx, quota and timeouts are not. */
function isTransient(err: unknown, status: number | undefined): boolean {
  if (err instanceof AppError || err instanceof TimeoutMarker) return false;
  if (status === undefined) {
    const name = (err as { name?: string } | null)?.name;
    return name !== 'AbortError' && name !== 'TimeoutError'; // network error
  }
  return status >= 500;
}

class TimeoutMarker extends Error {
  constructor() {
    super('Gemini request timed out');
  }
}

function providerStatus(err: unknown): number | undefined {
  if (err instanceof ApiError) return err.status;
  const s = (err as { status?: unknown } | null)?.status;
  return typeof s === 'number' ? s : undefined;
}

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Parses model output, tolerating an accidental ```json fence. */
export function parseModelJson(text: string | undefined): { ok: true; value: unknown } | { ok: false; error: string } {
  if (!text || text.trim() === '') return { ok: false, error: 'empty response' };
  let t = text.trim();
  const fence = /^```(?:json)?\s*([\s\S]*?)\s*```$/i.exec(t);
  if (fence?.[1]) t = fence[1];
  try {
    return { ok: true, value: JSON.parse(t) };
  } catch {
    return { ok: false, error: 'JSON.parse failed' };
  }
}
