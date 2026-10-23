@echo off
setlocal
for %%I in ("%~dp0..") do set "OPENSONG_ROOT=%%~fI"
call "%OPENSONG_ROOT%\gradlew.bat" -p "%OPENSONG_ROOT%\OpenSongWeb" desktopRun
set "result=%ERRORLEVEL%"
if not "%result%"=="0" (
    echo.
    echo OpenSongWeb could not be started. Review the error above.
    pause
)
exit /b %result%
