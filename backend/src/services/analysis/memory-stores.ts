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
} from './types';

/**
 * In-memory implementations used ONLY by automated tests and by the explicit
 * DEV_AUTH_BYPASS local-development mode. Data disappears on restart.
 * They are never selected when Firebase credentials are configured.
 */

export class InMemoryAnalysisStore implements AnalysisStore {
  readonly kind = 'memory' as const;
  private readonly data = new Map<string, Map<string, StoredAnalysis>>();
  readonly users = new Map<string, { email?: string }>();

  private bucket(uid: string) {
    let b = this.data.get(uid);
    if (!b) {
      b = new Map();
      this.data.set(uid, b);
    }
    return b;
  }

  async touchUser(uid: string, email?: string) {
    this.users.set(uid, { email });
  }
  async create(record: StoredAnalysis) {
    this.bucket(record.uid).set(record.analysisId, structuredClone(record));
  }
  async get(uid: string, id: string) {
    const r = this.data.get(uid)?.get(id);
    return r ? structuredClone(r) : null;
  }
  async list(uid: string, opts: ListOptions): Promise<ListResult> {
    const all = [...this.bucket(uid).values()].sort(
      (a, b) => b.createdAt.getTime() - a.createdAt.getTime() || b.analysisId.localeCompare(a.analysisId),
    );
    let start = 0;
    if (opts.cursor) {
      const idx = all.findIndex((r) => r.analysisId === opts.cursor);
      if (idx < 0) throw Errors.invalidInput('Cursor tidak valid.');
      start = idx + 1;
    }
    const slice = all.slice(start, start + opts.limit);
    const hasMore = start + opts.limit < all.length;
    return { items: slice.map((r) => structuredClone(r)), nextCursor: hasMore ? slice[slice.length - 1]!.analysisId : null };
  }
  async update(uid: string, id: string, patch: Partial<StoredAnalysis>) {
    const r = this.data.get(uid)?.get(id);
    if (r) Object.assign(r, patch);
  }
  async delete(uid: string, id: string) {
    this.data.get(uid)?.delete(id);
  }
  async deleteAllForUser(uid: string) {
    this.data.delete(uid);
    this.users.delete(uid);
  }
}

export class InMemoryPhotoStore implements PhotoStore {
  readonly files = new Map<string, Buffer>();
  constructor(readonly available = true) {}
  async save(path: string, data: Buffer) {
    this.files.set(path, Buffer.from(data));
  }
  async delete(path: string) {
    this.files.delete(path);
  }
  async signedReadUrl() {
    return null; // no public URL in memory mode
  }
  async deleteAllForUser(uid: string) {
    for (const k of [...this.files.keys()]) if (k.startsWith(`users/${uid}/`)) this.files.delete(k);
  }
}

export const DEV_BYPASS_TOKEN = 'dev-local-token';
export const DEV_BYPASS_UID = 'dev-local-user';

/** Accepts exactly one fixed token. Only wired up when DEV_AUTH_BYPASS=true and NODE_ENV!=production. */
export class DevBypassTokenVerifier implements TokenVerifier {
  constructor(private readonly fallback: TokenVerifier | null) {}
  async verify(token: string): Promise<AuthenticatedUser> {
    if (token === DEV_BYPASS_TOKEN) return { uid: DEV_BYPASS_UID, email: 'dev@localhost', devBypass: true };
    if (this.fallback) return this.fallback.verify(token);
    throw Errors.unauthenticated();
  }
}

export class NoopAccountAdmin implements AccountAdmin {
  async deleteUser() {}
}
