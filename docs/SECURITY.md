# Security & privacy

## Threat model highlights
* **Sensitive data:** facial photos. They are biometric-adjacent and privacy-sensitive.
* **Secrets:**
  - the Gemini API key (server only);
  - the Firebase service account (server only).
  The Android `google-services.json` identifies the app but is not a server secret. It is still gitignored.
* **Main risks:**
  - quota abuse of the AI endpoint;
  - cross-user data access;
  - photo leakage through logs or storage;
  - over-trusting AI output.

## Controls implemented
| Area | Control | Where |
|---|---|---|
| API key | Only in `backend/.env`; never in the APK, BuildConfig, resources, responses or logs | `config/env.ts`, `utils/logger.ts` |
| AuthN | Firebase ID token verified with `checkRevoked=true` | `services/firebase/firebase-adapters.ts` |
| AuthZ | uid taken only from the token; all store calls scoped to `users/{uid}`; stored owner re-checked; other users' ids → 404 | `analysis.service.ts` |
| Dev bypass | `DEV_AUTH_BYPASS` refused when `NODE_ENV=production`, ignored when Firebase is configured, debug-APK only on the client | `config/env.ts`, `container.ts`, `BuildConfig.DEV_MODE_ALLOWED` |
| App Check | `X-Firebase-AppCheck` verified in `monitor`/`enforce` mode; Play Integrity in release builds, debug provider in debug | `middleware/app-check.ts`, `AppCheckInstaller` |
| Upload | multer memory storage (no temp files), 1 file, size limit, MIME allow-list, real-bytes check with sharp, min resolution, pixel bomb limit | `middleware/upload.ts`, `services/image/image.service.ts` |
| Metadata | Photos re-encoded server-side (EXIF/GPS stripped) and on device | `image.service.ts`, `CapturedImageProcessor` |
| Input validation | Zod for params (UUID ids), query, bodies (strict), env | `validators/`, `config/env.ts` |
| AI output | JSON schema + Zod: closed enums, unknown keys stripped, lengths capped, no aggregate score | `schemas/skin-analysis.schema.ts` |
| Rate limiting | 120 req/min per IP globally; `RATE_LIMIT_MAX_REQUESTS` per user per window on analysis | `middleware/rate-limit.ts` |
| Headers | helmet; `x-powered-by` disabled; `Cache-Control: no-store` on analysis details | `app.ts` |
| CORS | No browser origins allowed unless listed in `CORS_ORIGIN` (native apps send none) | `app.ts` |
| Errors | Uniform envelope, no stack traces or provider messages to clients | `middleware/error-handler.ts` |
| Logging | Structured pino logs; authorization/App Check headers, keys, tokens and image buffers redacted; bodies never logged; request ids | `utils/logger.ts`, `app.ts` |
| Firestore rules | Owner-only reads; analyses writable only by the backend; deny-all fallback | `firebase/firestore.rules` |
| Storage rules | Owner-only read/delete of `users/{uid}/scans/*`; client writes denied | `firebase/storage.rules` |
| Transport | Release: HTTPS-only network security config and HTTPS base URL enforced at build time. Debug: cleartext allowed for local dev | `res/xml/network_security_config.xml`, `app/build.gradle.kts` |
| Device | `allowBackup=false`, backup/device-transfer exclusions, photos only in app-private cache and deleted after upload/retake | `AndroidManifest.xml`, `ScanSession` |
| Retention | Photos not stored by default; opt-in per scan and as a default; delete photo / analysis / account | app + API |
| Third party | Users told the photo goes to Google Gemini; deletion does not reach the provider's own retention | onboarding, privacy screen, `/config` |
| Medical safety | Non-diagnostic prompt rules, OTC-only advice, referral rules, disclaimers on every result and comparison | `skin-analysis.prompt.ts`, `dermatology-knowledge.ts` |

## Security checklist before production
- [ ] Rotate any API key that was ever shared in chat/email; restrict it to the Generative Language API.
- [ ] `NODE_ENV=production`, `DEV_AUTH_BYPASS=false`.
- [ ] Serve the API over HTTPS only (reverse proxy / Cloud Run); set `TRUST_PROXY=true` behind a proxy.
- [ ] Set `api.baseUrl.release` to the HTTPS URL; configure release signing.
- [ ] Deploy `firestore.rules`, `storage.rules`, indexes (`docs/FIREBASE_SETUP.md`).
- [ ] App Check: register Play Integrity; move `APP_CHECK_MODE` from `monitor` to `enforce`.
- [ ] Store the service account in a secret manager, not on disk in the repo.
- [ ] Review `RATE_LIMIT_*` and set a Gemini budget alert in Google Cloud Billing.
- [ ] Confirm the Gemini data-use terms of your plan (paid tiers are not used for training; check the current terms).
- [ ] Publish a privacy policy that matches docs/DATABASE_SCHEMA.md and the in-app privacy screen.
- [ ] `npm audit` and dependency updates; Android lint.
- [ ] Regulatory review: in Indonesia, facial images are personal data under the PDP law (UU 27/2022). Make sure consent and deletion flows meet it.
