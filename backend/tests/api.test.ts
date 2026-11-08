import request from 'supertest';
import { beforeAll, describe, expect, it } from 'vitest';
import { buildTestApp, insufficientGeminiOutput, makeJpeg, textResponse } from './helpers';

const AUTH = { Authorization: 'Bearer token-alice' };
const AUTH_BOB = { Authorization: 'Bearer token-bobby' };

let jpeg: Buffer;
beforeAll(async () => {
  jpeg = await makeJpeg();
});

const analyze = (app: Parameters<typeof request>[0], headers = AUTH, storePhoto = false) =>
  request(app)
    .post('/api/v1/skin-analysis')
    .set(headers)
    .field('storePhoto', String(storePhoto))
    .attach('image', jpeg, { filename: 'face.jpg', contentType: 'image/jpeg' });

describe('system endpoints', () => {
  it('GET /health returns ok with request id', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/health');
    expect(res.status).toBe(200);
    expect(res.body.data.status).toBe('ok');
    expect(res.headers['x-request-id']).toBeTruthy();
  });

  it('GET /config exposes no secrets', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/config');
    expect(res.status).toBe(200);
    expect(res.body.data.features.aiAnalysis).toBe(true);
    expect(JSON.stringify(res.body)).not.toContain('test-key-not-real');
  });

  it('unknown route returns 404 envelope', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/nope');
    expect(res.status).toBe(404);
    expect(res.body.success).toBe(false);
    expect(res.body.error.code).toBe('NOT_FOUND');
  });

  it('sets security headers', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/health');
    expect(res.headers['x-content-type-options']).toBe('nosniff');
    expect(res.headers['x-powered-by']).toBeUndefined();
  });
});

describe('authentication middleware', () => {
  it('rejects missing token with 401', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/skin-analysis/history');
    expect(res.status).toBe(401);
    expect(res.body.error.code).toBe('UNAUTHENTICATED');
  });

  it('rejects invalid token with 401', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/skin-analysis/history').set('Authorization', 'Bearer not-a-valid-token');
    expect(res.status).toBe(401);
  });

  it('returns 503 when auth is not configured', async () => {
    const { app } = buildTestApp({ verifier: null });
    const res = await request(app).get('/api/v1/skin-analysis/history').set(AUTH);
    expect(res.status).toBe(503);
    expect(res.body.error.code).toBe('NOT_CONFIGURED');
  });

  it('ignores a uid supplied by the client', async () => {
    const { app, store } = buildTestApp();
    const res = await analyze(app).field('uid', 'mallory');
    expect(res.status).toBe(201);
    const saved = await store.get('alice', res.body.data.analysisId);
    expect(saved?.uid).toBe('alice');
    expect(await store.get('mallory', res.body.data.analysisId)).toBeNull();
  });
});

describe('POST /skin-analysis — upload validation', () => {
  it('rejects request without image (400)', async () => {
    const { app } = buildTestApp();
    const res = await request(app).post('/api/v1/skin-analysis').set(AUTH).field('storePhoto', 'false');
    expect(res.status).toBe(400);
  });

  it('rejects non-multipart body (415)', async () => {
    const { app } = buildTestApp();
    const res = await request(app).post('/api/v1/skin-analysis').set(AUTH).send({ image: 'x' });
    expect(res.status).toBe(415);
  });

  it('rejects unsupported MIME (415)', async () => {
    const { app } = buildTestApp();
    const res = await request(app)
      .post('/api/v1/skin-analysis')
      .set(AUTH)
      .attach('image', Buffer.from('hello'), { filename: 'a.txt', contentType: 'text/plain' });
    expect(res.status).toBe(415);
    expect(res.body.error.code).toBe('UNSUPPORTED_MEDIA_TYPE');
  });

  it('rejects bytes that are not really an image (415)', async () => {
    const { app } = buildTestApp();
    const res = await request(app)
      .post('/api/v1/skin-analysis')
      .set(AUTH)
      .attach('image', Buffer.alloc(5000, 7), { filename: 'fake.jpg', contentType: 'image/jpeg' });
    expect(res.status).toBe(415);
  });

  it('rejects too-small resolution (400)', async () => {
    const { app } = buildTestApp();
    const tiny = await makeJpeg(100, 100);
    const res = await request(app)
      .post('/api/v1/skin-analysis')
      .set(AUTH)
      .attach('image', tiny, { filename: 'tiny.jpg', contentType: 'image/jpeg' });
    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('INVALID_IMAGE');
  });

  it('rejects oversized upload (413)', async () => {
    const { app } = buildTestApp({ env: { MAX_IMAGE_SIZE_MB: '1' } });
    const big = Buffer.concat([jpeg, Buffer.alloc(1024 * 1024 + 10, 1)]);
    const res = await request(app)
      .post('/api/v1/skin-analysis')
      .set(AUTH)
      .attach('image', big, { filename: 'big.jpg', contentType: 'image/jpeg' });
    expect(res.status).toBe(413);
    expect(res.body.error.code).toBe('IMAGE_TOO_LARGE');
  });
});

describe('POST /skin-analysis — Gemini handling', () => {
  it('saves and returns a validated analysis (201)', async () => {
    let sentMime = '';
    const { app, store } = buildTestApp({
      generate: async (args) => {
        const parts = (args.contents as Array<{ parts: Array<{ inlineData?: { mimeType: string } }> }>)[0]!.parts;
        sentMime = parts[0]!.inlineData!.mimeType;
        return { text: JSON.stringify((await import('./helpers')).validGeminiOutput) };
      },
    });
    const res = await analyze(app);
    expect(res.status).toBe(201);
    expect(sentMime).toBe('image/jpeg');
    const a = res.body.data.analysis;
    expect(a.observations[0].affectedArea).toBe('cheeks');
    expect(a.observations[0].confidence).toBe(0.85); // rounded to 0.05
    expect(a.limitations[0]).toContain('bukan diagnosis');
    expect(a).not.toHaveProperty('unexpected_field');
    expect(a).not.toHaveProperty('uid');
    expect(await store.get('alice', res.body.data.analysisId)).not.toBeNull();
  });

  it('returns 422 for insufficient image quality and does not save', async () => {
    const { app, store } = buildTestApp({ generate: textResponse(insufficientGeminiOutput) });
    const res = await analyze(app);
    expect(res.status).toBe(422);
    expect(res.body.error.code).toBe('INSUFFICIENT_IMAGE_QUALITY');
    expect(res.body.error.details.imageQuality.lighting).toBe('too_dark');
    expect((await store.list('alice', { limit: 10 })).items).toHaveLength(0);
  });

  it('returns 502 when Gemini returns invalid JSON twice', async () => {
    const { app } = buildTestApp({ generate: async () => ({ text: 'not json {' }) });
    const res = await analyze(app);
    expect(res.status).toBe(502);
    expect(res.body.error.code).toBe('AI_INVALID_RESPONSE');
  });

  it('returns 504 on Gemini timeout', async () => {
    const { app } = buildTestApp({
      env: { GEMINI_TIMEOUT_MS: '5000' },
      generate: (args) =>
        new Promise((_, reject) => {
          args.config.abortSignal!.addEventListener('abort', () => reject(Object.assign(new Error('aborted'), { name: 'AbortError' })));
        }),
    });
    const res = await analyze(app);
    expect(res.status).toBe(504);
    expect(res.body.error.code).toBe('AI_TIMEOUT');
  }, 15000);

  it('maps Gemini 429 to 503 AI_QUOTA_EXCEEDED', async () => {
    const { app } = buildTestApp({ generate: async () => Promise.reject(Object.assign(new Error('quota'), { status: 429 })) });
    const res = await analyze(app);
    expect(res.status).toBe(503);
    expect(res.body.error.code).toBe('AI_QUOTA_EXCEEDED');
  });

  it('never leaks provider error details', async () => {
    const { app } = buildTestApp({
      generate: async () => Promise.reject(Object.assign(new Error('secret internal detail key=abc'), { status: 500 })),
    });
    const res = await analyze(app);
    expect(res.status).toBe(502);
    expect(JSON.stringify(res.body)).not.toContain('secret internal detail');
    expect(JSON.stringify(res.body)).not.toContain('stack');
  });

  it('stores photo only when storePhoto=true', async () => {
    const { app, photos } = buildTestApp();
    const r1 = await analyze(app, AUTH, false);
    expect(r1.body.data.analysis.photoStorageEnabled).toBe(false);
    expect(photos.files.size).toBe(0);
    const r2 = await analyze(app, AUTH, true);
    expect(r2.body.data.analysis.photoStorageEnabled).toBe(true);
    expect(photos.files.has(`users/alice/scans/${r2.body.data.analysisId}.jpg`)).toBe(true);
  });

  it('still saves the analysis (without photo) when the photo upload fails', async () => {
    const { app, photos, store } = buildTestApp();
    photos.save = async () => {
      throw new Error('The specified bucket does not exist.');
    };
    const res = await analyze(app, AUTH, true);
    expect(res.status).toBe(201);
    expect(res.body.data.analysis.photoStorageEnabled).toBe(false);
    expect(res.body.data.analysis.photoUrl).toBeNull();
    expect((await store.list('alice', { limit: 10 })).items).toHaveLength(1);
  });

  it('returns signed photo URLs in the create response and history items', async () => {
    const { app, photos } = buildTestApp();
    photos.signedReadUrl = async (path: string) => `https://signed.example/${path}`;
    const withPhoto = await analyze(app, AUTH, true);
    const id = withPhoto.body.data.analysisId;
    expect(withPhoto.body.data.analysis.photoUrl).toBe(`https://signed.example/users/alice/scans/${id}.jpg`);
    await analyze(app, AUTH, false);
    const hist = await request(app).get('/api/v1/skin-analysis/history').set(AUTH);
    const urls = hist.body.data.items.map((i: { analysisId: string; photoUrl: string | null }) => [i.analysisId === id, i.photoUrl]);
    expect(urls).toContainEqual([true, `https://signed.example/users/alice/scans/${id}.jpg`]);
    expect(urls).toContainEqual([false, null]);
  });

  it('rate limits analysis per user (429)', async () => {
    const { app } = buildTestApp({ env: { RATE_LIMIT_MAX_REQUESTS: '2' } });
    expect((await analyze(app)).status).toBe(201);
    expect((await analyze(app)).status).toBe(201);
    const third = await analyze(app);
    expect(third.status).toBe(429);
    expect(third.body.error.code).toBe('RATE_LIMITED');
    // other users are not affected
    expect((await analyze(app, AUTH_BOB)).status).toBe(201);
  });
});

describe('history, ownership, deletion, feedback', () => {
  it('paginates history newest first', async () => {
    const { app } = buildTestApp();
    const ids: string[] = [];
    for (let i = 0; i < 3; i++) {
      ids.push((await analyze(app)).body.data.analysisId);
      await new Promise((r) => setTimeout(r, 5));
    }
    const p1 = await request(app).get('/api/v1/skin-analysis/history?limit=2').set(AUTH);
    expect(p1.status).toBe(200);
    expect(p1.body.data.items.map((i: { analysisId: string }) => i.analysisId)).toEqual([ids[2], ids[1]]);
    expect(p1.body.data.nextCursor).toBe(ids[1]);
    const p2 = await request(app).get(`/api/v1/skin-analysis/history?limit=2&cursor=${p1.body.data.nextCursor}`).set(AUTH);
    expect(p2.body.data.items.map((i: { analysisId: string }) => i.analysisId)).toEqual([ids[0]]);
    expect(p2.body.data.nextCursor).toBeNull();
  });

  it('rejects invalid pagination params (400)', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/skin-analysis/history?limit=500').set(AUTH);
    expect(res.status).toBe(400);
  });

  it('prevents other users from reading or deleting an analysis', async () => {
    const { app, store } = buildTestApp();
    const id = (await analyze(app)).body.data.analysisId;
    expect((await request(app).get(`/api/v1/skin-analysis/${id}`).set(AUTH_BOB)).status).toBe(404);
    expect((await request(app).delete(`/api/v1/skin-analysis/${id}`).set(AUTH_BOB)).status).toBe(404);
    expect(await store.get('alice', id)).not.toBeNull();
    const own = await request(app).get(`/api/v1/skin-analysis/${id}`).set(AUTH);
    expect(own.status).toBe(200);
    expect(own.body.data.analysisId).toBe(id);
  });

  it('rejects malformed analysis ids (400)', async () => {
    const { app } = buildTestApp();
    const res = await request(app).get('/api/v1/skin-analysis/..%2F..%2Fetc').set(AUTH);
    expect(res.status).toBe(400);
  });

  it('deletes an analysis and its stored photo', async () => {
    const { app, photos, store } = buildTestApp();
    const id = (await analyze(app, AUTH, true)).body.data.analysisId;
    expect(photos.files.size).toBe(1);
    const del = await request(app).delete(`/api/v1/skin-analysis/${id}`).set(AUTH);
    expect(del.status).toBe(200);
    expect(photos.files.size).toBe(0);
    expect(await store.get('alice', id)).toBeNull();
    expect((await request(app).get(`/api/v1/skin-analysis/${id}`).set(AUTH)).status).toBe(404);
  });

  it('deletes only the photo via DELETE /:id/photo', async () => {
    const { app, photos, store } = buildTestApp();
    const id = (await analyze(app, AUTH, true)).body.data.analysisId;
    const res = await request(app).delete(`/api/v1/skin-analysis/${id}/photo`).set(AUTH);
    expect(res.status).toBe(200);
    expect(photos.files.size).toBe(0);
    expect((await store.get('alice', id))?.photoStorageEnabled).toBe(false);
  });

  it('saves feedback and validates body', async () => {
    const { app, store } = buildTestApp();
    const id = (await analyze(app)).body.data.analysisId;
    const bad = await request(app).post(`/api/v1/skin-analysis/${id}/feedback`).set(AUTH).send({ helpful: 'yes' });
    expect(bad.status).toBe(400);
    const ok = await request(app)
      .post(`/api/v1/skin-analysis/${id}/feedback`)
      .set(AUTH)
      .send({ helpful: true, accuracyRating: 4, comment: 'Membantu' });
    expect(ok.status).toBe(201);
    expect((await store.get('alice', id))?.feedback?.accuracyRating).toBe(4);
  });

  it('deletes account data, photos and auth user', async () => {
    const { app, store, photos, accounts } = buildTestApp();
    await analyze(app, AUTH, true);
    await analyze(app, AUTH_BOB, true);
    const noConfirm = await request(app).post('/api/v1/user/account-deletion').set(AUTH).send({});
    expect(noConfirm.status).toBe(400);
    const res = await request(app).post('/api/v1/user/account-deletion').set(AUTH).send({ confirm: 'DELETE' });
    expect(res.status).toBe(200);
    expect((await store.list('alice', { limit: 10 })).items).toHaveLength(0);
    expect((await store.list('bobby', { limit: 10 })).items).toHaveLength(1);
    expect([...photos.files.keys()].every((k) => k.startsWith('users/bobby/'))).toBe(true);
    expect(accounts.deleted).toEqual(['alice']);
  });
});
