import { randomUUID } from 'node:crypto';
import type { Logger } from 'pino';
import { Errors } from '../../utils/errors';
import { normalizeAnalysis, BASE_DISCLAIMER } from '../../schemas/skin-analysis.schema';
import type { SkinAnalyzer } from '../gemini/gemini.service';
import type { ProductFinder } from '../products/product-finder';
import type { ProductSuggestions } from '../../schemas/product-suggestions.schema';
import { KNOWLEDGE_VERSION } from '../gemini/dermatology-knowledge';
import { processUploadedImage } from '../image/image.service';
import {
  photoPathFor,
  type AccountAdmin,
  type AnalysisStore,
  type AuthenticatedUser,
  type PhotoStore,
  type StoredAnalysis,
} from './types';

const SIGNED_URL_TTL_MS = 10 * 60 * 1000;

export interface AnalysisServiceDeps {
  analyzer: SkinAnalyzer;
  productFinder: ProductFinder;
  store: AnalysisStore;
  photos: PhotoStore;
  accounts: AccountAdmin;
  logger: Logger;
}

export class AnalysisService {
  constructor(private readonly d: AnalysisServiceDeps) {}

  async analyze(user: AuthenticatedUser, imageBytes: Buffer, storePhoto: boolean): Promise<StoredAnalysis> {
    const image = await processUploadedImage(imageBytes);
    const raw = await this.d.analyzer.analyze(image.buffer, image.mimeType);

    if (raw.status === 'insufficient_image_quality') {
      throw Errors.insufficientQuality({
        imageQuality: {
          status: raw.image_quality.status,
          lighting: raw.image_quality.lighting,
          faceVisibility: raw.image_quality.face_visibility,
          limitations: raw.image_quality.limitations,
        },
        limitations: raw.limitations.length ? raw.limitations : [BASE_DISCLAIMER],
      });
    }

    const result = normalizeAnalysis(raw);
    const analysisId = randomUUID();
    let photoStoragePath: string | null = null;

    if (storePhoto) {
      if (!this.d.photos.available) {
        this.d.logger.warn('storePhoto requested but photo storage is not configured; continuing without it');
      } else {
        const path = photoPathFor(user.uid, analysisId);
        try {
          await this.d.photos.save(path, image.buffer, image.mimeType);
          photoStoragePath = path;
        } catch (err) {
          // Photo retention is optional: a storage failure must not discard a completed analysis.
          this.d.logger.error({ err: (err as Error).message }, 'Photo upload failed; saving analysis without photo');
        }
      }
    }

    const record: StoredAnalysis = {
      ...result,
      analysisId,
      uid: user.uid,
      createdAt: new Date(),
      status: 'completed',
      model: this.d.analyzer.model,
      knowledgeVersion: KNOWLEDGE_VERSION,
      photoStorageEnabled: photoStoragePath !== null,
      photoStoragePath,
      feedback: null,
    };

    try {
      await this.d.store.touchUser(user.uid, user.email);
      await this.d.store.create(record);
    } catch (err) {
      // Don't leave an orphaned photo if the record could not be saved.
      if (photoStoragePath) await this.d.photos.delete(photoStoragePath).catch(() => undefined);
      throw err;
    }
    return record;
  }

  async get(user: AuthenticatedUser, analysisId: string): Promise<{ record: StoredAnalysis; photoUrl: string | null }> {
    const record = await this.requireOwned(user, analysisId);
    return { record, photoUrl: await this.photoUrlFor(record) };
  }

  /** Short-lived signed read URL for the record's stored photo, or null when there is none. */
  async photoUrlFor(record: StoredAnalysis): Promise<string | null> {
    return record.photoStoragePath && this.d.photos.available
      ? this.d.photos.signedReadUrl(record.photoStoragePath, SIGNED_URL_TTL_MS)
      : null;
  }

  /** History page; each item carries a short-lived signed URL for its stored photo (or null). */
  async list(user: AuthenticatedUser, limit: number, cursor?: string) {
    const page = await this.d.store.list(user.uid, { limit, cursor });
    const items = await Promise.all(
      page.items.map(async (record) => ({ record, photoUrl: await this.photoUrlFor(record) })),
    );
    return { items, nextCursor: page.nextCursor };
  }

  async delete(user: AuthenticatedUser, analysisId: string): Promise<void> {
    const record = await this.requireOwned(user, analysisId);
    if (record.photoStoragePath) await this.d.photos.delete(record.photoStoragePath);
    await this.d.store.delete(user.uid, analysisId);
  }

  async deletePhoto(user: AuthenticatedUser, analysisId: string): Promise<void> {
    const record = await this.requireOwned(user, analysisId);
    if (record.photoStoragePath) await this.d.photos.delete(record.photoStoragePath);
    await this.d.store.update(user.uid, analysisId, { photoStorageEnabled: false, photoStoragePath: null });
  }

  /**
   * Product suggestions for one of the user's analyses. Cached on the record so reopening is
   * instant and free; refresh=true asks Gemini again.
   */
  async findProducts(user: AuthenticatedUser, analysisId: string, refresh: boolean): Promise<ProductSuggestions> {
    const record = await this.requireOwned(user, analysisId);
    if (record.productSuggestions && !refresh) return record.productSuggestions;
    const suggestions = await this.d.productFinder.find(record);
    await this.d.store.update(user.uid, analysisId, { productSuggestions: suggestions });
    return suggestions;
  }

  async addFeedback(
    user: AuthenticatedUser,
    analysisId: string,
    fb: { helpful: boolean; accuracyRating?: number; comment?: string },
  ): Promise<void> {
    await this.requireOwned(user, analysisId);
    await this.d.store.update(user.uid, analysisId, {
      feedback: {
        helpful: fb.helpful,
        accuracyRating: fb.accuracyRating ?? null,
        comment: fb.comment ?? '',
        createdAt: new Date(),
      },
    });
  }

  async deleteAccount(user: AuthenticatedUser): Promise<void> {
    // Order: photos → Firestore data → auth account (so a partial failure is retryable while still signed in).
    await this.d.photos.deleteAllForUser(user.uid);
    await this.d.store.deleteAllForUser(user.uid);
    if (!user.devBypass) await this.d.accounts.deleteUser(user.uid);
  }

  private async requireOwned(user: AuthenticatedUser, analysisId: string): Promise<StoredAnalysis> {
    const record = await this.d.store.get(user.uid, analysisId);
    if (!record) throw Errors.notFound('Analisis tidak ditemukan.');
    // Defence in depth: the store is already uid-scoped, but verify the stored owner too.
    if (record.uid !== user.uid) throw Errors.forbidden();
    return record;
  }
}
