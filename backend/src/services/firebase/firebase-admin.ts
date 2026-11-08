import { cert, getApps, initializeApp, applicationDefault, type App } from 'firebase-admin/app';
import type { AppConfig } from '../../config/env';
import { isFirebaseConfigured } from '../../config/env';

/** Initializes the Firebase Admin SDK once. Returns null when credentials are not configured. */
export function initFirebaseAdmin(config: AppConfig): App | null {
  if (!isFirebaseConfigured(config)) return null;
  const existing = getApps()[0];
  if (existing) return existing;

  const f = config.firebase;
  const credential =
    f.projectId && f.clientEmail && f.privateKey
      ? cert({ projectId: f.projectId, clientEmail: f.clientEmail, privateKey: f.privateKey })
      : applicationDefault(); // reads GOOGLE_APPLICATION_CREDENTIALS

  return initializeApp({
    credential,
    projectId: f.projectId,
    storageBucket: f.storageBucket,
  });
}
