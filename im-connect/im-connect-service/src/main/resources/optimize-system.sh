#!/bin/bash

# ============================================================================
# Linux 系统优化脚本 - 支持百万级长连接
# ============================================================================
# 
# 使用方法：
#   sudo ./optimize-system.sh          # 临时生效（重启后失效）
#   sudo ./optimize-system.sh persist  # 永久生效（写入配置文件）
#
# 注意：需要 root 权限执行
# ============================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║      Linux 系统优化 - 支持百万级长连接                       ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""

# 检查 root 权限
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}❌ 请使用 root 权限运行此脚本${NC}"
    echo "   sudo $0 $@"
    exit 1
fi

PERSIST=${1:-""}

# ============================================================================
# 1. 文件句柄限制（最重要！）
# ============================================================================
echo -e "${YELLOW}[1/5] 优化文件句柄限制...${NC}"

# 临时设置（与 docker-compose 保持一致：1000000）
ulimit -n 1000000 2>/dev/null || true
echo 1000000 > /proc/sys/fs/file-max
echo 1000000 > /proc/sys/fs/nr_open

if [ "$PERSIST" = "persist" ]; then
    # 永久设置 - /etc/security/limits.conf
    cat >> /etc/security/limits.conf << 'EOF'

# ============ IM 百万连接优化 ============
*               soft    nofile          1000000
*               hard    nofile          1000000
*               soft    nproc           unlimited
*               hard    nproc           unlimited
root            soft    nofile          1000000
root            hard    nofile          1000000
# =========================================
EOF

    # 永久设置 - /etc/sysctl.conf
    echo "fs.file-max = 1000000" >> /etc/sysctl.conf
    echo "fs.nr_open = 1000000" >> /etc/sysctl.conf
fi

echo -e "${GREEN}   ✅ 文件句柄限制: $(ulimit -n)${NC}"

# ============================================================================
# 2. TCP/IP 网络栈优化
# ============================================================================
echo -e "${YELLOW}[2/5] 优化 TCP/IP 网络栈...${NC}"

# 临时设置
sysctl -w net.core.somaxconn=65535
sysctl -w net.core.netdev_max_backlog=65535
sysctl -w net.ipv4.tcp_max_syn_backlog=65535
sysctl -w net.ipv4.tcp_syncookies=1
sysctl -w net.ipv4.tcp_tw_reuse=1
sysctl -w net.ipv4.tcp_fin_timeout=10
sysctl -w net.ipv4.tcp_keepalive_time=600
sysctl -w net.ipv4.tcp_keepalive_intvl=30
sysctl -w net.ipv4.tcp_keepalive_probes=3
sysctl -w net.ipv4.ip_local_port_range="1024 65535"
sysctl -w net.ipv4.tcp_max_tw_buckets=1000000
sysctl -w net.ipv4.tcp_max_orphans=262144
sysctl -w net.ipv4.tcp_slow_start_after_idle=0

if [ "$PERSIST" = "persist" ]; then
    cat >> /etc/sysctl.conf << 'EOF'

# ============ IM 百万连接 - TCP/IP 优化 ============
# 连接队列
net.core.somaxconn = 65535
net.core.netdev_max_backlog = 65535
net.ipv4.tcp_max_syn_backlog = 65535

# TCP 连接优化
net.ipv4.tcp_syncookies = 1
net.ipv4.tcp_tw_reuse = 1
net.ipv4.tcp_fin_timeout = 10
net.ipv4.tcp_keepalive_time = 600
net.ipv4.tcp_keepalive_intvl = 30
net.ipv4.tcp_keepalive_probes = 3

# 端口范围
net.ipv4.ip_local_port_range = 1024 65535
net.ipv4.tcp_max_tw_buckets = 1000000
net.ipv4.tcp_max_orphans = 262144

# 【高QPS关键】禁用慢启动重启（长连接场景提升吞吐）
net.ipv4.tcp_slow_start_after_idle = 0
# ==================================================
EOF
fi

echo -e "${GREEN}   ✅ TCP/IP 网络栈优化完成${NC}"

# ============================================================================
# 3. 内存优化
# ============================================================================
echo -e "${YELLOW}[3/5] 优化内存配置...${NC}"

# 临时设置
sysctl -w net.core.rmem_max=16777216
sysctl -w net.core.wmem_max=16777216
sysctl -w net.core.rmem_default=262144
sysctl -w net.core.wmem_default=262144
sysctl -w net.ipv4.tcp_rmem="4096 87380 16777216"
sysctl -w net.ipv4.tcp_wmem="4096 65536 16777216"
sysctl -w net.ipv4.tcp_mem="786432 1048576 1572864"
sysctl -w vm.swappiness=10
sysctl -w vm.overcommit_memory=1

if [ "$PERSIST" = "persist" ]; then
    cat >> /etc/sysctl.conf << 'EOF'

# ============ IM 百万连接 - 内存优化 ============
# 网络缓冲区
net.core.rmem_max = 16777216
net.core.wmem_max = 16777216
net.core.rmem_default = 262144
net.core.wmem_default = 262144
net.ipv4.tcp_rmem = 4096 87380 16777216
net.ipv4.tcp_wmem = 4096 65536 16777216
net.ipv4.tcp_mem = 786432 1048576 1572864

# 虚拟内存
vm.swappiness = 10
vm.overcommit_memory = 1
# ================================================
EOF
fi

echo -e "${GREEN}   ✅ 内存配置优化完成${NC}"

# ============================================================================
# 4. 连接跟踪优化
# ============================================================================
echo -e "${YELLOW}[4/5] 优化连接跟踪...${NC}"

# 检查是否有 nf_conntrack 模块
if lsmod | grep -q nf_conntrack; then
    sysctl -w net.netfilter.nf_conntrack_max=2000000 2>/dev/null || true
    sysctl -w net.netfilter.nf_conntrack_tcp_timeout_established=1200 2>/dev/null || true
    sysctl -w net.netfilter.nf_conntrack_tcp_timeout_time_wait=30 2>/dev/null || true
    
    if [ "$PERSIST" = "persist" ]; then
        cat >> /etc/sysctl.conf << 'EOF'

# ============ IM 百万连接 - 连接跟踪优化 ============
net.netfilter.nf_conntrack_max = 2000000
net.netfilter.nf_conntrack_tcp_timeout_established = 1200
net.netfilter.nf_conntrack_tcp_timeout_time_wait = 30
# ====================================================
EOF
    fi
    echo -e "${GREEN}   ✅ 连接跟踪优化完成${NC}"
else
    echo -e "${YELLOW}   ⚠️  nf_conntrack 模块未加载，跳过${NC}"
fi

# ============================================================================
# 5. 应用配置
# ============================================================================
echo -e "${YELLOW}[5/5] 应用配置...${NC}"

if [ "$PERSIST" = "persist" ]; then
    sysctl -p
    echo -e "${GREEN}   ✅ 配置已永久保存到 /etc/sysctl.conf${NC}"
else
    echo -e "${YELLOW}   ⚠️  配置仅临时生效，重启后失效${NC}"
    echo -e "${YELLOW}   使用 '$0 persist' 永久保存配置${NC}"
fi

# ============================================================================
# 验证配置
# ============================================================================
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                    配置验证                                  ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}文件句柄限制:${NC}"
echo "  ulimit -n: $(ulimit -n)"
echo "  fs.file-max: $(cat /proc/sys/fs/file-max)"
echo ""
echo -e "${YELLOW}网络配置:${NC}"
echo "  net.core.somaxconn: $(sysctl -n net.core.somaxconn)"
echo "  net.ipv4.tcp_max_syn_backlog: $(sysctl -n net.ipv4.tcp_max_syn_backlog)"
echo "  net.ipv4.ip_local_port_range: $(sysctl -n net.ipv4.ip_local_port_range)"
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                    优化完成！                                ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${YELLOW}理论支持连接数: $(cat /proc/sys/fs/file-max)${NC}"
echo -e "${YELLOW}建议服务器配置: 16核32GB 内存${NC}"

