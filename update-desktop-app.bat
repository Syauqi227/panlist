@echo off
title Update Metrolist Desktop
net session >nul 2>&1
if %errorlevel% neq 0 (
    echo Memerlukan hak Administrator untuk memperbarui Program Files.
    echo Menjalankan ulang dengan hak Administrator...
    powershell -Command "Start-Process '%~f0' -Verb RunAs"
    exit /b
)

echo Menutup proses Metrolist jika sedang berjalan...
taskkill /f /im Metrolist.exe >nul 2>&1

echo Menyalin file biner dan ikon terbaru ke C:\Program Files\Metrolist...
copy /y "%~dp0desktop\build\compose\binaries\main\app\Metrolist\Metrolist.exe" "C:\Program Files\Metrolist\Metrolist.exe"
xcopy /s /e /y "%~dp0desktop\build\compose\binaries\main\app\Metrolist\app" "C:\Program Files\Metrolist\app\"

echo.
echo ========================================================
echo  Metrolist Desktop berhasil diperbarui dengan ikon baru!
echo ========================================================
echo.
pause
