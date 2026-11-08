import type { NextFunction, Request, Response } from 'express';
import type { Logger } from 'pino';
import { Errors } from '../utils/errors';

export type AppCheckMode = 'off' | 'monitor' | 'enforce';

export interface AppCheckVerifier {
  verify(token: string): Promise<boolean>;
}

/**
 * Firebase App Check (X-Firebase-AppCheck header, sent by the Android app).
 *  - off:     ignored
 *  - monitor: verified and logged, never blocks (use while rolling out)
 *  - enforce: missing/invalid tokens are rejected with 401
 */
export function appCheck(mode: AppCheckMode, verifier: AppCheckVerifier | null, logger: Logger) {
  return async (req: Request, _res: Response, next: NextFunction) => {
    if (mode === 'off' || !verifier || req.user?.devBypass) return next();
    const token = req.header('x-firebase-appcheck');
    let valid = false;
    if (token) {
      try {
        valid = await verifier.verify(token);
      } catch {
        valid = false;
      }
    }
    if (valid) return next();
    if (mode === 'enforce') return next(Errors.unauthenticated('Aplikasi tidak terverifikasi (App Check).'));
    logger.warn({ requestId: req.requestId, present: Boolean(token) }, 'App Check token missing or invalid (monitor mode)');
    next();
  };
}
