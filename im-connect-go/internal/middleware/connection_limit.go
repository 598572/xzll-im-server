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
	IPConnectionCountKeyPrefix = "im:limit:conn:"
	IPConnectionRateKeyPrefix  = "im:limit:rate:"
	GlobalConnectionCountKey   = "im:limit:global:count"
	IPBlockedKeyPrefix         = "im:limit:blocked:"
)

// ConnectionLimitConfig 连接限制配置（对标 Java ConnectionLimitHandler）
type ConnectionLimitConfig struct {
	MaxConnectionsPerIP     int           `yaml:"max_connections_per_ip"`     // 单个IP最大连接数
	MaxTotalConnections     int           `yaml:"max_total_connections"`      // 全局最大连接数
	MaxConnectionsPerMinute int           `yaml:"max_connections_per_minute"` // 每分钟最大连接数
	Enabled                 bool          `yaml:"enabled"`                    // 是否启用
	IPConnectionTTL         time.Duration `yaml:"ip_connection_ttl"`          // IP连接计数过期时间
	RateLimitTTL            time.Duration `yaml:"rate_limit_ttl"`             // 频率限制过期时间
}

// DefaultConnectionLimitConfig 默认配置
func DefaultConnectionLimitConfig() *ConnectionLimitConfig {
	return &ConnectionLimitConfig{
		MaxConnectionsPerIP:     1000,   // 单IP最大1000连接
		MaxTotalConnections:     100000, // 全局最大10万连接
		MaxConnectionsPerMinute: 6000,   // 每分钟最大6000连接
		Enabled:                 true,
		IPConnectionTTL:         1 * time.Hour,
		RateLimitTTL:            1 * time.Minute,
	}
}

// ConnectionLimiter 连接限制器（对标 Java ConnectionLimitHandler）
// 功能：
// 1. 限制单个IP的最大连接数（分布式）
// 2. 限制全局最大连接数（分布式）
// 3. 连接频率限制（分布式）
// 4. 支持分布式部署，所有数据存储在Redis中
type ConnectionLimiter struct {
	config      *ConnectionLimitConfig
	redisClient *redis.RedisClient
	logger      *zap.Logger
	mu          sync.RWMutex
	localStats  *LocalConnectionStats // 本地统计（快速路径）
}

// LocalConnectionStats 本地连接统计
type LocalConnectionStats struct {
	totalConnections int64
	ipConnections    map[string]int64
	mu               sync.RWMutex
}

// NewConnectionLimiter 创建连接限制器
func NewConnectionLimiter(config *ConnectionLimitConfig, redisClient *redis.RedisClient, logger *zap.Logger) *ConnectionLimiter {
	if config == nil {
		config = DefaultConnectionLimitConfig()
	}

	return &ConnectionLimiter{
		config:      config,
		redisClient: redisClient,
		logger:      logger,
		localStats: &LocalConnectionStats{
			ipConnections: make(map[string]int64),
		},
	}
}

// CheckConnection 检查连接是否允许
// 返回 true 表示允许连接，false 表示拒绝
func (l *ConnectionLimiter) CheckConnection(ctx context.Context, remoteAddr net.Addr) (bool, string) {
	if !l.config.Enabled {
		return true, ""
	}

	clientIP := l.getClientIP(remoteAddr)

	// 1. 检查IP是否被封禁
	if l.isIPBlocked(ctx, clientIP) {
		l.logger.Warn("IP已被封禁，拒绝连接",
			zap.String("ip", clientIP),
		)
		return false, "IP已被封禁"
	}

	// 2. 检查全局连接数限制
	if !l.checkGlobalConnectionLimit(ctx) {
		l.logger.Warn("全局连接数超过限制，拒绝连接",
			zap.String("ip", clientIP),
			zap.Int("limit", l.config.MaxTotalConnections),
		)
		return false, "全局连接数超限"
	}

	// 3. 检查单IP连接数限制
	if !l.checkIPConnectionLimit(ctx, clientIP) {
		l.logger.Warn("IP连接数超过限制，拒绝连接",
			zap.String("ip", clientIP),
			zap.Int("limit", l.config.MaxConnectionsPerIP),
		)
		return false, "IP连接数超限"
	}

	// 4. 检查连接频率限制
	if !l.checkIPConnectionRate(ctx, clientIP) {
		l.logger.Warn("IP连接频率超过限制，拒绝连接",
			zap.String("ip", clientIP),
			zap.Int("limit", l.config.MaxConnectionsPerMinute),
		)
		return false, "IP连接频率超限"
	}

	return true, ""
}

// OnConnect 连接建立时调用
func (l *ConnectionLimiter) OnConnect(ctx context.Context, remoteAddr net.Addr) {
	if !l.config.Enabled {
		return
	}

	clientIP := l.getClientIP(remoteAddr)
	l.incrementConnectionCounters(ctx, clientIP)

	l.logger.Debug("连接通过限制检查",
		zap.String("ip", clientIP),
	)
}

// OnDisconnect 连接断开时调用
func (l *ConnectionLimiter) OnDisconnect(ctx context.Context, remoteAddr net.Addr) {
	if !l.config.Enabled {
		return
	}

	clientIP := l.getClientIP(remoteAddr)
	l.decrementConnectionCounters(ctx, clientIP)

	l.logger.Debug("连接断开，更新计数器",
		zap.String("ip", clientIP),
	)
}

// ============= Redis 分布式限制功能 =============

// checkGlobalConnectionLimit 检查全局连接数限制
func (l *ConnectionLimiter) checkGlobalConnectionLimit(ctx context.Context) bool {
	if l.redisClient == nil {
		// 如果没有 Redis，使用本地统计
		l.localStats.mu.RLock()
		defer l.localStats.mu.RUnlock()
		return l.localStats.totalConnections < int64(l.config.MaxTotalConnections)
	}

	count, err := l.redisClient.GetInt64(ctx, GlobalConnectionCountKey)
	if err != nil {
		l.logger.Error("检查全局连接数限制异常", zap.Error(err))
		return true // 异常时不限制，避免误伤
	}

	return count < int64(l.config.MaxTotalConnections)
}

// checkIPConnectionLimit 检查IP连接数限制
func (l *ConnectionLimiter) checkIPConnectionLimit(ctx context.Context, ip string) bool {
	if l.redisClient == nil {
		l.localStats.mu.RLock()
		defer l.localStats.mu.RUnlock()
		return l.localStats.ipConnections[ip] < int64(l.config.MaxConnectionsPerIP)
	}

	connectionKey := IPConnectionCountKeyPrefix + ip
	count, err := l.redisClient.GetInt64(ctx, connectionKey)
	if err != nil {
		l.logger.Error("检查IP连接数限制异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return true
	}

	return count < int64(l.config.MaxConnectionsPerIP)
}

// checkIPConnectionRate 检查IP连接频率限制
func (l *ConnectionLimiter) checkIPConnectionRate(ctx context.Context, ip string) bool {
	if l.redisClient == nil {
		return true // 没有 Redis 时不做频率限制
	}

	rateKey := IPConnectionRateKeyPrefix + ip
	count, err := l.redisClient.GetInt64(ctx, rateKey)
	if err != nil {
		l.logger.Error("检查IP连接频率限制异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return true
	}

	return count < int64(l.config.MaxConnectionsPerMinute)
}

// incrementConnectionCounters 增加连接计数器
func (l *ConnectionLimiter) incrementConnectionCounters(ctx context.Context, ip string) {
	if l.redisClient == nil {
		l.localStats.mu.Lock()
		l.localStats.totalConnections++
		l.localStats.ipConnections[ip]++
		l.localStats.mu.Unlock()
		return
	}

	// 增加全局连接计数
	if err := l.redisClient.Incr(ctx, GlobalConnectionCountKey); err != nil {
		l.logger.Error("增加全局连接计数失败", zap.Error(err))
	}

	// 增加IP连接计数
	connectionKey := IPConnectionCountKeyPrefix + ip
	if err := l.redisClient.IncrWithExpire(ctx, connectionKey, l.config.IPConnectionTTL); err != nil {
		l.logger.Error("增加IP连接计数失败",
			zap.String("ip", ip),
			zap.Error(err),
		)
	}

	// 增加IP连接频率计数
	rateKey := IPConnectionRateKeyPrefix + ip
	if err := l.redisClient.IncrWithExpire(ctx, rateKey, l.config.RateLimitTTL); err != nil {
		l.logger.Error("增加IP连接频率计数失败",
			zap.String("ip", ip),
			zap.Error(err),
		)
	}
}

// decrementConnectionCounters 减少连接计数器
func (l *ConnectionLimiter) decrementConnectionCounters(ctx context.Context, ip string) {
	if l.redisClient == nil {
		l.localStats.mu.Lock()
		if l.localStats.totalConnections > 0 {
			l.localStats.totalConnections--
		}
		if l.localStats.ipConnections[ip] > 0 {
			l.localStats.ipConnections[ip]--
		}
		l.localStats.mu.Unlock()
		return
	}

	// 减少全局连接计数
	if err := l.redisClient.Decr(ctx, GlobalConnectionCountKey); err != nil {
		l.logger.Error("减少全局连接计数失败", zap.Error(err))
	}

	// 减少IP连接计数
	connectionKey := IPConnectionCountKeyPrefix + ip
	if err := l.redisClient.Decr(ctx, connectionKey); err != nil {
		l.logger.Error("减少IP连接计数失败",
			zap.String("ip", ip),
			zap.Error(err),
		)
	}
}

// isIPBlocked 检查IP是否被封禁
func (l *ConnectionLimiter) isIPBlocked(ctx context.Context, ip string) bool {
	if l.redisClient == nil {
		return false
	}

	blockedKey := IPBlockedKeyPrefix + ip
	exists, err := l.redisClient.Exists(ctx, blockedKey)
	if err != nil {
		l.logger.Error("检查IP封禁状态异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return false
	}

	return exists
}

// BlockIP 手动封禁IP
func (l *ConnectionLimiter) BlockIP(ctx context.Context, ip string, duration time.Duration) error {
	if l.redisClient == nil {
		return fmt.Errorf("Redis未配置")
	}

	blockedKey := IPBlockedKeyPrefix + ip
	blockedValue := fmt.Sprintf("%d", time.Now().UnixMilli())

	if duration > 0 {
		return l.redisClient.SetWithExpire(ctx, blockedKey, blockedValue, duration)
	}
	// 永久封禁
	return l.redisClient.Set(ctx, blockedKey, blockedValue)
}

// UnblockIP 解封IP
func (l *ConnectionLimiter) UnblockIP(ctx context.Context, ip string) error {
	if l.redisClient == nil {
		return fmt.Errorf("Redis未配置")
	}

	blockedKey := IPBlockedKeyPrefix + ip
	return l.redisClient.Delete(ctx, blockedKey)
}

// GetTotalConnections 获取全局连接数
func (l *ConnectionLimiter) GetTotalConnections(ctx context.Context) int64 {
	if l.redisClient == nil {
		l.localStats.mu.RLock()
		defer l.localStats.mu.RUnlock()
		return l.localStats.totalConnections
	}

	count, err := l.redisClient.GetInt64(ctx, GlobalConnectionCountKey)
	if err != nil {
		l.logger.Error("获取全局连接数异常", zap.Error(err))
		return 0
	}
	return count
}

// GetIPConnectionCount 获取指定IP的连接数
func (l *ConnectionLimiter) GetIPConnectionCount(ctx context.Context, ip string) int64 {
	if l.redisClient == nil {
		l.localStats.mu.RLock()
		defer l.localStats.mu.RUnlock()
		return l.localStats.ipConnections[ip]
	}

	connectionKey := IPConnectionCountKeyPrefix + ip
	count, err := l.redisClient.GetInt64(ctx, connectionKey)
	if err != nil {
		l.logger.Error("获取IP连接数异常",
			zap.String("ip", ip),
			zap.Error(err),
		)
		return 0
	}
	return count
}

// getClientIP 获取客户端IP地址
func (l *ConnectionLimiter) getClientIP(remoteAddr net.Addr) string {
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

// GetStats 获取连接统计信息
func (l *ConnectionLimiter) GetStats(ctx context.Context) string {
	total := l.GetTotalConnections(ctx)
	return fmt.Sprintf(`=== 分布式连接限制统计 ===
全局连接数: %d / %d
单IP最大连接: %d
每分钟最大连接: %d
Redis Key前缀:
  IP连接数: %s
  IP频率: %s
  全局计数: %s
  IP封禁: %s
`, total, l.config.MaxTotalConnections,
		l.config.MaxConnectionsPerIP,
		l.config.MaxConnectionsPerMinute,
		IPConnectionCountKeyPrefix,
		IPConnectionRateKeyPrefix,
		GlobalConnectionCountKey,
		IPBlockedKeyPrefix)
}
