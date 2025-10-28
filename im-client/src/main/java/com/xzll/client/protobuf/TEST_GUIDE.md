# Protobuf 客户端测试指南

## 测试架构

### 消息判断机制
客户端通过 `ImProtoResponse.type` 字段判断消息类型：

```java
MsgType msgType = protoResponse.getType();

switch (msgType) {
    case C2C_MSG_PUSH:      // 单聊消息推送（C2CMsgPush）
    case C2C_ACK:           // ACK推送（status: 1=服务器已接收, 3=对方未读, 4=对方已读）
    case C2C_WITHDRAW:      // 撤回通知（C2CWithdrawReq）
    case PUSH_BATCH_MSG_IDS:// 批量消息ID（BatchMsgIdsPush）
}
```

### 两个测试客户端

#### User1 (发送方 - 需手动交互)
- **包路径**: `com.xzll.client.protobuf.clien1`
- **UID**: `111`
- **功能**:
  - 发送单聊消息到 User2
  - 接收并打印服务端 ACK (status=1)
  - 接收并打印 User2 的未读/已读 ACK (status=3/4)
  - 支持命令行撤回消息：`withdraw <msgId>`
  - **不会自动发送 ACK**（需要手动测试）

#### User2 (接收方 - 自动响应)
- **包路径**: `com.xzll.client.protobuf.clien2`
- **UID**: `222`
- **功能**:
  - 自动接收 User1 的消息
  - 自动发送未读 ACK (status=3)
  - 延迟 500ms 后自动发送已读 ACK (status=4)
  - 自动打印撤回通知

---

## 测试步骤

### 1. 启动服务端
确保以下服务已启动：
- Nacos
- MySQL
- Redis
- HBase
- RocketMQ
- `im-auth`
- `im-business`
- `im-connect`
- `im-gateway`

### 2. 启动 User2（接收方）
```bash
# 运行 ProtobufClient_2.java
# User2 会自动连接并等待消息
```

**预期输出**：
```
[user2] 连接建立, uid: 222
[user2] WebSocket connected!
```

### 3. 启动 User1（发送方）
```bash
# 运行 ProtobufClient_1.java
# 根据提示获取消息ID并发送消息
```

### 4. 测试场景

#### 场景 1: 发送单聊消息
**User1 操作**：
1. 启动后等待提示：`是否获取msgId[y/n]:`
2. 输入 `y` 获取消息ID
3. 输入消息内容，例如：`你好，User2！`

**User1 预期输出**：
```
✓ 发送消息成功, msgId=<雪花ID>, to=222
★★★ [收到ACK] msgId=<雪花ID>, status=服务器已接收 ★★★
```

**User2 预期输出**：
```
============================================
[user2] 收到单聊消息推送:
  from=111
  msgId=<雪花ID>
  content=你好，User2！
============================================
[user2] 发送ACK完成: 未读, msgId=<雪花ID>
[user2] 发送ACK完成: 已读, msgId=<雪花ID>
```

**User1 随后收到**：
```
★★★ [收到ACK] msgId=<雪花ID>, status=对方未读 ★★★
★★★ [收到ACK] msgId=<雪花ID>, status=对方已读 ★★★
```

---

#### 场景 2: 撤回消息
**User1 操作**：
1. 发送一条消息（参考场景1）
2. 记录消息ID（例如：`7318732866797568`）
3. 输入命令：`withdraw 7318732866797568`

**User1 预期输出**：
```
✓ 撤回消息发送成功, msgId=7318732866797568
```

**User2 预期输出**：
```
★★★ [user2] 收到撤回通知, msgId=7318732866797568, from=111 ★★★
```

---

#### 场景 3: 连续发送多条消息
**User1 操作**：
连续发送多条消息，观察 ACK 的顺序和状态

**验证要点**：
- 每条消息都应收到服务端 ACK (status=1)
- 每条消息都应收到对方未读 ACK (status=3)
- 每条消息都应收到对方已读 ACK (status=4)
- ACK 的 msgId 应与发送的消息 ID 一一对应

---

## 关键消息类型说明

### 上行消息（客户端→服务端）
| 类型 | Protobuf 类 | 说明 |
|-----|------------|------|
| `C2C_SEND` | `C2CSendReq` | 发送单聊消息 |
| `C2C_ACK` | `C2CAckReq` | 客户端回执（未读/已读） |
| `C2C_WITHDRAW` | `C2CWithdrawReq` | 撤回消息请求 |
| `GET_BATCH_MSG_IDS` | `GetBatchMsgIdsReq` | 批量获取消息ID |

### 下行消息（服务端→客户端）
| 类型 | Protobuf 类 | 说明 |
|-----|------------|------|
| `C2C_MSG_PUSH` | `C2CMsgPush` | 推送单聊消息 |
| `C2C_ACK` | `C2CAckReq` | 推送ACK回执（复用上行结构） |
| `C2C_WITHDRAW` | `C2CWithdrawReq` | 推送撤回通知（复用上行结构） |
| `PUSH_BATCH_MSG_IDS` | `BatchMsgIdsPush` | 推送批量消息ID |

### ACK Status 状态码
| 状态码 | 含义 | 场景 |
|-------|------|------|
| 1 | SERVER_RECEIVED | 服务端已接收消息 |
| 3 | UN_READ | 接收方未读 |
| 4 | READED | 接收方已读 |

---

## 常见问题

### Q1: User1 没有收到 ACK？
**检查**：
- 确认 User2 已启动并连接成功
- 检查 `im-business` 日志，查看 ACK 是否发送
- 检查 `im-connect` 日志，查看 gRPC 调用是否成功

### Q2: User2 没有收到消息？
**检查**：
- 确认 User2 的 channel 已注册到 `LocalChannelManager`
- 检查 `C2CMsgSendProtoStrategyImpl` 是否正确推送
- 查看服务端日志是否有异常

### Q3: 撤回消息失败？
**检查**：
- 确认消息ID存在且属于发送方
- 检查消息是否在撤回时间窗口内（通常2分钟）
- 查看 `C2CClientWithdrawMsgHandler` 日志

### Q4: 编译错误找不到 `C2CMsgPush`？
**解决**：
```bash
cd im-common
rm -rf target
mvn protobuf:compile protobuf:compile-custom
mvn compile
```

---

## 代码示例

### 发送单聊消息
```java
C2CSendReq sendReq = C2CSendReq.newBuilder()
    .setMsgId(msgId)
    .setFrom("111")
    .setTo("222")
    .setFormat(1)  // 1=文本
    .setContent("Hello")
    .setTime(System.currentTimeMillis())
    .setChatId("c2c_111_222")
    .build();

ImProtoRequest request = ImProtoRequest.newBuilder()
    .setType(MsgType.C2C_SEND)
    .setPayload(ByteString.copyFrom(sendReq.toByteArray()))
    .build();

channel.writeAndFlush(new BinaryWebSocketFrame(
    Unpooled.wrappedBuffer(request.toByteArray())
));
```

### 发送 ACK
```java
C2CAckReq ackReq = C2CAckReq.newBuilder()
    .setMsgId(msgId)
    .setFrom("222")
    .setTo("111")
    .setStatus(3)  // 3=未读, 4=已读
    .setChatId("c2c_111_222")
    .build();

ImProtoRequest request = ImProtoRequest.newBuilder()
    .setType(MsgType.C2C_ACK)
    .setPayload(ByteString.copyFrom(ackReq.toByteArray()))
    .build();

channel.writeAndFlush(new BinaryWebSocketFrame(
    Unpooled.wrappedBuffer(request.toByteArray())
));
```

### 撤回消息
```java
C2CWithdrawReq withdrawReq = C2CWithdrawReq.newBuilder()
    .setMsgId(msgId)
    .setFrom("111")
    .setTo("222")
    .setChatId("c2c_111_222")
    .build();

ImProtoRequest request = ImProtoRequest.newBuilder()
    .setType(MsgType.C2C_WITHDRAW)
    .setPayload(ByteString.copyFrom(withdrawReq.toByteArray()))
    .build();

channel.writeAndFlush(new BinaryWebSocketFrame(
    Unpooled.wrappedBuffer(request.toByteArray())
));
```

---

## 测试验收标准

✅ **必须通过的测试**：
1. User1 发送消息 → User2 收到消息
2. User1 收到服务端 ACK (status=1)
3. User1 收到 User2 未读 ACK (status=3)
4. User1 收到 User2 已读 ACK (status=4)
5. User1 撤回消息 → User2 收到撤回通知
6. 所有消息均使用 Protobuf 二进制格式
7. 客户端正确解析 `ImProtoResponse.type` 判断消息类型
8. ACK 的 msgId 与原消息 ID 一致

---

## 架构优势总结

✅ **类型安全**: `XxxReq` (上行) / `XxxPush` (下行) 命名清晰  
✅ **协议统一**: 全程 Protobuf，无 JSON 混用  
✅ **路由简洁**: 通过 `MsgType` 枚举路由，无需 URL  
✅ **高性能**: 二进制序列化，体积小、速度快  
✅ **易维护**: proto 文件即文档，前后端契约清晰  

---

**测试完成后，请在本文件末尾记录测试结果！** ✍️

