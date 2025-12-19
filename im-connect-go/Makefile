# IM-Connect-Go Makefile

# 变量定义
BINARY_NAME=im-connect-go
BUILD_DIR=.
CMD_DIR=cmd
CONFIGS_DIR=configs
LOGS_DIR=logs

# Go 相关变量
GO=go
GOFLAGS=-v
LDFLAGS=-ldflags "-X main.Version=$(shell git describe --tags --always --dirty 2>/dev/null || echo 'dev') -X main.BuildTime=$(shell date -u '+%Y-%m-%d_%H:%M:%S')"

# 颜色定义
GREEN=\033[0;32m
YELLOW=\033[1;33m
BLUE=\033[0;34m
RED=\033[0;31m
NC=\033[0m # No Color

.PHONY: all build clean run run-dev run-test run-prod help test lint

# 默认目标
all: clean build

# 编译
build:
	@echo "$(BLUE)开始编译 $(BINARY_NAME)...$(NC)"
	@$(GO) build $(GOFLAGS) $(LDFLAGS) -o $(BUILD_DIR)/$(BINARY_NAME) $(CMD_DIR)/main.go
	@echo "$(GREEN)✅ 编译完成: $(BUILD_DIR)/$(BINARY_NAME)$(NC)"

# 编译（不显示详细信息）
build-quiet:
	@$(GO) build $(LDFLAGS) -o $(BUILD_DIR)/$(BINARY_NAME) $(CMD_DIR)/main.go

# 清理
clean:
	@echo "$(YELLOW)清理编译文件...$(NC)"
	@rm -f $(BUILD_DIR)/$(BINARY_NAME)
	@echo "$(GREEN)✅ 清理完成$(NC)"

# 运行（默认开发环境）
run: build
	@./$(BINARY_NAME) --env=dev

# 运行开发环境
run-dev: build
	@echo "$(BLUE)启动开发环境...$(NC)"
	@./$(BINARY_NAME) --env=dev

# 运行测试环境
run-test: build
	@echo "$(BLUE)启动测试环境...$(NC)"
	@./$(BINARY_NAME) --env=test

# 运行生产环境
run-prod: build
	@echo "$(BLUE)启动生产环境...$(NC)"
	@./$(BINARY_NAME) --env=prod

# 使用脚本启动开发环境
start-dev:
	@./scripts/start.sh dev

# 使用脚本启动测试环境
start-test:
	@./scripts/start.sh test

# 使用脚本启动生产环境
start-prod:
	@./scripts/start.sh prod

# 后台运行开发环境
daemon-dev: build
	@echo "$(BLUE)后台启动开发环境...$(NC)"
	@mkdir -p $(LOGS_DIR)
	@nohup ./$(BINARY_NAME) --env=dev > $(LOGS_DIR)/$(BINARY_NAME)-dev.log 2>&1 &
	@echo "$(GREEN)✅ 服务已在后台启动，日志: $(LOGS_DIR)/$(BINARY_NAME)-dev.log$(NC)"

# 后台运行生产环境
daemon-prod: build
	@echo "$(BLUE)后台启动生产环境...$(NC)"
	@mkdir -p $(LOGS_DIR)
	@nohup ./$(BINARY_NAME) --env=prod > $(LOGS_DIR)/$(BINARY_NAME)-prod.log 2>&1 &
	@echo "$(GREEN)✅ 服务已在后台启动，日志: $(LOGS_DIR)/$(BINARY_NAME)-prod.log$(NC)"

# 停止服务
stop:
	@echo "$(YELLOW)停止服务...$(NC)"
	@pkill -f $(BINARY_NAME) || true
	@echo "$(GREEN)✅ 服务已停止$(NC)"

# 重启服务（开发环境）
restart-dev: stop daemon-dev

# 重启服务（生产环境）
restart-prod: stop daemon-prod

# 查看日志（开发环境）
logs-dev:
	@tail -f $(LOGS_DIR)/$(BINARY_NAME)-dev.log

# 查看日志（生产环境）
logs-prod:
	@tail -f $(LOGS_DIR)/$(BINARY_NAME)-prod.log

# 运行测试
test:
	@echo "$(BLUE)运行测试...$(NC)"
	@$(GO) test -v -race -cover ./...
	@echo "$(GREEN)✅ 测试完成$(NC)"

# 代码检查
lint:
	@echo "$(BLUE)运行代码检查...$(NC)"
	@$(GO) vet ./...
	@echo "$(GREEN)✅ 代码检查完成$(NC)"

# 格式化代码
fmt:
	@echo "$(BLUE)格式化代码...$(NC)"
	@$(GO) fmt ./...
	@echo "$(GREEN)✅ 代码格式化完成$(NC)"

# 安装依赖
deps:
	@echo "$(BLUE)安装依赖...$(NC)"
	@$(GO) mod download
	@$(GO) mod tidy
	@echo "$(GREEN)✅ 依赖安装完成$(NC)"

# 生成 protobuf 代码
proto:
	@echo "$(BLUE)生成 protobuf 代码...$(NC)"
	@protoc --go_out=. --go_opt=paths=source_relative \
		--go-grpc_out=. --go-grpc_opt=paths=source_relative \
		proto/message_service.proto
	@echo "$(GREEN)✅ Protobuf 代码生成完成$(NC)"

# 创建必要的目录
dirs:
	@mkdir -p $(LOGS_DIR)
	@mkdir -p $(CONFIGS_DIR)
	@echo "$(GREEN)✅ 目录创建完成$(NC)"

# Docker 构建
docker-build:
	@echo "$(BLUE)构建 Docker 镜像...$(NC)"
	@docker build -t im-connect-go:latest .
	@echo "$(GREEN)✅ Docker 镜像构建完成$(NC)"

# Docker 运行（开发环境）
docker-run-dev:
	@docker run -d --name im-connect-go-dev \
		-p 10001:10001 -p 9091:9091 \
		-e APP_ENV=dev \
		im-connect-go:latest
	@echo "$(GREEN)✅ Docker 容器已启动$(NC)"

# 查看版本
version:
	@./$(BINARY_NAME) --version

# 显示帮助
help:
	@echo "$(BLUE)IM-Connect-Go Makefile 使用说明$(NC)"
	@echo ""
	@echo "$(GREEN)编译相关:$(NC)"
	@echo "  make build          - 编译项目"
	@echo "  make clean          - 清理编译文件"
	@echo ""
	@echo "$(GREEN)运行相关:$(NC)"
	@echo "  make run            - 编译并运行（开发环境）"
	@echo "  make run-dev        - 编译并运行开发环境"
	@echo "  make run-test       - 编译并运行测试环境"
	@echo "  make run-prod       - 编译并运行生产环境"
	@echo ""
	@echo "$(GREEN)后台运行:$(NC)"
	@echo "  make daemon-dev     - 后台运行开发环境"
	@echo "  make daemon-prod    - 后台运行生产环境"
	@echo "  make stop           - 停止服务"
	@echo "  make restart-dev    - 重启开发环境"
	@echo "  make restart-prod   - 重启生产环境"
	@echo ""
	@echo "$(GREEN)脚本启动:$(NC)"
	@echo "  make start-dev      - 使用脚本启动开发环境"
	@echo "  make start-test     - 使用脚本启动测试环境"
	@echo "  make start-prod     - 使用脚本启动生产环境"
	@echo ""
	@echo "$(GREEN)日志查看:$(NC)"
	@echo "  make logs-dev       - 查看开发环境日志"
	@echo "  make logs-prod      - 查看生产环境日志"
	@echo ""
	@echo "$(GREEN)测试和检查:$(NC)"
	@echo "  make test           - 运行测试"
	@echo "  make lint           - 代码检查"
	@echo "  make fmt            - 格式化代码"
	@echo ""
	@echo "$(GREEN)其他:$(NC)"
	@echo "  make deps           - 安装依赖"
	@echo "  make proto          - 生成 protobuf 代码"
	@echo "  make version        - 查看版本信息"
	@echo "  make help           - 显示此帮助信息"
	@echo ""
	@echo "$(YELLOW)示例:$(NC)"
	@echo "  make build && ./im-connect-go --env=dev"
	@echo "  make run-prod"
	@echo "  make daemon-prod && make logs-prod"

