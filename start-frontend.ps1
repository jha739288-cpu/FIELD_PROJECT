# Starts the LabMarket React frontend (Vite dev server).
# Requires: Node.js 20+ (npm). The backend must already be running (see start-backend.ps1).
# Usage: right-click → "Run with PowerShell" (or: powershell -ExecutionPolicy Bypass -File .\start-frontend.ps1)
$ErrorActionPreference = 'Stop'

$frontend = Join-Path $PSScriptRoot 'frontend'
if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
  Write-Host 'Node.js/npm not found. Install it first:  winget install OpenJS.NodeJS.LTS' -ForegroundColor Red
  Write-Host 'Then close and reopen this terminal and run this script again.'
  exit 1
}

Set-Location $frontend
if (-not (Test-Path 'node_modules')) {
  Write-Host 'First run: installing dependencies (one time, takes a few minutes)...'
  npm install
}

Write-Host 'Starting frontend on http://localhost:5173 ...'
npm run dev
