# Create GitHub repo and push main (keeps gh config under repo\.gh-config on D: if repo is on D:).
$ErrorActionPreference = "Stop"
$jossRoot = Split-Path $PSScriptRoot -Parent
$ghConfig = Join-Path $jossRoot ".gh-config"
New-Item -ItemType Directory -Force -Path $ghConfig | Out-Null
$env:GH_CONFIG_DIR = $ghConfig

$gh = $env:GH_EXE
if ([string]::IsNullOrWhiteSpace($gh) -or -not (Test-Path $gh)) {
  $gh = "C:\Program Files\GitHub CLI\gh.exe"
}
if (-not (Test-Path $gh)) { throw "gh not found. Set GH_EXE to gh.exe or install GitHub CLI." }

Write-Host "Using GH_CONFIG_DIR=$ghConfig (token/config stored here, not committed)"
# gh prints "not logged in" to stderr; with $ErrorActionPreference Stop that becomes a terminating error unless we relax here.
$prevEap = $ErrorActionPreference
$ErrorActionPreference = "Continue"
try {
  & $gh auth status 2>&1 | ForEach-Object { Write-Host $_ }
  $authExit = $LASTEXITCODE
} finally {
  $ErrorActionPreference = $prevEap
}
if ($authExit -ne 0) {
  Write-Host ""
  Write-Host "GitHub CLI is not logged in. Use the same GH_CONFIG_DIR as this script (under your repo on D:):"
  Write-Host "  `$env:GH_CONFIG_DIR = `"$ghConfig`""
  Write-Host ("  & `"$gh`" auth login -h github.com -p https -w")
  Write-Host ""
  Write-Host "Or with a PAT (repo scope), then re-run scripts\push-github.ps1:"
  Write-Host '  $env:GH_TOKEN = "<paste_token_here>"'
  Write-Host "  `$env:GH_TOKEN | & `"$gh`" auth login -h github.com --with-token"
  exit 1
}

Set-Location $jossRoot
git remote get-url origin 2>$null | Out-Null
if ($LASTEXITCODE -ne 0) {
  $name = if ($env:GITHUB_REPO_NAME) { $env:GITHUB_REPO_NAME } else { "Hytale_JossDoubleJump" }
  Write-Host "Creating GitHub repo $name and pushing..."
  & $gh repo create $name --public --source $jossRoot --remote origin --push
} else {
  Write-Host "Remote origin already set: $(git remote get-url origin)"
  git push -u origin main
}
