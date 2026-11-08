import { describe, expect, it } from 'vitest';
import { isFirebaseConfigured, loadConfig } from '../src/config/env';

describe('loadConfig', () => {
  it('applies defaults', () => {
    const c = loadConfig({});
    expect(c.port).toBe(3000);
    expect(c.gemini.model).toBe('gemini-3.5-flash');
    expect(c.maxImageBytes).toBe(10 * 1024 * 1024);
    expect(c.devAuthBypass).toBe(false);
    expect(isFirebaseConfigured(c)).toBe(false);
  });

  it('refuses dev auth bypass in production', () => {
    expect(() => loadConfig({ NODE_ENV: 'production', DEV_AUTH_BYPASS: 'true' })).toThrow(/not allowed/);
  });

  it('decodes escaped private key newlines', () => {
    const c = loadConfig({
      FIREBASE_PROJECT_ID: 'p',
      FIREBASE_CLIENT_EMAIL: 'e@p.iam.gserviceaccount.com',
      FIREBASE_PRIVATE_KEY: '-----BEGIN PRIVATE KEY-----\\nabc\\n-----END PRIVATE KEY-----',
    });
    expect(c.firebase.privateKey).toContain('\nabc\n');
    expect(isFirebaseConfigured(c)).toBe(true);
  });

  it('rejects invalid numbers', () => {
    expect(() => loadConfig({ PORT: 'abc' })).toThrow();
  });

  it('parses CORS origins list', () => {
    expect(loadConfig({ CORS_ORIGIN: 'https://a.com, https://b.com' }).corsOrigins).toEqual(['https://a.com', 'https://b.com']);
  });
});
