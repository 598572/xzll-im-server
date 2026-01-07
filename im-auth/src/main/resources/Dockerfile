# business 的  Dockerfile 内容
# jdk镜像
FROM eclipse-temurin:21-jdk
# 挂载
VOLUME /tmp

# 注意 这个源文件 必须是和Dockerfile在同一个目录下（你还不能 cp ../../target/im-auth.jar im-auth.jar 这样写），否则会找不到源文件。
# 解决办法就是在mvn clean package prod 后，将打好的jar copy到Dockerifile所在的这个目录
COPY im-auth.jar im-auth.jar

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
    "--add-opens", "java.xml/javax.xml.bind.util=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.helpers=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.annotation.adapters=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.attachment=ALL-UNNAMED", \
    "--add-opens", "java.xml/javax.xml.bind.spi=ALL-UNNAMED", \
    "-jar", "/im-auth.jar"]
# 暴露端口 与服务端口保持一致
EXPOSE 8082