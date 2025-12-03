#!/bin/bash

# ============================================================================
# IM Gatling 压力测试 - 本地打包脚本
# ============================================================================
# 
# 此脚本用于在本地编译打包，生成可在服务器上直接运行的 jar 包
#
# 使用方法：
# ./build-jar.sh              # 打包
# ./build-jar.sh upload       # 打包并上传到服务器
#
# ============================================================================

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          IM Gatling 压力测试 - 打包脚本                      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# 获取脚本所在目录
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
cd "$SCRIPT_DIR"

# ==================== 服务器配置（上传时使用） ====================
SERVER_USER=${SERVER_USER:-"hzz"}
SERVER_HOST=${SERVER_HOST:-"120.46.85.43"}
SERVER_PATH=${SERVER_PATH:-"/home/hzz/im-stress-test"}
# ================================================================

echo -e "${YELLOW}📦 开始编译打包...${NC}"
echo ""

# 先编译整个项目（确保 im-common 等依赖已安装）
cd ..
echo -e "${YELLOW}1. 安装依赖模块...${NC}"
mvn install -DskipTests -pl im-common -am -q
echo -e "${GREEN}   ✅ 依赖模块安装完成${NC}"

# 打包压测模块
cd im-stress-gatling
echo -e "${YELLOW}2. 编译压测模块...${NC}"
mvn clean compile test-compile -q
echo -e "${GREEN}   ✅ 编译完成${NC}"

echo -e "${YELLOW}3. 打包 Fat JAR...${NC}"
mvn package -DskipTests -q
echo -e "${GREEN}   ✅ 打包完成${NC}"

# 检查生成的 jar 文件
JAR_FILE="$SCRIPT_DIR/target/im-stress-gatling.jar"
if [ -f "$JAR_FILE" ]; then
    JAR_SIZE=$(ls -lh "$JAR_FILE" | awk '{print $5}')
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                    打包成功！                                ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}JAR 文件:${NC} $JAR_FILE"
    echo -e "${YELLOW}文件大小:${NC} $JAR_SIZE"
    echo ""
else
    echo -e "${RED}❌ 打包失败：JAR 文件未生成${NC}"
    exit 1
fi

# ==================== 上传到服务器 ====================
if [ "$1" = "upload" ]; then
    echo -e "${YELLOW}📤 准备上传到服务器...${NC}"
    echo ""
    
    if [ "$SERVER_HOST" = "your-server-ip" ]; then
        echo -e "${RED}❌ 请先配置服务器地址${NC}"
        echo ""
        echo "修改脚本中的以下变量："
        echo "  SERVER_USER=\"hzz\""
        echo "  SERVER_HOST=\"192.168.1.100\""
        echo "  SERVER_PATH=\"/home/hzz/im-stress-test\""
        echo ""
        echo "或通过环境变量设置："
        echo "  SERVER_HOST=192.168.1.100 ./build-jar.sh upload"
        exit 1
    fi
    
    echo -e "${YELLOW}目标服务器: ${NC}$SERVER_USER@$SERVER_HOST:$SERVER_PATH"
    echo ""
    
    # 创建远程目录
    ssh $SERVER_USER@$SERVER_HOST "mkdir -p $SERVER_PATH"
    
    # 上传文件
    echo -e "${YELLOW}上传 JAR 文件...${NC}"
    scp "$JAR_FILE" $SERVER_USER@$SERVER_HOST:$SERVER_PATH/
    
    echo -e "${YELLOW}上传运行脚本...${NC}"
    scp "$SCRIPT_DIR/run-jar.sh" $SERVER_USER@$SERVER_HOST:$SERVER_PATH/
    
    # 设置执行权限
    ssh $SERVER_USER@$SERVER_HOST "chmod +x $SERVER_PATH/run-jar.sh"
    
    echo ""
    echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                    上传成功！                                ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
    echo ""
    echo -e "${YELLOW}在服务器上运行:${NC}"
    echo "  ssh $SERVER_USER@$SERVER_HOST"
    echo "  cd $SERVER_PATH"
    echo "  ./run-jar.sh 192.168.1.150 10001 10000"
    echo ""
fi

# ==================== 使用说明 ====================
echo -e "${BLUE}┌──────────────────────────────────────────────────────────────┐${NC}"
echo -e "${BLUE}│  后续步骤                                                    │${NC}"
echo -e "${BLUE}├──────────────────────────────────────────────────────────────┤${NC}"
echo -e "${BLUE}│  1. 手动上传到服务器:                                        │${NC}"
echo -e "${BLUE}│     scp target/im-stress-gatling.jar user@server:/path/      │${NC}"
echo -e "${BLUE}│     scp run-jar.sh user@server:/path/                        │${NC}"
echo -e "${BLUE}│                                                              │${NC}"
echo -e "${BLUE}│  2. 或使用自动上传:                                          │${NC}"
echo -e "${BLUE}│     SERVER_HOST=192.168.1.100 ./build-jar.sh upload          │${NC}"
echo -e "${BLUE}│                                                              │${NC}"
echo -e "${BLUE}│  3. 在服务器上运行:                                          │${NC}"
echo -e "${BLUE}│     ./run-jar.sh 192.168.1.150 10001 10000                   │${NC}"
echo -e "${BLUE}└──────────────────────────────────────────────────────────────┘${NC}"

