package metrics

import (
	"sync"
	"sync/atomic"
	"time"
)

// ConnectionStats 连接统计器
// 对标 Java 版本的连接统计能力
type ConnectionStats struct {
	// 连接计数
	totalConnections    int64
	activeConnections   int64
	totalDisconnections int64

	// 连接建立/断开速率
	connectionsPerSecond    int64
	disconnectionsPerSecond int64

	// 时间统计
	totalConnectionTime int64 // 纳秒
	minConnectionTime   int64
	maxConnectionTime   int64
	avgConnectionTime   int64

	// 消息统计
	totalMessagesProcessed int64
	totalMessageBytes      int64
	avgMessageSize         int64

	// 错误统计
	authenticationFailures int64
	protocolErrors         int64
	heartbeatTimeouts      int64

	// 内存相关
	estimatedMemoryUsage int64 // 字节

	// 用于计算速率
	mu                     sync.RWMutex
	lastConnectTime        time.Time
	lastDisconnectTime     time.Time
	lastCalculationTime    time.Time
	lastConnectionCount    int64
	lastDisconnectionCount int64
}

// NewConnectionStats 创建连接统计器
func NewConnectionStats() *ConnectionStats {
	return &ConnectionStats{
		lastCalculationTime: time.Now(),
		minConnectionTime:   int64(1<<63 - 1), // MaxInt64
	}
}

// OnConnect 连接建立
func (cs *ConnectionStats) OnConnect() {
	atomic.AddInt64(&cs.totalConnections, 1)
	atomic.AddInt64(&cs.activeConnections, 1)

	cs.mu.Lock()
	cs.lastConnectTime = time.Now()
	cs.mu.Unlock()
}

// OnDisconnect 连接断开
func (cs *ConnectionStats) OnDisconnect(connectionDuration time.Duration) {
	atomic.AddInt64(&cs.totalDisconnections, 1)
	activeCount := atomic.AddInt64(&cs.activeConnections, -1)
	if activeCount < 0 {
		atomic.StoreInt64(&cs.activeConnections, 0)
	}

	// 更新连接时长统计
	durationNs := connectionDuration.Nanoseconds()
	if durationNs > 0 {
		atomic.AddInt64(&cs.totalConnectionTime, durationNs)

		// 更新最小/最大值（需要原子性）
		for {
			currentMin := atomic.LoadInt64(&cs.minConnectionTime)
			if durationNs < currentMin {
				if atomic.CompareAndSwapInt64(&cs.minConnectionTime, currentMin, durationNs) {
					break
				}
			} else {
				break
			}
		}

		for {
			currentMax := atomic.LoadInt64(&cs.maxConnectionTime)
			if durationNs > currentMax {
				if atomic.CompareAndSwapInt64(&cs.maxConnectionTime, currentMax, durationNs) {
					break
				}
			} else {
				break
			}
		}
	}

	cs.mu.Lock()
	cs.lastDisconnectTime = time.Now()
	cs.mu.Unlock()
}

// OnMessage 消息处理
func (cs *ConnectionStats) OnMessage(messageSize int) {
	atomic.AddInt64(&cs.totalMessagesProcessed, 1)
	atomic.AddInt64(&cs.totalMessageBytes, int64(messageSize))
}

// OnAuthFailure 认证失败
func (cs *ConnectionStats) OnAuthFailure() {
	atomic.AddInt64(&cs.authenticationFailures, 1)
}

// OnProtocolError 协议错误
func (cs *ConnectionStats) OnProtocolError() {
	atomic.AddInt64(&cs.protocolErrors, 1)
}

// OnHeartbeatTimeout 心跳超时
func (cs *ConnectionStats) OnHeartbeatTimeout() {
	atomic.AddInt64(&cs.heartbeatTimeouts, 1)
}

// CalculateStats 计算并更新统计数据
func (cs *ConnectionStats) CalculateStats() {
	cs.mu.Lock()
	defer cs.mu.Unlock()

	now := time.Now()
	timeSinceLastCalc := now.Sub(cs.lastCalculationTime).Seconds()
	if timeSinceLastCalc < 1 {
		return // 至少 1 秒计算一次
	}

	// 计算速率
	currentConnCount := atomic.LoadInt64(&cs.totalConnections)
	currentDisconnCount := atomic.LoadInt64(&cs.totalDisconnections)

	connRate := int64(float64(currentConnCount-cs.lastConnectionCount) / timeSinceLastCalc)
	disconnRate := int64(float64(currentDisconnCount-cs.lastDisconnectionCount) / timeSinceLastCalc)

	atomic.StoreInt64(&cs.connectionsPerSecond, connRate)
	atomic.StoreInt64(&cs.disconnectionsPerSecond, disconnRate)

	// 计算平均值
	totalConns := atomic.LoadInt64(&cs.totalConnections)
	if totalConns > 0 {
		totalTime := atomic.LoadInt64(&cs.totalConnectionTime)
		avgTime := totalTime / totalConns
		atomic.StoreInt64(&cs.avgConnectionTime, avgTime)
	}

	totalMsgs := atomic.LoadInt64(&cs.totalMessagesProcessed)
	if totalMsgs > 0 {
		totalBytes := atomic.LoadInt64(&cs.totalMessageBytes)
		avgSize := totalBytes / totalMsgs
		atomic.StoreInt64(&cs.avgMessageSize, avgSize)
	}

	// 估计内存占用（每连接约 1KB）
	activeConns := atomic.LoadInt64(&cs.activeConnections)
	estimatedMem := activeConns * 1024
	atomic.StoreInt64(&cs.estimatedMemoryUsage, estimatedMem)

	// 更新上次计算数据
	cs.lastCalculationTime = now
	cs.lastConnectionCount = currentConnCount
	cs.lastDisconnectionCount = currentDisconnCount
}

// GetStats 获取所有统计数据
func (cs *ConnectionStats) GetStats() map[string]interface{} {
	cs.CalculateStats()

	activeConns := atomic.LoadInt64(&cs.activeConnections)
	minTime := atomic.LoadInt64(&cs.minConnectionTime)
	if minTime == int64(1<<63-1) {
		minTime = 0
	}

	return map[string]interface{}{
		"total_connections":          atomic.LoadInt64(&cs.totalConnections),
		"active_connections":         activeConns,
		"total_disconnections":       atomic.LoadInt64(&cs.totalDisconnections),
		"connections_per_second":     atomic.LoadInt64(&cs.connectionsPerSecond),
		"disconnections_per_second":  atomic.LoadInt64(&cs.disconnectionsPerSecond),
		"avg_connection_duration_ms": atomic.LoadInt64(&cs.avgConnectionTime) / 1_000_000,
		"min_connection_duration_ms": minTime / 1_000_000,
		"max_connection_duration_ms": atomic.LoadInt64(&cs.maxConnectionTime) / 1_000_000,
		"total_messages":             atomic.LoadInt64(&cs.totalMessagesProcessed),
		"total_bytes":                atomic.LoadInt64(&cs.totalMessageBytes),
		"avg_message_size":           atomic.LoadInt64(&cs.avgMessageSize),
		"auth_failures":              atomic.LoadInt64(&cs.authenticationFailures),
		"protocol_errors":            atomic.LoadInt64(&cs.protocolErrors),
		"heartbeat_timeouts":         atomic.LoadInt64(&cs.heartbeatTimeouts),
		"estimated_memory_mb":        atomic.LoadInt64(&cs.estimatedMemoryUsage) / (1024 * 1024),
	}
}

// Reset 重置所有统计数据
func (cs *ConnectionStats) Reset() {
	atomic.StoreInt64(&cs.totalConnections, 0)
	atomic.StoreInt64(&cs.activeConnections, 0)
	atomic.StoreInt64(&cs.totalDisconnections, 0)
	atomic.StoreInt64(&cs.connectionsPerSecond, 0)
	atomic.StoreInt64(&cs.disconnectionsPerSecond, 0)
	atomic.StoreInt64(&cs.totalConnectionTime, 0)
	atomic.StoreInt64(&cs.minConnectionTime, int64(1<<63-1))
	atomic.StoreInt64(&cs.maxConnectionTime, 0)
	atomic.StoreInt64(&cs.avgConnectionTime, 0)
	atomic.StoreInt64(&cs.totalMessagesProcessed, 0)
	atomic.StoreInt64(&cs.totalMessageBytes, 0)
	atomic.StoreInt64(&cs.avgMessageSize, 0)
	atomic.StoreInt64(&cs.authenticationFailures, 0)
	atomic.StoreInt64(&cs.protocolErrors, 0)
	atomic.StoreInt64(&cs.heartbeatTimeouts, 0)
	atomic.StoreInt64(&cs.estimatedMemoryUsage, 0)

	cs.mu.Lock()
	cs.lastCalculationTime = time.Now()
	cs.lastConnectionCount = 0
	cs.lastDisconnectionCount = 0
	cs.mu.Unlock()
}
