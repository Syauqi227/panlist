@echo off
title Metrolist Desktop - Run
echo Menjalankan Metrolist Desktop...
echo.
call "%~dp0gradlew.bat" :desktop:run
pause
