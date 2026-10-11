$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $PSScriptRoot "Get-Maven.ps1")
if (-not (Get-Command java -ErrorAction SilentlyContinue)) { Write-Host "Java 21 was not found." -ForegroundColor Red; exit 1 }
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) { Write-Host "jpackage was not found. Install a full JDK 21." -ForegroundColor Red; exit 1 }
Set-Location (Join-Path $root "client")
Write-Host "Building IntelliLib desktop app..." -ForegroundColor Cyan
mvn -q clean package

if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "BUILD FAILED. Fix the compilation errors above." -ForegroundColor Red
    exit $LASTEXITCODE
}
$pkg = Join-Path (Get-Location) "target\package"
Remove-Item -Recurse -Force $pkg -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $pkg | Out-Null
Copy-Item "target\intellilib-client-2.0.0.jar" $pkg
Copy-Item "target\lib\*.jar" $pkg
$dist = Join-Path (Get-Location) "dist"
Remove-Item -Recurse -Force $dist -ErrorAction SilentlyContinue
$icon = Join-Path (Get-Location) "src\main\resources\ui\app-icon.ico"
jpackage --type app-image --input $pkg --dest $dist --name IntelliLib --main-jar intellilib-client-2.0.0.jar --main-class com.intellilib.client.Launcher --vendor "IntelliLib" --app-version 2.0.0 --icon $icon
Write-Host "" 
Write-Host "DONE: client\dist\IntelliLib\IntelliLib.exe" -ForegroundColor Green
Write-Host "This portable app includes its own Java runtime. Other PCs do not need Java/Maven/Docker." -ForegroundColor Green
