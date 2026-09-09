param([string]$RedisHome = 'C:\Program Files\Redis')
$ErrorActionPreference = 'Stop'
$backend = Split-Path -Parent $PSScriptRoot
$workspace = Split-Path -Parent (Split-Path -Parent $backend)
$testRoot = Join-Path $workspace '.redis-test'
$config = Join-Path $testRoot 'redis.conf'
$cli = Join-Path $RedisHome 'redis-cli.exe'
$serverBinary = Join-Path $RedisHome 'redis-server.exe'
if (!(Test-Path -LiteralPath $cli) -or !(Test-Path -LiteralPath $serverBinary)) { throw 'Redis binaries not found; specify -RedisHome.' }

function Test-IsolatedRedis {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $info = & $cli -h 127.0.0.1 -p 16379 INFO server 2>$null
    $connected = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previousPreference
    if (!$connected) { return $false }
    $portLine = $info | Where-Object { $_ -match '^tcp_port:' }
    $configLine = $info | Where-Object { $_ -match '^config_file:' }
    if (!$portLine -or !$configLine) { throw 'Port 16379 has an unidentified Redis instance.' }
    $actualConfig = $configLine.Substring('config_file:'.Length).Trim()
    if ($portLine.Trim() -ne 'tcp_port:16379' -or [IO.Path]::GetFullPath($actualConfig) -ne [IO.Path]::GetFullPath($config)) {
        throw 'Port 16379 belongs to another instance; refusing to use it.'
    }
    return $true
}

if (!(Test-IsolatedRedis)) {
    if (Get-NetTCPConnection -LocalPort 16379 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 16379 is occupied.' }
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
    @('bind 127.0.0.1','port 16379','protected-mode yes','save ""','appendonly no','databases 16','loglevel warning') |
        Set-Content -LiteralPath $config -Encoding ASCII
    $server = Start-Process -FilePath $serverBinary -ArgumentList ('"' + $config + '"') -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath (Join-Path $testRoot 'process-id.txt') -Value $server.Id
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-IsolatedRedis) { $ready = $true; break }
        if ($server.HasExited) { throw 'Isolated Redis exited.' }
        Start-Sleep -Milliseconds 200
    }
    if (!$ready) { throw 'Isolated Redis did not become ready.' }
}
Write-Output 'Isolated Redis ready: 127.0.0.1:16379. Tests use database 14 and a unique prefix; no flush commands.'
