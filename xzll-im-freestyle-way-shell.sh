#!/bin/bash

# 使用freestyle方式启动xzll-im项目时的shell脚本,此脚本在 jenkins的  build steps处填写。

log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $1"
}

exit_on_error() {
  if [ $? -ne 0 ]; then
    log "$1"
    exit 1
  fi
}

log ">>>>>>>>>>>> 开始部署 >>>>>>>>>>>>>"


# 确保 docker-compose 目录存在 不存在则创建
COMPOSE_DIR="/usr/local/soft_hzz/xzll-im/jar-file/jenkins_way_build_docker_compose/"
if [ ! -d "$COMPOSE_DIR" ]; then
  log ">>>>>>>>>>>> 目录不存在，创建目录 $COMPOSE_DIR >>>>>>>>>>>>>"
  mkdir -p "$COMPOSE_DIR" || { log "Failed to create directory $COMPOSE_DIR"; exit 1; }
fi

# 进入 docker-compose 目录
log ">>>>>>>>>>>> 进入到 docker-compose 目录中 >>>>>>>>>>>>>"
cd "$COMPOSE_DIR" || { log "Failed to enter directory $COMPOSE_DIR"; exit 1; }



# 如果项目目录不存在，则执行 clone，否则执行 pull
if [ ! -d "xzll-im" ]; then
  log ">>>>>>>>>>>> 项目目录不存在，执行 git clone >>>>>>>>>>>>>"
  git clone git@github.com:598572/xzll-im.git || { log "Git clone failed"; exit 1; }
else
  log ">>>>>>>>>>>> 项目目录已存在，进入目录并执行 git pull >>>>>>>>>>>>>"
  cd xzll-im/ || { log "Failed to enter directory"; exit 1; }
  git pull || { log "Git pull failed"; exit 1; }
  cd ..
fi

# 进入项目根目录
log ">>>>>>>>>>>> 进入项目根目录 >>>>>>>>>>>>>"
cd xzll-im/ || { log "Failed to enter project directory"; exit 1; }

git fetch

# 测试自动部署5

git checkout jenkins_build_20240709

# 开始打包
log ">>>>>>>>>>>> 开始打包 >>>>>>>>>>>>>"

mvn clean package -P prod || { log "Maven build failed"; exit 1; }

log ">>>>>>>>>>>> 打包完成 >>>>>>>>>>>>>"

# 确保 Docker 构建上下文包含所有 JAR 文件
declare -A JAR_FILES=(
  ["im-gateway/target/im-gateway.jar"]="im-gateway/src/main/resources"
  ["im-connect/im-connect-service/target/im-connect-service.jar"]="im-connect/im-connect-service/src/main/resources"
  ["im-auth/target/im-auth.jar"]="im-auth/src/main/resources"
  ["im-business/im-business-service/target/im-business-service.jar"]="im-business/im-business-service/src/main/resources"
  ["im-console/im-console-service/target/im-console-service.jar"]="im-console/im-console-service/src/main/resources"
)

for JAR_FILE in "${!JAR_FILES[@]}"; do
  TARGET_DIR="${JAR_FILES[$JAR_FILE]}"
  log ">>>>>>>>>>>> 复制 $JAR_FILE 到 $TARGET_DIR >>>>>>>>>>>>>"
  cp "$JAR_FILE" "$TARGET_DIR" || { log "Failed to copy $JAR_FILE to $TARGET_DIR"; exit 1; }
done


cp docker-compose.yml ../

# 返回 docker-compose 目录
log ">>>>>>>>>>>> 返回到 docker-compose 目录 >>>>>>>>>>>>>"
cd .. || { log "Failed to return to docker-compose directory"; exit 1; }




# 停止所有 docker-compose 运行的容器
log ">>>>>>>>>>>> 停止所有 docker-compose 运行的容器 >>>>>>>>>>>>>"
docker-compose down || { log "Docker-compose down failed"; exit 1; }

# 构建镜像并启动容器
log ">>>>>>>>>>>> 构建镜像并启动容器 >>>>>>>>>>>>>"
docker-compose up -d --build || { log "Docker-compose up failed"; exit 1; }

log ">>>>>>>>>>>> 部署结束 >>>>>>>>>>>>>"

# 清理 Docker 相关的垃圾
log ">>>>>>>>>>>> 清理 Docker 相关的垃圾 >>>>>>>>>>>>>"
docker system prune -f --volumes || { log "Docker system prune failed"; exit 1; }

log ">>>>>>>>>>>> 清理完成 >>>>>>>>>>>>>"
