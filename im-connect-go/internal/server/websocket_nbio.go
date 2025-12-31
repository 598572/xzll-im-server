package server

import (
	"context"
	"fmt"
	"net/http"
	"sync"
	"time"

	"im-connect-go/internal/auth"
	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	"im-connect-go/internal/handler"
	"im-connect-go/internal/metrics"
	pb "im-connect-go/internal/proto"
	"im-connect-go/internal/service"
	"im-connect-go/pkg/mq"
	"im-connect-go/pkg/redis"

	"github.com/lesismal/nbio/nbhttp"
	"github.com/lesismal/nbio/nbhttp/websocket"
	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// NbioWebSocketServer nbio 版本的 WebSocket 服务器
// 使用 nbio 的高性能网络库，原生支持 WebSocket，支持百万级连接
type NbioWebSocketServer struct {
	config            *config.Config
	logger            *zap.Logger
	server            *nbhttp.Server
	channelManager    *channel.NbioManager
	authHandler       *auth.Handler
	messageHandler    *handler.MessageHandler
	userStatusService *service.UserStatusService
	retryService      *service.C2CMsgRetryService // 消息重试服务

	// 连接映射：websocket.Conn -> userID
	connUserMap sync.Map // map[*websocket.Conn]string

	// 连接建立时间映射：userID -> time.Time
	connectTimeMap sync.Map // map[string]time.Time

	// 优化组件
	advancedHeartbeatHandler *handler.AdvancedHeartbeatHandler
	asyncMessageHandler      *handler.AsyncMessageHandler
	connectionStats          *metrics.ConnectionStats

	// 优雅关闭
	shutdown     chan struct{}
	shutdownOnce sync.Once
}

// NewNbioWebSocketServer 创建新的 nbio WebSocket 服务器
func NewNbioWebSocketServer(cfg *config.Config, logger *zap.Logger, mqProducer *mq.Producer, redisClient *redis.RedisClient) (*NbioWebSocketServer, error) {
	nettyConfig := cfg.GetNettyRuntimeConfig()

	// 创建连接管理器
	channelManager := channel.NewNbioManager(cfg, logger)

	// 创建认证处理器
	authHandler := auth.NewHandler(cfg, logger)

	// 创建消息重试服务（对标 Java C2CMsgRetryServiceImpl）
	retryConfig := service.DefaultC2CMsgRetryConfig()
	retryService := service.NewC2CMsgRetryService(retryConfig, redisClient, channelManager, logger)

	// 创建消息处理器（nbio 版本，传入 channelManager 和 retryService）
	messageHandler := handler.NewMessageHandler(cfg, logger, channelManager, mqProducer, redisClient, retryService)

	// 创建用户状态管理服务
	serverAddr := getServerAddress(cfg)
	userStatusService := service.NewUserStatusService(redisClient, logger, serverAddr)

	// 创建 HTTP 多路复用器
	mux := http.NewServeMux()

	server := &NbioWebSocketServer{
		config:            cfg,
		logger:            logger,
		channelManager:    channelManager,
		authHandler:       authHandler,
		messageHandler:    messageHandler,
		userStatusService: userStatusService,
		retryService:      retryService,
		shutdown:          make(chan struct{}),
	}

	// 设置 WebSocket 路由
	mux.HandleFunc("/websocket", server.handleWebSocket)

	// 创建 nbio HTTP 服务器
	nbServer := nbhttp.NewServer(nbhttp.Config{
		Network:            "tcp",
		Addrs:              []string{fmt.Sprintf(":%d", cfg.Server.Port)},
		MaxWriteBufferSize: nettyConfig.SocketBufferSize,
		ReadBufferSize:     nettyConfig.SocketBufferSize,
		NPoller:            nettyConfig.WorkerThreads,
		Handler:            mux,
	})

	server.server = nbServer

	// 初始化优化组件
	advancedHeartbeatHandler := handler.NewAdvancedHeartbeatHandler(cfg, logger, channelManager)
	asyncMessageHandler := handler.NewAsyncMessageHandler(cfg, logger, messageHandler, 10000, 16)
	connectionStats := metrics.NewConnectionStats()

	server.advancedHeartbeatHandler = advancedHeartbeatHandler
	server.asyncMessageHandler = asyncMessageHandler
	server.connectionStats = connectionStats

	logger.Info("✅ nbio WebSocket 服务器初始化完成",
		zap.String("address", fmt.Sprintf(":%d", cfg.Server.Port)),
		zap.Int("npoller", nettyConfig.WorkerThreads),
		zap.Int("max_write_buffer", nettyConfig.SocketBufferSize),
		zap.String("heartbeat_handler", "advanced"),
		zap.String("message_handler", "async"),
	)

	return server, nil
}

// handleWebSocket 处理 WebSocket 连接
func (s *NbioWebSocketServer) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	// 1. 认证检查
	userID, err := s.authHandler.Authenticate(r)
	if err != nil {
		s.logger.Warn("WebSocket 认证失败",
			zap.String("remote_addr", r.RemoteAddr),
			zap.Error(err),
		)
		http.Error(w, "认证失败", http.StatusUnauthorized)
		return
	}

	// 2. 连接数限制检查
	if !s.channelManager.CanAcceptConnection(userID) {
		s.logger.Warn("连接数超过限制",
			zap.String("user_id", userID),
			zap.String("remote_addr", r.RemoteAddr),
		)
		http.Error(w, "连接数超过限制", http.StatusTooManyRequests)
		return
	}

	// 3. 升级为 WebSocket 连接
	upgrader := websocket.NewUpgrader()

	// 设置连接属性
	nettyConfig := s.config.GetNettyRuntimeConfig()
	heartbeatTimeout := time.Duration(nettyConfig.HeartbeatTimeout) * time.Second

	// ✅ 设置 Ping 处理器（对标 Java IdleStateHandler 心跳处理）
	// Java 客户端使用 WebSocket Ping 帧作为心跳，需要在收到 Ping 时更新活跃度
	upgrader.SetPingHandler(func(c *websocket.Conn, message string) {
		// 更新读取超时
		c.SetReadDeadline(time.Now().Add(heartbeatTimeout))

		// 获取用户ID并更新心跳
		if userIDInterface, ok := s.connUserMap.Load(c); ok {
			userID := userIDInterface.(string)
			s.advancedHeartbeatHandler.OnRead(userID)
			s.logger.Debug("💓 收到 Ping 心跳",
				zap.String("user_id", userID),
			)
		}

		// 发送 Pong 响应
		c.WriteMessage(websocket.PongMessage, []byte(message))
	})

	// 设置 Pong 处理器（收到客户端的 Pong 响应时更新活跃度）
	upgrader.SetPongHandler(func(c *websocket.Conn, message string) {
		// 更新读取超时
		c.SetReadDeadline(time.Now().Add(heartbeatTimeout))

		// 获取用户ID并更新心跳
		if userIDInterface, ok := s.connUserMap.Load(c); ok {
			userID := userIDInterface.(string)
			s.advancedHeartbeatHandler.OnRead(userID)
			s.logger.Debug("💓 收到 Pong 响应",
				zap.String("user_id", userID),
			)
		}
	})

	// 设置 OnOpen 回调
	upgrader.OnOpen(func(c *websocket.Conn) {
		// 设置读取超时（心跳检测）
		c.SetReadDeadline(time.Now().Add(heartbeatTimeout))

		// 存储连接映射
		s.connUserMap.Store(c, userID)

		// 记录连接建立时间（对标 Java NettyAttrUtil.setConnectTime）
		connectTime := time.Now()
		s.connectTimeMap.Store(userID, connectTime)

		// ✅ 修复：添加连接到 userConnections 映射（对标 Java LocalChannelManager.addUserChannel）
		if err := s.channelManager.AddConnection(userID, c); err != nil {
			s.logger.Error("添加连接到管理器失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
			c.Close()
			return
		}

		// 注册连接包装器（用于消息发送）
		s.channelManager.RegisterConnection(nil, c, userID)

		// 设置用户在线状态到 Redis
		if err := s.userStatusService.UserConnectSuccessAfter(userID); err != nil {
			s.logger.Error("设置用户在线状态失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
			c.Close()
			return
		}

		// 记录连接建立统计
		s.connectionStats.OnConnect()

		// 初始化心跳记录
		s.advancedHeartbeatHandler.OnRead(userID)

		s.logger.Info("✅ WebSocket 连接建立",
			zap.String("user_id", userID),
			zap.String("remote_addr", c.RemoteAddr().String()),
			zap.Int64("total_connections", s.channelManager.GetConnectionCount()),
		)
	})

	// 设置 OnMessage 回调
	upgrader.OnMessage(func(c *websocket.Conn, messageType websocket.MessageType, data []byte) {
		// 更新心跳时间
		c.SetReadDeadline(time.Now().Add(heartbeatTimeout))

		// 获取用户ID
		userIDInterface, ok := s.connUserMap.Load(c)
		if !ok {
			return
		}
		currentUserID := userIDInterface.(string)

		// 更新活跃度（对标 Java IdleStateHandler）
		s.advancedHeartbeatHandler.OnRead(currentUserID)

		switch messageType {
		case websocket.BinaryMessage:
			// 处理二进制消息（Protobuf）- 使用异步处理（对标 Java ThreadPoolTaskExecutor）
			if connWrapper := s.channelManager.GetConnection(currentUserID, c); connWrapper != nil {
				if !s.asyncMessageHandler.Submit(currentUserID, connWrapper, data) {
					s.logger.Warn("⚠️ 消息处理队列满，消息被丢弃",
						zap.String("user_id", currentUserID),
						zap.Int("queue_length", s.asyncMessageHandler.GetQueueLength()),
					)
				} else {
					// 记录消息统计
					s.connectionStats.OnMessage(len(data))
				}
			}

		case websocket.TextMessage:
			s.logger.Debug("收到文本消息",
				zap.String("user_id", currentUserID),
				zap.String("message", string(data)),
			)

		case websocket.PingMessage:
			s.logger.Debug("收到 Ping 消息",
				zap.String("user_id", currentUserID),
			)
			// nbio websocket 会自动回复 Pong

		case websocket.PongMessage:
			s.logger.Debug("收到 Pong 消息",
				zap.String("user_id", currentUserID),
			)

		case websocket.CloseMessage:
			s.logger.Info("收到关闭消息",
				zap.String("user_id", currentUserID),
			)
		}
	})

	// 设置 OnClose 回调
	upgrader.OnClose(func(c *websocket.Conn, err error) {
		s.handleWebSocketClose(c, err)
	})

	// 执行升级
	conn, err := upgrader.Upgrade(w, r, nil)
	if err != nil {
		s.logger.Error("WebSocket 升级失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		return
	}

	// 连接已建立，回调会自动处理
	_ = conn
}

// handleWebSocketClose 处理 WebSocket 连接关闭
func (s *NbioWebSocketServer) handleWebSocketClose(c *websocket.Conn, err error) {
	// 获取用户ID
	userIDInterface, ok := s.connUserMap.LoadAndDelete(c)
	if !ok {
		s.logger.Debug("连接关闭（未找到用户映射）",
			zap.String("remote_addr", c.RemoteAddr().String()),
		)
		return
	}

	userID := userIDInterface.(string)

	// 计算连接时长（对标 Java 的连接时长统计）
	var connectionDuration time.Duration
	if connectTimeInterface, ok := s.connectTimeMap.LoadAndDelete(userID); ok {
		connectTime := connectTimeInterface.(time.Time)
		connectionDuration = time.Since(connectTime)
	}

	// 从连接管理器移除
	s.channelManager.RemoveConnection(userID, c)

	// 清除用户在线状态（仅当用户所有连接都关闭时）
	if !s.channelManager.IsUserOnline(userID) {
		if err := s.userStatusService.UserDisconnectAfter(userID); err != nil {
			s.logger.Error("清除用户在线状态失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
		}
	}

	// 记录断开统计
	s.connectionStats.OnDisconnect(connectionDuration)

	s.logger.Info("❌ WebSocket 连接关闭",
		zap.String("user_id", userID),
		zap.String("remote_addr", c.RemoteAddr().String()),
		zap.Duration("connection_duration", connectionDuration),
		zap.Error(err),
		zap.Int64("remaining_connections", s.channelManager.GetConnectionCount()),
	)
}

// Start 启动 WebSocket 服务器
func (s *NbioWebSocketServer) Start(ctx context.Context) error {
	// 启动消息重试服务（对标 Java @Scheduled 定时任务）
	if s.retryService != nil {
		s.retryService.Start(ctx)
		s.logger.Info("✅ 消息重试服务已启动")
	}

	// 启动 nbio HTTP 服务器
	if err := s.server.Start(); err != nil {
		return fmt.Errorf("启动 nbio 服务器失败: %w", err)
	}

	s.logger.Info("🎯 nbio WebSocket 服务器启动成功",
		zap.String("address", fmt.Sprintf(":%d", s.config.Server.Port)),
		zap.Int("npoller", s.config.GetNettyRuntimeConfig().WorkerThreads),
	)

	// 等待关闭信号
	select {
	case <-ctx.Done():
		return nil
	case <-s.shutdown:
		return nil
	}
}

// Shutdown 优雅关闭服务器
func (s *NbioWebSocketServer) Shutdown(ctx context.Context) error {
	var err error
	s.shutdownOnce.Do(func() {
		s.logger.Info("🔄 开始关闭 nbio WebSocket 服务器...")

		// 通知所有协程关闭
		close(s.shutdown)

		// 关闭消息重试服务
		if s.retryService != nil {
			s.logger.Info("关闭消息重试服务...")
			s.retryService.Stop()
		}

		// 关闭异步消息处理器（等待队列处理完成）
		s.logger.Info("关闭异步消息处理器...")
		s.asyncMessageHandler.Shutdown(ctx)

		// 关闭高级心跳处理器
		s.logger.Info("关闭高级心跳处理器...")
		s.advancedHeartbeatHandler.Shutdown()

		// 关闭所有连接
		s.channelManager.CloseAllConnections()

		// 停止 nbio 服务器
		s.server.Stop()

		// 输出最终统计信息
		s.logger.Info("📊 最终统计信息",
			zap.Any("connection_stats", s.connectionStats.GetStats()),
			zap.Any("heartbeat_stats", s.advancedHeartbeatHandler.GetStats()),
			zap.Any("message_handler_stats", s.asyncMessageHandler.GetStats()),
		)

		s.logger.Info("✅ nbio WebSocket 服务器关闭完成")
	})

	return err
}

// handleBinaryMessage 处理二进制消息（适配 MessageHandler）
func (s *NbioWebSocketServer) handleBinaryMessage(conn channel.Connection, message []byte) {
	userID := conn.GetUserID()

	// 解析 Protobuf 消息
	protoRequest := &pb.ImProtoRequest{}
	if err := proto.Unmarshal(message, protoRequest); err != nil {
		s.logger.Error("解析 Protobuf 消息失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		return
	}

	s.logger.Debug("处理二进制消息",
		zap.String("user_id", userID),
		zap.String("msg_type", protoRequest.Type.String()),
		zap.Int("message_size", len(message)),
	)

	// 调用 MessageHandler 处理消息
	if err := s.messageHandler.HandleBinaryMessage(conn, message); err != nil {
		s.logger.Error("消息处理失败",
			zap.String("user_id", userID),
			zap.String("msg_type", protoRequest.Type.String()),
			zap.Error(err),
		)
	}
}

// GetStats 获取服务器统计信息
func (s *NbioWebSocketServer) GetStats() map[string]interface{} {
	return map[string]interface{}{
		"connections": s.channelManager.GetConnectionCount(),
		"uptime":      time.Since(time.Now()).String(),
		"start_time":  time.Now(),
	}
}

// getServerAddress 获取服务器地址
func getServerAddress(cfg *config.Config) string {
	if cfg.Server.Host != "" {
		return fmt.Sprintf("%s:%d", cfg.Server.Host, cfg.GRPC.Port)
	}
	return fmt.Sprintf("localhost:%d", cfg.GRPC.Port)
}
