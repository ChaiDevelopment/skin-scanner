# AI Skin Scanner

Native Android app (Kotlin + Jetpack Compose) that takes a front-camera photo of your face, checks the framing on-device with ML Kit, and sends the photo to an Express + TypeScript backend. The backend asks **Google Gemini**, grounded with a dermatology reference, for a **structured visual skin observation** and gentle over-the-counter skincare suggestions. History is stored per user in Firebase.

> Ini adalah observasi visual berbasis AI, **bukan diagnosis medis**.

```
AI-SKIN-SCANNER/
├─ android/     Kotlin · Compose · Material 3 · CameraX · ML Kit · Hilt · Retrofit · Firebase Auth/Firestore/App Check
├─ backend/     Node.js · Express · TypeScript · @google/genai · firebase-admin · Zod · sharp · multer · pino
├─ firebase/    firestore.rules · storage.rules · firestore.indexes.json · firebase.json
├─ docs/        ARCHITECTURE · API_DOCUMENTATION · DATABASE_SCHEMA · FIREBASE_SETUP · GEMINI_SETUP · SECURITY · BUILD_APK
├─ scripts/     setup.ps1 · start-backend.ps1 · build-apk.ps1
└─ docker-compose.yml
```

## Quick start (Windows)
```powershell
.\scripts\setup.ps1            # checks Java/SDK/Node, creates config files, npm install
# edit backend\.env → GEMINI_API_KEY=...   (DEV_AUTH_BYPASS=true to test without Firebase)
.\scripts\start-backend.ps1    # http://localhost:3000/api/v1/health
.\scripts\build-apk.ps1        # → android\app\build\outputs\apk\debug\app-debug.apk
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```
* **Emulator:** the backend is at `http://10.0.2.2:3000/` (the default).
* **Physical phone:** set your PC's LAN IP in `android/config.properties` and rebuild. See [docs/BUILD_APK.md](docs/BUILD_APK.md).

## Configuration
| What | Where | Guide |
|---|---|---|
| Gemini key + model | `backend/.env` | [docs/GEMINI_SETUP.md](docs/GEMINI_SETUP.md) |
| Firebase Admin (service account) | `backend/.env` | [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) |
| Firebase Android config | `android/app/google-services.json` | [docs/FIREBASE_SETUP.md](docs/FIREBASE_SETUP.md) |
| Backend URL for the app | `android/config.properties` | [docs/BUILD_APK.md](docs/BUILD_APK.md) |

No secrets are committed; `.gitignore` covers `.env`, `service-account*.json`, `google-services.json`, `local.properties` and `config.properties`.

## Tests
```powershell
cd backend; npm test                    # 52 tests, Gemini mocked (no quota)
cd android; .\gradlew.bat testDebugUnitTest   # 44 JVM/Robolectric tests
npm run smoke:gemini                    # optional live Gemini check (uses quota)
```

## Features
* **Onboarding** (purpose, privacy, photo tips) → explicit consent → camera permission → direct-to-camera on later launches.
* **Front camera:**
  - live framing hints with an oval guide;
  - flash only when the lens has one;
  - lens switch;
  - capture → EXIF rotation, compression and size limits.
* **ML Kit validation:** no face, multiple faces, too far/small, outside the frame, head angle, too dark, overexposed, blurry.
* **Preview:** retake, plus a per-scan "store photo" choice (off by default).
* **Scanning:** staged progress, duplicate-submit guard, cancel, retry, network/timeout/quality/quota error states.
* **Result:**
  - summary, photo quality and ordinal appearance levels (pores, acne-like spots, comedones, texture, pigmentation, redness, dryness, shine);
  - observations with AI confidence and limitations;
  - ingredient-based recommendations with cautions, a morning/evening routine and a referral notice;
  - feedback, delete, delete photo.
* **History:** cursor pagination, pull to refresh, delete, long-press to compare two scans (per-attribute, with disclaimer).
* **Profile:** logout, permanent account deletion. **Settings:** default photo storage (synced to Firestore), privacy policy.
* **Design:** ivory / champagne / charcoal; serif display and sans-serif body; light and dark themes.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) and [docs/SECURITY.md](docs/SECURITY.md).
