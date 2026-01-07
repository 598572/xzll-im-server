# connect的 Dockerfile 内容（百万连接优化版）
# jdk镜像
FROM eclipse-temurin:21-jdk
# 挂载
VOLUME /tmp
COPY im-connect-service.jar im-connect-service.jar

# JVM 高性能参数（支持百万连接）
# 注意：这些是默认值，实际运行时通过 docker-compose 的 environment 覆盖
ENV JAVA_OPTS=""

# JDK21 模块开放参数（固定不变）
ENV JAVA_MODULE_OPTS="--add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED --add-opens java.base/java.lang.reflect=ALL-UNNAMED --add-opens java.base/java.text=ALL-UNNAMED --add-opens java.desktop/java.awt.font=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/jdk.internal.misc=ALL-UNNAMED"

# 启动命令（shell 格式，支持环境变量展开）
ENTRYPOINT exec java $JAVA_MODULE_OPTS $JAVA_OPTS -jar /im-connect-service.jar

# 暴露netty端口
EXPOSE 10001
# 暴露 promethues指标采集端口
EXPOSE 10000
