@echo off
setlocal enabledelayedexpansion
REM Kill processes listening on a specified port
REM Usage: kill_port.bat <port_number>

set PORT=%1

if "%PORT%"=="" (
    echo Error: Port number not specified
    echo Usage: kill_port.bat ^<port_number^>
    exit /b 1
)

echo Checking for processes on port %PORT%...

REM Find processes listening on the port and kill them
set FOUND=0
for /f "tokens=5" %%a in ('netstat -ano ^| findstr :%PORT% ^| findstr LISTENING') do (
    set PID=%%a
    if defined PID (
        set FOUND=1
        echo Found process !PID! using port %PORT%. Terminating...
        taskkill /F /PID !PID! >nul 2>&1
        echo   Terminated process !PID!
    )
)

if !FOUND! equ 0 (
    echo Port %PORT% is available.
) else (
    timeout /t 1 /nobreak >nul
    echo Port %PORT% cleared.
)
endlocal
