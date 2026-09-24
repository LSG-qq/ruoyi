@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

echo ============================================================
echo   停止全部服务
echo   注意：会一并停掉占用 8080 / 8081 的进程，
echo         如果此时 IDEA 里正跑着本项目，也会被停掉。
echo ============================================================
echo.

echo [1/3] 停止 nginx ...
taskkill /F /IM nginx.exe >nul 2>&1
if errorlevel 1 (echo       nginx 未在运行) else (echo       nginx 已停止)

echo [2/3] 停止后端（按 8080 / 8081 端口找进程）...
for %%P in (8080 8081) do (
    set FOUND=0
    for /f "tokens=5" %%I in ('netstat -ano ^| findstr ":%%P " ^| findstr LISTENING') do (
        set FOUND=1
        echo       端口 %%P 被 PID %%I 占用，正在停止 ...
        taskkill /F /PID %%I >nul 2>&1
    )
    if "!FOUND!"=="0" echo       端口 %%P 没有进程在监听
)

echo [3/3] 完成。
echo.
pause
