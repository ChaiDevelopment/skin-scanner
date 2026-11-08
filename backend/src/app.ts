import cors from 'cors';
import express, { type Express } from 'express';
import helmet from 'helmet';
import pinoHttp from 'pino-http';
import type { Logger } from 'pino';
import type { AppConfig } from './config/env';
import { errorHandler, notFoundHandler } from './middleware/error-handler';
import { globalLimiter } from './middleware/rate-limit';
import { requestId } from './middleware/request-id';
import type { AppCheckVerifier } from './middleware/app-check';
import { buildRouter } from './routes';
import { AnalysisService } from './services/analysis/analysis.service';
import type { AccountAdmin, AnalysisStore, PhotoStore, TokenVerifier } from './services/analysis/types';
import type { SkinAnalyzer } from './services/gemini/gemini.service';
import type { ProductFinder } from './services/products/product-finder';
import { Errors } from './utils/errors';

export interface AppDeps {
  config: AppConfig;
  logger: Logger;
  verifier: TokenVerifier | null;
  analyzer: SkinAnalyzer;
  productFinder: ProductFinder;
  store: AnalysisStore;
  photos: PhotoStore;
  accounts: AccountAdmin;
  firebaseConfigured: boolean;
  appCheckVerifier?: AppCheckVerifier | null;
}

export const API_VERSION = '1.0.0';

export function createApp(deps: AppDeps): Express {
  const app = express();
  app.disable('x-powered-by');
  if (deps.config.trustProxy) app.set('trust proxy', 1);

  app.use(requestId);
  app.use(
    pinoHttp({
      logger: deps.logger,
      genReqId: (req) => (req as express.Request).requestId ?? '',
      // Only log metadata — never bodies (which may contain images).
      serializers: {
        req: (req: { id: string; method: string; url: string }) => ({ id: req.id, method: req.method, url: req.url }),
        res: (res: { statusCode: number }) => ({ statusCode: res.statusCode }),
      },
      autoLogging: { ignore: (req) => req.url === '/api/v1/health' },
    }),
  );
  app.use(helmet());
  app.use(
    cors({
      origin: (origin, cb) => {
        // Native apps send no Origin header. Browser origins must be allow-listed.
        if (!origin || deps.config.corsOrigins.includes(origin)) return cb(null, true);
        cb(Errors.forbidden('Origin tidak diizinkan.'));
      },
      methods: ['GET', 'POST', 'DELETE'],
      allowedHeaders: ['Authorization', 'Content-Type', 'X-Request-Id', 'X-Firebase-AppCheck'],
      maxAge: 600,
    }),
  );
  app.use(globalLimiter());
  app.use(express.json({ limit: '32kb' }));

  const service = new AnalysisService({
    analyzer: deps.analyzer,
    productFinder: deps.productFinder,
    store: deps.store,
    photos: deps.photos,
    accounts: deps.accounts,
    logger: deps.logger,
  });

  app.use(
    '/api/v1',
    buildRouter({
      config: deps.config,
      verifier: deps.verifier,
      appCheckVerifier: deps.appCheckVerifier ?? null,
      logger: deps.logger,
      service,
      status: {
        geminiConfigured: deps.analyzer.configured,
        firebaseConfigured: deps.firebaseConfigured,
        photoStorageAvailable: deps.photos.available,
        devAuthBypass: deps.config.devAuthBypass && !deps.firebaseConfigured,
        maxImageBytes: deps.config.maxImageBytes,
        version: API_VERSION,
      },
    }),
  );

  app.use(notFoundHandler);
  app.use(errorHandler(deps.logger));
  return app;
}
