import type { Request, Response } from 'express';
import type { AnalysisService } from '../services/analysis/analysis.service';
import type { StoredAnalysis } from '../services/analysis/types';
import { AnalysisIdParams, AnalyzeBody, FeedbackBody, HistoryQuery, ProductsBody } from '../validators/analysis.validators';
import type { ProductSuggestions } from '../schemas/product-suggestions.schema';
import { Errors } from '../utils/errors';

/** Public shape of an analysis. Internal fields (uid, storage path) are not exposed. */
export function toApiAnalysis(r: StoredAnalysis, photoUrl: string | null = null) {
  return {
    analysisId: r.analysisId,
    createdAt: r.createdAt.toISOString(),
    status: r.status,
    analysisVersion: r.analysisVersion,
    model: r.model,
    overallSummary: r.overallSummary,
    imageQuality: r.imageQuality,
    observations: r.observations,
    skinAppearance: r.skinAppearance,
    recommendations: r.recommendations,
    routine: r.routine,
    limitations: r.limitations,
    professionalConsultationRecommended: r.professionalConsultationRecommended,
    professionalConsultationReason: r.professionalConsultationReason,
    photoStorageEnabled: r.photoStorageEnabled,
    photoUrl,
    feedback: r.feedback ? { ...r.feedback, createdAt: r.feedback.createdAt.toISOString() } : null,
  };
}

export function toApiHistoryItem(r: StoredAnalysis, photoUrl: string | null = null) {
  const summary = r.overallSummary.length > 180 ? `${r.overallSummary.slice(0, 177)}…` : r.overallSummary;
  return {
    analysisId: r.analysisId,
    createdAt: r.createdAt.toISOString(),
    status: r.status,
    overallSummary: summary,
    imageQualityStatus: r.imageQuality.status,
    observationCategories: [...new Set(r.observations.map((o) => o.category))],
    skinAppearance: r.skinAppearance,
    professionalConsultationRecommended: r.professionalConsultationRecommended,
    photoStorageEnabled: r.photoStorageEnabled,
    photoUrl,
  };
}

export function toApiProducts(analysisId: string, p: ProductSuggestions) {
  return {
    analysisId,
    summary: p.summary,
    products: p.products,
    generalTips: p.generalTips,
    sources: p.sources,
    searchGrounded: p.searchGrounded,
    disclaimer: p.disclaimer,
    createdAt: p.createdAt.toISOString(),
  };
}

const userOf = (req: Request) => {
  if (!req.user) throw Errors.unauthenticated();
  return req.user;
};

export function analysisController(service: AnalysisService) {
  return {
    create: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { storePhoto } = AnalyzeBody.parse(req.body ?? {});
      const record = await service.analyze(user, req.file!.buffer, storePhoto);
      // Drop our reference to the raw upload as soon as possible.
      req.file = undefined;
      res.status(201).json({
        success: true,
        data: { analysisId: record.analysisId, status: record.status, analysis: toApiAnalysis(record, await service.photoUrlFor(record)) },
      });
    },

    history: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { limit, cursor } = HistoryQuery.parse(req.query);
      const page = await service.list(user, limit, cursor);
      res.json({ success: true, data: { items: page.items.map((i) => toApiHistoryItem(i.record, i.photoUrl)), nextCursor: page.nextCursor } });
    },

    get: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { analysisId } = AnalysisIdParams.parse(req.params);
      const { record, photoUrl } = await service.get(user, analysisId);
      res.setHeader('Cache-Control', 'private, no-store');
      res.json({ success: true, data: toApiAnalysis(record, photoUrl) });
    },

    remove: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { analysisId } = AnalysisIdParams.parse(req.params);
      await service.delete(user, analysisId);
      res.json({ success: true, data: { analysisId, deleted: true } });
    },

    removePhoto: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { analysisId } = AnalysisIdParams.parse(req.params);
      await service.deletePhoto(user, analysisId);
      res.json({ success: true, data: { analysisId, photoDeleted: true } });
    },

    feedback: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { analysisId } = AnalysisIdParams.parse(req.params);
      const body = FeedbackBody.parse(req.body ?? {});
      await service.addFeedback(user, analysisId, body);
      res.status(201).json({ success: true, data: { analysisId, feedbackSaved: true } });
    },

    products: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { analysisId } = AnalysisIdParams.parse(req.params);
      const { refresh } = ProductsBody.parse(req.body ?? {});
      const suggestions = await service.findProducts(user, analysisId, refresh);
      res.json({ success: true, data: toApiProducts(analysisId, suggestions) });
    },
  };
}
