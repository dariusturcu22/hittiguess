# Spins up the whole app (Postgres, backend, AI service, frontend) with one
# command, for Windows PowerShell users who don't have Git Bash / make
# installed. macOS/Linux/Git Bash users should use scripts/dev.sh instead.
#
# Ctrl+C stops the backend, AI service, and frontend. The database containers
# keep running (docker compose -f backend/docker-compose.yml down to stop them).
#
# Cleanup on Ctrl+C is best-effort, same caveat as scripts/dev.sh: if a
# process is still running afterwards, check Task Manager for java.exe,
# python.exe, and node.exe and end it manually.

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

if (-not (Test-Path "backend\.env")) {
    Write-Error "backend\.env is missing. Copy backend\.env.example to backend\.env and fill in real values first."
    exit 1
}

Write-Host "Starting Postgres containers..."
docker compose -f backend/docker-compose.yml up -d

# Spring Boot doesn't load .env files itself; load backend\.env into this
# process's environment so the backend child process below inherits it.
Get-Content "backend\.env" | ForEach-Object {
    if ($_ -match '^\s*([^#=\s][^=]*)\s*=\s*(.*)\s*$') {
        [System.Environment]::SetEnvironmentVariable($matches[1].Trim(), $matches[2].Trim())
    }
}

$pythonExe = Join-Path $root "ai\.venv\Scripts\python.exe"

$backend = Start-Process -FilePath "$root\backend\mvnw.cmd" -ArgumentList "spring-boot:run" `
    -WorkingDirectory "$root\backend" -NoNewWindow -PassThru
$ai = Start-Process -FilePath $pythonExe -ArgumentList "-m", "uvicorn", "app.main:app", "--reload" `
    -WorkingDirectory "$root\ai" -NoNewWindow -PassThru
$frontend = Start-Process -FilePath "$root\frontend\node_modules\.bin\next.cmd" -ArgumentList "dev" `
    -WorkingDirectory "$root\frontend" -NoNewWindow -PassThru

Write-Host @"

hittiguess is starting up. Give it a minute for all three to finish booting.

  frontend   http://localhost:3000
  backend    http://localhost:8080
  ai         http://localhost:8000
  postgres   localhost:5500 (core), localhost:5501 (analytics)

Press Ctrl+C to stop the backend, AI service, and frontend.
"@

try {
    Wait-Process -Id $backend.Id, $ai.Id, $frontend.Id
}
finally {
    Write-Host "`nStopping backend, AI service, and frontend..."
    foreach ($p in @($backend, $ai, $frontend)) {
        if ($p -and -not $p.HasExited) {
            taskkill /F /T /PID $p.Id 2>$null | Out-Null
        }
    }
}
