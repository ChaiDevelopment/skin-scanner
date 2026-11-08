import type { Request, Response } from 'express';
import type { AnalysisService } from '../services/analysis/analysis.service';
import { AccountDeletionBody } from '../validators/analysis.validators';
import { ANALYSIS_VERSION, BASE_DISCLAIMER, COMPARISON_DISCLAIMER } from '../schemas/skin-analysis.schema';
import { SUPPORTED_MIME_TYPES } from '../services/image/image.service';
import { Errors } from '../utils/errors';

export interface SystemStatus {
  geminiConfigured: boolean;
  firebaseConfigured: boolean;
  photoStorageAvailable: boolean;
  devAuthBypass: boolean;
  maxImageBytes: number;
  version: string;
}

export function systemController(status: SystemStatus, service: AnalysisService) {
  return {
    health: (_req: Request, res: Response) => {
      res.json({
        success: true,
        data: { status: 'ok', version: status.version, uptimeSeconds: Math.round(process.uptime()), time: new Date().toISOString() },
      });
    },

    /** Public, non-secret client configuration. */
    config: (_req: Request, res: Response) => {
      res.json({
        success: true,
        data: {
          analysisVersion: ANALYSIS_VERSION,
          maxImageSizeBytes: status.maxImageBytes,
          supportedMimeTypes: SUPPORTED_MIME_TYPES,
          features: {
            aiAnalysis: status.geminiConfigured,
            authentication: status.firebaseConfigured || status.devAuthBypass,
            photoStorage: status.photoStorageAvailable,
            devMode: status.devAuthBypass,
          },
          disclaimers: {
            analysis: BASE_DISCLAIMER,
            comparison: COMPARISON_DISCLAIMER,
            thirdParty:
              'Foto Anda dikirim ke Google Gemini untuk diproses. Menghapus data di aplikasi ini tidak otomatis menghapus data yang mungkin disimpan sementara oleh penyedia pihak ketiga sesuai kebijakan mereka.',
          },
        },
      });
    },

    deleteAccount: async (req: Request, res: Response) => {
      if (!req.user) throw Errors.unauthenticated();
      AccountDeletionBody.parse(req.body ?? {});
      await service.deleteAccount(req.user);
      res.json({ success: true, data: { deleted: true } });
    },
  };
}
