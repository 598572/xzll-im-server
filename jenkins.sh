#!/bin/bash

args=$1
# 注意修改jenkinswar包的目录
jenkins_war_path="/usr/local/soft_hzz/jenkins"
# jenkins开放端口
jenkins_http_port="8079"
# java安装路径
java_home="/usr/lib/jvm/java-11-openjdk-11.0.22.0.7-1.el7_9.x86_64"
# 日志文件路径
jenkins_log_path="/tmp/data/logs/jenkins.log"

function isRunning(){
    local jenkinsPID=$(ps -ef | grep jenkins.war | grep -v grep | awk '{print $2}')
    if [ -z ${jenkinsPID} ]; then
        echo "0"
    else
        echo ${jenkinsPID}
    fi
}

# 停止jenkins
function stop(){
    local runFlag=$(isRunning)
    if [ ${runFlag} -eq "0" ]; then
        echo "Jenkins is already stopped."
    else
        kill -9 ${runFlag}
        echo "Stop Jenkins success."
    fi
}

# 启动jenkins
function start(){
    local runFlag=$(isRunning)
    echo "${runFlag}"
    if [ ${runFlag} -eq "0" ]; then
        nohup ${java_home}/bin/java -jar ${jenkins_war_path}/jenkins.war --httpPort=${jenkins_http_port} > ${jenkins_log_path} 2>&1 &
        if [ $? -eq 0 ]; then
            echo "Start Jenkins success."
            exit
        else
            echo "Start Jenkins fail."
        fi
    else
        echo "Jenkins is running now."
    fi
}

# 重启jenkins
function restart(){
    local runFlag=$(isRunning)
    if [ ${runFlag} -eq "0" ]; then
        echo "Jenkins is already stopped."
        exit
    else
        stop
        start
        echo "Restart Jenkins success."
    fi
}

# 根据输入的参数执行不同的动作
# 参数不能为空
if [ -z ${args} ]; then
    echo "Arg can not be null."
    exit
# 参数个数必须为1个
elif [ $# -ne 1 ]; then
    echo "Only one arg is required: start|stop|restart"
# 参数为start时启动jenkins
elif [ ${args} = "start" ]; then
    start
# 参数为stop时停止jenkins
elif [ ${args} = "stop" ]; then
    stop
# 参数为restart时重启jenkins
elif [ ${args} = "restart" ]; then
    restart
else
    echo "One of following args is required: start|stop|restart"
    exit 0
fi