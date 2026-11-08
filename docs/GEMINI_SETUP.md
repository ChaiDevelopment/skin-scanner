# Gemini setup

The Gemini API key lives **only** in `backend/.env` on the server. The Android app never sees it.

## 1. Get a key
1. Open <https://aistudio.google.com/apikey> and create an API key.
2. Put it in `backend/.env`:

```env
GEMINI_API_KEY=your-key
GEMINI_MODEL=gemini-3.5-flash
GEMINI_MAX_OUTPUT_TOKENS=8192
GEMINI_TIMEOUT_MS=90000
# Optional, model-specific: GEMINI_THINKING_BUDGET=1024
```

## 2. Choosing the model
`GEMINI_MODEL` is configurable. Facts verified on 2026‑11‑08 with this project's key:

| Model | Result |
|---|---|
| `gemini-2.5-flash` | **404**: "no longer available to new users" |
| `gemini-3.5-flash` | 200, ~1.7 s for a tiny prompt, ~7–8 s for a face analysis → **default** |
| `gemini-3.1-flash-lite` | 200, ~1.4 s (cheaper, lower quality) |
| `gemini-3.8-flash` | 200 but **107–169 s** latency (overloaded at test time) |
| `gemini-flash-latest` | 503 at test time |

List what your key can use:
```powershell
curl.exe -s -H "x-goog-api-key: $env:GEMINI_API_KEY" "https://generativelanguage.googleapis.com/v1beta/models?pageSize=100"
```

## 3. How the integration works
* `backend/src/services/gemini/gemini.service.ts` sends the **actual photo** (re-encoded JPEG, metadata stripped) as `inlineData` with the official `@google/genai` SDK.
* `responseMimeType: application/json` and `responseJsonSchema` force structured output. The response is then validated again with Zod (`backend/src/schemas/skin-analysis.schema.ts`):
  * closed enums: an invalid enum fails validation;
  * unknown keys are stripped;
  * over-long text and lists are truncated;
  * `confidence` is limited to 0–1 and rounded to 0.05.
* **Retries:** one retry for invalid JSON (with a correction prompt) and one for a transient 5xx. 429 (quota) and timeouts are not retried.
* **Error mapping:** 429 → `503 AI_QUOTA_EXCEEDED`, timeout → `504 AI_TIMEOUT`, provider 503 → `503 SERVICE_UNAVAILABLE`, other failures → `502`.
* **Insufficient photos:** if the photo is unusable, Gemini returns `insufficient_image_quality` and the API answers **422**. Nothing is saved in that case.

## 4. "Dermatologist-trained" behaviour
Gemini cannot be fine-tuned through this API, so it is **grounded** instead. Every request carries a curated dermatology reference (`backend/src/services/gemini/dermatology-knowledge.ts`, versioned as `KNOWLEDGE_VERSION`). It covers:
* the visual morphology vocabulary (open/closed comedones, papules, post-inflammatory marks, and so on);
* confounders such as lighting, filters, makeup and skin-tone diversity;
* the severity wording;
* core skincare principles;
* an OTC ingredient guide with typical concentrations and cautions (niacinamide, BHA, benzoyl peroxide, azelaic acid, retinol, AHA, vitamin C, tranexamic acid, barrier lipids);
* routine templates;
* Indonesian context: tropical UV, BPOM registration, unregistered "cream racikan";
* when to refer to a dermatologist (Sp.DVE), including ABCDE warning signs;
* hard prohibitions: no diagnosis, no prescription drugs, no brands, no claimed measurements.

The model answers in Bahasa Indonesia and recommends **ingredients and routines, not brands**. Every analysis stores the model name and the knowledge version.

## 5. Testing
* Automated tests use **mocked** Gemini responses: `npm test`, no quota used.
* Manual live check (uses a small amount of quota):
```powershell
cd backend
npm run smoke:gemini                       # synthetic non-face image → expects insufficient_image_quality
npm run smoke:gemini -- C:\path\face.jpg   # your own photo → full analysis
```
* Live end-to-end test through HTTP: see *Manual integration test* in `docs/BUILD_APK.md`.

## 6. Key hygiene
* Never commit `.env` (it is gitignored).
* Rotate the key in AI Studio if it was ever pasted into chat, email, or a ticket.
* Restrict the key to the Generative Language API in Google Cloud Console → Credentials.
