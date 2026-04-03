# Copies dist/JossDoubleJump.jar + runtime-config to <server>/earlyplugins/ (Hyxin mixin path).
# Hyxin only loads mixin configs from earlyplugins — use this instead of sync-to-mods.ps1 when using Hyxin.
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$serverRoot = $env:PEBBLE_SERVER_ROOT
if ([string]::IsNullOrWhiteSpace($serverRoot)) {
  $serverRoot = $jossRoot
}
$distJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
$cfgSrc = Join-Path $jossRoot "runtime-config\JossDoubleJumpConfig.json"
$earlyDir = Join-Path $serverRoot "earlyplugins"

if (-not (Test-Path $distJar)) { throw "Missing: $distJar" }
if (-not (Test-Path $cfgSrc)) { throw "Missing: $cfgSrc" }

New-Item -ItemType Directory -Force -Path $earlyDir | Out-Null
Copy-Item -Force $distJar (Join-Path $earlyDir "JossDoubleJump.jar")
Copy-Item -Force $cfgSrc (Join-Path $serverRoot "mods\JossDoubleJumpConfig.json")
Write-Host "Synced JossDoubleJump.jar -> $earlyDir"
Write-Host "Synced JossDoubleJumpConfig.json -> $(Join-Path $serverRoot 'mods')"
