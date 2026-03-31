# Rebuild JossDoubleJump.jar from patched Java sources (optional).
# Requires: HytaleServer.jar and the original "Double Jump-0.1.5.jar" at server root for compile classpath.
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$serverRoot = Split-Path $jossRoot -Parent
$sourcesDir = Join-Path $jossRoot "sources"
$outClasses = Join-Path $jossRoot "build\classes"
$workDir = Join-Path $jossRoot "build\unpack-original"
$assetsDir = Join-Path $jossRoot "build\assets"
$jdkHome = Get-ChildItem (Join-Path $jossRoot "tools\jdk25") -Directory | Select-Object -First 1
if (-not $jdkHome) { throw "Missing tools\jdk25 under $jossRoot" }
$javac = Join-Path $jdkHome.FullName "bin\javac.exe"
$jar = Join-Path $jdkHome.FullName "bin\jar.exe"
$hy = Join-Path $serverRoot "HytaleServer.jar"
$upstream = Join-Path $serverRoot "mods\Double Jump-0.1.5.jar"
if (-not (Test-Path $hy)) { throw "Missing HytaleServer.jar at $hy" }
if (-not (Test-Path $upstream)) { throw "Missing compile stub: mods\Double Jump-0.1.5.jar (keep a copy for classpath)" }
if (-not (Test-Path $sourcesDir)) { throw "Missing sources folder: $sourcesDir — restore Java files there to rebuild." }

Remove-Item -Recurse -Force $outClasses, $workDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $outClasses | Out-Null
New-Item -ItemType Directory -Force -Path $workDir | Out-Null

Push-Location $workDir
& $jar xf $upstream
Pop-Location

$cp = "$hy;$upstream"
$javaFiles = Get-ChildItem -Path $sourcesDir -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& $javac -encoding UTF-8 -cp $cp -d $outClasses @javaFiles

$plug = Join-Path $outClasses "org\narwhals\plugin"
Copy-Item -Force (Join-Path $plug "DoubleJump.class") (Join-Path $workDir "org\narwhals\plugin\")
Copy-Item -Force (Join-Path $plug "DoubleJumpComponent.class") (Join-Path $workDir "org\narwhals\plugin\")
Copy-Item -Force (Join-Path $plug "DoubleJumpConfig.class") (Join-Path $workDir "org\narwhals\plugin\")
Copy-Item -Force (Join-Path $plug "DoubleJumpExecutor.class") (Join-Path $workDir "org\narwhals\plugin\")
Copy-Item -Force (Join-Path $plug "DoubleJumpInteraction.class") (Join-Path $workDir "org\narwhals\plugin\")
Copy-Item -Force (Join-Path $plug "DoubleJumpSystem.class") (Join-Path $workDir "org\narwhals\plugin\")
Copy-Item -Force (Join-Path $plug "PlayerJoinDoubleJumpAdder.class") (Join-Path $workDir "org\narwhals\plugin\")
Copy-Item -Force (Join-Path $plug "ui\DoubleJumpConfigUIPage.class") (Join-Path $workDir "org\narwhals\plugin\ui\")
Copy-Item -Force (Join-Path $assetsDir "double_jump_defaults.json") $workDir
Copy-Item -Force (Join-Path $assetsDir "manifest.json") $workDir

$outJar = Join-Path $jossRoot "dist\JossDoubleJump.jar"
New-Item -ItemType Directory -Force -Path (Split-Path $outJar -Parent) | Out-Null
if (Test-Path $outJar) { Remove-Item -Force $outJar }
Push-Location $workDir
& $jar cfm $outJar META-INF\MANIFEST.MF -C . .
Pop-Location
Write-Host "Built: $outJar"
