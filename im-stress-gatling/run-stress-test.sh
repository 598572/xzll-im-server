#!/bin/bash

# IM Gatling 压力测试快速启动脚本
# 
# 使用方法：
# ./run-stress-test.sh                                    # 使用默认配置
# ./run-stress-test.sh 10000                              # 1万用户，其他默认
# ./run-stress-test.sh 10000 120 600                      # 1万用户，2分钟启动，测试10分钟
# ./run-stress-test.sh 10000 120 600 1000                 # 同上 + 消息间隔1秒
# ./run-stress-test.sh 10000 120 600 1000 30000           # 同上 + 心跳间隔30秒
# ./run-stress-test.sh 10000 120 600 0                    # 只测连接，不发消息（消息间隔=0）
#
# 环境变量方式：
# TARGET_HOST=120.46.85.43 TARGET_PORT=80 MSG_INTERVAL_MS=2000 ./run-stress-test.sh 10000

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║            IM Gatling 压力测试启动脚本                       ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ==================== 配置区 ====================
# 可以通过命令行参数覆盖（按顺序）
USERS=${1:-10000}                    # 用户数（默认 1 万）
RAMP_UP=${2:-120}                    # 启动时间（秒，默认 2 分钟）
DURATION=${3:-600}                   # 测试时长（秒，默认 10 分钟）
MSG_INTERVAL=${4:-5000}              # 消息发送间隔（毫秒，默认 5 秒，设为 0 则不发消息）
HEARTBEAT_INTERVAL=${5:-30000}       # 心跳间隔（毫秒，默认 30 秒）

# 服务器配置（可通过环境变量覆盖）
TARGET_HOST=${TARGET_HOST:-120.46.85.43}
TARGET_PORT=${TARGET_PORT:-80}
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

# Token（如果需要自定义）
TEST_TOKEN=${TEST_TOKEN:-"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VyX25hbWUiOiJoeHkxMTIyMzMiLCJzY29wZSI6WyJhbGwiXSwiZGV2aWNlX3R5cGUiOjEsImlkIjoxMjQ5NDg1NjcwNDAsImV4cCI6MTc2NDY3MjkyNywiYXV0aG9yaXRpZXMiOlsiQURNSU4iXSwianRpIjoiOWM5Yzk1ODctY2Q2MC00NGZhLWEyYmMtMDlhOGJiN2JmZDUxIiwiY2xpZW50X2lkIjoiY2xpZW50LWFwcCJ9.WS9BxHzBIY-vAaNomuX0toMOLeXDUo0K_q4C927pvN699v20lpyz1WKsDPS3rnVx-RuoIaWS86lpI273VTEpX7x4g7xlRlhP50Wwhehtp0xM_YTG2oYYd-JUFWxD3a-RDmu4zl39WqUcRoB5inYHU8vaa12dbAUWT7OJAGBgIE5B03xzYs-2hKRrxMLr60SSliTVd8GA_Ys7OBqwmgcp7upiECqQ0l7bW3WIZR9auhLket6RkbqThoA6s5PREdpG5t5d9zJ4VPrKCj4EAYlo-fvs2Ej01cDxftZHGmrCMU6nNH1cIbbtnjLsCINkbS1WdITr4uRZwwPJ_ez85DpijA"}
# ===============================================

echo -e "${YELLOW}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${YELLOW}│  压测配置                                                    │${NC}"
echo -e "${YELLOW}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${YELLOW}│  目标服务器: ${NC}$TARGET_HOST:$TARGET_PORT"
echo -e "${YELLOW}│  机器编号:   ${NC}$MACHINE_ID"
echo -e "${YELLOW}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${YELLOW}│  用户数:     ${NC}$USERS"
echo -e "${YELLOW}│  启动时间:   ${NC}$RAMP_UP 秒 ($(echo "scale=1; $RAMP_UP / 60" | bc) 分钟)"
echo -e "${YELLOW}│  测试时长:   ${NC}$DURATION 秒 ($(echo "scale=1; $DURATION / 60" | bc) 分钟)"
echo -e "${YELLOW}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${YELLOW}│  消息发送:   ${NC}$ENABLE_MSG_SEND"
if [ "$ENABLE_MSG_SEND" = "true" ]; then
echo -e "${YELLOW}│  消息间隔:   ${NC}$MSG_INTERVAL ms ($(echo "scale=1; $MSG_INTERVAL / 1000" | bc) 秒)"
echo -e "${YELLOW}│  预期QPS:    ${NC}${BLUE}$EXPECTED_QPS${NC} (连接数 × 1000 / 消息间隔)"
fi
echo -e "${YELLOW}│  心跳间隔:   ${NC}$HEARTBEAT_INTERVAL ms ($(echo "scale=1; $HEARTBEAT_INTERVAL / 1000" | bc) 秒)"
echo -e "${YELLOW}└──────────────────────────────────────────────────────────────┘${NC}"
echo ""

# 检查 Maven 是否安装
if ! command -v mvn &> /dev/null; then
    echo -e "${RED}❌ 错误: Maven 未安装${NC}"
    echo "请先安装 Maven: https://maven.apache.org/install.html"
    exit 1
fi

# 检查 Java 版本
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
if [ "$JAVA_VERSION" -lt 11 ]; then
    echo -e "${RED}❌ 错误: 需要 Java 11 或更高版本${NC}"
    echo "当前版本: $(java -version 2>&1 | head -n 1)"
    exit 1
fi

echo -e "${GREEN}✅ 环境检查通过 (Java $JAVA_VERSION)${NC}"
echo ""

# 检查是否需要编译
if [ ! -d "target" ]; then
    echo -e "${YELLOW}首次运行，正在编译项目...${NC}"
    mvn clean compile
    echo ""
fi

# 显示使用提示
echo -e "${BLUE}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${BLUE}│  快捷命令参考                                                │${NC}"
echo -e "${BLUE}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${BLUE}│  只测连接:    ${NC}./run-stress-test.sh 10000 120 300 0"
echo -e "${BLUE}│  低频消息:    ${NC}./run-stress-test.sh 10000 120 300 5000"
echo -e "${BLUE}│  高频消息:    ${NC}./run-stress-test.sh 10000 120 300 1000"
echo -e "${BLUE}│  极限测试:    ${NC}./run-stress-test.sh 30000 300 600 500"
echo -e "${BLUE}└──────────────────────────────────────────────────────────────┘${NC}"
echo ""

# 启动压测
echo -e "${GREEN}🚀 开始压力测试...${NC}"
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
        echo "  $LATEST_REPORT"
        echo ""
        echo -e "${YELLOW}在浏览器中打开报告：${NC}"
        
        # 根据操作系统自动打开报告
        if [[ "$OSTYPE" == "darwin"* ]]; then
            # macOS
            open "$LATEST_REPORT"
        elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
            # Linux
            xdg-open "$LATEST_REPORT" 2>/dev/null || echo "  请手动打开: $LATEST_REPORT"
        else
            echo "  请手动打开: $LATEST_REPORT"
        fi
    fi
else
    echo ""
    echo -e "${RED}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${RED}║                  压测失败！请检查日志                        ║${NC}"
    echo -e "${RED}╚══════════════════════════════════════════════════════════════╝${NC}"
    exit 1
fi
