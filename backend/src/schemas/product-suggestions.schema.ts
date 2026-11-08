import { z } from 'zod';

/**
 * Contract for the product finder.
 *
 * Gemini returns snake_case JSON (validated here); the API stores and returns the
 * camelCase shape. Shop/search links are built by the server from the product name —
 * never taken from the model — so they cannot be hallucinated or point anywhere unexpected.
 */

export const ProductType = [
  'cleanser',
  'toner',
  'serum',
  'moisturizer',
  'sunscreen',
  'exfoliant',
  'spot_treatment',
  'other',
] as const;
export const ProductRoutineTime = ['morning', 'evening', 'both', 'weekly'] as const;

const shortText = (max: number) => z.string().trim().max(max);

export const GeminiProductSchema = z.object({
  name: shortText(120).min(2),
  brand: shortText(80).min(1),
  product_type: z.enum(ProductType).catch('other'),
  key_ingredients: z.array(shortText(60)).max(8).default([]),
  why_it_fits: shortText(400).min(5),
  how_to_use: shortText(300).default(''),
  routine_time: z.enum(ProductRoutineTime).catch('both'),
  price_range: shortText(60).default(''),
  cautions: shortText(300).default(''),
});

export const GeminiProductSuggestionsSchema = z.object({
  summary: shortText(500).min(5),
  products: z.array(GeminiProductSchema).min(1).max(8),
  general_tips: z.array(shortText(250)).max(5).default([]),
});
export type GeminiProductSuggestions = z.infer<typeof GeminiProductSuggestionsSchema>;

/** Used only when Google Search is unavailable (search grounding and JSON mode cannot be combined reliably). */
export const geminiProductJsonSchema = {
  type: 'object',
  additionalProperties: false,
  properties: {
    summary: { type: 'string', description: 'Ringkasan 1-2 kalimat dalam Bahasa Indonesia.' },
    products: {
      type: 'array',
      minItems: 1,
      maxItems: 6,
      items: {
        type: 'object',
        additionalProperties: false,
        properties: {
          name: { type: 'string' },
          brand: { type: 'string' },
          product_type: { type: 'string', enum: [...ProductType] },
          key_ingredients: { type: 'array', items: { type: 'string' } },
          why_it_fits: { type: 'string' },
          how_to_use: { type: 'string' },
          routine_time: { type: 'string', enum: [...ProductRoutineTime] },
          price_range: { type: 'string' },
          cautions: { type: 'string' },
        },
        required: ['name', 'brand', 'product_type', 'key_ingredients', 'why_it_fits', 'how_to_use', 'routine_time', 'price_range', 'cautions'],
      },
    },
    general_tips: { type: 'array', items: { type: 'string' } },
  },
  required: ['summary', 'products', 'general_tips'],
} as const;

export interface ProductSuggestion {
  name: string;
  brand: string;
  productType: (typeof ProductType)[number];
  keyIngredients: string[];
  whyItFits: string;
  howToUse: string;
  routineTime: (typeof ProductRoutineTime)[number];
  priceRange: string;
  cautions: string;
  /** Server-built links (never from the model). */
  searchUrl: string;
  shopUrl: string;
}

export interface ProductSource {
  title: string;
  url: string;
}

export interface ProductSuggestions {
  summary: string;
  products: ProductSuggestion[];
  generalTips: string[];
  /** Web pages Google Search used for this answer (empty when search was unavailable). */
  sources: ProductSource[];
  /** true = answer grounded with live Google Search; false = model knowledge only. */
  searchGrounded: boolean;
  disclaimer: string;
  model: string;
  createdAt: Date;
}

export const PRODUCT_DISCLAIMER =
  'Rekomendasi produk dibuat oleh AI dan bukan endorsement. Cek nomor BPOM di cekbpom.pom.go.id, baca komposisi, dan lakukan patch test sebelum memakai produk baru. Harga dan ketersediaan bisa berubah.';
export const NO_SEARCH_NOTICE =
  'Pencarian internet sedang tidak tersedia, jadi saran ini berdasarkan pengetahuan AI saja. Pastikan produk masih dijual dan formulanya sesuai.';

const query = (p: { brand: string; name: string }) => {
  const brand = p.brand.trim();
  const name = p.name.trim();
  // Avoid "Wardah Wardah UV Shield" when the model repeats the brand inside the name.
  return name.toLowerCase().startsWith(brand.toLowerCase()) ? name : `${brand} ${name}`;
};

export const googleSearchUrl = (p: { brand: string; name: string }) =>
  `https://www.google.com/search?q=${encodeURIComponent(`${query(p)} BPOM`)}`;
export const shopSearchUrl = (p: { brand: string; name: string }) =>
  `https://shopee.co.id/search?keyword=${encodeURIComponent(query(p))}`;

export function normalizeProducts(
  raw: GeminiProductSuggestions,
  meta: { sources: ProductSource[]; searchGrounded: boolean; model: string; now?: Date },
): ProductSuggestions {
  const seen = new Set<string>();
  const products: ProductSuggestion[] = [];
  for (const p of raw.products) {
    const key = query(p).toLowerCase();
    if (seen.has(key)) continue;
    seen.add(key);
    products.push({
      name: p.name,
      brand: p.brand,
      productType: p.product_type,
      keyIngredients: p.key_ingredients.filter((i) => i.length > 0),
      whyItFits: p.why_it_fits,
      howToUse: p.how_to_use,
      routineTime: p.routine_time,
      priceRange: p.price_range,
      cautions: p.cautions,
      searchUrl: googleSearchUrl(p),
      shopUrl: shopSearchUrl(p),
    });
  }
  return {
    summary: raw.summary,
    products: products.slice(0, 6),
    generalTips: raw.general_tips.filter((t) => t.length > 0),
    sources: meta.sources,
    searchGrounded: meta.searchGrounded,
    disclaimer: meta.searchGrounded ? PRODUCT_DISCLAIMER : `${NO_SEARCH_NOTICE} ${PRODUCT_DISCLAIMER}`,
    model: meta.model,
    createdAt: meta.now ?? new Date(),
  };
}
