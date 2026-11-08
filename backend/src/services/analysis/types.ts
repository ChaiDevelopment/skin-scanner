import type { SkinAnalysisResult } from '../../schemas/skin-analysis.schema';

export interface AnalysisFeedback {
  helpful: boolean;
  accuracyRating: number | null;
  comment: string;
  createdAt: Date;
}

export interface StoredAnalysis extends SkinAnalysisResult {
  analysisId: string;
  uid: string;
  createdAt: Date;
  status: 'completed';
  model: string;
  knowledgeVersion: string;
  photoStorageEnabled: boolean;
  photoStoragePath: string | null;
  feedback: AnalysisFeedback | null;
}

export interface ListOptions {
  limit: number;
  cursor?: string;
}

export interface ListResult {
  items: StoredAnalysis[];
  nextCursor: string | null;
}

/** All methods are scoped by the authenticated uid — callers can never reach another user's data. */
export interface AnalysisStore {
  readonly kind: 'firestore' | 'memory';
  create(record: StoredAnalysis): Promise<void>;
  get(uid: string, analysisId: string): Promise<StoredAnalysis | null>;
  list(uid: string, opts: ListOptions): Promise<ListResult>;
  update(uid: string, analysisId: string, patch: Partial<Pick<StoredAnalysis, 'feedback' | 'photoStorageEnabled' | 'photoStoragePath'>>): Promise<void>;
  delete(uid: string, analysisId: string): Promise<void>;
  /** Removes users/{uid} and every subcollection (analyses, preferences). */
  deleteAllForUser(uid: string): Promise<void>;
  touchUser(uid: string, email?: string): Promise<void>;
}

export interface PhotoStore {
  readonly available: boolean;
  save(path: string, data: Buffer, mimeType: string): Promise<void>;
  delete(path: string): Promise<void>;
  signedReadUrl(path: string, ttlMs: number): Promise<string | null>;
  deleteAllForUser(uid: string): Promise<void>;
}

export interface AuthenticatedUser {
  uid: string;
  email?: string;
  devBypass?: boolean;
}

export interface TokenVerifier {
  verify(idToken: string): Promise<AuthenticatedUser>;
}

export interface AccountAdmin {
  deleteUser(uid: string): Promise<void>;
}

export const photoPathFor = (uid: string, analysisId: string) => `users/${uid}/scans/${analysisId}.jpg`;
