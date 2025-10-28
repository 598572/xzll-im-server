# Protobuf 客户端测试包

## 📦 包结构

```
com.xzll.client.protobuf/
├── clien1/                          # User1（发送方）
│   ├── ProtobufClient_1.java       # 主程序
│   ├── ProtobufWebsocketClient1.java    # WebSocket客户端
│   └── ProtobufWebsocketClientHandler1.java  # 消息处理器
├── clien2/                          # User2（接收方）
│   ├── ProtobufClient_2.java       # 主程序
│   ├── ProtobufWebsocketClient2.java    # WebSocket客户端
│   └── ProtobufWebsocketClientHandler2.java  # 消息处理器
└── TEST_GUIDE.md                    # 详细测试指南
```

## 🚀 快速开始

### 1. 启动服务端（必须）
确保以下服务已启动：
- ✅ Nacos
- ✅ MySQL 
- ✅ Redis
- ✅ HBase
- ✅ RocketMQ
- ✅ im-auth
- ✅ im-business
- ✅ im-connect  
- ✅ im-gateway

### 2. 启动接收方（User2）
```bash
# 运行 com.xzll.client.protobuf.clien2.ProtobufClient_2
# UID: 222
```

### 3. 启动发送方（User1）
```bash
# 运行 com.xzll.client.protobuf.clien1.ProtobufClient_1  
# UID: 111
```

### 4. 测试流程

#### 📨 发送消息
```
User1 控制台:
是否获取msgId[y/n]: y
请输入消息内容: 你好，User2！

User1 输出:
✓ 发送消息成功, msgId=7318732866797568, to=222
★★★ [收到ACK] msgId=7318732866797568, status=服务器已接收 ★★★
★★★ [收到ACK] msgId=7318732866797568, status=对方未读 ★★★
★★★ [收到ACK] msgId=7318732866797568, status=对方已读 ★★★

User2 输出:
============================================
[user2] 收到单聊消息推送:
  from=111
  msgId=7318732866797568
  content=你好，User2！
============================================
[user2] 发送ACK完成: 未读, msgId=7318732866797568
[user2] 发送ACK完成: 已读, msgId=7318732866797568
```

#### 🔙 撤回消息
```
User1 控制台:
withdraw 7318732866797568

User1 输出:
✓ 撤回消息发送成功, msgId=7318732866797568

User2 输出:
★★★ [user2] 收到撤回通知, msgId=7318732866797568, from=111 ★★★
```

## 🔍 消息判断机制

### 服务端推送的消息格式
```protobuf
ImProtoResponse {
  MsgType type;      // 消息类型（用于判断）
  bytes payload;     // 具体消息体
  int32 code;        // 响应码
  string msg;        // 错误信息
}
```

### 客户端判断逻辑
```java
ImProtoResponse protoResponse = ImProtoResponse.parseFrom(bytes);
MsgType msgType = protoResponse.getType();

switch (msgType) {
    case C2C_MSG_PUSH:
        // 解析为 C2CMsgPush - 单聊消息
        C2CMsgPush pushMsg = C2CMsgPush.parseFrom(protoResponse.getPayload());
        break;
        
    case C2C_ACK:
        // 解析为 C2CAckReq - ACK回执
        C2CAckReq ack = C2CAckReq.parseFrom(protoResponse.getPayload());
        // status: 1=服务器已接收, 3=对方未读, 4=对方已读
        break;
        
    case C2C_WITHDRAW:
        // 解析为 C2CWithdrawReq - 撤回通知
        C2CWithdrawReq withdraw = C2CWithdrawReq.parseFrom(protoResponse.getPayload());
        break;
        
    case PUSH_BATCH_MSG_IDS:
        // 解析为 BatchMsgIdsPush - 批量消息ID
        BatchMsgIdsPush batchIds = BatchMsgIdsPush.parseFrom(protoResponse.getPayload());
        break;
}
```

## 📋 支持的消息类型

### 上行（客户端→服务端）
| MsgType | Protobuf 类 | 说明 |
|---------|------------|------|
| `C2C_SEND` | `C2CSendReq` | 发送单聊消息 |
| `C2C_ACK` | `C2CAckReq` | 回复ACK（未读/已读） |
| `C2C_WITHDRAW` | `C2CWithdrawReq` | 撤回消息 |
| `GET_BATCH_MSG_IDS` | `GetBatchMsgIdsReq` | 获取消息ID |

### 下行（服务端→客户端）
| MsgType | Protobuf 类 | 说明 |
|---------|------------|------|
| `C2C_MSG_PUSH` | `C2CMsgPush` | 推送单聊消息 |
| `C2C_ACK` | `C2CAckReq` | 推送ACK（复用上行结构） |
| `C2C_WITHDRAW` | `C2CWithdrawReq` | 推送撤回通知（复用上行结构） |
| `PUSH_BATCH_MSG_IDS` | `BatchMsgIdsPush` | 推送批量消息ID |

## 🎯 测试验收标准

- ✅ User1 发送消息，User2 能收到
- ✅ User1 收到服务端 ACK（status=1）
- ✅ User1 收到 User2 未读 ACK（status=3）
- ✅ User1 收到 User2 已读 ACK（status=4）
- ✅ User1 撤回消息，User2 收到撤回通知
- ✅ 所有消息均为 Protobuf 二进制格式（BinaryWebSocketFrame）
- ✅ 客户端通过 `ImProtoResponse.type` 正确判断消息类型
- ✅ 无任何 JSON 协议混用

## 📚 详细文档

请查看 [TEST_GUIDE.md](./TEST_GUIDE.md) 获取：
- 详细测试步骤
- 代码示例
- 常见问题排查
- 架构设计说明

## ⚠️ 注意事项

1. **编译顺序**：必须先编译 `im-common` 才能编译 `im-client`
   ```bash
   cd im-common
   mvn clean install
   cd ../im-client
   mvn compile
   ```

2. **User1 vs User2**：
   - User1：手动交互，用于测试发送和查看 ACK
   - User2：自动响应，模拟真实接收方行为

3. **撤回时间窗口**：消息撤回通常有时间限制（如2分钟），超时无法撤回

4. **消息ID**：必须使用服务端生成的雪花ID，不能自己编造

5. **会话ID格式**：`c2c_{smallerId}_{biggerId}`，例如 `c2c_111_222`

---

**有问题请查看 [TEST_GUIDE.md](./TEST_GUIDE.md)** 📖

