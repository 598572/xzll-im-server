package redis

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"im-connect-go/internal/config"

	"github.com/go-redis/redis/v8"
	"go.uber.org/zap"
)

var (
	client *redis.Client
	logger *zap.Logger
	ctx    = context.Background()
)

// RedisClient Redis 客户端包装类型（用于依赖注入）
type RedisClient struct {
	client *redis.Client
	ctx    context.Context
}

// NewRedisClient 创建新的 Redis 客户端实例
func NewRedisClient(client *redis.Client) *RedisClient {
	return &RedisClient{
		client: client,
		ctx:    context.Background(),
	}
}

// GetInt64 获取 int64 值
func (r *RedisClient) GetInt64(ctx context.Context, key string) (int64, error) {
	result := r.client.Get(ctx, key)
	if result.Err() == redis.Nil {
		return 0, nil
	}
	return result.Int64()
}

// Incr 增加计数器
func (r *RedisClient) Incr(ctx context.Context, key string) error {
	return r.client.Incr(ctx, key).Err()
}

// Decr 减少计数器
func (r *RedisClient) Decr(ctx context.Context, key string) error {
	return r.client.Decr(ctx, key).Err()
}

// IncrWithExpire 增加计数器并设置过期时间
func (r *RedisClient) IncrWithExpire(ctx context.Context, key string, expiration time.Duration) error {
	pipe := r.client.TxPipeline()
	pipe.Incr(ctx, key)
	pipe.Expire(ctx, key, expiration)
	_, err := pipe.Exec(ctx)
	return err
}

// IncrByWithExpire 按指定数量增加计数器并设置过期时间
func (r *RedisClient) IncrByWithExpire(ctx context.Context, key string, value int64, expiration time.Duration) error {
	pipe := r.client.TxPipeline()
	pipe.IncrBy(ctx, key, value)
	pipe.Expire(ctx, key, expiration)
	_, err := pipe.Exec(ctx)
	return err
}

// Set 设置键值对
func (r *RedisClient) Set(ctx context.Context, key string, value interface{}) error {
	return r.client.Set(ctx, key, value, 0).Err()
}

// SetWithExpire 设置键值对并设置过期时间
func (r *RedisClient) SetWithExpire(ctx context.Context, key string, value interface{}, expiration time.Duration) error {
	return r.client.Set(ctx, key, value, expiration).Err()
}

// Get 获取字符串值
func (r *RedisClient) Get(ctx context.Context, key string) (string, error) {
	result := r.client.Get(ctx, key)
	if result.Err() == redis.Nil {
		return "", nil
	}
	return result.Result()
}

// Delete 删除键
func (r *RedisClient) Delete(ctx context.Context, keys ...string) error {
	return r.client.Del(ctx, keys...).Err()
}

// Exists 检查键是否存在
func (r *RedisClient) Exists(ctx context.Context, key string) (bool, error) {
	result, err := r.client.Exists(ctx, key).Result()
	if err != nil {
		return false, err
	}
	return result > 0, nil
}

// ZAdd 向有序集合添加成员
func (r *RedisClient) ZAdd(ctx context.Context, key string, score float64, member interface{}) error {
	return r.client.ZAdd(ctx, key, &redis.Z{
		Score:  score,
		Member: member,
	}).Err()
}

// ZRem 从有序集合移除成员
func (r *RedisClient) ZRem(ctx context.Context, key string, members ...interface{}) error {
	return r.client.ZRem(ctx, key, members...).Err()
}

// ZRangeByScore 按分数范围获取有序集合成员
func (r *RedisClient) ZRangeByScore(ctx context.Context, key string, min, max float64, limit int) ([]string, error) {
	return r.client.ZRangeByScore(ctx, key, &redis.ZRangeBy{
		Min:    fmt.Sprintf("%f", min),
		Max:    fmt.Sprintf("%f", max),
		Offset: 0,
		Count:  int64(limit),
	}).Result()
}

// HSet 设置哈希字段
func (r *RedisClient) HSet(ctx context.Context, key string, field string, value interface{}) error {
	return r.client.HSet(ctx, key, field, value).Err()
}

// HGet 获取哈希字段值
func (r *RedisClient) HGet(ctx context.Context, key string, field string) (string, error) {
	result := r.client.HGet(ctx, key, field)
	if result.Err() == redis.Nil {
		return "", nil
	}
	return result.Result()
}

// HDel 删除哈希字段
func (r *RedisClient) HDel(ctx context.Context, key string, fields ...string) error {
	return r.client.HDel(ctx, key, fields...).Err()
}

// InitRedis 初始化 Redis 客户端
func InitRedis(cfg *config.Config) error {
	logger = zap.NewNop() // 临时日志，实际应该传入

	// 创建 Redis 客户端
	client = redis.NewClient(&redis.Options{
		Addr:         cfg.Redis.Address,
		Password:     cfg.Redis.Password,
		DB:           cfg.Redis.DB,
		MaxRetries:   cfg.Redis.MaxRetries,
		PoolSize:     cfg.Redis.PoolSize,
		MinIdleConns: cfg.Redis.MinIdleConns,
		DialTimeout:  cfg.Redis.DialTimeout,
		ReadTimeout:  cfg.Redis.ReadTimeout,
		WriteTimeout: cfg.Redis.WriteTimeout,

		// 连接池配置
		PoolTimeout: 30 * time.Second,
		IdleTimeout: 5 * time.Minute,

		// 重试配置
		MaxRetryBackoff: 512 * time.Millisecond,
		MinRetryBackoff: 8 * time.Millisecond,
	})

	// 测试连接
	if err := client.Ping(ctx).Err(); err != nil {
		return fmt.Errorf("Redis 连接失败: %w", err)
	}

	logger.Info("✅ Redis 连接成功",
		zap.String("address", cfg.Redis.Address),
		zap.Int("db", cfg.Redis.DB),
		zap.Int("pool_size", cfg.Redis.PoolSize),
	)

	return nil
}

// Close 关闭 Redis 连接
func Close() error {
	if client != nil {
		return client.Close()
	}
	return nil
}

// GetClient 获取 Redis 客户端实例
func GetClient() *redis.Client {
	return client
}

// GetRedisClient 获取 RedisClient 包装实例
func GetRedisClient() *RedisClient {
	if client == nil {
		return nil
	}
	return NewRedisClient(client)
}

// ============================
// 键值操作
// ============================

// Set 设置键值对
func Set(key string, value interface{}, expiration time.Duration) error {
	return client.Set(ctx, key, value, expiration).Err()
}

// Get 获取键值
func Get(key string) (string, error) {
	result := client.Get(ctx, key)
	if result.Err() == redis.Nil {
		return "", nil // 键不存在
	}
	return result.Result()
}

// Del 删除键
func Del(keys ...string) error {
	return client.Del(ctx, keys...).Err()
}

// Exists 检查键是否存在
func Exists(keys ...string) (int64, error) {
	return client.Exists(ctx, keys...).Result()
}

// Expire 设置键的过期时间
func Expire(key string, expiration time.Duration) error {
	return client.Expire(ctx, key, expiration).Err()
}

// ============================
// 哈希操作
// ============================

// HSet 设置哈希字段
func HSet(key string, values ...interface{}) error {
	return client.HSet(ctx, key, values...).Err()
}

// HGet 获取哈希字段值
func HGet(key, field string) (string, error) {
	result := client.HGet(ctx, key, field)
	if result.Err() == redis.Nil {
		return "", nil
	}
	return result.Result()
}

// HGetAll 获取哈希的所有字段和值
func HGetAll(key string) (map[string]string, error) {
	return client.HGetAll(ctx, key).Result()
}

// HDel 删除哈希字段
func HDel(key string, fields ...string) error {
	return client.HDel(ctx, key, fields...).Err()
}

// HExists 检查哈希字段是否存在
func HExists(key, field string) (bool, error) {
	return client.HExists(ctx, key, field).Result()
}

// ============================
// 集合操作
// ============================

// SAdd 向集合添加成员
func SAdd(key string, members ...interface{}) error {
	return client.SAdd(ctx, key, members...).Err()
}

// SRem 从集合移除成员
func SRem(key string, members ...interface{}) error {
	return client.SRem(ctx, key, members...).Err()
}

// SMembers 获取集合的所有成员
func SMembers(key string) ([]string, error) {
	return client.SMembers(ctx, key).Result()
}

// SIsMember 检查成员是否在集合中
func SIsMember(key string, member interface{}) (bool, error) {
	return client.SIsMember(ctx, key, member).Result()
}

// ============================
// 有序集合操作
// ============================

// ZAdd 向有序集合添加成员
func ZAdd(key string, members ...*redis.Z) error {
	return client.ZAdd(ctx, key, members...).Err()
}

// ZRem 从有序集合移除成员
func ZRem(key string, members ...interface{}) error {
	return client.ZRem(ctx, key, members...).Err()
}

// ZRange 按索引范围获取有序集合成员
func ZRange(key string, start, stop int64) ([]string, error) {
	return client.ZRange(ctx, key, start, stop).Result()
}

// ZRangeByScore 按分数范围获取有序集合成员
func ZRangeByScore(key string, opt *redis.ZRangeBy) ([]string, error) {
	return client.ZRangeByScore(ctx, key, opt).Result()
}

// ============================
// 列表操作
// ============================

// LPush 从列表左侧推入元素
func LPush(key string, values ...interface{}) error {
	return client.LPush(ctx, key, values...).Err()
}

// RPush 从列表右侧推入元素
func RPush(key string, values ...interface{}) error {
	return client.RPush(ctx, key, values...).Err()
}

// LPop 从列表左侧弹出元素
func LPop(key string) (string, error) {
	result := client.LPop(ctx, key)
	if result.Err() == redis.Nil {
		return "", nil
	}
	return result.Result()
}

// RPop 从列表右侧弹出元素
func RPop(key string) (string, error) {
	result := client.RPop(ctx, key)
	if result.Err() == redis.Nil {
		return "", nil
	}
	return result.Result()
}

// LRange 获取列表指定范围的元素
func LRange(key string, start, stop int64) ([]string, error) {
	return client.LRange(ctx, key, start, stop).Result()
}

// LLen 获取列表长度
func LLen(key string) (int64, error) {
	return client.LLen(ctx, key).Result()
}

// ============================
// IM 专用功能
// ============================

// SetServerInfo 设置服务器信息（对标 Java NettyAttrUtil）
func SetServerInfo(serverIP string, info map[string]interface{}) error {
	const serverInfoKey = "im:servers"

	// 序列化服务器信息
	infoJSON, err := json.Marshal(info)
	if err != nil {
		return fmt.Errorf("序列化服务器信息失败: %w", err)
	}

	// 存储到哈希中
	return HSet(serverInfoKey, serverIP, string(infoJSON))
}

// GetServerInfo 获取服务器信息
func GetServerInfo(serverIP string) (map[string]interface{}, error) {
	const serverInfoKey = "im:servers"

	infoJSON, err := HGet(serverInfoKey, serverIP)
	if err != nil {
		return nil, err
	}

	if infoJSON == "" {
		return nil, nil // 服务器信息不存在
	}

	var info map[string]interface{}
	if err := json.Unmarshal([]byte(infoJSON), &info); err != nil {
		return nil, fmt.Errorf("反序列化服务器信息失败: %w", err)
	}

	return info, nil
}

// GetAllServerInfo 获取所有服务器信息
func GetAllServerInfo() (map[string]map[string]interface{}, error) {
	const serverInfoKey = "im:servers"

	serversJSON, err := HGetAll(serverInfoKey)
	if err != nil {
		return nil, err
	}

	servers := make(map[string]map[string]interface{})
	for serverIP, infoJSON := range serversJSON {
		var info map[string]interface{}
		if err := json.Unmarshal([]byte(infoJSON), &info); err != nil {
			logger.Warn("反序列化服务器信息失败",
				zap.String("server_ip", serverIP),
				zap.Error(err))
			continue
		}
		servers[serverIP] = info
	}

	return servers, nil
}

// SetUserServer 设置用户所在服务器（用于跨服务器转发）
func SetUserServer(userID, serverIP string) error {
	const userServerKey = "im:user_servers"
	return HSet(userServerKey, userID, serverIP)
}

// GetUserServer 获取用户所在服务器
func GetUserServer(userID string) (string, error) {
	const userServerKey = "im:user_servers"
	return HGet(userServerKey, userID)
}

// RemoveUserServer 移除用户服务器映射
func RemoveUserServer(userID string) error {
	const userServerKey = "im:user_servers"
	return HDel(userServerKey, userID)
}

// SetUserOnlineStatus 设置用户在线状态
func SetUserOnlineStatus(userID string, online bool, serverIP string) error {
	const onlineUsersKey = "im:online_users"

	if online {
		// 添加到在线用户集合，同时记录所在服务器
		if err := SAdd(onlineUsersKey, userID); err != nil {
			return err
		}
		return SetUserServer(userID, serverIP)
	} else {
		// 从在线用户集合移除，同时清理服务器映射
		if err := SRem(onlineUsersKey, userID); err != nil {
			return err
		}
		return RemoveUserServer(userID)
	}
}

// IsUserOnline 检查用户是否在线
func IsUserOnline(userID string) (bool, error) {
	const onlineUsersKey = "im:online_users"
	return SIsMember(onlineUsersKey, userID)
}

// GetOnlineUsers 获取所有在线用户
func GetOnlineUsers() ([]string, error) {
	const onlineUsersKey = "im:online_users"
	return SMembers(onlineUsersKey)
}

// GetOnlineUserCount 获取在线用户数量
func GetOnlineUserCount() (int64, error) {
	const onlineUsersKey = "im:online_users"
	return client.SCard(ctx, onlineUsersKey).Result()
}

// SaveOfflineMessage 保存离线消息
func SaveOfflineMessage(userID string, messageData map[string]interface{}) error {
	offlineKey := fmt.Sprintf("im:offline_messages:%s", userID)

	// 序列化消息数据
	messageJSON, err := json.Marshal(messageData)
	if err != nil {
		return fmt.Errorf("序列化离线消息失败: %w", err)
	}

	// 添加到列表末尾，使用时间戳作为分数
	score := float64(time.Now().Unix())
	return client.ZAdd(ctx, offlineKey, &redis.Z{
		Score:  score,
		Member: string(messageJSON),
	}).Err()
}

// GetOfflineMessages 获取离线消息
func GetOfflineMessages(userID string, limit int64) ([]map[string]interface{}, error) {
	offlineKey := fmt.Sprintf("im:offline_messages:%s", userID)

	// 获取最新的 limit 条消息
	messages, err := client.ZRevRange(ctx, offlineKey, 0, limit-1).Result()
	if err != nil {
		return nil, err
	}

	result := make([]map[string]interface{}, 0, len(messages))
	for _, messageJSON := range messages {
		var messageData map[string]interface{}
		if err := json.Unmarshal([]byte(messageJSON), &messageData); err != nil {
			logger.Warn("反序列化离线消息失败",
				zap.String("user_id", userID),
				zap.Error(err))
			continue
		}
		result = append(result, messageData)
	}

	return result, nil
}

// ClearOfflineMessages 清空用户的离线消息
func ClearOfflineMessages(userID string) error {
	offlineKey := fmt.Sprintf("im:offline_messages:%s", userID)
	return Del(offlineKey)
}

// ============================
// 分布式锁
// ============================

// AcquireLock 获取分布式锁
func AcquireLock(lockKey string, expiration time.Duration) (bool, error) {
	result := client.SetNX(ctx, lockKey, "locked", expiration)
	return result.Result()
}

// ReleaseLock 释放分布式锁
func ReleaseLock(lockKey string) error {
	return Del(lockKey)
}

// ============================
// 限流功能
// ============================

// IsRateLimited 检查是否被限流
func IsRateLimited(key string, maxRequests int64, window time.Duration) (bool, error) {
	now := time.Now().Unix()
	windowStart := now - int64(window.Seconds())

	// 使用有序集合实现滑动窗口限流
	pipe := client.TxPipeline()

	// 移除过期的请求记录
	pipe.ZRemRangeByScore(ctx, key, "0", fmt.Sprintf("%d", windowStart))

	// 添加当前请求
	pipe.ZAdd(ctx, key, &redis.Z{Score: float64(now), Member: now})

	// 获取当前窗口内的请求数量
	countCmd := pipe.ZCard(ctx, key)

	// 设置键的过期时间
	pipe.Expire(ctx, key, window)

	_, err := pipe.Exec(ctx)
	if err != nil {
		return false, err
	}

	count, err := countCmd.Result()
	if err != nil {
		return false, err
	}

	return count > maxRequests, nil
}

// ============================
// 统计功能
// ============================

// IncrementCounter 增加计数器
func IncrementCounter(key string) (int64, error) {
	return client.Incr(ctx, key).Result()
}

// IncrementCounterBy 按指定数量增加计数器
func IncrementCounterBy(key string, increment int64) (int64, error) {
	return client.IncrBy(ctx, key, increment).Result()
}

// GetCounter 获取计数器值
func GetCounter(key string) (int64, error) {
	result := client.Get(ctx, key)
	if result.Err() == redis.Nil {
		return 0, nil
	}
	return result.Int64()
}

// ============================
// 健康检查
// ============================

// HealthCheck Redis 健康检查
func HealthCheck() error {
	return client.Ping(ctx).Err()
}

// GetStats 获取 Redis 连接统计
func GetStats() map[string]interface{} {
	stats := client.PoolStats()
	return map[string]interface{}{
		"hits":        stats.Hits,
		"misses":      stats.Misses,
		"timeouts":    stats.Timeouts,
		"total_conns": stats.TotalConns,
		"idle_conns":  stats.IdleConns,
		"stale_conns": stats.StaleConns,
	}
}
