@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\package-apk.ps1"
exit /b %ERRORLEVEL%
