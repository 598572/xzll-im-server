# connect的  Dockerfile 内容
# jdk镜像
FROM openjdk:11-jre-slim
# 挂载
VOLUME /tmp
COPY im-connect-service.jar im-connect-service.jar
# 启动命令
ENTRYPOINT ["java", "-jar", "/im-connect-service.jar"]

# 暴露netty端口
EXPOSE 10001
# 暴露 promethues指标采集端口
EXPOSE 10000