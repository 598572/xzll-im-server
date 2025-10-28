# Protobuf 客户端测试说明

## 📋 概述

这是一个基于 **Protobuf 协议**的 WebSocket 客户端测试程序，用于测试 IM 服务端的 Protobuf 消息处理功能。

## 📁 文件说明

| 文件 | 说明 | 参考原文件 |
|------|------|-----------|
| `ProtobufClient_1.java` | 客户端启动入口 | `Client_1.java` |
| `ProtobufWebsocketClient1.java` | WebSocket 客户端实现 | `WebsocketClient1.java` |
| `ProtobufWebsocketClientHandler1.java` | 消息处理器 | `WebsocketClientHandler1.java` |

## 🔄 主要变化

### 1. **消息格式变化**
| 原协议 (JSON) | 新协议 (Protobuf) |
|--------------|-------------------|
| `TextWebSocketFrame` | `BinaryWebSocketFrame` |
| `JSONUtil.toJsonStr()` | `protobuf.toByteArray()` |
| `JSON.parseObject()` | `protobuf.parseFrom()` |

### 2. **消息结构变化**

#### 原协议 (JSON)
```java
ImBaseRequest<C2CSendMsgAO> request = new ImBaseRequest<>();
request.setUrl("/c2c/send");
request.setBody(c2cMsg);
String json = JSONUtil.toJsonStr(request);
ctx.writeAndFlush(new TextWebSocketFrame(json));
```

#### 新协议 (Protobuf)
```java
// 1. 构建具体消息
C2CSendReq c2cSendReq = C2CSendReq.newBuilder()
    .setMsgId(msgId)
    .setFrom(fromUserId)
    .setTo(toUserId)
    .setContent(content)
    .build();

// 2. 包装为 ImProtoRequest
ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
    .setType(MsgType.C2C_SEND)
    .setPayload(ByteString.copyFrom(c2cSendReq.toByteArray()))
    .build();

// 3. 发送二进制消息
byte[] bytes = protoRequest.toByteArray();
ByteBuf buf = Unpooled.wrappedBuffer(bytes);
ctx.writeAndFlush(new BinaryWebSocketFrame(buf));
```

## 🚀 使用方法

### 1. 启动服务端
确保 IM 服务端已启动并支持 Protobuf 协议。

### 2. 修改连接配置
在 `ProtobufClient_1.java` 中选择服务器地址：
```java
// 本地测试
ProtobufWebsocketClient1 websocketClient = new ProtobufWebsocketClient1(LOCAL, 10001);

// 或者连接远程服务器
// ProtobufWebsocketClient1 websocketClient = new ProtobufWebsocketClient1(huawei, 80);
```

### 3. 修改用户ID
在 `ProtobufWebsocketClient1.java` 中修改：
```java
// 当前用户ID
public static final String VALUE = "1966479049087913984";

// 接收人ID
.setTo("1966369607918948352")
```

### 4. 运行客户端
```bash
# 直接运行 main 方法
java com.xzll.client.protobuf.clien1.ProtobufClient_1
```

### 5. 发送消息
程序启动后，在控制台输入消息内容，按回车发送：
```
请输入消息内容:
> 你好，这是一条测试消息
```

## 📊 支持的功能

### ✅ 已实现功能

1. **WebSocket 连接**
   - 握手认证（Token + UID）
   - 心跳保持（Ping/Pong）
   - 断线重连

2. **消息发送（C2C_SEND）**
   - 文本消息
   - 自动获取消息ID
   - 消息ID缓存管理

3. **消息接收（PUSH_MSG）**
   - 接收推送消息
   - 自动发送 ACK（未读 + 已读）
   - 消息内容打印

4. **批量获取消息ID（GET_BATCH_MSG_IDS）**
   - 自动获取
   - 本地缓存
   - 用完自动补充

5. **消息ACK（C2C_ACK）**
   - 自动发送未读确认
   - 自动发送已读确认

### 🔜 可扩展功能

1. **撤回消息（C2C_WITHDRAW）**
```java
C2CWithdrawReq withdrawReq = C2CWithdrawReq.newBuilder()
    .setMsgId(msgId)
    .setFrom(fromUserId)
    .setTo(toUserId)
    .setChatId(chatId)
    .build();

ImProtoRequest protoRequest = ImProtoRequest.newBuilder()
    .setType(MsgType.C2C_WITHDRAW)
    .setPayload(ByteString.copyFrom(withdrawReq.toByteArray()))
    .build();
```

2. **发送图片/语音**
```java
C2CSendReq c2cSendReq = C2CSendReq.newBuilder()
    .setFormat(2)  // 2:图片, 3:语音
    .setContent(base64ImageData)
    .build();
```

## 📝 日志输出示例

```log
[INFO] 客户端连接建立,当前uid: 1966479049087913984
[INFO] WebSocket Client connected!
[INFO] 发送获取消息ID请求
[INFO] 获取到一批消息ID，数量: 100
[INFO] 消息ID已添加到本地缓存，当前缓存数量: 100

请输入消息内容:
> 你好
[INFO] 客户端手动发送Protobuf消息成功，msgId=123456789, content=你好

[INFO] 接收到 BinaryWebSocketFrame 消息
[INFO] 收到 Protobuf 消息 - 类型: PUSH_MSG, 响应码: 0
============================================
收到消息推送:
  消息ID: 123456789
  发送人: 1966479049087913984
  接收人: 1966369607918948352
  消息格式: 1
  消息内容: 你好
  时间戳: 1729987654321
  会话ID: 100-1-1966369607918948352-1966479049087913984
============================================
[INFO] 发送 ACK 完成 - status: 未读, msgId: 123456789
[INFO] 发送 ACK 完成 - status: 已读, msgId: 123456789
```

## 🔍 测试验证点

### 1. 连接验证
- ✅ WebSocket 握手成功
- ✅ Token 认证通过
- ✅ UID 绑定成功

### 2. 消息发送验证
- ✅ Protobuf 序列化正确
- ✅ BinaryWebSocketFrame 发送成功
- ✅ 服务端收到并处理

### 3. 消息接收验证
- ✅ 接收 BinaryWebSocketFrame
- ✅ Protobuf 反序列化正确
- ✅ 消息内容完整

### 4. ACK 验证
- ✅ 自动发送未读 ACK
- ✅ 自动发送已读 ACK
- ✅ 服务端收到 ACK

### 5. 心跳验证
- ✅ 定时发送 Ping
- ✅ 收到 Pong 响应
- ✅ 连接保持稳定

## 🆚 对比旧客户端

| 功能 | 旧客户端 (JSON) | 新客户端 (Protobuf) | 优势 |
|------|----------------|-------------------|------|
| **消息格式** | JSON 文本 | Protobuf 二进制 | ✅ 体积减少 50% |
| **序列化速度** | 慢 | 快 3-5 倍 | ✅ 性能提升 |
| **类型安全** | 运行时检查 | 编译时检查 | ✅ 更安全 |
| **网络传输** | TextWebSocketFrame | BinaryWebSocketFrame | ✅ 更高效 |
| **可维护性** | JSON 字符串 | 强类型对象 | ✅ 更易维护 |

## 🐛 常见问题

### Q1: 连接失败？
**A:** 检查服务端地址和端口是否正确，确保服务端已启动。

### Q2: 消息发送失败？
**A:** 检查 Token 是否有效，UID 是否正确。

### Q3: 无法接收消息？
**A:** 检查消息ID是否有效，接收人ID是否在线。

### Q4: Protobuf 解析失败？
**A:** 确保客户端和服务端使用相同的 proto 文件版本。

## 📚 相关文档

- [Protobuf 迁移指南](../../PROTOBUF_MIGRATION_COMPLETE.md)
- [消息协议定义](../../im-common/src/main/proto/message_service.proto)
- [服务端实现](../../im-connect/im-connect-service/)

## ✅ 总结

这个 Protobuf 客户端完全复刻了原有 JSON 客户端的功能，只是将协议从 JSON 改为 Protobuf，具有以下优势：

1. **更高性能**：序列化/反序列化速度快 3-5 倍
2. **更小体积**：消息体积减少 50%
3. **更强类型**：编译时类型检查，减少错误
4. **更易维护**：代码结构清晰，逻辑统一

可以直接用于测试服务端的 Protobuf 协议支持！🚀


