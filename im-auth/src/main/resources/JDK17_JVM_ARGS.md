# im-auth JDK 17 启动配置说明

## 📋 问题描述

在 JDK 17 环境下启动 `im-auth` 服务时，会遇到以下错误：

```
java.lang.NullPointerException: Cannot invoke "java.lang.reflect.Method.invoke(Object, Object[])" 
because "com.sun.xml.bind.v2.runtime.reflect.opt.Injector.defineClass" is null
```

**原因**：JDK 17 移除了对部分内部 API 的访问权限，而 Spring Security OAuth2 依赖的 JAXB 需要访问这些 API。

## ✅ 解决方案

### 方式 1: IDEA 启动配置（推荐开发环境）

1. 打开 IDEA，点击右上角的 `Run/Debug Configurations`
2. 选择 `IMAuthServiceApplication`
3. 在 `VM options` 中添加以下参数：

```bash
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens java.base/java.time=ALL-UNNAMED
--add-opens java.base/java.security=ALL-UNNAMED
--add-opens java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED
--add-exports java.base/sun.security.action=ALL-UNNAMED
```

4. 点击 `Apply` 和 `OK`
5. 重新启动服务

### 方式 2: Maven 启动（推荐）

使用 Maven 命令启动，JVM 参数已配置在 `pom.xml` 中：

```bash
cd im-auth
mvn spring-boot:run
```

**注意**：`pom.xml` 中已经配置了必要的 JVM 参数：

```xml
<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
    <configuration>
        <mainClass>com.xzll.auth.IMAuthServiceApplication</mainClass>
        <layout>ZIP</layout>
        <!-- JDK 17 兼容性 JVM 参数 -->
        <jvmArguments>
            --add-opens java.base/java.nio=ALL-UNNAMED
            --add-opens java.base/sun.nio.ch=ALL-UNNAMED
            --add-opens java.base/java.lang=ALL-UNNAMED
            --add-opens java.base/java.lang.reflect=ALL-UNNAMED
            --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
            --add-opens java.base/java.time=ALL-UNNAMED
            --add-opens java.base/java.security=ALL-UNNAMED
            --add-opens java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED
            --add-opens java.xml/javax.xml.bind=ALL-UNNAMED
            --add-opens java.xml/com.sun.xml.bind=ALL-UNNAMED
            --add-opens java.xml/com.sun.xml.bind.v2=ALL-UNNAMED
            --add-opens java.xml/com.sun.xml.bind.v2.runtime=ALL-UNNAMED
            --add-opens java.xml/com.sun.xml.bind.v2.runtime.reflect=ALL-UNNAMED
            --add-opens java.xml/com.sun.xml.bind.v2.runtime.reflect.opt=ALL-UNNAMED
        </jvmArguments>
    </configuration>
</plugin>
```

### 方式 3: JAR 包启动（生产环境）

打包后使用 `java -jar` 启动：

```bash
# 1. 打包
cd im-auth
mvn clean package -DskipTests

# 2. 启动（添加JVM参数）
java \
  --add-opens java.base/java.nio=ALL-UNNAMED \
  --add-opens java.base/sun.nio.ch=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.lang.reflect=ALL-UNNAMED \
  --add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-opens java.base/java.time=ALL-UNNAMED \
  --add-opens java.base/java.security=ALL-UNNAMED \
  --add-opens java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED \
  --add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
  --add-exports java.base/sun.security.action=ALL-UNNAMED \
  -jar target/im-auth.jar
```

### 方式 4: 启动脚本（推荐生产环境）

创建启动脚本 `start-im-auth.sh`：

```bash
#!/bin/bash

# JDK 17 JVM 参数
JVM_ARGS="
--add-opens java.base/java.nio=ALL-UNNAMED
--add-opens java.base/sun.nio.ch=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.lang.reflect=ALL-UNNAMED
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens java.base/java.time=ALL-UNNAMED
--add-opens java.base/java.security=ALL-UNNAMED
--add-opens java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED
--add-exports java.base/sun.security.action=ALL-UNNAMED
"

# 内存配置
MEMORY_OPTS="-Xms512m -Xmx1024m -XX:MetaspaceSize=256m -XX:MaxMetaspaceSize=512m"

# GC 配置（JDK 17 推荐使用 G1GC）
GC_OPTS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 日志配置
LOG_OPTS="-Dlogging.config=classpath:log4j2.xml"

# 启动
java $JVM_ARGS $MEMORY_OPTS $GC_OPTS $LOG_OPTS -jar im-auth.jar

echo "im-auth started"
```

使用方式：
```bash
chmod +x start-im-auth.sh
./start-im-auth.sh
```

### 方式 5: Docker 启动

`Dockerfile` 示例：

```dockerfile
FROM openjdk:17-jdk-slim

WORKDIR /app

COPY target/im-auth.jar app.jar

# 设置环境变量
ENV JAVA_OPTS="\
--add-opens java.base/java.nio=ALL-UNNAMED \
--add-opens java.base/sun.nio.ch=ALL-UNNAMED \
--add-opens java.base/java.lang=ALL-UNNAMED \
--add-opens java.base/java.lang.reflect=ALL-UNNAMED \
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED \
--add-opens java.base/java.time=ALL-UNNAMED \
--add-opens java.base/java.security=ALL-UNNAMED \
--add-opens java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED \
--add-exports java.base/jdk.internal.misc=ALL-UNNAMED \
--add-exports java.base/sun.security.action=ALL-UNNAMED \
-Xms512m -Xmx1024m"

EXPOSE 8084

ENTRYPOINT exec java $JAVA_OPTS -jar app.jar
```

## 📝 JVM 参数说明

| 参数 | 说明 | 必需性 |
|------|------|--------|
| `--add-opens java.base/java.nio=ALL-UNNAMED` | 开放 NIO 模块 | ✅ 必需 |
| `--add-opens java.base/java.lang=ALL-UNNAMED` | 开放语言核心模块 | ✅ 必需 |
| `--add-opens java.base/java.lang.reflect=ALL-UNNAMED` | 开放反射模块 | ✅ 必需 |
| `--add-opens java.base/jdk.internal.misc=ALL-UNNAMED` | 开放内部工具类 | ✅ 必需 |
| `--add-opens java.xml/com.sun.org.apache.xerces.internal.jaxp=ALL-UNNAMED` | 开放 XML 解析模块 | ✅ 必需（JAXB） |
| `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED` | 导出内部API | ⚠️ 推荐 |
| `--add-exports java.base/sun.security.action=ALL-UNNAMED` | 导出安全API | ⚠️ 推荐 |

## 🔍 验证启动是否成功

启动后，检查以下内容：

### 1. 查看日志
```bash
tail -f logs/im-auth.log
```

### 2. 检查端口
```bash
# im-auth 默认端口 8084
netstat -an | grep 8084
# 或
lsof -i:8084
```

### 3. 健康检查
```bash
curl http://localhost:8084/actuator/health
```

预期响应：
```json
{
  "status": "UP"
}
```

### 4. 测试 OAuth2 端点
```bash
# 获取 Token
curl -X POST http://localhost:8084/oauth/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=password&username=test&password=123456&client_id=im-client&client_secret=im-secret"
```

## ⚠️ 常见问题

### Q1: 启动时仍然报同样的错误？

**A**: 检查是否真的添加了 JVM 参数：
```bash
# 查看进程的 JVM 参数
jps -v | grep IMAuth
```

如果输出中没有 `--add-opens` 参数，说明参数没有生效。

### Q2: 参数是否有顺序要求？

**A**: 没有严格的顺序要求，但建议：
- `--add-opens` 和 `--add-exports` 参数放在最前面
- 内存参数（`-Xms`, `-Xmx`）放在中间
- `-jar` 参数必须在最后

### Q3: 为什么使用 `ALL-UNNAMED`？

**A**: 
- `ALL-UNNAMED` 表示允许所有未命名模块访问
- 这是最简单的解决方案，适合开发和中小型项目
- 如果需要更严格的模块控制，可以指定具体的模块名

### Q4: 这些参数会影响性能吗？

**A**: 
- ❌ 不会影响性能
- ✅ 只是开放了模块访问权限
- ✅ 运行时性能与 JDK 8/11 基本一致

### Q5: 可以降级到 JDK 8/11 吗？

**A**: 
- ✅ 可以，项目支持 JDK 8/11/17
- 如果使用 JDK 8/11，不需要这些参数
- 但推荐使用 JDK 17（性能更好，长期支持）

## 📚 相关文档

- [JDK 17 模块系统说明](https://openjdk.org/jeps/403)
- [Spring Boot 2.7 + JDK 17 兼容性](https://spring.io/blog/2021/09/02/spring-boot-2-5-x-and-java-17)
- [JAXB 迁移指南](https://docs.oracle.com/en/java/javase/17/migrate/migrating-jdk-8-later-jdk-releases.html)

## 📅 更新日期

- **2025-10-29**: 初始版本，添加 JDK 17 启动配置说明

---

**提示**：如果还有问题，请查看 `pom.xml` 中的 JVM 参数配置是否完整。

