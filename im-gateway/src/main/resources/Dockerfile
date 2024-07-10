# business 的  Dockerfile 内容
# jdk镜像
FROM openjdk:11-jre-slim
# 挂载
VOLUME /tmp
COPY ../../../target/im-gateway.jar im-gateway.jar
# 启动命令
ENTRYPOINT ["java", "-jar", "/im-gateway.jar"]
# 暴露端口 与服务端口保持一致
EXPOSE 8081