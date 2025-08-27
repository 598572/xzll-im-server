# 单聊历史消息查询API文档

## 接口概述

本Controller提供单聊历史消息的完整查询功能，包括分页查询、条件查询、会话查询等。

## 基础信息

- **Controller路径**: `/api/c2c/message`
- **支持跨域**: 是
- **数据格式**: JSON
- **认证方式**: 根据系统配置

## API接口列表

### 1. 分页查询单聊历史消息

**接口地址**: `GET /api/c2c/message/history/page`

**请求参数**:
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| limit | int | 否 | 20 | 每页数量，最大100 |
| lastRowKey | string | 否 | - | 上一页的最后一个RowKey，用于分页 |

**请求示例**:
```http
GET /api/c2c/message/history/page?limit=50&lastRowKey=100-1-111-222_9223372036854775807_1-111-xxx
```

**响应示例**:
```json
{
  "success": true,
  "data": [
    {
      "msgCreateTime": 1756299389805,
      "chatId": "100-1-111-222",
      "fromUserId": "111",
      "msgId": "1-111-1960687134775255385",
      "msgFormat": 1,
      "updateTime": 1756299390154,
      "toUserId": "222",
      "msgStatus": 4,
      "msgContent": "你好啊 我发消息压死你，第345条消息",
      "createTime": 1756299389930
    }
  ],
  "count": 50,
  "limit": 50,
  "hasMore": true,
  "nextRowKey": "100-1-111-222_9223372036854775807_1-111-xxx"
}
```

### 2. 获取最新单聊消息

**接口地址**: `GET /api/c2c/message/history/latest`

**请求参数**:
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| limit | int | 否 | 20 | 消息数量限制，最大100 |

**请求示例**:
```http
GET /api/c2c/message/history/latest?limit=30
```

**响应示例**:
```json
{
  "success": true,
  "data": [...],
  "count": 30,
  "limit": 30
}
```

### 3. 根据会话ID查询单聊历史消息

**接口地址**: `GET /api/c2c/message/history/chat/{chatId}`

**路径参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| chatId | string | 是 | 会话ID |

**请求参数**:
| 参数名 | 类型 | 必填 | 默认值 | 说明 |
|--------|------|------|--------|------|
| limit | int | 否 | 50 | 消息数量限制，最大100 |

**请求示例**:
```http
GET /api/c2c/message/history/chat/100-1-111-222?limit=100
```

**响应示例**:
```json
{
  "success": true,
  "data": [...],
  "count": 100,
  "chatId": "100-1-111-222",
  "limit": 100
}
```

### 4. 条件查询单聊历史消息

**接口地址**: `GET /api/c2c/message/history/search`

**请求参数**:
| 参数名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| fromUserId | string | 否 | 发送者ID |
| toUserId | string | 否 | 接收者ID |
| chatId | string | 否 | 会话ID |

**请求示例**:
```http
GET /api/c2c/message/history/search?fromUserId=111&chatId=100-1-111-222
```

**响应示例**:
```json
{
  "success": true,
  "data": [...],
  "count": 25
}
```

### 5. HBase连接健康检查

**接口地址**: `GET /api/c2c/message/health`

**请求参数**: 无

**响应示例**:
```json
{
  "success": true,
  "hbaseHealthy": true,
  "connectionStatus": "HBase连接正常",
  "tableExists": true,
  "timestamp": 1756299389805,
  "message": "HBase连接正常"
}
```

## 分页查询使用说明

### 分页逻辑

1. **首次查询**: 不传`lastRowKey`参数，获取第一页数据
2. **下一页**: 使用返回的`nextRowKey`作为`lastRowKey`参数
3. **判断结束**: 当`hasMore`为`false`时，表示没有更多数据

### 分页示例

```javascript
// 第一页
const page1 = await fetch('/api/c2c/message/history/page?limit=20');
const data1 = await page1.json();
const nextRowKey = data1.nextRowKey;

// 第二页
if (data1.hasMore) {
  const page2 = await fetch(`/api/c2c/message/history/page?limit=20&lastRowKey=${nextRowKey}`);
  const data2 = await page2.json();
}
```

## 错误处理

### 错误响应格式

```json
{
  "success": false,
  "message": "错误描述信息",
  "error": "详细错误信息（可选）"
}
```

### 常见错误码

| 错误情况 | 说明 | 解决方案 |
|----------|------|----------|
| 查询数量超限 | limit > 100 | 系统自动调整为100 |
| 会话ID为空 | chatId参数缺失 | 检查请求参数 |
| HBase连接异常 | 数据库连接失败 | 检查HBase服务状态 |
| 表不存在 | 目标表未创建 | 检查HBase表结构 |

## 性能优化建议

### 1. 查询数量控制
- 单次查询建议不超过50条
- 避免频繁的大数据量查询

### 2. 分页查询
- 使用分页接口获取大量数据
- 合理设置每页数量

### 3. 缓存策略
- 对于不常变化的数据，考虑客户端缓存
- 合理使用`nextRowKey`进行分页

### 4. 错误重试
- 对于网络异常，实现指数退避重试
- 监控HBase连接状态

## 注意事项

1. **数据一致性**: 分页查询过程中，新数据可能影响分页结果
2. **性能考虑**: 避免在短时间内频繁调用接口
3. **资源管理**: 及时释放不需要的连接和资源
4. **监控告警**: 定期检查接口性能和HBase连接状态 