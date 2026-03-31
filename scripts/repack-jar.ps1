# Produce dist/JossDoubleJump.jar from an existing template JAR + build/assets (no javac).
# Use when sources are CFR snapshots and full compile is not available. Output is gitignored (dist/).
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$assetsDir = Join-Path $jossRoot "build\assets"
$workDir = Join-Path $jossRoot "build\unpack-repack"
$jdkHome = Get-ChildItem (Join-Path $jossRoot "tools\jdk25") -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdkHome) { throw "Missing tools\jdk25 under $jossRoot (need jar.exe). tools\ is gitignored; install JDK 25 locally." }
$jar = Join-Path $jdkHome.FullName "bin\jar.exe"

$parent = Split-Path $jossRoot -Parent
$serverMods = Join-Path (Join-Path $parent "PebbleHotServerRoot") "mods"
$upstream = @(
  (Join-Path $jossRoot "mods\JossDoubleJump.jar"),
  (Join-Path $serverMods "JossDoubleJump.jar"),
  (Join-Path $jossRoot "dist\JossDoubleJump.jar"),
  (Join-Path $serverMods "Double Jump-0.1.5.jar")
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $upstream) {
  throw "No template JAR found. Add dist\JossDoubleJump.jar, copy JossDoubleJump.jar under mods\, or install Double Jump / this mod on a server next to the repo."
}
$def = Join-Path $assetsDir "double_jump_defaults.json"
$mft = Join-Path $assetsDir "manifest.json"
if (-not (Test-Path $def)) { throw "Missing: $def" }
if (-not (Test-Path $mft)) { throw "Missing: $mft" }

Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
Push-Location $workDir
try {
  & $jar xf $upstream
} finally {
  Pop-Location
}

Copy-Item -Force $def (Join-Path $workDir "double_jump_defaults.json")
Copy-Item -Force $mft (Join-Path $workDir "manifest.json")

$outJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
New-Item -ItemType Directory -Force -Path (Split-Path $outJar -Parent) | Out-Null
if (Test-Path $outJar) { Remove-Item -Force $outJar }
Push-Location $workDir
try {
  & $jar cfm $outJar META-INF\MANIFEST.MF -C . .
} finally {
  Pop-Location
}
Write-Host "Repacked: $outJar (from $upstream)"
