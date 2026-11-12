@echo off
echo ========================================================
echo Starting MATRIQ Frontend (Vite on port 5173)
echo ========================================================

cd /d "%~dp0frontend"
npm run dev
pause
