# Copies dist/JossDoubleJump.jar and runtime-config into <server root>/mods/
# (paths are resolved from this script’s location; server cwd does not matter).
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$serverRoot = Split-Path $jossRoot -Parent
$distJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
$cfgSrc = Join-Path $jossRoot "runtime-config\double_jump_config.json"
$modsDir = Join-Path $serverRoot "mods"
$cfgDestDir = Join-Path $modsDir "JossDoubleJump"

if (-not (Test-Path $distJar)) { throw "Missing: $distJar" }
if (-not (Test-Path $cfgSrc)) { throw "Missing: $cfgSrc" }

New-Item -ItemType Directory -Force -Path $modsDir | Out-Null
New-Item -ItemType Directory -Force -Path $cfgDestDir | Out-Null
Copy-Item -Force $distJar (Join-Path $modsDir "JossDoubleJump.jar")
Copy-Item -Force $cfgSrc (Join-Path $cfgDestDir "double_jump_config.json")
Write-Host "Synced JossDoubleJump -> $modsDir"
