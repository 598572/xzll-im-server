#!/bin/bash

# 创建主连接
echo "正在创建主连接..."
ssh -fN xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "主连接已创建成功。"
else
    echo "主连接创建失败！"
    exit 1
fi

# 打开 Redis 隧道
echo "正在打开 Redis 隧道 (16379)..."
ssh -fN -L 16379:192.168.122.138:6379  xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "Redis 隧道已成功打开。"
    lsof -iTCP:16379 -sTCP:LISTEN
else
    echo "Redis 隧道打开失败！"
fi

# 打开 宝塔 隧道
echo "正在打开 宝塔 隧道 (14271)..."
ssh -fN  -L 14271:localhost:14270 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "宝塔 隧道已成功打开。"
    lsof -iTCP:14271 -sTCP:LISTEN -P
else
    echo "宝塔 隧道打开失败！"
fi

# 打开 rocketmq 控制台 隧道
echo "正在打开 rocketmq 控制台  隧道 (18080)..."
ssh -fN  -L 18080:192.168.122.138:8080 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo " rocketmq 控制台 隧道已成功打开。"
    lsof -iTCP:18080 -sTCP:LISTEN -P
else
    echo " rocketmq 控制台 隧道打开失败！"
fi

# 打开 rocketmq server 隧道
echo "正在打开 rocketmq server  隧道 (19876)..."
ssh -fN  -L 19876:192.168.122.138:9876 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo " rocketmq server 隧道已成功打开。"
    lsof -iTCP:19876 -sTCP:LISTEN -P
else
    echo " rocketmq server 隧道打开失败！"
fi


# 打开 MySQL 隧道
echo "正在打开 MySQL 隧道 (13306)..."
ssh -fN  -L 13306:192.168.122.138:3306 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "MySQL 隧道已成功打开。"
    lsof -iTCP:13306 -sTCP:LISTEN
else
    echo "MySQL 隧道打开失败！"
fi

# 打开 CK  http 隧道
echo "正在打开 CK http 隧道 (18123)..."
ssh -fN  -L 18123:192.168.122.138:8123 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "CK  http 隧道已成功打开。"
    lsof -iTCP:18123 -sTCP:LISTEN
else
    echo "CK http 隧道打开失败！"
fi


# 打开 CK http端口  隧道
echo "正在打开 CK tcp 端口 隧道 (18123)..."
ssh -fN  -L 19000:192.168.122.138:9000 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "CK tcp 隧道已成功打开。"
    lsof -iTCP:19000 -sTCP:LISTEN
else
    echo "CK tcp  隧道打开失败！"
fi

# 打开 nacos 隧道
echo "正在打开 nacos 隧道 (18848)..."
ssh -fN  -L 18848:192.168.122.138:8848 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "nacos 隧道已成功打开。"
    lsof -iTCP:18848 -sTCP:LISTEN
else
    echo "nacos 隧道打开失败！"
fi


# 打开 es 隧道
echo "正在打开 elasticsearch 隧道 (19200)..."
ssh -fN  -L 19200:192.168.122.138:9200 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "elasticsearch 隧道已成功打开。"
    lsof -iTCP:19200 -sTCP:LISTEN
else
    echo "elasticsearch 隧道打开失败！"
fi


# 打开 kabana 隧道
echo "正在打开 kabana 隧道 (15601)..."
ssh -fN  -L 15601:192.168.122.138:5601 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "kabana 隧道已成功打开。"
    lsof -iTCP:15601 -sTCP:LISTEN
else
    echo "kabana 隧道打开失败！"
fi

# 打开 rocketmq server 隧道
echo "正在打开 zk server  隧道 (12181)..."
ssh -fN  -L 12181:192.168.122.138:2181 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo " zk server  隧道已成功打开。"
    lsof -iTCP:12181 -sTCP:LISTEN -P
else
    echo " zk server  隧道打开失败！"
fi




# 打开 rocketmq server 隧道
echo "正在打开 rocketmq broker  隧道 (10909)..."
ssh -fN  -L 10909:192.168.122.138:10909 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo " rocketmq broker  隧道已成功打开。"
    lsof -iTCP:10909 -sTCP:LISTEN -P
else
    echo " rocketmq broker  隧道打开失败！"
fi


# # 打开 rocketmq server 隧道
echo "正在打开 rocketmq broker  隧道 (10911)..."
ssh -fN  -L 10911:192.168.122.138:10911 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo " rocketmq broker  隧道已成功打开。"
    lsof -iTCP:10911 -sTCP:LISTEN -P
else
    echo " rocketmq broker  隧道打开失败！"
fi


# # 打开 vm03的  ssh  隧道
echo "正在打开 vm03 ssh 隧道 (2222)..."
ssh -fN  -L 2222:localhost:2222 xzll_suzhuji
if [ $? -eq 0 ]; then
    echo "vm03 ssh 隧道已成功打开。"
    lsof -iTCP:2222 -sTCP:LISTEN
else
    echo "vm03 ssh 隧道打开失败！"
fi


echo "所有隧道已成功打开。"


