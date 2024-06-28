
**docker-compose.yml中相关中间件依赖的配置 记录**

注：目前均是单机部署 后期改成集群模式

# zoo.cfg配置文件内容：

```
tickTime=2000
dataDir=/data
dataLogDir=/datalog
clientPort=2181
initLimit=5
syncLimit=2
server.1=zookeeper:2888:3888
```

# redis.conf文件内容：
```
appendonly yes
requirepass 123456

```

# broker.conf文件  内容：

```
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
```
