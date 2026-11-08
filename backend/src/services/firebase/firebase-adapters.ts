import type { App } from 'firebase-admin/app';
import { getAuth } from 'firebase-admin/auth';
import { FieldValue, Timestamp, getFirestore, type DocumentData, type Firestore } from 'firebase-admin/firestore';
import { getStorage } from 'firebase-admin/storage';
import type { Logger } from 'pino';
import { Errors } from '../../utils/errors';
import type {
  AccountAdmin,
  AnalysisStore,
  AuthenticatedUser,
  ListOptions,
  ListResult,
  PhotoStore,
  StoredAnalysis,
  TokenVerifier,
} from '../analysis/types';

export class FirebaseTokenVerifier implements TokenVerifier {
  constructor(private readonly app: App) {}

  async verify(idToken: string): Promise<AuthenticatedUser> {
    try {
      // checkRevoked=true so disabled/deleted accounts lose access immediately.
      const decoded = await getAuth(this.app).verifyIdToken(idToken, true);
      return { uid: decoded.uid, email: decoded.email };
    } catch {
      throw Errors.unauthenticated('Sesi tidak valid atau telah berakhir. Silakan masuk kembali.');
    }
  }
}

export class FirebaseAccountAdmin implements AccountAdmin {
  constructor(private readonly app: App) {}
  async deleteUser(uid: string): Promise<void> {
    try {
      await getAuth(this.app).deleteUser(uid);
    } catch (err) {
      if ((err as { code?: string }).code === 'auth/user-not-found') return;
      throw err;
    }
  }
}

const toDate = (v: unknown): Date => {
  if (v instanceof Timestamp) return v.toDate();
  if (v instanceof Date) return v;
  return new Date(0);
};

function fromDoc(data: DocumentData): StoredAnalysis {
  return {
    ...(data as StoredAnalysis),
    createdAt: toDate(data.createdAt),
    feedback: data.feedback ? { ...data.feedback, createdAt: toDate(data.feedback.createdAt) } : null,
  };
}

export class FirestoreAnalysisStore implements AnalysisStore {
  readonly kind = 'firestore' as const;
  private readonly db: Firestore;

  constructor(app: App) {
    this.db = getFirestore(app);
  }

  private col(uid: string) {
    return this.db.collection('users').doc(uid).collection('skin_analyses');
  }

  async touchUser(uid: string, email?: string): Promise<void> {
    const ref = this.db.collection('users').doc(uid);
    await this.db.runTransaction(async (tx) => {
      const snap = await tx.get(ref);
      if (snap.exists) {
        tx.update(ref, { updatedAt: FieldValue.serverTimestamp() });
      } else {
        tx.set(ref, {
          uid,
          email: email ?? null,
          displayName: email ? email.split('@')[0] : 'User',
          createdAt: FieldValue.serverTimestamp(),
          updatedAt: FieldValue.serverTimestamp(),
        });
      }
    });
  }

  async create(record: StoredAnalysis): Promise<void> {
    await this.col(record.uid)
      .doc(record.analysisId)
      .set({ ...record, createdAt: Timestamp.fromDate(record.createdAt) });
  }

  async get(uid: string, analysisId: string): Promise<StoredAnalysis | null> {
    const snap = await this.col(uid).doc(analysisId).get();
    return snap.exists ? fromDoc(snap.data()!) : null;
  }

  async list(uid: string, opts: ListOptions): Promise<ListResult> {
    let q = this.col(uid).orderBy('createdAt', 'desc').orderBy('analysisId', 'desc').limit(opts.limit + 1);
    if (opts.cursor) {
      const cursorSnap = await this.col(uid).doc(opts.cursor).get();
      if (!cursorSnap.exists) throw Errors.invalidInput('Cursor tidak valid.');
      q = q.startAfter(cursorSnap);
    }
    const snap = await q.get();
    const docs = snap.docs.map((d) => fromDoc(d.data()));
    const hasMore = docs.length > opts.limit;
    const items = hasMore ? docs.slice(0, opts.limit) : docs;
    return { items, nextCursor: hasMore ? items[items.length - 1]!.analysisId : null };
  }

  async update(uid: string, analysisId: string, patch: Partial<StoredAnalysis>): Promise<void> {
    const data: DocumentData = { ...patch };
    if (patch.feedback) data.feedback = { ...patch.feedback, createdAt: Timestamp.fromDate(patch.feedback.createdAt) };
    await this.col(uid).doc(analysisId).update(data);
  }

  async delete(uid: string, analysisId: string): Promise<void> {
    await this.col(uid).doc(analysisId).delete();
  }

  async deleteAllForUser(uid: string): Promise<void> {
    await this.db.recursiveDelete(this.db.collection('users').doc(uid));
  }
}

export class FirebasePhotoStore implements PhotoStore {
  readonly available: boolean;

  constructor(
    private readonly app: App,
    bucketName: string | undefined,
    private readonly logger: Logger,
  ) {
    this.available = Boolean(bucketName);
  }

  private bucket() {
    return getStorage(this.app).bucket();
  }

  async save(path: string, data: Buffer, mimeType: string): Promise<void> {
    await this.bucket().file(path).save(data, {
      resumable: false,
      contentType: mimeType,
      metadata: { cacheControl: 'private, max-age=0, no-store' },
    });
  }

  async delete(path: string): Promise<void> {
    await this.bucket().file(path).delete({ ignoreNotFound: true });
  }

  async signedReadUrl(path: string, ttlMs: number): Promise<string | null> {
    try {
      const [url] = await this.bucket()
        .file(path)
        .getSignedUrl({ action: 'read', expires: Date.now() + ttlMs, version: 'v4' });
      return url;
    } catch (err) {
      this.logger.warn({ err: (err as Error).message }, 'Could not create signed photo URL');
      return null;
    }
  }

  async deleteAllForUser(uid: string): Promise<void> {
    await this.bucket().deleteFiles({ prefix: `users/${uid}/`, force: true });
  }
}
