import { z } from 'zod';

/**
 * Contract for the JSON that Gemini must return (snake_case), plus the
 * normalized camelCase shape the API stores and returns.
 *
 * - Enums are closed; unknown enum values fail validation.
 * - Unknown object keys are stripped (never persisted or returned).
 * - Severity/confidence are approximate AI assessments, NOT clinical measurements.
 * - There is deliberately no aggregated "skin health score".
 */

export const ANALYSIS_VERSION = '1.0';

export const AnalysisStatus = ['success', 'insufficient_image_quality'] as const;
export const ImageQualityStatus = ['good', 'acceptable', 'poor'] as const;
export const Lighting = ['adequate', 'too_dark', 'too_bright', 'uneven'] as const;
export const FaceVisibility = ['clear', 'partial', 'obstructed', 'unclear'] as const;
export const ObservationCategory = [
  'pores',
  'acne_like_lesions',
  'comedones',
  'texture',
  'pigmentation',
  'redness',
  'dryness_flaking',
  'visible_shine',
  'other',
] as const;
/** Visual prominence on an ordinal scale. "unknown" = not assessable from this photo. */
export const Severity = ['none', 'mild', 'moderate', 'pronounced', 'unknown'] as const;
export const FaceArea = [
  'forehead',
  'nose',
  'cheeks',
  'chin',
  'jawline',
  'under_eye',
  't_zone',
  'full_face',
  'unspecified',
] as const;
export const Texture = ['smooth', 'slightly_uneven', 'uneven', 'unknown'] as const;
export const RecommendationCategory = [
  'cleansing',
  'moisturizing',
  'sun_protection',
  'targeted_care',
  'lifestyle',
  'professional_care',
] as const;
export const RoutineTime = ['morning', 'evening', 'both', 'weekly', 'as_needed'] as const;
export const Priority = ['high', 'medium', 'low'] as const;

/** Over-long model text is truncated (not rejected) so a verbose answer doesn't fail the whole scan. */
const text = (max: number) => z.string().transform((s) => truncate(s.trim(), max));
const requiredText = (max: number) => z.string().trim().min(1).transform((s) => truncate(s, max));
const shortList = (maxItems: number, maxLen: number) =>
  z.array(z.string()).default([]).transform((a) => a.map((s) => truncate(s.trim(), maxLen)).filter(Boolean).slice(0, maxItems));
const cappedArray = <T extends z.ZodTypeAny>(item: T, maxItems: number) =>
  z.array(item).default([]).transform((a) => a.slice(0, maxItems));

function truncate(s: string, max: number): string {
  return s.length <= max ? s : `${s.slice(0, max - 1).trimEnd()}…`;
}

// ---------- Raw Gemini output (snake_case) ----------

export const GeminiImageQualitySchema = z.object({
  status: z.enum(ImageQualityStatus),
  lighting: z.enum(Lighting),
  face_visibility: z.enum(FaceVisibility),
  limitations: shortList(8, 300),
});

export const GeminiObservationSchema = z.object({
  category: z.enum(ObservationCategory),
  severity: z.enum(Severity),
  confidence: z.number().min(0).max(1),
  observation: requiredText(600),
  affected_area: z.enum(FaceArea),
});

export const GeminiSkinAppearanceSchema = z.object({
  texture: z.enum(Texture),
  visible_redness: z.enum(Severity),
  visible_dryness: z.enum(Severity),
  pigmentation: z.enum(Severity),
  visible_shine: z.enum(Severity),
});

export const GeminiRecommendationSchema = z.object({
  category: z.enum(RecommendationCategory),
  title: requiredText(120),
  description: requiredText(800),
  key_ingredients: shortList(8, 80),
  routine_time: z.enum(RoutineTime),
  priority: z.enum(Priority),
  cautions: text(500).default(''),
});

export const GeminiRoutineSchema = z.object({
  morning: shortList(8, 200),
  evening: shortList(8, 200),
});

export const GeminiSkinAnalysisSchema = z.object({
  status: z.enum(AnalysisStatus),
  overall_summary: text(1200),
  image_quality: GeminiImageQualitySchema,
  skin_observations: cappedArray(GeminiObservationSchema, 16),
  skin_appearance: GeminiSkinAppearanceSchema,
  recommendations: cappedArray(GeminiRecommendationSchema, 10),
  routine: GeminiRoutineSchema.default({ morning: [], evening: [] }),
  limitations: shortList(10, 400),
  professional_consultation_recommended: z.boolean(),
  professional_consultation_reason: text(600).default(''),
});

export type GeminiSkinAnalysis = z.infer<typeof GeminiSkinAnalysisSchema>;

/**
 * JSON Schema handed to Gemini (`responseJsonSchema`) so the model is
 * constrained to the same shape that Zod validates afterwards.
 */
export const geminiResponseJsonSchema = {
  type: 'object',
  additionalProperties: false,
  properties: {
    status: { type: 'string', enum: [...AnalysisStatus] },
    overall_summary: { type: 'string', description: 'Ringkasan observasi visual dalam Bahasa Indonesia, 2-4 kalimat.' },
    image_quality: {
      type: 'object',
      additionalProperties: false,
      properties: {
        status: { type: 'string', enum: [...ImageQualityStatus] },
        lighting: { type: 'string', enum: [...Lighting] },
        face_visibility: { type: 'string', enum: [...FaceVisibility] },
        limitations: { type: 'array', items: { type: 'string' } },
      },
      required: ['status', 'lighting', 'face_visibility', 'limitations'],
    },
    skin_observations: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        properties: {
          category: { type: 'string', enum: [...ObservationCategory] },
          severity: { type: 'string', enum: [...Severity] },
          confidence: { type: 'number', minimum: 0, maximum: 1 },
          observation: { type: 'string' },
          affected_area: { type: 'string', enum: [...FaceArea] },
        },
        required: ['category', 'severity', 'confidence', 'observation', 'affected_area'],
      },
    },
    skin_appearance: {
      type: 'object',
      additionalProperties: false,
      properties: {
        texture: { type: 'string', enum: [...Texture] },
        visible_redness: { type: 'string', enum: [...Severity] },
        visible_dryness: { type: 'string', enum: [...Severity] },
        pigmentation: { type: 'string', enum: [...Severity] },
        visible_shine: { type: 'string', enum: [...Severity] },
      },
      required: ['texture', 'visible_redness', 'visible_dryness', 'pigmentation', 'visible_shine'],
    },
    recommendations: {
      type: 'array',
      items: {
        type: 'object',
        additionalProperties: false,
        properties: {
          category: { type: 'string', enum: [...RecommendationCategory] },
          title: { type: 'string' },
          description: { type: 'string' },
          key_ingredients: { type: 'array', items: { type: 'string' } },
          routine_time: { type: 'string', enum: [...RoutineTime] },
          priority: { type: 'string', enum: [...Priority] },
          cautions: { type: 'string' },
        },
        required: ['category', 'title', 'description', 'key_ingredients', 'routine_time', 'priority', 'cautions'],
      },
    },
    routine: {
      type: 'object',
      additionalProperties: false,
      properties: {
        morning: { type: 'array', items: { type: 'string' } },
        evening: { type: 'array', items: { type: 'string' } },
      },
      required: ['morning', 'evening'],
    },
    limitations: { type: 'array', items: { type: 'string' } },
    professional_consultation_recommended: { type: 'boolean' },
    professional_consultation_reason: { type: 'string' },
  },
  required: [
    'status',
    'overall_summary',
    'image_quality',
    'skin_observations',
    'skin_appearance',
    'recommendations',
    'routine',
    'limitations',
    'professional_consultation_recommended',
    'professional_consultation_reason',
  ],
} as const;

// ---------- Normalized API / Firestore shape (camelCase) ----------

export interface ImageQuality {
  status: (typeof ImageQualityStatus)[number];
  lighting: (typeof Lighting)[number];
  faceVisibility: (typeof FaceVisibility)[number];
  limitations: string[];
}

export interface Observation {
  category: (typeof ObservationCategory)[number];
  severity: (typeof Severity)[number];
  /** Approximate model self-assessment, 0..1, rounded to 0.05. Not a clinical measurement. */
  confidence: number;
  observation: string;
  affectedArea: (typeof FaceArea)[number];
}

export interface SkinAppearance {
  texture: (typeof Texture)[number];
  visibleRedness: (typeof Severity)[number];
  visibleDryness: (typeof Severity)[number];
  pigmentation: (typeof Severity)[number];
  visibleShine: (typeof Severity)[number];
}

export interface Recommendation {
  category: (typeof RecommendationCategory)[number];
  title: string;
  description: string;
  keyIngredients: string[];
  routineTime: (typeof RoutineTime)[number];
  priority: (typeof Priority)[number];
  cautions: string;
}

export interface SkinAnalysisResult {
  analysisVersion: string;
  overallSummary: string;
  imageQuality: ImageQuality;
  observations: Observation[];
  skinAppearance: SkinAppearance;
  recommendations: Recommendation[];
  routine: { morning: string[]; evening: string[] };
  limitations: string[];
  professionalConsultationRecommended: boolean;
  professionalConsultationReason: string;
}

export const BASE_DISCLAIMER = 'Ini adalah observasi visual berbasis AI, bukan diagnosis medis.';
export const COMPARISON_DISCLAIMER =
  'Perbedaan antar foto dapat disebabkan oleh pencahayaan, pengaturan kamera, dan posisi wajah — bukan bukti perubahan klinis.';

const roundConfidence = (c: number) => Math.round(Math.min(1, Math.max(0, c)) * 20) / 20;

/** Converts validated Gemini output into the stored/returned shape and applies safety normalizations. */
export function normalizeAnalysis(raw: GeminiSkinAnalysis): SkinAnalysisResult {
  const limitations = [...raw.limitations];
  if (!limitations.some((l) => l.toLowerCase().includes('bukan diagnosis'))) {
    limitations.unshift(BASE_DISCLAIMER);
  }
  return {
    analysisVersion: ANALYSIS_VERSION,
    overallSummary: raw.overall_summary,
    imageQuality: {
      status: raw.image_quality.status,
      lighting: raw.image_quality.lighting,
      faceVisibility: raw.image_quality.face_visibility,
      limitations: raw.image_quality.limitations,
    },
    observations: raw.skin_observations.map((o) => ({
      category: o.category,
      severity: o.severity,
      confidence: roundConfidence(o.confidence),
      observation: o.observation,
      affectedArea: o.affected_area,
    })),
    skinAppearance: {
      texture: raw.skin_appearance.texture,
      visibleRedness: raw.skin_appearance.visible_redness,
      visibleDryness: raw.skin_appearance.visible_dryness,
      pigmentation: raw.skin_appearance.pigmentation,
      visibleShine: raw.skin_appearance.visible_shine,
    },
    recommendations: raw.recommendations.map((r) => ({
      category: r.category,
      title: r.title,
      description: r.description,
      keyIngredients: r.key_ingredients,
      routineTime: r.routine_time,
      priority: r.priority,
      cautions: r.cautions,
    })),
    routine: raw.routine,
    limitations,
    professionalConsultationRecommended: raw.professional_consultation_recommended,
    professionalConsultationReason: raw.professional_consultation_reason,
  };
}
