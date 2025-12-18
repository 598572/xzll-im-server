# Nacos 配置问题修复说明

## 🐛 问题描述

启动时报错：
```
❌ 初始化 Redis 失败  error: "Redis 连接失败: dial tcp [::1]:6379: connect: connection refused"
```

**原因**：虽然在 Nacos 中配置了 Redis，但是代码没有从 Nacos 读取 Redis 配置，导致使用了默认值 `localhost:6379`。

---

## ✅ 修复内容

### 1. 修改 `pkg/nacos/nacos.go`

**增加配置结构**：
```go
// NacosConfig 增加 Redis 和 RocketMQ 配置
type NacosConfig struct {
    Redis struct {
        Address      string        `yaml:"addr"`
        Password     string        `yaml:"password"`
        DB           int           `yaml:"db"`
        PoolSize     int           `yaml:"pool_size"`
        // ...
    } `yaml:"redis"`
    
    RocketMQ struct {
        ServerAddr string `yaml:"server_addr"`
        Producer   struct {
            GroupName string `yaml:"group_name"`
            // ...
        } `yaml:"producer"`
    } `yaml:"rocketmq"`
    
    // ... 其他配置
}
```

**更新配置读取函数**：
```go
func updateDynamicConfig(nacosConfig *NacosConfig) {
    // ========== 更新 Redis 配置 ==========
    if nacosConfig.Redis.Address != "" {
        appConfig.Redis.Address = nacosConfig.Redis.Address
        logger.Info("更新 Redis 地址", zap.String("address", nacosConfig.Redis.Address))
    }
    // ...
    
    // ========== 更新 RocketMQ 配置 ==========
    if nacosConfig.RocketMQ.ServerAddr != "" {
        appConfig.RocketMQ.ServerAddr = nacosConfig.RocketMQ.ServerAddr
        logger.Info("更新 RocketMQ 地址", zap.String("address", nacosConfig.RocketMQ.ServerAddr))
    }
    // ...
}
```

### 2. 修改 `cmd/main.go`

**添加配置打印**：
```go
// 初始化 Nacos 配置中心
if err := nacos.InitNacosConfig(cfg, logger); err != nil {
    logger.Fatal("❌ 初始化 Nacos 失败", zap.Error(err))
}
logger.Info("✅ Nacos 配置中心初始化成功")

// 等待 Nacos 配置加载完成
time.Sleep(2 * time.Second)

// 打印从 Nacos 加载的配置（调试用）
logger.Info("📋 从 Nacos 加载的配置",
    zap.String("redis_address", cfg.Redis.Address),
    zap.String("rocketmq_address", cfg.RocketMQ.ServerAddr),
)
```

---

## 📝 Nacos 配置示例

在 Nacos 控制台中，配置文件应该包含以下内容：

### Namespace: `dev_id`
### Group: `xzll-im`
### Data ID: `im-connect-go.yaml`

```yaml
# ==================== Redis 配置 ====================
redis:
  # Redis 地址（必须配置）
  addr: "120.46.85.43:6379"
  
  # Redis 密码
  password: "your_redis_password"
  
  # 数据库索引
  db: 0
  
  # 连接池大小
  pool_size: 32
  
  # 最小空闲连接数
  min_idle_conns: 4
  
  # 超时配置
  dial_timeout: 10s
  read_timeout: 5s
  write_timeout: 5s

# ==================== RocketMQ 配置 ====================
rocketmq:
  # MQ 服务器地址
  server_addr: "192.168.1.100:9876"
  
  # 生产者配置
  producer:
    group_name: "ImConnectGoProducerGroup"
    max_message_size: 4096
    send_timeout: 10
    retry_times: 3

# ==================== Netty 配置 ====================
netty:
  boss_threads: 0
  worker_threads: 0
  so_backlog: 1024
  socket_buffer_size: 32768
  write_buffer_low_water_mark: 32768
  write_buffer_high_water_mark: 131072
  enable_compression: false
  ping_interval: 25000
  pong_timeout: 10000
  max_message_size: 8192
  heartbeat_timeout: 45
  max_heartbeat_failures: 3
  idle_state_check_interval: 30

# ==================== 其他配置 ====================
# ... 根据需要添加
```

---

## 🚀 启动验证

重新编译并启动：

```bash
# 1. 编译
cd /Users/hzz/myself_project/开源09/xzll-im-server/im-connect-go
go build -o im-connect-go cmd/main.go

# 2. 启动（开发环境）
./im-connect-go --env=dev
```

**预期日志**：

```
✅ 配置加载成功
   nacos_server: 120.46.85.43:8848
   nacos_namespace: dev_id
   nacos_group: xzll-im

✅ Nacos 配置中心初始化成功

✅ 配置已从 Nacos 更新
   redis_address: 120.46.85.43:6379
   rocketmq_address: 192.168.1.100:9876

📋 从 Nacos 加载的配置
   redis_address: 120.46.85.43:6379
   rocketmq_address: 192.168.1.100:9876
   rocketmq_group: ImConnectGoProducerGroup

✅ Redis 连接池初始化成功
   address: 120.46.85.43:6379
   db: 0

✅ RocketMQ Producer 启动成功
   name_servers: [192.168.1.100:9876]
```

---

## 🔍 故障排查

### 1. Redis 配置未生效

**检查步骤**：

1. **确认 Nacos 中配置存在**：
   ```bash
   # 访问 Nacos 控制台
   http://120.46.85.43:8848/nacos
   
   # 查看配置列表
   # Namespace: dev_id
   # Group: xzll-im
   # Data ID: im-connect-go.yaml
   ```

2. **查看启动日志**：
   ```bash
   ./im-connect-go --env=dev 2>&1 | grep -E "Redis|Nacos|配置"
   ```

3. **检查配置格式**：
   - 确保 `addr` 字段名称正确（不是 `address`）
   - 确保缩进正确（YAML 格式严格）
   - 确保 Redis 地址格式：`host:port`

### 2. RocketMQ 配置未生效

**检查步骤**：

1. **确认配置字段名**：
   ```yaml
   rocketmq:          # 注意：是 rocketmq，不是 rocket_mq
     server_addr:     # 注意：是 server_addr，不是 serverAddr
   ```

2. **检查日志**：
   ```bash
   ./im-connect-go --env=dev 2>&1 | grep -E "RocketMQ|rocketmq"
   ```

### 3. Nacos 连接失败

**常见原因**：
1. Nacos 服务器地址错误
2. Namespace 或 Group 配置错误
3. 网络不通（防火墙）

**解决方法**：
```bash
# 测试 Nacos 连接
curl "http://120.46.85.43:8848/nacos/v1/cs/configs?dataId=im-connect-go.yaml&group=xzll-im&tenant=dev_id"
```

---

## 📋 完整配置清单

### 本地 Bootstrap 文件（`configs/bootstrap-dev.yaml`）

```yaml
nacos:
  server_addr: "120.46.85.43:8848"
  namespace: "dev_id"
  data_id: "im-connect-go.yaml"
  group: "xzll-im"
  username: "nacos"
  password: "nacos"
  context_path: "/nacos"
  timeout: 10s
```

**注意**：
- Bootstrap 文件**只配置 Nacos 连接信息**
- Redis、RocketMQ 等业务配置**全部在 Nacos 中配置**
- 这是微服务的最佳实践

### Nacos 远程配置（`im-connect-go.yaml`）

```yaml
# 完整配置示例
redis:
  addr: "120.46.85.43:6379"
  password: "your_password"
  db: 0
  pool_size: 32
  min_idle_conns: 4
  dial_timeout: 10s
  read_timeout: 5s
  write_timeout: 5s

rocketmq:
  server_addr: "192.168.1.100:9876"
  producer:
    group_name: "ImConnectGoProducerGroup"
    max_message_size: 4096
    send_timeout: 10
    retry_times: 3

server:
  port: 10001
  prometheus_port: 10000
  max_connections: 10000

netty:
  boss_threads: 0
  worker_threads: 0
  so_backlog: 1024
  # ... 其他配置

security:
  max_connections_per_ip: 1000
  max_total_connections: 10000
  max_connections_per_minute: 60

flow_control:
  max_messages_per_second: 10
  max_message_size: 8192
  max_bytes_per_second: 102400

auth:
  enabled: false
  token_expire_check: false
  stress_test_enabled: true
  stress_test_token: "DEV_STRESS_TEST_TOKEN"

grpc:
  port: 9091
  max_recv_msg_size: 1048576
  max_send_msg_size: 1048576
  connection_timeout: 10s

retry:
  enabled: true
  max_retries: 3
  delays: [2, 5, 20]
  batch_size: 10000
  scan_interval: 2000

msg:
  c2c:
    push_offline_msg_count: 9
  group:
    max_user_count: 1000

logging:
  level: "debug"
  format: "console"
  output: "stdout"

app:
  name: "im-connect-go"
  version: "1.0.0-dev"
  environment: "dev"
  machine_id: 1
```

---

## ✅ 总结

修复后，程序会：

1. ✅ 从本地 `bootstrap-dev.yaml` 读取 Nacos 连接信息
2. ✅ 连接到 Nacos 服务器
3. ✅ 从 Nacos 读取完整的业务配置（Redis、RocketMQ 等）
4. ✅ 更新内存中的配置
5. ✅ 使用正确的 Redis 和 RocketMQ 地址启动服务

**关键变化**：
- 🔧 增加了 Redis 和 RocketMQ 配置的 Nacos 读取支持
- 🔧 增加了配置更新日志，方便调试
- 🔧 增加了 2 秒等待时间，确保异步配置加载完成

现在你的服务应该可以正确连接 Redis 和 RocketMQ 了！🎉

