# Doc-QA stop: stops the backend and frontend. Add -All to also stop the database.
param([switch]$All)

$Root = Split-Path -Parent $MyInvocation.MyCommand.Path

$procs = Get-CimInstance Win32_Process | Where-Object {
    ($_.Name -eq 'java.exe' -and $_.CommandLine -match 'doc-reader|docqa|DocQaApplication|spring-boot') -or
    ($_.Name -eq 'node.exe' -and $_.CommandLine -match 'vite') -or
    ($_.Name -eq 'cmd.exe' -and $_.CommandLine -match 'npm run dev|mvnw') -or
    ($_.Name -eq 'powershell.exe' -and $_.CommandLine -match 'Doc-QA (backend|frontend)')
}
$procs | ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
Write-Host "Stopped backend and frontend ($(@($procs).Count) processes)." -ForegroundColor Green

if ($All) {
    Push-Location $Root
    try { cmd /c "docker compose down >nul 2>&1" } finally { Pop-Location }
    Write-Host "Stopped the database. Your chats and files are kept." -ForegroundColor Green
} else {
    Write-Host "The database is still running (your data is safe). Use stop-all.bat to stop it too." -ForegroundColor Gray
}
