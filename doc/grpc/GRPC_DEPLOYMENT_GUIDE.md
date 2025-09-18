# gRPC完整部署和测试指南

## 🚀 部署步骤

### 1. 安装protoc编译器

#### macOS
```bash
brew install protobuf
```

#### Ubuntu/Debian
```bash
sudo apt update
sudo apt install protobuf-compiler
```

#### CentOS/RHEL
```bash
sudo yum install protobuf-compiler
```

### 2. 编译项目

```bash
# 清理并编译
mvn clean compile

# 或者只编译proto文件
mvn protobuf:compile
mvn protobuf:compile-custom
```

### 3. 启动服务

```bash
# 启动im-connect-service
cd im-connect/im-connect-service
mvn spring-boot:run

# 启动im-business-service
cd im-business/im-business-service
mvn spring-boot:run
```

## 🧪 测试步骤

### 1. 基础功能测试

#### 测试gRPC服务是否启动
```bash
# 检查端口是否监听
netstat -an | grep 9090
# 或者
lsof -i :9090
```

#### 测试gRPC服务健康状态
```bash
# 使用grpcurl测试（需要先安装）
grpcurl -plaintext localhost:9090 list
```

### 2. 性能测试

#### 通过HTTP接口测试
```bash
# 运行完整性能测试
curl http://localhost:8080/grpc/test/performance

# 运行单次调用测试
curl http://localhost:8080/grpc/test/single

# 运行并发测试
curl http://localhost:8080/grpc/test/concurrent

# 运行批量发送测试
curl http://localhost:8080/grpc/test/batch
```

#### 查看测试结果
```bash
# 查看应用日志
tail -f logs/application.log

# 或者查看控制台输出
```

### 3. 业务功能测试

#### 测试消息发送
```bash
# 模拟发送单聊消息
curl -X POST http://localhost:8080/api/c2c/send \
  -H "Content-Type: application/json" \
  -d '{
    "fromUserId": "user001",
    "toUserId": "user002",
    "msgContent": "Hello gRPC!",
    "msgFormat": "text"
  }'
```

## 📊 性能对比

### 预期性能提升

| 指标 | Dubbo | gRPC | 提升幅度 |
|------|-------|------|----------|
| 单次调用延迟 | ~2-5ms | ~0.5-2ms | **2-10倍** |
| 并发处理能力 | ~1000 QPS | ~5000 QPS | **5倍** |
| 内存占用 | 中等 | 低 | **30-50%** |
| 网络效率 | 中等 | 高 | **3-5倍** |

### 性能测试指标

- **响应时间**: 单次调用的平均响应时间
- **吞吐量**: 每秒处理的请求数（QPS）
- **并发能力**: 同时处理的请求数
- **资源占用**: CPU、内存、网络使用情况

## 🔧 配置说明

### gRPC服务器配置

```yaml
# application-grpc.yml
grpc:
  server:
    port: 9090
    max-inbound-message-size: 1048576  # 1MB
    max-inbound-metadata-size: 8192    # 8KB
    keep-alive-time: 30                # 30秒
    keep-alive-timeout: 5              # 5秒
    permit-keep-alive-without-calls: true
```

### 连接池配置

```java
// SmartGrpcClientManager中的配置
.keepAliveTime(30, TimeUnit.SECONDS)           // 保活时间
.keepAliveTimeout(5, TimeUnit.SECONDS)         // 保活超时
.maxInboundMessageSize(10 * 1024 * 1024)      // 最大消息大小
.maxInboundMetadataSize(8192)                  // 最大元数据大小
```

## 🐛 故障排除

### 常见问题

#### 1. protoc编译失败
```bash
# 检查protoc版本
protoc --version

# 确保版本 >= 3.0.0
```

#### 2. gRPC服务启动失败
```bash
# 检查端口占用
lsof -i :9090

# 检查日志
tail -f logs/application.log
```

#### 3. 连接失败
```bash
# 检查网络连通性
telnet target_ip 9090

# 检查防火墙设置
sudo ufw status
```

### 日志分析

#### 关键日志关键字
- `gRPC服务器启动成功` - 服务正常启动
- `创建gRPC连接到` - 新连接建立
- `gRPC发送消息成功` - 消息发送成功
- `gRPC发送消息失败` - 消息发送失败

## 📈 监控和优化

### 监控指标

1. **连接状态**
   - 活跃连接数
   - 连接创建/关闭频率
   - 连接健康状态

2. **性能指标**
   - 请求响应时间
   - 成功/失败率
   - QPS变化趋势

3. **资源使用**
   - CPU使用率
   - 内存占用
   - 网络I/O

### 优化建议

1. **连接池调优**
   - 根据实际负载调整连接池大小
   - 设置合适的连接超时时间

2. **并发调优**
   - 调整线程池大小
   - 优化异步处理策略

3. **网络调优**
   - 启用TLS加密（生产环境）
   - 配置负载均衡

## 🎯 下一步计划

### 短期目标（1-2周）
- [x] 完成基础gRPC服务
- [x] 替换核心消息处理器
- [x] 实现性能测试框架
- [ ] 完成所有Dubbo服务替换
- [ ] 进行性能测试和调优

### 中期目标（1个月）
- [ ] 生产环境部署
- [ ] 性能监控系统集成
- [ ] 自动化测试完善
- [ ] 文档和培训

### 长期目标（3个月）
- [ ] 完全移除Dubbo依赖
- [ ] 实现服务网格集成
- [ ] 多语言客户端支持
- [ ] 云原生架构优化

## 📞 技术支持

如果在部署或测试过程中遇到问题，请：

1. 查看应用日志
2. 检查配置文件
3. 参考故障排除部分
4. 联系开发团队

---

**注意**: 本指南基于当前项目状态编写，随着项目发展可能会有所更新。 