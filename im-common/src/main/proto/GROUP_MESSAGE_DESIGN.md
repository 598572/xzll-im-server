# 群聊消息协议设计文档

## 📋 设计概述

本文档描述了群聊消息的 Protobuf 协议设计，目前已在 `message_service.proto` 中预留定义，**暂未实现业务逻辑**。

### 设计原则

1. **类型分离**: 单聊和群聊使用不同的 `MsgType`，便于客户端快速判断
2. **语义清晰**: 上行使用 `XxxReq`，下行使用 `XxxPush`
3. **信息完整**: 群聊消息包含发送人昵称、头像等展示信息
4. **可扩展**: 预留字段便于后续功能扩展

---

## 🎯 消息类型定义

### MsgType 枚举

```protobuf
enum MsgType {
  // ========== 单聊相关 ==========
  C2C_SEND = 1;              // C2C发送消息（上行）
  C2C_ACK = 2;               // C2C消息确认（上行/下行）
  C2C_WITHDRAW = 3;          // C2C撤回消息（上行/下行）
  C2C_PUSH_MSG = 5;              // 服务端推送单聊消息（下行）
  
  // ========== 群聊相关（预留，暂未实现） ==========
  GROUP_SEND = 7;            // 群聊发送消息（上行）
  GROUP_MSG_PUSH = 8;        // 服务端推送群聊消息（下行）
  GROUP_ACK = 9;             // 群聊消息确认（上行）
  GROUP_WITHDRAW = 10;       // 群聊撤回消息（上行/下行）
}
```

### 为什么单聊和群聊要分开？

| 对比项 | 单聊 (C2C_PUSH_MSG) | 群聊 (GROUP_MSG_PUSH) |
|-------|----------------|----------------------|
| **接收方** | 1个人 | 多个人 |
| **显示信息** | 仅需对方ID | 需要发送人昵称、头像、群名 |
| **ACK机制** | 简单（未读/已读） | 复杂（多人已读状态） |
| **撤回权限** | 仅发送者 | 发送者 + 群管理员 |
| **客户端处理** | 更新单聊会话 | 更新群聊会话，需展示发送人信息 |

**结论**: 使用独立的 `MsgType` 可以让客户端一眼就知道是单聊还是群聊，无需二次解析。

---

## 📦 群聊消息定义

### 1. 发送群聊消息（上行）

```protobuf
message GroupSendReq {
  string msgId = 1;           // 消息ID（雪花算法生成）
  string from = 2;            // 发送人ID
  string groupId = 3;         // 群ID
  int32 format = 4;           // 消息格式（1:文本,2:图片,3:语音等）
  string content = 5;         // 消息内容
  int64 time = 6;             // 客户端时间戳（毫秒）
}
```

**客户端发送示例**：
```java
GroupSendReq sendReq = GroupSendReq.newBuilder()
    .setMsgId("7318732866797568")
    .setFrom("111")
    .setGroupId("group_1001")
    .setFormat(1)
    .setContent("大家好！")
    .setTime(System.currentTimeMillis())
    .build();

ImProtoRequest request = ImProtoRequest.newBuilder()
    .setType(MsgType.GROUP_SEND)
    .setPayload(ByteString.copyFrom(sendReq.toByteArray()))
    .build();

channel.writeAndFlush(new BinaryWebSocketFrame(
    Unpooled.wrappedBuffer(request.toByteArray())
));
```

---

### 2. 推送群聊消息（下行）

```protobuf
message GroupMsgPush {
  string msgId = 1;           // 消息ID
  string from = 2;            // 发送人ID
  string fromNickname = 3;    // 发送人昵称（群聊显示用）
  string fromAvatar = 4;      // 发送人头像（群聊显示用）
  string groupId = 5;         // 群ID
  string groupName = 6;       // 群名称
  int32 format = 7;           // 消息格式
  string content = 8;         // 消息内容
  int64 time = 9;             // 服务器时间戳（毫秒）
  int32 memberCount = 10;     // 群成员数（可选）
}
```

**为什么要包含昵称和头像？**
- 群聊中，客户端需要显示每条消息的发送人信息
- 如果只传 `userId`，客户端需要额外查询用户信息
- 直接携带昵称和头像，减少客户端请求，提升性能

**客户端接收示例**：
```java
switch (protoResponse.getType()) {
    case GROUP_MSG_PUSH:
        GroupMsgPush groupMsg = GroupMsgPush.parseFrom(protoResponse.getPayload());
        System.out.println("【群消息】");
        System.out.println("  群名: " + groupMsg.getGroupName());
        System.out.println("  发送人: " + groupMsg.getFromNickname());
        System.out.println("  内容: " + groupMsg.getContent());
        break;
}
```

---

### 3. 群聊消息确认（上行）

```protobuf
message GroupAckReq {
  string msgId = 1;           // 消息ID
  string userId = 2;          // 确认人ID
  string groupId = 3;         // 群ID
  int32 status = 4;           // 消息状态（3:未读，4:已读）
}
```

**群聊 ACK 与单聊 ACK 的区别**：
- **单聊**: 只需通知发送方"对方已读"
- **群聊**: 需要记录哪些人已读（类似微信群聊的"已读"功能）

**未来扩展方向**：
- 可以添加 `GROUP_ACK_PUSH` 类型，推送已读人数给发送方
- 例如："张三、李四已读（2/10人）"

---

### 4. 群聊撤回消息（上行）

```protobuf
message GroupWithdrawReq {
  string msgId = 1;           // 要撤回的消息ID
  string from = 2;            // 发送人ID（或管理员ID）
  string groupId = 3;         // 群ID
  int64 withdrawTime = 4;     // 撤回时间戳
}
```

---

### 5. 群聊撤回消息推送（下行）

```protobuf
message GroupWithdrawPush {
  string msgId = 1;           // 被撤回的消息ID
  string from = 2;            // 撤回操作人ID
  string groupId = 3;         // 群ID
  string operatorNickname = 4;// 操作人昵称（用于显示"XXX撤回了一条消息"）
  int64 withdrawTime = 5;     // 撤回时间戳
  bool isAdmin = 6;           // 是否为管理员撤回（true=管理员撤回他人消息）
}
```

**为什么需要 `isAdmin` 字段？**
- 群主/管理员可以撤回他人消息
- 客户端需要区分显示：
  - `isAdmin = false`: "张三撤回了一条消息"
  - `isAdmin = true`: "管理员撤回了张三的消息"

**客户端接收示例**：
```java
case GROUP_WITHDRAW:
    GroupWithdrawPush withdraw = GroupWithdrawPush.parseFrom(protoResponse.getPayload());
    if (withdraw.getIsAdmin()) {
        System.out.println("管理员撤回了 " + withdraw.getOperatorNickname() + " 的消息");
    } else {
        System.out.println(withdraw.getOperatorNickname() + " 撤回了一条消息");
    }
    break;
```

---

## 🔄 客户端消息判断流程

### 完整的消息类型判断

```java
ImProtoResponse protoResponse = ImProtoResponse.parseFrom(bytes);
MsgType msgType = protoResponse.getType();

switch (msgType) {
    // ========== 单聊消息 ==========
    case C2C_PUSH_MSG:
        C2CMsgPush c2cMsg = C2CMsgPush.parseFrom(protoResponse.getPayload());
        handleC2CMessage(c2cMsg);
        break;
        
    case C2C_ACK:
        C2CAckReq ack = C2CAckReq.parseFrom(protoResponse.getPayload());
        handleC2CAck(ack);
        break;
        
    case C2C_WITHDRAW:
        C2CWithdrawReq withdraw = C2CWithdrawReq.parseFrom(protoResponse.getPayload());
        handleC2CWithdraw(withdraw);
        break;
    
    // ========== 群聊消息（预留） ==========
    case GROUP_MSG_PUSH:
        GroupMsgPush groupMsg = GroupMsgPush.parseFrom(protoResponse.getPayload());
        handleGroupMessage(groupMsg);
        break;
        
    case GROUP_WITHDRAW:
        GroupWithdrawPush groupWithdraw = GroupWithdrawPush.parseFrom(protoResponse.getPayload());
        handleGroupWithdraw(groupWithdraw);
        break;
    
    default:
        System.out.println("未知消息类型: " + msgType);
        break;
}
```

---

## 🎨 单聊 vs 群聊对比表

| 特性 | 单聊 | 群聊 |
|-----|------|------|
| **消息类型** | `C2C_PUSH_MSG` | `GROUP_MSG_PUSH` |
| **发送类型** | `C2C_SEND` | `GROUP_SEND` |
| **接收方** | 1人 | 多人（群成员） |
| **显示信息** | 对方ID | 发送人昵称、头像、群名 |
| **ACK机制** | 简单（1对1） | 复杂（多人已读统计） |
| **撤回权限** | 仅发送者 | 发送者 + 群管理员 |
| **撤回推送** | `C2C_WITHDRAW` | `GROUP_WITHDRAW` + `isAdmin` |
| **会话列表** | 显示对方昵称 | 显示群名 + 最后发言人 |

---

## 🚀 未来实现建议

### 服务端架构

```
群聊消息发送流程:
1. Client A 发送 GROUP_SEND → im-connect
2. im-connect → im-business 处理
3. im-business 查询群成员列表
4. 遍历群成员，调用 gRPC 推送 GROUP_MSG_PUSH
5. im-connect 推送到每个在线群成员
6. 离线成员存储到离线消息表
```

### 数据库设计建议

```sql
-- 群消息表
CREATE TABLE im_group_msg_record (
  msg_id VARCHAR(64) PRIMARY KEY,
  group_id VARCHAR(64) NOT NULL,
  from_user_id VARCHAR(64) NOT NULL,
  msg_format TINYINT NOT NULL,
  msg_content TEXT,
  msg_create_time BIGINT NOT NULL,
  INDEX idx_group_time (group_id, msg_create_time)
);

-- 群消息已读记录表
CREATE TABLE im_group_msg_read_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msg_id VARCHAR(64) NOT NULL,
  group_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  read_time BIGINT NOT NULL,
  UNIQUE KEY uk_msg_user (msg_id, user_id),
  INDEX idx_msg (msg_id)
);
```

---

## ✅ 当前状态

- ✅ Protobuf 消息定义完成
- ✅ MsgType 枚举预留完成
- ✅ 编译验证通过
- ⏳ 业务逻辑暂未实现
- ⏳ 数据库表暂未创建
- ⏳ gRPC 服务暂未扩展

---

## 📚 相关文档

- [单聊测试指南](../../../im-client/src/main/java/com/xzll/client/protobuf/TEST_GUIDE.md)
- [Protobuf 协议定义](./message_service.proto)

---

**协议已预留，随时可以开始群聊功能开发！** 🎉

