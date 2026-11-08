import rateLimit from 'express-rate-limit';
import { Errors } from '../utils/errors';

/** Broad per-IP limiter for every route. */
export function globalLimiter() {
  return rateLimit({
    windowMs: 60_000,
    limit: 120,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    handler: (_req, _res, next) => next(Errors.rateLimited()),
  });
}

/** Per-user limiter for expensive AI endpoints. Must run after requireAuth. [scope] keeps separate budgets per endpoint. */
export function analysisLimiter(windowMs: number, max: number, scope = 'analysis') {
  return rateLimit({
    windowMs,
    limit: max,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    keyGenerator: (req) => `${scope}:uid:${req.user?.uid ?? 'anonymous'}`,
    handler: (_req, _res, next) => next(Errors.rateLimited()),
  });
}
