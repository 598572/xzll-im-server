#!/bin/bash

# ============================================================================
# 文件句柄限制修复脚本 - 立即生效
# ============================================================================
# 
# 问题：执行 ulimit -n 仍然显示 1024
# 原因：limits.conf 需要重新登录才生效，且对 root 用户可能无效
#
# 使用方法：
#   sudo ./fix-ulimit.sh              # 临时修复（当前 shell）
#   sudo ./fix-ulimit.sh persist      # 永久修复（需重启）
#
# ============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║          文件句柄限制修复脚本                                ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ 请使用 root 权限运行${NC}"
    echo "   sudo $0 $@"
    exit 1
fi

PERSIST=${1:-""}
TARGET_LIMIT=1000000

echo -e "${YELLOW}当前状态:${NC}"
echo "  ulimit -n: $(ulimit -n)"
echo "  fs.file-max: $(cat /proc/sys/fs/file-max 2>/dev/null || echo 'N/A')"
echo "  fs.nr_open: $(cat /proc/sys/fs/nr_open 2>/dev/null || echo 'N/A')"
echo ""

# ============================================================================
# 1. 立即生效（当前 shell）
# ============================================================================
echo -e "${BLUE}【1/4】修改当前 shell 限制...${NC}"

# 修改系统级限制（必须先修改这个）
echo $TARGET_LIMIT > /proc/sys/fs/file-max
echo $TARGET_LIMIT > /proc/sys/fs/nr_open

# 修改当前 shell 的 ulimit（直接生效）
ulimit -n $TARGET_LIMIT 2>/dev/null

CURRENT_LIMIT=$(ulimit -n)
if [ "$CURRENT_LIMIT" -ge "$TARGET_LIMIT" ]; then
    echo -e "${GREEN}   ✅ 当前 shell: $CURRENT_LIMIT${NC}"
else
    echo -e "${YELLOW}   ⚠️  当前 shell: $CURRENT_LIMIT (低于目标值)${NC}"
fi

# ============================================================================
# 2. 修改 /etc/security/limits.conf（需重新登录）
# ============================================================================
echo -e "${BLUE}【2/4】修改 /etc/security/limits.conf...${NC}"

# 检查是否已存在配置
if grep -q "# ============ IM 百万连接优化 ============" /etc/security/limits.conf 2>/dev/null; then
    echo -e "${YELLOW}   ⚠️  配置已存在，跳过${NC}"
else
    cat >> /etc/security/limits.conf << EOF

# ============ IM 百万连接优化 ============
*               soft    nofile          $TARGET_LIMIT
*               hard    nofile          $TARGET_LIMIT
*               soft    nproc           unlimited
*               hard    nproc           unlimited
root            soft    nofile          $TARGET_LIMIT
root            hard    nofile          $TARGET_LIMIT
# =========================================
EOF
    echo -e "${GREEN}   ✅ 已添加到 limits.conf${NC}"
fi

# ============================================================================
# 3. 修改 systemd 限制（对 Docker 等服务重要）
# ============================================================================
echo -e "${BLUE}【3/4】修改 systemd 默认限制...${NC}"

SYSTEMD_CONF="/etc/systemd/system.conf"
if [ -f "$SYSTEMD_CONF" ]; then
    # 备份原文件
    cp $SYSTEMD_CONF ${SYSTEMD_CONF}.bak 2>/dev/null || true
    
    # 检查是否已配置
    if grep -q "^DefaultLimitNOFILE=" $SYSTEMD_CONF 2>/dev/null; then
        # 已存在，更新值
        sed -i "s/^DefaultLimitNOFILE=.*/DefaultLimitNOFILE=$TARGET_LIMIT/" $SYSTEMD_CONF
        echo -e "${GREEN}   ✅ 已更新 systemd 配置${NC}"
    elif grep -q "^#DefaultLimitNOFILE=" $SYSTEMD_CONF 2>/dev/null; then
        # 存在但被注释，取消注释并设置
        sed -i "s/^#DefaultLimitNOFILE=.*/DefaultLimitNOFILE=$TARGET_LIMIT/" $SYSTEMD_CONF
        echo -e "${GREEN}   ✅ 已启用并设置 systemd 配置${NC}"
    else
        # 不存在，添加到文件末尾
        echo "DefaultLimitNOFILE=$TARGET_LIMIT" >> $SYSTEMD_CONF
        echo -e "${GREEN}   ✅ 已添加到 systemd 配置${NC}"
    fi
    
    # 重新加载 systemd 配置
    systemctl daemon-reexec 2>/dev/null || true
    echo -e "${GREEN}   ✅ systemd 配置已重新加载${NC}"
else
    echo -e "${YELLOW}   ⚠️  $SYSTEMD_CONF 不存在，跳过${NC}"
fi

# ============================================================================
# 4. 修改 sysctl.conf（永久生效）
# ============================================================================
if [ "$PERSIST" = "persist" ]; then
    echo -e "${BLUE}【4/4】写入 /etc/sysctl.conf（永久生效）...${NC}"
    
    if grep -q "^fs.file-max" /etc/sysctl.conf 2>/dev/null; then
        sed -i "s/^fs.file-max.*/fs.file-max = $TARGET_LIMIT/" /etc/sysctl.conf
        sed -i "s/^fs.nr_open.*/fs.nr_open = $TARGET_LIMIT/" /etc/sysctl.conf
    else
        cat >> /etc/sysctl.conf << EOF

# ============ IM 百万连接优化 ============
fs.file-max = $TARGET_LIMIT
fs.nr_open = $TARGET_LIMIT
# =========================================
EOF
    fi
    
    sysctl -p >/dev/null 2>&1 || true
    echo -e "${GREEN}   ✅ 已写入 sysctl.conf 并应用${NC}"
else
    echo -e "${BLUE}【4/4】跳过永久配置（使用 'persist' 参数永久保存）${NC}"
fi

# ============================================================================
# 验证结果
# ============================================================================
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                    配置完成                                  ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

echo -e "${YELLOW}当前生效值:${NC}"
CURRENT_ULIMIT=$(ulimit -n)
CURRENT_FILE_MAX=$(cat /proc/sys/fs/file-max)
CURRENT_NR_OPEN=$(cat /proc/sys/fs/nr_open)

if [ "$CURRENT_ULIMIT" -ge "$TARGET_LIMIT" ]; then
    echo -e "  ulimit -n: ${GREEN}$CURRENT_ULIMIT ✅${NC}"
else
    echo -e "  ulimit -n: ${RED}$CURRENT_ULIMIT ❌${NC}"
fi

if [ "$CURRENT_FILE_MAX" -ge "$TARGET_LIMIT" ]; then
    echo -e "  fs.file-max: ${GREEN}$CURRENT_FILE_MAX ✅${NC}"
else
    echo -e "  fs.file-max: ${YELLOW}$CURRENT_FILE_MAX ⚠️${NC}"
fi

if [ "$CURRENT_NR_OPEN" -ge "$TARGET_LIMIT" ]; then
    echo -e "  fs.nr_open: ${GREEN}$CURRENT_NR_OPEN ✅${NC}"
else
    echo -e "  fs.nr_open: ${YELLOW}$CURRENT_NR_OPEN ⚠️${NC}"
fi

echo ""

# ============================================================================
# 重要提示
# ============================================================================
echo -e "${YELLOW}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║                    重要提示                                  ║${NC}"
echo -e "${YELLOW}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$CURRENT_ULIMIT" -ge "$TARGET_LIMIT" ]; then
    echo -e "${GREEN}✅ 当前 shell 已生效！可以直接运行压测：${NC}"
    echo -e "   ${BLUE}./run-jar.sh 192.168.1.150 10001 10000 120 600 5000 30000${NC}"
    echo ""
else
    echo -e "${RED}❌ 当前 shell 未生效！${NC}"
    echo ""
    echo -e "${YELLOW}解决方案（选择其一）：${NC}"
    echo ""
    echo -e "${BLUE}方案 1：在当前 shell 手动执行（立即生效）${NC}"
    echo -e "   ${GREEN}ulimit -n $TARGET_LIMIT${NC}"
    echo -e "   ${GREEN}./run-jar.sh 192.168.1.150 10001 10000 120 600 5000 30000${NC}"
    echo ""
    echo -e "${BLUE}方案 2：重新登录后生效（推荐）${NC}"
    echo -e "   ${GREEN}exit${NC}"
    echo -e "   ${GREEN}ssh root@server${NC}"
    echo -e "   ${GREEN}ulimit -n  # 验证是否生效${NC}"
    echo ""
    echo -e "${BLUE}方案 3：使用 su 命令重新加载限制${NC}"
    echo -e "   ${GREEN}su - root${NC}"
    echo -e "   ${GREEN}ulimit -n  # 验证是否生效${NC}"
    echo ""
fi

if [ "$PERSIST" != "persist" ]; then
    echo -e "${YELLOW}💡 使用 '$0 persist' 永久保存配置${NC}"
    echo ""
fi

echo -e "${YELLOW}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${YELLOW}║              后续操作（如果重启服务器）                      ║${NC}"
echo -e "${YELLOW}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "1. ${BLUE}重启 Docker（如果使用 Docker）：${NC}"
echo -e "   ${GREEN}systemctl restart docker${NC}"
echo ""
echo -e "2. ${BLUE}验证 Docker 容器的文件句柄限制：${NC}"
echo -e "   ${GREEN}docker run --rm ubuntu:latest ulimit -n${NC}"
echo ""
echo -e "3. ${BLUE}验证 im-connect 容器：${NC}"
echo -e "   ${GREEN}docker exec im-connect sh -c 'ulimit -n'${NC}"
echo ""


