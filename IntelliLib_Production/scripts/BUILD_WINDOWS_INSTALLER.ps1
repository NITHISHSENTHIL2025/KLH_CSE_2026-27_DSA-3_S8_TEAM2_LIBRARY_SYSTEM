$ErrorActionPreference = "Stop"
$root = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
. (Join-Path $PSScriptRoot "Get-Maven.ps1")
if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) { Write-Host "jpackage was not found. Install JDK 21." -ForegroundColor Red; exit 1 }
Set-Location (Join-Path $root "client")
mvn -q clean package
$pkg = Join-Path (Get-Location) "target\package"
Remove-Item -Recurse -Force $pkg -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $pkg | Out-Null
Copy-Item "target\intellilib-client-2.0.0.jar" $pkg
Copy-Item "target\lib\*.jar" $pkg
$dist = Join-Path (Get-Location) "installer"
Remove-Item -Recurse -Force $dist -ErrorAction SilentlyContinue
$icon = Join-Path (Get-Location) "src\main\resources\ui\app-icon.ico"
try {
  jpackage --type exe --input $pkg --dest $dist --name IntelliLib --main-jar intellilib-client-2.0.0.jar --main-class com.intellilib.client.Launcher --vendor "IntelliLib" --app-version 2.0.0 --icon $icon --win-menu --win-shortcut --win-dir-chooser
  Write-Host "Installer created in client\installer" -ForegroundColor Green
} catch {
  Write-Host "Windows EXE installer packaging needs the WiX Toolset on some JDK builds." -ForegroundColor Yellow
  Write-Host "Use BUILD_WINDOWS_PORTABLE.ps1 instead; it creates IntelliLib.exe with a bundled Java runtime." -ForegroundColor Yellow
  throw
}
