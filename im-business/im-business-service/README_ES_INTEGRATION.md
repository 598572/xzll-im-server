# IM单聊消息ES集成说明

## 概述

本项目已集成Elasticsearch，用于存储和查询单聊消息记录。HBase作为主要存储，ES作为查询主力（在非rowKey查询场景时）。

## 架构设计

### 存储策略
- **HBase**: 主要存储，适合按rowKey精确查询
- **Elasticsearch**: 辅助存储，适合多条件查询、模糊查询、全文搜索

### 数据同步
- 写入HBase后自动同步到ES
- ES写入失败不影响HBase操作
- 支持消息状态更新同步

## 核心组件

### 1. ES实体类
- `ImC2CMsgRecordES`: ES消息记录实体
- 支持IK分词器进行中文分词
- 字段类型优化，支持高效查询

### 2. HBase服务增强
- `ImC2CMsgRecordHBaseServiceImpl`: 增强版HBase服务
- 自动同步数据到ES
- 支持重试、状态更新等操作同步

### 3. ES查询服务
- `ImC2CMsgRecordESQueryService`: ES查询服务接口
- `ImC2CMsgRecordESQueryServiceImpl`: ES查询服务实现
- 支持多种查询方式和复合查询

### 4. 查询控制器
- `ImC2CMsgRecordESController`: ES查询REST API
- 提供多种查询接口
- 支持分页查询

## 使用方法

### 1. 消息存储
消息会自动同时存储到HBase和ES，无需额外操作：

```java
// 在C2CSendMsgHandler.sendC2CMsgDeal()中调用
boolean writeMsg = imC2CMsgRecordService.saveC2CMsg(dto);
// 会自动同步到ES
```

### 2. 消息查询

#### 根据会话ID查询
```http
GET /es/c2c/msg/chat/{chatId}?page=0&size=20
```

#### 根据发送者查询
```http
GET /es/c2c/msg/from/{fromUserId}?page=0&size=20
```

#### 根据接收者查询
```http
GET /es/c2c/msg/to/{toUserId}?page=0&size=20
```

#### 消息内容搜索
```http
GET /es/c2c/msg/search/content?content=关键词&page=0&size=20
```

#### 会话内内容搜索
```http
GET /es/c2c/msg/search/chat-content?chatId=会话ID&content=关键词&page=0&size=20
```

#### 时间范围查询
```http
GET /es/c2c/msg/search/time-range?chatId=会话ID&startTime=开始时间戳&endTime=结束时间戳&page=0&size=20
```

#### 消息状态查询
```http
GET /es/c2c/msg/search/status?chatId=会话ID&msgStatus=状态码&page=0&size=20
```

#### 复合查询
```http
POST /es/c2c/msg/search/complex?chatId=会话ID&content=关键词&msgStatus=状态码&page=0&size=20
```

### 3. 查询参数说明

- `page`: 页码，从0开始
- `size`: 每页大小，默认20
- `chatId`: 会话ID
- `content`: 搜索内容（支持中文分词）
- `msgStatus`: 消息状态
- `startTime`: 开始时间戳（毫秒）
- `endTime`: 结束时间戳（毫秒）

## 性能优化

### 1. 索引优化
- 使用合适的字段类型（Keyword、Text、Long等）
- 消息内容使用IK分词器，支持中文搜索
- 时间字段使用Long类型，支持范围查询

### 2. 查询优化
- 支持分页查询，避免大量数据返回
- 复合查询使用BoolQuery，支持条件组合
- 默认按时间倒序排列，符合聊天场景

### 3. 容错处理
- ES操作失败不影响HBase操作
- 详细的日志记录，便于问题排查
- 异常捕获和错误处理

## 配置要求

### 1. ES配置
确保在配置文件中设置ES连接信息：

```yaml
im:
  elasticsearch:
    uris:
      - http://localhost:9200
```

### 2. 依赖配置
项目已包含必要的ES依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-elasticsearch</artifactId>
</dependency>
```

## 注意事项

1. **数据一致性**: ES作为辅助存储，可能存在短暂的数据不一致
2. **性能影响**: 每次写入都会同步到ES，可能影响写入性能
3. **存储空间**: 数据会同时存储在HBase和ES中，需要额外的存储空间
4. **故障处理**: ES故障时，HBase操作仍可正常进行

## 扩展建议

1. **异步同步**: 考虑使用消息队列异步同步数据到ES
2. **批量操作**: 支持批量写入和更新操作
3. **数据清理**: 定期清理ES中的过期数据
4. **监控告警**: 添加ES操作监控和异常告警

## 故障排查

### 1. ES连接问题
- 检查ES服务状态
- 验证连接配置
- 查看网络连通性

### 2. 数据同步问题
- 检查HBase操作日志
- 查看ES写入日志
- 验证数据格式

### 3. 查询性能问题
- 检查ES索引配置
- 优化查询语句
- 调整分页参数 