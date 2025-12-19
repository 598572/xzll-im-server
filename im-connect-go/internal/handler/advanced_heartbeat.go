package handler

import (
	"sync"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"

	"github.com/lesismal/nbio/nbhttp/websocket"
	"go.uber.org/zap"
)

// AdvancedHeartbeatHandler 高级心跳处理器（对标 Java NettyServerHeartBeatHandlerImpl）
// 功能：
// 1. 智能超时检测（区分网络差 vs 真的断线）
// 2. 主动心跳发送
// 3. 失败重试机制
// 4. 心跳统计
type AdvancedHeartbeatHandler struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.NbioManager

	// 每个连接的最后读取时间
	lastReadTime sync.Map // userID -> time.Time

	// 每个连接的心跳失败次数
	heartbeatFailures sync.Map // userID -> int

	// 配置
	idleCheckInterval       time.Duration // IdleStateHandler 检测周期
	heartbeatTimeout        time.Duration // 心跳超时时间
	maxHeartbeatFailures    int           // 最大失败次数（失败N次后关闭）
	activeHeartbeatInterval time.Duration // 主动心跳间隔

	// 停止信号
	stopChan chan struct{}
	wg       sync.WaitGroup
}

// NewAdvancedHeartbeatHandler 创建高级心跳处理器
func NewAdvancedHeartbeatHandler(cfg *config.Config, logger *zap.Logger, cm *channel.NbioManager) *AdvancedHeartbeatHandler {
	handler := &AdvancedHeartbeatHandler{
		config:                  cfg,
		logger:                  logger,
		channelManager:          cm,
		idleCheckInterval:       30 * time.Second,
		heartbeatTimeout:        90 * time.Second,
		maxHeartbeatFailures:    3,
		activeHeartbeatInterval: 20 * time.Second,
		stopChan:                make(chan struct{}),
	}

	// 从配置读取
	nettyConfig := cfg.GetNettyRuntimeConfig()
	if nettyConfig.HeartbeatTimeout > 0 {
		handler.heartbeatTimeout = time.Duration(nettyConfig.HeartbeatTimeout) * time.Second
		handler.idleCheckInterval = handler.heartbeatTimeout / 3
	}

	// 启动定期检测
	handler.start()

	logger.Info("✅ 高级心跳处理器初始化完成",
		zap.Duration("idle_check_interval", handler.idleCheckInterval),
		zap.Duration("heartbeat_timeout", handler.heartbeatTimeout),
		zap.Int("max_failures", handler.maxHeartbeatFailures),
	)

	return handler
}

// start 启动心跳检测协程
func (h *AdvancedHeartbeatHandler) start() {
	h.wg.Add(1)
	go h.heartbeatChecker()
}

// OnRead 有读取时调用（更新活跃度）
func (h *AdvancedHeartbeatHandler) OnRead(userID string) {
	h.lastReadTime.Store(userID, time.Now())
	// 重置失败计数
	h.heartbeatFailures.Delete(userID)
}

// heartbeatChecker 定期检测超时连接
func (h *AdvancedHeartbeatHandler) heartbeatChecker() {
	defer h.wg.Done()

	ticker := time.NewTicker(h.idleCheckInterval)
	defer ticker.Stop()

	for {
		select {
		case <-h.stopChan:
			return
		case <-ticker.C:
			h.checkAllConnections()
		}
	}
}

// checkAllConnections 检查所有连接的活跃度
func (h *AdvancedHeartbeatHandler) checkAllConnections() {
	now := time.Now()

	h.lastReadTime.Range(func(key, value interface{}) bool {
		userID := key.(string)
		lastTime := value.(time.Time)
		timeSinceLastRead := now.Sub(lastTime)

		// 智能判断：是否应该主动发送心跳
		if timeSinceLastRead > h.heartbeatTimeout {
			h.handleHeartbeatTimeout(userID, timeSinceLastRead)
		} else if timeSinceLastRead > h.heartbeatTimeout/2 {
			// 距离超时还有一段时间，主动发送ping探测
			h.sendActiveHeartbeat(userID)
		}

		return true
	})
}

// handleHeartbeatTimeout 处理心跳超时
func (h *AdvancedHeartbeatHandler) handleHeartbeatTimeout(userID string, timeSinceLastRead time.Duration) {
	// 获取失败计数
	failureCount := 0
	if val, ok := h.heartbeatFailures.Load(userID); ok {
		failureCount = val.(int)
	}
	failureCount++

	h.logger.Warn("⏱️ 心跳超时检测",
		zap.String("user_id", userID),
		zap.Duration("time_since_last_read", timeSinceLastRead),
		zap.Int("failure_count", failureCount),
		zap.Int("max_failures", h.maxHeartbeatFailures),
	)

	if failureCount >= h.maxHeartbeatFailures {
		// 超过最大失败次数，关闭连接
		h.closeConnection(userID)
	} else {
		// 增加失败计数并重试
		h.heartbeatFailures.Store(userID, failureCount)
		h.sendActiveHeartbeat(userID)
	}
}

// sendActiveHeartbeat 主动发送心跳探测
func (h *AdvancedHeartbeatHandler) sendActiveHeartbeat(userID string) {
	connections := h.channelManager.GetUserConnections(userID)
	if len(connections) == 0 {
		h.lastReadTime.Delete(userID)
		return
	}

	for _, wsConn := range connections {
		// 直接使用 WebSocket 连接发送 Ping
		if err := wsConn.WriteMessage(websocket.PingMessage, nil); err != nil {
			h.logger.Debug("发送心跳探测失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
		}
	}
}

// closeConnection 关闭连接
func (h *AdvancedHeartbeatHandler) closeConnection(userID string) {
	h.logger.Warn("❌ 心跳超时，关闭连接",
		zap.String("user_id", userID),
	)

	// 关闭用户所有连接
	connections := h.channelManager.GetUserConnections(userID)
	for _, wsConn := range connections {
		wsConn.Close()
	}

	// 清除数据
	h.lastReadTime.Delete(userID)
	h.heartbeatFailures.Delete(userID)
}

// Shutdown 关闭心跳处理器
func (h *AdvancedHeartbeatHandler) Shutdown() {
	h.logger.Info("🔄 关闭高级心跳处理器...")
	close(h.stopChan)
	h.wg.Wait()
	h.logger.Info("✅ 高级心跳处理器已关闭")
}

// GetStats 获取统计信息
func (h *AdvancedHeartbeatHandler) GetStats() map[string]interface{} {
	var totalConnections, failedConnections int
	h.lastReadTime.Range(func(key, value interface{}) bool {
		totalConnections++
		return true
	})
	h.heartbeatFailures.Range(func(key, value interface{}) bool {
		failedConnections++
		return true
	})

	return map[string]interface{}{
		"total_connections":      totalConnections,
		"failed_connections":     failedConnections,
		"heartbeat_timeout":      h.heartbeatTimeout.String(),
		"idle_check_interval":    h.idleCheckInterval.String(),
		"max_heartbeat_failures": h.maxHeartbeatFailures,
	}
}
