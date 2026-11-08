import request from 'supertest';
import { beforeAll, describe, expect, it } from 'vitest';
import { buildTestApp, makeJpeg, testConfig, validProductOutput } from './helpers';
import { GeminiProductFinder, extractSources, parseLooseJson, type ProductGenerateFn } from '../src/services/products/product-finder';
import { buildProductBrief } from '../src/services/products/product-finder.prompt';
import { NO_SEARCH_NOTICE } from '../src/schemas/product-suggestions.schema';
import { silentLogger } from '../src/utils/logger';

const AUTH = { Authorization: 'Bearer token-alice' };
const AUTH_BOB = { Authorization: 'Bearer token-bobby' };

let jpeg: Buffer;
beforeAll(async () => {
  jpeg = await makeJpeg();
});

const grounded = (text: string) => ({
  text,
  candidates: [
    {
      groundingMetadata: {
        groundingChunks: [
          { web: { uri: 'https://www.wardah.co.id/uv-shield', title: 'Wardah UV Shield' } },
          { web: { uri: 'https://www.wardah.co.id/uv-shield', title: 'duplicate' } },
          { web: { uri: 'http://insecure.example/page', title: 'insecure' } },
        ],
      },
    },
  ],
});

async function createAnalysis(app: Parameters<typeof request>[0]) {
  const res = await request(app)
    .post('/api/v1/skin-analysis')
    .set(AUTH)
    .field('storePhoto', 'false')
    .attach('image', jpeg, { filename: 'face.jpg', contentType: 'image/jpeg' });
  expect(res.status).toBe(201);
  return res.body.data.analysisId as string;
}

describe('POST /skin-analysis/:id/products', () => {
  it('returns grounded suggestions with server-built links and caches them', async () => {
    const calls: boolean[] = [];
    const productGenerate: ProductGenerateFn = async ({ config }) => {
      calls.push(Boolean(config.tools));
      return grounded(`Berikut hasilnya:\n${JSON.stringify(validProductOutput)}\nSemoga membantu.`);
    };
    const { app, store } = buildTestApp({ productGenerate });
    const id = await createAnalysis(app);

    const res = await request(app).post(`/api/v1/skin-analysis/${id}/products`).set(AUTH).send({});
    expect(res.status).toBe(200);
    const d = res.body.data;
    expect(d.searchGrounded).toBe(true);
    expect(d.products).toHaveLength(2);
    expect(d.products[0].productType).toBe('sunscreen');
    expect(d.products[0].searchUrl).toBe(
      `https://www.google.com/search?q=${encodeURIComponent('Wardah UV Shield Essential Sunscreen Gel SPF 35 PA+++ BPOM')}`,
    );
    expect(d.products[1].shopUrl).toBe(`https://shopee.co.id/search?keyword=${encodeURIComponent('Somethinc 10% Niacinamide Serum')}`);
    expect(d.sources).toEqual([{ title: 'Wardah UV Shield', url: 'https://www.wardah.co.id/uv-shield' }]);
    expect(d.disclaimer).toContain('BPOM');
    expect(d.disclaimer).not.toContain(NO_SEARCH_NOTICE);

    // Cached: no second Gemini call, and stored on the record.
    const again = await request(app).post(`/api/v1/skin-analysis/${id}/products`).set(AUTH).send({});
    expect(again.status).toBe(200);
    expect(calls).toEqual([true]);
    expect((await store.get('alice', id))?.productSuggestions?.products).toHaveLength(2);

    // refresh=true asks again.
    await request(app).post(`/api/v1/skin-analysis/${id}/products`).set(AUTH).send({ refresh: true });
    expect(calls).toEqual([true, true]);
  });

  it('is owner-only and validates input', async () => {
    const { app } = buildTestApp();
    const id = await createAnalysis(app);
    expect((await request(app).post(`/api/v1/skin-analysis/${id}/products`).set(AUTH_BOB).send({})).status).toBe(404);
    expect((await request(app).post(`/api/v1/skin-analysis/${id}/products`).send({})).status).toBe(401);
    expect((await request(app).post('/api/v1/skin-analysis/not-a-uuid/products').set(AUTH).send({})).status).toBe(400);
    expect((await request(app).post(`/api/v1/skin-analysis/${id}/products`).set(AUTH).send({ refresh: 'yes' })).status).toBe(400);
  });

  it('never returns URLs invented by the model', async () => {
    const withUrls = {
      ...validProductOutput,
      products: validProductOutput.products.map((p) => ({ ...p, url: 'https://evil.example/buy', searchUrl: 'https://evil.example' })),
    };
    const { app } = buildTestApp({ productGenerate: async () => grounded(JSON.stringify(withUrls)) });
    const id = await createAnalysis(app);
    const res = await request(app).post(`/api/v1/skin-analysis/${id}/products`).set(AUTH).send({});
    expect(JSON.stringify(res.body)).not.toContain('evil.example');
  });
});

describe('GeminiProductFinder', () => {
  const cfg = testConfig().gemini;
  const record = {
    analysisId: '00000000-0000-4000-8000-000000000000',
    uid: 'alice',
    createdAt: new Date(),
    status: 'completed' as const,
    model: 'm',
    knowledgeVersion: 'k',
    photoStorageEnabled: false,
    photoStoragePath: null,
    feedback: null,
    analysisVersion: '1.0',
    overallSummary: 'Pori tampak di pipi.',
    imageQuality: { status: 'good' as const, lighting: 'adequate' as const, faceVisibility: 'clear' as const, limitations: [] },
    observations: [
      { category: 'pores' as const, severity: 'moderate' as const, confidence: 0.8, observation: 'x', affectedArea: 'cheeks' as const },
      { category: 'redness' as const, severity: 'unknown' as const, confidence: 0.2, observation: 'y', affectedArea: 'cheeks' as const },
    ],
    skinAppearance: { texture: 'smooth' as const, visibleRedness: 'none' as const, visibleDryness: 'none' as const, pigmentation: 'none' as const, visibleShine: 'mild' as const },
    recommendations: [],
    routine: { morning: [], evening: [] },
    limitations: [],
    professionalConsultationRecommended: false,
    professionalConsultationReason: '',
  };

  it('falls back to model knowledge when Google Search is refused, then skips search during the cooldown', async () => {
    const calls: boolean[] = [];
    let now = 1_000_000;
    const finder = new GeminiProductFinder(
      cfg,
      silentLogger,
      async ({ config }) => {
        calls.push(Boolean(config.tools));
        if (config.tools) throw Object.assign(new Error('quota'), { status: 429 });
        expect(config.responseMimeType).toBe('application/json');
        return { text: JSON.stringify(validProductOutput) };
      },
      () => now,
    );
    const first = await finder.find(record);
    expect(first.searchGrounded).toBe(false);
    expect(first.sources).toEqual([]);
    expect(first.disclaimer).toContain(NO_SEARCH_NOTICE);
    expect(calls).toEqual([true, false]);

    await finder.find(record);
    expect(calls).toEqual([true, false, false]); // search skipped during cooldown

    now += 31 * 60 * 1000;
    await finder.find(record);
    expect(calls).toEqual([true, false, false, true, false]); // retried after cooldown
  });

  it('falls back when the grounded answer is not valid JSON', async () => {
    const finder = new GeminiProductFinder(cfg, silentLogger, async ({ config }) =>
      config.tools ? grounded('Maaf, saya tidak bisa membuat JSON.') : { text: JSON.stringify(validProductOutput) },
    );
    expect((await finder.find(record)).searchGrounded).toBe(false);
  });

  it('maps persistent provider failures after trying search, then knowledge-only twice', async () => {
    let calls = 0;
    const finder = new GeminiProductFinder(cfg, silentLogger, async () => {
      calls++;
      throw Object.assign(new Error('boom'), { status: 500 });
    }, Date.now, 0);
    await expect(finder.find(record)).rejects.toMatchObject({ code: 'AI_PROVIDER_ERROR' });
    expect(calls).toBe(3);
  });

  it('retries the knowledge-only call once on a transient 503', async () => {
    let calls = 0;
    const finder = new GeminiProductFinder(cfg, silentLogger, async ({ config }) => {
      calls++;
      if (config.tools) throw Object.assign(new Error('quota'), { status: 429 });
      if (calls === 2) throw Object.assign(new Error('busy'), { status: 503 });
      return { text: JSON.stringify(validProductOutput) };
    }, Date.now, 0);
    expect((await finder.find(record)).products).toHaveLength(2);
    expect(calls).toBe(3);
  });

  it('does not fall back on a timeout', async () => {
    const finder = new GeminiProductFinder(cfg, silentLogger, async () => {
      throw Object.assign(new Error('aborted'), { name: 'AbortError' });
    }, Date.now, 0);
    await expect(finder.find(record)).rejects.toMatchObject({ code: 'AI_TIMEOUT' });
  });

  it('rejects answers that still fail validation', async () => {
    const finder = new GeminiProductFinder(cfg, silentLogger, async () => ({ text: JSON.stringify({ summary: 'x', products: [] }) }));
    await expect(finder.find(record)).rejects.toMatchObject({ code: 'AI_INVALID_RESPONSE' });
  });

  it('brief carries skin findings but no user identity, and drops unknown/none observations', () => {
    const brief = buildProductBrief(record);
    expect(brief).toContain('pores');
    expect(brief).not.toContain('redness');
    expect(brief).not.toContain('alice');
    expect(brief).not.toContain(record.analysisId);
  });

  it('parses JSON wrapped in prose and extracts https sources only', () => {
    expect(parseLooseJson('hasil: {"a":1} selesai')).toEqual({ ok: true, value: { a: 1 } });
    expect(parseLooseJson('tidak ada json').ok).toBe(false);
    expect(extractSources(grounded('{}'))).toHaveLength(1);
  });
});
