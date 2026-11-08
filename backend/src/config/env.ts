import { z } from 'zod';

const boolish = z
  .string()
  .optional()
  .transform((v) => (v ?? '').trim().toLowerCase() === 'true');

const optionalString = z
  .string()
  .optional()
  .transform((v) => {
    const t = (v ?? '').trim();
    return t.length > 0 ? t : undefined;
  });

const intWithDefault = (def: number, min: number, max: number) =>
  z
    .string()
    .optional()
    .transform((v) => (v === undefined || v.trim() === '' ? def : Number(v)))
    .pipe(z.number().int().min(min).max(max));

const EnvSchema = z.object({
  PORT: intWithDefault(3000, 1, 65535),
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),

  GEMINI_API_KEY: optionalString,
  GEMINI_MODEL: optionalString.transform((v) => v ?? 'gemini-3.5-flash'),
  GEMINI_MAX_OUTPUT_TOKENS: intWithDefault(8192, 512, 65536),
  GEMINI_TIMEOUT_MS: intWithDefault(90000, 5000, 300000),
  GEMINI_THINKING_BUDGET: optionalString.transform((v) => (v === undefined ? undefined : Number(v))),

  FIREBASE_PROJECT_ID: optionalString,
  FIREBASE_CLIENT_EMAIL: optionalString,
  FIREBASE_PRIVATE_KEY: optionalString,
  GOOGLE_APPLICATION_CREDENTIALS: optionalString,
  FIREBASE_STORAGE_BUCKET: optionalString,

  CORS_ORIGIN: optionalString,
  RATE_LIMIT_WINDOW_MS: intWithDefault(60000, 1000, 3600000),
  RATE_LIMIT_MAX_REQUESTS: intWithDefault(10, 1, 10000),
  MAX_IMAGE_SIZE_MB: intWithDefault(10, 1, 25),
  TRUST_PROXY: boolish,
  DEV_AUTH_BYPASS: boolish,
  APP_CHECK_MODE: z.enum(['off', 'monitor', 'enforce']).default('off'),
});

export interface AppConfig {
  port: number;
  nodeEnv: 'development' | 'test' | 'production';
  gemini: {
    apiKey?: string;
    model: string;
    maxOutputTokens: number;
    timeoutMs: number;
    thinkingBudget?: number;
  };
  firebase: {
    projectId?: string;
    clientEmail?: string;
    privateKey?: string;
    credentialsFile?: string;
    storageBucket?: string;
  };
  corsOrigins: string[];
  rateLimit: { windowMs: number; maxRequests: number };
  maxImageBytes: number;
  trustProxy: boolean;
  devAuthBypass: boolean;
  appCheckMode: 'off' | 'monitor' | 'enforce';
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const parsed = EnvSchema.safeParse(env);
  if (!parsed.success) {
    const issues = parsed.error.issues.map((i) => `${i.path.join('.')}: ${i.message}`).join('; ');
    throw new Error(`Invalid environment configuration: ${issues}`);
  }
  const e = parsed.data;

  if (e.DEV_AUTH_BYPASS && e.NODE_ENV === 'production') {
    throw new Error('DEV_AUTH_BYPASS=true is not allowed when NODE_ENV=production.');
  }
  if (e.GEMINI_THINKING_BUDGET !== undefined && !Number.isFinite(e.GEMINI_THINKING_BUDGET)) {
    throw new Error('GEMINI_THINKING_BUDGET must be a number when set.');
  }

  return {
    port: e.PORT,
    nodeEnv: e.NODE_ENV,
    gemini: {
      apiKey: e.GEMINI_API_KEY,
      model: e.GEMINI_MODEL,
      maxOutputTokens: e.GEMINI_MAX_OUTPUT_TOKENS,
      timeoutMs: e.GEMINI_TIMEOUT_MS,
      thinkingBudget: e.GEMINI_THINKING_BUDGET,
    },
    firebase: {
      projectId: e.FIREBASE_PROJECT_ID,
      clientEmail: e.FIREBASE_CLIENT_EMAIL,
      // .env files store the PEM with literal "\n" sequences.
      privateKey: e.FIREBASE_PRIVATE_KEY?.replace(/\\n/g, '\n'),
      credentialsFile: e.GOOGLE_APPLICATION_CREDENTIALS,
      storageBucket: e.FIREBASE_STORAGE_BUCKET,
    },
    corsOrigins: (e.CORS_ORIGIN ?? '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean),
    rateLimit: { windowMs: e.RATE_LIMIT_WINDOW_MS, maxRequests: e.RATE_LIMIT_MAX_REQUESTS },
    maxImageBytes: e.MAX_IMAGE_SIZE_MB * 1024 * 1024,
    trustProxy: e.TRUST_PROXY,
    devAuthBypass: e.DEV_AUTH_BYPASS,
    appCheckMode: e.APP_CHECK_MODE,
  };
}

export function isFirebaseConfigured(config: AppConfig): boolean {
  const f = config.firebase;
  return Boolean((f.projectId && f.clientEmail && f.privateKey) || f.credentialsFile);
}
