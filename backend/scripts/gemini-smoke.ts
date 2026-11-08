/**
 * Manual live check against the real Gemini API (consumes a small amount of quota).
 *   npm run smoke:gemini                 -> sends a synthetic non-face image (expects insufficient_image_quality)
 *   npm run smoke:gemini -- path/to.jpg  -> sends your own photo
 * Prints only the validated, normalized result. Never prints the API key.
 */
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import sharp from 'sharp';
import { loadConfig } from '../src/config/env';
import { GeminiSkinAnalyzer } from '../src/services/gemini/gemini.service';
import { processUploadedImage } from '../src/services/image/image.service';
import { normalizeAnalysis } from '../src/schemas/skin-analysis.schema';
import { createLogger } from '../src/utils/logger';

async function main() {
  const envFile = resolve(__dirname, '..', '.env');
  if (existsSync(envFile)) process.loadEnvFile(envFile);
  const config = loadConfig();
  const analyzer = new GeminiSkinAnalyzer(config.gemini, createLogger('warn'));
  if (!analyzer.configured) throw new Error('GEMINI_API_KEY is not set in backend/.env');

  const arg = process.argv[2];
  const input = arg
    ? readFileSync(resolve(arg))
    : await sharp({ create: { width: 640, height: 640, channels: 3, background: { r: 120, g: 140, b: 200 } } }).jpeg().toBuffer();
  const image = await processUploadedImage(input);

  const started = Date.now();
  const raw = await analyzer.analyze(image.buffer, image.mimeType);
  console.log(`model=${analyzer.model} status=${raw.status} latencyMs=${Date.now() - started}`);
  console.log(JSON.stringify(raw.status === 'success' ? normalizeAnalysis(raw) : raw, null, 2));
}

main().catch((err) => {
  console.error('Smoke test failed:', err?.code ?? '', err?.message ?? err);
  process.exit(1);
});
