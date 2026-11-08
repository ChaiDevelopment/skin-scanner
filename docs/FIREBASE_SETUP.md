# Firebase setup

No real Firebase credentials ship with this repository. Without them:
* the **Android app** builds and runs, and the login screen shows *"Firebase belum dikonfigurasi"*. Debug builds offer an explicit **local developer mode**.
* the **backend** returns `503 NOT_CONFIGURED` on protected endpoints, unless `DEV_AUTH_BYPASS=true` (development only, refused in production).

## 1. Create the project
1. Open <https://console.firebase.google.com> → **Add project**.
2. Google Analytics is optional.

## 2. Register the Android app
1. Go to **Project settings → Your apps → Android**.
2. Package name: `com.chai.aiskin`.
3. SHA‑1 is needed for App Check / Play Integrity. Get it with:
   ```powershell
   keytool -list -v -keystore "$env:USERPROFILE\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android
   ```
4. Download **`google-services.json`** to `android/app/google-services.json`. It is gitignored.
5. Rebuild. The Gradle build applies the Google Services plugin automatically when the file exists.

## 3. Authentication
**Build → Authentication → Sign-in method → Email/Password → Enable.**
The app supports registration, login, logout, password reset (Firebase email) and account deletion (through the backend).

## 4. Firestore
1. **Build → Firestore Database → Create database** (production mode). Choose a region close to your users, e.g. `asia-southeast2` (Jakarta).
2. Data layout: see `docs/DATABASE_SCHEMA.md`.

## 5. Storage (optional photo retention)
1. **Build → Storage → Get started**.
2. Copy the bucket name (e.g. `your-project.firebasestorage.app`) into `FIREBASE_STORAGE_BUCKET` in `backend/.env`.
3. Without a bucket, photo storage is disabled and photos are never kept.

## 6. Backend service account
1. **Project settings → Service accounts → Generate new private key.**
2. Keep the JSON **outside the repo**, or in a `service-account*.json` file (gitignored).
3. Configure `backend/.env` with **one** of these options:
```env
# Option A
FIREBASE_PROJECT_ID=your-project
FIREBASE_CLIENT_EMAIL=firebase-adminsdk-xxx@your-project.iam.gserviceaccount.com
FIREBASE_PRIVATE_KEY="-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----\n"
# Option B
GOOGLE_APPLICATION_CREDENTIALS=C:\secrets\aiskin-service-account.json
```
4. Set `DEV_AUTH_BYPASS=false` once Firebase is configured. When credentials are present, dev bypass is ignored anyway.

## 7. App Check
1. **Build → App Check → Apps → Android → Play Integrity** → register.
2. Debug builds use the debug provider:
   - run the app once;
   - find `DebugAppCheckProvider: Enter this debug secret into the allow list` in Logcat;
   - add that token under **App Check → Manage debug tokens**.
3. The backend verifies `X-Firebase-AppCheck` according to `APP_CHECK_MODE`:
   - `off` (default) → `monitor` (logs only) → `enforce` (blocks).
   - Switch to `enforce` only after the debug and release apps send valid tokens.
4. For direct client access to Firestore/Storage, optionally enable App Check enforcement in the console as well.

## 8. Deploy security rules
```powershell
npm install -g firebase-tools
firebase login
cd firebase
firebase use --add            # select your project
firebase deploy --only firestore:rules,firestore:indexes,storage
```
* Users can read only `users/{theirUid}/**`.
* Analyses can be created, updated and deleted **only by the backend** (Admin SDK), which guarantees validated content and photo cleanup.
* Storage allows owners to read/delete `users/{uid}/scans/*`; direct client writes are blocked.

## 9. Verify
```powershell
.\scripts\start-backend.ps1        # log should say "Using Firebase Auth + Firestore"
.\scripts\build-apk.ps1            # then install, register, scan
```
