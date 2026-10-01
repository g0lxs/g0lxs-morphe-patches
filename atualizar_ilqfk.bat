@echo off
chcp 65001 >nul
title Atualizador Ilqfk (Finch)
cd /d "%~dp0"

echo ============================================================
echo   Iniciando atualizador Ilqfk...
echo ============================================================
echo.

python tools\update_ilqfk.py %*

echo.
echo ============================================================
pause
