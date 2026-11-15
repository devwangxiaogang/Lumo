# Device lock: serialize adb access across concurrent sessions (see docs/DEVICE_LOCK.md)
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet('acquire', 'release', 'status', 'force-release')]
    [string]$Action,

    [Parameter(Position = 1)]
    [string]$Owner = 'unknown',

    [int]$WaitSeconds = 0,

    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$lockPath = Join-Path $root '.device.lock'
$staleMinutes = 15

function Read-Lock {
    if (-not (Test-Path $lockPath)) { return $null }
    try {
        return (Get-Content $lockPath -Raw | ConvertFrom-Json)
    } catch {
        return [pscustomobject]@{ owner = 'corrupt'; at = (Get-Date).ToString('o') }
    }
}

function Test-Stale($lock) {
    if ($null -eq $lock) { return $true }
    try {
        $at = [datetime]::Parse($lock.at)
        return ((Get-Date) - $at).TotalMinutes -gt $staleMinutes
    } catch {
        return $true
    }
}

function Write-Lock($name) {
    $payload = @{
        owner    = $name
        at       = (Get-Date).ToString('o')
        pid      = $PID
        hostname = $env:COMPUTERNAME
    }
    $payload | ConvertTo-Json | Set-Content -Path $lockPath -Encoding utf8
}

if ($Action -eq 'status') {
    $lock = Read-Lock
    if ($null -eq $lock) {
        Write-Host 'device lock: free' -ForegroundColor Green
    } else {
        $state = if (Test-Stale $lock) { ' (stale, takeover allowed)' } else { '' }
        Write-Host ('device lock: held by ' + $lock.owner + ' since ' + $lock.at + $state) -ForegroundColor Yellow
    }
    exit 0
}

if ($Action -eq 'acquire') {
    $deadline = (Get-Date).AddSeconds($WaitSeconds)
    while ($true) {
        $lock = Read-Lock
        if ($null -eq $lock -or $lock.owner -eq $Owner) {
            Write-Lock $Owner
            Write-Host ('device lock: acquired by ' + $Owner) -ForegroundColor Green
            exit 0
        }
        if (Test-Stale $lock) {
            if ($Force -or $WaitSeconds -gt 0) {
                Write-Lock $Owner
                Write-Host ('device lock: took over stale lock from ' + $lock.owner) -ForegroundColor Yellow
                exit 0
            }
            Write-Host ('device lock held by ' + $lock.owner + ' for over ' + $staleMinutes + ' min; use -Force to take over') -ForegroundColor Red
            exit 1
        }
        if ((Get-Date) -ge $deadline) {
            Write-Host ('device lock held by ' + $lock.owner + ' since ' + $lock.at + '; retry later') -ForegroundColor Red
            exit 1
        }
        Start-Sleep -Seconds 5
    }
}

if ($Action -eq 'release') {
    $lock = Read-Lock
    if ($null -eq $lock) {
        Write-Host 'device lock: already free' -ForegroundColor Green
        exit 0
    }
    if ($lock.owner -ne $Owner -and -not $Force) {
        Write-Host ('device lock held by ' + $lock.owner + '; use -Force to release it') -ForegroundColor Red
        exit 1
    }
    Remove-Item $lockPath -Force
    Write-Host ('device lock: released by ' + $Owner) -ForegroundColor Green
    exit 0
}

if ($Action -eq 'force-release') {
    if (Test-Path $lockPath) { Remove-Item $lockPath -Force }
    Write-Host 'device lock: cleared' -ForegroundColor Yellow
    exit 0
}
