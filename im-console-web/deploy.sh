#!/bin/bash

# ========================================
# OkIM 管理后台前端自动部署脚本
# ========================================

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 服务器配置
SERVER_IP="47.93.209.60"
SERVER_PORT="2221"
SERVER_USER="root"
SERVER_DIR="/home/hzz/nginx/html/im-console-web"

# 本地配置
PROJECT_DIR="/Users/hzz/myself_project/开源09/xzll-im-server/im-console-web"
DIST_DIR="$PROJECT_DIR/dist"
BACKUP_NAME="im-console-web-backup-$(date +%Y%m%d_%H%M%S).tar.gz"

echo -e "${BLUE}======================================${NC}"
echo -e "${BLUE}  OkIM 管理后台前端部署脚本${NC}"
echo -e "${BLUE}======================================${NC}"
echo ""

# 步骤 1: 检查当前目录
echo -e "${YELLOW}[步骤 1/6] 检查项目目录...${NC}"
if [ ! -f "$PROJECT_DIR/package.json" ]; then
    echo -e "${RED}❌ 错误：未找到 package.json 文件${NC}"
    echo -e "${RED}请确保在正确的项目目录下运行此脚本${NC}"
    exit 1
fi
cd "$PROJECT_DIR"
echo -e "${GREEN}✓ 项目目录正确${NC}"
echo ""

# 步骤 2: 清理旧构建
echo -e "${YELLOW}[步骤 2/6] 清理旧的构建文件...${NC}"
if [ -d "$DIST_DIR" ]; then
    rm -rf "$DIST_DIR"
    echo -e "${GREEN}✓ 已删除旧的 dist 目录${NC}"
else
    echo -e "${GREEN}✓ 无旧构建文件需要清理${NC}"
fi
echo ""

# 步骤 3: 构建前端
echo -e "${YELLOW}[步骤 3/6] 开始构建前端项目...${NC}"
echo -e "${BLUE}执行: npm run build:prod${NC}"
npm run build:prod

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 构建失败！请检查错误信息${NC}"
    exit 1
fi

if [ ! -d "$DIST_DIR" ]; then
    echo -e "${RED}❌ 构建失败：未生成 dist 目录${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 构建成功${NC}"
echo ""

# 步骤 4: 打包
echo -e "${YELLOW}[步骤 4/6] 打包构建文件...${NC}"
cd "$DIST_DIR"
tar -czf "$PROJECT_DIR/im-console-web.tar.gz" .
cd "$PROJECT_DIR"

if [ ! -f "$PROJECT_DIR/im-console-web.tar.gz" ]; then
    echo -e "${RED}❌ 打包失败${NC}"
    exit 1
fi

# 获取文件大小
FILE_SIZE=$(du -h "$PROJECT_DIR/im-console-web.tar.gz" | cut -f1)
echo -e "${GREEN}✓ 打包成功：im-console-web.tar.gz ($FILE_SIZE)${NC}"
echo ""

# 步骤 5: 上传到服务器
echo -e "${YELLOW}[步骤 5/6] 上传文件到服务器...${NC}"
echo -e "${BLUE}服务器：${SERVER_USER}@${SERVER_IP}:${SERVER_PORT}${NC}"
echo -e "${BLUE}目标目录：${SERVER_DIR}${NC}"
echo -e "${YELLOW}请输入服务器密码（需要输入2次）...${NC}"

# 先上传到临时目录
scp -P "$SERVER_PORT" "$PROJECT_DIR/im-console-web.tar.gz" "${SERVER_USER}@${SERVER_IP}:/tmp/"

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ 上传失败${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 上传成功${NC}"
echo ""

# 步骤 6: 在服务器上解压
echo -e "${YELLOW}[步骤 6/6] 在服务器上部署...${NC}"
echo -e "${YELLOW}请再次输入服务器密码...${NC}"

ssh -p "$SERVER_PORT" "${SERVER_USER}@${SERVER_IP}" << EOF
# 备份现有文件
if [ -d "$SERVER_DIR" ]; then
    echo "备份现有文件..."
    cd "$SERVER_DIR"
    tar -czf "/tmp/$BACKUP_NAME" . 2>/dev/null
    echo "备份完成: /tmp/$BACKUP_NAME"
fi

# 创建目录（如果不存在）
mkdir -p "$SERVER_DIR"

# 清空目标目录
rm -rf "$SERVER_DIR"/*

# 解压新文件
echo "解压新文件..."
tar -xzf /tmp/im-console-web.tar.gz -C "$SERVER_DIR"

# 清理临时文件
rm -f /tmp/im-console-web.tar.gz

# 设置权限
chmod -R 755 "$SERVER_DIR"

echo "部署完成！"
echo ""
echo "文件列表："
ls -lh "$SERVER_DIR" | head -10
EOF

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}======================================${NC}"
    echo -e "${GREEN}✓ 部署成功！${NC}"
    echo -e "${GREEN}======================================${NC}"
    echo ""
    echo -e "${BLUE}访问地址：${NC}http://47.93.209.60:8092"
    echo -e "${YELLOW}提示：如果页面未更新，请清除浏览器缓存（Ctrl+Shift+R）${NC}"
    echo ""

    # 清理本地临时文件
    rm -f "$PROJECT_DIR/im-console-web.tar.gz"
    echo -e "${GREEN}✓ 已清理本地临时文件${NC}"
else
    echo -e "${RED}❌ 服务器部署失败${NC}"
    exit 1
fi
