# Doc-QA one-click start.
# Checks and fixes everything needed, then starts the database, backend and frontend.
# Run by double-clicking start.bat (or: powershell -ExecutionPolicy Bypass -File start.ps1)

$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$Backend = Join-Path $Root 'backend'
$Frontend = Join-Path $Root 'frontend'

function Step($msg) { Write-Host ""; Write-Host "==> $msg" -ForegroundColor Cyan }
function Ok($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Warn($msg) { Write-Host "    [!]  $msg" -ForegroundColor Yellow }
function Fail($msg) { Write-Host ""; Write-Host "    [X]  $msg" -ForegroundColor Red; Write-Host ""; exit 1 }

# Run a native command quietly through cmd (Windows PowerShell 5.1 turns native stderr into errors).
function Invoke-Quiet($cmdline) { cmd /c "$cmdline >nul 2>&1"; return ($LASTEXITCODE -eq 0) }
function Get-DbHealth { (cmd /c "docker inspect -f {{.State.Health.Status}} docqa-postgres 2>nul" | Out-String).Trim() }

function Test-Url($url) {
    try { Invoke-WebRequest -UseBasicParsing $url -TimeoutSec 2 | Out-Null; return $true } catch { return $false }
}

function Wait-Until($seconds, [scriptblock]$check) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        if (& $check) { return $true }
        Start-Sleep -Seconds 2
    }
    return $false
}

Write-Host ""
Write-Host "  Doc-QA  -  starting up" -ForegroundColor Magenta
Write-Host "  ----------------------"

# ---------------------------------------------------------------- 1. Java
Step "Checking Java"
$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\java.exe'))) {
    $javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
}
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome 'bin\java.exe'))) {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) { Fail "Java is not installed. Install JDK 21 or newer from https://adoptium.net and run this again." }
    $props = & cmd /c "`"$($javaCmd.Source)`" -XshowSettings:properties -version 2>&1"
    $line = $props | Where-Object { $_ -match 'java\.home\s*=' } | Select-Object -First 1
    if (-not $line) { Fail "Could not find the Java installation folder. Set JAVA_HOME to your JDK folder." }
    $javaHome = ($line -split '=', 2)[1].Trim()
    setx JAVA_HOME "$javaHome" | Out-Null
    Warn "JAVA_HOME was missing - set it to $javaHome (saved for next time)"
}
$env:JAVA_HOME = $javaHome
$verLine = & cmd /c "`"$javaHome\bin\java.exe`" -version 2>&1" | Select-Object -First 1
if ($verLine -match '"(\d+)') {
    if ([int]$Matches[1] -lt 21) { Fail "Java $($Matches[1]) found, but Java 21 or newer is required. Install it from https://adoptium.net" }
}
Ok "Java: $verLine"

# ---------------------------------------------------------------- 2. Node
Step "Checking Node.js"
if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    Fail "Node.js is not installed. Install the LTS version from https://nodejs.org and run this again."
}
Ok "Node $(node -v)"

# ---------------------------------------------------------------- 3. Gemini key
Step "Checking Gemini API key"
$key = $env:GEMINI_API_KEY
if (-not $key) { $key = [Environment]::GetEnvironmentVariable('GEMINI_API_KEY', 'User') }
if (-not $key) {
    Warn "No GEMINI_API_KEY found. Get a free key at https://aistudio.google.com/apikey"
    $secure = Read-Host "    Paste your Gemini API key (input is hidden)" -AsSecureString
    $key = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
    if (-not $key) { Fail "A Gemini API key is required." }
    setx GEMINI_API_KEY "$key" | Out-Null
    Ok "Key saved for next time"
}
$env:GEMINI_API_KEY = $key
if ([Environment]::GetEnvironmentVariable('ANTHROPIC_API_KEY', 'User')) {
    $env:ANTHROPIC_API_KEY = [Environment]::GetEnvironmentVariable('ANTHROPIC_API_KEY', 'User')
}
if ([Environment]::GetEnvironmentVariable('ANSWER_PROVIDER', 'User')) {
    $env:ANSWER_PROVIDER = [Environment]::GetEnvironmentVariable('ANSWER_PROVIDER', 'User')
}
Ok "Gemini key found"

# ---------------------------------------------------------------- 4. Docker
Step "Checking Docker"
$dockerReady = Invoke-Quiet 'docker info'
if (-not $dockerReady) {
    $candidates = @(
        "$env:LOCALAPPDATA\Programs\DockerDesktop\Docker Desktop.exe",
        "$env:ProgramFiles\Docker\Docker\Docker Desktop.exe"
    )
    $exe = $candidates | Where-Object { Test-Path $_ } | Select-Object -First 1
    if (-not $exe) { Fail "Docker Desktop is not installed. Install it from https://www.docker.com/products/docker-desktop" }
    Warn "Docker Desktop is not running - starting it (this can take a minute)..."
    Start-Process $exe
    $dockerReady = Wait-Until 180 { Invoke-Quiet 'docker info' }
    if (-not $dockerReady) {
        Fail "Docker did not start. Open Docker Desktop, check for an error message, then run this again. (If it mentions WSL, run 'wsl --update' as Administrator.)"
    }
}
Ok "Docker engine is running"

# ---------------------------------------------------------------- 5. Database
Step "Starting database (PostgreSQL + pgvector)"
Push-Location $Root
try { $composed = Invoke-Quiet 'docker compose up -d' } finally { Pop-Location }
if (-not $composed) { Fail "Could not start the database container. Run 'docker compose up -d' in $Root to see the error." }
$healthy = Wait-Until 90 { (Get-DbHealth) -eq 'healthy' }
if (-not $healthy) { Fail "The database did not become healthy. Run 'docker compose logs postgres' in $Root to see why." }
Ok "Database is healthy"

# ---------------------------------------------------------------- 6. Ports
Step "Checking ports 8080 (backend) and 5173 (frontend)"
foreach ($port in 8080, 5173) {
    $conns = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue
    foreach ($c in $conns) {
        $p = Get-CimInstance Win32_Process -Filter "ProcessId=$($c.OwningProcess)" -ErrorAction SilentlyContinue
        if (-not $p) { continue }
        if ($p.CommandLine -match 'doc-reader|docqa|DocQaApplication|spring-boot|vite') {
            Warn "Port $port is used by an old Doc-QA process - stopping it"
            Stop-Process -Id $p.ProcessId -Force -ErrorAction SilentlyContinue
        } else {
            Fail "Port $port is used by another program: $($p.Name) (PID $($p.ProcessId)). Close it and run this again."
        }
    }
}
Start-Sleep -Seconds 1
Ok "Ports are free"

# ---------------------------------------------------------------- 7. Frontend packages
if (-not (Test-Path (Join-Path $Frontend 'node_modules'))) {
    Step "Installing frontend packages (first run only)"
    Push-Location $Frontend
    try { cmd /c "npm install --no-audit --no-fund" } finally { Pop-Location }
    if ($LASTEXITCODE -ne 0) { Fail "npm install failed. Check your internet connection and try again." }
    Ok "Packages installed"
}

# ---------------------------------------------------------------- 8. Backend
Step "Starting backend (first run downloads dependencies - can take a few minutes)"
Start-Process powershell -WorkingDirectory $Backend -ArgumentList @(
    '-NoExit', '-NoProfile', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'Doc-QA backend (close this window to stop)'; .\mvnw.cmd spring-boot:run"
)
$up = Wait-Until 300 { Test-Url 'http://localhost:8080/actuator/health' }
if (-not $up) { Fail "The backend did not start. Look at the 'Doc-QA backend' window for the error." }
Ok "Backend is running on http://localhost:8080"

# ---------------------------------------------------------------- 9. Frontend
Step "Starting frontend"
Start-Process powershell -WorkingDirectory $Frontend -ArgumentList @(
    '-NoExit', '-NoProfile', '-Command',
    "`$host.UI.RawUI.WindowTitle = 'Doc-QA frontend (close this window to stop)'; npm run dev"
)
$up = Wait-Until 60 { Test-Url 'http://localhost:5173' }
if (-not $up) { Fail "The frontend did not start. Look at the 'Doc-QA frontend' window for the error." }
Ok "Frontend is running on http://localhost:5173"

Start-Process 'http://localhost:5173'
Write-Host ""
Write-Host "  Doc-QA is ready:  http://localhost:5173" -ForegroundColor Green
Write-Host "  To stop it, double-click stop.bat (or close the two Doc-QA windows)." -ForegroundColor Gray
Write-Host ""
