import type { Logger } from 'pino';
import type { AppConfig } from './config/env';
import type { AppDeps } from './app';
import { initFirebaseAdmin } from './services/firebase/firebase-admin';
import {
  FirebaseAccountAdmin,
  FirebasePhotoStore,
  FirebaseTokenVerifier,
  FirestoreAnalysisStore,
} from './services/firebase/firebase-adapters';
import {
  DevBypassTokenVerifier,
  InMemoryAnalysisStore,
  InMemoryPhotoStore,
  NoopAccountAdmin,
} from './services/analysis/memory-stores';
import { GeminiSkinAnalyzer } from './services/gemini/gemini.service';
import { GeminiProductFinder } from './services/products/product-finder';
import { getAppCheck } from 'firebase-admin/app-check';

/** Wires real infrastructure from configuration. Reports clearly what is missing. */
export function buildProductionDeps(config: AppConfig, logger: Logger): AppDeps {
  const analyzer = new GeminiSkinAnalyzer(config.gemini, logger);
  const productFinder = new GeminiProductFinder(config.gemini, logger);
  if (!analyzer.configured) logger.warn('GEMINI_API_KEY is not set — POST /skin-analysis will return 503 NOT_CONFIGURED');

  const firebaseApp = initFirebaseAdmin(config);
  if (firebaseApp) {
    if (config.devAuthBypass) logger.warn('DEV_AUTH_BYPASS ignored because Firebase credentials are configured');
    const photos = new FirebasePhotoStore(firebaseApp, config.firebase.storageBucket, logger);
    if (!photos.available) logger.warn('FIREBASE_STORAGE_BUCKET not set — optional photo storage disabled');
    logger.info({ model: analyzer.model }, 'Using Firebase Auth + Firestore');
    return {
      config,
      logger,
      analyzer,
      productFinder,
      firebaseConfigured: true,
      verifier: new FirebaseTokenVerifier(firebaseApp),
      store: new FirestoreAnalysisStore(firebaseApp),
      photos,
      accounts: new FirebaseAccountAdmin(firebaseApp),
      appCheckVerifier: {
        verify: async (token: string) => {
          await getAppCheck(firebaseApp).verifyToken(token);
          return true;
        },
      },
    };
  }

  if (config.devAuthBypass) {
    logger.warn(
      'DEV MODE: Firebase not configured. Accepting the fixed token "dev-local-token" and storing history IN MEMORY (lost on restart). Never use in production.',
    );
    return {
      config,
      logger,
      analyzer,
      productFinder,
      firebaseConfigured: false,
      verifier: new DevBypassTokenVerifier(null),
      store: new InMemoryAnalysisStore(),
      photos: new InMemoryPhotoStore(false),
      accounts: new NoopAccountAdmin(),
    };
  }

  logger.warn('Firebase Admin credentials missing — protected endpoints will return 503 NOT_CONFIGURED. See docs/FIREBASE_SETUP.md');
  return {
    config,
    logger,
    analyzer,
    productFinder,
    firebaseConfigured: false,
    verifier: null,
    store: new InMemoryAnalysisStore(),
    photos: new InMemoryPhotoStore(false),
    accounts: new NoopAccountAdmin(),
  };
}
