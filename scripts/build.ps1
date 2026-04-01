# Full rebuild: compile src/main/java -> dist/JossDoubleJump.jar
# Requires: HytaleServer.jar (PEBBLE_SERVER_ROOT or next to PebbleHotServerRoot), JDK 25 in tools/jdk25,
#           and an existing JossDoubleJump.jar (dist/ or mods/) to unpack non-class assets + classpath.
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent

function Get-ServerRoot([string]$repoRoot) {
  $custom = $env:PEBBLE_SERVER_ROOT
  if (-not [string]::IsNullOrWhiteSpace($custom) -and (Test-Path (Join-Path $custom "HytaleServer.jar"))) {
    return $custom
  }
  $parent = Split-Path $repoRoot -Parent
  $candidates = @(
    (Join-Path $parent "PebbleHotServerRoot"),
    $parent,
    $repoRoot
  )
  foreach ($d in $candidates) {
    if ([string]::IsNullOrWhiteSpace($d)) { continue }
    if (Test-Path (Join-Path $d "HytaleServer.jar")) { return $d }
  }
  throw "Could not find HytaleServer.jar. Set PEBBLE_SERVER_ROOT to the folder that contains it."
}

$serverRoot = Get-ServerRoot $jossRoot
$sourcesDir = Join-Path $jossRoot "src\main\java"
$outClasses = Join-Path $jossRoot "build\classes"
$workDir = Join-Path $jossRoot "build\unpack-original"
$assetsDir = Join-Path $jossRoot "jar-assets"
$jdkHome = Get-ChildItem (Join-Path $jossRoot "tools\jdk25") -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jdkHome) { throw "Missing tools\jdk25\<jdk> (JDK 25). tools\ is gitignored." }
$javac = Join-Path $jdkHome.FullName "bin\javac.exe"
$jar = Join-Path $jdkHome.FullName "bin\jar.exe"
$hy = Join-Path $serverRoot "HytaleServer.jar"

$templateJar = @(
  (Join-Path $jossRoot "dist\JossDoubleJump.jar"),
  (Join-Path $jossRoot "mods\JossDoubleJump.jar")
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not (Test-Path $hy)) { throw "Missing HytaleServer.jar at $hy" }
if (-not $templateJar) {
  throw "No JossDoubleJump.jar template. Place dist\JossDoubleJump.jar or mods\JossDoubleJump.jar (run scripts\repack-jar.ps1 first if you have no JAR yet)."
}
if (-not (Test-Path $sourcesDir)) { throw "Missing Java sources: $sourcesDir" }

Remove-Item -Recurse -Force $outClasses, $workDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outClasses | Out-Null
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

Push-Location $workDir
& $jar xf $templateJar
Pop-Location

$cp = "$hy;$templateJar"
$javaFiles = Get-ChildItem -Path $sourcesDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -cp $cp -d $outClasses @javaFiles
if ($LASTEXITCODE -ne 0) { throw "javac failed ($LASTEXITCODE)." }

Get-ChildItem -Path $outClasses -Recurse -File | ForEach-Object {
  $rel = $_.FullName.Substring($outClasses.Length + 1)
  $dest = Join-Path $workDir $rel
  $destParent = Split-Path $dest -Parent
  if (-not (Test-Path $destParent)) {
    New-Item -ItemType Directory -Force -Path $destParent | Out-Null
  }
  Copy-Item -Force $_.FullName $dest
}

Copy-Item -Force (Join-Path $assetsDir "double_jump_defaults.json") $workDir
Copy-Item -Force (Join-Path $assetsDir "manifest.json") $workDir

$outJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
New-Item -ItemType Directory -Force -Path (Split-Path $outJar -Parent) | Out-Null
if (Test-Path $outJar) { Remove-Item -Force $outJar }
Push-Location $workDir
& $jar cfm $outJar META-INF\MANIFEST.MF -C . .
Pop-Location
Write-Host "Built: $outJar"
