import request from 'supertest';
import { describe, expect, it } from 'vitest';
import { buildTestApp } from './helpers';

const AUTH = { Authorization: 'Bearer token-alice' };
const verifier = { verify: async (t: string) => t === 'good-app-check-token' };

describe('App Check middleware', () => {
  it('enforce: rejects requests without a valid App Check token', async () => {
    const { app } = buildTestApp({ env: { APP_CHECK_MODE: 'enforce' }, appCheckVerifier: verifier });
    expect((await request(app).get('/api/v1/skin-analysis/history').set(AUTH)).status).toBe(401);
    expect((await request(app).get('/api/v1/skin-analysis/history').set(AUTH).set('X-Firebase-AppCheck', 'bad')).status).toBe(401);
    expect((await request(app).get('/api/v1/skin-analysis/history').set(AUTH).set('X-Firebase-AppCheck', 'good-app-check-token')).status).toBe(200);
  });

  it('monitor: never blocks', async () => {
    const { app } = buildTestApp({ env: { APP_CHECK_MODE: 'monitor' }, appCheckVerifier: verifier });
    expect((await request(app).get('/api/v1/skin-analysis/history').set(AUTH)).status).toBe(200);
  });

  it('off by default', async () => {
    const { app } = buildTestApp({ appCheckVerifier: verifier });
    expect((await request(app).get('/api/v1/skin-analysis/history').set(AUTH)).status).toBe(200);
  });
});
