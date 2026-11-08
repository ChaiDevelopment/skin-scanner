import sharp from 'sharp';
import { Errors } from '../../utils/errors';

export const SUPPORTED_MIME_TYPES = ['image/jpeg', 'image/png', 'image/webp'] as const;
const SHARP_FORMATS = new Set(['jpeg', 'png', 'webp']);

export const MIN_DIMENSION_PX = 320;
export const MAX_OUTPUT_DIMENSION_PX = 1536;

export interface ProcessedImage {
  buffer: Buffer;
  mimeType: 'image/jpeg';
  width: number;
  height: number;
}

/**
 * Validates the real image bytes (not the client-declared MIME type), applies
 * EXIF orientation, downsizes, and re-encodes as JPEG. Re-encoding strips all
 * metadata (EXIF GPS, device info) before anything leaves the server.
 * Everything happens in memory; no temporary files are written.
 */
export async function processUploadedImage(input: Buffer): Promise<ProcessedImage> {
  let meta: sharp.Metadata;
  try {
    meta = await sharp(input, { failOn: 'error', limitInputPixels: 50_000_000 }).metadata();
  } catch {
    throw Errors.unsupportedMedia('File bukan gambar yang valid atau formatnya tidak didukung.');
  }
  if (!meta.format || !SHARP_FORMATS.has(meta.format)) {
    throw Errors.unsupportedMedia();
  }
  if (!meta.width || !meta.height || Math.min(meta.width, meta.height) < MIN_DIMENSION_PX) {
    throw Errors.invalidImage(`Resolusi gambar terlalu kecil (minimal ${MIN_DIMENSION_PX}px).`);
  }

  try {
    const { data, info } = await sharp(input, { failOn: 'error' })
      .rotate()
      .resize({
        width: MAX_OUTPUT_DIMENSION_PX,
        height: MAX_OUTPUT_DIMENSION_PX,
        fit: 'inside',
        withoutEnlargement: true,
      })
      .jpeg({ quality: 88, mozjpeg: true })
      .toBuffer({ resolveWithObject: true });
    return { buffer: data, mimeType: 'image/jpeg', width: info.width, height: info.height };
  } catch {
    throw Errors.invalidImage();
  }
}
