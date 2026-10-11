$ErrorActionPreference = "Stop"
if (Get-Command mvn -ErrorAction SilentlyContinue) { return }
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$tools = Join-Path $root ".tools"
$mvnHome = Join-Path $tools "apache-maven-3.9.11"
if (-not (Test-Path (Join-Path $mvnHome "bin\mvn.cmd"))) {
  New-Item -ItemType Directory -Force -Path $tools | Out-Null
  $zip = Join-Path $tools "maven.zip"
  Write-Host "Downloading Maven 3.9.11 for this project..." -ForegroundColor Cyan
  Invoke-WebRequest -Uri "https://archive.apache.org/dist/maven/maven-3/3.9.11/binaries/apache-maven-3.9.11-bin.zip" -OutFile $zip
  Expand-Archive -Force $zip $tools
  Remove-Item $zip
}
$env:PATH = (Join-Path $mvnHome "bin") + ";" + $env:PATH
