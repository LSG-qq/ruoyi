@echo off

rem jar平级目录
rem 第一个参数指定要管理的服务，默认 ruoyi-admin.jar（管理后台）。本工程有两个可独立启动的入口：
rem   ry.bat                       管理后台（ruoyi-admin.jar，端口 8080）
rem   ry.bat ruoyi-client.jar      终端服务（ruoyi-client.jar，端口 8081）
rem 两者可以同时运行，按各自 jar 名分别启停。
set AppName=%1
if "%AppName%"=="" set AppName=ruoyi-admin.jar

if not exist "%AppName%" (
	echo.
	echo 当前目录下找不到 %AppName%
	echo 请先执行 mvn package，并在 jar 同级目录运行本脚本
	PAUSE
	EXIT /B 1
)

rem JVM参数
set JVM_OPTS="-Dname=%AppName%  -Duser.timezone=Asia/Shanghai -Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDateStamps  -XX:+PrintGCDetails -XX:NewRatio=1 -XX:SurvivorRatio=30 -XX:+UseParallelGC -XX:+UseParallelOldGC"


ECHO.
	ECHO.  [1] 启动%AppName%
	ECHO.  [2] 关闭%AppName%
	ECHO.  [3] 重启%AppName%
	ECHO.  [4] 启动状态 %AppName%
	ECHO.  [5] 退 出
ECHO.

ECHO.请输入选择项目的序号:
set /p ID=
	IF "%id%"=="1" GOTO start
	IF "%id%"=="2" GOTO stop
	IF "%id%"=="3" GOTO restart
	IF "%id%"=="4" GOTO status
	IF "%id%"=="5" EXIT
PAUSE
:start
    for /f "usebackq tokens=1-2" %%a in (`jps -l ^| findstr %AppName%`) do (
		set pid=%%a
		set image_name=%%b
	)
	if  defined pid (
		echo %%is running
		PAUSE
	)

start javaw %JVM_OPTS% -jar %AppName%

echo  starting……
echo  Start %AppName% success...
goto:eof

rem 函数stop通过jps命令查找pid并结束进程
:stop
	for /f "usebackq tokens=1-2" %%a in (`jps -l ^| findstr %AppName%`) do (
		set pid=%%a
		set image_name=%%b
	)
	if not defined pid (echo process %AppName% does not exists) else (
		echo prepare to kill %image_name%
		echo start kill %pid% ...
		rem 根据进程ID，kill进程
		taskkill /f /pid %pid%
	)
goto:eof
:restart
	call :stop
    call :start
goto:eof
:status
	for /f "usebackq tokens=1-2" %%a in (`jps -l ^| findstr %AppName%`) do (
		set pid=%%a
		set image_name=%%b
	)
	if not defined pid (echo process %AppName% is dead ) else (
		echo %image_name% is running
	)
goto:eof
