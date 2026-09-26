@echo off
chcp 65001 >nul
cd /d "c:\Users\shayin\WorkBuddy AI\2026-09-05-11-57-48\scheduling-system\frontend"
if exist "accept-frontend.log" del "accept-frontend.log"
start "scheduling-frontend-dev" /min cmd /c "npm run dev > accept-frontend.log 2>&1"
echo frontend dev server started, check accept-frontend.log
