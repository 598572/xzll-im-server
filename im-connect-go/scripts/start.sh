#!/bin/bash

# ============================================================
# im-connect-go 启动脚本
# ============================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示帮助信息
show_help() {
    cat << EOF
╔══════════════════════════════════════════════════════════════════════════╗
║                    IM-Connect-Go 启动脚本帮助                            ║
╚══════════════════════════════════════════════════════════════════════════╝

使用方法:
    ./scripts/start.sh [环境]

支持的环境:
    dev         开发环境（使用 dev namespace）
    test        测试环境（使用 test namespace）
    pre         预发环境（使用 pre namespace）
    prod        生产环境（使用 prod namespace）

示例:
    ./scripts/start.sh dev      # 启动开发环境
    ./scripts/start.sh prod     # 启动生产环境

配置文件:
    configs/bootstrap-dev.yaml      # 开发环境配置
    configs/bootstrap-test.yaml     # 测试环境配置
    configs/bootstrap-prod.yaml     # 生产环境配置

════════════════════════════════════════════════════════════════════════════
EOF
}

# 检查环境参数
ENV=${1:-dev}

if [ "$ENV" == "help" ] || [ "$ENV" == "-h" ] || [ "$ENV" == "--help" ]; then
    show_help
    exit 0
fi

# 验证环境参数
if [[ ! "$ENV" =~ ^(dev|test|pre|prod)$ ]]; then
    print_error "无效的环境参数: $ENV"
    print_info "支持的环境: dev, test, pre, prod"
    echo ""
    show_help
    exit 1
fi

print_info "准备启动 IM-Connect-Go 服务..."
print_info "运行环境: $ENV"

# 检查配置文件是否存在
CONFIG_FILE="configs/bootstrap-${ENV}.yaml"
if [ ! -f "$CONFIG_FILE" ]; then
    print_error "配置文件不存在: $CONFIG_FILE"
    exit 1
fi
print_success "找到配置文件: $CONFIG_FILE"

# 检查可执行文件
if [ ! -f "./im-connect-go" ]; then
    print_warning "可执行文件不存在，开始编译..."
    make build || {
        print_error "编译失败"
        exit 1
    }
    print_success "编译完成"
fi

# 创建日志目录
mkdir -p logs
print_success "日志目录已创建: logs/"

# 启动服务
print_info "启动 IM-Connect-Go 服务 (环境: $ENV)..."
echo ""
echo "════════════════════════════════════════════════════════════════════════════"

# 启动服务（带日志输出）
./im-connect-go --env=$ENV 2>&1 | tee logs/im-connect-go-${ENV}.log

echo "════════════════════════════════════════════════════════════════════════════"
print_info "服务已停止"

