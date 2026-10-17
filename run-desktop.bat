@echo off
title Echo Desktop - Run
echo Menjalankan Echo Desktop...
echo.
call "%~dp0gradlew.bat" :desktop:run
pause
