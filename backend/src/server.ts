import { existsSync } from 'node:fs';
import { resolve } from 'node:path';
import { createApp } from './app';
import { loadConfig } from './config/env';
import { buildProductionDeps } from './container';
import { createLogger } from './utils/logger';

// Load backend/.env if present (Node >= 20.12 built-in, no dotenv dependency).
const envFile = resolve(__dirname, '..', '.env');
if (existsSync(envFile)) process.loadEnvFile(envFile);

const logger = createLogger();

let config;
try {
  config = loadConfig();
} catch (err) {
  logger.fatal((err as Error).message);
  process.exit(1);
}

const app = createApp(buildProductionDeps(config, logger));
const server = app.listen(config.port, '0.0.0.0', () => {
  logger.info({ port: config.port, env: config.nodeEnv }, `AI Skin Scanner API listening on http://0.0.0.0:${config.port}/api/v1`);
});
// Allow long Gemini calls but don't hang forever.
server.requestTimeout = config.gemini.timeoutMs + 30_000;

const shutdown = (signal: string) => {
  logger.info({ signal }, 'Shutting down');
  server.close(() => process.exit(0));
  setTimeout(() => process.exit(1), 10_000).unref();
};
process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));
