package server

import (
	"context"
	"fmt"
	"net"
	"net/http"
	"runtime"
	"sync"
	"time"

	"im-connect-go/internal/auth"
	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	"im-connect-go/internal/handler"
	"im-connect-go/internal/service"
	"im-connect-go/pkg/mq"
	"im-connect-go/pkg/redis"

	"github.com/gorilla/websocket"
	"go.uber.org/zap"
)

// WebSocketServer Go 版本的 WebSocket 服务器
// 对标 Java 版本的 NettyServer，支持百万级连接和高QPS消息处理
type WebSocketServer struct {
	config            *config.Config
	logger            *zap.Logger
	upgrader          websocket.Upgrader
	server            *http.Server
	channelManager    *channel.Manager
	authHandler       *auth.Handler
	messageHandler    *handler.MessageHandler
	userStatusService *service.UserStatusService // 用户状态管理服务（新增）

	// 统计信息
	connections   int64
	totalMessages int64
	startTime     time.Time

	// 优雅关闭
	shutdown     chan struct{}
	shutdownOnce sync.Once
}

// NewWebSocketServer 创建新的 WebSocket 服务器
func NewWebSocketServer(cfg *config.Config, logger *zap.Logger, mqProducer *mq.Producer, redisClient *redis.RedisClient) (*WebSocketServer, error) {
	// 获取运行时 Netty 配置（自动计算线程数等）
	nettyConfig := cfg.GetNettyRuntimeConfig()

	logger.Info("📊 WebSocket 服务器配置",
		zap.String("host", cfg.Server.Host),
		zap.Int("port", cfg.Server.Port),
		zap.Int("boss_threads", nettyConfig.BossThreads),
		zap.Int("worker_threads", nettyConfig.WorkerThreads),
		zap.Int("max_connections", cfg.Server.MaxConnections),
		zap.Int("so_backlog", nettyConfig.SoBackLog),
		zap.Int("socket_buffer_size_kb", nettyConfig.SocketBufferSize/1024),
		zap.Bool("compression", nettyConfig.EnableCompression),
	)

	// 创建 WebSocket 升级器（对标 Java WebSocketServerProtocolHandler）
	upgrader := websocket.Upgrader{
		ReadBufferSize:  nettyConfig.SocketBufferSize,
		WriteBufferSize: nettyConfig.SocketBufferSize,
		CheckOrigin: func(r *http.Request) bool {
			// 允许所有来源（生产环境应该限制）
			return true
		},
		EnableCompression: nettyConfig.EnableCompression,
	}

	// 创建连接管理器（对标 Java LocalChannelManager）
	channelManager := channel.NewManager(cfg, logger)

	// 创建认证处理器（对标 Java AuthHandler）
	authHandler := auth.NewHandler(cfg, logger)

	// 创建消息处理器（对标 Java HandlerDispatcher + WebSocketServerHandler）
	// 传入 mqProducer 和 redisClient，用于消息投递和状态查询
	messageHandler := handler.NewMessageHandler(cfg, logger, channelManager, mqProducer, redisClient)

	// 创建用户状态管理服务（对标 Java UserStatusManagerService）
	// 获取本机外网 IP（简化版，实际应该从配置或自动检测）
	serverAddr := getServerAddress(cfg)
	userStatusService := service.NewUserStatusService(redisClient, logger, serverAddr)
	logger.Info("✅ 用户状态管理服务初始化完成", zap.String("server_addr", serverAddr))

	// 设置 Go 运行时参数（对标 Java EventLoopGroup 线程配置）
	if nettyConfig.WorkerThreads > 0 {
		runtime.GOMAXPROCS(nettyConfig.WorkerThreads)
		logger.Info("🔧 设置 Go 运行时", zap.Int("GOMAXPROCS", nettyConfig.WorkerThreads))
	}

	server := &WebSocketServer{
		config:            cfg,
		logger:            logger,
		upgrader:          upgrader,
		channelManager:    channelManager,
		authHandler:       authHandler,
		messageHandler:    messageHandler,
		userStatusService: userStatusService,
		startTime:         time.Now(),
		shutdown:          make(chan struct{}),
	}

	return server, nil
}

// Start 启动 WebSocket 服务器（对标 Java NettyServer.run()）
func (s *WebSocketServer) Start(ctx context.Context) error {
	nettyConfig := s.config.GetNettyRuntimeConfig()

	// 创建 HTTP 路由（对标 Java WebSocketChannelInitializer）
	mux := http.NewServeMux()

	// WebSocket 路径：/websocket（与 Java 版本保持一致）
	mux.HandleFunc("/websocket", s.handleWebSocket)

	// 健康检查端点
	mux.HandleFunc("/health", s.handleHealth)

	// 统计信息端点
	mux.HandleFunc("/metrics", s.handleMetrics)

	// 创建 HTTP 服务器，配置类似 Java ServerBootstrap
	s.server = &http.Server{
		Addr:         fmt.Sprintf("%s:%d", s.config.Server.Host, s.config.Server.Port),
		Handler:      mux,
		ReadTimeout:  s.config.Server.ReadTimeout,
		WriteTimeout: s.config.Server.WriteTimeout,
	}

	// 创建监听器，配置 Socket 参数（对标 Java SO_BACKLOG）
	listener, err := s.createOptimizedListener(s.server.Addr, nettyConfig)
	if err != nil {
		return fmt.Errorf("创建监听器失败: %w", err)
	}

	s.logger.Info("🎯 WebSocket 服务器启动成功",
		zap.String("address", s.server.Addr),
		zap.Int("backlog", nettyConfig.SoBackLog),
		zap.String("version", "go-optimized"),
	)

	// 启动服务器
	return s.server.Serve(listener)
}

// createOptimizedListener 创建优化的监听器（对标 Java ServerBootstrap 配置）
func (s *WebSocketServer) createOptimizedListener(addr string, nettyConfig config.NettyConfig) (net.Listener, error) {
	// 解析地址
	tcpAddr, err := net.ResolveTCPAddr("tcp", addr)
	if err != nil {
		return nil, fmt.Errorf("解析地址失败: %w", err)
	}

	// 创建 TCP 监听器
	listener, err := net.ListenTCP("tcp", tcpAddr)
	if err != nil {
		return nil, fmt.Errorf("创建 TCP 监听器失败: %w", err)
	}

	// TODO: 设置 SO_BACKLOG（Go 标准库不直接支持，需要通过系统调用）
	// 这部分可以通过 syscall 或第三方库实现，类似 Java 的 ServerBootstrap.option(ChannelOption.SO_BACKLOG)

	s.logger.Info("🔧 TCP 监听器配置完成",
		zap.String("address", addr),
		zap.Int("target_backlog", nettyConfig.SoBackLog),
	)

	return listener, nil
}

// handleWebSocket 处理 WebSocket 连接（对标 Java WebSocketChannelInitializer）
func (s *WebSocketServer) handleWebSocket(w http.ResponseWriter, r *http.Request) {
	// 1. 认证检查（对标 Java AuthHandler）
	userID, err := s.authHandler.Authenticate(r)
	if err != nil {
		s.logger.Warn("WebSocket 认证失败",
			zap.String("remote_addr", r.RemoteAddr),
			zap.Error(err),
		)
		http.Error(w, "认证失败", http.StatusUnauthorized)
		return
	}

	// 2. 连接数限制检查（对标 Java ConnectionLimitHandler）
	if !s.channelManager.CanAcceptConnection(userID) {
		s.logger.Warn("连接数超过限制",
			zap.String("user_id", userID),
			zap.String("remote_addr", r.RemoteAddr),
		)
		http.Error(w, "连接数超过限制", http.StatusTooManyRequests)
		return
	}

	// 3. 升级为 WebSocket 连接（对标 Java WebSocketServerProtocolHandler）
	conn, err := s.upgrader.Upgrade(w, r, nil)
	if err != nil {
		s.logger.Error("WebSocket 升级失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		return
	}

	// 4. 创建连接对象并注册（对标 Java LocalChannelManager.addChannel）
	wsConn := &WebSocketConnection{
		conn:       conn,
		userID:     userID,
		remoteAddr: r.RemoteAddr,
		startTime:  time.Now(),
		lastPing:   time.Now(),
		server:     s,
	}

	// 5. 注册连接到管理器
	if err := s.channelManager.AddConnection(userID, wsConn); err != nil {
		s.logger.Error("注册连接失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		conn.Close()
		return
	}

	// 6. 设置用户在线状态到 Redis（对标 Java userConnectSuccessAfter）
	if err := s.userStatusService.UserConnectSuccessAfter(userID); err != nil {
		s.logger.Error("设置用户在线状态失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		// 状态设置失败，清理已设置的映射，关闭连接让用户重连
		s.channelManager.RemoveConnection(userID, wsConn)
		conn.Close()
		return
	}

	s.logger.Info("✅ WebSocket 连接建立",
		zap.String("user_id", userID),
		zap.String("remote_addr", r.RemoteAddr),
		zap.Int64("total_connections", s.channelManager.GetConnectionCount()),
	)

	// 6. 启动连接处理协程（对标 Java WebSocketServerHandler）
	go wsConn.handleConnection()
}

// WebSocketConnection WebSocket 连接封装（对标 Java Channel）
type WebSocketConnection struct {
	conn       *websocket.Conn
	userID     string
	remoteAddr string
	startTime  time.Time
	lastPing   time.Time
	server     *WebSocketServer

	// 心跳失败计数（对标 Java heartbeatFailureCount）
	heartbeatFailureCount int
	heartbeatMux          sync.Mutex

	// 同步机制
	writeMux sync.Mutex
	closed   bool
	closeMux sync.RWMutex
}

// handleConnection 处理连接生命周期（对标 Java WebSocketServerHandler）
func (c *WebSocketConnection) handleConnection() {
	defer c.close()

	nettyConfig := c.server.config.GetNettyRuntimeConfig()

	c.conn.SetReadLimit(nettyConfig.MaxMessageSize)

	// SetPingHandler: The default handler is overridden. We must manually send a pong.
	c.conn.SetPingHandler(func(appData string) error {
		c.server.logger.Info("✅ SetPingHandler 被触发，收到客户端 Ping",
			zap.String("user_id", c.userID),
			zap.String("app_data", appData),
		)
		c.recordHeartbeatResponse("ping")
		// Manually send a Pong response, as setting a handler overrides the default behavior.
		if err := c.SendPong([]byte(appData)); err != nil {
			c.server.logger.Warn("回复 Pong 失败", zap.String("user_id", c.userID), zap.Error(err))
		}
		return nil
	})

	c.conn.SetPongHandler(func(appData string) error {
		c.server.logger.Info("✅ SetPongHandler 被触发，收到客户端 Pong",
			zap.String("user_id", c.userID),
			zap.String("app_data", appData),
		)
		c.recordHeartbeatResponse("pong")
		return nil
	})

	heartbeatTimeout := time.Duration(nettyConfig.HeartbeatTimeout) * time.Second
	go c.heartbeatChecker(nettyConfig.PingInterval, heartbeatTimeout)

	c.server.logger.Info("🔄 开始消息循环，等待客户端消息",
		zap.String("user_id", c.userID),
	)

	for {
		// No SetReadDeadline here. Let ReadMessage block indefinitely.
		// The heartbeatChecker goroutine is responsible for detecting dead connections.

		messageType, message, err := c.conn.ReadMessage()
		if err != nil {
			// When the connection is closed (either by this goroutine via return,
			// or by heartbeatChecker), ReadMessage will return an error.
			if c.IsClosed() {
				c.server.logger.Debug("连接已关闭，正常退出消息循环", zap.String("user_id", c.userID))
			} else {
				// If not closed yet, it's an unexpected error. Log it and let defer c.close() handle it.
				if websocket.IsUnexpectedCloseError(err, websocket.CloseGoingAway, websocket.CloseNormalClosure) {
					c.server.logger.Error("意外的 WebSocket 关闭错误",
						zap.String("user_id", c.userID),
						zap.Error(err),
					)
				} else {
					// This branch handles "expected" close errors, like the client closing the connection.
					c.server.logger.Info("WebSocket 读取终止（可能正常关闭）",
						zap.String("user_id", c.userID),
						zap.Error(err),
					)
				}
			}
			// On any error, exit the loop. The deferred c.close() will ensure cleanup.
			return
		}

		c.server.logger.Info("✅ ReadMessage() 收到消息",
			zap.String("user_id", c.userID),
			zap.Int("message_type", int(messageType)),
			zap.String("message_type_name", getMessageTypeName(messageType)),
			zap.Int("message_size", len(message)),
		)

		switch messageType {
		case websocket.TextMessage:
			c.server.logger.Warn("收到不支持的文本消息",
				zap.String("user_id", c.userID),
				zap.String("message", string(message)),
			)
			c.recordHeartbeatResponse("text")

		case websocket.BinaryMessage:
			c.recordHeartbeatResponse("binary")
			c.server.messageHandler.HandleBinaryMessage(c, message)

		// Ping and Pong cases are not needed here as they are handled by the handlers.
		case websocket.PingMessage:
			c.server.logger.Warn("收到 Ping 消息（应该在 SetPingHandler 中处理）", zap.String("user_id", c.userID))
			c.recordHeartbeatResponse("ping")

		case websocket.PongMessage:
			c.server.logger.Warn("收到 Pong 消息（应该在 SetPongHandler 中处理）", zap.String("user_id", c.userID))
			c.recordHeartbeatResponse("pong")

		case websocket.CloseMessage:
			c.server.logger.Info("收到关闭消息", zap.String("user_id", c.userID))
			return
		}
	}
}

// heartbeatChecker 心跳检测（对标 Java NettyServerHeartBeatHandlerImpl.process）
// 核心逻辑：
// 1. 定期检查是否超时（timeSinceLastRead > heartbeatTimeout）
// 2. 超时时增加失败计数，失败次数 >= maxFailures 才关闭连接
// 3. 未超过最大失败次数时，主动发送 Ping 尝试恢复
// 4. 未超时时重置失败计数
// 5. 距离超时不到一半时，主动发送 Ping 保活
func (c *WebSocketConnection) heartbeatChecker(checkInterval, heartbeatTimeout time.Duration) {
	// 使用配置的检测间隔（对标 Java idleStateCheckInterval）
	nettyConfig := c.server.config.GetNettyRuntimeConfig()
	idleCheckInterval := time.Duration(nettyConfig.IdleStateCheckInterval) * time.Second

	ticker := time.NewTicker(idleCheckInterval)
	defer ticker.Stop()

	maxFailures := nettyConfig.MaxHeartbeatFailures

	for {
		select {
		case <-ticker.C:
			// 检查连接是否已关闭
			if c.IsClosed() {
				c.server.logger.Debug("连接已关闭，停止心跳检测",
					zap.String("user_id", c.userID),
				)
				return
			}

			// 获取当前时间和最后读取时间
			currentTime := time.Now()
			timeSinceLastRead := currentTime.Sub(c.lastPing)

			// 检查是否超时
			if timeSinceLastRead > heartbeatTimeout {
				// 心跳超时，处理失败逻辑
				c.handleHeartbeatTimeout(timeSinceLastRead, heartbeatTimeout, maxFailures)
			} else {
				// 心跳正常，重置失败计数
				c.heartbeatMux.Lock()
				if c.heartbeatFailureCount > 0 {
					c.server.logger.Debug("心跳恢复正常，重置失败计数",
						zap.String("user_id", c.userID),
						zap.Int("previous_failures", c.heartbeatFailureCount),
					)
					c.heartbeatFailureCount = 0
				}
				c.heartbeatMux.Unlock()

				// 如果距离超时还有一半以上时间，可以主动发送 Ping（对标 Java 主动保活）
				timeUntilTimeout := heartbeatTimeout - timeSinceLastRead
				if timeUntilTimeout < heartbeatTimeout/2 {
					c.sendActiveHeartbeat()
				}

				c.server.logger.Debug("心跳检测正常",
					zap.String("user_id", c.userID),
					zap.Duration("time_since_last_read", timeSinceLastRead),
					zap.Duration("heartbeat_timeout", heartbeatTimeout),
				)
			}

		case <-c.server.shutdown:
			return
		}
	}
}

// handleHeartbeatTimeout 处理心跳超时（对标 Java handleHeartbeatTimeout）
func (c *WebSocketConnection) handleHeartbeatTimeout(timeSinceLastRead, heartbeatTimeout time.Duration, maxFailures int) {
	// 增加失败计数
	c.heartbeatMux.Lock()
	c.heartbeatFailureCount++
	failureCount := c.heartbeatFailureCount
	c.heartbeatMux.Unlock()

	c.server.logger.Warn("心跳超时检测",
		zap.String("user_id", c.userID),
		zap.Duration("timeout_duration", timeSinceLastRead),
		zap.Int("failure_count", failureCount),
		zap.Int("max_failures", maxFailures),
	)

	if failureCount >= maxFailures {
		// 超过最大失败次数，关闭连接（对标 Java closeConnectionDueToHeartbeatFailure）
		c.closeConnectionDueToHeartbeatFailure(timeSinceLastRead, heartbeatTimeout, maxFailures)
	} else {
		// 尝试主动发送心跳（对标 Java sendActiveHeartbeat）
		c.sendActiveHeartbeat()
	}
}

// closeConnectionDueToHeartbeatFailure 由于心跳失败关闭连接
// 对标 Java closeConnectionDueToHeartbeatFailure
func (c *WebSocketConnection) closeConnectionDueToHeartbeatFailure(timeSinceLastRead, heartbeatTimeout time.Duration, maxFailures int) {
	// 【重要】关闭前再次确认是否真的超时，防止误杀刚重连的用户（对标 Java 二次确认逻辑）
	actualTimeSinceLastRead := time.Since(c.lastPing)
	if actualTimeSinceLastRead < heartbeatTimeout {
		// 用户可能刚发送了消息，取消关闭，重置失败计数
		c.heartbeatMux.Lock()
		c.heartbeatFailureCount = 0
		c.heartbeatMux.Unlock()

		c.server.logger.Info("检测到用户可能刚活跃，取消关闭连接",
			zap.String("user_id", c.userID),
			zap.Duration("actual_timeout", actualTimeSinceLastRead),
			zap.Duration("threshold", heartbeatTimeout),
		)
		return
	}

	c.server.logger.Warn("客户端心跳超时，连续失败多次，关闭连接",
		zap.String("user_id", c.userID),
		zap.Duration("timeout_duration", timeSinceLastRead),
		zap.Int("continuous_failures", maxFailures),
	)

	// 清理心跳数据并关闭连接
	c.cleanupHeartbeat()
	c.close()
}

// sendActiveHeartbeat 发送主动心跳（对标 Java sendActiveHeartbeat）
func (c *WebSocketConnection) sendActiveHeartbeat() {
	if err := c.SendPing(nil); err != nil {
		c.server.logger.Warn("主动心跳发送失败",
			zap.String("user_id", c.userID),
			zap.Error(err),
		)
	} else {
		c.server.logger.Debug("主动心跳发送成功",
			zap.String("user_id", c.userID),
		)
	}
}

// recordHeartbeatResponse 记录心跳响应（对标 Java recordHeartbeatResponse）
// 在以下场景调用：
// 1. 收到客户端主动发送的 Ping 时
// 2. 收到客户端回复的 Pong 时
// 3. 收到业务消息时
func (c *WebSocketConnection) recordHeartbeatResponse(heartbeatType string) {
	// 更新读取时间
	c.lastPing = time.Now()

	// 重置失败计数
	c.heartbeatMux.Lock()
	if c.heartbeatFailureCount > 0 {
		c.server.logger.Debug("心跳响应收到，重置失败计数",
			zap.String("user_id", c.userID),
			zap.String("heartbeat_type", heartbeatType),
			zap.Int("previous_failures", c.heartbeatFailureCount),
		)
	}
	c.heartbeatFailureCount = 0
	c.heartbeatMux.Unlock()

	c.server.logger.Info("✅ recordHeartbeatResponse 被调用",
		zap.String("user_id", c.userID),
		zap.String("type", heartbeatType),
		zap.Time("last_ping", c.lastPing),
	)
}

// cleanupHeartbeat 清理心跳数据（对标 Java cleanup）
func (c *WebSocketConnection) cleanupHeartbeat() {
	c.heartbeatMux.Lock()
	c.heartbeatFailureCount = 0
	c.heartbeatMux.Unlock()

	c.server.logger.Debug("清理心跳数据",
		zap.String("user_id", c.userID),
	)
}

// SendPing 发送 Ping 消息
func (c *WebSocketConnection) SendPing(data []byte) error {
	c.writeMux.Lock()
	defer c.writeMux.Unlock()

	c.closeMux.RLock()
	if c.closed {
		c.closeMux.RUnlock()
		return fmt.Errorf("连接已关闭")
	}
	c.closeMux.RUnlock()

	return c.conn.WriteMessage(websocket.PingMessage, data)
}

// SendPong 发送 Pong 消息
func (c *WebSocketConnection) SendPong(data []byte) error {
	c.writeMux.Lock()
	defer c.writeMux.Unlock()

	c.closeMux.RLock()
	if c.closed {
		c.closeMux.RUnlock()
		return fmt.Errorf("连接已关闭")
	}
	c.closeMux.RUnlock()

	return c.conn.WriteMessage(websocket.PongMessage, data)
}

// SendBinary 发送二进制消息（对标 Java BinaryWebSocketFrame）
func (c *WebSocketConnection) SendBinary(data []byte) error {
	c.writeMux.Lock()
	defer c.writeMux.Unlock()

	c.closeMux.RLock()
	if c.closed {
		c.closeMux.RUnlock()
		return fmt.Errorf("连接已关闭")
	}
	c.closeMux.RUnlock()

	return c.conn.WriteMessage(websocket.BinaryMessage, data)
}

// GetUserID 获取用户 ID
func (c *WebSocketConnection) GetUserID() string {
	return c.userID
}

// GetRemoteAddr 获取远程地址
func (c *WebSocketConnection) GetRemoteAddr() string {
	return c.remoteAddr
}

// IsActive 检查连接是否活跃
func (c *WebSocketConnection) IsActive() bool {
	c.closeMux.RLock()
	defer c.closeMux.RUnlock()
	return !c.closed
}

// IsClosed 检查连接是否已关闭
func (c *WebSocketConnection) IsClosed() bool {
	c.closeMux.RLock()
	defer c.closeMux.RUnlock()
	return c.closed
}

// close 关闭连接
func (c *WebSocketConnection) close() {
	c.closeMux.Lock()
	if c.closed {
		c.closeMux.Unlock()
		return
	}
	c.closed = true
	c.closeMux.Unlock()

	// 清理心跳数据（对标 Java cleanup）
	c.cleanupHeartbeat()

	// 从连接管理器中移除
	c.server.channelManager.RemoveConnection(c.userID, c)

	// 清除用户在线状态从 Redis（对标 Java userDisconnectAfter）
	// 注意：只有当用户所有连接都关闭时才清除状态
	if !c.server.channelManager.IsUserOnline(c.userID) {
		if err := c.server.userStatusService.UserDisconnectAfter(c.userID); err != nil {
			c.server.logger.Error("清除用户在线状态失败",
				zap.String("user_id", c.userID),
				zap.Error(err),
			)
		}
	}

	// 关闭 WebSocket 连接
	c.conn.Close()

	c.server.logger.Info("❌ WebSocket 连接关闭",
		zap.String("user_id", c.userID),
		zap.String("remote_addr", c.remoteAddr),
		zap.Duration("duration", time.Since(c.startTime)),
		zap.Int64("remaining_connections", c.server.channelManager.GetConnectionCount()),
	)
}

// handleHealth 健康检查端点
func (s *WebSocketServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	status := map[string]interface{}{
		"status":      "healthy",
		"connections": s.channelManager.GetConnectionCount(),
		"uptime":      time.Since(s.startTime).String(),
		"version":     "im-connect-go-1.0.0",
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	// 简单的 JSON 响应
	fmt.Fprintf(w, `{"status":"%s","connections":%d,"uptime":"%s","version":"%s"}`,
		status["status"], status["connections"], status["uptime"], status["version"])
}

// getServerAddress 获取服务器地址（对标 Java NettyAttrUtil.getIpPortStr()）
// 格式：ip:port
func getServerAddress(cfg *config.Config) string {
	// 方式1：从配置中读取（优先）
	// TODO: 从配置中读取外网 IP 或内网 IP

	// 方式2：自动检测（简化版）
	// 实际生产环境应该从配置文件或环境变量中读取
	host := cfg.Server.Host
	if host == "" || host == "0.0.0.0" {
		// 尝试获取本机 IP
		host = getLocalIP()
	}

	port := cfg.Server.Port
	return fmt.Sprintf("%s:%d", host, port)
}

// getLocalIP 获取本机 IP（简化版）
func getLocalIP() string {
	addrs, err := net.InterfaceAddrs()
	if err != nil {
		return "127.0.0.1"
	}

	for _, addr := range addrs {
		if ipNet, ok := addr.(*net.IPNet); ok && !ipNet.IP.IsLoopback() {
			if ipNet.IP.To4() != nil {
				return ipNet.IP.String()
			}
		}
	}

	return "127.0.0.1"
}

// getMessageTypeName 获取消息类型名称（用于日志）
func getMessageTypeName(messageType int) string {
	switch messageType {
	case websocket.TextMessage:
		return "TextMessage"
	case websocket.BinaryMessage:
		return "BinaryMessage"
	case websocket.CloseMessage:
		return "CloseMessage"
	case websocket.PingMessage:
		return "PingMessage"
	case websocket.PongMessage:
		return "PongMessage"
	default:
		return fmt.Sprintf("Unknown(%d)", messageType)
	}
}

// handleMetrics 统计信息端点
func (s *WebSocketServer) handleMetrics(w http.ResponseWriter, r *http.Request) {
	stats := s.channelManager.GetStats()

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)

	fmt.Fprintf(w, `{
		"connections": {
			"current": %d,
			"total": %d,
			"peak": %d
		},
		"messages": {
			"total": %d,
			"rate": %.2f
		},
		"server": {
			"uptime": "%s",
			"start_time": "%s",
			"version": "im-connect-go-1.0.0"
		}
	}`,
		stats.CurrentConnections,
		stats.TotalConnections,
		stats.PeakConnections,
		stats.TotalMessages,
		stats.MessageRate,
		time.Since(s.startTime).String(),
		s.startTime.Format(time.RFC3339),
	)
}

// Shutdown 优雅关闭服务器
func (s *WebSocketServer) Shutdown(ctx context.Context) error {
	var err error

	s.shutdownOnce.Do(func() {
		s.logger.Info("🔄 开始关闭 WebSocket 服务器...")

		// 通知所有协程关闭
		close(s.shutdown)

		// 关闭所有连接
		s.channelManager.CloseAllConnections()

		// 关闭 HTTP 服务器
		if s.server != nil {
			err = s.server.Shutdown(ctx)
		}

		s.logger.Info("✅ WebSocket 服务器关闭完成")
	})

	return err
}

// GetStats 获取服务器统计信息
func (s *WebSocketServer) GetStats() ServerStats {
	return ServerStats{
		Connections: s.channelManager.GetConnectionCount(),
		Uptime:      time.Since(s.startTime),
		StartTime:   s.startTime,
	}
}

// ServerStats 服务器统计信息
type ServerStats struct {
	Connections int64
	Uptime      time.Duration
	StartTime   time.Time
}
