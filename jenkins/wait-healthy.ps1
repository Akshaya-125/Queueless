# Usage: powershell -File jenkins\wait-healthy.ps1 queueless-mysql queueless-backend queueless-frontend
param([string[]]$Containers)
foreach ($c in $Containers) {
    Write-Host "Waiting for $c to become healthy..."
    $status = 'unknown'
    for ($i = 0; $i -lt 40; $i++) {
        $status = (docker inspect -f '{{.State.Health.Status}}' $c 2>$null)
        if ($status -eq 'healthy') { break }
        Start-Sleep -Seconds 5
    }
    if ($status -ne 'healthy') {
        Write-Host "$c did not become healthy (last status: $status)"
        docker logs --tail 50 $c
        exit 1
    }
    Write-Host "$c is healthy"
}
exit 0
