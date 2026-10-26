# Basic frontend checks for the Windows Jenkins pipeline (PowerShell). Same checks as check.sh.
$root = $PSScriptRoot
if ([string]::IsNullOrEmpty($root)) { $root = Split-Path -Parent $MyInvocation.MyCommand.Path }
$pub = Join-Path $root 'public'
Write-Host "Checking frontend in: $pub"

if (-not (Test-Path -LiteralPath $pub)) {
    Write-Host "The folder 'public' was not found. Contents of ${root}:"
    Get-ChildItem -LiteralPath $root | ForEach-Object { Write-Host ("  " + $_.Name) }
    exit 1
}

foreach ($page in 'index','login','register','dashboard','book','track','admin') {
    $file = Join-Path $pub ($page + '.html')
    if (-not (Test-Path -LiteralPath $file)) {
        Write-Host "MISSING $file"
        Write-Host 'Files found in public:'
        Get-ChildItem -LiteralPath $pub | ForEach-Object { Write-Host ("  " + $_.Name) }
        exit 1
    }
}

$refs = Get-ChildItem -LiteralPath $pub -Filter *.html |
    ForEach-Object { Select-String -LiteralPath $_.FullName -Pattern '(?:src|href)="((?:js|css)/[^"]*)"' -AllMatches } |
    ForEach-Object { $_.Matches } |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique
foreach ($r in $refs) {
    if (-not (Test-Path -LiteralPath (Join-Path $pub $r))) { Write-Host "BROKEN REFERENCE: $r"; exit 1 }
}

if (Get-Command node -ErrorAction SilentlyContinue) {
    foreach ($js in Get-ChildItem -LiteralPath (Join-Path $pub 'js') -Filter *.js) {
        node --check $js.FullName
        if ($LASTEXITCODE -ne 0) { Write-Host "JavaScript syntax error in $($js.Name)"; exit 1 }
    }
    Write-Host 'JavaScript syntax OK'
} else {
    Write-Host 'node not installed - skipping JavaScript syntax check'
}
Write-Host 'Frontend checks passed'
exit 0
