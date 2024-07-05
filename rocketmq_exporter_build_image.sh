#! /bin/bash

remote_dependency_framework="/usr/local/soft_hzz/docker"
# 定义 Maven 路径 此处需要指定远程机器的mvn路径 否则没发执行mvn命令
mvn_path="/usr/local/soft_hzz/maven/apache-maven-3.8.8/bin/mvn"


echo "使用的 Maven 路径是: $mvn_path"
echo "引用到的LOCAL_IP是: $LOCAL_IP"
echo "引用到的remote_dependency_framework是: $remote_dependency_framework"
# 检查 Maven 路径是否有效
if [ ! -x "$mvn_path" ]; then
  echo "Maven 路径无效或 Maven 未安装在指定路径: $mvn_path"
  exit 1
fi

# 检查镜像是否存在 不存在构建镜像 否则不构建
if [[ "$(docker images -q rocketmq-exporter 2> /dev/null)" == "" ]]; then
  echo "镜像 rocketmq-exporter 不存在，开始构建..."
  cd  ${remote_dependency_framework}
  sudo mkdir -p ${remote_dependency_framework}/rocketmq-exporter
  cd ${remote_dependency_framework}/rocketmq-exporter
  # 下载文件并保存到指定目录
  wget https://codeload.github.com/apache/rocketmq-exporter/zip/refs/tags/rocketmq-exporter-0.0.2 -O rocketmq-exporter-0.0.2.zip
  # 删除之前的解压目录
  rm -rf rocketmq-exporter-rocketmq-exporter-0.0.2
  # 解压文件
  unzip rocketmq-exporter-0.0.2.zip
  cd rocketmq-exporter-rocketmq-exporter-0.0.2

  # 修改 namesrvAddr
  sed -i "s/namesrvAddr:.*/namesrvAddr: ${LOCAL_IP}:9876/" src/main/resources/application.yml
  # 构建项目
  $mvn_path clean install
  # 复制文件 注意: 不复制的话会报错 因为他打镜像时找的是 rocketmq-exporter-0.0.2-SNAPSHOT-exec.jar ， 不是 rocketmq-exporter-0.0.2-exec.jar ！！！
  cp ./target/rocketmq-exporter-0.0.2-exec.jar ./src/main/docker/rocketmq-exporter-0.0.2-SNAPSHOT-exec.jar
  # 构建Docker镜像
  cd ./src/main/docker/
  docker build -t rocketmq-exporter .
  # 清理打包文件和下载的JAR文件
  cd ${remote_dependency_framework}/rocketmq-exporter
  rm rocketmq-exporter-0.0.2.zip
  cd rocketmq-exporter-rocketmq-exporter-0.0.2
  $mvn_path clean
else
  echo "镜像 rocketmq-exporter 已存在，跳过构建步骤。"
fi