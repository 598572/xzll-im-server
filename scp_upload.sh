#!/bin/bash

# ========================================
# xzll-im 项目 SCP 上传脚本
# ========================================
# 功能：将本地代码上传到服务器，供Jenkins构建使用
# 使用：./scp_upload.sh [选项]
# ========================================

set -e  # 遇到错误立即退出

# ========== 配置区域 ==========
# 服务器配置
SERVER_USER="root"
SERVER_HOST="47.93.209.60"  # 修改为你的服务器IP
SERVER_PORT="2250"

# 目标路径（Jenkins工作目录）
TARGET_DIR="/home/hzz/im/jenkins_way_build_docker_compose/xzll-im"

# 本地项目路径（当前脚本所在目录）
LOCAL_PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# 排除文件/目录（减少上传体积）
EXCLUDE_PATTERNS=(
    "target/"
    ".git/"
    ".idea/"
    "*.iml"
    ".DS_Store"
    "node_modules/"
    "logs/"
    "*.log"
    ".vscode/"
)

# ========== 颜色输出 ==========
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# ========== 工具函数 ==========
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查SSH连接
check_ssh_connection() {
    log_info "检查SSH连接: ${SERVER_USER}@${SERVER_HOST}:${SERVER_PORT}"
    if ! ssh -p ${SERVER_PORT} -o ConnectTimeout=5 ${SERVER_USER}@${SERVER_HOST} "echo 'SSH连接成功'" > /dev/null 2>&1; then
        log_error "无法连接到服务器，请检查："
        log_error "  1. 服务器IP、端口、用户名是否正确"
        log_error "  2. SSH密钥是否配置（建议配置免密登录）"
        log_error "  3. 服务器SSH服务是否启动"
        exit 1
    fi
    log_info "SSH连接正常"
}

# 构建rsync排除参数
build_exclude_args() {
    local exclude_args=""
    for pattern in "${EXCLUDE_PATTERNS[@]}"; do
        exclude_args="$exclude_args --exclude='$pattern'"
    done
    echo "$exclude_args"
}

# 显示上传信息
show_upload_info() {
    log_info "=========================================="
    log_info "准备上传代码到服务器"
    log_info "=========================================="
    log_info "本地路径: ${LOCAL_PROJECT_DIR}"
    log_info "服务器: ${SERVER_USER}@${SERVER_HOST}:${SERVER_PORT}"
    log_info "目标路径: ${TARGET_DIR}"
    log_info "=========================================="
}

# 上传代码
upload_code() {
    log_info "开始上传代码..."
    
    # 创建目标目录
    ssh -p ${SERVER_PORT} ${SERVER_USER}@${SERVER_HOST} "mkdir -p ${TARGET_DIR}"
    
    # 构建rsync命令
    local exclude_args=$(build_exclude_args)
    
    # 使用rsync同步（更高效，支持增量上传）
    eval rsync -avz --delete \
        -e \"ssh -p ${SERVER_PORT}\" \
        ${exclude_args} \
        \"${LOCAL_PROJECT_DIR}/\" \
        \"${SERVER_USER}@${SERVER_HOST}:${TARGET_DIR}/\"
    
    if [ $? -eq 0 ]; then
        log_info "代码上传成功！"
        return 0
    else
        log_error "代码上传失败"
        return 1
    fi
}

# 显示上传后的操作提示
show_next_steps() {
    log_info "=========================================="
    log_info "代码已上传到服务器"
    log_info "=========================================="
    log_info "下一步操作："
    log_info "  1. 登录 Jenkins"
    log_info "  2. 选择你的构建任务"
    log_info "  3. 点击 'Build with Parameters'"
    log_info "  4. 选择 BUILD_MODE = scp"
    log_info "  5. 点击 'Build' 开始构建"
    log_info "=========================================="
}

# 清理服务器上的代码
clean_server_code() {
    log_warn "将清除服务器上的代码: ${TARGET_DIR}"
    read -p "确认清除？(yes/no): " confirm
    if [ "$confirm" == "yes" ]; then
        ssh -p ${SERVER_PORT} ${SERVER_USER}@${SERVER_HOST} "rm -rf ${TARGET_DIR}"
        log_info "服务器代码已清除"
    else
        log_info "取消清除操作"
    fi
}

# 显示帮助信息
show_help() {
    cat << EOF
使用方法: $0 [选项]

选项:
    -h, --help          显示帮助信息
    -c, --clean         清除服务器上的代码
    -t, --test          测试SSH连接
    
默认行为:
    不带参数运行时，将上传代码到服务器

示例:
    $0                  # 上传代码
    $0 --test           # 测试SSH连接
    $0 --clean          # 清除服务器代码

配置说明:
    请修改脚本开头的配置区域：
    - SERVER_HOST: 你的服务器IP
    - SERVER_USER: SSH登录用户名
    - SERVER_PORT: SSH端口（默认22）
    - TARGET_DIR: 服务器目标路径

EOF
}

# ========== 主流程 ==========
main() {
    case "${1:-}" in
        -h|--help)
            show_help
            exit 0
            ;;
        -t|--test)
            check_ssh_connection
            log_info "SSH连接测试通过"
            exit 0
            ;;
        -c|--clean)
            check_ssh_connection
            clean_server_code
            exit 0
            ;;
        "")
            # 默认：上传代码
            show_upload_info
            check_ssh_connection
            upload_code
            show_next_steps
            ;;
        *)
            log_error "未知参数: $1"
            show_help
            exit 1
            ;;
    esac
}

# 执行主流程
main "$@"
