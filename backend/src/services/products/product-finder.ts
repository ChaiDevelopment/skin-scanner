import { GoogleGenAI, ApiError, type GenerateContentConfig } from '@google/genai';
import type { Logger } from 'pino';
import type { AppConfig } from '../../config/env';
import { AppError, Errors } from '../../utils/errors';
import {
  GeminiProductSuggestionsSchema,
  geminiProductJsonSchema,
  normalizeProducts,
  type ProductSource,
  type ProductSuggestions,
} from '../../schemas/product-suggestions.schema';
import { parseModelJson } from '../gemini/gemini.service';
import type { StoredAnalysis } from '../analysis/types';
import { buildProductBrief, PRODUCT_FINDER_SYSTEM_PROMPT } from './product-finder.prompt';

export interface ProductFinder {
  readonly configured: boolean;
  find(analysis: StoredAnalysis): Promise<ProductSuggestions>;
}

/** The part of a Gemini response we read. Lets tests inject a fake without network. */
export interface ProductGenerateResult {
  text?: string | undefined;
  candidates?: Array<{
    groundingMetadata?: { groundingChunks?: Array<{ web?: { uri?: string; title?: string } }> };
  }>;
}
export type ProductGenerateFn = (args: {
  model: string;
  contents: unknown;
  config: GenerateContentConfig;
}) => Promise<ProductGenerateResult>;

/** After Google Search is refused (e.g. free-tier quota 0), skip it for a while instead of failing every call. */
const SEARCH_COOLDOWN_MS = 30 * 60 * 1000;
const MAX_SOURCES = 8;

export class GeminiProductFinder implements ProductFinder {
  readonly configured: boolean;
  private readonly generate: ProductGenerateFn | null;
  private searchDisabledUntil = 0;

  constructor(
    private readonly cfg: AppConfig['gemini'],
    private readonly logger: Logger,
    generate?: ProductGenerateFn,
    private readonly now: () => number = Date.now,
    private readonly retryDelayMs = 1500,
  ) {
    if (generate) {
      this.generate = generate;
    } else if (cfg.apiKey) {
      const client = new GoogleGenAI({ apiKey: cfg.apiKey, httpOptions: { timeout: cfg.timeoutMs } });
      this.generate = (args) =>
        client.models.generateContent(args as Parameters<typeof client.models.generateContent>[0]) as Promise<ProductGenerateResult>;
    } else {
      this.generate = null;
    }
    this.configured = this.generate !== null;
  }

  async find(analysis: StoredAnalysis): Promise<ProductSuggestions> {
    if (!this.generate) throw Errors.notConfigured('Gemini API key');
    const brief = buildProductBrief(analysis);

    if (this.now() >= this.searchDisabledUntil) {
      try {
        const res = await this.call(brief, true);
        const parsed = this.validate(res.text);
        if (parsed) return normalizeProducts(parsed, { sources: extractSources(res), searchGrounded: true, model: this.cfg.model });
        this.logger.warn('Search-grounded product answer was not valid JSON; falling back to model knowledge');
      } catch (err) {
        if (isSearchUnavailable(err)) {
          this.searchDisabledUntil = this.now() + SEARCH_COOLDOWN_MS;
          this.logger.warn({ providerStatus: providerStatus(err) }, 'Google Search grounding unavailable; using model knowledge only');
        } else if (isTransient(err)) {
          this.logger.warn({ providerStatus: providerStatus(err) }, 'Search-grounded call failed transiently; using model knowledge only');
        } else {
          throw this.mapError(err);
        }
      }
    }

    // Knowledge-only answer. One retry for invalid output or a transient provider 5xx/network error.
    for (let attempt = 1; attempt <= 2; attempt++) {
      try {
        const res = await this.call(brief, false);
        const parsed = this.validate(res.text);
        if (parsed) return normalizeProducts(parsed, { sources: [], searchGrounded: false, model: this.cfg.model });
      } catch (err) {
        if (attempt === 1 && isTransient(err)) {
          await new Promise((r) => setTimeout(r, this.retryDelayMs));
          continue;
        }
        throw this.mapError(err);
      }
    }
    throw Errors.aiInvalidResponse();
  }

  private validate(text: string | undefined) {
    const json = parseLooseJson(text);
    if (!json.ok) return null;
    const result = GeminiProductSuggestionsSchema.safeParse(json.value);
    if (!result.success) {
      this.logger.warn(
        { issues: result.error.issues.slice(0, 5).map((i) => `${i.path.join('.')}: ${i.message}`) },
        'Product answer failed schema validation',
      );
      return null;
    }
    return result.data;
  }

  private async call(brief: string, withSearch: boolean): Promise<ProductGenerateResult> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.cfg.timeoutMs);
    try {
      const config: GenerateContentConfig = {
        systemInstruction: PRODUCT_FINDER_SYSTEM_PROMPT,
        maxOutputTokens: this.cfg.maxOutputTokens,
        temperature: 0.3,
        abortSignal: controller.signal,
      };
      if (withSearch) {
        config.tools = [{ googleSearch: {} }];
      } else {
        config.responseMimeType = 'application/json';
        config.responseJsonSchema = geminiProductJsonSchema;
      }
      if (this.cfg.thinkingBudget !== undefined) config.thinkingConfig = { thinkingBudget: this.cfg.thinkingBudget };
      return await this.generate!({
        model: this.cfg.model,
        contents: [{ role: 'user', parts: [{ text: brief }] }],
        config,
      });
    } catch (err) {
      if (controller.signal.aborted) throw Errors.aiTimeout();
      throw err;
    } finally {
      clearTimeout(timer);
    }
  }

  private mapError(err: unknown): AppError {
    if (err instanceof AppError) return err;
    const name = (err as { name?: string } | null)?.name;
    if (name === 'AbortError' || name === 'TimeoutError') return Errors.aiTimeout();
    const status = providerStatus(err);
    this.logger.warn({ providerStatus: status }, 'Product finder Gemini call failed');
    if (status === 429) return Errors.aiQuota();
    if (status === 503) return Errors.unavailable('Layanan AI sedang sibuk. Silakan coba lagi sebentar lagi.');
    return Errors.aiProvider();
  }
}

function providerStatus(err: unknown): number | undefined {
  if (err instanceof ApiError) return err.status;
  const s = (err as { status?: unknown } | null)?.status;
  return typeof s === 'number' ? s : undefined;
}

/** Provider 5xx or a network error (no status) — worth one retry. Timeouts and 4xx are not. */
function isTransient(err: unknown): boolean {
  if (err instanceof AppError) return false;
  const status = providerStatus(err);
  if (status === undefined) {
    const name = (err as { name?: string } | null)?.name;
    return name !== 'AbortError' && name !== 'TimeoutError';
  }
  return status >= 500;
}

/**
 * Search refused for this key/model: 429 (no grounding quota on the free tier) or 400/403
 * (tool not supported/allowed). Anything else (timeouts, 5xx) is a real failure.
 */
function isSearchUnavailable(err: unknown): boolean {
  if (err instanceof AppError) return false;
  const status = providerStatus(err);
  return status === 429 || status === 400 || status === 403;
}

/** Like parseModelJson, but also accepts prose around the object (search-grounded answers cannot use JSON mode). */
export function parseLooseJson(text: string | undefined): { ok: true; value: unknown } | { ok: false } {
  const direct = parseModelJson(text);
  if (direct.ok) return direct;
  if (!text) return { ok: false };
  const start = text.indexOf('{');
  const end = text.lastIndexOf('}');
  if (start < 0 || end <= start) return { ok: false };
  try {
    return { ok: true, value: JSON.parse(text.slice(start, end + 1)) };
  } catch {
    return { ok: false };
  }
}

/** Web sources from Google Search grounding, https only, deduplicated. */
export function extractSources(res: ProductGenerateResult): ProductSource[] {
  const chunks = res.candidates?.[0]?.groundingMetadata?.groundingChunks ?? [];
  const out: ProductSource[] = [];
  const seen = new Set<string>();
  for (const c of chunks) {
    const url = c.web?.uri;
    if (!url || !url.startsWith('https://') || seen.has(url)) continue;
    seen.add(url);
    out.push({ title: (c.web?.title ?? '').trim().slice(0, 120) || new URL(url).hostname, url });
    if (out.length >= MAX_SOURCES) break;
  }
  return out;
}
