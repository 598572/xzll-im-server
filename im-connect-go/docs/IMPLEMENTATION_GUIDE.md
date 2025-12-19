# im-connect-go 缺失功能实现指南

## 📌 核心问题总结

从 Java 版本 `C2CMsgSendProtoStrategyImpl` 的代码分析，发现 Go 版本缺失以下关键逻辑：

### Java 版本的完整流程（第106行）

```java
//1. 更新会话记录并保存消息记录
c2CMsgProvider.sendC2CMsg(packet);  // ⬅️ 这一步Go版本完全缺失！
```

**`C2CMsgProvider.sendC2CMsg()` 做了什么？**

```java
public boolean sendC2CMsg(C2CSendMsgAO dto) {
    // 1. 包装成 ClusterEvent
    ClusterEvent clusterEvent = new ClusterEvent();
    clusterEvent.setData(JSONUtil.toJsonStr(dto));
    clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_SEND_MSG);
    
    // 2. 发送到 RocketMQ（Topic: XZLL_C2CMSG_TOPIC）
    result = rocketMqProducerWrap.sendClusterEvent(C2C_TOPIC, clusterEvent, dto.getMsgId());
    
    log.info("往mq发送单聊消息结果:{}", result);
    return result;
}
```

**关键点**：
- ✅ **每条消息都投递到 RocketMQ**（无论在线/离线）
- ✅ im-business 服务消费 MQ，负责：
  - 消息持久化到 MySQL
  - 更新会话记录
  - 消息审核、敏感词过滤
  - 消息统计、分析

---

## 🎯 快速实现方案（最小改动）

### 方案一：使用 HTTP 调用 im-business（最简单）⭐ 推荐

**优点**：
- 不需要引入 RocketMQ 依赖
- 实现简单快速
- 可以复用 Java 版本的 im-business 服务

**实现步骤**：

#### 1. 创建 HTTP 客户端

```go
// pkg/httpclient/business_client.go
package httpclient

import (
    "bytes"
    "encoding/json"
    "fmt"
    "net/http"
    "time"
    
    "go.uber.org/zap"
)

type BusinessClient struct {
    baseURL    string
    httpClient *http.Client
    logger     *zap.Logger
}

func NewBusinessClient(baseURL string, logger *zap.Logger) *BusinessClient {
    return &BusinessClient{
        baseURL: baseURL,
        httpClient: &http.Client{
            Timeout: 10 * time.Second,
        },
        logger: logger,
    }
}

// C2CMsgEvent 消息事件（对应 Java 的 C2CSendMsgAO）
type C2CMsgEvent struct {
    ClientMsgID   string `json:"clientMsgId"`
    MsgID         string `json:"msgId"`
    FromUserID    string `json:"fromUserId"`
    ToUserID      string `json:"toUserId"`
    ChatID        string `json:"chatId"`
    MsgContent    string `json:"msgContent"`
    MsgFormat     int32  `json:"msgFormat"`
    MsgCreateTime int64  `json:"msgCreateTime"`
}

// SendC2CMsg 发送 C2C 消息到 im-business
func (c *BusinessClient) SendC2CMsg(event *C2CMsgEvent) error {
    url := fmt.Sprintf("%s/api/msg/c2c/send", c.baseURL)
    
    data, err := json.Marshal(event)
    if err != nil {
        return fmt.Errorf("序列化消息失败: %w", err)
    }
    
    req, err := http.NewRequest("POST", url, bytes.NewBuffer(data))
    if err != nil {
        return fmt.Errorf("创建请求失败: %w", err)
    }
    
    req.Header.Set("Content-Type", "application/json")
    
    resp, err := c.httpClient.Do(req)
    if err != nil {
        return fmt.Errorf("HTTP请求失败: %w", err)
    }
    defer resp.Body.Close()
    
    if resp.StatusCode != http.StatusOK {
        return fmt.Errorf("HTTP请求失败: status=%d", resp.StatusCode)
    }
    
    c.logger.Info("消息已发送到 im-business",
        zap.String("msg_id", event.MsgID),
        zap.String("url", url),
    )
    
    return nil
}

// SaveOfflineMsg 保存离线消息
func (c *BusinessClient) SaveOfflineMsg(event *C2CMsgEvent) error {
    url := fmt.Sprintf("%s/api/msg/c2c/offline", c.baseURL)
    
    data, err := json.Marshal(event)
    if err != nil {
        return fmt.Errorf("序列化离线消息失败: %w", err)
    }
    
    req, err := http.NewRequest("POST", url, bytes.NewBuffer(data))
    if err != nil {
        return fmt.Errorf("创建请求失败: %w", err)
    }
    
    req.Header.Set("Content-Type", "application/json")
    
    resp, err := c.httpClient.Do(req)
    if err != nil {
        return fmt.Errorf("HTTP请求失败: %w", err)
    }
    defer resp.Body.Close()
    
    if resp.StatusCode != http.StatusOK {
        return fmt.Errorf("HTTP请求失败: status=%d", resp.StatusCode)
    }
    
    c.logger.Info("离线消息已发送到 im-business",
        zap.String("msg_id", event.MsgID),
        zap.String("to_user_id", event.ToUserID),
    )
    
    return nil
}
```

#### 2. 集成到消息发送策略

```go
// internal/strategy/c2c_send.go
type C2CMsgSendStrategy struct {
    config         *config.Config
    logger         *zap.Logger
    channelManager *channel.Manager
    businessClient *httpclient.BusinessClient  // 新增
}

func NewC2CMsgSendStrategy(..., businessClient *httpclient.BusinessClient) *C2CMsgSendStrategy {
    return &C2CMsgSendStrategy{
        config:         cfg,
        logger:         logger,
        channelManager: cm,
        businessClient: businessClient,  // 新增
    }
}

func (s *C2CMsgSendStrategy) Exchange(conn channel.Connection, protoRequest *pb.ImProtoRequest) error {
    // ... 前面的解析和验证代码 ...
    
    // 3. 生成服务器消息ID
    serverMsgID := s.generateMessageID()
    sendReq.MsgId = serverMsgID
    
    // 4. 发送到 im-business（新增）⭐ 对应 Java 的 c2CMsgProvider.sendC2CMsg()
    msgEvent := &httpclient.C2CMsgEvent{
        ClientMsgID:   string(sendReq.ClientMsgId),
        MsgID:         fmt.Sprintf("%d", serverMsgID),
        FromUserID:    fromUserID,
        ToUserID:      toUserID,
        ChatID:        fmt.Sprintf("%s_%s", fromUserID, toUserID), // 或使用工具生成
        MsgContent:    sendReq.Content,
        MsgFormat:     sendReq.Format,
        MsgCreateTime: time.Now().UnixMilli(),
    }
    
    // 异步发送（不阻塞主流程）
    go func() {
        if err := s.businessClient.SendC2CMsg(msgEvent); err != nil {
            s.logger.Error("发送消息到 im-business 失败",
                zap.String("msg_id", msgEvent.MsgID),
                zap.Error(err),
            )
        }
    }()
    
    // 5. 检查接收人是否在线
    if s.channelManager.IsUserOnline(toUserID) {
        // ... 推送逻辑 ...
    } else {
        // 离线：发送离线消息到 im-business
        go func() {
            if err := s.businessClient.SaveOfflineMsg(msgEvent); err != nil {
                s.logger.Error("保存离线消息失败", zap.Error(err))
            }
        }()
    }
    
    // ... 后续逻辑 ...
}
```

#### 3. 在 im-business（Java）添加 HTTP 接口

```java
// im-business-service/src/main/java/com/xzll/business/controller/MsgController.java
@RestController
@RequestMapping("/api/msg/c2c")
@Slf4j
public class MsgController {
    
    @Resource
    private C2CMsgService c2CMsgService;
    
    /**
     * 接收来自 im-connect-go 的消息（HTTP方式）
     */
    @PostMapping("/send")
    public WebBaseResponse receiveC2CMsg(@RequestBody C2CSendMsgAO msgAO) {
        log.info("收到 im-connect-go 发送的消息: msgId={}, from={}, to={}", 
            msgAO.getMsgId(), msgAO.getFromUserId(), msgAO.getToUserId());
        
        try {
            // 1. 保存消息到数据库
            c2CMsgService.saveMessage(msgAO);
            
            // 2. 更新会话记录
            c2CMsgService.updateSession(msgAO);
            
            // 3. 消息审核、敏感词过滤等
            c2CMsgService.auditMessage(msgAO);
            
            return WebBaseResponse.returnResultSuccess("消息已保存");
        } catch (Exception e) {
            log.error("保存消息失败", e);
            return WebBaseResponse.returnResultError("保存消息失败: " + e.getMessage());
        }
    }
    
    /**
     * 接收离线消息
     */
    @PostMapping("/offline")
    public WebBaseResponse receiveOfflineMsg(@RequestBody C2COffLineMsgAO msgAO) {
        log.info("收到 im-connect-go 发送的离线消息: msgId={}, toUserId={}", 
            msgAO.getMsgId(), msgAO.getToUserId());
        
        try {
            // 保存离线消息
            c2CMsgService.saveOfflineMessage(msgAO);
            
            return WebBaseResponse.returnResultSuccess("离线消息已保存");
        } catch (Exception e) {
            log.error("保存离线消息失败", e);
            return WebBaseResponse.returnResultError("保存离线消息失败: " + e.getMessage());
        }
    }
}
```

---

### 方案二：集成 RocketMQ（完整方案）

**优点**：
- 与 Java 版本架构一致
- 解耦，异步处理
- 支持消息重试、削峰填谷

**缺点**：
- 需要引入 RocketMQ Go 客户端
- 实现复杂度高

**实现步骤**：

#### 1. 安装依赖

```bash
go get github.com/apache/rocketmq-client-go/v2@latest
```

#### 2. 创建 RocketMQ 生产者

```go
// pkg/mq/producer.go
package mq

import (
    "context"
    "encoding/json"
    
    "github.com/apache/rocketmq-client-go/v2"
    "github.com/apache/rocketmq-client-go/v2/primitive"
    "github.com/apache/rocketmq-client-go/v2/producer"
    "go.uber.org/zap"
)

const (
    C2C_TOPIC = "XZLL_C2CMSG_TOPIC"  // 对应 Java 的 Topic
)

type Producer struct {
    producer rocketmq.Producer
    logger   *zap.Logger
}

func NewProducer(cfg *Config, logger *zap.Logger) (*Producer, error) {
    // 解析 NameServer 地址（支持分号分隔）
    nameServers := strings.Split(cfg.ServerAddr, ";")
    
    p, err := rocketmq.NewProducer(
        producer.WithGroupName(cfg.ProducerGroupName),
        producer.WithNameServer(nameServers),
        producer.WithRetry(cfg.RetryTimes),
        producer.WithSendMsgTimeout(time.Duration(cfg.SendTimeout) * time.Second),
    )
    
    if err != nil {
        return nil, fmt.Errorf("创建 RocketMQ Producer 失败: %w", err)
    }
    
    if err := p.Start(); err != nil {
        return nil, fmt.Errorf("启动 RocketMQ Producer 失败: %w", err)
    }
    
    logger.Info("✅ RocketMQ Producer 启动成功",
        zap.Strings("name_servers", nameServers),
        zap.String("group", cfg.ProducerGroupName),
    )
    
    return &Producer{
        producer: p,
        logger:   logger,
    }, nil
}

// ClusterEvent 集群事件（对应 Java 的 ClusterEvent）
type ClusterEvent struct {
    ClusterEventType string `json:"clusterEventType"`  // 事件类型
    Data             string `json:"data"`              // 事件数据（JSON字符串）
}

// SendC2CMsg 发送 C2C 消息事件
func (p *Producer) SendC2CMsg(msgData *C2CMsgEvent) error {
    // 1. 序列化消息数据
    dataJSON, err := json.Marshal(msgData)
    if err != nil {
        return fmt.Errorf("序列化消息失败: %w", err)
    }
    
    // 2. 构建 ClusterEvent
    clusterEvent := &ClusterEvent{
        ClusterEventType: "C2C_SEND_MSG",  // 对应 Java 的 ImConstant.ClusterEventTypeConstant.C2C_SEND_MSG
        Data:             string(dataJSON),
    }
    
    // 3. 序列化 ClusterEvent
    eventJSON, err := json.Marshal(clusterEvent)
    if err != nil {
        return fmt.Errorf("序列化 ClusterEvent 失败: %w", err)
    }
    
    // 4. 构建 RocketMQ 消息
    msg := &primitive.Message{
        Topic: C2C_TOPIC,
        Body:  eventJSON,
    }
    msg.WithTag("C2C_SEND")
    msg.WithKeys([]string{msgData.MsgID})
    
    // 5. 异步发送
    p.producer.SendAsync(context.Background(), 
        func(ctx context.Context, result *primitive.SendResult, err error) {
            if err != nil {
                p.logger.Error("消息投递 RocketMQ 失败",
                    zap.String("msg_id", msgData.MsgID),
                    zap.Error(err),
                )
            } else {
                p.logger.Info("消息投递 RocketMQ 成功",
                    zap.String("msg_id", msgData.MsgID),
                    zap.String("queue", result.MessageQueue.String()),
                    zap.String("offset", fmt.Sprintf("%d", result.QueueOffset)),
                )
            }
        }, 
        msg,
    )
    
    return nil
}

// SaveOfflineMsg 发送离线消息事件
func (p *Producer) SaveOfflineMsg(msgData *C2CMsgEvent) error {
    // 类似 SendC2CMsg，但 ClusterEventType 改为 "C2C_OFF_LINE_MSG"
    // ...
}

// Stop 停止生产者
func (p *Producer) Stop() error {
    return p.producer.Shutdown()
}
```

#### 3. 集成到消息发送策略

```go
// internal/strategy/c2c_send.go
type C2CMsgSendStrategy struct {
    config         *config.Config
    logger         *zap.Logger
    channelManager *channel.Manager
    mqProducer     *mq.Producer  // 新增
}

func (s *C2CMsgSendStrategy) Exchange(...) error {
    // ... 前面的代码 ...
    
    // 4. 发送到 RocketMQ（对应 Java 的 c2CMsgProvider.sendC2CMsg()）
    msgEvent := &mq.C2CMsgEvent{
        ClientMsgID:   string(sendReq.ClientMsgId),
        MsgID:         fmt.Sprintf("%d", serverMsgID),
        FromUserID:    fromUserID,
        ToUserID:      toUserID,
        ChatID:        fmt.Sprintf("%s_%s", fromUserID, toUserID),
        MsgContent:    sendReq.Content,
        MsgFormat:     sendReq.Format,
        MsgCreateTime: time.Now().UnixMilli(),
    }
    
    if err := s.mqProducer.SendC2CMsg(msgEvent); err != nil {
        s.logger.Error("发送消息到 RocketMQ 失败", zap.Error(err))
        // 不阻塞主流程
    }
    
    // ... 后续逻辑 ...
}
```

---

## 📋 配置文件更新

### 开发环境配置（nacos-dev-env.yaml）

```yaml
# ==================== im-business 配置（HTTP方案）====================
business:
  base_url: "http://localhost:8080"  # im-business 服务地址
  timeout: 10s

# ==================== RocketMQ 配置（RocketMQ方案）====================
rocketmq:
  server_addr: "192.168.1.100:9876"
  producer:
    group_name: "ImConnectGoProducerGroup"
    max_message_size: 4096
    send_timeout: 10
    retry_times: 3
```

---

## 🔄 消息重试机制完善

### 问题：Go 版本的重试只推送 WebSocket，不投递 MQ

```go
// internal/service/retry_service.go
func (s *C2CMsgRetryService) processRetryBatch(...) {
    for _, event := range events {
        // 检查用户是否在线
        if isOnline && conn != nil {
            // ✅ 在线：WebSocket 推送
            s.sendRetryMessage(ctx, conn, event)
        } else {
            // ❌ 离线：只打日志，没有投递 MQ
            s.markAsOffline(ctx, event)
        }
    }
}
```

### 修改方案

```go
// internal/service/retry_service.go
type C2CMsgRetryService struct {
    config         *C2CMsgRetryConfig
    redisClient    *redis.RedisClient
    channelManager *channel.Manager
    businessClient *httpclient.BusinessClient  // 新增（HTTP方案）
    // mqProducer     *mq.Producer               // 或使用 RocketMQ
    logger         *zap.Logger
    
    stopChan chan struct{}
    wg       sync.WaitGroup
}

func (s *C2CMsgRetryService) markAsOffline(ctx context.Context, event *C2CMsgRetryEvent) {
    // 从 Hash 索引删除
    if err := s.redisClient.HDel(ctx, C2CMsgRetryIndex, event.MsgID); err != nil {
        s.logger.Error("删除消息索引失败", zap.Error(err))
    }
    
    // ✅ 发送离线消息到 im-business（新增）
    msgEvent := &httpclient.C2CMsgEvent{
        ClientMsgID:   event.ClientMsgID,
        MsgID:         event.MsgID,
        FromUserID:    event.FromUserID,
        ToUserID:      event.ToUserID,
        ChatID:        event.ChatID,
        MsgContent:    event.MsgContent,
        MsgFormat:     event.MsgFormat,
        MsgCreateTime: event.MsgCreateTime,
    }
    
    go func() {
        if err := s.businessClient.SaveOfflineMsg(msgEvent); err != nil {
            s.logger.Error("保存离线消息失败",
                zap.String("msg_id", event.MsgID),
                zap.Error(err),
            )
        } else {
            s.logger.Info("离线消息已发送到 im-business",
                zap.String("msg_id", event.MsgID),
                zap.String("to_user_id", event.ToUserID),
            )
        }
    }()
}
```

---

## ✅ 实现清单

### 阶段1：最小可用（HTTP方案，1-2天）

- [ ] 创建 `pkg/httpclient/business_client.go`
- [ ] 修改 `internal/strategy/c2c_send.go`，添加 `businessClient`
- [ ] 修改 `internal/service/retry_service.go`，添加离线消息投递
- [ ] 在 Java 的 im-business 添加 HTTP 接口
- [ ] 测试消息发送、离线消息流程

### 阶段2：完整方案（RocketMQ，1周）

- [ ] 安装 RocketMQ Go 客户端
- [ ] 创建 `pkg/mq/producer.go`
- [ ] 集成 RocketMQ 到消息发送流程
- [ ] 测试 MQ 消息生产和消费

### 阶段3：测试和优化

- [ ] 单元测试
- [ ] 集成测试（与 Java im-business 联调）
- [ ] 性能测试
- [ ] 监控和日志优化

---

## 🎯 推荐实现顺序

```
1. HTTP 方案（快速实现，1-2天）
   ↓
2. 联调测试（与 Java im-business）
   ↓
3. 上线验证
   ↓
4. （可选）迁移到 RocketMQ 方案
```

**理由**：
- HTTP 方案实现简单，可以快速验证功能
- 可以复用现有的 Java im-business 服务
- 后续如有需要，再迁移到 RocketMQ（不影响业务）

---

## 📚 相关文件

- Java 版本参考：
  - `im-connect/im-connect-service/src/main/java/com/xzll/connect/strategy/impl/c2c/C2CMsgSendProtoStrategyImpl.java`
  - `im-connect/im-connect-service/src/main/java/com/xzll/connect/cluster/provider/C2CMsgProvider.java`

- Go 版本需要修改：
  - `im-connect-go/internal/strategy/c2c_send.go`
  - `im-connect-go/internal/service/retry_service.go`
  - `im-connect-go/pkg/httpclient/business_client.go`（新建）

---

## 🤔 常见问题

### Q: 为什么要发送到 im-business？
**A**: 因为 im-connect 只负责长连接和消息推送，而消息的持久化、审核、统计等业务逻辑由 im-business 处理，这是微服务职责分离的体现。

### Q: HTTP 方案性能够吗？
**A**: 对于中小规模（QPS < 10000）完全够用。如果需要支持更高并发，可以后续迁移到 RocketMQ。

### Q: 消息会丢吗？
**A**: 
- HTTP 方案：如果 im-business 挂了，消息会丢失（但可以通过重试机制降低风险）
- RocketMQ 方案：RocketMQ 保证消息可靠性，不会丢失

### Q: 需要改 Java 代码吗？
**A**: 是的，需要在 Java 的 im-business 中添加 HTTP 接口来接收 Go 版本发送的消息。

---

希望这个指南能帮助你快速补齐 im-connect-go 的缺失功能！🚀

