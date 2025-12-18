package middleware

import (
	"context"
	"fmt"
	"net"
	"sync"
	"time"

	"im-connect-go/pkg/redis"

	"go.uber.org/zap"
)

// ============= Redis Key 前缀定义 =============
const (
	IPMessageCountKeyPrefix = "im:flow:msg:"
	IPByteCountKeyPrefix    = "im:flow:byte:"
	IPThrottledKeyPrefix    = "im:flow:throttled:"
)

// FlowControlConfig 流量控制配置（对标 Java FlowControlHandler）
type FlowControlConfig struct {
	MaxMessagesPerSecond int           `yaml:"max_messages_per_second"` // 每秒最大消息数
	MaxMessageSize       int           `yaml:"max_message_size"`        // 单条消息最大字节数
	MaxBytesPerSecond    int64         `yaml:"max_bytes_per_second"`    // 每秒最大字节数（带宽控制）
	ThrottleDuration     time.Duration `yaml:"throttle_duration"`       // 限流时间
	Enabled              bool          `yaml:"enabled"`                 // 是否启用
	MessageCountTTL      time.Duration `yaml:"message_count_ttl"`       // 消息计数过期时间
	ByteCountTTL         time.Duration `yaml:"byte_count_ttl"`          // 字节计数过期时间
}

// DefaultFlowControlConfig 默认配置
func DefaultFlowControlConfig() *FlowControlConfig {
	return &FlowControlConfig{
		MaxMessagesPerSecond: 10000,           // 每秒最大10000条消息
		MaxMessageSize:       8192,            // 单条消息最大8KB
		MaxBytesPerSecond:    102400,          // 每秒最大100KB
		ThrottleDuration:     1 * time.Minute, // 限流1分钟
		Enabled:              true,
		MessageCountTTL:      1 * time.Second,
		ByteCountTTL:         1 * time.Second,
	}
}

// FlowController 流量控制器（对标 Java FlowControlHandler）
// 功能：
// 1. 消息频率限制（分布式）
// 2. 消息大小限制
// 3. 带宽控制（分布式）
// 4. 自动限流和恢复（分布式）
// 5. 支持分布式部署，所有数据存储在Redis中
type FlowController struct {
	config      *FlowControlConfig
	redisClient *redis.RedisClient
	logger      *zap.Logger
	mu          sync.RWMutex
	localStats  *LocalFlowStats // 本地统计（快速路径）
}

// LocalFlowStats 本地流量统计
type LocalFlowStats struct {
	messageCount map[string]int64 // IP -> 消息计数
	byteCount    map[string]int64 // IP -> 字节计数
	throttled    map[string]int64 // IP -> 限流开始时间
	lastReset    time.Time        // 上次重置时间
	mu           sync.RWMutex
}

// NewFlowController 创建流量控制器
func NewFlowController(config *FlowControlConfig, redisClient *redis.RedisClient, logger *zap.Logger) *FlowController {
	if config == nil {
		config = DefaultFlowControlConfig()
	}

	fc := &FlowController{
		config:      config,
		redisClient: redisClient,
		logger:      logger,
		localStats: &LocalFlowStats{
			messageCount: make(map[string]int64),
			byteCount:    make(map[string]int64),
			throttled:    make(map[string]int64),
			lastReset:    time.Now(),
		},
	}

	// 启动本地统计重置协程
	go fc.resetLocalStatsRoutine()

	return fc
}

// resetLocalStatsRoutine 定期重置本地统计
func (f *FlowController) resetLocalStatsRoutine() {
	ticker := time.NewTicker(1 * time.Second)
	defer ticker.Stop()

	for range ticker.C {
		f.localStats.mu.Lock()
		f.localStats.messageCount = make(map[string]int64)
		f.localStats.byteCount = make(map[string]int64)
		f.localStats.lastReset = time.Now()
		f.localStats.mu.Unlock()
	}
}

// CheckMessage 检查消息是否允许发送
// 返回 (允许, 原因)
func (f *FlowController) CheckMessage(ctx context.Context, remoteAddr net.Addr, messageSize int) (bool, string) {
	if !f.config.Enabled {
		return true, ""
	}

	clientIP := f.getClientIP(remoteAddr)

	// 1. 检查是否被限流
	if f.isThrottled(ctx, clientIP) {
		f.logger.Debug("IP被限流，丢弃消息",
			zap.String("ip", clientIP),
		)
		return false, "IP被限流"
	}

	// 2. 检查消息大小限制
	if messageSize > f.config.MaxMessageSize {
		f.logger.Warn("消息大小超过限制，限流处理",
			zap.String("ip", clientIP),
			zap.Int("size", messageSize),
			zap.Int("limit", f.config.MaxMessageSize),
		)
		f.throttleIP(ctx, clientIP, "消息大小超限")
		return false, "消息大小超限"
	}

	// 3. 检查消息频率限制
	if !f.checkMessageFrequency(ctx, clientIP) {
		f.logger.Warn("消息频率超过限制，限流处理",
			zap.String("ip", clientIP),
			zap.Int("limit", f.config.MaxMessagesPerSecond),
		)
		f.throttleIP(ctx, clientIP, "消息频率超限")
		return false, "消息频率超限"
	}

	// 4. 检查带宽限制
	if !f.checkBandwidthLimit(ctx, clientIP, messageSize) {
		f.logger.Warn("带宽超过限制，限流处理",
			zap.String("ip", clientIP),
			zap.Int64("limit", f.config.MaxBytesPerSecond),
		)
		f.throttleIP(ctx, clientIP, "带宽超限")
		return false, "带宽超限"
	}

	// 5. 更新计数器
	f.updateCounters(ctx, clientIP, messageSize)

	f.logger.Debug("消息通过流控检查",
		zap.String("ip", clientIP),
		zap.Int("size", messageSize),
	)

	return true, ""
}

// ============= Redis 分布式流控功能 =============

// checkMessageFrequency 检查消息频率限制
func (f *FlowController) checkMessageFrequency(ctx context.Context, ip string) bool {
	if f.redisClient == nil {
		f.localStats.mu.RLock()
		defer f.localStats.mu.RUnlock()
		return f.localStats.messageCount[ip] < int64(f.config.MaxMessagesPerSecond)
	}

	messageKey := IPMessageCountKeyPrefix + ip
	count, err := f.redisClient.GetInt64(ctx, messageKey)
	if err != nil {
		f.logger.Error("检查消息频率限制异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return false // 异常时限制，保护系统
	}

	return count < int64(f.config.MaxMessagesPerSecond)
}

// checkBandwidthLimit 检查带宽限制
func (f *FlowController) checkBandwidthLimit(ctx context.Context, ip string, messageSize int) bool {
	if f.redisClient == nil {
		f.localStats.mu.RLock()
		defer f.localStats.mu.RUnlock()
		return (f.localStats.byteCount[ip] + int64(messageSize)) <= f.config.MaxBytesPerSecond
	}

	byteKey := IPByteCountKeyPrefix + ip
	currentBytes, err := f.redisClient.GetInt64(ctx, byteKey)
	if err != nil {
		f.logger.Error("检查带宽限制异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return false
	}

	return (currentBytes + int64(messageSize)) <= f.config.MaxBytesPerSecond
}

// updateCounters 更新计数器
func (f *FlowController) updateCounters(ctx context.Context, ip string, messageSize int) {
	if f.redisClient == nil {
		f.localStats.mu.Lock()
		f.localStats.messageCount[ip]++
		f.localStats.byteCount[ip] += int64(messageSize)
		f.localStats.mu.Unlock()
		return
	}

	// 更新消息计数（每秒重置）
	messageKey := IPMessageCountKeyPrefix + ip
	if err := f.redisClient.IncrWithExpire(ctx, messageKey, f.config.MessageCountTTL); err != nil {
		f.logger.Error("更新消息计数失败",
			zap.String("ip", ip),
			zap.Error(err),
		)
	}

	// 更新字节计数（每秒重置）
	byteKey := IPByteCountKeyPrefix + ip
	if err := f.redisClient.IncrByWithExpire(ctx, byteKey, int64(messageSize), f.config.ByteCountTTL); err != nil {
		f.logger.Error("更新字节计数失败",
			zap.String("ip", ip),
			zap.Error(err),
		)
	}
}

// isThrottled 检查IP是否被限流
func (f *FlowController) isThrottled(ctx context.Context, ip string) bool {
	if f.redisClient == nil {
		f.localStats.mu.RLock()
		defer f.localStats.mu.RUnlock()
		throttleTime, exists := f.localStats.throttled[ip]
		if !exists {
			return false
		}
		// 检查是否已过期
		if time.Now().UnixMilli()-throttleTime > f.config.ThrottleDuration.Milliseconds() {
			return false
		}
		return true
	}

	throttledKey := IPThrottledKeyPrefix + ip
	exists, err := f.redisClient.Exists(ctx, throttledKey)
	if err != nil {
		f.logger.Error("检查IP限流状态异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return false
	}
	return exists
}

// throttleIP 限流指定IP
func (f *FlowController) throttleIP(ctx context.Context, ip string, reason string) {
	if f.redisClient == nil {
		f.localStats.mu.Lock()
		f.localStats.throttled[ip] = time.Now().UnixMilli()
		f.localStats.mu.Unlock()
		f.logger.Info("IP被限流",
			zap.String("ip", ip),
			zap.String("reason", reason),
			zap.Duration("duration", f.config.ThrottleDuration),
		)
		return
	}

	throttledKey := IPThrottledKeyPrefix + ip
	throttledValue := fmt.Sprintf("%s:%d", reason, time.Now().UnixMilli())

	if err := f.redisClient.SetWithExpire(ctx, throttledKey, throttledValue, f.config.ThrottleDuration); err != nil {
		f.logger.Error("限流IP失败",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return
	}

	f.logger.Info("IP被限流",
		zap.String("ip", ip),
		zap.String("reason", reason),
		zap.Duration("duration", f.config.ThrottleDuration),
	)
}

// UnthrottleIP 手动解除IP限流
func (f *FlowController) UnthrottleIP(ctx context.Context, ip string) error {
	if f.redisClient == nil {
		f.localStats.mu.Lock()
		delete(f.localStats.throttled, ip)
		delete(f.localStats.messageCount, ip)
		delete(f.localStats.byteCount, ip)
		f.localStats.mu.Unlock()
		f.logger.Info("手动解除IP限流", zap.String("ip", ip))
		return nil
	}

	throttledKey := IPThrottledKeyPrefix + ip
	messageKey := IPMessageCountKeyPrefix + ip
	byteKey := IPByteCountKeyPrefix + ip

	if err := f.redisClient.Delete(ctx, throttledKey); err != nil {
		return err
	}
	if err := f.redisClient.Delete(ctx, messageKey); err != nil {
		return err
	}
	if err := f.redisClient.Delete(ctx, byteKey); err != nil {
		return err
	}

	f.logger.Info("手动解除IP限流", zap.String("ip", ip))
	return nil
}

// ThrottleIP 手动限流IP
func (f *FlowController) ThrottleIP(ctx context.Context, ip string, reason string, duration time.Duration) error {
	if f.redisClient == nil {
		f.localStats.mu.Lock()
		f.localStats.throttled[ip] = time.Now().UnixMilli()
		f.localStats.mu.Unlock()
		return nil
	}

	throttledKey := IPThrottledKeyPrefix + ip
	throttledValue := fmt.Sprintf("%s:%d", reason, time.Now().UnixMilli())

	if duration <= 0 {
		duration = f.config.ThrottleDuration
	}

	return f.redisClient.SetWithExpire(ctx, throttledKey, throttledValue, duration)
}

// GetIPMessageRate 获取IP的消息频率
func (f *FlowController) GetIPMessageRate(ctx context.Context, ip string) int64 {
	if f.redisClient == nil {
		f.localStats.mu.RLock()
		defer f.localStats.mu.RUnlock()
		return f.localStats.messageCount[ip]
	}

	messageKey := IPMessageCountKeyPrefix + ip
	count, err := f.redisClient.GetInt64(ctx, messageKey)
	if err != nil {
		f.logger.Error("获取IP消息频率异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return 0
	}
	return count
}

// GetIPBandwidthUsage 获取IP的带宽使用
func (f *FlowController) GetIPBandwidthUsage(ctx context.Context, ip string) int64 {
	if f.redisClient == nil {
		f.localStats.mu.RLock()
		defer f.localStats.mu.RUnlock()
		return f.localStats.byteCount[ip]
	}

	byteKey := IPByteCountKeyPrefix + ip
	bytes, err := f.redisClient.GetInt64(ctx, byteKey)
	if err != nil {
		f.logger.Error("获取IP带宽使用异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return 0
	}
	return bytes
}

// IsIPThrottled 检查IP是否被限流（公共接口）
func (f *FlowController) IsIPThrottled(ctx context.Context, ip string) bool {
	return f.isThrottled(ctx, ip)
}

// getClientIP 获取客户端IP地址
func (f *FlowController) getClientIP(remoteAddr net.Addr) string {
	if remoteAddr == nil {
		return "unknown"
	}

	switch addr := remoteAddr.(type) {
	case *net.TCPAddr:
		return addr.IP.String()
	case *net.UDPAddr:
		return addr.IP.String()
	default:
		host, _, err := net.SplitHostPort(remoteAddr.String())
		if err != nil {
			return "unknown"
		}
		return host
	}
}

// GetStats 获取流控统计信息
func (f *FlowController) GetStats() string {
	return fmt.Sprintf(`=== 分布式流量控制统计 ===
每秒最大消息数: %d
单条消息最大大小: %d bytes
每秒最大带宽: %d bytes
限流时长: %v
Redis Key前缀:
  消息计数: %s
  字节计数: %s
  限流记录: %s
`,
		f.config.MaxMessagesPerSecond,
		f.config.MaxMessageSize,
		f.config.MaxBytesPerSecond,
		f.config.ThrottleDuration,
		IPMessageCountKeyPrefix,
		IPByteCountKeyPrefix,
		IPThrottledKeyPrefix)
}
