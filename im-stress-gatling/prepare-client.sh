#!/bin/bash

# IM Gatling 压测客户端环境准备脚本
# 
# 功能：
# 1. 检查并优化系统参数（ulimit、网络等）
# 2. 检查 Java 和 Maven 环境
# 3. 编译项目
#
# 使用方法：
# ./prepare-client.sh

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║         IM Gatling 压测客户端环境准备脚本                   ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# ==================== 1. 检查操作系统 ====================
echo -e "${BLUE}【1/6】检查操作系统...${NC}"
OS_TYPE=$(uname -s)
echo -e "   操作系统: ${GREEN}$OS_TYPE${NC}"
echo ""

# ==================== 2. 检查 Java 环境 ====================
echo -e "${BLUE}【2/6】检查 Java 环境...${NC}"
if command -v java &> /dev/null; then
    JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{print $1}')
    if [ "$JAVA_VERSION" -ge 11 ]; then
        echo -e "   Java 版本: ${GREEN}✅ $(java -version 2>&1 | head -n 1)${NC}"
    else
        echo -e "   ${RED}❌ Java 版本过低（需要 >= 11）${NC}"
        echo -e "   当前版本: $(java -version 2>&1 | head -n 1)"
        exit 1
    fi
else
    echo -e "   ${RED}❌ Java 未安装${NC}"
    echo -e "   请安装 JDK 11 或更高版本: https://adoptium.net/"
    exit 1
fi
echo ""

# ==================== 3. 检查 Maven 环境 ====================
echo -e "${BLUE}【3/6】检查 Maven 环境...${NC}"
if command -v mvn &> /dev/null; then
    MVN_VERSION=$(mvn -version | head -n 1)
    echo -e "   Maven 版本: ${GREEN}✅ $MVN_VERSION${NC}"
else
    echo -e "   ${RED}❌ Maven 未安装${NC}"
    echo -e "   请安装 Maven: https://maven.apache.org/install.html"
    exit 1
fi
echo ""

# ==================== 4. 优化文件句柄限制 ====================
echo -e "${BLUE}【4/6】优化文件句柄限制...${NC}"
CURRENT_ULIMIT=$(ulimit -n)
echo -e "   当前限制: ${YELLOW}$CURRENT_ULIMIT${NC}"

# 根据操作系统选择不同的配置方法
if [[ "$OS_TYPE" == "Darwin" ]]; then
    # macOS
    RECOMMENDED_LIMIT=65536
    echo -e "   建议限制: ${GREEN}$RECOMMENDED_LIMIT${NC}"
    
    # 尝试临时提升
    ulimit -n $RECOMMENDED_LIMIT 2>/dev/null
    if [ $? -eq 0 ]; then
        echo -e "   ${GREEN}✅ 临时提升成功: $(ulimit -n)${NC}"
    else
        echo -e "   ${YELLOW}⚠️  无法自动提升，请手动执行:${NC}"
        echo -e "      ${BLUE}ulimit -n $RECOMMENDED_LIMIT${NC}"
    fi
    
    # 提示永久配置方法
    echo ""
    echo -e "   ${YELLOW}💡 如需永久提升，请执行:${NC}"
    echo -e "      ${BLUE}sudo launchctl limit maxfiles 65536 200000${NC}"
    echo -e "      ${BLUE}然后重新登录${NC}"
    
elif [[ "$OS_TYPE" == "Linux" ]]; then
    # Linux
    RECOMMENDED_LIMIT=100000
    echo -e "   建议限制: ${GREEN}$RECOMMENDED_LIMIT${NC}"
    
    # 尝试临时提升
    ulimit -n $RECOMMENDED_LIMIT 2>/dev/null
    if [ $? -eq 0 ]; then
        echo -e "   ${GREEN}✅ 临时提升成功: $(ulimit -n)${NC}"
    else
        echo -e "   ${YELLOW}⚠️  无法自动提升，请手动执行:${NC}"
        echo -e "      ${BLUE}ulimit -n $RECOMMENDED_LIMIT${NC}"
    fi
    
    # 检查是否需要永久配置
    if [ -f /etc/security/limits.conf ]; then
        SOFT_LIMIT=$(grep "^* soft nofile" /etc/security/limits.conf 2>/dev/null | awk '{print $4}')
        HARD_LIMIT=$(grep "^* hard nofile" /etc/security/limits.conf 2>/dev/null | awk '{print $4}')
        
        if [ -z "$SOFT_LIMIT" ] || [ "$SOFT_LIMIT" -lt "$RECOMMENDED_LIMIT" ]; then
            echo ""
            echo -e "   ${YELLOW}💡 如需永久提升，请执行:${NC}"
            echo -e "      ${BLUE}sudo tee -a /etc/security/limits.conf <<EOF${NC}"
            echo -e "      ${BLUE}*  soft  nofile  $RECOMMENDED_LIMIT${NC}"
            echo -e "      ${BLUE}*  hard  nofile  $RECOMMENDED_LIMIT${NC}"
            echo -e "      ${BLUE}EOF${NC}"
            echo -e "      ${BLUE}然后重新登录${NC}"
        fi
    fi
fi
echo ""

# ==================== 5. 检查网络连通性 ====================
echo -e "${BLUE}【5/6】检查网络连通性...${NC}"
TARGET_HOST=${TARGET_HOST:-"192.168.1.150"}
TARGET_PORT=${TARGET_PORT:-"10001"}

echo -e "   目标服务器: ${YELLOW}$TARGET_HOST:$TARGET_PORT${NC}"

# ping 测试
if command -v ping &> /dev/null; then
    if ping -c 1 -W 2 $TARGET_HOST &> /dev/null; then
        echo -e "   Ping 测试: ${GREEN}✅ 通畅${NC}"
    else
        echo -e "   Ping 测试: ${RED}❌ 不通${NC}"
        echo -e "   ${YELLOW}⚠️  请检查网络配置或防火墙${NC}"
    fi
fi

# telnet 测试（如果可用）
if command -v telnet &> /dev/null; then
    timeout 2 bash -c "echo > /dev/tcp/$TARGET_HOST/$TARGET_PORT" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo -e "   端口测试: ${GREEN}✅ 可连接${NC}"
    else
        echo -e "   端口测试: ${RED}❌ 无法连接${NC}"
        echo -e "   ${YELLOW}⚠️  请检查服务端是否启动${NC}"
    fi
fi
echo ""

# ==================== 6. 编译项目 ====================
echo -e "${BLUE}【6/6】编译项目...${NC}"
if [ ! -d "target" ] || [ ! -f "target/test-classes/com/xzll/stress/ImWebSocketSimulation.class" ]; then
    echo -e "   ${YELLOW}正在编译...${NC}"
    mvn clean compile -q
    if [ $? -eq 0 ]; then
        echo -e "   ${GREEN}✅ 编译成功${NC}"
    else
        echo -e "   ${RED}❌ 编译失败${NC}"
        exit 1
    fi
else
    echo -e "   ${GREEN}✅ 项目已编译${NC}"
fi
echo ""

# ==================== 完成 ====================
echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                     ✅ 准备完成！                            ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

echo -e "${YELLOW}💡 下一步操作建议：${NC}"
echo ""
echo -e "   ${BLUE}1. 小规模测试（100 连接）${NC}"
echo -e "      ${GREEN}./run-stress-test.sh 100 30 120 5000 30000${NC}"
echo ""
echo -e "   ${BLUE}2. 中等规模测试（1000 连接）${NC}"
echo -e "      ${GREEN}./run-stress-test.sh 1000 60 300 5000 30000${NC}"
echo ""
echo -e "   ${BLUE}3. 大规模测试（10000 连接）${NC}"
echo -e "      ${GREEN}./run-stress-test.sh 10000 120 600 5000 30000${NC}"
echo ""
echo -e "   ${BLUE}4. 查看详细指南${NC}"
echo -e "      ${GREEN}cat STRESS_TEST_GUIDE.md${NC}"
echo ""

# 显示当前系统状态
echo -e "${YELLOW}📊 当前系统状态：${NC}"
echo -e "   文件句柄限制: ${GREEN}$(ulimit -n)${NC}"
echo -e "   Java 版本: ${GREEN}$JAVA_VERSION${NC}"
echo -e "   Maven: ${GREEN}已安装${NC}"
echo -e "   项目状态: ${GREEN}已编译${NC}"
echo ""

echo -e "${GREEN}🚀 准备就绪！现在可以开始压测了！${NC}"


