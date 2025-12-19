# 开发环境 vs 测试环境配置对比

## 📊 快速对比表

| 配置项 | 开发环境（dev） | 测试环境（test） | 说明 |
|-------|---------------|----------------|------|
| **日志级别** | `debug` | `info` | 开发环境输出详细日志 |
| **调试模式** | `true` | `false` | 开发环境启用调试 |
| **认证开关** | `false` | `false` | 两个环境都未启用认证 |

---

## 🔍 详细配置差异

### 1. Redis 配置差异

#### 连接地址
```yaml
# 开发环境
redis:
  addr: "${global.host}:6379"          # 单机地址

# 测试环境
redis:
  addr: "${global.host_131}:6379"      # 指定测试服务器
```

#### 连接池配置
| 配置项 | 开发环境 | 测试环境 | 差异说明 |
|-------|---------|---------|---------|
| `pool_size` | **32** | **8** | 开发环境 4 倍，支持频繁调试 |
| `min_idle_conns` | **4** | **0** | 开发环境保持最小连接 |
| `max_idle_conns` | **16** | - | 开发环境新增配置 |
| `pool_timeout` | **10s** | - | 开发环境新增配置 |
| `idle_timeout` | **5m** | - | 开发环境新增配置 |

**为什么开发环境连接池更大？**
- 支持频繁的安全检查和调试操作
- 避免连接池耗尽导致的调试中断
- 对应 Java 的 `lettuce.pool.max-active: 32`

---

### 2. TCP 连接配置差异

```yaml
# 开发环境
netty:
  so_backlog: 1024                     # 较小的连接队列

# 测试环境
netty:
  so_backlog: 65535                    # 大连接队列，支持高并发
```

**差异说明**：
- **开发环境**：1024 连接队列，适合小规模测试
- **测试环境**：65535 连接队列，模拟生产环境高并发

---

### 3. 安全限制差异（重要！）

| 配置项 | 开发环境 | 测试环境 | 倍数差异 |
|-------|---------|---------|---------|
| `max_connections_per_ip` | **1,000** | **1,000,000** | **1000x** |
| `max_total_connections` | **10,000** | **100,000,000** | **10000x** |
| `max_connections_per_minute` | **60** | **600,000,000** | **10000000x** |

**开发环境为什么这么严格？**
```yaml
# 开发环境 - 严格限制
security:
  max_connections_per_ip: 1000         # 单IP最多1000连接
  max_total_connections: 10000         # 全局最多1万连接
  max_connections_per_minute: 60       # 每分钟最多60个新连接

# 测试环境 - 极度宽松（接近无限制）
security:
  max_connections_per_ip: 1000000      # 单IP最多100万连接
  max_total_connections: 100000000     # 全局最多1亿连接
  max_connections_per_minute: 600000000 # 每分钟最多6亿新连接
```

**理由**：
- ✅ 开发环境适度限制，方便测试限流逻辑
- ✅ 测试环境接近生产，支持压力测试
- ✅ 防止开发时误操作导致资源耗尽

---

### 4. 流量控制差异（关键！）

| 配置项 | 开发环境 | 测试环境 | 倍数差异 |
|-------|---------|---------|---------|
| `max_messages_per_second` | **10** | **100,000** | **10000x** |
| `max_message_size` | **8 KB** | **8 MB** | **1000x** |
| `max_bytes_per_second` | **100 KB** | **100 MB** | **1000x** |

**为什么差异这么大？**

```yaml
# 开发环境 - 严格限流（方便测试）
flow_control:
  max_messages_per_second: 10          # 每秒最多10条消息
  max_message_size: 8192               # 单条消息最大8KB
  max_bytes_per_second: 102400         # 每秒最多100KB

# 测试环境 - 宽松限流（压力测试）
flow_control:
  max_messages_per_second: 100000      # 每秒最多10万条消息
  max_message_size: 8192000            # 单条消息最大8MB
  max_bytes_per_second: 102400000      # 每秒最多100MB
```

**使用场景**：
- 🎯 **开发环境**：容易触发限流，方便测试限流逻辑
- 🎯 **测试环境**：模拟生产高并发，压力测试

---

### 5. 消息重试扫描间隔

```yaml
# 开发环境 - 扫描更频繁
retry:
  scan_interval: 2000                  # 2秒扫描一次

# 测试环境 - 扫描较慢
retry:
  scan_interval: 10000                 # 10秒扫描一次
```

**差异说明**：
- **开发环境**：2秒扫描，更快发现重试消息，方便调试
- **测试环境**：10秒扫描，降低系统负担

---

### 6. RocketMQ 配置差异

#### 服务器地址

```yaml
# 开发环境 - 单机
rocketmq:
  server_addr: "${global.host}:9876"   # 单机MQ

# 测试环境 - 集群
rocketmq:
  server_addr: "${global.host_130}:9876;${global.host_131}:9876;${global.host_132}:9876"
```

#### 消费者/生产者组名

| 配置项 | 开发环境 | 测试环境 |
|-------|---------|---------|
| **生产者组** | `ExampleProducerGroup` | `ImProducerGroup` |
| **消费者组** | `ExampleConsumer` | `ImConsumer` |

**为什么不同？**
- 开发环境使用示例名称，方便识别
- 测试环境使用正式名称，接近生产

---

### 7. 日志配置差异

```yaml
# 开发环境 - 详细日志
logging:
  level: "debug"                       # 调试级别
  format: "console"                    # 控制台格式（带颜色）
  output: "stdout"                     # 输出到标准输出
  file:
    max_backups: 3                     # 保留3个备份
    max_age: 3                         # 保留3天
    compress: false                    # 不压缩

# 测试环境 - 生产级日志
logging:
  level: "info"                        # 信息级别
  format: "json"                       # JSON格式（便于分析）
  output: "logs/im-connect-go.log"     # 输出到文件
  file:
    max_backups: 7                     # 保留7个备份
    max_age: 7                         # 保留7天
    compress: true                     # 压缩旧日志
```

**差异说明**：
- **开发环境**：`debug` 级别，控制台输出，方便实时查看
- **测试环境**：`info` 级别，JSON格式，方便日志分析

---

### 8. 调试功能差异

```yaml
# 开发环境 - 启用调试功能
debug:
  enabled: true                        # 启用调试模式
  verbose_connection: true             # 打印详细连接信息
  verbose_message: true                # 打印详细消息内容
  pprof_enabled: true                  # 启用性能分析
  pprof_port: 6060                     # pprof 端口
  stack_trace: true                    # 打印堆栈跟踪

# 测试环境 - 无此配置
# （测试环境不需要调试功能）
```

**开发环境专属功能**：
- 🔍 `pprof` 性能分析（访问 `http://localhost:6060/debug/pprof`）
- 📊 详细的连接和消息日志
- 🐛 堆栈跟踪，方便定位问题

---

## 🎯 配置选择建议

### 什么时候用开发环境配置？

✅ **适用场景**：
- 本地开发调试
- 功能验证测试
- 限流逻辑测试
- 性能分析优化
- Bug 排查定位

✅ **特点**：
- 严格的流量限制（容易触发）
- 详细的调试日志
- 性能分析工具
- 快速的重试扫描

### 什么时候用测试环境配置？

✅ **适用场景**：
- 压力测试
- 性能测试
- 集成测试
- 预发布验证
- 多实例部署测试

✅ **特点**：
- 宽松的流量限制
- 生产级日志
- 集群模式
- 高并发支持

---

## 📝 配置文件对应关系

### 本地 Bootstrap 文件

```bash
# 开发环境
configs/bootstrap-dev.yaml          # 只包含 Nacos 连接信息

# 测试环境
configs/bootstrap-test.yaml         # 只包含 Nacos 连接信息
```

**内容示例**：
```yaml
# configs/bootstrap-dev.yaml
nacos:
  server_addr: "localhost:8848"
  namespace: "dev"                   # 开发命名空间
  data_id: "im-connect-go.yaml"
  group: "DEFAULT_GROUP"

# configs/bootstrap-test.yaml
nacos:
  server_addr: "nacos-server:8848"
  namespace: "test"                  # 测试命名空间
  data_id: "im-connect-go.yaml"
  group: "DEFAULT_GROUP"
```

### Nacos 远程配置文件

```bash
# 开发环境（在 Nacos 中创建）
Namespace: dev
Data ID:   im-connect-go.yaml
内容:      configs/nacos-dev-env.yaml 的内容

# 测试环境（在 Nacos 中创建）
Namespace: test
Data ID:   im-connect-go.yaml
内容:      configs/nacos-test-env.yaml 的内容
```

---

## 🚀 启动命令对比

```bash
# ========== 开发环境启动 ==========

# 方式1：使用 --env 参数（推荐）
./im-connect-go --env=dev

# 方式2：使用 --namespace 参数
./im-connect-go --namespace=dev

# 方式3：使用 Makefile
make start-dev

# 方式4：IDE 调试模式
# 在 IDE 中配置启动参数：--env=dev


# ========== 测试环境启动 ==========

# 方式1：使用 --env 参数（推荐）
./im-connect-go --env=test

# 方式2：使用 --namespace 参数
./im-connect-go --namespace=test

# 方式3：使用 Makefile
make start-test

# 方式4：后台运行
nohup ./im-connect-go --env=test > logs/app.log 2>&1 &
```

---

## 🔧 配置验证清单

### 开发环境验证

```bash
# 1. 检查服务启动
curl http://localhost:10000/health

# 2. 检查 WebSocket 端口
lsof -i :10001

# 3. 查看 pprof 性能分析
curl http://localhost:6060/debug/pprof/

# 4. 查看 Prometheus 指标
curl http://localhost:10000/metrics

# 5. 检查日志输出
tail -f logs/im-connect-go.log | grep -i "debug"

# 6. 测试限流（应该很容易触发）
# 每秒发送超过10条消息，应该被限流
```

### 测试环境验证

```bash
# 1. 检查服务启动
curl http://test-server:10000/health

# 2. 检查所有端口
lsof -i :10001  # WebSocket
lsof -i :10000  # Prometheus
lsof -i :9091   # gRPC

# 3. 压力测试
# 可以发送大量消息而不被限流

# 4. 查看集群状态
# 检查 RocketMQ 集群连接

# 5. 检查日志格式
tail -f logs/im-connect-go.log | head -1
# 应该是 JSON 格式
```

---

## ⚠️ 常见问题

### Q1: 为什么开发环境这么容易触发限流？

**A**: 这是故意设计的！

```yaml
# 开发环境配置
flow_control:
  max_messages_per_second: 10  # 故意设置很小
```

**目的**：
- ✅ 方便测试限流功能是否正常工作
- ✅ 不需要发送大量消息就能看到限流效果
- ✅ 确保限流逻辑在上线前被充分测试

**如果需要关闭限流测试**：
```bash
# 临时使用测试环境配置
./im-connect-go --env=test
```

### Q2: 开发环境的 Redis 连接池为什么是 32？

**A**: 对应 Java 版本的配置：

```yaml
# Java 开发环境配置
lettuce:
  pool:
    max-active: 32      # 最大活跃连接
    max-idle: 16        # 最大空闲连接
    min-idle: 4         # 最小空闲连接
```

**理由**：
- 支持频繁的安全检查操作
- 避免开发时连接池耗尽
- 匹配 Java 版本的行为

### Q3: 测试环境的限制为什么这么宽松？

**A**: 测试环境需要支持压力测试：

```yaml
# 测试环境配置（接近无限制）
security:
  max_connections_per_ip: 1000000
  max_total_connections: 100000000

flow_control:
  max_messages_per_second: 100000
```

**目的**：
- 压力测试不受限制
- 模拟生产环境高并发
- 验证系统极限性能

### Q4: 如何在开发环境临时关闭限制？

**方法1**：修改 Nacos 配置
```yaml
# 在 Nacos dev 命名空间中修改
flow_control:
  max_messages_per_second: 100000  # 临时改大
  max_message_size: 8192000
```

**方法2**：使用测试环境配置
```bash
./im-connect-go --env=test
```

**方法3**：创建自定义配置
```bash
# 复制开发配置，调整限制后使用
./im-connect-go --config=configs/bootstrap-custom.yaml
```

---

## 📚 相关文档

- [Nacos 配置指南](../NACOS_SETUP_GUIDE.md)
- [Java 到 Go 配置映射](./JAVA_TO_GO_CONFIG_MAPPING.md)
- [配置架构说明](../configs/ARCHITECTURE.md)
- [快速启动指南](../QUICK_START.md)

---

## 💡 最佳实践

1. **本地开发**
   - 使用开发环境配置 (`--env=dev`)
   - 启用调试日志和 pprof
   - 保持严格的限流配置（测试限流逻辑）

2. **功能测试**
   - 使用开发环境配置
   - 验证限流、认证等功能
   - 使用 `debug` 日志快速定位问题

3. **压力测试**
   - 使用测试环境配置 (`--env=test`)
   - 关闭调试日志（使用 `info` 级别）
   - 验证系统在高并发下的表现

4. **集成测试**
   - 使用测试环境配置
   - 启用集群模式（RocketMQ 集群）
   - 验证多实例协作

5. **预发布验证**
   - 使用生产环境配置 (`--env=prod`)
   - 启用认证和安全检查
   - 使用生产级日志配置

