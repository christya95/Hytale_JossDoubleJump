# Copies dist/JossDoubleJump.jar and runtime-config into <server>/mods/
# Config is a single file: mods/double_jump_config.json (same as first-run defaults from the JAR).
# Optional: PEBBLE_SERVER_ROOT = folder with HytaleServer.jar and mods\
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$serverRoot = $env:PEBBLE_SERVER_ROOT
if ([string]::IsNullOrWhiteSpace($serverRoot)) {
    $serverRoot = $jossRoot
}
$distJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
$cfgSrc = Join-Path $jossRoot "runtime-config\double_jump_config.json"
$modsDir = Join-Path $serverRoot "mods"

if (-not (Test-Path $distJar)) { throw "Missing: $distJar" }
if (-not (Test-Path $cfgSrc)) { throw "Missing: $cfgSrc" }

New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
Copy-Item -Force $distJar (Join-Path $modsDir "JossDoubleJump.jar")
Copy-Item -Force $cfgSrc (Join-Path $modsDir "double_jump_config.json")
Write-Host "Synced JossDoubleJump.jar and double_jump_config.json -> $modsDir"
