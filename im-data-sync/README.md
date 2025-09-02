# Data Sync 数据同步服务

## 概述

Data Sync 是一个专门用于数据同步的服务模块，主要负责将业务数据从HBase同步到Elasticsearch等外部存储系统。

## 架构设计

```
长连接服务 → MQ → Business服务 → HBase落库 → RocketMQ → Data Sync服务 → ES写入
                                    ↓
                              发送给接收方
```

## 主要功能

1. **C2C消息记录同步**：将单聊消息记录从HBase同步到ES
2. **消息状态更新同步**：同步消息的已读、未读、离线等状态变更
3. **消息撤回状态同步**：同步消息的撤回状态变更

## 技术栈

- Spring Boot 2.7.0
- 复用现有的RocketMQ架构（im-common模块）
- Elasticsearch
- Hutool工具库

## 配置说明

### RocketMQ配置
```yaml
# 使用现有的RocketMQ配置，无需额外配置
# 消息发送通过DefaultMQProducer
# 消息消费通过RocketMQClusterEventListener接口实现
```

### Elasticsearch配置
```yaml
spring:
  elasticsearch:
    rest:
      uris: http://localhost:9200
      connection-timeout: 5000
      read-timeout: 5000
```

## 消息格式

### 数据同步消息结构
```json
// 外层ClusterEvent结构
{
  "clusterEventType": 1,
  "balanceId": "chat_123",
  "data": "{\"operationType\":\"SAVE\",\"dataType\":\"C2C_MSG_RECORD\",\"data\":{...},\"timestamp\":1640995200000,\"chatId\":\"chat_123\",\"msgId\":\"msg_456\"}",
  "createTime": "2024-12-20T10:00:00.000+00:00"
}

// 内层业务数据结构
{
  "operationType": "SAVE|UPDATE_STATUS|UPDATE_WITHDRAW",
  "dataType": "C2C_MSG_RECORD",
  "data": {
    // ImC2CMsgRecord对象数据
  },
  "timestamp": 1640995200000,
  "chatId": "chat_123",
  "msgId": "msg_456"
}
```

## 消费者架构

### 高效订阅机制
- **启动时订阅**：在`afterPropertiesSet()`方法中订阅指定的topic
- **避免网络浪费**：不在收到消息后再过滤topic，而是在启动时就只订阅需要的topic
- **并发消费**：使用并发消费模式，提高处理效率

### 核心组件
1. **DataSyncMessageConsumer**：实现`RocketMQClusterEventListener`和`InitializingBean`接口
2. **DataSyncConsumerWrap**：消费者包装类，管理RocketMQ消费者生命周期
3. **DataSyncService**：数据同步业务逻辑
4. **ESSyncService**：ES同步服务

## 部署说明

1. 确保RocketMQ服务已启动
2. 确保Elasticsearch服务已启动
3. 启动Data Sync服务
4. 确保DefaultMQProducer已正确配置（复用现有配置）
5. 配置正确的ES连接信息

## 扩展说明

后续可以轻松添加其他类型的数据同步：

1. 群聊消息记录同步
2. 用户信息同步
3. 群组信息同步
4. 其他业务数据同步

只需要在`DataSyncMessageConsumer`中添加新的数据类型处理逻辑即可。

## 性能优化

- **启动时订阅**：避免不必要的网络传输
- **并发消费**：提高消息处理效率
- **异步处理**：ES写入不影响主业务流程
- **错误处理**：完善的异常处理和日志记录
