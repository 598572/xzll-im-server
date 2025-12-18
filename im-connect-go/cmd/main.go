package main

import (
	"context"
	"flag"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"sync"
	"syscall"
	"time"

	"im-connect-go/internal/config"
	"im-connect-go/internal/server"
	"im-connect-go/pkg/grpc"
	"im-connect-go/pkg/mq"
	"im-connect-go/pkg/nacos"
	"im-connect-go/pkg/redis"

	"go.uber.org/zap"
	"go.uber.org/zap/zapcore"
)

// 命令行参数
var (
	configFile  = flag.String("config", "", "配置文件路径，例如: --config=configs/bootstrap-prod.yaml")
	env         = flag.String("env", "", "运行环境，例如: --env=prod (会自动查找 configs/bootstrap-{env}.yaml)")
	namespace   = flag.String("namespace", "", "Nacos 命名空间，例如: --namespace=prod")
	showHelp    = flag.Bool("help", false, "显示帮助信息")
	showVersion = flag.Bool("version", false, "显示版本信息")
)

// IMConnectGoApplication Go 版本的 IM 长连接服务
// 功能对标 Java 版本的 im-connect-service，支持：
// 1. WebSocket 长连接管理（百万级连接）
// 2. Protobuf 消息处理（与 Java 版本兼容）
// 3. 用户认证和心跳检测
// 4. gRPC 跨服务器消息转发
// 5. Redis 用户状态管理
// 6. Nacos 配置中心集成
func main() {
	// 解析命令行参数
	flag.Parse()

	// 显示帮助信息
	if *showHelp {
		printHelp()
		return
	}

	// 显示版本信息
	if *showVersion {
		printVersion()
		return
	}

	// 初始化日志
	logger := initLogger()
	defer logger.Sync()

	logger.Info("🚀 启动 IM-Connect-Go 服务")
	logger.Info("📋 服务版本: v1.0.0")
	logger.Info("🏗️  构建时间: " + getBuildTime())

	// 打印启动参数
	if *configFile != "" {
		logger.Info("📁 配置文件", zap.String("config", *configFile))
	}
	if *env != "" {
		logger.Info("🌍 运行环境", zap.String("env", *env))
	}
	if *namespace != "" {
		logger.Info("📦 Nacos 命名空间", zap.String("namespace", *namespace))
	}

	// 创建上下文
	ctx, cancel := context.WithCancel(context.Background())
	defer cancel()

	// 初始化配置（支持命令行参数）
	cfg, err := config.LoadConfigWithOptions(&config.LoadOptions{
		ConfigFile: *configFile,
		Env:        *env,
		Namespace:  *namespace,
	})
	if err != nil {
		logger.Fatal("❌ 加载配置失败", zap.Error(err))
	}
	logger.Info("✅ 配置加载成功",
		zap.String("nacos_server", cfg.Nacos.ServerAddr),
		zap.String("nacos_namespace", cfg.Nacos.Namespace),
		zap.String("nacos_group", cfg.Nacos.Group),
	)

	// 初始化 Nacos 配置中心
	if err := nacos.InitNacosConfig(cfg, logger); err != nil {
		logger.Fatal("❌ 初始化 Nacos 失败", zap.Error(err))
	}
	logger.Info("✅ Nacos 配置中心初始化成功")

	// 等待 Nacos 配置加载完成（异步加载需要一点时间）
	time.Sleep(2 * time.Second)

	// 打印从 Nacos 加载的配置（调试用）
	logger.Info("📋 从 Nacos 加载的配置",
		zap.Int("server_port", cfg.Server.Port),
		zap.String("redis_address", cfg.Redis.Address),
		zap.String("rocketmq_address", cfg.RocketMQ.ServerAddr),
		zap.String("rocketmq_group", cfg.RocketMQ.Producer.GroupName),
	)

	// 初始化 Redis
	if err := redis.InitRedis(cfg); err != nil {
		logger.Fatal("❌ 初始化 Redis 失败", zap.Error(err))
	}
	logger.Info("✅ Redis 连接池初始化成功",
		zap.String("address", cfg.Redis.Address),
		zap.Int("db", cfg.Redis.DB),
	)

	// 初始化 RocketMQ 生产者
	mqConfig := &mq.Config{
		ServerAddr: cfg.RocketMQ.ServerAddr,
		Producer: mq.ProducerConfig{
			GroupName:      cfg.RocketMQ.Producer.GroupName,
			MaxMessageSize: cfg.RocketMQ.Producer.MaxMessageSize,
			SendTimeout:    cfg.RocketMQ.Producer.SendTimeout,
			RetryTimes:     cfg.RocketMQ.Producer.RetryTimes,
		},
	}
	mqProducer, err := mq.NewProducer(mqConfig, logger)
	if err != nil {
		logger.Fatal("❌ 初始化 RocketMQ 生产者失败", zap.Error(err))
	}
	logger.Info("✅ RocketMQ 生产者初始化成功")

	// 获取 Redis 客户端实例
	redisClient := redis.GetRedisClient()
	if redisClient == nil {
		logger.Fatal("❌ Redis 客户端未初始化")
	}

	// 初始化 WebSocket 服务器（传入 mqProducer 和 redisClient）
	wsServer, err := server.NewWebSocketServer(cfg, logger, mqProducer, redisClient)
	if err != nil {
		logger.Fatal("❌ 创建 WebSocket 服务器失败", zap.Error(err))
	}

	// 初始化 gRPC 服务器
	grpcServer, err := grpc.NewGrpcServer(cfg, logger)
	if err != nil {
		logger.Fatal("❌ 创建 gRPC 服务器失败", zap.Error(err))
	}

	// 启动所有服务
	var wg sync.WaitGroup

	// 启动 WebSocket 服务器
	wg.Add(1)
	go func() {
		defer wg.Done()
		logger.Info("🔗 启动 WebSocket 服务器",
			zap.String("address", fmt.Sprintf(":%d", cfg.Server.Port)))
		if err := wsServer.Start(ctx); err != nil && err != http.ErrServerClosed {
			logger.Fatal("❌ WebSocket 服务器启动失败", zap.Error(err))
		}
	}()

	// 启动 gRPC 服务器
	wg.Add(1)
	go func() {
		defer wg.Done()
		logger.Info("🔗 启动 gRPC 服务器",
			zap.String("address", fmt.Sprintf(":%d", cfg.GRPC.Port)))
		if err := grpcServer.Start(ctx); err != nil {
			logger.Fatal("❌ gRPC 服务器启动失败", zap.Error(err))
		}
	}()

	// 注册服务信息到 Redis（用于负载均衡）
	if err := registerServerInfo(cfg, logger); err != nil {
		logger.Warn("⚠️ 注册服务信息失败", zap.Error(err))
	} else {
		logger.Info("✅ 服务信息已注册到 Redis")
	}

	// 监听系统信号
	sigChan := make(chan os.Signal, 1)
	signal.Notify(sigChan, syscall.SIGINT, syscall.SIGTERM, syscall.SIGQUIT)

	logger.Info("🎉 IM-Connect-Go 服务启动完成")
	logger.Info("📈 支持功能: 百万级连接 | 高QPS消息 | 跨服务器转发")

	// 等待关闭信号
	sig := <-sigChan
	logger.Info("📨 接收到关闭信号", zap.String("signal", sig.String()))

	// 优雅关闭
	logger.Info("🔄 开始优雅关闭服务...")

	// 取消上下文，通知所有服务停止
	cancel()

	// 设置关闭超时
	shutdownCtx, shutdownCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer shutdownCancel()

	// 关闭 WebSocket 服务器
	if err := wsServer.Shutdown(shutdownCtx); err != nil {
		logger.Error("❌ WebSocket 服务器关闭失败", zap.Error(err))
	} else {
		logger.Info("✅ WebSocket 服务器已关闭")
	}

	// 关闭 gRPC 服务器
	grpcServer.Shutdown()
	logger.Info("✅ gRPC 服务器已关闭")

	// 关闭 RocketMQ 生产者
	if err := mqProducer.Stop(); err != nil {
		logger.Error("❌ RocketMQ 生产者关闭失败", zap.Error(err))
	} else {
		logger.Info("✅ RocketMQ 生产者已关闭")
	}

	// 关闭 Redis 连接
	redis.Close()
	logger.Info("✅ Redis 连接已关闭")

	// 等待所有协程结束
	wg.Wait()

	logger.Info("🎯 IM-Connect-Go 服务已完全关闭")
}

// initLogger 初始化日志系统
func initLogger() *zap.Logger {
	config := zap.NewProductionConfig()
	config.Level = zap.NewAtomicLevelAt(zap.InfoLevel)
	config.Development = false
	config.Encoding = "console"
	config.EncoderConfig.TimeKey = "timestamp"
	config.EncoderConfig.EncodeTime = func(t time.Time, enc zapcore.PrimitiveArrayEncoder) {
		enc.AppendString(t.Format("2006-01-02 15:04:05.000"))
	}

	logger, err := config.Build(
		zap.AddStacktrace(zap.ErrorLevel),
		zap.AddCaller(),
		zap.AddCallerSkip(0),
	)
	if err != nil {
		panic(fmt.Sprintf("初始化日志失败: %v", err))
	}

	return logger
}

// getBuildTime 获取构建时间
func getBuildTime() string {
	// 这个值会在编译时通过 ldflags 注入
	return "development"
}

// registerServerInfo 注册服务器信息到 Redis
func registerServerInfo(cfg *config.Config, logger *zap.Logger) error {
	serverInfo := map[string]interface{}{
		"ip":         cfg.Server.Host,
		"port":       cfg.Server.Port,
		"grpc_port":  cfg.GRPC.Port,
		"start_time": time.Now().Unix(),
		"version":    "1.0.0-go",
		"type":       "im-connect-go",
	}

	return redis.SetServerInfo(cfg.Server.Host, serverInfo)
}

// printHelp 打印帮助信息
func printHelp() {
	fmt.Println(`
╔══════════════════════════════════════════════════════════════════════════╗
║                    IM-Connect-Go 服务启动帮助                            ║
╚══════════════════════════════════════════════════════════════════════════╝

🚀 启动方式：

  方式 1: 指定环境（自动加载 configs/bootstrap-{env}.yaml）
    ./im-connect-go --env=dev
    ./im-connect-go --env=test
    ./im-connect-go --env=prod

  方式 2: 指定配置文件
    ./im-connect-go --config=configs/bootstrap.yaml
    ./im-connect-go --config=/etc/im/bootstrap-prod.yaml

  方式 3: 指定 Nacos 命名空间（覆盖配置文件中的值）
    ./im-connect-go --namespace=prod
    ./im-connect-go --config=configs/bootstrap.yaml --namespace=prod

  方式 4: 组合使用（优先级：命令行 > 配置文件）
    ./im-connect-go --env=prod --namespace=prod

📋 命令行参数：

  --env string          运行环境 (dev/test/pre/prod)
                        自动查找: configs/bootstrap-{env}.yaml

  --config string       配置文件路径
                        示例: --config=configs/bootstrap-prod.yaml

  --namespace string    Nacos 命名空间
                        覆盖配置文件中的 namespace 配置

  --version             显示版本信息
  --help                显示此帮助信息

🌍 环境配置文件示例：

  configs/
    ├── bootstrap.yaml           # 默认配置
    ├── bootstrap-dev.yaml       # 开发环境（namespace: dev）
    ├── bootstrap-test.yaml      # 测试环境（namespace: test）
    ├── bootstrap-pre.yaml       # 预发环境（namespace: pre）
    └── bootstrap-prod.yaml      # 生产环境（namespace: prod）

📦 优先级顺序：

  1. 命令行参数 (--namespace)
  2. 环境变量 (NACOS_NAMESPACE)
  3. 配置文件 (bootstrap.yaml)
  4. 默认值

💡 推荐用法：

  开发环境：./im-connect-go --env=dev
  测试环境：./im-connect-go --env=test
  生产环境：./im-connect-go --env=prod

════════════════════════════════════════════════════════════════════════════
`)
}

// printVersion 打印版本信息
func printVersion() {
	fmt.Printf(`
IM-Connect-Go 长连接服务
版本: v1.0.0
构建时间: %s
Go 版本: go1.25+
协议: WebSocket + Protobuf + gRPC
功能: 百万级连接 | 高QPS消息 | 跨服务器转发
`, getBuildTime())
}
