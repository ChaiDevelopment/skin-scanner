import type { NextFunction, Request, Response } from 'express';
import { Errors } from '../utils/errors';
import type { TokenVerifier } from '../services/analysis/types';

/**
 * Verifies `Authorization: Bearer <Firebase ID token>`. The uid used everywhere
 * downstream comes ONLY from the verified token, never from the request body/query.
 */
export function requireAuth(verifier: TokenVerifier | null) {
  return async (req: Request, _res: Response, next: NextFunction) => {
    try {
      if (!verifier) throw Errors.notConfigured('Firebase Authentication');
      const header = req.header('authorization') ?? '';
      const match = /^Bearer\s+([A-Za-z0-9._-]{10,4096})$/.exec(header);
      if (!match?.[1]) throw Errors.unauthenticated();
      req.user = await verifier.verify(match[1]);
      next();
    } catch (err) {
      next(err);
    }
  };
}
