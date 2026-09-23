#!/bin/sh
# ./ry.sh <start|stop|restart|status> [jar名]
#
# 第二个参数指定要管理的服务，默认 ruoyi-admin.jar（管理后台）。
# 本工程有两个可独立启动的入口，两者可以同时运行：
#   ./ry.sh start                    启动管理后台（ruoyi-admin.jar，端口 8080）
#   ./ry.sh start ruoyi-client.jar   启动终端服务（ruoyi-client.jar，端口 8081）
#   ./ry.sh stop ruoyi-client.jar    只停终端服务，不影响管理后台
AppName=${2:-ruoyi-admin.jar}

# JVM参数
JVM_OPTS="-Dname=$AppName  -Duser.timezone=Asia/Shanghai -Xms512m -Xmx1024m -XX:MetaspaceSize=128m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError -XX:+PrintGCDateStamps  -XX:+PrintGCDetails -XX:NewRatio=1 -XX:SurvivorRatio=30 -XX:+UseParallelGC -XX:+UseParallelOldGC"
APP_HOME=`pwd`
LOG_PATH=$APP_HOME/logs/$AppName.log

if [ "$1" = "" ];
then
    echo -e "\033[0;31m 未输入操作名 \033[0m  \033[0;34m {start|stop|restart|status} \033[0m"
    exit 1
fi

# 脚本按「jar 与脚本同目录」使用，找不到 jar 就直接退出：
# 否则 start 会在后台起一个注定失败、日志还被丢进 /dev/null 没人看得见的进程
if [ ! -f "$AppName" ];
then
    echo -e "\033[0;31m 当前目录下找不到 $AppName \033[0m  \033[0;34m 请先 mvn package，并在 jar 同级目录执行 \033[0m"
    exit 1
fi

function start()
{
    PID=`ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}'`

	if [ x"$PID" != x"" ]; then
	    echo "$AppName is running..."
	else
		nohup java $JVM_OPTS -jar $AppName > /dev/null 2>&1 &
		echo "Start $AppName success..."
	fi
}

function stop()
{
    echo "Stop $AppName"

	PID=""
	query(){
		PID=`ps -ef |grep java|grep $AppName|grep -v grep|awk '{print $2}'`
	}

	query
	if [ x"$PID" != x"" ]; then
		kill -TERM $PID
		echo "$AppName (pid:$PID) exiting..."
		while [ x"$PID" != x"" ]
		do
			sleep 1
			query
		done
		echo "$AppName exited."
	else
		echo "$AppName already stopped."
	fi
}

function restart()
{
    stop
    sleep 2
    start
}

function status()
{
    PID=`ps -ef |grep java|grep $AppName|grep -v grep|wc -l`
    if [ $PID != 0 ];then
        echo "$AppName is running..."
    else
        echo "$AppName is not running..."
    fi
}

case $1 in
    start)
    start;;
    stop)
    stop;;
    restart)
    restart;;
    status)
    status;;
    *)

esac
