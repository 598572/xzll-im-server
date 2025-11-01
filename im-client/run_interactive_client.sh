#!/bin/bash

# 交互式测试客户端启动脚本
# Author: hzz
# Date: 2025-10-29

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印标题
echo -e "${BLUE}╔════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║     交互式 IM 测试客户端 - 快速启动脚本           ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════╝${NC}"
echo ""

# 检查是否在正确的目录
if [ ! -f "pom.xml" ]; then
    echo -e "${RED}❌ 错误: 请在 im-client 目录下运行此脚本${NC}"
    exit 1
fi

# 检查 Java 环境
if ! command -v java &> /dev/null; then
    echo -e "${RED}❌ 错误: 未找到 Java 环境，请先安装 JDK${NC}"
    exit 1
fi

# 显示 Java 版本
echo -e "${GREEN}✅ Java 环境:${NC}"
java -version 2>&1 | head -n 1
echo ""

# 编译项目（如果需要）
if [ "$1" == "--rebuild" ] || [ ! -d "target/classes" ]; then
    echo -e "${YELLOW}🔨 正在编译项目...${NC}"
    mvn clean compile -DskipTests
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ 编译失败，请检查错误信息${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ 编译成功${NC}"
    echo ""
fi

# 运行客户端
echo -e "${GREEN}🚀 正在启动交互式测试客户端...${NC}"
echo ""

mvn exec:java -Dexec.mainClass="com.xzll.client.protobuf.interactive.InteractiveTestClient"

# 退出时的提示
echo ""
echo -e "${BLUE}👋 测试客户端已退出${NC}"

