package grpc

import (
	"context"
	"fmt"
	"net"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	pb "im-connect-go/internal/proto"
	"im-connect-go/internal/strategy"

	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/grpc/keepalive"
	"google.golang.org/grpc/reflection"
)

// GrpcServer gRPC 服务器（对标 Java MessageServiceGrpcImpl）
// 功能：跨服务器消息转发、好友请求推送等
type GrpcServer struct {
	config         *config.Config
	logger         *zap.Logger
	server         *grpc.Server
	channelManager *channel.NbioManager
	c2cStrategy    *strategy.C2CMsgSendStrategy
	serviceImpl    *MessageServiceImpl
	listener       net.Listener
}

// NewGrpcServer 创建 gRPC 服务器
func NewGrpcServer(cfg *config.Config, logger *zap.Logger) (*GrpcServer, error) {
	// 创建监听器
	addr := fmt.Sprintf(":%d", cfg.GRPC.Port)
	listener, err := net.Listen("tcp", addr)
	if err != nil {
		return nil, fmt.Errorf("创建 gRPC 监听器失败: %w", err)
	}

	// gRPC 服务器配置（对标 Java gRPC 配置）
	keepaliveParams := keepalive.ServerParameters{
		MaxConnectionIdle:     cfg.GRPC.MaxConnectionIdle,
		MaxConnectionAge:      cfg.GRPC.MaxConnectionAge,
		MaxConnectionAgeGrace: cfg.GRPC.MaxConnectionAgeGrace,
		Time:                  30 * time.Second,
		Timeout:               5 * time.Second,
	}

	keepalivePolicy := keepalive.EnforcementPolicy{
		MinTime:             5 * time.Second,
		PermitWithoutStream: true,
	}

	// 创建 gRPC 服务器
	server := grpc.NewServer(
		grpc.MaxRecvMsgSize(cfg.GRPC.MaxRecvMsgSize),
		grpc.MaxSendMsgSize(cfg.GRPC.MaxSendMsgSize),
		grpc.ConnectionTimeout(cfg.GRPC.ConnectionTimeout),
		grpc.KeepaliveParams(keepaliveParams),
		grpc.KeepaliveEnforcementPolicy(keepalivePolicy),
	)

	grpcServer := &GrpcServer{
		config:   cfg,
		logger:   logger,
		server:   server,
		listener: listener,
	}

	logger.Info("✅ gRPC 服务器创建成功",
		zap.String("address", addr),
		zap.Int("max_recv_msg_size_mb", cfg.GRPC.MaxRecvMsgSize/1024/1024),
		zap.Int("max_send_msg_size_mb", cfg.GRPC.MaxSendMsgSize/1024/1024),
	)

	return grpcServer, nil
}

// SetDependencies 设置依赖（连接管理器和C2C策略）
func (s *GrpcServer) SetDependencies(channelManager *channel.NbioManager, c2cStrategy *strategy.C2CMsgSendStrategy) {
	s.channelManager = channelManager
	s.c2cStrategy = c2cStrategy

	// 创建并注册 gRPC 服务实现
	s.serviceImpl = NewMessageServiceImpl(s.logger, channelManager, c2cStrategy)
	pb.RegisterMessageServiceServer(s.server, s.serviceImpl)

	s.logger.Info("✅ gRPC 服务实现已注册")
}

// Start 启动 gRPC 服务器
func (s *GrpcServer) Start(ctx context.Context) error {
	// 注册反射服务（用于调试）
	reflection.Register(s.server)

	s.logger.Info("🚀 启动 gRPC 服务器",
		zap.String("address", s.listener.Addr().String()),
	)

	// 在单独的协程中处理关闭信号
	go func() {
		<-ctx.Done()
		s.logger.Info("📨 接收到关闭信号，开始关闭 gRPC 服务器")
		s.Shutdown()
	}()

	// 启动服务器（阻塞）
	if err := s.server.Serve(s.listener); err != nil {
		return fmt.Errorf("gRPC 服务器启动失败: %w", err)
	}

	return nil
}

// Shutdown 关闭 gRPC 服务器
func (s *GrpcServer) Shutdown() {
	if s.server != nil {
		s.server.GracefulStop()
		s.logger.Info("✅ gRPC 服务器已关闭")
	}
}
