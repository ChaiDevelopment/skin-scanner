import multer from 'multer';
import type { NextFunction, Request, Response } from 'express';
import { Errors } from '../utils/errors';
import { SUPPORTED_MIME_TYPES } from '../services/image/image.service';

/**
 * Single-image multipart upload kept in memory (never written to disk, so
 * there are no temp files to clean up). Only the "image" field is accepted.
 * The declared MIME is checked here; the real bytes are checked by sharp later.
 */
export function imageUpload(maxBytes: number) {
  const upload = multer({
    storage: multer.memoryStorage(),
    limits: { fileSize: maxBytes, files: 1, fields: 5, fieldSize: 1024, parts: 7 },
    fileFilter: (_req, file, cb) => {
      if ((SUPPORTED_MIME_TYPES as readonly string[]).includes(file.mimetype)) cb(null, true);
      else cb(Errors.unsupportedMedia());
    },
  }).single('image');

  const maxMb = Math.round(maxBytes / (1024 * 1024));
  return (req: Request, res: Response, next: NextFunction) => {
    if (!req.is('multipart/form-data')) return next(Errors.unsupportedMedia('Gunakan multipart/form-data dengan field "image".'));
    upload(req, res, (err: unknown) => {
      if (!err) {
        if (!req.file) return next(Errors.invalidInput('Field "image" wajib diisi.'));
        return next();
      }
      if (err instanceof multer.MulterError) {
        if (err.code === 'LIMIT_FILE_SIZE') return next(Errors.imageTooLarge(maxMb));
        return next(Errors.invalidInput('Unggahan tidak valid.'));
      }
      next(err);
    });
  };
}
