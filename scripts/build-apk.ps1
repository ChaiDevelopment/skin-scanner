<#
.SYNOPSIS
  Builds the AI Skin Scanner Android APK.
.PARAMETER Release
  Build the release variant instead of debug (requires your own signing config).
.PARAMETER Test
  Run JVM unit tests before building.
.PARAMETER GradleHome
  Keep Gradle's caches/temp files on another drive (e.g. D:\.gradle-aiskin) when C: is low on space.
  Existing downloads in %USERPROFILE%\.gradle are reused read-only.
.EXAMPLE
  .\scripts\build-apk.ps1
  .\scripts\build-apk.ps1 -Test
  .\scripts\build-apk.ps1 -GradleHome D:\.gradle-aiskin
#>
param([switch]$Release, [switch]$Test, [string]$GradleHome)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$Android = Join-Path $Root 'android'

if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Write-Host "Java 17 not found. Install from https://adoptium.net" -ForegroundColor Red; exit 1 }
if (-not (Test-Path (Join-Path $Android 'local.properties')) -and -not $env:ANDROID_HOME) {
    Write-Host "Android SDK location unknown. Run scripts\setup.ps1 or set ANDROID_HOME." -ForegroundColor Red; exit 1
}
if (-not (Test-Path (Join-Path $Android 'config.properties'))) {
    Copy-Item (Join-Path $Android 'config.properties.example') (Join-Path $Android 'config.properties')
    Write-Host "Created android/config.properties (backend URL http://10.0.2.2:3000/ for the emulator)."
}
if (-not (Test-Path (Join-Path $Android 'app\google-services.json'))) {
    Write-Host "NOTE: app/google-services.json missing - building without Firebase (login disabled; debug dev mode available)." -ForegroundColor Yellow
}

if ($GradleHome) {
    New-Item -ItemType Directory -Force -Path "$GradleHome\tmp" | Out-Null
    $env:GRADLE_USER_HOME = $GradleHome
    $default = Join-Path $env:USERPROFILE '.gradle\caches'
    if (Test-Path $default) { $env:GRADLE_RO_DEP_CACHE = $default }
    $env:TEMP = "$GradleHome\tmp"; $env:TMP = "$GradleHome\tmp"
    $env:GRADLE_OPTS = "-Djava.io.tmpdir=$GradleHome\tmp"
    $wrapperSrc = Join-Path $env:USERPROFILE '.gradle\wrapper\dists\gradle-8.11.1-bin'
    if ((Test-Path $wrapperSrc) -and -not (Test-Path "$GradleHome\wrapper\dists\gradle-8.11.1-bin")) {
        New-Item -ItemType Directory -Force -Path "$GradleHome\wrapper\dists" | Out-Null
        Copy-Item $wrapperSrc "$GradleHome\wrapper\dists\" -Recurse
    }
    Write-Host "Using Gradle home $GradleHome"
}

$variant = if ($Release) { 'Release' } else { 'Debug' }
$tasks = @()
if ($Test) { $tasks += "test${variant}UnitTest" }
$tasks += "assemble$variant"

Push-Location $Android
try {
    & .\gradlew.bat @tasks --console=plain
    if ($LASTEXITCODE -ne 0) { Write-Host "`nBuild failed. See the Gradle output above." -ForegroundColor Red; exit $LASTEXITCODE }
} finally { Pop-Location }

$apk = Get-ChildItem (Join-Path $Android "app\build\outputs\apk\$($variant.ToLower())") -Filter *.apk -ErrorAction SilentlyContinue | Select-Object -First 1
if ($apk) {
    Write-Host "`nAPK ready: $($apk.FullName) ($([math]::Round($apk.Length / 1MB, 1)) MB)" -ForegroundColor Green
    Write-Host "Install on a connected device/emulator:  adb install -r `"$($apk.FullName)`""
} else { Write-Host "Build finished but no APK was found." -ForegroundColor Yellow }
