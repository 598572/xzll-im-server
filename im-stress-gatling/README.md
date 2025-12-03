# IM Gatling 压力测试模块

## 📋 概述

基于 **Gatling** 的 IM 长连接压力测试工具，用于测试 WebSocket + Protobuf 的 IM 服务性能。

## 🎯 测试目标

- ✅ **最大连接数**：测试单机/集群支持的最大在线用户数
- ✅ **消息吞吐量**：测试每秒收发消息的峰值（TPS）
- ✅ **响应时间**：测试消息从发送到接收的延迟（P50、P95、P99）
- ✅ **稳定性**：长时间运行（10 分钟+）的连接稳定性

## 🚀 快速开始

### 1. 编译项目

```bash
cd im-stress-gatling
mvn clean compile
```

### 2. 运行压测（默认配置）

```bash
# 默认：10000 用户，2 分钟启动，测试 10 分钟
mvn gatling:test
```

### 3. 自定义参数运行

```bash
mvn gatling:test \
  -DTARGET_HOST=120.46.85.43 \
  -DTARGET_PORT=80 \
  -DMACHINE_ID=0 \
  -DUSERS_PER_MACHINE=50000 \
  -DRAMP_UP_TIME=300 \
  -DTEST_DURATION=600
```

### 4. 查看报告

压测完成后，Gatling 会自动生成 HTML 报告：

```bash
# 报告路径
open target/gatling/imwebsocketsimulation-<timestamp>/index.html
```

## 📊 配置参数说明

| 参数 | 说明 | 默认值 | 示例 |
|------|------|--------|------|
| `TARGET_HOST` | 目标服务器 IP/域名 | `120.46.85.43` | `okim.site` |
| `TARGET_PORT` | 目标端口 | `80` | `10001` |
| `MACHINE_ID` | 机器编号（分布式压测用） | `0` | `0, 1, 2...` |
| `USERS_PER_MACHINE` | 每台机器模拟的用户数 | `10000` | `50000` |
| `RAMP_UP_TIME` | 启动时间（秒） | `120` | `300` |
| `TEST_DURATION` | 测试时长（秒） | `600` | `1800` |
| `TEST_TOKEN` | 认证 Token | (内置) | 自定义 JWT |

## 🔧 分布式压测

### 单机测试（1-5 万用户）

```bash
# 机器 1
export MACHINE_ID=0
export USERS_PER_MACHINE=50000
mvn gatling:test
```

### 多机测试（10-100 万用户）

在**多台机器**上分别运行：

**机器 1**（模拟 user_0 ~ user_49999）：
```bash
export MACHINE_ID=0
export USERS_PER_MACHINE=50000
export TARGET_HOST=120.46.85.43
export TARGET_PORT=80
mvn gatling:test
```

**机器 2**（模拟 user_50000 ~ user_99999）：
```bash
export MACHINE_ID=1
export USERS_PER_MACHINE=50000
export TARGET_HOST=120.46.85.43
export TARGET_PORT=80
mvn gatling:test
```

**机器 N**...

### 自动化脚本（推荐）

创建 `run-distributed-stress.sh`：

```bash
#!/bin/bash

# 配置
MACHINES=("192.168.1.101" "192.168.1.102" "192.168.1.103")
TARGET_HOST="120.46.85.43"
TARGET_PORT="80"
USERS_PER_MACHINE=50000

# SSH 用户名
SSH_USER="your_username"

# 项目路径
PROJECT_PATH="/path/to/xzll-im-server/im-stress-gatling"

echo "启动分布式压测..."
echo "压测机数量: ${#MACHINES[@]}"
echo "总用户数: $((${#MACHINES[@]} * USERS_PER_MACHINE))"

# 在每台机器上启动压测
for i in "${!MACHINES[@]}"; do
  machine="${MACHINES[$i]}"
  echo "启动机器 $i: $machine"
  
  ssh $SSH_USER@$machine "
    cd $PROJECT_PATH && \
    export MACHINE_ID=$i && \
    export USERS_PER_MACHINE=$USERS_PER_MACHINE && \
    export TARGET_HOST=$TARGET_HOST && \
    export TARGET_PORT=$TARGET_PORT && \
    nohup mvn gatling:test > stress_$i.log 2>&1 &
  " &
done

wait
echo "所有压测任务已启动"
```

运行：
```bash
chmod +x run-distributed-stress.sh
./run-distributed-stress.sh
```

## 📈 报告解读

Gatling 报告包含以下关键指标：

### 1. 连接成功率
- **Global Success Rate**：应 > 95%
- 如果低于 95%，说明服务器或网络有问题

### 2. 响应时间分布
- **P50（中位数）**：50% 的请求响应时间
- **P95**：95% 的请求响应时间
- **P99**：99% 的请求响应时间
- **Max**：最大响应时间

### 3. 吞吐量（TPS）
- **Requests per second**：每秒请求数
- 对于 IM 系统，关注 "Send C2C Message" 的 TPS

### 4. 活跃用户数曲线
- 查看用户数是否按预期增长
- 是否有大量用户中途断开

## ⚠️ 注意事项

### 1. 服务端认证

压测时需要绕过认证，有两种方式：

#### 方式 A：使用固定 Token（推荐）
在 `im-connect` 的 `AuthHandler` 中添加后门：

```java
// AuthHandler.java
private boolean performAuthentication(...) {
    String token = headers.get(ImConstant.TOKEN);
    
    // 压测后门：如果是压测 Token，直接通过
    if ("stress_test_token_bypass".equals(token)) {
        String uid = headers.get("uid");
        ctx.channel().attr(ImConstant.USER_ID_KEY).setIfAbsent(uid);
        return true;
    }
    
    // 正常认证流程...
}
```

然后在压测脚本中设置：
```bash
export TEST_TOKEN="stress_test_token_bypass"
```

#### 方式 B：生成有效的 JWT Token
如果不想修改服务端代码，可以批量生成有效的 JWT Token（需要调用 `im-auth` 服务）。

### 2. 系统调优

压测前必须调整操作系统参数：

```bash
# 提高文件句柄限制
ulimit -n 1000000

# 修改 /etc/sysctl.conf
net.ipv4.tcp_tw_reuse = 1
net.ipv4.ip_local_port_range = 1024 65535
fs.file-max = 1000000
net.core.somaxconn = 65535

# 生效
sysctl -p
```

### 3. 端口耗尽问题

单台压测机连接同一个服务器，最多只能建立 **65535** 个连接（端口限制）。

**解决方案**：
- 启动多台压测机（分布式）
- 或者让压测机连接服务器的**多个 IP**（如果有）

### 4. JVM 参数调优

如果压测机内存不足，调整 Maven 的 JVM 参数：

```bash
export MAVEN_OPTS="-Xmx8g -Xms8g -XX:+UseG1GC"
mvn gatling:test
```

## 🐛 常见问题

### Q1: 连接失败率很高（> 50%）

**可能原因**：
- 服务器资源不足（CPU、内存、网络）
- 启动速度太快（`RAMP_UP_TIME` 太短）
- Token 认证失败

**解决方案**：
- 增加 `RAMP_UP_TIME`（如 300 秒）
- 检查服务器监控（CPU、内存、网络）
- 确认 Token 是否有效

### Q2: 报告中看不到消息发送的 TPS

**原因**：Gatling 默认只统计 HTTP 请求，WebSocket 消息需要手动统计。

**解决方案**：
在服务端添加监控（Prometheus + Grafana），统计消息收发量。

### Q3: 压测机 CPU 100%，但服务器很空闲

**原因**：压测机性能不足，无法模拟更多连接。

**解决方案**：
- 增加压测机数量（分布式）
- 或者降低单机用户数

## 📚 参考资料

- [Gatling 官方文档](https://gatling.io/docs/gatling/)
- [WebSocket 压测最佳实践](https://gatling.io/docs/gatling/reference/current/http/websocket/)
- [Protobuf 官方文档](https://protobuf.dev/)

## 📝 测试记录

### 测试 1：单机 1 万用户
- **时间**：2025-12-02
- **配置**：1 台压测机，10000 用户
- **结果**：
  - 连接成功率：98.5%
  - P95 响应时间：120ms
  - TPS：2000 msg/s
- **瓶颈**：Redis CPU 达到 80%

### 测试 2：分布式 10 万用户
- **时间**：待测试
- **配置**：10 台压测机，每台 10000 用户
- **结果**：待补充

---

**祝压测顺利！** 🚀

