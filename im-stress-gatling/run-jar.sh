#!/bin/bash

# ============================================================================
# IM Gatling 压力测试 - JAR 包运行脚本（服务器专用）
# ============================================================================
# 
# 此脚本用于在服务器上直接运行编译好的 jar 包，无需 Maven
#
# 使用方法：
# ./run-jar.sh <目标IP> <目标端口> [用户数] [启动时间] [测试时长] [消息间隔ms]
#
# 示例：
# ./run-jar.sh 192.168.1.150 10001                    # 基本用法
# ./run-jar.sh 192.168.1.150 10001 10000              # 1万用户
# ./run-jar.sh 192.168.1.150 10001 10000 120 600 2000 # 完整参数
# ./run-jar.sh 127.0.0.1 10001 10000 120 300 0        # 只测连接
#
# ============================================================================

set -e

# ==================== JDK 配置（根据服务器实际情况修改） ====================
JAVA_HOME="/home/hzz/jdk/jdk-17.0.2"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
# ==========================================================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║        IM Gatling 压力测试 - JAR 包运行脚本                  ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ==================== 参数解析 ====================
TARGET_HOST=${1:-""}
TARGET_PORT=${2:-""}

# 检查必填参数
if [ -z "$TARGET_HOST" ] || [ -z "$TARGET_PORT" ]; then
    echo -e "${RED}❌ 错误: 必须指定目标 IP 和端口${NC}"
    echo ""
    echo -e "${YELLOW}使用方法:${NC}"
    echo "  ./run-jar.sh <目标IP> <目标端口> [用户数] [启动时间] [测试时长] [消息间隔ms]"
    echo ""
    echo -e "${YELLOW}示例:${NC}"
    echo "  ./run-jar.sh 192.168.1.150 10001"
    echo "  ./run-jar.sh 192.168.1.150 10001 10000"
    echo "  ./run-jar.sh 192.168.1.150 10001 10000 120 600 2000"
    echo "  ./run-jar.sh 127.0.0.1 10001 10000 120 300 0  # 只测连接"
    exit 1
fi

# 可选参数（带默认值）
USERS=${3:-10000}                    # 用户数（默认 1 万）
RAMP_UP=${4:-120}                    # 启动时间（秒，默认 2 分钟）
DURATION=${5:-600}                   # 测试时长（秒，默认 10 分钟）
MSG_INTERVAL=${6:-5000}              # 消息发送间隔（毫秒，默认 5 秒）
HEARTBEAT_INTERVAL=${7:-30000}       # 心跳间隔（毫秒，默认 30 秒）

# 机器编号（用于分布式压测）
MACHINE_ID=${MACHINE_ID:-0}

# 消息发送开关
if [ "$MSG_INTERVAL" -eq 0 ]; then
    ENABLE_MSG_SEND="false"
    MSG_INTERVAL=5000
else
    ENABLE_MSG_SEND="true"
fi

# 计算预期 QPS
if [ "$ENABLE_MSG_SEND" = "true" ]; then
    EXPECTED_QPS=$((USERS * 1000 / MSG_INTERVAL))
else
    EXPECTED_QPS=0
fi

# Token（默认使用压测专用Token，需要服务端开启 im.netty.auth.stress-test-enabled=true）
TEST_TOKEN=${TEST_TOKEN:-"STRESS_TEST_BYPASS_TOKEN"}

# ==================== 显示配置 ====================
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
echo -e "${YELLOW}│  预期QPS:    ${NC}${BLUE}$EXPECTED_QPS${NC}"
fi
echo -e "${YELLOW}│  心跳间隔:   ${NC}$HEARTBEAT_INTERVAL ms ($((HEARTBEAT_INTERVAL / 1000)) 秒)"
echo -e "${YELLOW}└──────────────────────────────────────────────────────────────┘${NC}"
echo ""

# ==================== 环境检查 ====================
if [ ! -d "$JAVA_HOME" ]; then
    echo -e "${RED}❌ 错误: JDK 目录不存在: $JAVA_HOME${NC}"
    echo "请修改脚本中的 JAVA_HOME 变量"
    exit 1
fi

if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ 错误: java 命令不可用${NC}"
    exit 1
fi

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

# 查找 jar 文件（支持多个可能的位置和文件名）
JAR_FILE=""
for pattern in \
    "$SCRIPT_DIR/im-stress-gatling.jar" \
    "$SCRIPT_DIR/im-stress-gatling-*.jar" \
    "$SCRIPT_DIR/target/im-stress-gatling.jar" \
    "$SCRIPT_DIR/target/im-stress-gatling-*.jar"; do
    for f in $pattern; do
        if [ -f "$f" ]; then
            JAR_FILE="$f"
            break 2
        fi
    done
done

# 检查 jar 文件是否存在
if [ -z "$JAR_FILE" ] || [ ! -f "$JAR_FILE" ]; then
    echo -e "${RED}❌ 错误: JAR 文件不存在${NC}"
    echo ""
    echo -e "${YELLOW}已检查以下位置:${NC}"
    echo "  - $SCRIPT_DIR/im-stress-gatling*.jar"
    echo "  - $SCRIPT_DIR/target/im-stress-gatling*.jar"
    echo ""
    echo -e "${YELLOW}请先在本地编译并上传:${NC}"
    echo "  1. 本地执行: cd im-stress-gatling && mvn clean package -DskipTests"
    echo "  2. 上传文件: scp target/im-stress-gatling.jar user@server:/path/to/"
    exit 1
fi

echo -e "${GREEN}✅ 找到 JAR 文件: $JAR_FILE${NC}"

echo -e "${GREEN}✅ 环境检查通过${NC}"
echo ""

# ==================== JVM 参数配置 ====================
if [ "$USERS" -ge 30000 ]; then
    JVM_OPTS="-Xmx8g -Xms4g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
elif [ "$USERS" -ge 10000 ]; then
    JVM_OPTS="-Xmx4g -Xms2g -XX:+UseG1GC -XX:MaxGCPauseMillis=100"
else
    JVM_OPTS="-Xmx2g -Xms1g -XX:+UseG1GC"
fi

echo -e "${YELLOW}JVM参数: $JVM_OPTS${NC}"
echo ""

# ==================== 创建结果目录 ====================
RESULTS_DIR="$SCRIPT_DIR/results"
mkdir -p "$RESULTS_DIR"

# ==================== 启动压测 ====================
echo -e "${GREEN}🚀 开始压力测试...${NC}"
echo -e "${GREEN}   目标: $TARGET_HOST:$TARGET_PORT${NC}"
echo -e "${GREEN}   用户: $USERS | 预期QPS: $EXPECTED_QPS${NC}"
echo ""

# 运行 Gatling
java $JVM_OPTS \
  -DTARGET_HOST=$TARGET_HOST \
  -DTARGET_PORT=$TARGET_PORT \
  -DMACHINE_ID=$MACHINE_ID \
  -DUSERS_PER_MACHINE=$USERS \
  -DRAMP_UP_TIME=$RAMP_UP \
  -DTEST_DURATION=$DURATION \
  -DMSG_INTERVAL_MS=$MSG_INTERVAL \
  -DHEARTBEAT_INTERVAL_MS=$HEARTBEAT_INTERVAL \
  -DENABLE_MSG_SEND=$ENABLE_MSG_SEND \
  -DTEST_TOKEN="$TEST_TOKEN" \
  -jar "$JAR_FILE" \
  --simulation com.xzll.stress.ImWebSocketSimulation \
  --results-folder "$RESULTS_DIR"

# ==================== 结果处理 ====================
if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                      压测完成！                              ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # 查找最新的报告
    LATEST_REPORT=$(find "$RESULTS_DIR" -name "index.html" -type f -print0 2>/dev/null | xargs -0 ls -t 2>/dev/null | head -n 1)
    
    if [ -n "$LATEST_REPORT" ]; then
        echo -e "${YELLOW}📊 报告路径:${NC}"
        echo "  $LATEST_REPORT"
        echo ""
        echo -e "${YELLOW}下载报告到本地查看:${NC}"
        echo "  scp -r user@server:$RESULTS_DIR ./gatling-reports"
    fi
else
    echo ""
    echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                  压测失败！请检查日志                        ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi

