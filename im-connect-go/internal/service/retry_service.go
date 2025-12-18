package service

import (
	"context"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"im-connect-go/internal/channel"
	pb "im-connect-go/internal/proto"
	"im-connect-go/pkg/redis"

	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// ============= Redis Key 定义 =============
const (
	C2CMsgRetryQueue = "im:c2c:retry:queue" // ZSet 延迟队列
	C2CMsgRetryIndex = "im:c2c:retry:index" // Hash 消息索引
)

// C2CMsgRetryConfig 消息重试配置（对标 Java C2CMsgRetryServiceImpl）
type C2CMsgRetryConfig struct {
	Enabled      bool          `yaml:"enabled"`       // 是否启用
	MaxRetries   int           `yaml:"max_retries"`   // 最大重试次数
	RetryDelays  []int         `yaml:"retry_delays"`  // 重试延迟（秒）
	BatchSize    int           `yaml:"batch_size"`    // 每次扫描最多处理的消息数量
	ScanInterval time.Duration `yaml:"scan_interval"` // 定时任务扫描间隔
}

// DefaultC2CMsgRetryConfig 默认配置
func DefaultC2CMsgRetryConfig() *C2CMsgRetryConfig {
	return &C2CMsgRetryConfig{
		Enabled:      true,
		MaxRetries:   3,
		RetryDelays:  []int{5, 30, 300}, // 5秒、30秒、5分钟
		BatchSize:    10000,
		ScanInterval: 1 * time.Second,
	}
}

// C2CMsgRetryEvent 重试事件（对标 Java C2CMsgRetryEvent）
type C2CMsgRetryEvent struct {
	ClientMsgID   string `json:"client_msg_id"`
	MsgID         string `json:"msg_id"`
	FromUserID    string `json:"from_user_id"`
	ToUserID      string `json:"to_user_id"`
	ChatID        string `json:"chat_id"`
	RetryCount    int    `json:"retry_count"`
	MsgContent    string `json:"msg_content"`
	MsgFormat     int32  `json:"msg_format"`
	MsgCreateTime int64  `json:"msg_create_time"`
	MaxRetries    int    `json:"max_retries"`
	CreateTime    string `json:"create_time"`
}

// C2CMsgRetryService 消息重试服务（对标 Java C2CMsgRetryServiceImpl）
// 使用Redis ZSet实现延迟队列，定时任务扫描到期消息
// ps: 此机制是消息可靠性重要一环！提供了服务端消息重推的机制
type C2CMsgRetryService struct {
	config         *C2CMsgRetryConfig
	redisClient    *redis.RedisClient
	channelManager *channel.Manager
	logger         *zap.Logger

	stopChan chan struct{}
	wg       sync.WaitGroup
}

// NewC2CMsgRetryService 创建消息重试服务
func NewC2CMsgRetryService(
	config *C2CMsgRetryConfig,
	redisClient *redis.RedisClient,
	channelManager *channel.Manager,
	logger *zap.Logger,
) *C2CMsgRetryService {
	if config == nil {
		config = DefaultC2CMsgRetryConfig()
	}

	// 验证配置一致性
	if config.MaxRetries != len(config.RetryDelays) {
		logger.Warn("配置不一致，自动调整 maxRetries",
			zap.Int("maxRetries", config.MaxRetries),
			zap.Int("retryDelays.length", len(config.RetryDelays)),
		)
		config.MaxRetries = len(config.RetryDelays)
	}

	return &C2CMsgRetryService{
		config:         config,
		redisClient:    redisClient,
		channelManager: channelManager,
		logger:         logger,
		stopChan:       make(chan struct{}),
	}
}

// Start 启动重试服务
func (s *C2CMsgRetryService) Start(ctx context.Context) {
	if !s.config.Enabled {
		s.logger.Info("[C2C消息重试服务] 重试机制未启用")
		return
	}

	s.logger.Info("[C2C消息重试服务] 配置初始化完成",
		zap.Int("max_retries", s.config.MaxRetries),
		zap.Ints("retry_delays", s.config.RetryDelays),
		zap.Int("batch_size", s.config.BatchSize),
		zap.Duration("scan_interval", s.config.ScanInterval),
	)

	s.wg.Add(1)
	go s.scanRetryQueueRoutine(ctx)
}

// Stop 停止重试服务
func (s *C2CMsgRetryService) Stop() {
	close(s.stopChan)
	s.wg.Wait()
	s.logger.Info("[C2C消息重试服务] 已停止")
}

// AddToRetryQueue 添加消息到延迟队列（等待客户端（接收方）ACK）
// 在C2CMsgSendStrategy中调用
func (s *C2CMsgRetryService) AddToRetryQueue(ctx context.Context, event *C2CMsgRetryEvent) error {
	if !s.config.Enabled {
		s.logger.Debug("[C2C消息重试服务] 重试机制未启用，跳过",
			zap.String("client_msg_id", event.ClientMsgID),
		)
		return nil
	}

	// 序列化重试事件
	jsonValue, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("序列化重试事件失败: %w", err)
	}

	// 计算执行时间戳（当前时间 + 延迟时间）
	executeTime := time.Now().UnixMilli() + int64(s.config.RetryDelays[0]*1000)

	// 添加到 ZSet（延迟队列）
	if err := s.redisClient.ZAdd(ctx, C2CMsgRetryQueue, float64(executeTime), event.MsgID); err != nil {
		return fmt.Errorf("添加到延迟队列失败: %w", err)
	}

	// 添加到 Hash（消息索引）
	if err := s.redisClient.HSet(ctx, C2CMsgRetryIndex, event.MsgID, string(jsonValue)); err != nil {
		return fmt.Errorf("添加到消息索引失败: %w", err)
	}

	s.logger.Info("[C2C消息重试服务] 消息已添加到延迟队列",
		zap.String("client_msg_id", event.ClientMsgID),
		zap.String("msg_id", event.MsgID),
		zap.Int("delay_seconds", s.config.RetryDelays[0]),
	)

	return nil
}

// RemoveFromRetryQueue 从延迟队列删除消息（收到客户端ACK时）
func (s *C2CMsgRetryService) RemoveFromRetryQueue(ctx context.Context, msgID string) error {
	// 从 ZSet 删除
	if err := s.redisClient.ZRem(ctx, C2CMsgRetryQueue, msgID); err != nil {
		return fmt.Errorf("从延迟队列删除失败: %w", err)
	}

	// 从 Hash 删除
	if err := s.redisClient.HDel(ctx, C2CMsgRetryIndex, msgID); err != nil {
		return fmt.Errorf("从消息索引删除失败: %w", err)
	}

	s.logger.Info("[C2C消息重试服务] 收到客户端ACK，从延迟队列删除消息",
		zap.String("msg_id", msgID),
	)

	return nil
}

// scanRetryQueueRoutine 定时扫描延迟队列
func (s *C2CMsgRetryService) scanRetryQueueRoutine(ctx context.Context) {
	defer s.wg.Done()

	ticker := time.NewTicker(s.config.ScanInterval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return
		case <-s.stopChan:
			return
		case <-ticker.C:
			s.scanRetryQueue(ctx)
		}
	}
}

// scanRetryQueue 扫描延迟队列
func (s *C2CMsgRetryService) scanRetryQueue(ctx context.Context) {
	currentTime := time.Now().UnixMilli()

	// 从 ZSet 获取到期的 msgID 列表
	expiredMsgIDs, err := s.redisClient.ZRangeByScore(ctx, C2CMsgRetryQueue, 0, float64(currentTime), s.config.BatchSize)
	if err != nil {
		s.logger.Error("[C2C消息重试服务] 扫描延迟队列异常", zap.Error(err))
		return
	}

	if len(expiredMsgIDs) == 0 {
		return // 没有到期的消息
	}

	s.logger.Debug("[C2C消息重试服务] 扫描到到期消息",
		zap.Int("count", len(expiredMsgIDs)),
		zap.Int("batch_size", s.config.BatchSize),
	)

	if len(expiredMsgIDs) >= s.config.BatchSize {
		s.logger.Warn("[C2C消息重试服务] 本次扫描达到批量上限，可能还有更多到期消息",
			zap.Int("batch_size", s.config.BatchSize),
		)
	}

	// 按用户分组处理
	groupedByUser := make(map[string][]*C2CMsgRetryEvent)

	for _, msgID := range expiredMsgIDs {
		// 从 Hash 获取完整数据
		jsonValue, err := s.redisClient.HGet(ctx, C2CMsgRetryIndex, msgID)
		if err != nil || jsonValue == "" {
			s.logger.Warn("[C2C消息重试服务] 消息数据不存在",
				zap.String("msg_id", msgID),
			)
			continue
		}

		var event C2CMsgRetryEvent
		if err := json.Unmarshal([]byte(jsonValue), &event); err != nil {
			s.logger.Error("[C2C消息重试服务] 解析消息数据异常",
				zap.String("msg_id", msgID),
				zap.Error(err),
			)
			continue
		}

		event.MsgID = msgID
		groupedByUser[event.ToUserID] = append(groupedByUser[event.ToUserID], &event)
	}

	// 按用户批量处理
	for toUserID, events := range groupedByUser {
		go s.processRetryBatch(ctx, toUserID, events)
	}
}

// processRetryBatch 批量处理重试（按用户分组）
func (s *C2CMsgRetryService) processRetryBatch(ctx context.Context, toUserID string, events []*C2CMsgRetryEvent) {
	if len(events) == 0 {
		return
	}

	// 检查接收人是否在线
	conns := s.channelManager.GetUserConnections(toUserID)
	isOnline := len(conns) > 0
	var conn channel.Connection
	if isOnline {
		conn = conns[0] // 使用第一个连接
	}

	for _, event := range events {
		// 从 ZSet 删除（避免重复处理）
		if err := s.redisClient.ZRem(ctx, C2CMsgRetryQueue, event.MsgID); err != nil {
			s.logger.Error("[C2C消息重试服务] 从延迟队列删除失败",
				zap.String("msg_id", event.MsgID),
				zap.Error(err),
			)
		}

		// 检查是否已收到客户端 ACK
		indexValue, err := s.redisClient.HGet(ctx, C2CMsgRetryIndex, event.MsgID)
		if err != nil || indexValue == "" {
			s.logger.Debug("[C2C消息重试服务] 消息已收到客户端ACK，取消重试",
				zap.String("client_msg_id", event.ClientMsgID),
				zap.String("msg_id", event.MsgID),
			)
			continue
		}

		// 检查重试次数
		if event.RetryCount >= s.config.MaxRetries {
			s.logger.Warn("[C2C消息重试服务] 消息重试超过最大次数，改为离线消息",
				zap.String("client_msg_id", event.ClientMsgID),
				zap.String("msg_id", event.MsgID),
				zap.Int("retry_count", event.RetryCount),
				zap.Int("max_retries", s.config.MaxRetries),
			)
			s.markAsOffline(ctx, event)
			continue
		}

		// 根据在线状态处理
		if isOnline && conn != nil {
			// 发送重试消息
			if err := s.sendRetryMessage(ctx, conn, event); err != nil {
				s.logger.Error("[C2C消息重试服务] 重试发送消息失败",
					zap.String("msg_id", event.MsgID),
					zap.Error(err),
				)
			}

			// 更新重试次数
			event.RetryCount++
			event.CreateTime = time.Now().Format("2006-01-02 15:04:05")

			// 检查是否还有下次重试
			if event.RetryCount >= len(s.config.RetryDelays) {
				s.logger.Warn("[C2C消息重试服务] 消息已达最大重试次数，标记为离线消息",
					zap.String("client_msg_id", event.ClientMsgID),
					zap.String("msg_id", event.MsgID),
					zap.Int("retry_count", event.RetryCount),
				)
				s.markAsOffline(ctx, event)
				continue
			}

			// 计算下次执行时间
			nextDelay := s.config.RetryDelays[event.RetryCount]
			executeTime := time.Now().UnixMilli() + int64(nextDelay*1000)

			// 重新添加到延迟队列
			jsonValue, _ := json.Marshal(event)
			if err := s.redisClient.ZAdd(ctx, C2CMsgRetryQueue, float64(executeTime), event.MsgID); err != nil {
				s.logger.Error("[C2C消息重试服务] 重新添加到延迟队列失败",
					zap.String("msg_id", event.MsgID),
					zap.Error(err),
				)
			}
			if err := s.redisClient.HSet(ctx, C2CMsgRetryIndex, event.MsgID, string(jsonValue)); err != nil {
				s.logger.Error("[C2C消息重试服务] 更新消息索引失败",
					zap.String("msg_id", event.MsgID),
					zap.Error(err),
				)
			}

			s.logger.Debug("[C2C消息重试服务] 消息重试成功，已添加下次重试任务",
				zap.String("client_msg_id", event.ClientMsgID),
				zap.String("msg_id", event.MsgID),
				zap.Int("retry_count", event.RetryCount),
				zap.Int("next_delay_seconds", nextDelay),
			)
		} else {
			s.logger.Warn("[C2C消息重试服务] 重试时接收人已离线，改为离线消息",
				zap.String("client_msg_id", event.ClientMsgID),
				zap.String("msg_id", event.MsgID),
			)
			s.markAsOffline(ctx, event)
		}
	}
}

// sendRetryMessage 发送重试消息
func (s *C2CMsgRetryService) sendRetryMessage(ctx context.Context, conn channel.Connection, event *C2CMsgRetryEvent) error {
	// 构建推送消息
	pushMsg := &pb.C2CMsgPush{
		ClientMsgId: []byte(event.ClientMsgID),
		MsgId:       parseUint64(event.MsgID),
		From:        parseUint64(event.FromUserID),
		To:          parseUint64(event.ToUserID),
		Format:      event.MsgFormat,
		Content:     event.MsgContent,
		Time:        uint64(event.MsgCreateTime),
	}

	// 构建响应
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_C2C_MSG_PUSH,
		Payload: mustMarshal(pushMsg),
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 序列化
	data, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化响应失败: %w", err)
	}

	// 发送
	return conn.SendBinary(data)
}

// markAsOffline 标记为离线消息
func (s *C2CMsgRetryService) markAsOffline(ctx context.Context, event *C2CMsgRetryEvent) {
	// 从 Hash 索引删除
	if err := s.redisClient.HDel(ctx, C2CMsgRetryIndex, event.MsgID); err != nil {
		s.logger.Error("[C2C消息重试服务] 删除消息索引失败",
			zap.String("msg_id", event.MsgID),
			zap.Error(err),
		)
	}

	// TODO: 发送离线消息到 im-business 服务
	// 这里需要实现 RPC 调用或消息队列推送
	s.logger.Info("[C2C消息重试服务] 消息已改为离线消息",
		zap.String("client_msg_id", event.ClientMsgID),
		zap.String("msg_id", event.MsgID),
	)
}

// parseUint64 解析 uint64
func parseUint64(s string) uint64 {
	var result uint64
	fmt.Sscanf(s, "%d", &result)
	return result
}

// mustMarshal 序列化（忽略错误）
func mustMarshal(m proto.Message) []byte {
	data, _ := proto.Marshal(m)
	return data
}
