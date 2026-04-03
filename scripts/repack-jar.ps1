# Refresh dist/JossDoubleJump.jar from an existing JAR + jar-assets (no javac).
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$assetsDir = Join-Path $jossRoot "jar-assets"
$workDir = Join-Path $jossRoot "build\unpack-repack"
$jdkHome = Get-ChildItem (Join-Path $jossRoot "tools\jdk25") -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdkHome) { throw "Missing tools\jdk25 (need jar.exe)." }
$jar = Join-Path $jdkHome.FullName "bin\jar.exe"

$parent = Split-Path $jossRoot -Parent
$serverMods = Join-Path (Join-Path $parent "PebbleHotServerRoot") "mods"
$templateJar = @(
  (Join-Path $jossRoot "dist\JossDoubleJump.jar"),
  (Join-Path $jossRoot "mods\JossDoubleJump.jar"),
  (Join-Path $serverMods "JossDoubleJump.jar")
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $templateJar) {
  throw "No JossDoubleJump.jar found. Add dist\JossDoubleJump.jar or mods\JossDoubleJump.jar."
}
$def = Join-Path $assetsDir "double_jump_defaults.json"
$mft = Join-Path $assetsDir "manifest.json"
$mix = Join-Path $assetsDir "jossdoublejump.mixins.json"
if (-not (Test-Path $def)) { throw "Missing: $def" }
if (-not (Test-Path $mft)) { throw "Missing: $mft" }
if (-not (Test-Path $mix)) { throw "Missing: $mix" }

Remove-Item -Recurse -Force $workDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $workDir | Out-Null
Push-Location $workDir
try {
  & $jar xf $templateJar
} finally {
  Pop-Location
}

Copy-Item -Force $def (Join-Path $workDir "double_jump_defaults.json")
Copy-Item -Force $mft (Join-Path $workDir "manifest.json")
Copy-Item -Force $mix (Join-Path $workDir "jossdoublejump.mixins.json")

$outJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
New-Item -ItemType Directory -Force -Path (Split-Path $outJar -Parent) | Out-Null
if (Test-Path $outJar) { Remove-Item -Force $outJar }
Push-Location $workDir
try {
  & $jar cfm $outJar META-INF\MANIFEST.MF -C . .
} finally {
  Pop-Location
}
Write-Host "Repacked: $outJar (from $templateJar)"
