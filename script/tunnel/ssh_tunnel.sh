#!/bin/bash

# 此脚本为优化后的（批量循环打洞）

# 检测是否存在已有的 ssh 进程
echo "正在检查是否存在 SSH 进程..."
SSH_PID=$(pgrep -f "ssh -fN")

if [ -n "$SSH_PID" ]; then
    echo "检测到 SSH 进程 (PID: $SSH_PID)，正在终止..."
    kill -9 $SSH_PID
    echo "SSH 进程已终止。"
else
    echo "没有检测到现有的 SSH 进程。"
fi

# 创建主连接
echo "正在创建主连接..."
ssh -fN xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "主连接已创建成功。"
else
    echo "主连接创建失败！"
    exit 1
fi

# 定义隧道信息，格式：    "<隧道名称>:<本地监听端口>:<远程服务器IP>:<远程服务器端口>"
tunnels=(
    "Redis 隧道 (16379):16379:192.168.122.138:6379"
    "宝塔 隧道 (14270):8888:localhost:14270"
    "rocketmq 控制台 隧道 (18080):18080:192.168.122.138:8080"
    "rocketmq server 隧道 (19876):19876:192.168.122.138:9876"
    "MySQL 隧道 (13306):13306:192.168.122.138:3306"
    "CK http 隧道 (18123):18123:192.168.122.138:8123"
    "CK tcp 端口 隧道 (19000):19000:192.168.122.138:9000"
    "nacos 隧道 (18848):18848:192.168.122.138:8848"
    "elasticsearch 隧道 (19200):19200:192.168.122.138:9200"
    "kabana 隧道 (15601):15601:192.168.122.138:5601"
    "zk server 隧道 (12181):12181:192.168.122.138:2181"
    "rocketmq broker 隧道 (10909):10909:192.168.122.138:10909"
    "rocketmq broker 隧道 (10911):10911:192.168.122.138:10911"
)

# 循环创建隧道
for tunnel_info in "${tunnels[@]}"; do
    tunnel_name="${tunnel_info%%:*}" # 获取隧道名称
    tunnel="${tunnel_info#*:}"       # 获取隧道端口信息

    echo "正在打开 ${tunnel_name}..."
    ssh -fN -L $tunnel xzll_suzhuji
    if [ $? -eq 0 ]; then
        echo "${tunnel_name} 已成功打开。"
        lsof -iTCP:$(echo $tunnel | cut -d':' -f1) -sTCP:LISTEN
    else
        echo "${tunnel_name} 打开失败！"
    fi
    echo ""
done

echo "所有隧道已成功打开。"
