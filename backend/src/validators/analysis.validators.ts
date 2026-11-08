import { z } from 'zod';

/** Our own generated ids are UUID v4; reject anything else so ids can't be used for path tricks. */
export const AnalysisIdParams = z.object({
  analysisId: z.string().uuid({ message: 'analysisId tidak valid.' }),
});

export const HistoryQuery = z.object({
  limit: z.coerce.number().int().min(1).max(50).default(10),
  cursor: z.string().uuid().optional(),
});

/** multipart text fields arrive as strings. */
export const AnalyzeBody = z.object({
  storePhoto: z
    .union([z.literal('true'), z.literal('false'), z.literal('1'), z.literal('0'), z.boolean()])
    .optional()
    .transform((v) => v === true || v === 'true' || v === '1'),
});

export const FeedbackBody = z
  .object({
    helpful: z.boolean(),
    accuracyRating: z.number().int().min(1).max(5).optional(),
    comment: z.string().trim().max(1000).optional(),
  })
  .strict();

export const ProductsBody = z
  .object({ refresh: z.boolean().optional().default(false) })
  .strict();

export const AccountDeletionBody = z
  .object({
    confirm: z.literal('DELETE', { errorMap: () => ({ message: 'Ketik DELETE untuk konfirmasi.' }) }),
  })
  .strict();
