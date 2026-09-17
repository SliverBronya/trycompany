@echo off
chcp 65001 >nul
title 田诊助手 · 打包 APK

echo.
echo   ============================================================
echo     TianZhen Assistant  -  Build Android APK
echo   ============================================================
echo.

cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\build-apk.ps1" %*

echo.
echo   ------------------------------------------------------------
echo   Press any key to close this window.
echo   ------------------------------------------------------------
pause >nul
