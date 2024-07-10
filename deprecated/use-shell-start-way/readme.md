# 此目录，收录通过shell脚本启动项目的相关内容，使用jenkins后， shell脚本启动的方式被弃用，所以移到此处来

### 部署步骤
1. mvn clean package -P prod 
1. 执行此脚本 ./upload_files_and_exec_docker_compose.sh --reset 填入目标服务器的ip和密码
1. 此脚本upload_files_and_exec_docker_compose.sh 将上传打好的jar文件以及Dockerfile和相关shell以及环境变量到虚拟机的指定目录，具体见脚本详情
