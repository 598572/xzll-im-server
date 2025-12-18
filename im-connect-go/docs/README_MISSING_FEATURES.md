# im-connect-go 缺失功能说明

## 🚨 核心问题

你的担心是**完全正确的**！im-connect-go 确实缺少了一些关键功能。

---

## ❌ 缺失功能清单

### 1. 消息持久化 ❌

**现状**：
```go
// internal/strategy/c2c_send.go:230-243
func (s *C2CMsgSendStrategy) saveMessage(...) error {
    // TODO: 实现数据库保存逻辑
    s.logger.Debug("保存 C2C 消息到数据库", ...)
    
    // 模拟数据库保存 ⚠️ 实际没有保存！
    return nil
}
```

**影响**：
- 消息没有保存到数据库
- 服务重启后消息丢失
- 无法查询历史消息

---

### 2. RocketMQ 投递 ❌（最关键！）

**Java 版本**：
```java
// C2CMsgSendProtoStrategyImpl.java:106
//1. 更新会话记录并保存消息记录
c2CMsgProvider.sendC2CMsg(packet);  // ⬅️ 发送到 RocketMQ
```

**Go 版本**：
```go
// internal/strategy/c2c_send.go
// ❌ 完全没有这一步！
```

**影响**：
- im-business 服务收不到消息
- 无法做消息持久化
- 无法做消息审核、敏感词过滤
- 无法做消息统计、分析
- **整个微服务架构断链**

---

### 3. 离线消息处理 ❌

**Java 版本**：
```java
// C2CMsgSendProtoStrategyImpl.java:134
c2CMsgProvider.offLineMsg(buildOffLineMsgDTO(packet));  // ⬅️ 保存离线消息
```

**Go 版本**：
```go
// internal/strategy/c2c_send.go:245-257
func (s *C2CMsgSendStrategy) saveOfflineMessage(...) error {
    // TODO: 实现离线消息保存逻辑
    s.logger.Info("保存离线消息", ...)
    
    // 模拟离线消息保存 ⚠️ 实际没有保存！
    return nil
}
```

**影响**：
- 用户离线期间的消息无法保存
- 用户上线后收不到离线消息

---

### 4. 消息重投递不完整 ⚠️

**Go 版本有重试机制，但只推送 WebSocket**：
```go
// internal/service/retry_service.go:306-312
if isOnline && conn != nil {
    // ✅ 发送重试消息（WebSocket）
    s.sendRetryMessage(ctx, conn, event)
} else {
    // ❌ 离线时只打日志，没有投递到 MQ 或保存到数据库
    s.markAsOffline(ctx, event)
}
```

**Java 版本会重新投递到 MQ**。

---

## 📊 对比表

| 功能 | Java im-connect | Go im-connect-go | 影响 |
|-----|----------------|------------------|------|
| **接收消息** | ✅ | ✅ | - |
| **消息路由** | ✅ | ✅ | - |
| **投递 RocketMQ** | ✅ | ❌ | 🔴 **严重** |
| **消息持久化** | ✅ (通过 MQ→im-business) | ❌ | 🔴 **严重** |
| **离线消息** | ✅ | ❌ | 🟡 **中等** |
| **WebSocket 推送** | ✅ | ✅ | - |
| **消息重试** | ✅ (推送 + MQ) | ⚠️ (只推送) | 🟡 **中等** |
| **跨服务器转发** | ✅ | ⚠️ (部分) | 🟡 **中等** |

---

## 🎯 Java 版本的完整流程

```
客户端发送消息
    ↓
im-connect 接收
    ↓
【1. 投递到 RocketMQ】 ✅ Topic: XZLL_C2CMSG_TOPIC
    ↓
im-business 消费 MQ
    ├─ 保存消息到 MySQL ✅
    ├─ 更新会话记录 ✅
    ├─ 消息审核、敏感词过滤 ✅
    └─ 消息统计、分析 ✅
    
im-connect 继续处理
    ↓
【2. 检查接收人在线状态】
    ├─ 在线 → WebSocket 推送 ✅
    ├─ 离线 → 保存离线消息（通过 MQ）✅
    └─ 跨服务器 → gRPC 转发 ✅
    
【3. 添加到重试队列（Redis ZSet）】✅
    ↓
等待客户端 ACK
    ├─ 收到 ACK → 删除重试任务 ✅
    └─ 超时 → 重新投递 MQ ✅
```

---

## 🚫 Go 版本的当前流程（有问题）

```
客户端发送消息
    ↓
im-connect-go 接收
    ↓
【1. 投递到 RocketMQ】 ❌ 完全缺失！
    ↓
❌ im-business 收不到消息
❌ 无法保存到 MySQL
❌ 无法做消息审核
❌ 无法做统计分析
    
im-connect-go 继续处理
    ↓
【2. 检查接收人在线状态】
    ├─ 在线 → WebSocket 推送 ✅
    ├─ 离线 → ❌ TODO（只打日志）
    └─ 跨服务器 → ⚠️ 部分实现
    
【3. 添加到重试队列（Redis ZSet）】⚠️ 未集成
    ↓
等待客户端 ACK
    ├─ 收到 ACK → ⚠️ 未集成
    └─ 超时 → ⚠️ 只推送 WebSocket，不投递 MQ
```

---

## ✅ 快速解决方案

### 推荐方案：HTTP 调用 im-business（最简单）

**为什么推荐？**
- 实现简单，1-2天即可完成
- 不需要引入 RocketMQ 依赖
- 可以复用现有的 Java im-business 服务

**实现步骤**：

#### 1. 创建 HTTP 客户端（Go）

```go
// pkg/httpclient/business_client.go
type BusinessClient struct {
    baseURL    string
    httpClient *http.Client
    logger     *zap.Logger
}

// 发送消息到 im-business
func (c *BusinessClient) SendC2CMsg(event *C2CMsgEvent) error {
    url := fmt.Sprintf("%s/api/msg/c2c/send", c.baseURL)
    
    data, _ := json.Marshal(event)
    req, _ := http.NewRequest("POST", url, bytes.NewBuffer(data))
    req.Header.Set("Content-Type", "application/json")
    
    resp, err := c.httpClient.Do(req)
    // ... 处理响应 ...
    
    return err
}
```

#### 2. 集成到消息发送（Go）

```go
// internal/strategy/c2c_send.go
func (s *C2CMsgSendStrategy) Exchange(...) error {
    // ... 解析消息 ...
    
    // ⭐ 新增：发送到 im-business（对应 Java 的 c2CMsgProvider.sendC2CMsg()）
    msgEvent := &httpclient.C2CMsgEvent{
        MsgID:      fmt.Sprintf("%d", serverMsgID),
        FromUserID: fromUserID,
        ToUserID:   toUserID,
        MsgContent: sendReq.Content,
        // ...
    }
    
    go func() {
        if err := s.businessClient.SendC2CMsg(msgEvent); err != nil {
            s.logger.Error("发送到 im-business 失败", zap.Error(err))
        }
    }()
    
    // ... 后续逻辑 ...
}
```

#### 3. 添加 HTTP 接口（Java im-business）

```java
// im-business-service/.../MsgController.java
@RestController
@RequestMapping("/api/msg/c2c")
public class MsgController {
    
    @PostMapping("/send")
    public WebBaseResponse receiveC2CMsg(@RequestBody C2CSendMsgAO msgAO) {
        // 1. 保存消息到数据库
        c2CMsgService.saveMessage(msgAO);
        
        // 2. 更新会话记录
        c2CMsgService.updateSession(msgAO);
        
        return WebBaseResponse.returnResultSuccess("消息已保存");
    }
    
    @PostMapping("/offline")
    public WebBaseResponse receiveOfflineMsg(@RequestBody C2COffLineMsgAO msgAO) {
        c2CMsgService.saveOfflineMessage(msgAO);
        return WebBaseResponse.returnResultSuccess("离线消息已保存");
    }
}
```

---

## 📋 实现清单

### 🔴 紧急（必须实现）

- [ ] **消息投递到 im-business**（HTTP 或 RocketMQ）
  - 这是最关键的功能，直接影响系统可用性

### 🟡 重要（建议实现）

- [ ] 离线消息处理
- [ ] 完善消息重试机制
- [ ] 用户上线时推送离线消息

### 🟢 优化（后续实现）

- [ ] 消息持久化（可以通过 im-business 实现）
- [ ] 消息审核、敏感词过滤
- [ ] 消息统计、分析

---

## 📚 详细文档

- [功能差异详细分析](./FEATURE_GAP_ANALYSIS.md) - 完整的功能对比
- [实现指南](./IMPLEMENTATION_GUIDE.md) - 手把手实现教程
- [Java 到 Go 配置映射](./JAVA_TO_GO_CONFIG_MAPPING.md) - 配置对照表

---

## 🤝 需要帮助？

如果你需要：
1. 实现代码示例
2. 架构设计建议
3. 问题排查帮助

可以随时提问！

