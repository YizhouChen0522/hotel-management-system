param([string]$MySqlHome = 'D:\MySQL')
$ErrorActionPreference = 'Stop'
$backend = Split-Path -Parent $PSScriptRoot
$gitRoot = Split-Path -Parent $backend
$workspace = Split-Path -Parent $gitRoot
$testRoot = Join-Path $workspace '.lifecycle-test-mysql'
$testData = Join-Path $testRoot 'data'
$mysql = Join-Path $MySqlHome 'bin\mysql.exe'
$mysqld = Join-Path $MySqlHome 'bin\mysqld.exe'
if (!(Test-Path -LiteralPath $mysql) -or !(Test-Path -LiteralPath $mysqld)) { throw 'MySQL binaries not found; specify -MySqlHome.' }

function Test-IsolatedIdentity {
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $identity = & $mysql --no-defaults --protocol=TCP --host=localhost --port=33079 --user=root --batch --skip-column-names -e 'SELECT @@port, @@datadir;' 2>$null
    $connected = $LASTEXITCODE -eq 0
    $ErrorActionPreference = $previousPreference
    if (!$connected) { return $false }
    $columns = $identity -split "`t"
    $actualPath = $columns[1].Replace('\\','\').TrimEnd('\','/')
    if ($columns[0] -ne '33079' -or [IO.Path]::GetFullPath($actualPath) -ne [IO.Path]::GetFullPath($testData)) {
        throw 'Port 33079 belongs to a different instance; refusing to use it.'
    }
    return $true
}

if (!(Test-IsolatedIdentity)) {
    $listener = Get-NetTCPConnection -LocalPort 33079 -State Listen -ErrorAction SilentlyContinue
    if ($listener) { throw 'Port 33079 is occupied; no fallback to another database.' }
    New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
    if (!(Test-Path -LiteralPath $testData)) {
        & $mysqld --no-defaults --initialize-insecure "--basedir=$MySqlHome" "--datadir=$testData" --console
        if ($LASTEXITCODE -ne 0) { throw 'Isolated MySQL initialization failed.' }
    }
    $serverArgs = @('--no-defaults', "--basedir=`"$MySqlHome`"", "--datadir=`"$testData`"", '--port=33079', '--bind-address=127.0.0.1',
        '--log_syslog=0', '--explicit_defaults_for_timestamp=1', "--log-error=`"$(Join-Path $testRoot 'server.log')`"")
    $server = Start-Process -FilePath $mysqld -ArgumentList $serverArgs -WindowStyle Hidden -PassThru
    Set-Content -LiteralPath (Join-Path $testRoot 'process-id.txt') -Value $server.Id
    $ready = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        if (Test-IsolatedIdentity) { $ready = $true; break }
        if ($server.HasExited) { throw 'Isolated MySQL exited; inspect its server.log.' }
        Start-Sleep -Milliseconds 500
    }
    if (!$ready) { throw 'Isolated MySQL did not become ready.' }
}
if (!(Test-IsolatedIdentity)) { throw 'Test instance identity unavailable.' }
& $mysql --no-defaults --protocol=TCP --host=localhost --port=33079 --user=root -e 'CREATE DATABASE IF NOT EXISTS hotel_lifecycle_test CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;'
if ($LASTEXITCODE -ne 0) { throw 'Could not create isolated test schema.' }
Write-Output 'Isolated MySQL ready: localhost:33079 / hotel_lifecycle_test. Existing schemas are never dropped by this script.'
