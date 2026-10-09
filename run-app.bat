@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0run-app.ps1"
set "result=%ERRORLEVEL%"
if not "%result%"=="0" (
    echo.
    echo OpenSong could not be started. Review the error above.
    pause
)
exit /b %result%