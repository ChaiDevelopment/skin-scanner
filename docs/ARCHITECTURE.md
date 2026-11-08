# Architecture

```
┌──────────── Android (Kotlin, Compose, Hilt) ─────────────┐        ┌────────── Backend (Express, TS) ──────────┐
│ features/*  (Screens + ViewModels, StateFlow)            │        │ routes → middleware → controllers         │
│   camera ─ CameraX + ML Kit live hints                   │        │   requestId · helmet · CORS · rate limit  │
│   preview ─ FaceQualityRules (framing/light/blur)        │ HTTPS  │   requireAuth (Firebase ID token)         │
│   scanning ─ AnalyzeSkinUseCase ───────────────────────────────▶ │   appCheck · multer(memory) · Zod         │
│   result / history / compare / profile / settings        │ multip.│ services/                                 │
│ domain/  models, repository interfaces, use cases        │        │   image  ─ sharp (verify, rotate, strip)  │
│ data/    Retrofit DTOs+mappers, DataStore, Firebase Auth │        │   gemini ─ prompt + dermatology KB + schema│──▶ Google Gemini
│ core/    designsystem, network(auth interceptor), security│       │   analysis ─ orchestration, ownership     │
└──────────────────────────────────────────────────────────┘        │   firebase ─ Auth verify, Firestore, Storage│──▶ Firebase
          │ Firebase Auth (sign-in) · Firestore (profile, prefs)    └───────────────────────────────────────────┘
          ▼
       Firebase
```

## Key decisions
1. **All analysis data goes through the backend.** The app never writes analyses to Firestore directly. The backend:
   - validates Gemini's output;
   - writes the result with the Admin SDK;
   - enforces ownership on its own (client rules are not trusted as the only control).

   History is also read through the API. That keeps a single source of truth and lets the debug developer mode work without Firebase. The app uses the Firebase SDKs directly only for Auth, the `users/{uid}` profile and `preferences/privacy`.
2. **Two layers of validation:**
   - Gemini is constrained with `responseJsonSchema`;
   - the response is then parsed with Zod.

   The Android mappers accept unknown enum values and fall back to `unknown`/`other`, so a newer backend cannot crash an older app.
3. **Face detection ≠ skin analysis.** ML Kit only checks framing:
   - one face, size, inside the frame, head angle;
   - brightness, clipping and sharpness (variance of the Laplacian).

   The skin observation is done only by Gemini. No landmarks, classification or recognition are used.
4. **Dermatology grounding instead of fine-tuning.** A versioned reference text (`dermatology-knowledge.ts`) is injected as the system instruction, together with the strict non-diagnostic rules.
5. **No aggregate score.** Each attribute is an ordinal visual level with an `unknown` state. Comparisons work per attribute and always carry the lighting/positioning disclaimer.
6. **Privacy by default:**
   - photos are not stored unless the user opts in;
   - local copies are deleted after upload or retake;
   - metadata is stripped.
7. **Missing configuration is visible, not hidden:**
   - backend endpoints return `503 NOT_CONFIGURED`;
   - the app shows a "Firebase belum dikonfigurasi" notice.

   The only substitute is the explicit, debug-only dev mode (fixed token, in-memory store), which production refuses.

## Scan flow
```
Camera (front, CameraX) ─capture JPEG→ CapturedImageProcessor (EXIF rotate, ≤1600px, JPEG q90→70, ≤4MB)
  → FaceDetectorService.validate → ScanSession → Preview (issues/warnings, per-scan "store photo")
  → Scanning (single in-flight request, staged progress, cancel/retry) → POST /skin-analysis
  → backend: verify token → rate limit → multer → sharp → Gemini → Zod → Firestore (+Storage opt-in) → 201
  → Result (cached, no refetch) → History (cursor pages of 10) → Detail / Compare / Delete / Feedback
```

## Android module layout
`com.chai.aiskin`:

| Package | Contents |
|---|---|
| `core/common` | AppResult, AppError, dispatchers, date formatting |
| `core/constants` | limits and camera guidance text |
| `core/designsystem` | colours, type, theme, shared components |
| `core/network` | OkHttp/Retrofit, AuthInterceptor, error mapping |
| `core/security` | FirebaseStatus, App Check (debug/release source sets) |
| `data/local` | DataStore preferences |
| `data/remote` | API and DTOs |
| `data/model` | mappers |
| `data/repository` | Auth and Analysis repositories |
| `domain/model` | domain models |
| `domain/repository` | repository interfaces |
| `domain/usecase` | AnalyzeSkin, CompareAnalyses |
| `camera` | FrontCameraController |
| `detection` | FaceQualityRules, ImageQualityMetrics, FaceDetectorService, LiveFaceAnalyzer |
| `scanner` | CapturedImageProcessor, ScanSession, ScanStage |
| `features/*` | onboarding (incl. splash), auth, home, camera, preview, scanning, result, history (incl. compare), profile, settings (incl. privacy) |
| `navigation` | type-safe routes, NavHost with auth guard and consent gate |
| `di` | Hilt modules |

## Backend layout
`backend/src`:

| Folder | Contents |
|---|---|
| `config` | env (Zod) |
| `controllers` | analysis, system |
| `middleware` | auth, app-check, rate-limit, upload, request-id, error-handler |
| `routes` | route definitions |
| `schemas` | skin-analysis schema |
| `services/gemini` | service, prompt, dermatology-knowledge |
| `services/image` | image processing |
| `services/firebase` | admin init, adapters |
| `services/analysis` | service, types, memory-stores |
| `validators` | request validators |
| `utils` | errors, logger |
| `app.ts` / `server.ts` / `container.ts` | app factory / entry point / dependency wiring |

`createApp(deps)` takes every dependency as a parameter. Tests inject a mocked Gemini, in-memory stores and a fake token verifier. `container.ts` wires the real infrastructure.
