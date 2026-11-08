import pino, { type Logger } from 'pino';

/**
 * Structured JSON logger. Authorization headers, cookies, API keys and any
 * image payloads are redacted — raw facial images must never reach logs.
 */
export function createLogger(level: string = process.env.LOG_LEVEL ?? 'info'): Logger {
  return pino({
    level,
    base: { service: 'ai-skin-scanner-api' },
    redact: {
      paths: [
        'req.headers.authorization',
        'req.headers.cookie',
        'req.headers["x-goog-api-key"]',
        'req.headers["x-firebase-appcheck"]',
        'res.headers["set-cookie"]',
        '*.apiKey',
        '*.privateKey',
        '*.token',
        '*.idToken',
        '*.image',
        '*.buffer',
        '*.data',
      ],
      censor: '[REDACTED]',
    },
    timestamp: pino.stdTimeFunctions.isoTime,
  });
}

export const silentLogger: Logger = pino({ level: 'silent' });
