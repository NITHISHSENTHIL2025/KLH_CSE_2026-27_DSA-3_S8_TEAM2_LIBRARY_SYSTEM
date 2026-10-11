$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $PSScriptRoot "Get-Maven.ps1")
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
  Write-Host "Java 21 was not found. Install JDK 21, then run this again." -ForegroundColor Red
  exit 1
}
Set-Location (Join-Path $root "client")
mvn -q javafx:run
