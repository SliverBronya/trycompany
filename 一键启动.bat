@echo off
chcp 65001 >nul
title 田诊助手 · 一键启动

echo.
echo   ============================================================
echo     田诊助手  TIANZHEN  -  One Click Start
echo   ============================================================
echo.

cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\start-all.ps1" %*

echo.
echo   ------------------------------------------------------------
echo   服务已在后台运行，关掉这个窗口不影响它们。
echo   To stop everything:  scripts\stop-all.ps1
echo   ------------------------------------------------------------
echo.
pause
