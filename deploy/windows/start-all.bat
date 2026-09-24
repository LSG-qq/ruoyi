@echo off
chcp 65001 >nul
setlocal
rem =====================================================================
rem  启动全部服务（两个后端 jar + nginx）
rem  用法：双击本文件。首次使用请先确认 D:\ruoyi\app 下已有两个 jar。
rem =====================================================================

set ROOT=D:\ruoyi
set APP=%ROOT%\app
set JAVA=D:\JDK\bin\java.exe
set NGINX=D:\nginx\nginx.exe

if not exist "%JAVA%" (
    echo [错误] 找不到 java：%JAVA%
    echo        请修改本文件顶部的 JAVA 变量。
    pause & exit /b 1
)
if not exist "%APP%\ruoyi-admin.jar" (
    echo [错误] 找不到 %APP%\ruoyi-admin.jar，请先执行打包并拷贝产物。
    pause & exit /b 1
)
if not exist "%APP%\ruoyi-client.jar" (
    echo [错误] 找不到 %APP%\ruoyi-client.jar，请先执行打包并拷贝产物。
    pause & exit /b 1
)

echo [1/3] 启动管理后台（8080）...
start "ruoyi-admin" /min "%JAVA%" -Xms512m -Xmx1024m -Duser.timezone=Asia/Shanghai -jar "%APP%\ruoyi-admin.jar"

echo [2/3] 启动借阅终端（8081）...
start "ruoyi-client" /min "%JAVA%" -Xms512m -Xmx1024m -Duser.timezone=Asia/Shanghai -jar "%APP%\ruoyi-client.jar"

echo      等待后端就绪（约 30 秒）...
timeout /t 30 /nobreak >nul

if exist "%NGINX%" (
    echo [3/3] 启动 nginx（80 / 81）...
    start "nginx" /min /D "D:\nginx" nginx.exe
) else (
    echo [3/3] 未找到 %NGINX%，跳过。前端页面将无法访问。
)

echo.
echo 启动完毕。本机访问：
echo    管理后台  http://localhost/
echo    借阅终端  http://localhost:81/
echo 局域网内其它机器把 localhost 换成这台电脑的 IP 即可。
echo.
echo 关闭窗口不会停止服务；要停止请运行 stop-all.bat。
pause
