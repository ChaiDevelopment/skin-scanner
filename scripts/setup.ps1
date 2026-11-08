<#
.SYNOPSIS
  Checks the Windows development environment for AI Skin Scanner and installs backend dependencies.
  Never overwrites existing configuration files without asking.
#>
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$ok = $true

function Pass($m) { Write-Host "  [OK]   $m" -ForegroundColor Green }
function Warn($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function Fail($m) { Write-Host "  [FAIL] $m" -ForegroundColor Red; $script:ok = $false }

function Copy-IfMissing($from, $to) {
    if (Test-Path $to) {
        $answer = Read-Host "  $to already exists. Overwrite with template? (y/N)"
        if ($answer -ne 'y') { Pass "Kept existing $to"; return }
    }
    Copy-Item $from $to -Force
    Pass "Created $to from template"
}

Write-Host "`n== AI Skin Scanner: environment check ==" -ForegroundColor Cyan

# Java 17+
try {
    if (-not (Get-Command java -ErrorAction SilentlyContinue)) { throw 'missing' }
    # java -version prints to stderr; run through cmd so PowerShell 5.1 doesn't treat it as an error.
    $javaOut = (cmd /c "java -version 2>&1" | Out-String)
    if ($javaOut -match 'version "(\d+)') {
        if ([int]$Matches[1] -ge 17) { Pass "Java $($Matches[1])" } else { Fail "Java 17+ required (found $($Matches[1])). Install Temurin 17: https://adoptium.net" }
    } else { Fail "Could not read Java version" }
} catch { Fail "Java not found. Install JDK 17 (https://adoptium.net) and set JAVA_HOME." }

# Android SDK
$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
if (Test-Path "$sdk\platforms\android-35") { Pass "Android SDK platform 35 at $sdk" }
elseif (Test-Path $sdk) { Fail "Android SDK found at $sdk but platform android-35 is missing. Install it with: sdkmanager `"platforms;android-35`"" }
else { Fail "Android SDK not found. Install Android Studio or the command-line tools and set ANDROID_HOME." }
if (Test-Path "$sdk\emulator\emulator.exe") { Pass "Android Emulator installed" } else { Warn "Android Emulator not installed (optional; needed only to run on an emulator)" }

$localProps = Join-Path $Root 'android\local.properties'
if (-not (Test-Path $localProps) -and (Test-Path $sdk)) {
    "sdk.dir=$($sdk -replace '\\','/' -replace ':','\:')" | Set-Content -Encoding ascii $localProps
    Pass "Wrote android/local.properties"
}

# Node.js 20.12+
try {
    $nodeV = (& node -v).TrimStart('v')
    $major = [int]($nodeV.Split('.')[0]); $minor = [int]($nodeV.Split('.')[1])
    if ($major -gt 20 -or ($major -eq 20 -and $minor -ge 12)) { Pass "Node.js $nodeV" } else { Fail "Node.js 20.12+ required (found $nodeV)" }
    Pass "npm $(& npm -v)"
} catch { Fail "Node.js not found. Install Node.js 20 LTS or newer: https://nodejs.org" }

# Disk space (Gradle needs several GB)
$cFree = [math]::Round((Get-PSDrive C).Free / 1GB, 1)
if ($cFree -lt 3) { Warn "Only $cFree GB free on C:. scripts/build-apk.ps1 -GradleHome D:\.gradle-aiskin keeps Gradle files on another drive." }

Write-Host "`n== Configuration ==" -ForegroundColor Cyan
$envFile = Join-Path $Root 'backend\.env'
if (-not (Test-Path $envFile)) { Copy-IfMissing (Join-Path $Root 'backend\.env.example') $envFile } else { Pass "backend/.env exists" }
$cfg = Join-Path $Root 'android\config.properties'
if (-not (Test-Path $cfg)) { Copy-IfMissing (Join-Path $Root 'android\config.properties.example') $cfg } else { Pass "android/config.properties exists" }

$envText = Get-Content $envFile -Raw
if ($envText -match '(?m)^GEMINI_API_KEY=\s*$') { Warn "GEMINI_API_KEY is empty in backend/.env (see docs/GEMINI_SETUP.md)" } else { Pass "GEMINI_API_KEY is set" }
if ($envText -match '(?m)^FIREBASE_PROJECT_ID=\S+' -or $envText -match '(?m)^GOOGLE_APPLICATION_CREDENTIALS=\S+') { Pass "Firebase Admin credentials configured" }
else { Warn "Firebase Admin credentials missing in backend/.env (see docs/FIREBASE_SETUP.md). DEV_AUTH_BYPASS=true allows local testing without Firebase." }
if (Test-Path (Join-Path $Root 'android\app\google-services.json')) { Pass "android/app/google-services.json present" }
else { Warn "android/app/google-services.json missing - the app builds, but login is disabled until you add it (docs/FIREBASE_SETUP.md)" }

Write-Host "`n== Backend dependencies ==" -ForegroundColor Cyan
if (Get-Command npm -ErrorAction SilentlyContinue) {
    Push-Location (Join-Path $Root 'backend')
    try {
        if (-not (Test-Path node_modules)) { npm install --no-audit --no-fund; Pass "npm install done" } else { Pass "node_modules present (run 'npm install' to update)" }
    } finally { Pop-Location }
}

if ($ok) { Write-Host "`nSetup complete. Next: scripts\start-backend.ps1 and scripts\build-apk.ps1`n" -ForegroundColor Green }
else { Write-Host "`nSome required tools are missing. Fix the [FAIL] items above and re-run.`n" -ForegroundColor Red; exit 1 }
