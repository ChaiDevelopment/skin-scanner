import type { Request, Response } from 'express';
import type { AnalysisService } from '../services/analysis/analysis.service';
import type { StoredAnalysis } from '../services/analysis/types';
import { AnalysisIdParams, AnalyzeBody, FeedbackBody, HistoryQuery } from '../validators/analysis.validators';
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

export function toApiHistoryItem(r: StoredAnalysis) {
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
        data: { analysisId: record.analysisId, status: record.status, analysis: toApiAnalysis(record) },
      });
    },

    history: async (req: Request, res: Response) => {
      const user = userOf(req);
      const { limit, cursor } = HistoryQuery.parse(req.query);
      const page = await service.list(user, limit, cursor);
      res.json({ success: true, data: { items: page.items.map(toApiHistoryItem), nextCursor: page.nextCursor } });
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
  };
}
