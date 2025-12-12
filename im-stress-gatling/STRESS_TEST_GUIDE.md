# IM 长连接服务压测指南

## 🔴 你遇到的问题分析

### 问题 1：文件句柄不足（最严重）
```
打开的文件过多 (958 次错误)
```
**原因**：Gatling 客户端的 ulimit -n 限制太低（默认 256 或 1024）  
**影响**：无法建立足够多的 WebSocket 连接

### 问题 2：连接过早关闭（88% 错误）
```
Client issued binary frame but server has closed the WebSocket (61498 次，88.02%)
```
**原因**：
1. ❌ 心跳逻辑错误：`pace(30s)` + `pause(5s)` = 实际 35 秒才发一次心跳
2. ❌ 服务端 30 秒心跳超时，客户端发送间隔过长
3. ❌ 服务端资源不足，提前断开连接

### 问题 3：脚本逻辑错误
```scala
.during(testDuration) {
  pace(heartbeatIntervalMs.milliseconds)  // ❌ 阻塞整个循环
    .exec(ws("Send Ping Heartbeat"))
    .doIf(_ => enableMsgSend) {
      pause(msgIntervalMs.milliseconds)   // ❌ 又暂停，导致心跳间隔过长
```

**已修复**：✅ 改用独立的心跳和消息循环

---

## 🔧 已修复的内容

### 1. `run-stress-test.sh`
- ✅ 自动检查并提升 `ulimit -n`
- ✅ 根据用户数计算所需的文件句柄限制

### 2. `ImWebSocketSimulation.scala`
- ✅ 修复心跳和消息发送逻辑
- ✅ 心跳独立发送，不再被消息阻塞
- ✅ 消息在两次心跳之间均匀分布

---

## 🚀 渐进式压测步骤

### 第 1 步：小规模测试（100 连接）
验证基础功能是否正常

```bash
# 在客户端机器执行
cd im-stress-gatling

# 提升文件句柄限制
ulimit -n 65535

# 运行小规模测试
./run-stress-test.sh 100 30 120 5000 30000

# 参数说明：
# 100      - 100 个用户
# 30       - 30 秒内启动完成
# 120      - 测试 2 分钟
# 5000     - 每 5 秒发一条消息
# 30000    - 每 30 秒发一次心跳
```

**预期结果**：
- ✅ 成功率 > 95%
- ✅ 无 "打开的文件过多" 错误
- ✅ 无大量 "Premature close" 错误

---

### 第 2 步：中等规模测试（1000 连接）

```bash
ulimit -n 65535

./run-stress-test.sh 1000 60 300 5000 30000

# 参数说明：
# 1000     - 1000 个用户
# 60       - 1 分钟内启动完成
# 300      - 测试 5 分钟
# 5000     - 每 5 秒发一条消息
# 30000    - 每 30 秒发一次心跳
```

**预期结果**：
- ✅ 成功率 > 95%
- ✅ 消息 TPS 约：1000 × 1000ms / 5000ms = 200 条/秒

**如果失败**：
1. 检查服务端日志
2. 检查服务器资源占用（CPU、内存、网络）
3. 降低用户数重新测试

---

### 第 3 步：大规模测试（10000 连接）

```bash
ulimit -n 65535

./run-stress-test.sh 10000 120 600 5000 30000

# 参数说明：
# 10000    - 1 万个用户
# 120      - 2 分钟内启动完成
# 600      - 测试 10 分钟
# 5000     - 每 5 秒发一条消息
# 30000    - 每 30 秒发一次心跳
```

**预期结果**：
- ✅ 成功率 > 90%
- ✅ 消息 TPS 约：10000 × 1000ms / 5000ms = 2000 条/秒

---

### 第 4 步：极限测试（50000+ 连接）

#### 方案 A：单机极限（需要优化系统）

```bash
# ⚠️ 需要先优化系统参数
ulimit -n 100000

# 极限测试
./run-stress-test.sh 50000 300 600 10000 60000

# 参数说明：
# 50000    - 5 万个用户
# 300      - 5 分钟内启动完成
# 600      - 测试 10 分钟
# 10000    - 每 10 秒发一条消息（降低频率）
# 60000    - 每 60 秒发一次心跳（降低频率）
```

#### 方案 B：多机分布式测试（推荐）

**机器 1**：
```bash
MACHINE_ID=0 ./run-stress-test.sh 30000 300 600 5000 30000
```

**机器 2**：
```bash
MACHINE_ID=1 ./run-stress-test.sh 30000 300 600 5000 30000
```

**机器 3**：
```bash
MACHINE_ID=2 ./run-stress-test.sh 30000 300 600 5000 30000
```

**总用户数**：30000 × 3 = 90000 连接

---

## 📊 监控关键指标

### 客户端指标
```bash
# 查看 Gatling 报告
# 自动打开：压测完成后自动打开浏览器
# 手动打开：target/gatling/最新目录/index.html

关注指标：
- 成功率（Success Rate）> 95%
- 响应时间 P99 < 1000ms
- 错误类型分布
```

### 服务端指标

```bash
# 1. 查看连接数
docker exec im-connect netstat -an | grep :10001 | grep ESTABLISHED | wc -l

# 2. 查看 CPU 和内存
docker stats im-connect

# 3. 查看 GC 日志
docker logs im-connect | grep GC

# 4. 查看文件句柄占用
docker exec im-connect ls -l /proc/$(pidof java)/fd | wc -l
```

---

## 🛠️ 优化建议

### 如果出现 "打开的文件过多"

#### macOS
```bash
# 临时提升（当前会话）
ulimit -n 65535

# 永久提升（需要重启）
sudo launchctl limit maxfiles 65536 200000
```

#### Linux
```bash
# 临时提升
ulimit -n 100000

# 永久提升
sudo vi /etc/security/limits.conf
# 添加：
*  soft  nofile  100000
*  hard  nofile  100000

# 重新登录生效
```

### 如果出现 "Connection refused"
```bash
# 检查服务端是否正常
curl http://192.168.1.150:10001/health

# 检查防火墙
sudo firewall-cmd --list-all

# 检查 Docker 网络
docker network inspect bridge
```

### 如果出现大量 "Premature close"
1. **降低连接数**：从 10000 → 5000 → 1000 逐步测试
2. **延长启动时间**：RAMP_UP 从 120s → 300s
3. **降低消息频率**：MSG_INTERVAL 从 5000ms → 10000ms
4. **检查服务端日志**：`docker logs -f im-connect`

---

## 📈 预期性能目标

基于当前服务端配置（4G 堆 + ZGC + Netty 优化）：

| 指标 | 预期值 | 说明 |
|------|--------|------|
| **最大连接数** | 50,000 - 80,000 | 单机 |
| **消息 TPS** | 10,000 - 15,000 | 文本消息 |
| **GC 停顿** | < 10ms | ZGC 优势 |
| **P99 延迟** | < 100ms | 消息端到端 |
| **成功率** | > 95% | 稳定性指标 |

---

## 🎯 下一步行动

1. ✅ **立即执行**：从第 1 步（100 连接）开始测试
2. ✅ **逐步增加**：确认每一步都成功后再进行下一步
3. ✅ **记录数据**：记录每次测试的连接数、TPS、成功率
4. ✅ **找到瓶颈**：当出现性能问题时，分析是客户端还是服务端瓶颈

---

## 📝 问题排查清单

### 客户端检查
- [ ] ulimit -n 是否足够？（至少 用户数 + 10000）
- [ ] 是否有其他进程占用端口？
- [ ] 网络是否通畅？（ping、telnet）
- [ ] Java 版本是否 >= 11？

### 服务端检查
- [ ] 是否执行了 optimize-system.sh？
- [ ] Docker 容器是否正常运行？
- [ ] CPU 和内存是否充足？
- [ ] 是否有大量 GC？
- [ ] 网络带宽是否充足？

---

## 🆘 常见错误及解决

| 错误信息 | 原因 | 解决方案 |
|---------|------|---------|
| **打开的文件过多** | ulimit -n 太小 | `ulimit -n 100000` |
| **Premature close** | 服务端资源不足 | 降低用户数、优化服务端 |
| **Connection refused** | 服务端未启动 | 检查 `docker ps` |
| **Request timeout** | 网络延迟高 | 检查网络、降低用户数 |
| **Client issued binary frame but server has closed** | 心跳超时 | 已修复（新脚本） |

---

## 💡 Tips

1. **先小后大**：不要一开始就测 10 万连接
2. **记录数据**：每次测试记录结果，对比优化效果
3. **分布式测试**：单机无法突破时，使用多台机器
4. **监控优先**：先确保监控到位，再进行大规模测试
5. **逐步优化**：找到瓶颈 → 优化 → 再测试

---

Good luck! 🚀


