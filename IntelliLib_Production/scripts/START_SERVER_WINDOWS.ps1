$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
Set-Location $root
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
  Write-Host "Docker was not found. Install Docker Desktop first." -ForegroundColor Red
  exit 1
}
Write-Host "Starting LOCAL DEVELOPMENT PostgreSQL + IntelliLib server..." -ForegroundColor Cyan
docker compose up -d --build
Write-Host "Waiting for the server..."
for ($i=0; $i -lt 120; $i++) {
  try {
    $r = Invoke-WebRequest -UseBasicParsing http://localhost:8080/api/health -TimeoutSec 2
    if ($r.StatusCode -eq 200) {
      Write-Host "IntelliLib server is LIVE on http://localhost:8080" -ForegroundColor Green
      Write-Host "For production, deploy server/ to Render and Neon. Local Docker is only for development."
      exit 0
    }
  } catch {}
  Start-Sleep -Seconds 2
}
Write-Host "Server did not become healthy in time. Run: docker compose logs server" -ForegroundColor Red
exit 1
