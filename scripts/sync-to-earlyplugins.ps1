# Copies dist/JossDoubleJump.jar to BOTH earlyplugins/ (Hyxin mixin config discovery) and mods/ (JavaPlugin setup).
# Hyxin only reads mixin configs from jars in earlyplugins; the server still enables JavaPlugins from mods/.
# Config: mods/JossDoubleJumpConfig.json only (same as sync-to-mods.ps1).
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$serverRoot = $env:PEBBLE_SERVER_ROOT
if ([string]::IsNullOrWhiteSpace($serverRoot)) {
  $serverRoot = $jossRoot
}
$distJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
$cfgSrc = Join-Path $jossRoot "runtime-config\JossDoubleJumpConfig.json"
$earlyDir = Join-Path $serverRoot "earlyplugins"
$modsDir = Join-Path $serverRoot "mods"

if (-not (Test-Path $distJar)) { throw "Missing: $distJar" }
if (-not (Test-Path $cfgSrc)) { throw "Missing: $cfgSrc" }

New-Item -ItemType Directory -Force -Path $earlyDir | Out-Null
New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
Copy-Item -Force $distJar (Join-Path $earlyDir "JossDoubleJump.jar")
Copy-Item -Force $distJar (Join-Path $modsDir "JossDoubleJump.jar")
Copy-Item -Force $cfgSrc (Join-Path $modsDir "JossDoubleJumpConfig.json")
Write-Host "Synced JossDoubleJump.jar -> $earlyDir and $modsDir"
Write-Host "Synced JossDoubleJumpConfig.json -> $modsDir"
