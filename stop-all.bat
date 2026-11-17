@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop.ps1" -All
timeout /t 4 >nul
