pipeline {
    agent any

    parameters {
        choice(
            name: 'GIT_BRANCH',
            choices: ['main', 'dev01', 'jenkins_build_20240709', 'docker_compose_way_bak','group_chat_dev_20240623'],
            defaultValue: 'jenkins_build_20240709',
            description: '选择Git分支' // 选择Git分支
        )
    }

    environment {
        COMPOSE_DIR = "/usr/local/soft_hzz/xzll-im/jar-file/jenkins_way_build_docker_compose/" // Docker Compose目录
        GIT_REPO = "git@github.com:598572/xzll-im.git" // Git仓库地址
        GIT_BRANCH = "${params.GIT_BRANCH}" // 选择的Git分支
    }

    stages {
        stage('Prepare Environment') {
            steps {
                script {
                    log("开始部署") // 日志记录

                    if (!fileExists(COMPOSE_DIR)) {
                        log("目录不存在，创建目录 $COMPOSE_DIR")
                        sh "mkdir -p $COMPOSE_DIR" // 创建Docker Compose目录
                        exit_on_error("Failed to create directory $COMPOSE_DIR")
                    }

                    log("进入到 docker-compose 目录中")
                    dir(COMPOSE_DIR) {} // 进入Docker Compose目录
                }
            }
        }

        stage('Clone or Update Repo') {
            steps {
                script {
                    dir(COMPOSE_DIR) {
                        if (!fileExists('xzll-im')) {
                            log("项目目录不存在，执行 git clone")
                            sh "git clone $GIT_REPO" // 克隆Git仓库
                            exit_on_error("Git clone failed")
                        } else {
                            log("项目目录已存在，进入目录并执行 git pull")
                            dir('xzll-im') {
                                sh "git pull" // 更新Git仓库
                                exit_on_error("Git pull failed")
                            }
                        }
                    }
                }
            }
        }

        stage('Checkout Branch') {
            steps {
                script {
                    dir("$COMPOSE_DIR/xzll-im") {
                        log("进入项目根目录")
                        sh "git fetch" // 拉取最新的分支信息
                        sh "git checkout $GIT_BRANCH" // 切换到选择的分支
                        exit_on_error("Failed to checkout branch")
                    }
                }
            }
        }

        stage('Build Project') {
            steps {
                script {
                    dir("$COMPOSE_DIR/xzll-im") {
                        log("开始打包")
                        sh "mvn clean package -P test" // 使用Maven打包项目
                        exit_on_error("Maven build failed")
                        log("打包完成")
                    }
                }
            }
        }

        stage('Copy JAR Files') {
            steps {
                script {
                    def jarFiles = [
                        'im-gateway/target/im-gateway.jar': 'im-gateway/src/main/resources',
                        'im-connect/im-connect-service/target/im-connect-service.jar': 'im-connect/im-connect-service/src/main/resources',
                        'im-auth/target/im-auth.jar': 'im-auth/src/main/resources',
                        'im-business/im-business-service/target/im-business-service.jar': 'im-business/im-business-service/src/main/resources',
                        'im-console/im-console-service/target/im-console-service.jar': 'im-console/im-console-service/src/main/resources'
                    ]

                    dir("$COMPOSE_DIR/xzll-im") {
                        jarFiles.each { jar, targetDir ->
                            log("复制 $jar 到 $targetDir")
                            sh "cp $jar $targetDir" // 复制JAR文件到相应目录
                            exit_on_error("Failed to copy $jar to $targetDir")
                        }
                        sh "cp docker-compose.yml ../" // 复制docker-compose.yml文件
                    }
                }
            }
        }

        stage('Deploy with Docker Compose') {
            steps {
                script {
                    dir(COMPOSE_DIR) {
                        log("返回到 docker-compose 目录")
                        sh "docker-compose down" // 停止并移除Docker容器
                        exit_on_error("Docker-compose down failed")

                        log("构建镜像并启动容器")
                        sh "docker-compose up -d --build" // 构建镜像并启动容器
                        exit_on_error("Docker-compose up failed")
                    }
                }
            }
        }

        stage('Clean Up') {
            steps {
                script {
                    log("清理 Docker 相关的垃圾")
                    sh "docker system prune -f --volumes" // 清理Docker垃圾
                    exit_on_error("Docker system prune failed")
                    log("清理完成")
                }
            }
        }
    }

    post {
        always {
            script {
                log("部署结束") // 部署结束的日志
            }
        }
        success {
            script {
                log("构建成功") // 构建成功的日志
            }
            emailext (
                subject: "Jenkins 构建成功通知: ${currentBuild.fullDisplayName}",
                body: """Jenkins 构建成功通知:
                        项目: ${env.JOB_NAME}
                        构建编号: ${env.BUILD_NUMBER}
                        构建URL: ${env.BUILD_URL}""",
                to: 'h163361631@163.com' // 构建成功发送邮件通知
            )
        }
        failure {
            script {
                log("构建失败") // 构建失败的日志
            }
            emailext (
                subject: "Jenkins 构建失败通知: ${currentBuild.fullDisplayName}",
                body: """Jenkins 构建失败通知:
                        项目: ${env.JOB_NAME}
                        构建编号: ${env.BUILD_NUMBER}
                        构建URL: ${env.BUILD_URL}""",
                to: 'h163361631@163.com' // 构建失败发送邮件通知
            )
        }
    }
}

def log(message) {
    echo "[${new Date().format('yyyy-MM-dd HH:mm:ss')}] ${message}" // 日志记录函数
}

def exit_on_error(message) {
    if (currentBuild.result == 'FAILURE') {
         error(message) // 构建失败时退出并记录错误信息
    }
}