#!/bin/bash


# 批量上传本地文件到 虚拟机 以便镜像构建以及 容器运行

# 定义本地和远程目录前缀
local_base_dir="/Users/hzz/myself_project/im-开源04/xzll-im-parent"
remote_base_dir="/usr/local/soft_hzz/xzll-im/jar-file/docker-compose-way"

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
  "$local_base_dir/prometheus.yml:/usr/local/soft_hzz/docker/prometheus/conf/"

  # 上传 jmx_exporter.yaml 文件 (此文件用于定义jmx采集规则) 使用prometheus后 jmx采集这种方式就弃用了。
  # "$local_base_dir/jmx_exporter.yaml:/usr/local/soft_hzz/docker/jmx/"
)

# 远程服务器的用户名和主机名
remote_user="root"
remote_host="192.168.1.103" # $XUNIJI_ADDRESS
remote_password="$REMOTE_PASSWORD"

# 日志文件
log_file="upload_log.txt"

# 遍历数组并执行上传
for file_pair in "${files[@]}"; do
  file="${file_pair%%:*}"
  remote_dir="${file_pair##*:}"

  echo "确保远程目录存在: $remote_dir" | tee -a "$log_file"
  sshpass -p "$remote_password" ssh ${remote_user}@${remote_host} "mkdir -p $remote_dir"

  echo "上传文件 $file 到 $remote_dir" | tee -a "$log_file"
  if sshpass -p "$remote_password" scp -r "$file" ${remote_user}@${remote_host}:"$remote_dir"; then
    echo "上传成功: $file" | tee -a "$log_file"
  else
    echo "上传失败: $file" | tee -a "$log_file"
  fi
  echo "" | tee -a "$log_file"
done

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