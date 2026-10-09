# Starts the LabMarket Spring Boot backend (dev profile, local Oracle XE).
# Requires: JDK 21+, Apache Maven, Oracle XE running with the labmarket user.
# Usage: right-click → "Run with PowerShell" (or: powershell -ExecutionPolicy Bypass -File .\start-backend.ps1)
$ErrorActionPreference = 'Stop'

$backend = Join-Path $PSScriptRoot 'backend'
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
  Write-Host 'Maven (mvn) not found. Install it first:  winget install Apache.Maven' -ForegroundColor Red
  Write-Host 'Then close and reopen this terminal and run this script again.'
  exit 1
}

$env:SPRING_PROFILES_ACTIVE = 'dev'
$env:DB_URL = 'jdbc:oracle:thin:@localhost:1521/XEPDB1'
$env:DB_USERNAME = 'labmarket'
$env:DB_PASSWORD = 'change-me-in-dev'

Write-Host 'Starting backend on http://localhost:8080 ... (wait for "Started LabMarketplaceApplication")'
Set-Location $backend
& mvn spring-boot:run
