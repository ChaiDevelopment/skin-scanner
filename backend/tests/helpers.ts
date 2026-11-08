import sharp from 'sharp';
import { createApp } from '../src/app';
import { loadConfig, type AppConfig } from '../src/config/env';
import { InMemoryAnalysisStore, InMemoryPhotoStore, NoopAccountAdmin } from '../src/services/analysis/memory-stores';
import type { AccountAdmin, TokenVerifier } from '../src/services/analysis/types';
import { GeminiSkinAnalyzer, type GenerateContentFn } from '../src/services/gemini/gemini.service';
import { Errors } from '../src/utils/errors';
import { silentLogger } from '../src/utils/logger';

export const validGeminiOutput = {
  status: 'success',
  overall_summary: 'Kulit terlihat cukup merata dengan pori yang tampak di area hidung dan pipi.',
  image_quality: { status: 'good', lighting: 'adequate', face_visibility: 'clear', limitations: [] },
  skin_observations: [
    {
      category: 'pores',
      severity: 'moderate',
      confidence: 0.83,
      observation: 'Pori tampak terlihat di area pipi.',
      affected_area: 'cheeks',
    },
  ],
  skin_appearance: {
    texture: 'slightly_uneven',
    visible_redness: 'mild',
    visible_dryness: 'unknown',
    pigmentation: 'mild',
    visible_shine: 'mild',
  },
  recommendations: [
    {
      category: 'sun_protection',
      title: 'Tabir surya harian',
      description: 'Gunakan sunscreen SPF 30+ PA+++ setiap pagi.',
      key_ingredients: ['zinc oxide'],
      routine_time: 'morning',
      priority: 'high',
      cautions: '',
    },
  ],
  routine: { morning: ['Cuci muka', 'Pelembap', 'Sunscreen'], evening: ['Cuci muka', 'Pelembap'] },
  limitations: [],
  professional_consultation_recommended: false,
  professional_consultation_reason: '',
  unexpected_field: 'should be stripped',
};

export const insufficientGeminiOutput = {
  ...validGeminiOutput,
  status: 'insufficient_image_quality',
  overall_summary: '',
  image_quality: { status: 'poor', lighting: 'too_dark', face_visibility: 'unclear', limitations: ['Foto terlalu gelap.'] },
  skin_observations: [],
  recommendations: [],
  skin_appearance: { texture: 'unknown', visible_redness: 'unknown', visible_dryness: 'unknown', pigmentation: 'unknown', visible_shine: 'unknown' },
};

export const textResponse = (obj: unknown): GenerateContentFn => async () => ({ text: JSON.stringify(obj) });

export class FakeVerifier implements TokenVerifier {
  async verify(token: string) {
    const m = /^token-([a-z]+)$/.exec(token);
    if (!m) throw Errors.unauthenticated();
    return { uid: m[1]!, email: `${m[1]}@example.com` };
  }
}

export class RecordingAccountAdmin implements AccountAdmin {
  deleted: string[] = [];
  async deleteUser(uid: string) {
    this.deleted.push(uid);
  }
}

export function testConfig(overrides: Record<string, string> = {}): AppConfig {
  return loadConfig({
    NODE_ENV: 'test',
    GEMINI_API_KEY: 'test-key-not-real',
    GEMINI_MODEL: 'gemini-test',
    GEMINI_TIMEOUT_MS: '5000',
    MAX_IMAGE_SIZE_MB: '2',
    RATE_LIMIT_MAX_REQUESTS: '100',
    ...overrides,
  });
}

export function buildTestApp(
  opts: {
    generate?: GenerateContentFn;
    env?: Record<string, string>;
    verifier?: TokenVerifier | null;
    photosAvailable?: boolean;
    appCheckVerifier?: { verify(token: string): Promise<boolean> } | null;
  } = {},
) {
  const config = testConfig(opts.env);
  const store = new InMemoryAnalysisStore();
  const photos = new InMemoryPhotoStore(opts.photosAvailable ?? true);
  const accounts = new RecordingAccountAdmin();
  const analyzer = new GeminiSkinAnalyzer(config.gemini, silentLogger, opts.generate ?? textResponse(validGeminiOutput));
  const app = createApp({
    config,
    logger: silentLogger,
    verifier: opts.verifier === undefined ? new FakeVerifier() : opts.verifier,
    analyzer,
    store,
    photos,
    accounts,
    firebaseConfigured: true,
    appCheckVerifier: opts.appCheckVerifier ?? null,
  });
  return { app, store, photos, accounts, config };
}

export async function makeJpeg(width = 640, height = 640): Promise<Buffer> {
  return sharp({ create: { width, height, channels: 3, background: { r: 210, g: 170, b: 150 } } })
    .jpeg({ quality: 80 })
    .toBuffer();
}

export { NoopAccountAdmin };
