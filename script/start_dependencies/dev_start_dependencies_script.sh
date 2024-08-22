#!/bin/bash



# 定义默认的 LOCAL_IP 变量
DEFAULT_LOCAL_IP="172.30.128.65"

# 如果传递了参数，则使用参数中的 IP 地址，否则使用默认值
LOCAL_IP="${1:-$DEFAULT_LOCAL_IP}"

echo "使用的 IP 地址是: $LOCAL_IP"


# 创建需要的目录

mkdir -p /usr/local/hzz/docker/rocketmq/{store,logs,conf}
mkdir -p /usr/local/hzz/docker/redis/{data,conf}
mkdir -p /usr/local/hzz/docker/zookeeper/{data,datalog,conf}

chmod -R 777 /usr/local/hzz/docker/rocketmq
chmod -R 777 /usr/local/hzz/docker/redis
chmod -R 777 /usr/local/hzz/docker/zookeeper


# 创建并写入zoo.cfg文件
cat > /usr/local/hzz/docker/zookeeper/conf/zoo.cfg <<EOL
tickTime=2000
dataDir=/data
dataLogDir=/datalog
clientPort=2181
initLimit=5
syncLimit=2
server.1=zookeeper:2888:3888
EOL

# 创建并写入redis.conf文件
cat > /usr/local/hzz/docker/redis/conf/redis.conf <<EOL
appendonly yes
requirepass 123456
EOL

# 创建并写入broker.conf文件
cat > /usr/local/hzz/docker/rocketmq/conf/broker.conf <<EOL
brokerClusterName = DefaultCluster
brokerName = broker-a
brokerId = 0
deleteWhen = 04
fileReservedTime = 48
brokerRole = ASYNC_MASTER
flushDiskType = ASYNC_FLUSH
namesrvAddr=${LOCAL_IP}:9876
autoCreateTopicEnable=true
brokerIP1=${LOCAL_IP}
EOL

# 设置权限
chmod -R 755 /usr/local/hzz/docker/zookeeper/conf
chmod -R 755 /usr/local/hzz/docker/redis/conf
chmod -R 755 /usr/local/hzz/docker/rocketmq/conf

echo "配置文件已成功创建并写入内容。"


# 函数：停止并删除已有容器
stop_and_remove_container() {
    local container_name=$1
    if [ "$(docker ps -q -f name=${container_name})" ]; then
        echo "停止并删除已有容器: ${container_name}"
        docker stop ${container_name}
        docker rm ${container_name}
    fi
}


# 启动 RocketMQ 的 NameServer
stop_and_remove_container rmqnamesrv
docker run -d \
  --name rmqnamesrv \
  --restart always \
  -p 9876:9876 \
  -v /usr/local/hzz/docker/rocketmq/store:/root/store \
  -v /usr/local/hzz/docker/rocketmq/logs:/root/logs \
  apache/rocketmq:4.8.0 \
  sh mqnamesrv

# 启动 RocketMQ 的 Broker
stop_and_remove_container rmqbroker
docker run -d \
  --name rmqbroker \
  --restart always \
  -p 10911:10911 \
  -p 10909:10909 \
  -v /usr/local/hzz/docker/rocketmq/store:/root/store \
  -v /usr/local/hzz/docker/rocketmq/logs:/root/logs \
  -v /usr/local/hzz/docker/rocketmq/conf/broker.conf:/opt/rocketmq-4.8.0/conf/broker.conf \
  apache/rocketmq:4.8.0 \
  sh mqbroker -c /opt/rocketmq-4.8.0/conf/broker.conf


# 启动 RocketMQ 控制台（其中ip请自行替换）
stop_and_remove_container rocketmq-console
docker run -d \
  --name rocketmq-console \
  --restart always \
  -p 8080:8080 \
  -e "JAVA_OPTS=-Drocketmq.namesrv.addr=${LOCAL_IP}:9876 -Dcom.rocketmq.sendMessageWithVIPChannel=false" \
  styletang/rocketmq-console-ng


# 安装并启动redis
stop_and_remove_container redis
docker run -d --name redis \
  --restart always \
  -p 6379:6379 \
  -v /usr/local/hzz/docker/redis/data:/data \
  -v /usr/local/hzz/docker/redis/conf/redis.conf:/usr/local/etc/redis/redis.conf \
  redis \
  redis-server /usr/local/etc/redis/redis.conf


# 启动zk
stop_and_remove_container zookeeper
docker run -d --name zookeeper \
  --restart always \
  -p 2181:2181 \
  -v /usr/local/hzz/docker/zookeeper/data:/data \
  -v /usr/local/hzz/docker/zookeeper/datalog:/datalog \
  -v /usr/local/hzz/docker/zookeeper/conf/zoo.cfg:/conf/zoo.cfg \
  zookeeper

# 启动nacos
stop_and_remove_container nacos
docker run -d --name nacos --restart always -e MODE=standalone -p 8848:8848 nacos/nacos-server:2.0.3


# 显示当前正在运行的容器
echo "正在运行的容器："
docker ps