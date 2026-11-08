<#
.SYNOPSIS
  Starts the AI Skin Scanner backend.
.PARAMETER Mode
  dev  (default) - hot reload with tsx
  prod           - compile TypeScript and run dist/server.js
.EXAMPLE
  .\scripts\start-backend.ps1
  .\scripts\start-backend.ps1 -Mode prod
#>
param([ValidateSet('dev', 'prod')][string]$Mode = 'dev')
$ErrorActionPreference = 'Stop'
$Backend = Join-Path (Split-Path -Parent $PSScriptRoot) 'backend'

if (-not (Get-Command node -ErrorAction SilentlyContinue)) { Write-Host "Node.js not found. Install from https://nodejs.org" -ForegroundColor Red; exit 1 }
if (-not (Test-Path (Join-Path $Backend '.env'))) {
    Write-Host "backend/.env not found. Run scripts\setup.ps1 or copy backend\.env.example to backend\.env." -ForegroundColor Red; exit 1
}

Push-Location $Backend
try {
    if (-not (Test-Path node_modules)) { Write-Host "Installing dependencies..."; npm install --no-audit --no-fund }

    $port = ((Get-Content .env | Where-Object { $_ -match '^PORT=' }) -replace 'PORT=', '').Trim()
    if (-not $port) { $port = '3000' }
    $lan = (Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue | Where-Object { $_.PrefixOrigin -in 'Dhcp', 'Manual' -and $_.IPAddress -notlike '169.*' } | Select-Object -First 1).IPAddress

    Write-Host "`nBackend will listen on port $port" -ForegroundColor Cyan
    Write-Host "  This PC:           http://localhost:$port/api/v1/health"
    Write-Host "  Android Emulator:  http://10.0.2.2:$port/"
    if ($lan) { Write-Host "  Physical device:   http://${lan}:$port/  (same Wi-Fi; allow Node.js through Windows Firewall)" }
    Write-Host ""

    if ($Mode -eq 'prod') { npm run build; if ($LASTEXITCODE -ne 0) { exit 1 }; npm start }
    else { npm run dev }
} finally { Pop-Location }
