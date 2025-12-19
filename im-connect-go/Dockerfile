# ============================================================================
# IM-Connect-Go Dockerfile - 百万级连接优化版
# ============================================================================
# 
# 构建命令：
#   docker build -t im-connect-go:latest .
#
# 运行命令：
#   docker run -p 10001:10001 -p 9090:9090 \
#     -e SERVER_HOST=0.0.0.0 \
#     -e REDIS_ADDRESS=redis:6379 \
#     -e NACOS_SERVER_ADDR=nacos:8848 \
#     im-connect-go:latest
#
# ============================================================================

# 第一阶段：构建
FROM golang:1.21-alpine AS builder

# 设置工作目录
WORKDIR /app

# 安装必要的工具
RUN apk add --no-cache git protoc protobuf-dev

# 复制 go mod 文件
COPY go.mod go.sum ./

# 下载依赖
RUN go mod download

# 复制源代码
COPY . .

# 安装 protoc-gen-go
RUN go install google.golang.org/protobuf/cmd/protoc-gen-go@latest

# 生成 protobuf 代码（如果需要）
RUN export PATH=$PATH:$(go env GOPATH)/bin && \
    protoc --go_out=./internal --go_opt=paths=source_relative proto/message_service.proto

# 构建应用
RUN CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build \
    -ldflags="-w -s -X 'main.buildTime=$(date -u +%Y-%m-%dT%H:%M:%SZ)'" \
    -o im-connect-go ./cmd/main.go

# 第二阶段：运行时镜像
FROM alpine:3.18

# 安装运行时依赖
RUN apk add --no-cache \
    ca-certificates \
    tzdata \
    curl \
    && rm -rf /var/cache/apk/*

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

# 创建非root用户
RUN addgroup -g 1000 -S imuser && \
    adduser -u 1000 -S imuser -G imuser

# 创建必要的目录
RUN mkdir -p /app/logs /app/config /tmp/nacos/log /tmp/nacos/cache && \
    chown -R imuser:imuser /app /tmp/nacos

# 设置工作目录
WORKDIR /app

# 从构建阶段复制二进制文件
COPY --from=builder /app/im-connect-go .
COPY --from=builder /app/configs/ ./configs/

# 复制启动脚本
COPY --from=builder /app/deployments/ ./deployments/

# 设置文件权限
RUN chmod +x im-connect-go && \
    chmod +x deployments/*.sh 2>/dev/null || true

# 切换到非root用户
USER imuser

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:10001/health || exit 1

# 暴露端口
EXPOSE 10001 9090

# 设置默认环境变量
ENV SERVER_HOST=0.0.0.0 \
    SERVER_PORT=10001 \
    GRPC_PORT=9090 \
    LOG_LEVEL=info \
    GOMAXPROCS=0

# 启动命令
CMD ["./im-connect-go"]

# ============================================================================
# 镜像标签和元数据
# ============================================================================
LABEL maintainer="IM-Connect-Go Team" \
      version="1.0.0" \
      description="高性能 IM 长连接服务 (Go版本)" \
      org.opencontainers.image.title="IM-Connect-Go" \
      org.opencontainers.image.description="支持百万级连接的高性能 IM 长连接服务" \
      org.opencontainers.image.version="1.0.0" \
      org.opencontainers.image.vendor="xzll-im" \
      org.opencontainers.image.licenses="Apache-2.0"
