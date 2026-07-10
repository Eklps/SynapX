@echo off
setlocal enabledelayedexpansion

REM AgentX one-click start script - Windows version
REM Supports deployment modes: local/production/external

REM ANSI color codes
set GREEN=[32m
set YELLOW=[33m
set RED=[31m
set BLUE=[34m
set NC=[0m

REM Banner
echo %BLUE%
echo    ███████ ███████ ███   ███ ██   ██ ███████ ███████
echo     ██    ██     █████  ████ ██  ██  ██     ██     ██
echo     ██    █████  ██ ████ ██ █████   █████  ████████
echo     ██    ██     ██  ████ ██ ██  ██  ██     ██   ██
echo     ██    ███████ ██   ███  ██   ██ ███████ ██    ██
echo %NC%
echo %GREEN%              SynapX - AI Agent Platform (Dev Deploy)%NC%
echo %BLUE%========================================================%NC%
echo.

echo %YELLOW%AgentX development environment startup%NC%
echo This script is for local development.
echo For production deployment, see: docs/deployment/PRODUCTION_DEPLOY.md
echo.

REM Check Docker
:check_docker
where docker >nul 2>&1
if errorlevel 1 (
    echo %RED%Error: Docker is not installed. Please install Docker Desktop first.%NC%
    pause
    exit /b 1
)

docker compose version >nul 2>&1
if errorlevel 1 (
    echo %RED%Error: Docker Compose is not installed or version is too old.%NC%
    pause
    exit /b 1
)

echo %GREEN%[OK] Docker environment check passed%NC%
echo.

REM Development mode config
:set_development_mode
set MODE=dev
set ENV_FILE=.env.local.example
set PROFILE=local,dev
set DOCKERFILE_SUFFIX=.dev

echo %GREEN%[...] Starting development mode%NC%
echo   - Built-in database + message queue
echo   - Code hot-reload support
echo   - Database management tool (Adminer)
echo   - Debug port exposed
echo.

REM Prepare .env
:prepare_env
if not exist ".env" (
    echo %YELLOW%Creating .env config file...%NC%
    if exist "%ENV_FILE%" (
        copy "%ENV_FILE%" ".env" >nul
        echo %GREEN%[OK] Created .env from template: %ENV_FILE%%NC%
    ) else (
        echo %RED%Error: template file %ENV_FILE% not found%NC%
        pause
        exit /b 1
    )
) else (
    echo %GREEN%[OK] Using existing .env config file%NC%
)
echo.

REM Start services
:start_services
echo %BLUE%Starting AgentX services...%NC%
echo Deploy mode: %MODE%
echo Docker Compose Profile: %PROFILE%
echo.

REM Set environment variables
set COMPOSE_PROFILES=%PROFILE%
set DOCKERFILE_SUFFIX=%DOCKERFILE_SUFFIX%

REM Start services (multiple profiles)
echo %YELLOW%Building and starting containers...%NC%
docker compose --profile local --profile dev up -d --build

if errorlevel 1 (
    echo.
    echo %RED%[FAIL] Service startup failed. Check the error above.%NC%
    echo.
    echo %YELLOW%Troubleshooting commands:%NC%
    echo   View logs:        docker compose logs
    echo   Container status: docker compose ps
    echo   Rebuild:          docker compose build --no-cache
    echo.
    echo %YELLOW%If you see "dockerDesktopLinuxEngine not found",%NC%
    echo %YELLOW%start Docker Desktop first, then re-run this script.%NC%
    pause
    exit /b 1
)

echo.
echo %GREEN%[DONE] AgentX started successfully!%NC%
echo.
echo %BLUE%Service URLs:%NC%
echo   Frontend:      http://localhost:3000
echo   Backend API:   http://localhost:8088
echo   API Gateway:   http://localhost:8081

if "%MODE%"=="dev" (
    echo   DB Adminer:    http://localhost:8082
)

echo.
echo %BLUE%Default accounts:%NC%
echo   Admin: admin@agentx.ai / admin123
echo   User:  test@agentx.ai / test123

echo.
echo %YELLOW%Common commands:%NC%
echo   View logs:   docker compose logs -f
echo   Stop:        docker compose down
echo   Restart:     docker compose restart
echo   Status:      docker compose ps
