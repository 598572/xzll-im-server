# 好友请求功能测试说明

## 📋 功能概述

本文档说明如何使用客户端测试完整的好友申请流程，包括：
1. **user1** 向 **user2** 发送好友申请
2. **user2** 收到好友请求推送，自动同意申请
3. **user1** 收到好友申请响应（同意通知）

## 🎯 消息流程

```
┌─────────┐                  ┌─────────┐                  ┌─────────┐
│  user1  │                  │  服务端  │                  │  user2  │
│ (申请人) │                  │         │                  │ (接收人) │
└────┬────┘                  └────┬────┘                  └────┬────┘
     │                            │                            │
     │  1. HTTP: 发送好友申请      │                            │
     ├───────────────────────────>│                            │
     │                            │                            │
     │                            │  2. WebSocket推送:         │
     │                            │     FRIEND_REQUEST         │
     │                            ├───────────────────────────>│
     │                            │                            │
     │                            │  3. HTTP: 处理好友申请      │
     │                            │    (同意/拒绝)             │
     │                            │<───────────────────────────┤
     │                            │                            │
     │  4. WebSocket推送:         │                            │
     │     FRIEND_RESPONSE        │                            │
     │<───────────────────────────┤                            │
     │                            │                            │
```

## 🔧 代码实现

### 1. user2 处理好友请求

**文件**: `ProtobufWebsocketClientHandler2.java`

#### 📨 收到好友请求（FRIEND_REQUEST）

```java
private void handleFriendRequest(ImProtoResponse protoResponse) {
    // 解析好友请求
    FriendRequestPush request = FriendRequestPush.parseFrom(protoResponse.getPayload());
    
    // 显示好友请求信息
    System.out.println("📨 [user2] 收到好友请求:");
    System.out.println("  申请人: " + request.getFromUserName());
    System.out.println("  申请消息: " + request.getRequestMessage());
    
    // 延迟2秒后自动同意
    new Thread(() -> {
        Thread.sleep(2000);
        
        // 构建处理请求
        JSONObject handleRequest = new JSONObject();
        handleRequest.put("requestId", request.getRequestId());
        handleRequest.put("userId", request.getToUserId());
        handleRequest.put("handleResult", 1); // 1=同意, 2=拒绝
        
        // 调用HTTP接口
        String result = sendHttpPost(
            "http://127.0.0.1:8083/api/friend/request/handle", 
            handleRequest.toJSONString()
        );
        
        System.out.println("✅ [user2] 已同意好友申请");
    }).start();
}
```

#### 📬 收到好友响应（FRIEND_RESPONSE）

```java
private void handleFriendResponse(ImProtoResponse protoResponse) {
    FriendResponsePush response = FriendResponsePush.parseFrom(protoResponse.getPayload());
    
    if (response.getStatus() == 1) {
        System.out.println("🎉 恭喜！" + response.getFromUserName() + " 同意了你的好友申请");
    } else {
        System.out.println("😔 " + response.getFromUserName() + " 拒绝了你的好友申请");
    }
}
```

### 2. user1 处理好友响应

**文件**: `ProtobufWebsocketClientHandler1.java`

#### 📬 收到好友响应（FRIEND_RESPONSE）

```java
private void handleFriendResponse(ImProtoResponse protoResponse) {
    FriendResponsePush response = FriendResponsePush.parseFrom(protoResponse.getPayload());
    
    if (response.getStatus() == 1) {
        System.out.println("🎉 [user1] 恭喜！" + response.getFromUserName() + " 同意了你的好友申请");
    } else {
        System.out.println("😔 [user1] " + response.getFromUserName() + " 拒绝了你的好友申请");
    }
}
```

## 🚀 测试步骤

### 步骤 1: 启动服务端

确保以下服务已启动：
- im-gateway (端口 8082)
- im-business (端口 8083)
- im-connect (端口 8081)
- MySQL
- Redis
- Nacos

### 步骤 2: 启动客户端

1. **启动 user2 客户端**：
   ```java
   // 运行 ProtobufWebsocketClient2.java
   // user2 会自动连接到 WebSocket 服务器
   ```

2. **启动 user1 客户端**：
   ```java
   // 运行 ProtobufWebsocketClient1.java
   // user1 会自动连接到 WebSocket 服务器
   ```

### 步骤 3: 发送好友申请

使用 Postman 或 curl 发送 HTTP 请求：

```bash
curl -X POST http://127.0.0.1:8083/api/friend/request/send \
  -H "Content-Type: application/json" \
  -d '{
    "fromUserId": "user1",
    "toUserId": "user2",
    "requestMessage": "你好，我想加你为好友"
  }'
```

### 步骤 4: 观察日志输出

#### user2 控制台输出：

```
============================================
📨 [user2] 收到好友请求:
  申请人: user1 (user1)
  申请消息: 你好，我想加你为好友
  请求ID: xxxx-xxxx-xxxx
  状态: 待处理
  推送标题: 好友请求
  推送内容: user1 请求添加你为好友
============================================

💡 [user2] 准备处理好友请求...

✅ [user2] 好友请求处理完成！
   响应结果: {"code":1,"msg":"响应成功","data":true}
   已同意 user1 的好友申请
```

#### user1 控制台输出：

```
============================================
📬 [user1] 收到好友申请响应:
  响应人: user2 (user2)
  请求ID: xxxx-xxxx-xxxx
  结果: ✅ 已同意
  🎉 恭喜！user2 同意了你的好友申请
  推送标题: 好友申请处理结果
  推送内容: user2 同意了你的好友申请
============================================
```

## 🎨 控制台输出示例

### user2 收到并处理好友请求

```
============================================
📨 [user2] 收到好友请求:
  申请人: 张三 (user1)
  申请消息: 你好，我想加你为好友
  请求ID: 1234567890
  状态: 待处理
  推送标题: 好友请求
  推送内容: 张三 请求添加你为好友
============================================
💡 [user2] 准备处理好友请求...
✅ [user2] 好友请求处理完成！
   响应结果: {"code":1,"msg":"响应成功","data":true}
   已同意 张三 的好友申请
```

### user1 收到好友申请响应

```
============================================
📬 [user1] 收到好友申请响应:
  响应人: 李四 (user2)
  请求ID: 1234567890
  结果: ✅ 已同意
  🎉 恭喜！李四 同意了你的好友申请
  推送标题: 好友申请处理结果
  推送内容: 李四 同意了你的好友申请
============================================
```

## 📊 消息类型说明

| 消息类型 | 枚举值 | 方向 | 说明 |
|---------|-------|-----|------|
| `FRIEND_REQUEST` | 11 | 服务端 → 客户端 | 好友请求推送 |
| `FRIEND_RESPONSE` | 12 | 服务端 → 客户端 | 好友申请响应推送 |

## 🔍 关键特性

### 1. 自动处理
user2 收到好友请求后，会在 **2 秒后自动同意**，模拟用户操作：

```java
Thread.sleep(2000); // 模拟用户思考时间
handleRequest.put("handleResult", 1); // 1=同意
```

### 2. HTTP + WebSocket 结合
- **HTTP 接口**: 发送好友申请、处理好友申请
- **WebSocket**: 实时推送好友请求和响应

### 3. 离线支持
如果用户不在线，消息会保存到 Redis，用户上线后自动推送。

## ⚠️ 注意事项

1. **端口配置**: 确保 HTTP 请求的端口（8083）与 im-business 服务端口一致
2. **用户ID**: 确保 user1 和 user2 已在数据库中存在
3. **网络连接**: 确保客户端能访问服务端的 WebSocket 和 HTTP 端口
4. **消息顺序**: 先启动客户端，再发送好友申请

## 🐛 调试技巧

### 查看完整日志
如果消息没有收到，检查：
1. WebSocket 连接是否正常建立
2. 服务端日志是否有推送记录
3. Redis 中是否有离线消息

### 修改自动同意行为
如果想手动控制，可以注释掉自动同意的代码：

```java
// 注释掉这段代码，不自动同意
// new Thread(() -> { ... }).start();
```

## 📅 更新日期

- **2025-10-29**: 初始版本，实现好友请求的自动处理功能

## 🔗 相关文档

- [好友功能实现文档](../../doc/好友/README_好友功能实现完成.md)
- [好友消息类型更新](../../doc/好友/README_好友消息类型更新.md)
- [好友申请推送功能](../../doc/好友/README_好友申请推送功能.md)

