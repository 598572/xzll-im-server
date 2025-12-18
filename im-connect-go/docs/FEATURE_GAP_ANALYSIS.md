# im-connect（Java）vs im-connect-go 功能差异分析

## 📊 功能对比总览

| 功能模块 | Java 版本 | Go 版本 | 完成度 | 优先级 |
|---------|----------|---------|--------|--------|
| **WebSocket 连接管理** | ✅ 完整 | ✅ 完整 | 100% | - |
| **消息接收和解析** | ✅ 完整 | ✅ 完整 | 100% | - |
| **消息路由分发** | ✅ 完整 | ✅ 完整 | 100% | - |
| **认证和鉴权** | ✅ 完整 | ✅ 完整 | 100% | - |
| **心跳检测** | ✅ 完整 | ✅ 完整 | 100% | - |
| **流量控制** | ✅ 完整 | ✅ 完整 | 100% | - |
| **消息持久化** | ✅ 完整 | ❌ TODO | 0% | 🔴 **高** |
| **RocketMQ 投递** | ✅ 完整 | ❌ 缺失 | 0% | 🔴 **高** |
| **消息重试机制** | ✅ 完整 | ⚠️ 部分 | 60% | 🟡 **中** |
| **离线消息推送** | ✅ 完整 | ❌ TODO | 0% | 🟡 **中** |
| **跨服务器转发** | ✅ 完整 | ⚠️ 部分 | 40% | 🟡 **中** |
| **消息回执确认** | ✅ 完整 | ✅ 完整 | 100% | - |
| **群聊消息** | ✅ 完整 | ❌ 未实现 | 0% | 🟢 低 |
| **消息撤回** | ✅ 完整 | ✅ 完整 | 100% | - |

---

## 🚨 关键功能缺失

### 1. 消息持久化到数据库 ❌

**Java 版本实现**：
```java
// C2CMsgSendProtoStrategyImpl.java
@Override
public void exchange(Channel channel, ImProtoRequest protoRequest) {
    // ... 解析消息 ...
    
    // 1. 持久化消息到MySQL
    C2CMessage dbMessage = buildC2CMessage(sendReq);
    c2cMsgService.saveMessage(dbMessage);  // 保存到数据库
    
    // 2. 投递到RocketMQ
    c2cMsgProducer.send(buildMQMessage(dbMessage));
    
    // 3. 推送给在线用户或转发
    // ...
}
```

**Go 版本现状**：
```go
// c2c_send.go (第94-103行)
// 4. 保存消息到数据库（对标 Java 消息持久化）
if err := s.saveMessage(fromUserID, toUserID, sendReq); err != nil {
    s.logger.Error("保存消息失败", ...)
    // 消息保存失败，但继续尝试发送（可配置行为）
}

// 实际实现（第230-243行）
func (s *C2CMsgSendStrategy) saveMessage(...) error {
    // TODO: 实现数据库保存逻辑
    // 可以使用 MySQL、PostgreSQL 或其他数据库
    
    s.logger.Debug("保存 C2C 消息到数据库", ...)
    
    // 模拟数据库保存 ⚠️ 没有真正保存！
    return nil
}
```

**影响**：
- ❌ 消息没有持久化，服务重启后丢失
- ❌ 无法查询历史消息
- ❌ 离线消息无法从数据库加载

---

### 2. RocketMQ 消息投递 ❌

**Java 版本实现**：
```java
// C2CMsgSendProtoStrategyImpl.java
public void exchange(Channel channel, ImProtoRequest protoRequest) {
    // ... 消息处理 ...
    
    // 投递到RocketMQ（供im-business等服务消费）
    C2CMsgEvent mqEvent = C2CMsgEvent.builder()
        .clientMsgId(clientMsgId)
        .msgId(msgId)
        .fromUserId(fromUserId)
        .toUserId(toUserId)
        .msgContent(sendReq.getContent())
        .msgFormat(sendReq.getFormat())
        .msgCreateTime(System.currentTimeMillis())
        .build();
        
    // 发送到MQ
    rocketMQTemplate.asyncSend(
        "im-c2c-msg-topic",  // Topic
        MessageBuilder.withPayload(mqEvent).build(),
        new SendCallback() {
            @Override
            public void onSuccess(SendResult sendResult) {
                log.info("消息投递MQ成功: {}", msgId);
            }
            
            @Override
            public void onException(Throwable e) {
                log.error("消息投递MQ失败: {}", msgId, e);
            }
        }
    );
}
```

**Go 版本现状**：
- ❌ 完全没有 RocketMQ 集成
- ❌ 没有消息队列投递逻辑
- ⚠️ 配置文件中有 RocketMQ 配置，但代码中未使用

**影响**：
- ❌ im-business 服务无法消费消息（无法做消息审核、统计等）
- ❌ 无法实现异步消息处理
- ❌ 无法与其他微服务集成

---

### 3. 消息重投递机制不完整 ⚠️

**Java 版本实现**：
```java
// C2CMsgRetryServiceImpl.java
@Scheduled(fixedDelay = 1000) // 每秒扫描一次
public void scanRetryQueue() {
    // 1. 从Redis ZSet扫描到期消息
    Set<String> expiredMsgIds = redisTemplate.opsForZSet()
        .rangeByScore(C2C_MSG_RETRY_QUEUE, 0, System.currentTimeMillis());
    
    for (String msgId : expiredMsgIds) {
        // 2. 获取消息详情
        C2CMsgRetryEvent event = getRetryEvent(msgId);
        
        // 3. 重新投递到RocketMQ
        if (event.getRetryCount() < maxRetries) {
            // 重新投递
            rocketMQTemplate.send("im-c2c-msg-topic", event);
            
            // 更新重试次数
            event.setRetryCount(event.getRetryCount() + 1);
            
            // 重新加入延迟队列
            addToRetryQueue(event, getNextDelay(event.getRetryCount()));
        } else {
            // 超过最大重试次数，标记为失败
            markAsFailed(event);
        }
    }
}
```

**Go 版本现状**：
```go
// retry_service.go 实现了基于Redis的重试机制
// ✅ 有延迟队列扫描
// ✅ 有重试逻辑
// ❌ 但只是重新推送WebSocket，没有投递到MQ
// ❌ 没有持久化到数据库

func (s *C2CMsgRetryService) processRetryBatch(...) {
    // 检查用户是否在线
    if isOnline && conn != nil {
        // 发送重试消息
        s.sendRetryMessage(ctx, conn, event)  // ✅ 只推送WebSocket
        // ❌ 没有投递到MQ
    } else {
        // 标记为离线消息
        s.markAsOffline(ctx, event)
        // ❌ 没有真正保存到数据库或MQ
    }
}
```

**差异**：
| 功能 | Java 版本 | Go 版本 |
|-----|----------|---------|
| Redis 延迟队列 | ✅ | ✅ |
| 定时扫描 | ✅ | ✅ |
| WebSocket 重推 | ✅ | ✅ |
| MQ 重投递 | ✅ | ❌ |
| 数据库更新 | ✅ | ❌ |
| 离线消息处理 | ✅ | ❌ TODO |

---

### 4. 离线消息处理 ❌

**Java 版本实现**：
```java
// OfflineMsgServiceImpl.java
public void saveOfflineMessage(C2CMessage message) {
    // 1. 保存到数据库
    offlineMsgMapper.insert(message);
    
    // 2. 保存到Redis（快速查询）
    String key = String.format("im:offline:msg:%s", message.getToUserId());
    redisTemplate.opsForList().rightPush(key, message);
    
    // 3. 设置过期时间（7天）
    redisTemplate.expire(key, 7, TimeUnit.DAYS);
}

// 用户上线时推送离线消息
public void pushOfflineMessages(String userId, Channel channel) {
    // 从Redis获取离线消息（最近N条）
    String key = String.format("im:offline:msg:%s", userId);
    List<C2CMessage> messages = redisTemplate.opsForList()
        .range(key, 0, pushOfflineMsgCount - 1);
    
    // 推送给客户端
    for (C2CMessage message : messages) {
        channel.writeAndFlush(buildProtoMsg(message));
    }
    
    // 清理已推送的离线消息
    redisTemplate.delete(key);
}
```

**Go 版本现状**：
```go
// c2c_send.go
func (s *C2CMsgSendStrategy) saveOfflineMessage(...) error {
    // TODO: 实现离线消息保存逻辑
    // 可以使用 Redis、数据库或其他存储
    
    s.logger.Info("保存离线消息", ...)
    
    // 模拟离线消息保存 ⚠️ 没有真正保存！
    return nil
}
```

**影响**：
- ❌ 用户离线期间的消息无法保存
- ❌ 用户上线后收不到离线消息
- ❌ 违背了即时通讯的可靠性要求

---

## 📝 详细功能差异

### 消息处理流程对比

#### Java 版本完整流程

```
客户端A发送消息
    ↓
im-connect接收
    ↓
┌──────────────────┐
│ 1. 消息验证      │
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 2. 生成服务器MsgID│ (雪花算法)
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 3. 持久化到MySQL  │ ✅ 保存聊天记录
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 4. 投递到RocketMQ │ ✅ 供im-business消费
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 5. 检查接收人在线 │
└────┬─────────┬───┘
     │         │
 在线│         │离线
     ↓         ↓
┌─────────┐ ┌──────────────┐
│ WebSocket│ │ 保存离线消息 │ ✅
│ 推送     │ │ (DB + Redis) │
└────┬────┘ └──────┬───────┘
     │            │
     ↓            ↓
┌──────────────────┐
│ 6. 添加到重试队列 │ ✅ Redis ZSet
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 7. 发送ServerAck  │ ✅ 给发送者
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 8. 等待客户端ACK  │
└────┬────────┬────┘
     │        │
收到ACK│      │超时
     ↓        ↓
 ┌─────┐  ┌──────────────┐
 │删除 │  │ 重新投递MQ   │ ✅
 │重试 │  │ 或标记为离线 │
 └─────┘  └──────────────┘
```

#### Go 版本当前流程

```
客户端A发送消息
    ↓
im-connect-go接收
    ↓
┌──────────────────┐
│ 1. 消息验证      │ ✅
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 2. 生成服务器MsgID│ ✅ (简化版时间戳)
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 3. 持久化到MySQL  │ ❌ TODO（只打日志）
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 4. 投递到RocketMQ │ ❌ 完全缺失
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 5. 检查接收人在线 │ ✅
└────┬─────────┬───┘
     │         │
 在线│         │离线
     ↓         ↓
┌─────────┐ ┌──────────────┐
│ WebSocket│ │ 保存离线消息 │ ❌ TODO
│ 推送     │ │              │
└────┬────┘ └──────┬───────┘
     │            │
     ↓            ↓
┌──────────────────┐
│ 6. 添加到重试队列 │ ⚠️ 有但未集成
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 7. 发送ServerAck  │ ✅
└────────┬─────────┘
         ↓
┌──────────────────┐
│ 8. 等待客户端ACK  │ ⚠️ 重试只推WebSocket
└────┬────────┬────┘
     │        │
收到ACK│      │超时
     ↓        ↓
 ┌─────┐  ┌──────────────┐
 │删除 │  │ 重新推送WS   │ ⚠️ 只推WebSocket
 │重试 │  │ 无MQ投递     │ ❌ 没有MQ
 └─────┘  └──────────────┘
```

---

## 🔧 实现建议

### 优先级 1：消息持久化（高）

**实现步骤**：

1. **创建数据库表**
```sql
CREATE TABLE `im_c2c_message` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  `msg_id` BIGINT NOT NULL COMMENT '服务器消息ID',
  `client_msg_id` VARCHAR(64) NOT NULL COMMENT '客户端消息ID',
  `from_user_id` BIGINT NOT NULL COMMENT '发送人ID',
  `to_user_id` BIGINT NOT NULL COMMENT '接收人ID',
  `chat_id` VARCHAR(64) DEFAULT NULL COMMENT '会话ID',
  `msg_content` TEXT NOT NULL COMMENT '消息内容',
  `msg_format` INT DEFAULT 1 COMMENT '消息格式',
  `msg_status` INT DEFAULT 0 COMMENT '消息状态 0-发送中 1-成功 2-失败',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_msg_id` (`msg_id`),
  KEY `idx_from_to_time` (`from_user_id`, `to_user_id`, `create_time`),
  KEY `idx_to_status_time` (`to_user_id`, `msg_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='C2C消息表';
```

2. **实现数据库操作层**
```go
// pkg/database/c2c_message.go
package database

type C2CMessageDAO struct {
    db *gorm.DB
}

func (dao *C2CMessageDAO) SaveMessage(msg *C2CMessage) error {
    return dao.db.Create(msg).Error
}

func (dao *C2CMessageDAO) GetOfflineMessages(userID string, limit int) ([]*C2CMessage, error) {
    var messages []*C2CMessage
    err := dao.db.Where("to_user_id = ? AND msg_status = 0", userID).
        Order("create_time DESC").
        Limit(limit).
        Find(&messages).Error
    return messages, err
}
```

3. **集成到消息发送流程**
```go
// internal/strategy/c2c_send.go
func (s *C2CMsgSendStrategy) saveMessage(...) error {
    // 构建数据库记录
    dbMessage := &database.C2CMessage{
        MsgID:        sendReq.MsgId,
        ClientMsgID:  string(sendReq.ClientMsgId),
        FromUserID:   fromUserID,
        ToUserID:     toUserID,
        MsgContent:   sendReq.Content,
        MsgFormat:    sendReq.Format,
        MsgStatus:    0, // 发送中
        CreateTime:   time.Now(),
    }
    
    // 保存到数据库
    return s.messageDAO.SaveMessage(dbMessage)
}
```

---

### 优先级 2：RocketMQ 集成（高）

**实现步骤**：

1. **引入 RocketMQ Go 客户端**
```bash
go get github.com/apache/rocketmq-client-go/v2
```

2. **创建 RocketMQ 生产者**
```go
// pkg/mq/rocketmq/producer.go
package rocketmq

import (
    "github.com/apache/rocketmq-client-go/v2"
    "github.com/apache/rocketmq-client-go/v2/primitive"
    "github.com/apache/rocketmq-client-go/v2/producer"
)

type Producer struct {
    producer rocketmq.Producer
    logger   *zap.Logger
}

func NewProducer(cfg *Config, logger *zap.Logger) (*Producer, error) {
    p, err := rocketmq.NewProducer(
        producer.WithGroupName(cfg.ProducerGroupName),
        producer.WithNameServer(strings.Split(cfg.ServerAddr, ";")),
        producer.WithRetry(cfg.RetryTimes),
    )
    
    if err != nil {
        return nil, err
    }
    
    if err := p.Start(); err != nil {
        return nil, err
    }
    
    return &Producer{
        producer: p,
        logger:   logger,
    }, nil
}

// 发送C2C消息事件
func (p *Producer) SendC2CMsgEvent(event *C2CMsgEvent) error {
    // 序列化消息
    data, err := json.Marshal(event)
    if err != nil {
        return err
    }
    
    // 构建RocketMQ消息
    msg := &primitive.Message{
        Topic: "im-c2c-msg-topic",
        Body:  data,
    }
    msg.WithTag("C2C_MSG")
    msg.WithKeys([]string{event.MsgID})
    
    // 异步发送
    result, err := p.producer.SendAsync(context.Background(), 
        func(ctx context.Context, result *primitive.SendResult, err error) {
            if err != nil {
                p.logger.Error("消息投递MQ失败",
                    zap.String("msg_id", event.MsgID),
                    zap.Error(err),
                )
            } else {
                p.logger.Info("消息投递MQ成功",
                    zap.String("msg_id", event.MsgID),
                    zap.String("message_queue", result.MessageQueue.String()),
                )
            }
        }, 
        msg,
    )
    
    return err
}
```

3. **集成到消息发送流程**
```go
// internal/strategy/c2c_send.go
type C2CMsgSendStrategy struct {
    config         *config.Config
    logger         *zap.Logger
    channelManager *channel.Manager
    messageDAO     *database.C2CMessageDAO  // 新增
    mqProducer     *rocketmq.Producer       // 新增
}

func (s *C2CMsgSendStrategy) Exchange(...) error {
    // ... 前面的处理 ...
    
    // 4. 保存消息到数据库
    if err := s.saveMessage(fromUserID, toUserID, sendReq); err != nil {
        s.logger.Error("保存消息失败", zap.Error(err))
        // 继续处理
    }
    
    // 5. 投递到RocketMQ（新增）
    mqEvent := &rocketmq.C2CMsgEvent{
        ClientMsgID:   string(sendReq.ClientMsgId),
        MsgID:         fmt.Sprintf("%d", serverMsgID),
        FromUserID:    fromUserID,
        ToUserID:      toUserID,
        MsgContent:    sendReq.Content,
        MsgFormat:     sendReq.Format,
        MsgCreateTime: time.Now().UnixMilli(),
    }
    
    if err := s.mqProducer.SendC2CMsgEvent(mqEvent); err != nil {
        s.logger.Error("投递消息到MQ失败", zap.Error(err))
        // 不阻塞主流程
    }
    
    // 6. 检查接收人是否在线
    // ...
}
```

---

### 优先级 3：完善重试机制（中）

**实现步骤**：

1. **修改重试服务，增加 MQ 重投递**
```go
// internal/service/retry_service.go
type C2CMsgRetryService struct {
    config         *C2CMsgRetryConfig
    redisClient    *redis.RedisClient
    channelManager *channel.Manager
    mqProducer     *rocketmq.Producer  // 新增
    logger         *zap.Logger
    
    stopChan chan struct{}
    wg       sync.WaitGroup
}

func (s *C2CMsgRetryService) processRetryBatch(...) {
    for _, event := range events {
        // ... 前面的处理 ...
        
        // 根据在线状态处理
        if isOnline && conn != nil {
            // 在线：WebSocket 推送
            if err := s.sendRetryMessage(ctx, conn, event); err != nil {
                s.logger.Error("重试发送消息失败", zap.Error(err))
            }
        } else {
            // 离线：重新投递到MQ（新增）
            s.logger.Info("用户离线，重新投递消息到MQ",
                zap.String("msg_id", event.MsgID),
            )
            
            mqEvent := &rocketmq.C2CMsgEvent{
                ClientMsgID:   event.ClientMsgID,
                MsgID:         event.MsgID,
                FromUserID:    event.FromUserID,
                ToUserID:      event.ToUserID,
                MsgContent:    event.MsgContent,
                MsgFormat:     event.MsgFormat,
                MsgCreateTime: event.MsgCreateTime,
            }
            
            if err := s.mqProducer.SendC2CMsgEvent(mqEvent); err != nil {
                s.logger.Error("重新投递MQ失败", zap.Error(err))
            }
        }
        
        // 更新重试次数或标记为离线
        // ...
    }
}
```

---

### 优先级 4：离线消息处理（中）

**实现步骤**：

1. **实现离线消息保存**
```go
// internal/service/offline_msg_service.go
package service

type OfflineMsgService struct {
    redisClient *redis.RedisClient
    messageDAO  *database.C2CMessageDAO
    logger      *zap.Logger
}

func (s *OfflineMsgService) SaveOfflineMessage(msg *C2CMessage) error {
    // 1. 保存到数据库
    if err := s.messageDAO.SaveOfflineMessage(msg); err != nil {
        return err
    }
    
    // 2. 保存到Redis（快速查询）
    key := fmt.Sprintf("im:offline:msg:%s", msg.ToUserID)
    data, _ := json.Marshal(msg)
    
    if err := s.redisClient.LPush(key, string(data)); err != nil {
        return err
    }
    
    // 3. 设置过期时间（7天）
    s.redisClient.Expire(key, 7*24*time.Hour)
    
    return nil
}

func (s *OfflineMsgService) GetOfflineMessages(userID string, limit int) ([]*C2CMessage, error) {
    // 从Redis获取
    key := fmt.Sprintf("im:offline:msg:%s", userID)
    messages, err := s.redisClient.LRange(key, 0, int64(limit-1))
    
    if err != nil || len(messages) == 0 {
        // Redis中没有，从数据库加载
        return s.messageDAO.GetOfflineMessages(userID, limit)
    }
    
    // 解析消息
    var result []*C2CMessage
    for _, msgData := range messages {
        var msg C2CMessage
        if err := json.Unmarshal([]byte(msgData), &msg); err == nil {
            result = append(result, &msg)
        }
    }
    
    return result, nil
}

func (s *OfflineMsgService) ClearOfflineMessages(userID string) error {
    key := fmt.Sprintf("im:offline:msg:%s", userID)
    return s.redisClient.Del(key)
}
```

2. **用户上线时推送离线消息**
```go
// internal/auth/handler.go
func (h *AuthHandler) OnUserOnline(conn channel.Connection, userID string) {
    // ... 原有逻辑 ...
    
    // 推送离线消息
    offlineMessages, err := h.offlineMsgService.GetOfflineMessages(userID, 10)
    if err != nil {
        h.logger.Error("获取离线消息失败", zap.Error(err))
        return
    }
    
    h.logger.Info("推送离线消息",
        zap.String("user_id", userID),
        zap.Int("count", len(offlineMessages)),
    )
    
    for _, msg := range offlineMessages {
        h.pushOfflineMessage(conn, msg)
    }
    
    // 清理已推送的离线消息
    h.offlineMsgService.ClearOfflineMessages(userID)
}
```

---

## 📋 实现计划

### 第一阶段（核心功能，1-2周）
- [ ] 实现消息持久化到 MySQL
- [ ] 集成 RocketMQ 生产者
- [ ] 消息发送时投递到 MQ

### 第二阶段（可靠性，1周）
- [ ] 完善消息重试机制
- [ ] 集成 MQ 重投递
- [ ] 实现离线消息保存

### 第三阶段（完整性，1周）
- [ ] 实现离线消息推送
- [ ] 完善跨服务器转发
- [ ] 添加消息状态跟踪

### 第四阶段（测试和优化）
- [ ] 单元测试
- [ ] 集成测试
- [ ] 性能测试和优化

---

## 🎯 总结

### 当前 Go 版本缺失的核心功能

1. **消息持久化** ❌ - 导致消息无法持久化
2. **RocketMQ 投递** ❌ - 导致微服务无法协作
3. **离线消息处理** ❌ - 导致离线消息丢失
4. **重试机制不完整** ⚠️ - 只推WebSocket，不投递MQ

### 建议的实现顺序

```
优先级 1: 消息持久化 (数据库)
    ↓
优先级 2: RocketMQ 集成
    ↓
优先级 3: 完善重试机制
    ↓
优先级 4: 离线消息处理
```

### 预期工作量

- **核心功能实现**：2-3周
- **测试和调试**：1周
- **文档和优化**：1周
- **总计**：4-5周

---

## 📚 相关资源

- [RocketMQ Go 客户端文档](https://github.com/apache/rocketmq-client-go)
- [GORM 文档](https://gorm.io/docs/)
- [消息队列最佳实践](https://rocketmq.apache.org/docs/bestPractice/01bestpractice/)

