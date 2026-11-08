# API documentation (v1)

**Base URL:** `http://localhost:3000/api/v1`. From the Android emulator: `http://10.0.2.2:3000/api/v1`.

**Authentication:** protected endpoints require `Authorization: Bearer <Firebase ID token>`. The user id is **always** taken from the verified token; any `uid` sent by the client is ignored. When `APP_CHECK_MODE` is `monitor` or `enforce`, the `X-Firebase-AppCheck` header is verified too.

**Request IDs:** every response carries `X-Request-Id`. Clients may send their own (8–64 characters, `[A-Za-z0-9-]`).

## Envelope
```json
{ "success": true,  "data": { } }
{ "success": false, "error": { "code": "ERROR_CODE", "message": "Pesan untuk pengguna", "details": {} }, "requestId": "…" }
```
Error responses never include stack traces or provider internals.

| Status | Code(s) | When |
|---|---|---|
| 400 | `INVALID_INPUT`, `INVALID_IMAGE` | bad params/body, resolution < 320 px |
| 401 | `UNAUTHENTICATED` | missing/invalid/revoked token, App Check failure (enforce) |
| 403 | `FORBIDDEN` | disallowed CORS origin; ownership mismatch |
| 404 | `NOT_FOUND` | unknown route or analysis not owned by caller |
| 413 | `IMAGE_TOO_LARGE` | upload > `MAX_IMAGE_SIZE_MB` |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | not JPEG/PNG/WebP (checked by declared type **and** real bytes) |
| 422 | `INSUFFICIENT_IMAGE_QUALITY` | Gemini judged the photo unusable (`details.imageQuality`) |
| 429 | `RATE_LIMITED` | per-user analysis limit or per-IP global limit |
| 500 | `INTERNAL_ERROR` | unexpected |
| 502 | `AI_PROVIDER_ERROR`, `AI_INVALID_RESPONSE` | Gemini failed / output invalid after retry |
| 503 | `AI_QUOTA_EXCEEDED`, `SERVICE_UNAVAILABLE`, `NOT_CONFIGURED` | quota, provider overloaded, missing server config |
| 504 | `AI_TIMEOUT` | Gemini exceeded `GEMINI_TIMEOUT_MS` |

---

### GET /health
Public. `{"success":true,"data":{"status":"ok","version":"1.0.0","uptimeSeconds":12,"time":"…"}}`

### GET /config
Public, non-secret client configuration.
```json
{ "success": true, "data": {
  "analysisVersion": "1.0", "maxImageSizeBytes": 10485760,
  "supportedMimeTypes": ["image/jpeg","image/png","image/webp"],
  "features": { "aiAnalysis": true, "authentication": true, "photoStorage": false, "devMode": false },
  "disclaimers": { "analysis": "…", "comparison": "…", "thirdParty": "…" } } }
```

### POST /skin-analysis
`Content-Type: multipart/form-data`

| Field | Type | Notes |
|---|---|---|
| `image` | file | required, JPEG/PNG/WebP, ≤ `MAX_IMAGE_SIZE_MB` |
| `storePhoto` | `"true"`/`"false"` | default false; stores the processed photo in Firebase Storage (if configured) |

Rate limit: `RATE_LIMIT_MAX_REQUESTS` per `RATE_LIMIT_WINDOW_MS` per user.

**201**
```json
{ "success": true, "data": { "analysisId": "uuid", "status": "completed", "analysis": {
  "analysisId": "uuid", "createdAt": "2026-11-08T09:00:00.000Z", "status": "completed",
  "analysisVersion": "1.0", "model": "gemini-3.5-flash",
  "overallSummary": "…",
  "imageQuality": { "status": "good|acceptable|poor", "lighting": "adequate|too_dark|too_bright|uneven",
                    "faceVisibility": "clear|partial|obstructed|unclear", "limitations": [] },
  "observations": [ { "category": "pores|acne_like_lesions|comedones|texture|pigmentation|redness|dryness_flaking|visible_shine|other",
                      "severity": "none|mild|moderate|pronounced|unknown", "confidence": 0.85,
                      "observation": "…", "affectedArea": "forehead|nose|cheeks|chin|jawline|under_eye|t_zone|full_face|unspecified" } ],
  "skinAppearance": { "texture": "smooth|slightly_uneven|uneven|unknown", "visibleRedness": "…", "visibleDryness": "…",
                      "pigmentation": "…", "visibleShine": "…" },
  "recommendations": [ { "category": "cleansing|moisturizing|sun_protection|targeted_care|lifestyle|professional_care",
                         "title": "…", "description": "…", "keyIngredients": ["niacinamide 2–5%"],
                         "routineTime": "morning|evening|both|weekly|as_needed", "priority": "high|medium|low", "cautions": "…" } ],
  "routine": { "morning": ["…"], "evening": ["…"] },
  "limitations": ["Ini adalah observasi visual berbasis AI, bukan diagnosis medis."],
  "professionalConsultationRecommended": false, "professionalConsultationReason": "",
  "photoStorageEnabled": false, "photoUrl": null, "feedback": null } } }
```
`severity` and `confidence` are approximate AI assessments, **not clinical measurements**. There is deliberately no aggregated "skin score".

**422**
```json
{ "success": false, "error": { "code": "INSUFFICIENT_IMAGE_QUALITY", "message": "…",
  "details": { "imageQuality": { "status": "poor", "lighting": "too_dark", "faceVisibility": "unclear", "limitations": ["…"] }, "limitations": [] } } }
```

### GET /skin-analysis/history?limit=10&cursor=<analysisId>
Returns newest first. `limit` is 1–50 (default 10). Pass `nextCursor` from the previous page; `null` means there are no more pages.
```json
{ "success": true, "data": { "items": [ { "analysisId": "…", "createdAt": "…", "status": "completed",
  "overallSummary": "≤180 chars", "imageQualityStatus": "good", "observationCategories": ["pores"],
  "skinAppearance": { }, "professionalConsultationRecommended": false, "photoStorageEnabled": false } ],
  "nextCursor": "uuid-or-null" } }
```

### GET /skin-analysis/:analysisId
Returns the full analysis (same shape as above). If a photo is stored, `photoUrl` is a signed URL valid for 10 minutes. `Cache-Control: private, no-store`. Responds 404 if the analysis does not exist or belongs to another user.

### DELETE /skin-analysis/:analysisId
Deletes the analysis **and** its stored photo. `{"success":true,"data":{"analysisId":"…","deleted":true}}`

### DELETE /skin-analysis/:analysisId/photo
Deletes only the stored photo; the text analysis remains.

### POST /skin-analysis/:analysisId/feedback
`application/json`: `{ "helpful": true, "accuracyRating": 4, "comment": "optional, ≤1000 chars" }`. Returns **201**.

### POST /skin-analysis/:analysisId/products
AI skincare product suggestions for one of the caller's analyses. Body: `{ "refresh": false }` (optional).
The result is cached on the analysis; `refresh: true` asks Gemini again. Per-user rate limit (separate budget from scans).

Gemini first tries **Google Search grounding**. If the key has no search quota (free tier → 429) the server falls back to
model knowledge only for 30 minutes and returns `searchGrounded: false`. `searchUrl`/`shopUrl` are built by the server
from the product name (never taken from the model). Brief sent to Gemini contains only the skin findings — no photo, uid or email.
```json
{ "success": true, "data": {
  "analysisId": "…", "summary": "…", "searchGrounded": false, "createdAt": "2026-11-08T10:00:00.000Z",
  "products": [ { "name": "Hydrasoothe Sunscreen Gel SPF 45 PA++++", "brand": "Azarine", "productType": "sunscreen",
    "keyIngredients": ["niacinamide"], "whyItFits": "…", "howToUse": "…", "routineTime": "morning",
    "priceRange": "Rp39.000–65.000", "cautions": "",
    "searchUrl": "https://www.google.com/search?q=…", "shopUrl": "https://shopee.co.id/search?keyword=…" } ],
  "generalTips": ["…"], "sources": [ { "title": "…", "url": "https://…" } ], "disclaimer": "…" } }
```
`productType`: cleanser | toner | serum | moisturizer | sunscreen | exfoliant | spot_treatment | other.
`routineTime`: morning | evening | both | weekly. Errors: 404 (not yours), 429, 502/503/504 (AI).

### POST /user/account-deletion
`application/json`: `{ "confirm": "DELETE" }`. Deletes, in order:
1. every stored photo;
2. `users/{uid}` with all its subcollections;
3. the Firebase Auth user.

This does not delete data a third-party provider (Google) may retain under its own policy.

## curl examples
```powershell
$T = "dev-local-token"   # DEV_AUTH_BYPASS only; otherwise a Firebase ID token
curl.exe -s http://localhost:3000/api/v1/health
curl.exe -s -H "Authorization: Bearer $T" -F "storePhoto=false" -F "image=@face.jpg;type=image/jpeg" http://localhost:3000/api/v1/skin-analysis
curl.exe -s -H "Authorization: Bearer $T" "http://localhost:3000/api/v1/skin-analysis/history?limit=10"
```
