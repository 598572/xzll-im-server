# 快速开始指南

## 🚀 5 分钟上手

### 1. 前置条件

确保你已经安装：
- ✅ Java 17+
- ✅ Maven 3.6+

检查版本：
```bash
java -version   # 应显示 17 或更高
mvn -version    # 应显示 3.6 或更高
```

### 2. 编译项目

```bash
cd im-stress-gatling
mvn clean compile
```

### 3. 运行压测（推荐方式）

#### 方式 A：使用脚本（最简单）

```bash
# 赋予执行权限
chmod +x run-stress-test.sh

# 默认配置：1 万用户，2 分钟启动，测试 10 分钟
./run-stress-test.sh

# 自定义配置：5 万用户，5 分钟启动，测试 30 分钟
./run-stress-test.sh 50000 300 1800

# 指定服务器
TARGET_HOST=okim.site TARGET_PORT=80 ./run-stress-test.sh
```

#### 方式 B：使用 Maven 命令

```bash
# 默认配置
mvn gatling:test

# 自定义配置
mvn gatling:test \
  -DTARGET_HOST=120.46.85.43 \
  -DTARGET_PORT=80 \
  -DUSERS_PER_MACHINE=10000 \
  -DRAMP_UP_TIME=120 \
  -DTEST_DURATION=600
```

### 4. 查看报告

压测完成后，Gatling 会自动生成 HTML 报告并尝试打开浏览器。

如果没有自动打开，手动查找：
```bash
# 查找最新报告
find target/gatling -name "index.html" -type f | head -n 1

# macOS 打开
open target/gatling/imwebsocketsimulation-<timestamp>/index.html

# Linux 打开
xdg-open target/gatling/imwebsocketsimulation-<timestamp>/index.html
```

## 📊 压测场景说明

### 默认场景

1. **连接阶段**（2 分钟）
   - 逐步启动 10000 个虚拟用户
   - 每个用户建立一个 WebSocket 连接

2. **测试阶段**（10 分钟）
   - 每个用户每 30 秒发送一次心跳（Ping）
   - 每个用户每 5 秒发送一次单聊消息
   - 接收服务端推送的消息

3. **关闭阶段**
   - 所有用户优雅关闭连接

### 预期指标

| 指标 | 预期值 | 说明 |
|------|--------|------|
| 连接成功率 | > 95% | 低于 95% 说明服务器有问题 |
| P95 响应时间 | < 500ms | 95% 的消息在 500ms 内送达 |
| 消息 TPS | 2000+ | 每秒发送 2000 条消息 |
| 连接稳定性 | 无异常断开 | 10 分钟内连接保持稳定 |

## ⚠️ 常见问题

### Q1: 编译失败 - 找不到 im-common

**错误信息**：
```
[ERROR] Failed to execute goal on project im-stress-gatling: 
Could not resolve dependencies for project com.xzll:im-stress-gatling:jar:1.0.0: 
Could not find artifact com.xzll:im-common:jar:0.0.1-SNAPSHOT
```

**解决方案**：
先编译父项目：
```bash
cd ..  # 回到项目根目录
mvn clean install -DskipTests
cd im-stress-gatling
mvn clean compile
```

### Q2: 连接失败 - 403 Forbidden

**错误信息**：
```
WebSocket handshake failed: 403 Forbidden
```

**原因**：Token 认证失败。

**解决方案 A**：在服务端添加压测后门

编辑 `im-connect/im-connect-service/src/main/java/com/xzll/connect/netty/handler/AuthHandler.java`：

```java
private boolean performAuthentication(ChannelHandlerContext ctx, FullHttpRequest request, String clientIp) {
    HttpHeaders headers = request.headers();
    String token = headers.get(ImConstant.TOKEN);
    
    // 【新增】压测后门：如果是压测 Token，直接通过
    if ("stress_test_token_bypass".equals(token)) {
        String uid = headers.get("uid");
        if (StringUtils.isNotBlank(uid)) {
            ctx.channel().attr(ImConstant.USER_ID_KEY).setIfAbsent(uid);
            log.info("压测用户认证通过：uid={}", uid);
            return true;
        }
    }
    
    // 正常认证流程...
}
```

然后重启 im-connect 服务，并设置压测 Token：
```bash
export TEST_TOKEN="stress_test_token_bypass"
./run-stress-test.sh
```

**解决方案 B**：使用有效的 JWT Token

如果不想修改服务端代码，可以使用你现有的有效 Token：
```bash
export TEST_TOKEN="你的有效JWT"
./run-stress-test.sh
```

### Q3: 压测机 CPU 100%，但用户数不到 1 万

**原因**：压测机性能不足。

**解决方案**：
- 降低单机用户数：`./run-stress-test.sh 5000`
- 或者使用多台压测机（分布式）

### Q4: 报告中看不到消息 TPS

**原因**：Gatling 默认只统计 HTTP 请求，WebSocket 消息需要在服务端统计。

**解决方案**：
在服务端添加 Prometheus 监控，统计消息收发量。

## 🎯 进阶使用

### 分布式压测（10 万+ 用户）

创建 `machines.txt`：
```
192.168.1.101
192.168.1.102
192.168.1.103
```

创建分布式启动脚本 `run-distributed.sh`：
```bash
#!/bin/bash

MACHINES=($(cat machines.txt))
USERS_PER_MACHINE=50000
TARGET_HOST="120.46.85.43"

for i in "${!MACHINES[@]}"; do
  machine="${MACHINES[$i]}"
  echo "启动机器 $i: $machine"
  
  ssh user@$machine "
    cd /path/to/im-stress-gatling && \
    export MACHINE_ID=$i && \
    export USERS_PER_MACHINE=$USERS_PER_MACHINE && \
    export TARGET_HOST=$TARGET_HOST && \
    nohup ./run-stress-test.sh > stress_$i.log 2>&1 &
  " &
done

wait
echo "所有压测任务已启动"
```

### 自定义测试场景

编辑 `src/test/scala/com/xzll/stress/ImWebSocketSimulation.scala`，修改：
- 心跳间隔（默认 30 秒）
- 消息发送间隔（默认 5 秒）
- 消息内容
- 负载模型（渐进式、阶梯式、恒定式）

## 📚 下一步

- 📖 阅读 [README.md](README.md) 了解详细配置
- 📊 学习如何解读 Gatling 报告
- 🔧 配置服务端监控（Prometheus + Grafana）
- 🚀 尝试分布式压测

---

**祝压测顺利！** 如有问题，请查看 [README.md](README.md) 或提 Issue。

