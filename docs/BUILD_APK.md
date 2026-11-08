# Building and installing the APK

## Toolchain (verified on this machine)
* JDK 17 (Temurin 17.0.20)
* Android SDK platform 35 and build-tools 35.0.0
* Gradle wrapper 8.11.1, AGP 8.7.2
* Kotlin 2.0.21 and KSP 2.0.21-1.0.28
* minSdk 26 (Android 8.0), targetSdk 35

## Build
```powershell
.\scripts\setup.ps1          # one time: checks tools, creates local.properties/config.properties
.\scripts\build-apk.ps1      # debug APK
.\scripts\build-apk.ps1 -Test   # run unit tests first
```
Output: **`android\app\build\outputs\apk\debug\app-debug.apk`**

To build directly:
```powershell
cd android
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

### Low disk space on C:
Gradle keeps several GB in `%USERPROFILE%\.gradle`. If C: is full, you will see errors like `Problems writing to Binary store`. Fix it by moving Gradle's working files to another drive; existing downloads are reused read-only:
```powershell
.\scripts\build-apk.ps1 -GradleHome D:\.gradle-aiskin
```

## Point the app at the backend
Set the URL in `android/config.properties` (not a secret, and gitignored):
```properties
api.baseUrl.debug=http://10.0.2.2:3000/        # Android Emulator → host PC
# Physical phone on the same Wi-Fi: use the PC's LAN IP, e.g.
# api.baseUrl.debug=http://192.168.1.20:3000/
api.baseUrl.release=https://api.example.com/   # must be HTTPS
```
* The emulator reaches the host at **10.0.2.2**. `localhost` inside the emulator is the emulator itself.
* For a physical device:
  - find the PC's IP with `ipconfig`;
  - allow Node.js through Windows Defender Firewall (private network);
  - make sure both devices share the same network.
* Debug builds allow cleartext HTTP for local development. **Release builds are HTTPS-only.**

Rebuild after changing the URL.

## Install
```powershell
adb devices
adb install -r android\app\build\outputs\apk\debug\app-debug.apk
```
Alternatively, copy the APK to the phone and open it (allow "Install unknown apps").

## Emulator (optional)
No emulator is installed on the current machine. To add one:
```powershell
sdkmanager "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n aiskin -k "system-images;android-35;google_apis;x86_64" -d pixel_7
emulator -avd aiskin -camera-front webcam0
```
`-camera-front webcam0` maps your PC webcam to the emulator's front camera.

## Manual integration test (real Gemini key)
1. Fill in `backend/.env`:
   - `GEMINI_API_KEY`;
   - either `DEV_AUTH_BYPASS=true` (no Firebase) or the Firebase Admin credentials.
2. Run `.\scripts\start-backend.ps1`. Check `http://localhost:3000/api/v1/health`.
3. Install the APK. Complete onboarding, accept consent and grant camera permission.
4. Sign in:
   - **without Firebase:** tap *"Lanjutkan dengan mode pengembang lokal"* (debug builds only);
   - **with Firebase:** register an account.
5. Capture a well-lit selfie and check the preview validation messages. Tap **Analisis foto ini**. Expect results in about 10–40 s.
6. Verify each of the following:
   - the result shows the summary, quality, appearance rows, observations, recommendations, routine and limitations;
   - **Riwayat** lists the scan;
   - opening it shows the detail; deleting it removes it;
   - long-press two scans → **Bandingkan** shows the comparison and its disclaimer;
   - a covered or dark photo gives the *"Kualitas foto belum cukup"* screen (HTTP 422);
   - airplane mode during a scan gives *"Koneksi terputus"* with retry.
7. With Firebase configured, also check:
   - Firestore → `users/{uid}/skin_analyses` contains the document;
   - with **Simpan foto** on, Storage → `users/{uid}/scans/` contains the photo;
   - deleting the analysis removes both.

## Instrumented tests (needs emulator/device)
```powershell
cd android; .\gradlew.bat connectedDebugAndroidTest
```
