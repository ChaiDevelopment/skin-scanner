import { describe, expect, it } from 'vitest';
import { GeminiSkinAnalyzer, parseModelJson, type GenerateContentFn } from '../src/services/gemini/gemini.service';
import { GeminiSkinAnalysisSchema, normalizeAnalysis } from '../src/schemas/skin-analysis.schema';
import { SKIN_ANALYSIS_SYSTEM_PROMPT } from '../src/services/gemini/skin-analysis.prompt';
import { AppError } from '../src/utils/errors';
import { silentLogger } from '../src/utils/logger';
import { testConfig, validGeminiOutput } from './helpers';

const analyzer = (generate: GenerateContentFn) => new GeminiSkinAnalyzer(testConfig().gemini, silentLogger, generate);
const img = Buffer.from([0xff, 0xd8, 0xff]);

describe('GeminiSkinAnalyzer', () => {
  it('sends system prompt, JSON schema and image', async () => {
    let captured: Parameters<GenerateContentFn>[0] | undefined;
    await analyzer(async (args) => {
      captured = args;
      return { text: JSON.stringify(validGeminiOutput) };
    }).analyze(img, 'image/jpeg');
    expect(captured!.model).toBe('gemini-test');
    expect(captured!.config.responseMimeType).toBe('application/json');
    expect(captured!.config.responseJsonSchema).toBeTruthy();
    expect(String(captured!.config.systemInstruction)).toContain('DERMATOLOGY REFERENCE');
  });

  it('retries once after invalid JSON and succeeds', async () => {
    let calls = 0;
    const result = await analyzer(async () => {
      calls++;
      return { text: calls === 1 ? '{"status":' : JSON.stringify(validGeminiOutput) };
    }).analyze(img, 'image/jpeg');
    expect(calls).toBe(2);
    expect(result.status).toBe('success');
  });

  it('rejects schema-violating output (bad enum)', async () => {
    const bad = { ...validGeminiOutput, skin_appearance: { ...validGeminiOutput.skin_appearance, texture: 'perfect' } };
    await expect(analyzer(async () => ({ text: JSON.stringify(bad) })).analyze(img, 'image/jpeg')).rejects.toMatchObject({
      code: 'AI_INVALID_RESPONSE',
    });
  });

  it('truncates over-long text and lists instead of failing', () => {
    const verbose = {
      ...validGeminiOutput,
      overall_summary: 'x'.repeat(5000),
      limitations: Array.from({ length: 30 }, (_, i) => `batas ${i}`),
      skin_observations: Array.from({ length: 25 }, () => validGeminiOutput.skin_observations[0]),
    };
    const r = GeminiSkinAnalysisSchema.parse(verbose);
    expect(r.overall_summary.length).toBe(1200);
    expect(r.overall_summary.endsWith('…')).toBe(true);
    expect(r.limitations).toHaveLength(10);
    expect(r.skin_observations).toHaveLength(16);
  });

  it('rejects an empty observation text', () => {
    const bad = { ...validGeminiOutput, skin_observations: [{ ...validGeminiOutput.skin_observations[0], observation: '  ' }] };
    expect(GeminiSkinAnalysisSchema.safeParse(bad).success).toBe(false);
  });

  it('rejects out-of-range confidence', () => {
    const bad = {
      ...validGeminiOutput,
      skin_observations: [{ ...validGeminiOutput.skin_observations[0], confidence: 1.7 }],
    };
    expect(GeminiSkinAnalysisSchema.safeParse(bad).success).toBe(false);
  });

  it('retries a transient 5xx once', async () => {
    let calls = 0;
    const res = await analyzer(async () => {
      calls++;
      if (calls === 1) throw Object.assign(new Error('unavailable'), { status: 503 });
      return { text: JSON.stringify(validGeminiOutput) };
    }).analyze(img, 'image/jpeg');
    expect(res.status).toBe('success');
    expect(calls).toBe(2);
  });

  it('does not retry a 404 (bad model name)', async () => {
    let calls = 0;
    await expect(
      analyzer(async () => {
        calls++;
        throw Object.assign(new Error('model not found'), { status: 404 });
      }).analyze(img, 'image/jpeg'),
    ).rejects.toMatchObject({ code: 'AI_PROVIDER_ERROR' });
    expect(calls).toBe(1);
  });

  it('maps a persistent provider 503 to SERVICE_UNAVAILABLE', async () => {
    await expect(
      analyzer(async () => {
        throw Object.assign(new Error('overloaded'), { status: 503 });
      }).analyze(img, 'image/jpeg'),
    ).rejects.toMatchObject({ status: 503, code: 'SERVICE_UNAVAILABLE' });
  });

  it('reports NOT_CONFIGURED without an API key', async () => {
    const a = new GeminiSkinAnalyzer({ ...testConfig().gemini, apiKey: undefined }, silentLogger);
    expect(a.configured).toBe(false);
    await expect(a.analyze(img, 'image/jpeg')).rejects.toBeInstanceOf(AppError);
  });
});

describe('parseModelJson', () => {
  it('handles fenced JSON', () => {
    expect(parseModelJson('```json\n{"a":1}\n```')).toEqual({ ok: true, value: { a: 1 } });
  });
  it('rejects empty', () => {
    expect(parseModelJson('').ok).toBe(false);
  });
});

describe('normalizeAnalysis', () => {
  it('converts to camelCase, strips unknown keys, adds disclaimer', () => {
    const n = normalizeAnalysis(GeminiSkinAnalysisSchema.parse(validGeminiOutput));
    expect(n.skinAppearance.visibleRedness).toBe('mild');
    expect(n.limitations[0]).toContain('bukan diagnosis medis');
    expect(n).not.toHaveProperty('unexpected_field');
    expect(n).not.toHaveProperty('score');
  });
});

describe('system prompt safety', () => {
  it('contains the non-diagnostic rules', () => {
    for (const phrase of ['must not diagnose', 'Never invent findings', 'insufficient_image_quality', 'prescription']) {
      expect(SKIN_ANALYSIS_SYSTEM_PROMPT).toContain(phrase);
    }
  });
});
