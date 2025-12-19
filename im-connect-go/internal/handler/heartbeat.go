package handler

import (
	"sync"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	"im-connect-go/internal/metrics"

	"go.uber.org/zap"
)

// HeartbeatHandler 心跳处理器
// 对标 Java HeartBeatHandler/NettyServerHeartBeatHandlerImpl
// 功能：
// 1. 处理客户端心跳（Ping/Pong）
// 2. 检测心跳超时
// 3. 主动发送心跳探测
// 4. 管理连接活跃状态
type HeartbeatHandler struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.Manager
	metrics        *metrics.Metrics

	// 用户最后心跳时间
	lastHeartbeat sync.Map // userID -> time.Time

	// 配置
	heartbeatInterval time.Duration // 心跳间隔
	heartbeatTimeout  time.Duration // 心跳超时

	// 控制
	stopChan chan struct{}
	wg       sync.WaitGroup
}

// NewHeartbeatHandler 创建心跳处理器
func NewHeartbeatHandler(cfg *config.Config, logger *zap.Logger, channelManager *channel.Manager) *HeartbeatHandler {
	handler := &HeartbeatHandler{
		config:            cfg,
		logger:            logger,
		channelManager:    channelManager,
		metrics:           metrics.GetMetrics(),
		heartbeatInterval: 30 * time.Second,
		heartbeatTimeout:  90 * time.Second, // 3倍心跳间隔
		stopChan:          make(chan struct{}),
	}

	// 从配置读取心跳参数
	if cfg.Netty.PingInterval > 0 {
		handler.heartbeatInterval = cfg.Netty.PingInterval
	}
	if cfg.Netty.PongTimeout > 0 {
		handler.heartbeatTimeout = cfg.Netty.PongTimeout * 3 // 3倍超时时间
	}

	// 启动心跳检测
	handler.start()

	logger.Info("✅ 心跳处理器初始化完成",
		zap.Duration("interval", handler.heartbeatInterval),
		zap.Duration("timeout", handler.heartbeatTimeout),
	)

	return handler
}

// start 启动心跳检测
func (h *HeartbeatHandler) start() {
	h.wg.Add(1)
	go h.heartbeatChecker()
}

// OnHeartbeat 处理客户端心跳
func (h *HeartbeatHandler) OnHeartbeat(userID string, conn channel.Connection) {
	now := time.Now()
	h.lastHeartbeat.Store(userID, now)

	// 记录监控指标
	if h.metrics != nil {
		h.metrics.OnHeartbeatReceived()
	}

	// 发送 Pong 响应
	if conn != nil {
		if err := conn.SendPong(nil); err != nil {
			h.logger.Debug("发送Pong失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
		} else {
			if h.metrics != nil {
				h.metrics.OnHeartbeatSent()
			}
		}
	}

	h.logger.Debug("收到心跳",
		zap.String("user_id", userID),
		zap.Time("time", now),
	)
}

// OnBinaryHeartbeat 处理二进制心跳（空消息）
func (h *HeartbeatHandler) OnBinaryHeartbeat(userID string, conn channel.Connection) {
	h.OnHeartbeat(userID, conn)
}

// IsAlive 检查用户是否存活
func (h *HeartbeatHandler) IsAlive(userID string) bool {
	if lastTime, ok := h.lastHeartbeat.Load(userID); ok {
		return time.Since(lastTime.(time.Time)) < h.heartbeatTimeout
	}
	return false
}

// GetLastHeartbeat 获取最后心跳时间
func (h *HeartbeatHandler) GetLastHeartbeat(userID string) time.Time {
	if lastTime, ok := h.lastHeartbeat.Load(userID); ok {
		return lastTime.(time.Time)
	}
	return time.Time{}
}

// RegisterUser 注册用户心跳
func (h *HeartbeatHandler) RegisterUser(userID string) {
	h.lastHeartbeat.Store(userID, time.Now())
}

// UnregisterUser 注销用户心跳
func (h *HeartbeatHandler) UnregisterUser(userID string) {
	h.lastHeartbeat.Delete(userID)
}

// heartbeatChecker 心跳检测协程
func (h *HeartbeatHandler) heartbeatChecker() {
	defer h.wg.Done()

	ticker := time.NewTicker(h.heartbeatInterval) // 使用配置的心跳间隔
	defer ticker.Stop()

	for {
		select {
		case <-h.stopChan:
			return
		case <-ticker.C:
			h.checkTimeoutUsers()
		}
	}
}

// checkTimeoutUsers 检查超时用户
func (h *HeartbeatHandler) checkTimeoutUsers() {
	now := time.Now()
	var timeoutUsers []string

	h.lastHeartbeat.Range(func(key, value interface{}) bool {
		userID := key.(string)
		lastTime := value.(time.Time)

		if now.Sub(lastTime) > h.heartbeatTimeout {
			timeoutUsers = append(timeoutUsers, userID)
		}
		return true
	})

	// 处理超时用户
	for _, userID := range timeoutUsers {
		h.handleTimeout(userID)
	}

	if len(timeoutUsers) > 0 {
		h.logger.Info("心跳超时检测完成",
			zap.Int("timeout_count", len(timeoutUsers)),
		)
	}
}

// handleTimeout 处理心跳超时
func (h *HeartbeatHandler) handleTimeout(userID string) {
	h.logger.Warn("用户心跳超时",
		zap.String("user_id", userID),
	)

	// 记录监控指标
	if h.metrics != nil {
		h.metrics.OnHeartbeatTimeout()
	}

	// 从心跳记录中移除
	h.lastHeartbeat.Delete(userID)

	// 获取用户所有连接并关闭
	connections := h.channelManager.GetUserConnections(userID)
	for _, conn := range connections {
		// 连接移除（会自动关闭）
		h.channelManager.RemoveConnection(userID, conn)
	}
}

// SendPing 主动发送心跳探测
func (h *HeartbeatHandler) SendPing(userID string) error {
	connections := h.channelManager.GetUserConnections(userID)
	if len(connections) == 0 {
		return nil
	}

	var lastErr error
	for _, conn := range connections {
		if err := conn.SendPing(nil); err != nil {
			h.logger.Debug("发送Ping失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
			lastErr = err
		} else {
			if h.metrics != nil {
				h.metrics.OnHeartbeatSent()
			}
		}
	}

	return lastErr
}

// GetActiveUserCount 获取活跃用户数
func (h *HeartbeatHandler) GetActiveUserCount() int {
	var count int
	h.lastHeartbeat.Range(func(key, value interface{}) bool {
		count++
		return true
	})
	return count
}

// GetStats 获取心跳统计
func (h *HeartbeatHandler) GetStats() HeartbeatStats {
	now := time.Now()
	var activeCount, idleCount int

	h.lastHeartbeat.Range(func(key, value interface{}) bool {
		lastTime := value.(time.Time)
		if now.Sub(lastTime) < h.heartbeatInterval {
			activeCount++
		} else {
			idleCount++
		}
		return true
	})

	return HeartbeatStats{
		ActiveUsers:       activeCount,
		IdleUsers:         idleCount,
		HeartbeatInterval: h.heartbeatInterval,
		HeartbeatTimeout:  h.heartbeatTimeout,
	}
}

// HeartbeatStats 心跳统计
type HeartbeatStats struct {
	ActiveUsers       int           `json:"active_users"`
	IdleUsers         int           `json:"idle_users"`
	HeartbeatInterval time.Duration `json:"heartbeat_interval"`
	HeartbeatTimeout  time.Duration `json:"heartbeat_timeout"`
}

// Shutdown 关闭心跳处理器
func (h *HeartbeatHandler) Shutdown() {
	h.logger.Info("🔄 关闭心跳处理器...")

	close(h.stopChan)
	h.wg.Wait()

	h.logger.Info("✅ 心跳处理器已关闭")
}
