# Elasticsearch 单元测试说明

## 概述

本项目为 `RestHighLevelClient` 创建了完整的单元测试，用于验证ES功能的各种操作。

## 测试文件结构

### 1. 测试基类
- **`BaseElasticsearchTest.java`**: ES测试基类，提供通用的测试方法和数据管理
  - 自动管理测试索引的创建和清理
  - 提供通用的测试数据生成方法
  - 管理测试生命周期

### 2. 具体测试类
- **`ImC2CMsgRecordESServiceTest.java`**: 单聊消息ES服务测试类
  - 测试消息的CRUD操作
  - 测试批量消息操作
  - 测试各种搜索查询功能

### 3. 测试配置
- **`application.yml`**: 主测试配置文件，设置测试profile
- **`application-test.yml`**: 测试环境特定配置
  - ES连接配置
  - HBase连接配置
  - 日志配置

## 测试功能覆盖

### 1. 基础操作测试
- ✅ ES连接测试
- ✅ 索引创建和删除
- ✅ 文档索引（创建）
- ✅ 文档获取
- ✅ 文档更新
- ✅ 文档删除

### 2. 搜索查询测试
- ✅ 精确匹配查询
- ✅ 全文搜索查询
- ✅ 复合查询（Bool查询）
- ✅ 时间范围查询
- ✅ 分页和排序查询

### 3. 批量操作测试
- ✅ 批量文档索引
- ✅ 批量操作结果验证

### 4. 异常处理测试
- ✅ 索引不存在的异常处理
- ✅ 连接异常处理

### 5. 性能测试
- ✅ 批量写入性能测试
- ✅ 查询性能测试

## 运行测试

### 1. 环境准备

#### ES环境要求
- Elasticsearch 7.x 版本
- 本地运行或可访问的ES实例
- 默认端口：9200

#### 修改测试配置
如果ES不是运行在本地，请修改 `application-test.yml`：

```yaml
spring:
  elasticsearch:
    uris: http://your-es-host:9200
```

**注意**: 配置文件结构说明：
- `application.yml` - 主配置文件，设置 `spring.profiles.active: test`
- `application-test.yml` - 测试环境特定配置，不能包含 `spring.profiles.active` 属性

### 2. 运行所有测试

```bash
# 在项目根目录执行
mvn test -Dtest=*ElasticsearchTest

# 或者在business-service模块执行
cd im-business/im-business-service
mvn test -Dtest=*ElasticsearchTest
```

### 3. 运行特定测试类

```bash
# 运行基类测试
mvn test -Dtest=BaseElasticsearchTest

# 运行消息服务测试
mvn test -Dtest=ImC2CMsgRecordESServiceTest

# 运行RestHighLevelClient测试
mvn test -Dtest=RestHighLevelClientTest
```

### 4. 运行特定测试方法

```bash
# 运行特定的测试方法
mvn test -Dtest=ImC2CMsgRecordESServiceTest#testMessageCRUD
mvn test -Dtest=ImC2CMsgRecordESServiceTest#testMessageSearchExactMatch
```

## 测试数据管理

### 1. 自动清理
- 测试完成后自动清理创建的测试索引
- 使用时间戳确保索引名称唯一性
- 避免测试数据污染生产环境

### 2. 测试数据生成
- 使用随机数据生成器创建测试消息
- 支持自定义消息内容和属性
- 批量数据生成支持

### 3. 索引管理
- 自动创建测试索引
- 配置合适的索引设置（分片、副本）
- 自动映射字段类型

## 测试最佳实践

### 1. 测试隔离
- 每个测试使用独立的索引
- 测试完成后自动清理
- 避免测试间的数据干扰

### 2. 异步操作处理
- 索引创建后等待可用状态
- 使用合理的超时时间
- 处理ES的异步特性

### 3. 错误处理
- 验证ES操作的返回结果
- 处理连接异常和超时
- 提供详细的错误信息

### 4. 性能考虑
- 批量操作提高测试效率
- 合理的查询大小限制
- 避免过大的测试数据集

## 常见问题

### 1. ES连接失败
- 检查ES服务是否运行
- 验证连接配置是否正确
- 检查网络连通性

### 2. 索引创建失败
- 检查ES集群状态
- 验证索引名称是否合法
- 检查权限设置

### 3. 测试超时
- 增加超时时间配置
- 检查ES集群性能
- 优化测试数据量

### 4. 清理失败
- 检查索引是否存在
- 验证删除权限
- 手动清理残留索引

## 扩展测试

### 1. 添加新的测试方法
继承 `BaseElasticsearchTest` 类，添加新的测试方法：

```java
@Test
@DisplayName("测试新功能")
public void testNewFeature() throws IOException {
    // 测试逻辑
}
```

### 2. 自定义测试数据
重写基类方法或添加新的数据生成方法：

```java
protected ImC2CMsgRecordES createCustomTestMessage() {
    // 自定义测试数据
}
```

### 3. 性能测试
添加性能相关的测试方法：

```java
@Test
@DisplayName("性能测试")
public void testPerformance() throws IOException {
    long startTime = System.currentTimeMillis();
    // 执行操作
    long duration = System.currentTimeMillis() - startTime;
    log.info("操作耗时: {} ms", duration);
}
```

## 注意事项

1. **测试环境隔离**: 确保测试不会影响生产数据
2. **资源清理**: 测试完成后及时清理资源
3. **异常处理**: 妥善处理测试过程中的异常
4. **日志记录**: 记录详细的测试执行信息
5. **性能监控**: 监控测试执行性能，及时发现问题

## 总结

这套ES测试框架提供了完整的测试覆盖，包括：
- 基础CRUD操作测试
- 各种搜索查询测试
- 批量操作测试
- 异常处理测试
- 性能测试

通过运行这些测试，可以验证ES功能的正确性和稳定性，为生产环境提供可靠的保障。 