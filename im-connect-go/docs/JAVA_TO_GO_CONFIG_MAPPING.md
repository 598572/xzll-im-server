# Java 配置到 Go 配置映射对照表

## 📋 快速对照表

### Redis 配置

| Java 配置路径 | Java 值示例 | Go 配置路径 | Go 值示例 |
|-------------|-----------|-----------|---------|
| `spring.redis.database` | `0` | `redis.db` | `0` |
| `spring.redis.host` + `spring.redis.port` | `${global.host_131}` + `6379` | `redis.addr` | `"${global.host_131}:6379"` |
| `spring.redis.password` | `wcnim@bilp740.` | `redis.password` | `"wcnim@bilp740."` |
| `spring.redis.timeout` | `10000` (毫秒) | `redis.dial_timeout` | `10s` |
| `spring.redis.pool.max-active` | `8` | `redis.pool_size` | `8` |
| `spring.redis.pool.max-idle` | `8` | _（连接池自动管理）_ | - |
| `spring.redis.pool.min-idle` | `0` | `redis.min_idle_conns` | `0` |

### Netty/服务器配置

| Java 配置路径 | Java 值 | Go 配置路径 | Go 值 |
|-------------|--------|-----------|------|
| `im.netty.nettyPort` | `10001` | `server.port` | `10001` |
| `im.netty.prometheusPort` | `10000` | `server.prometheus_port` | `10000` |
| `im.netty.soBackLog` | `65535` | `netty.so_backlog` | `65535` |
| `im.netty.debug` | `false` | `logging.level` | `"info"` |
| `im.netty.bossThreads` | `0` | `netty.boss_threads` | `0` |
| `im.netty.workerThreads` | `0` | `netty.worker_threads` | `0` |
| `im.netty.socketBufferSize` | `32768` | `netty.socket_buffer_size` | `32768` |
| `im.netty.writeBufferLowWaterMark` | `32768` | `netty.write_buffer_low_water_mark` | `32768` |
| `im.netty.writeBufferHighWaterMark` | `131072` | `netty.write_buffer_high_water_mark` | `131072` |
| `im.netty.enableCompression` | `false` | `netty.enable_compression` | `false` |
| `im.netty.idleStateCheckInterval` | `30` (秒) | `netty.idle_state_check_interval` | `30` |
| `im.netty.heartBeatTime` | `45` (秒) | `netty.heartbeat_timeout` | `45` |
| `im.netty.maxHeartbeatFailures` | `3` | `netty.max_heartbeat_failures` | `3` |
| `im.netty.activeHeartbeatInterval` | `25` (秒) | `netty.ping_interval` | `25s` |

### 安全配置

| Java 配置路径 | Java 值 | Go 配置路径 | Go 值 |
|-------------|--------|-----------|------|
| `im.netty.security.max-connections-per-ip` | `1000000` | `security.max_connections_per_ip` | `1000000` |
| `im.netty.security.max-total-connections` | `100000000` | `security.max_total_connections` | `100000000` |
| `im.netty.security.max-connections-per-minute` | `600000000` | `security.max_connections_per_minute` | `600000000` |

### 流量控制配置

| Java 配置路径 | Java 值 | Go 配置路径 | Go 值 |
|-------------|--------|-----------|------|
| `im.netty.flow-control.max-messages-per-second` | `100000` | `flow_control.max_messages_per_second` | `100000` |
| `im.netty.flow-control.max-message-size` | `8192000` | `flow_control.max_message_size` | `8192000` |
| `im.netty.flow-control.max-bytes-per-second` | `102400000` | `flow_control.max_bytes_per_second` | `102400000` |

### 认证配置

| Java 配置路径 | Java 值 | Go 配置路径 | Go 值 |
|-------------|--------|-----------|------|
| `im.netty.auth.enabled` | `false` | `auth.enabled` | `false` |
| `im.netty.auth.token-expire-check` | `false` | `auth.token_expire_check` | `false` |
| `im.netty.auth.max-auth-failures` | `5` | `auth.max_auth_failures` | `5` |
| `im.netty.auth.lockout-duration-minutes` | `30` | `auth.lockout_duration_minutes` | `30` |

### gRPC 配置

| Java 配置路径 | Java 值 | Go 配置路径 | Go 值 |
|-------------|--------|-----------|------|
| `grpc.server.port` | `9091` | `grpc.port` | `9091` |
| `grpc.server.max-inbound-message-size` | `1048576` | `grpc.max_recv_msg_size` | `1048576` |
| `grpc.server.keep-alive-time` | `30` | `grpc.max_connection_idle` | `30s` |
| `grpc.server.keep-alive-timeout` | `5` | `grpc.max_connection_age_grace` | `5s` |
| `grpc.client.connect-timeout` | `10` | `grpc.connection_timeout` | `10s` |

### 消息重试配置

| Java 配置路径 | Java 值 | Go 配置路径 | Go 值 |
|-------------|--------|-----------|------|
| `im-server.c2c.retry.enabled` | `true` | `retry.enabled` | `true` |
| `im-server.c2c.retry.max-retries` | `3` | `retry.max_retries` | `3` |
| `im-server.c2c.retry.delays` | `2,5,20` | `retry.delays` | `[2, 5, 20]` |
| `im-server.c2c.retry.batch-size` | `10000` | `retry.batch_size` | `10000` |
| `im-server.c2c.retry.scan-interval` | `10000` | `retry.scan_interval` | `10000` |

### 消息配置

| Java 配置路径 | Java 值 | Go 配置路径 | Go 值 |
|-------------|--------|-----------|------|
| `im.msg.c2cMsgConfig.pushOfflineMsgCount` | `9` | `msg.c2c.push_offline_msg_count` | `9` |
| `im.msg.groupMsgConfig.groupMaxUserCount` | `1000` | `msg.group.max_user_count` | `1000` |

---

## 🔑 主要差异说明

### 1. 时间单位表示

**Java 版本**：通常使用纯数字（单位在注释说明）
```yaml
timeout: 10000  # 毫秒
heartBeatTime: 45  # 秒
```

**Go 版本**：使用带单位的字符串
```yaml
dial_timeout: 10s
heartbeat_timeout: 45
ping_interval: 25s
```

**Go 时间单位**：
- `ns` = 纳秒
- `us` = 微秒
- `ms` = 毫秒
- `s` = 秒
- `m` = 分钟
- `h` = 小时

### 2. 数组格式

**Java 版本**：逗号分隔
```yaml
delays: 2,5,20
```

**Go 版本**：YAML 数组格式
```yaml
delays: [2, 5, 20]
# 或
delays:
  - 2
  - 5
  - 20
```

### 3. Redis 地址配置

**Java 版本**：host 和 port 分开
```yaml
spring:
  redis:
    host: ${global.host_131}
    port: 6379
```

**Go 版本**：合并为 addr
```yaml
redis:
  addr: "${global.host_131}:6379"
```

### 4. 配置键名风格

**Java 版本**：kebab-case（短横线分隔）
```yaml
max-connections-per-ip: 1000000
max-message-size: 8192
```

**Go 版本**：snake_case（下划线分隔）
```yaml
max_connections_per_ip: 1000000
max_message_size: 8192
```

### 5. 布尔值表示

两个版本都支持：
```yaml
enabled: true
enabled: false
```

---

## 📝 配置迁移步骤

### 第一步：准备 Nacos

1. **登录 Nacos 控制台**
   ```
   地址：http://your-nacos-server:8848/nacos
   ```

2. **创建命名空间**（如果还没有）
   - 命名空间 ID：`test`
   - 命名空间名：`测试环境`

3. **创建配置**
   - Data ID：`im-connect-go.yaml`
   - Group：`DEFAULT_GROUP`
   - 配置格式：`YAML`

### 第二步：迁移配置

1. **复制模板**
   ```bash
   # 复制生成的 Nacos 配置模板
   cat configs/nacos-test-env.yaml
   ```

2. **替换占位符**
   - 将 `${global.host_131}` 替换为实际 IP
   - 修改密码等敏感信息

3. **粘贴到 Nacos**
   - 在 Nacos 控制台粘贴配置内容
   - 点击"发布"

### 第三步：启动应用

```bash
# 方式1：指定环境（自动加载 configs/bootstrap-test.yaml）
./im-connect-go --env=test

# 方式2：指定 Nacos 命名空间
./im-connect-go --namespace=test

# 方式3：指定配置文件
./im-connect-go --config=configs/bootstrap-test.yaml
```

### 第四步：验证配置

```bash
# 检查日志确认配置加载成功
tail -f logs/im-connect-go.log | grep "config loaded"

# 检查服务端口
lsof -i :10001  # WebSocket 端口
lsof -i :10000  # Prometheus 端口
lsof -i :9091   # gRPC 端口
```

---

## 🎯 配置加载优先级

Go 版本的配置加载顺序（高到低）：

1. **命令行参数**
   ```bash
   --namespace=prod
   ```

2. **环境变量**
   ```bash
   NACOS_NAMESPACE=prod
   ```

3. **Nacos 远程配置**
   ```yaml
   # 从 Nacos 加载的业务配置
   ```

4. **本地 Bootstrap 文件**
   ```yaml
   # configs/bootstrap-test.yaml
   ```

5. **默认值**
   ```go
   // 代码中的默认值
   ```

---

## ⚙️ 配置项详细说明

### Redis 连接池

**Java 版本**（Apache Commons Pool）：
```yaml
pool:
  max-active: 8      # 最大活跃连接
  max-idle: 8        # 最大空闲连接
  min-idle: 0        # 最小空闲连接
```

**Go 版本**（go-redis）：
```yaml
redis:
  pool_size: 8           # 连接池大小（相当于 max-active）
  min_idle_conns: 0      # 最小空闲连接
  # Go 的 go-redis 会自动管理连接池
```

**差异说明**：
- Go 的 `go-redis` 库连接池管理更智能
- 不需要配置 `max-idle`，会自动优化
- `pool_size` 相当于 Java 的 `max-active`

### 心跳机制

**Java 版本**：
```yaml
heartBeatTime: 45               # 心跳超时时间
maxHeartbeatFailures: 3         # 最大失败次数
activeHeartbeatInterval: 25     # 主动心跳间隔
```

**Go 版本**：
```yaml
netty:
  heartbeat_timeout: 45          # 心跳超时时间
  max_heartbeat_failures: 3      # 最大失败次数
  ping_interval: 25s             # Ping 间隔（主动心跳）
  pong_timeout: 10s              # Pong 超时
```

**计算公式**：
- 总超时时间 = `heartbeat_timeout` × `max_heartbeat_failures`
- 示例：45秒 × 3次 = 135秒（2分15秒后断线）

### 流量控制

**Java 版本**：
```yaml
flow-control:
  max-messages-per-second: 100000
  max-message-size: 8192000
  max-bytes-per-second: 102400000
```

**Go 版本**（相同）：
```yaml
flow_control:
  max_messages_per_second: 100000    # 单用户每秒最大消息数
  max_message_size: 8192000          # 单条消息最大字节数（8MB）
  max_bytes_per_second: 102400000    # 单用户每秒最大流量（100MB）
```

---

## 🚀 性能优化建议

### 根据环境调整配置

#### 开发环境（dev）
```yaml
# 宽松配置，方便调试
security:
  max_connections_per_ip: 100
flow_control:
  max_messages_per_second: 1000
logging:
  level: "debug"
```

#### 测试环境（test）
```yaml
# 接近生产配置
security:
  max_connections_per_ip: 10000
flow_control:
  max_messages_per_second: 50000
logging:
  level: "info"
```

#### 生产环境（prod）
```yaml
# 严格配置
security:
  max_connections_per_ip: 1000
flow_control:
  max_messages_per_second: 10000
logging:
  level: "warn"
auth:
  enabled: true  # 生产环境必须开启认证
```

---

## 📚 相关文档

- [Nacos 配置指南](../NACOS_SETUP_GUIDE.md)
- [配置架构说明](../configs/ARCHITECTURE.md)
- [快速启动指南](../QUICK_START.md)
- [启动参数说明](../STARTUP_GUIDE.md)

---

## 🛠️ 故障排查

### 配置未生效

**检查清单**：
1. ✅ Nacos 配置已发布？
2. ✅ 命名空间正确？
3. ✅ Data ID 匹配？（`im-connect-go.yaml`）
4. ✅ Group 正确？（`DEFAULT_GROUP`）
5. ✅ 应用启动时指定了正确的 `--env` 或 `--namespace`？

**调试方法**：
```bash
# 1. 查看实际加载的配置
curl http://localhost:10000/debug/config

# 2. 检查 Nacos 连接
curl "http://your-nacos:8848/nacos/v1/cs/configs?dataId=im-connect-go.yaml&group=DEFAULT_GROUP&tenant=test"

# 3. 查看应用日志
tail -f logs/im-connect-go.log | grep -i "nacos"
```

### Redis 连接失败

**常见原因**：
1. 地址格式错误
   - ❌ 错误：`addr: ${global.host_131}` + `port: 6379`
   - ✅ 正确：`addr: "${global.host_131}:6379"`

2. 占位符未替换
   - ❌ 错误：`addr: "${global.host_131}:6379"`（Nacos 不支持此占位符）
   - ✅ 正确：`addr: "192.168.1.131:6379"`

3. 密码包含特殊字符
   - ✅ 使用引号：`password: "wcnim@bilp740."`

---

## 💡 最佳实践

1. **敏感信息管理**
   - 生产环境密码使用 Nacos 配置加密功能
   - 不要在代码仓库中提交真实密码

2. **配置分层**
   - Bootstrap 文件：只配置 Nacos 连接信息
   - Nacos 远程配置：所有业务配置

3. **配置版本管理**
   - 在 Nacos 中为每次配置变更添加备注
   - 重要变更前做好配置备份

4. **动态配置热更新**
   - Redis 地址、密码等支持热更新（无需重启）
   - 端口号等需要重启才能生效

5. **环境隔离**
   - dev/test/prod 使用不同的 Nacos 命名空间
   - 避免配置相互影响

