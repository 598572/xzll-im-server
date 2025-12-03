#!/bin/bash

# ============================================================================
# IM Gatling 压力测试 - 服务器专用启动脚本
# ============================================================================
# 
# 使用方法：
# ./run-stress-test-server.sh <目标IP> <目标端口> [用户数] [启动时间] [测试时长] [消息间隔ms]
#
# 示例：
# ./run-stress-test-server.sh 192.168.1.150 10001                    # 基本用法
# ./run-stress-test-server.sh 192.168.1.150 10001 10000              # 1万用户
# ./run-stress-test-server.sh 192.168.1.150 10001 10000 120 600 2000 # 完整参数
# ./run-stress-test-server.sh 127.0.0.1 10001 10000 120 300 0        # 只测连接
#
# ============================================================================

set -e

# ==================== JDK 配置（服务器专用） ====================
JAVA_HOME="/home/hzz/jdk/jdk-17.0.2"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
# ================================================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║        IM Gatling 压力测试 - 服务器专用脚本                  ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ==================== 参数解析 ====================
# 必填参数
TARGET_HOST=${1:-""}
TARGET_PORT=${2:-""}

# 检查必填参数
if [ -z "$TARGET_HOST" ] || [ -z "$TARGET_PORT" ]; then
    echo -e "${RED}❌ 错误: 必须指定目标 IP 和端口${NC}"
    echo ""
    echo -e "${YELLOW}使用方法:${NC}"
    echo "  ./run-stress-test-server.sh <目标IP> <目标端口> [用户数] [启动时间] [测试时长] [消息间隔ms]"
    echo ""
    echo -e "${YELLOW}示例:${NC}"
    echo "  ./run-stress-test-server.sh 192.168.1.150 10001"
    echo "  ./run-stress-test-server.sh 192.168.1.150 10001 10000"
    echo "  ./run-stress-test-server.sh 192.168.1.150 10001 10000 120 600 2000"
    echo "  ./run-stress-test-server.sh 127.0.0.1 10001 10000 120 300 0  # 只测连接"
    exit 1
fi

# 可选参数（带默认值）
USERS=${3:-10000}                    # 用户数（默认 1 万）
RAMP_UP=${4:-120}                    # 启动时间（秒，默认 2 分钟）
DURATION=${5:-600}                   # 测试时长（秒，默认 10 分钟）
MSG_INTERVAL=${6:-5000}              # 消息发送间隔（毫秒，默认 5 秒，设为 0 则不发消息）
HEARTBEAT_INTERVAL=${7:-30000}       # 心跳间隔（毫秒，默认 30 秒）

# 机器编号（用于分布式压测，可通过环境变量设置）
MACHINE_ID=${MACHINE_ID:-0}

# 消息发送开关（消息间隔为 0 时自动关闭）
if [ "$MSG_INTERVAL" -eq 0 ]; then
    ENABLE_MSG_SEND="false"
    MSG_INTERVAL=5000  # 重置为默认值，避免除零错误
else
    ENABLE_MSG_SEND="true"
fi

# 计算预期 QPS
if [ "$ENABLE_MSG_SEND" = "true" ]; then
    EXPECTED_QPS=$((USERS * 1000 / MSG_INTERVAL))
else
    EXPECTED_QPS=0
fi

# Token（如果需要自定义，可通过环境变量 TEST_TOKEN 设置）
TEST_TOKEN=${TEST_TOKEN:-"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJoeHkxMTIyMzMiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxMjQ5NDg1NjcwNDAsImV4cCI6MTc2NDY3MjkyNywiYXV0aG9yaXRpZXMiOlsiQURNSU4iXSwianRpIjoiOWM5Yzk1ODctY2Q2MC00NGZhLWEyYmMtMDlhOGJiN2JmZDUxIiwiY2xpZW50X2lkIjoiY2xpZW50LWFwcCJ9.WS9BxHzBIY-vAaNomuX0toMOLeXDUo0K_q4C927pvN699v20lpyz1WKsDPS3rnVx-RuoIaWS86lpI273VTEpX7x4g7xlRlhP50Wwhehtp0xM_YTG2oYYd-JUFWxD3a-RDmu4zl39WqUcRoB5inYHU8vaa12dbAUWT7OJAGBgIE5B03xzYs-2hKRrxMLr60SSliTVd8GA_Ys7OBqwmgcp7upiECqQ0l7bW3WIZR9auhLket6RkbqThoA6s5PREdpG5t5d9zJ4VPrKCj4EAYlo-fvs2Ej01cDxftZHGmrCMU6nNH1cIbbtnjLsCINkbS1WdITr4uRZwwPJ_ez85DpijA"}
# ===============================================

echo -e "${YELLOW}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${YELLOW}│  环境配置                                                    │${NC}"
echo -e "${YELLOW}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${YELLOW}│  JAVA_HOME: ${NC}$JAVA_HOME"
echo -e "${YELLOW}│  Java版本:  ${NC}$(java -version 2>&1 | head -n 1)"
echo -e "${YELLOW}└──────────────────────────────────────────────────────────────┘${NC}"
echo ""

echo -e "${YELLOW}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${YELLOW}│  压测配置                                                    │${NC}"
echo -e "${YELLOW}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${YELLOW}│  目标服务器: ${NC}${BLUE}$TARGET_HOST:$TARGET_PORT${NC}"
echo -e "${YELLOW}│  机器编号:   ${NC}$MACHINE_ID"
echo -e "${YELLOW}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${YELLOW}│  用户数:     ${NC}$USERS"
echo -e "${YELLOW}│  启动时间:   ${NC}$RAMP_UP 秒 ($((RAMP_UP / 60)) 分 $((RAMP_UP % 60)) 秒)"
echo -e "${YELLOW}│  测试时长:   ${NC}$DURATION 秒 ($((DURATION / 60)) 分 $((DURATION % 60)) 秒)"
echo -e "${YELLOW}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${YELLOW}│  消息发送:   ${NC}$ENABLE_MSG_SEND"
if [ "$ENABLE_MSG_SEND" = "true" ]; then
echo -e "${YELLOW}│  消息间隔:   ${NC}$MSG_INTERVAL ms ($((MSG_INTERVAL / 1000)) 秒)"
echo -e "${YELLOW}│  预期QPS:    ${NC}${BLUE}$EXPECTED_QPS${NC} (连接数 × 1000 / 消息间隔)"
fi
echo -e "${YELLOW}│  心跳间隔:   ${NC}$HEARTBEAT_INTERVAL ms ($((HEARTBEAT_INTERVAL / 1000)) 秒)"
echo -e "${YELLOW}└──────────────────────────────────────────────────────────────┘${NC}"
echo ""

# 检查 JDK 目录
if [ ! -d "$JAVA_HOME" ]; then
    echo -e "${RED}❌ 错误: JDK 目录不存在: $JAVA_HOME${NC}"
    echo "请修改脚本中的 JAVA_HOME 变量"
    exit 1
fi

# 检查 java 命令
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ 错误: java 命令不可用${NC}"
    exit 1
fi

# 检查 Java 版本
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo -e "${RED}❌ 错误: 需要 Java 11 或更高版本${NC}"
    echo "当前版本: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

# 检查 Maven 是否安装
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ 错误: Maven 未安装${NC}"
    echo "请先安装 Maven: https://maven.apache.org/install.html"
    exit 1
fi

echo -e "${GREEN}✅ 环境检查通过${NC}"
echo ""

# 获取脚本所在目录（处理软链接情况）
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# 检查是否需要编译
if [ ! -d "target" ]; then
    echo -e "${YELLOW}首次运行，正在编译项目...${NC}"
    mvn clean compile -q
    echo -e "${GREEN}✅ 编译完成${NC}"
    echo ""
fi

# 显示使用提示
echo -e "${BLUE}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${BLUE}│  快捷命令参考                                                │${NC}"
echo -e "${BLUE}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${BLUE}│  只测连接:  ${NC}./run-stress-test-server.sh $TARGET_HOST $TARGET_PORT 10000 120 300 0"
echo -e "${BLUE}│  低频消息:  ${NC}./run-stress-test-server.sh $TARGET_HOST $TARGET_PORT 10000 120 300 5000"
echo -e "${BLUE}│  高频消息:  ${NC}./run-stress-test-server.sh $TARGET_HOST $TARGET_PORT 10000 120 300 1000"
echo -e "${BLUE}│  极限测试:  ${NC}./run-stress-test-server.sh $TARGET_HOST $TARGET_PORT 30000 300 600 500"
echo -e "${BLUE}└──────────────────────────────────────────────────────────────┘${NC}"
echo ""

# 启动压测
echo -e "${GREEN}🚀 开始压力测试...${NC}"
echo -e "${GREEN}   目标: $TARGET_HOST:$TARGET_PORT${NC}"
echo -e "${GREEN}   用户: $USERS | QPS: $EXPECTED_QPS${NC}"
echo ""

# JVM 优化参数（根据用户数调整）
if [ "$USERS" -ge 30000 ]; then
    MAVEN_OPTS="-Xmx8g -Xms4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
elif [ "$USERS" -ge 10000 ]; then
    MAVEN_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
else
    MAVEN_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"
fi
export MAVEN_OPTS

echo -e "${YELLOW}JVM参数: $MAVEN_OPTS${NC}"
echo ""

mvn gatling:test \
  -DTARGET_HOST=$TARGET_HOST \
  -DTARGET_PORT=$TARGET_PORT \
  -DMACHINE_ID=$MACHINE_ID \
  -DUSERS_PER_MACHINE=$USERS \
  -DRAMP_UP_TIME=$RAMP_UP \
  -DTEST_DURATION=$DURATION \
  -DMSG_INTERVAL_MS=$MSG_INTERVAL \
  -DHEARTBEAT_INTERVAL_MS=$HEARTBEAT_INTERVAL \
  -DENABLE_MSG_SEND=$ENABLE_MSG_SEND \
  -DTEST_TOKEN="$TEST_TOKEN"

# 检查执行结果
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                      压测完成！                              ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}📊 查看报告：${NC}"
    
    # 查找最新的报告
    LATEST_REPORT=$(find target/gatling -name "index.html" -type f -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -n 1)
    
    if [ -n "$LATEST_REPORT" ]; then
        echo "  报告路径: $SCRIPT_DIR/$LATEST_REPORT"
        echo ""
        echo -e "${YELLOW}下载报告到本地查看：${NC}"
        echo "  scp -r user@server:$SCRIPT_DIR/target/gatling ./gatling-reports"
    fi
else
    echo ""
    echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                  压测失败！请检查日志                        ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi

