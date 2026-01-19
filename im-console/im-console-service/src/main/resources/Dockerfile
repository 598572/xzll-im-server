# business 的  Dockerfile 内容
# jdk镜像
FROM eclipse-temurin:21-jdk
# 挂载
VOLUME /tmp
COPY im-console-service.jar im-console-service.jar
# 启动命令 - 添加JDK21模块开放参数
ENTRYPOINT ["java", \
    "--add-opens", "java.base/java.nio=ALL-UNNAMED", \
    "--add-opens", "java.base/java.lang=ALL-UNNAMED", \
    "--add-opens", "java.base/java.util=ALL-UNNAMED", \
    "--add-opens", "java.base/java.lang.reflect=ALL-UNNAMED", \
    "--add-opens", "java.base/java.text=ALL-UNNAMED", \
    "--add-opens", "java.desktop/java.awt.font=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.annotation=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.util=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.helpers=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.annotation.adapters=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.attachment=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.spi=ALL-UNNAMED", \
    "-jar", "/im-console-service.jar"]
# 暴露端口 与服务端口保持一致
EXPOSE 8084