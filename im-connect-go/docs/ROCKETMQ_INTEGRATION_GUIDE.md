# RocketMQ 集成指南

## ✅ 实现完成

im-connect-go 已成功集成 RocketMQ，实现完整的消息投递流程！

```
im-connect-go → RocketMQ → im-business（Java）→ 保存到数据库
```

---

## 🎯 实现的功能

### 1. 消息持久化（通过 RocketMQ）
```
客户端发送消息 → im-connect-go 
                    ↓
                投递到 RocketMQ（Topic: XZLL_C2CMSG_TOPIC）
                    ↓
                im-business 消费 MQ
                    ├─ 保存消息到 MySQL ✅
                    ├─ 更新会话记录 ✅
                    ├─ 消息审核、敏感词过滤 ✅
                    └─ 消息统计、分析 ✅
```

### 2. 离线消息处理
```
用户离线 → im-connect-go 
              ↓
          投递离线消息到 RocketMQ
              ↓
          im-business 消费 MQ
              └─ 保存离线消息到数据库 ✅
```

### 3. 客户端 ACK 处理
```
客户端发送 ACK → im-connect-go
                    ↓
                投递 ACK 事件到 RocketMQ
                    ↓
                im-business 消费 MQ
                    └─ 更新消息状态为"已送达" ✅
```

---

## 📁 代码结构

```
im-connect-go/
├── pkg/mq/                      # RocketMQ 包
│   ├── config.go               # MQ 配置结构
│   └── producer.go             # MQ 生产者（核心）
│
├── internal/strategy/
│   └── c2c_send.go             # 消息发送策略（已集成 MQ）
│
├── internal/handler/
│   └── message.go              # 消息处理器（已集成 MQ）
│
├── internal/config/
│   └── config.go               # 配置读取（已添加 RocketMQ 配置）
│
├── internal/server/
│   └── websocket.go            # WebSocket 服务器（已传递 MQ Producer）
│
└── cmd/
    └── main.go                 # 主程序（已初始化 MQ Producer）
```

---

## 🚀 使用方法

### 1. 配置 RocketMQ

#### 方式1：在 Nacos 中配置（推荐）

登录 Nacos 控制台，在 `im-connect-go.yaml` 中添加：

```yaml
# ==================== RocketMQ 配置 ====================
rocketmq:
  # MQ 服务器地址（支持多个，分号分隔）
  server_addr: "192.168.1.100:9876;192.168.1.101:9876;192.168.1.102:9876"
  
  # 生产者配置
  producer:
    group_name: "ImConnectGoProducerGroup"
    max_message_size: 4096
    send_timeout: 10
    retry_times: 3
  
  # 消费者配置（暂时不需要，im-business 消费）
  consumer:
    group_name: "ImConnectGoConsumer"
    thread_min: 10
    thread_max: 20
    max_reconsume_times: 5
    batch_size: 1
    consume_timeout: 15
```

#### 方式2：本地配置文件

编辑 `configs/bootstrap-dev.yaml`（或 bootstrap-test.yaml）：

```yaml
nacos:
  server_addr: "localhost:8848"
  namespace: "dev"
  data_id: "im-connect-go.yaml"
  group: "DEFAULT_GROUP"
  
# 本地测试可以临时添加（优先级低于 Nacos）
rocketmq:
  server_addr: "localhost:9876"
  producer:
    group_name: "ImConnectGoProducerGroup"
    max_message_size: 4096
    send_timeout: 10
    retry_times: 3
```

### 2. 启动服务

```bash
# 开发环境
./im-connect-go --env=dev

# 测试环境
./im-connect-go --env=test

# 生产环境
./im-connect-go --env=prod
```

### 3. 查看日志

成功启动后，你会看到：

```
✅ RocketMQ Producer 启动成功
   name_servers: [192.168.1.100:9876]
   group: ImConnectGoProducerGroup
   retry_times: 3
   send_timeout: 10
```

当有消息发送时：

```
✅ 消息已发送到 RocketMQ
   msg_id: 1234567890
   from_user_id: 10001
   to_user_id: 10002
   
✅ 消息投递 RocketMQ 成功
   topic: XZLL_C2CMSG_TOPIC
   tag: C2C_SEND
   msg_id: 1234567890
   event_type: C2C_SEND_MSG
   queue: broker-a:0
   offset: 12345
```

---

## 📊 消息流程详解

### 流程1：C2C 消息发送

```go
// 1. 客户端发送消息
// internal/strategy/c2c_send.go:89-106

// 2. 解析消息
C2CSendReq req = parse(protoRequest.Payload)

// 3. 生成服务器消息ID
serverMsgID := generateMessageID()

// 4. ⭐ 发送到 RocketMQ
msgEvent := &mq.C2CMsgEvent{
    ClientMsgID:   clientMsgID,
    MsgID:         serverMsgID,
    FromUserID:    fromUserID,
    ToUserID:      toUserID,
    ChatID:        chatID,
    MsgContent:    content,
    MsgFormat:     format,
    MsgCreateTime: timestamp,
}
s.mqProducer.SendC2CMsg(msgEvent)  // 异步发送

// 5. 检查接收人在线
if isOnline {
    pushMessage(toUserID)        // WebSocket 推送
} else {
    s.sendOfflineMsgToMQ(msgEvent) // 离线消息投递 MQ
}

// 6. 发送 ServerAck 给发送者
sendServerAck(conn, sendReq)
```

### 流程2：RocketMQ 生产者处理

```go
// pkg/mq/producer.go:76-109

// 1. 构建 ClusterEvent（对标 Java 的 ClusterEvent）
clusterEvent := &ClusterEvent{
    ClusterEventType: "C2C_SEND_MSG",  // 事件类型
    Data:             string(jsonData), // 消息数据（JSON）
}

// 2. 构建 RocketMQ 消息
msg := &primitive.Message{
    Topic: "XZLL_C2CMSG_TOPIC",
    Body:  eventJSON,
}
msg.WithTag("C2C_SEND")
msg.WithKeys([]string{msgID})

// 3. 异步发送
producer.SendAsync(context.Background(), 
    func(result, err) {
        if err != nil {
            logger.Error("消息投递失败", err)
        } else {
            logger.Info("消息投递成功", msgID)
        }
    },
    msg,
)
```

### 流程3：im-business 消费处理（Java）

```java
// im-business-service/.../C2CMsgConsumer.java

@RocketMQMessageListener(
    topic = "XZLL_C2CMSG_TOPIC",
    consumerGroup = "ImBusinessConsumerGroup",
    selectorExpression = "C2C_SEND || C2C_OFFLINE || C2C_ACK"
)
public class C2CMsgConsumer implements RocketMQListener<String> {
    
    @Override
    public void onMessage(String message) {
        // 1. 解析 ClusterEvent
        ClusterEvent event = JSON.parseObject(message, ClusterEvent.class);
        
        // 2. 根据事件类型处理
        switch (event.getClusterEventType()) {
            case "C2C_SEND_MSG":
                // 保存消息到 MySQL
                c2cMsgService.saveMessage(event.getData());
                // 更新会话记录
                c2cMsgService.updateSession(event.getData());
                break;
                
            case "C2C_OFF_LINE_MSG":
                // 保存离线消息
                c2cMsgService.saveOfflineMessage(event.getData());
                break;
                
            case "C2C_CLIENT_RECEIVED_ACK":
                // 更新消息状态为"已送达"
                c2cMsgService.updateMessageStatus(event.getData());
                break;
        }
    }
}
```

---

## 🧪 测试验证

### 1. 启动服务

```bash
# 终端1：启动 RocketMQ NameServer
cd /path/to/rocketmq
nohup sh bin/mqnamesrv &

# 终端2：启动 RocketMQ Broker
nohup sh bin/mqbroker -n localhost:9876 &

# 终端3：启动 im-business（Java）
cd /path/to/im-business
mvn spring-boot:run

# 终端4：启动 im-connect-go
cd /path/to/im-connect-go
./im-connect-go --env=dev
```

### 2. 发送测试消息

使用 WebSocket 客户端发送消息：

```javascript
// 连接 WebSocket
const ws = new WebSocket('ws://localhost:10001/ws');

// 发送 C2C 消息
const message = {
    type: 'C2C_SEND',
    payload: {
        clientMsgId: 'uuid-12345',
        to: 10002,
        from: 10001,
        content: 'Hello, World!',
        format: 1,
    }
};
ws.send(JSON.stringify(message));
```

### 3. 验证结果

#### im-connect-go 日志：
```
✅ 消息已发送到 RocketMQ
   msg_id: 1234567890
   from_user_id: 10001
   to_user_id: 10002
   
✅ 消息投递 RocketMQ 成功
   topic: XZLL_C2CMSG_TOPIC
   event_type: C2C_SEND_MSG
   offset: 12345
```

#### im-business 日志：
```
收到 MQ 消息: topic=XZLL_C2CMSG_TOPIC, event_type=C2C_SEND_MSG
保存消息到数据库: msgId=1234567890, from=10001, to=10002
更新会话记录: chatId=10001_10002
```

#### MySQL 数据库：
```sql
SELECT * FROM im_c2c_message WHERE msg_id = '1234567890';

+-------+------------+---------------+---------+---------+---------------+
| id    | msg_id     | client_msg_id | from_id | to_id   | msg_content   |
+-------+------------+---------------+---------+---------+---------------+
| 12345 | 1234567890 | uuid-12345    | 10001   | 10002   | Hello, World! |
+-------+------------+---------------+---------+---------+---------------+
```

---

## 📋 对比 Java 版本

| 功能 | Java im-connect | Go im-connect-go | 状态 |
|-----|----------------|------------------|------|
| **接收消息** | ✅ | ✅ | ✅ 一致 |
| **投递 RocketMQ** | ✅ | ✅ | ✅ 一致 |
| **离线消息** | ✅ | ✅ | ✅ 一致 |
| **WebSocket 推送** | ✅ | ✅ | ✅ 一致 |
| **客户端 ACK** | ✅ | ✅ | ✅ 一致 |
| **消息重试** | ✅ | ⚠️ 部分 | 🟡 待完善 |
| **跨服务器转发** | ✅ | ⚠️ 部分 | 🟡 待完善 |

---

## 🔧 故障排查

### 1. RocketMQ 连接失败

**错误信息**：
```
❌ 初始化 RocketMQ 生产者失败: dial tcp 192.168.1.100:9876: connect: connection refused
```

**解决方法**：
1. 检查 RocketMQ NameServer 是否启动
   ```bash
   jps | grep NamesrvStartup
   ```
2. 检查防火墙是否开放 9876 端口
3. 确认配置中的 IP 地址正确

### 2. 消息发送失败

**错误信息**：
```
❌ 消息投递 RocketMQ 失败: topic[XZLL_C2CMSG_TOPIC] not exist
```

**解决方法**：
1. 创建 Topic
   ```bash
   sh bin/mqadmin updateTopic -n localhost:9876 -t XZLL_C2CMSG_TOPIC -c DefaultCluster
   ```
2. 或者设置 RocketMQ 自动创建 Topic（开发环境）

### 3. im-business 未消费消息

**可能原因**：
1. im-business 未启动
2. 消费者组配置不正确
3. Topic 或 Tag 配置不匹配

**解决方法**：
检查 im-business 的消费者配置：
```java
@RocketMQMessageListener(
    topic = "XZLL_C2CMSG_TOPIC",  // 确保与 Go 版本一致
    consumerGroup = "ImBusinessConsumerGroup",
    selectorExpression = "C2C_SEND || C2C_OFFLINE || C2C_ACK"  // 确保包含这些 Tag
)
```

---

## 🎉 总结

### ✅ 已实现

1. **完整的 RocketMQ 集成**
   - Producer 生产者实现
   - 消息封装（ClusterEvent）
   - 异步发送与回调

2. **消息持久化流程**
   - C2C 消息投递
   - 离线消息投递
   - 客户端 ACK 投递

3. **与 Java 版本兼容**
   - Topic、Tag 命名一致
   - ClusterEvent 结构一致
   - 消息格式兼容

### 🟡 待优化

1. **消息重试机制**
   - 当前：只重推 WebSocket
   - 目标：重新投递 MQ

2. **跨服务器转发**
   - 当前：部分实现
   - 目标：完整 gRPC 转发

3. **监控和统计**
   - 添加 Prometheus 指标
   - MQ 发送成功率统计
   - 消息延迟监控

---

## 📚 相关文档

- [功能差异分析](./FEATURE_GAP_ANALYSIS.md)
- [实现指南](./IMPLEMENTATION_GUIDE.md)
- [缺失功能说明](./README_MISSING_FEATURES.md)
- [Java 到 Go 配置映射](./JAVA_TO_GO_CONFIG_MAPPING.md)

---

恭喜！🎉 你的 im-connect-go 现在已经完全支持 RocketMQ 消息投递了！

