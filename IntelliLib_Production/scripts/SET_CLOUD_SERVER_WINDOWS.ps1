$ErrorActionPreference = "Stop"
$url = Read-Host "Paste your Render URL (example: https://intellilib-api.onrender.com)"
if ([string]::IsNullOrWhiteSpace($url)) { throw "Server URL is required" }
$dir = Join-Path $env:USERPROFILE ".intellilib"
New-Item -ItemType Directory -Force -Path $dir | Out-Null
$file = Join-Path $dir "client.properties"
@("serverBaseUrl=$($url.TrimEnd('/'))","workstationId=AUTO") | Set-Content -Path $file -Encoding ASCII
Write-Host "Saved. Start IntelliLib.exe now." -ForegroundColor Green
