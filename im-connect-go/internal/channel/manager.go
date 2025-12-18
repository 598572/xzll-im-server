package channel

import (
	"fmt"
	"sync"
	"sync/atomic"
	"time"

	"im-connect-go/internal/config"

	"go.uber.org/zap"
)

// Connection WebSocket 连接接口（对标 Java Channel）
type Connection interface {
	GetUserID() string
	GetRemoteAddr() string
	SendBinary(data []byte) error
	SendPing(data []byte) error
	SendPong(data []byte) error
	IsActive() bool
}

// Manager 连接管理器（对标 Java LocalChannelManager）
// 功能：
// 1. 用户连接映射管理（支持单用户多设备）
// 2. 连接统计和监控
// 3. 内存泄漏防护和自动清理
// 4. 线程安全的连接操作
// 5. 连接限制和负载保护
type Manager struct {
	config *config.Config
	logger *zap.Logger

	// 用户ID到连接的映射（对标 Java userIdChannelMap）
	// 支持单用户多设备：一个用户可以有多个连接
	userConnections map[string]map[string]Connection // userID -> connectionID -> Connection
	connectionUsers map[string]string                // connectionID -> userID

	// 连接统计（对标 Java 统计字段）
	currentConnections int64 // 当前连接数
	totalConnections   int64 // 历史总连接数
	peakConnections    int64 // 峰值连接数
	totalMessages      int64 // 总消息数

	// 连接时间记录（对标 Java userConnectTimeMap）
	connectionTime map[string]time.Time // connectionID -> connectTime

	// 同步锁
	mutex sync.RWMutex

	// 清理任务（对标 Java cleanupExecutor）
	cleanupTicker *time.Ticker
	stopCleanup   chan struct{}

	// 限制配置
	maxConnectionsPerUser int // 单用户最大连接数
	maxTotalConnections   int // 系统最大连接数
}

// ConnectionStats 连接统计信息
type ConnectionStats struct {
	CurrentConnections    int64   `json:"current_connections"`
	TotalConnections      int64   `json:"total_connections"`
	PeakConnections       int64   `json:"peak_connections"`
	TotalMessages         int64   `json:"total_messages"`
	MessageRate           float64 `json:"message_rate"` // 消息速率（条/秒）
	UserCount             int     `json:"user_count"`   // 在线用户数
	AvgConnectionsPerUser float64 `json:"avg_connections_per_user"`
}

// NewManager 创建新的连接管理器
func NewManager(cfg *config.Config, logger *zap.Logger) *Manager {
	manager := &Manager{
		config:                cfg,
		logger:                logger,
		userConnections:       make(map[string]map[string]Connection),
		connectionUsers:       make(map[string]string),
		connectionTime:        make(map[string]time.Time),
		maxConnectionsPerUser: 5, // 默认单用户最多5个设备
		maxTotalConnections:   cfg.Server.MaxConnections,
		stopCleanup:           make(chan struct{}),
	}

	// 启动定时清理任务（对标 Java 定时器）
	manager.startCleanupTask()

	logger.Info("✅ 连接管理器初始化完成",
		zap.Int("max_connections_per_user", manager.maxConnectionsPerUser),
		zap.Int("max_total_connections", manager.maxTotalConnections),
	)

	return manager
}

// AddConnection 添加连接（对标 Java addChannel）
func (m *Manager) AddConnection(userID string, conn Connection) error {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	connectionID := m.generateConnectionID(userID, conn)

	// 检查用户连接数限制
	if userConns, exists := m.userConnections[userID]; exists {
		if len(userConns) >= m.maxConnectionsPerUser {
			// 移除最旧的连接（对标 Java 连接替换逻辑）
			m.removeOldestConnectionLocked(userID)
		}
	}

	// 检查系统总连接数限制
	if m.currentConnections >= int64(m.maxTotalConnections) {
		return fmt.Errorf("系统连接数达到上限: %d", m.maxTotalConnections)
	}

	// 初始化用户连接映射
	if m.userConnections[userID] == nil {
		m.userConnections[userID] = make(map[string]Connection)
	}

	// 添加连接
	m.userConnections[userID][connectionID] = conn
	m.connectionUsers[connectionID] = userID
	m.connectionTime[connectionID] = time.Now()

	// 更新统计信息
	current := atomic.AddInt64(&m.currentConnections, 1)
	atomic.AddInt64(&m.totalConnections, 1)

	// 更新峰值连接数
	for {
		peak := atomic.LoadInt64(&m.peakConnections)
		if current <= peak || atomic.CompareAndSwapInt64(&m.peakConnections, peak, current) {
			break
		}
	}

	m.logger.Debug("➕ 连接已添加",
		zap.String("user_id", userID),
		zap.String("connection_id", connectionID),
		zap.String("remote_addr", conn.GetRemoteAddr()),
		zap.Int64("current_connections", current),
		zap.Int("user_connections", len(m.userConnections[userID])),
	)

	return nil
}

// RemoveConnection 移除连接（对标 Java removeChannel）
func (m *Manager) RemoveConnection(userID string, conn Connection) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	connectionID := m.generateConnectionID(userID, conn)
	m.removeConnectionLocked(connectionID)
}

// removeConnectionLocked 移除连接（内部方法，需要持有锁）
func (m *Manager) removeConnectionLocked(connectionID string) {
	userID, exists := m.connectionUsers[connectionID]
	if !exists {
		return
	}

	// 移除连接映射
	if userConns, exists := m.userConnections[userID]; exists {
		delete(userConns, connectionID)
		// 如果用户没有其他连接，移除用户记录
		if len(userConns) == 0 {
			delete(m.userConnections, userID)
		}
	}

	delete(m.connectionUsers, connectionID)
	delete(m.connectionTime, connectionID)

	// 更新统计信息
	current := atomic.AddInt64(&m.currentConnections, -1)

	m.logger.Debug("➖ 连接已移除",
		zap.String("user_id", userID),
		zap.String("connection_id", connectionID),
		zap.Int64("current_connections", current),
	)
}

// removeOldestConnectionLocked 移除最旧的连接（内部方法，需要持有锁）
func (m *Manager) removeOldestConnectionLocked(userID string) {
	userConns, exists := m.userConnections[userID]
	if !exists || len(userConns) == 0 {
		return
	}

	var oldestID string
	var oldestTime time.Time

	// 找到最旧的连接
	for connectionID := range userConns {
		if connectTime, exists := m.connectionTime[connectionID]; exists {
			if oldestID == "" || connectTime.Before(oldestTime) {
				oldestID = connectionID
				oldestTime = connectTime
			}
		}
	}

	if oldestID != "" {
		m.logger.Warn("⚠️ 移除最旧连接（连接数超限）",
			zap.String("user_id", userID),
			zap.String("connection_id", oldestID),
			zap.Time("connect_time", oldestTime),
		)
		m.removeConnectionLocked(oldestID)
	}
}

// GetUserConnections 获取用户的所有连接（对标 Java getUserChannels）
func (m *Manager) GetUserConnections(userID string) []Connection {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	userConns, exists := m.userConnections[userID]
	if !exists {
		return nil
	}

	connections := make([]Connection, 0, len(userConns))
	for _, conn := range userConns {
		if conn.IsActive() {
			connections = append(connections, conn)
		}
	}

	return connections
}

// IsUserOnline 检查用户是否在线（对标 Java isUserOnline）
func (m *Manager) IsUserOnline(userID string) bool {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	userConns, exists := m.userConnections[userID]
	if !exists {
		return false
	}

	// 检查是否有活跃连接
	for _, conn := range userConns {
		if conn.IsActive() {
			return true
		}
	}

	return false
}

// CanAcceptConnection 检查是否可以接受新连接
func (m *Manager) CanAcceptConnection(userID string) bool {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	// 检查系统总连接数限制
	if m.currentConnections >= int64(m.maxTotalConnections) {
		return false
	}

	// 检查用户连接数限制（但不阻止，因为可以替换旧连接）
	return true
}

// GetConnectionCount 获取当前连接数
func (m *Manager) GetConnectionCount() int64 {
	return atomic.LoadInt64(&m.currentConnections)
}

// GetUserCount 获取在线用户数
func (m *Manager) GetUserCount() int {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	activeUsers := 0
	for userID := range m.userConnections {
		if m.isUserOnlineLocked(userID) {
			activeUsers++
		}
	}

	return activeUsers
}

// isUserOnlineLocked 检查用户是否在线（内部方法，需要持有锁）
func (m *Manager) isUserOnlineLocked(userID string) bool {
	userConns, exists := m.userConnections[userID]
	if !exists {
		return false
	}

	for _, conn := range userConns {
		if conn.IsActive() {
			return true
		}
	}

	return false
}

// GetStats 获取连接统计信息
func (m *Manager) GetStats() ConnectionStats {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	current := atomic.LoadInt64(&m.currentConnections)
	total := atomic.LoadInt64(&m.totalConnections)
	peak := atomic.LoadInt64(&m.peakConnections)
	messages := atomic.LoadInt64(&m.totalMessages)

	userCount := m.GetUserCount()
	avgConnectionsPerUser := float64(0)
	if userCount > 0 {
		avgConnectionsPerUser = float64(current) / float64(userCount)
	}

	// 简单的消息速率计算（实际应该基于时间窗口）
	messageRate := float64(0)
	if total > 0 {
		// 这里应该基于实际的时间窗口计算，暂时简化
		messageRate = float64(messages) / 3600 // 假设运行1小时
	}

	return ConnectionStats{
		CurrentConnections:    current,
		TotalConnections:      total,
		PeakConnections:       peak,
		TotalMessages:         messages,
		MessageRate:           messageRate,
		UserCount:             userCount,
		AvgConnectionsPerUser: avgConnectionsPerUser,
	}
}

// IncrementMessageCount 增加消息计数
func (m *Manager) IncrementMessageCount() {
	atomic.AddInt64(&m.totalMessages, 1)
}

// CloseAllConnections 关闭所有连接（用于优雅关闭）
func (m *Manager) CloseAllConnections() {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	m.logger.Info("🔄 关闭所有连接...",
		zap.Int64("total_connections", m.currentConnections),
	)

	// 这里实际上不能直接关闭连接，因为 Connection 接口没有 Close 方法
	// 实际的关闭需要在 WebSocket 连接的实现中处理
	// 这里只是清理内部映射

	m.userConnections = make(map[string]map[string]Connection)
	m.connectionUsers = make(map[string]string)
	m.connectionTime = make(map[string]time.Time)
	atomic.StoreInt64(&m.currentConnections, 0)
}

// generateConnectionID 生成连接 ID
func (m *Manager) generateConnectionID(userID string, conn Connection) string {
	// 使用用户ID + 远程地址 + 时间戳生成唯一ID
	return fmt.Sprintf("%s_%s_%d", userID, conn.GetRemoteAddr(), time.Now().UnixNano())
}

// startCleanupTask 启动清理任务（对标 Java cleanupExecutor）
func (m *Manager) startCleanupTask() {
	m.cleanupTicker = time.NewTicker(time.Minute) // 每分钟清理一次

	go func() {
		defer m.cleanupTicker.Stop()

		for {
			select {
			case <-m.cleanupTicker.C:
				m.cleanupInactiveConnections()
			case <-m.stopCleanup:
				return
			}
		}
	}()

	m.logger.Info("✅ 连接清理任务启动",
		zap.Duration("cleanup_interval", time.Minute),
	)
}

// cleanupInactiveConnections 清理非活跃连接（对标 Java 清理逻辑）
func (m *Manager) cleanupInactiveConnections() {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	inactiveConnections := make([]string, 0)

	// 查找非活跃连接
	for connectionID, userID := range m.connectionUsers {
		if userConns, exists := m.userConnections[userID]; exists {
			if conn, exists := userConns[connectionID]; exists {
				if !conn.IsActive() {
					inactiveConnections = append(inactiveConnections, connectionID)
				}
			}
		}
	}

	// 移除非活跃连接
	for _, connectionID := range inactiveConnections {
		m.removeConnectionLocked(connectionID)
	}

	if len(inactiveConnections) > 0 {
		m.logger.Info("🧹 清理非活跃连接",
			zap.Int("cleaned_count", len(inactiveConnections)),
			zap.Int64("remaining_connections", m.currentConnections),
		)
	}
}

// Stop 停止连接管理器
func (m *Manager) Stop() {
	close(m.stopCleanup)
	m.CloseAllConnections()

	m.logger.Info("✅ 连接管理器已停止")
}

// GetOnlineUsers 获取在线用户列表
func (m *Manager) GetOnlineUsers() []string {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	users := make([]string, 0, len(m.userConnections))
	for userID := range m.userConnections {
		if m.isUserOnlineLocked(userID) {
			users = append(users, userID)
		}
	}

	return users
}

// BroadcastToUser 向用户的所有连接广播消息（对标 Java 用户消息推送）
func (m *Manager) BroadcastToUser(userID string, message []byte) error {
	connections := m.GetUserConnections(userID)
	if len(connections) == 0 {
		return fmt.Errorf("用户 %s 不在线", userID)
	}

	var lastErr error
	successCount := 0

	// 向用户的所有连接发送消息
	for _, conn := range connections {
		if err := conn.SendBinary(message); err != nil {
			m.logger.Warn("发送消息失败",
				zap.String("user_id", userID),
				zap.String("remote_addr", conn.GetRemoteAddr()),
				zap.Error(err),
			)
			lastErr = err
		} else {
			successCount++
		}
	}

	m.IncrementMessageCount()

	m.logger.Debug("📤 消息已广播",
		zap.String("user_id", userID),
		zap.Int("total_connections", len(connections)),
		zap.Int("success_count", successCount),
		zap.Int("message_size", len(message)),
	)

	// 如果没有任何连接发送成功，返回错误
	if successCount == 0 {
		return fmt.Errorf("所有连接发送失败: %w", lastErr)
	}

	return nil
}
