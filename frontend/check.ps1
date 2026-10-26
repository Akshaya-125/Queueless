# Basic frontend checks for the Windows Jenkins pipeline (PowerShell). Same checks as check.sh.
Set-Location -Path $PSScriptRoot

foreach ($f in 'index','login','register','dashboard','book','track','admin') {
    if (-not (Test-Path "public/$f.html")) { Write-Host "MISSING public/$f.html"; exit 1 }
}

$refs = Get-ChildItem public -Filter *.html |
    ForEach-Object { Select-String -Path $_.FullName -Pattern '(?:src|href)="((?:js|css)/[^"]*)"' -AllMatches } |
    ForEach-Object { $_.Matches } |
    ForEach-Object { $_.Groups[1].Value } |
    Sort-Object -Unique
foreach ($r in $refs) {
    if (-not (Test-Path "public/$r")) { Write-Host "BROKEN REFERENCE: $r"; exit 1 }
}

if (Get-Command node -ErrorAction SilentlyContinue) {
    foreach ($js in Get-ChildItem public/js -Filter *.js) {
        node --check $js.FullName
        if ($LASTEXITCODE -ne 0) { Write-Host "JavaScript syntax error in $($js.Name)"; exit 1 }
    }
    Write-Host 'JavaScript syntax OK'
} else {
    Write-Host 'node not installed - skipping JavaScript syntax check'
}
Write-Host 'Frontend checks passed'
exit 0
