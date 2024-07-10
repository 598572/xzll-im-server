#!/bin/bash

# 批量上传本地文件到 虚拟机 以便镜像构建以及 容器运行

# 定义本地和远程目录前缀
local_base_dir="/Users/hzz/myself_project/im-开源04/xzll-im-parent"
remote_base_dir="/usr/local/soft_hzz/xzll-im/jar-file/docker-compose-way"

# docker-compose构建过程中依赖的中间件存放地址
remote_dependency_framework="/usr/local/soft_hzz/docker"


 # 函数：删除本地环境变量
delete_local_env_vars() {
  unset REMOTE_IP
  unset REMOTE_PASSWORD
  sed -i '' '/export REMOTE_IP=/d' ~/.zshrc
  sed -i '' '/export REMOTE_PASSWORD=/d' ~/.zshrc
  source ~/.zshrc
  echo "已删除本地的 REMOTE_IP 和 REMOTE_PASSWORD 环境变量。"
}

# 检查是否需要删除当前的环境变量
if [ "$1" == "--reset" ]; then
  delete_local_env_vars
fi

# 检查本地是否已设置远程服务器的 IP 地址和密码
if [ -z "$REMOTE_IP" ] || [ -z "$REMOTE_PASSWORD" ]; then
  echo "未找到远程服务器的 IP 地址和密码。"
  read -p "请输入远程服务器的 IP 地址: " input_ip
  read -s -p "请输入远程服务器的密码: " input_password
  echo

  if [ -z "$input_ip" ] || [ -z "$input_password" ]; then
    echo "IP 地址或密码未输入，退出脚本。"
    exit 1
  else
    export REMOTE_IP=$input_ip
    export REMOTE_PASSWORD=$input_password

    # 将远程 IP 和密码保存到本地环境变量
    echo "export REMOTE_IP=$input_ip" >> ~/.zshrc
    echo "export REMOTE_PASSWORD=$input_password" >> ~/.zshrc
    source ~/.zshrc
  fi
else
  echo "使用已设置的远程服务器 IP 地址和密码。"
fi

# 远程服务器的用户名和主机名
remote_user="root"
remote_host=${REMOTE_IP}
remote_password=${REMOTE_PASSWORD}

# 打印远程服务器的 IP 地址和用户名
echo "远程服务器的 IP 地址是: ${REMOTE_IP}"
echo "远程服务器的用户名是: ${remote_user}"

source ~/.zshrc

# 定义要上传的文件和目标目录
files=(
  # 上传jar
  "$local_base_dir/im-gateway/target/im-gateway.jar:$remote_base_dir/im-gateway/"
  "$local_base_dir/im-auth/target/im-auth.jar:$remote_base_dir/im-auth/"
  "$local_base_dir/im-business/im-business-service/target/im-business-service.jar:$remote_base_dir/im-business/"
  "$local_base_dir/im-connect/im-connect-service/target/im-connect-service.jar:$remote_base_dir/im-connect/"
  "$local_base_dir/im-console/im-console-service/target/im-console-service.jar:$remote_base_dir/im-console/"

  # 上传Dockerfile
  "$local_base_dir/im-gateway/src/main/resources/Dockerfile:$remote_base_dir/im-gateway"
  "$local_base_dir/im-auth/src/main/resources/Dockerfile:$remote_base_dir/im-auth/"
  "$local_base_dir/im-business/im-business-service/src/main/resources/Dockerfile:$remote_base_dir/im-business/"
  "$local_base_dir/im-connect/im-connect-service/src/main/resources/Dockerfile:$remote_base_dir/im-connect/"
  "$local_base_dir/im-console/im-console-service/src/main/resources/Dockerfile:$remote_base_dir/im-console/"

  # 上传docker-compose.yml 文件
  "$local_base_dir/docker-compose.yml:$remote_base_dir/"

  # 上传 prometheus.yml 文件
  "$local_base_dir/prometheus.yml:$remote_dependency_framework/prometheus/conf/"

  # 上传 jmx_exporter.yaml 文件 (此文件用于定义jmx采集规则) 使用prometheus后 jmx采集这种方式就弃用了。
  # "$local_base_dir/jmx_exporter.yaml:/usr/local/soft_hzz/docker/jmx/"
)

# 日志文件
log_file="upload_log.txt"

# 遍历数组并执行上传
for file_pair in "${files[@]}"; do
  file="${file_pair%%:*}"
  remote_dir="${file_pair##*:}"

  echo "确保远程目录存在: $remote_dir" | tee -a "$log_file"
  sshpass -p "$remote_password" ssh -o StrictHostKeyChecking=no ${remote_user}@${remote_host} "mkdir -p $remote_dir"

  sshpass -p "$remote_password" ssh -o StrictHostKeyChecking=no ${remote_user}@${remote_host} "export LOCAL_IP=${remote_host}"
  # 打印 LOCAL_IP 环境变量
  echo "已将 REMOTE_IP 设置为 LOCAL_IP: $LOCAL_IP"
  echo "上传文件 $file 到 $remote_dir" | tee -a "$log_file"
  if sshpass -p "$remote_password" scp -o StrictHostKeyChecking=no -r "$file" ${remote_user}@${remote_host}:"$remote_dir"; then
    echo "上传成功: $file" | tee -a "$log_file"
  else
    echo "上传失败: $file" | tee -a "$log_file"
  fi
  echo "" | tee -a "$log_file"
done

# 获取当前脚本的目录
script_dir=$(cd "$(dirname "$0")" && pwd)

# 执行第二个脚本来构建RocketMQ Exporter镜像
sshpass -p "$remote_password" ssh -o StrictHostKeyChecking=no ${remote_user}@${remote_host} "bash -s" < "${script_dir}/rocketmq_exporter_build_image.sh"


# 执行docker-compose命令并列出所有容器
read -p "是否执行Docker命令？ (y/n): " user_input

if [[ "$user_input" == "y" ]]; then
    sshpass -p "$remote_password" ssh ${remote_user}@${remote_host} <<EOF | tee -a "$log_file"
cd /usr/local/soft_hzz/xzll-im/jar-file/docker-compose-way/
docker-compose down
docker-compose up -d --build
docker ps -a
EOF
else
    echo "操作已取消。"
fi