import { Router, type NextFunction, type Request, type RequestHandler, type Response } from 'express';
import type { AppConfig } from '../config/env';
import { analysisController } from '../controllers/analysis.controller';
import { systemController, type SystemStatus } from '../controllers/system.controller';
import { requireAuth } from '../middleware/auth';
import { appCheck, type AppCheckVerifier } from '../middleware/app-check';
import type { Logger } from 'pino';
import { analysisLimiter } from '../middleware/rate-limit';
import { imageUpload } from '../middleware/upload';
import type { AnalysisService } from '../services/analysis/analysis.service';
import type { TokenVerifier } from '../services/analysis/types';

/** Forwards async errors to the error handler (Express 4 does not do it automatically). */
const wrap =
  (fn: (req: Request, res: Response) => Promise<void> | void): RequestHandler =>
  (req: Request, res: Response, next: NextFunction) => {
    Promise.resolve()
      .then(() => fn(req, res))
      .catch(next);
  };

export function buildRouter(opts: {
  config: AppConfig;
  status: SystemStatus;
  verifier: TokenVerifier | null;
  appCheckVerifier: AppCheckVerifier | null;
  logger: Logger;
  service: AnalysisService;
}): Router {
  const r = Router();
  const authOnly = requireAuth(opts.verifier);
  const check = appCheck(opts.config.appCheckMode, opts.appCheckVerifier, opts.logger);
  const auth = [authOnly, check];
  const sys = systemController(opts.status, opts.service);
  const analysis = analysisController(opts.service);

  r.get('/health', wrap(sys.health));
  r.get('/config', wrap(sys.config));

  r.post(
    '/skin-analysis',
    auth,
    analysisLimiter(opts.config.rateLimit.windowMs, opts.config.rateLimit.maxRequests),
    imageUpload(opts.config.maxImageBytes),
    wrap(analysis.create),
  );
  r.get('/skin-analysis/history', auth, wrap(analysis.history));
  r.get('/skin-analysis/:analysisId', auth, wrap(analysis.get));
  r.delete('/skin-analysis/:analysisId', auth, wrap(analysis.remove));
  r.delete('/skin-analysis/:analysisId/photo', auth, wrap(analysis.removePhoto));
  r.post('/skin-analysis/:analysisId/feedback', auth, wrap(analysis.feedback));
  r.post('/user/account-deletion', auth, wrap(sys.deleteAccount));

  return r;
}
