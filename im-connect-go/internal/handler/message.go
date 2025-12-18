package handler

import (
	"fmt"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	pb "im-connect-go/internal/proto"
	"im-connect-go/internal/strategy"
	"im-connect-go/pkg/mq"
	"im-connect-go/pkg/redis"

	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// MessageHandler 消息处理器（对标 Java HandlerDispatcher + WebSocketServerHandler）
// 功能：
// 1. Protobuf 消息解析和分发
// 2. 消息类型路由（策略模式）
// 3. 消息处理统计和监控
// 4. 异常处理和容错
// 5. 性能优化（异步处理、批量处理等）
type MessageHandler struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.Manager
	mqProducer     *mq.Producer       // RocketMQ 生产者
	redisClient    *redis.RedisClient // Redis 客户端

	// 消息处理策略映射（对标 Java protoHandlers）
	strategies map[pb.MsgType]strategy.ProtoMsgHandlerStrategy

	// 统计信息
	messageStats MessageStats

	// 异步处理队列（可选优化）
	messageQueue chan *MessageTask
	workerCount  int
}

// MessageTask 消息处理任务
type MessageTask struct {
	Connection channel.Connection
	Message    []byte
	ReceivedAt time.Time
}

// MessageStats 消息处理统计
type MessageStats struct {
	TotalProcessed int64 `json:"total_processed"`
	TotalErrors    int64 `json:"total_errors"`
	C2CMessages    int64 `json:"c2c_messages"`
	Heartbeats     int64 `json:"heartbeats"`
	AckMessages    int64 `json:"ack_messages"`
	ProcessingTime int64 `json:"avg_processing_time_ms"`
}

// NewMessageHandler 创建消息处理器
func NewMessageHandler(cfg *config.Config, logger *zap.Logger, cm *channel.Manager, mqProducer *mq.Producer, redisClient *redis.RedisClient) *MessageHandler {
	handler := &MessageHandler{
		config:         cfg,
		logger:         logger,
		channelManager: cm,
		mqProducer:     mqProducer,
		redisClient:    redisClient,
		strategies:     make(map[pb.MsgType]strategy.ProtoMsgHandlerStrategy),
		messageQueue:   make(chan *MessageTask, 10000), // 消息队列大小
		workerCount:    cfg.GetNettyRuntimeConfig().WorkerThreads,
	}

	// 注册消息处理策略（对标 Java ApplicationContext 自动注入）
	handler.registerStrategies()

	// 启动异步处理worker（可选优化）
	handler.startWorkers()

	logger.Info("✅ 消息处理器初始化完成",
		zap.Int("strategy_count", len(handler.strategies)),
		zap.Int("worker_count", handler.workerCount),
		zap.Int("queue_size", cap(handler.messageQueue)),
	)

	return handler
}

// registerStrategies 注册消息处理策略（对标 Java setApplicationContext）
func (h *MessageHandler) registerStrategies() {
	// 注册 C2C 消息发送策略
	c2cSendStrategy := strategy.NewC2CMsgSendStrategy(h.config, h.logger, h.channelManager, h.mqProducer, h.redisClient)
	h.strategies[pb.MsgType_C2C_SEND] = c2cSendStrategy
	h.logger.Info("注册消息处理策略", zap.String("type", "C2C_SEND"))

	// 注册客户端消息确认策略
	c2cAckStrategy := strategy.NewC2CMsgAckStrategy(h.config, h.logger, h.channelManager)
	h.strategies[pb.MsgType_CLIENT_RECEIVED_MSG_ACK] = c2cAckStrategy
	h.logger.Info("注册消息处理策略", zap.String("type", "CLIENT_RECEIVED_MSG_ACK"))

	// 注册撤回消息策略
	withdrawStrategy := strategy.NewWithdrawMsgStrategy(h.config, h.logger, h.channelManager)
	h.strategies[pb.MsgType_WITHDRAW_MSG_SEND] = withdrawStrategy
	h.logger.Info("注册消息处理策略", zap.String("type", "WITHDRAW_MSG_SEND"))

	// TODO: 添加其他消息类型策略
	// - 群聊消息
	// - 文件传输
	// - 系统通知
	// 等等...
}

// HandleBinaryMessage 处理二进制消息（对标 Java WebSocketServerHandler BinaryWebSocketFrame 处理）
func (h *MessageHandler) HandleBinaryMessage(conn channel.Connection, message []byte) {
	startTime := time.Now()

	// 消息长度检查（对标 Java MAX_MESSAGE_LENGTH）
	nettyConfig := h.config.GetNettyRuntimeConfig()
	if len(message) > int(nettyConfig.MaxMessageSize) {
		h.logger.Warn("消息长度超过限制",
			zap.String("user_id", conn.GetUserID()),
			zap.Int("message_size", len(message)),
			zap.Int64("max_size", nettyConfig.MaxMessageSize),
		)
		h.messageStats.TotalErrors++
		return
	}

	// 异步处理模式（可选优化）
	if h.workerCount > 0 {
		task := &MessageTask{
			Connection: conn,
			Message:    message,
			ReceivedAt: time.Now(),
		}

		select {
		case h.messageQueue <- task:
			// 消息已加入队列
		default:
			// 队列满，直接处理
			h.logger.Warn("消息队列已满，直接处理",
				zap.String("user_id", conn.GetUserID()),
			)
			h.processBinaryMessage(conn, message, startTime)
		}
	} else {
		// 同步处理模式
		h.processBinaryMessage(conn, message, startTime)
	}
}

// processBinaryMessage 处理二进制消息的具体逻辑
func (h *MessageHandler) processBinaryMessage(conn channel.Connection, message []byte, startTime time.Time) {
	defer func() {
		// 记录处理时间
		processingTime := time.Since(startTime).Milliseconds()
		h.messageStats.ProcessingTime = (h.messageStats.ProcessingTime + processingTime) / 2 // 简单平均
	}()

	// 1. 解析 Protobuf 消息（对标 Java ImProtoRequest.parseFrom）
	protoRequest := &pb.ImProtoRequest{}
	if err := proto.Unmarshal(message, protoRequest); err != nil {
		h.logger.Error("解析 Protobuf 消息失败",
			zap.String("user_id", conn.GetUserID()),
			zap.Error(err),
			zap.Int("message_size", len(message)),
		)
		h.messageStats.TotalErrors++
		return
	}

	h.logger.Debug("收到 Protobuf 消息",
		zap.String("user_id", conn.GetUserID()),
		zap.String("msg_type", protoRequest.Type.String()),
		zap.Int("payload_size", len(protoRequest.Payload)),
	)

	// 2. 查找处理策略（对标 Java HandlerDispatcher.dispatcher）
	msgStrategy, exists := h.strategies[protoRequest.Type]
	if !exists {
		h.logger.Warn("未找到消息处理策略",
			zap.String("user_id", conn.GetUserID()),
			zap.String("msg_type", protoRequest.Type.String()),
		)
		h.messageStats.TotalErrors++
		return
	}

	// 3. 执行消息处理策略（对标 Java ProtoMsgHandlerStrategy.exchange）
	if err := msgStrategy.Exchange(conn, protoRequest); err != nil {
		h.logger.Error("消息处理失败",
			zap.String("user_id", conn.GetUserID()),
			zap.String("msg_type", protoRequest.Type.String()),
			zap.Error(err),
		)
		h.messageStats.TotalErrors++
		return
	}

	// 4. 更新统计信息
	h.messageStats.TotalProcessed++
	h.updateMessageTypeStats(protoRequest.Type)

	h.logger.Debug("消息处理完成",
		zap.String("user_id", conn.GetUserID()),
		zap.String("msg_type", protoRequest.Type.String()),
		zap.Duration("processing_time", time.Since(startTime)),
	)
}

// updateMessageTypeStats 更新消息类型统计
func (h *MessageHandler) updateMessageTypeStats(msgType pb.MsgType) {
	switch msgType {
	case pb.MsgType_C2C_SEND:
		h.messageStats.C2CMessages++
	case pb.MsgType_CLIENT_RECEIVED_MSG_ACK:
		h.messageStats.AckMessages++
	case pb.MsgType_HEARTBEAT:
		h.messageStats.Heartbeats++
	}
}

// startWorkers 启动异步处理worker
func (h *MessageHandler) startWorkers() {
	if h.workerCount <= 0 {
		return
	}

	for i := 0; i < h.workerCount; i++ {
		go h.messageWorker(i)
	}

	h.logger.Info("🚀 消息处理worker启动",
		zap.Int("worker_count", h.workerCount),
	)
}

// messageWorker 消息处理worker协程
func (h *MessageHandler) messageWorker(workerID int) {
	h.logger.Debug("消息处理worker启动",
		zap.Int("worker_id", workerID),
	)

	for task := range h.messageQueue {
		// 检查消息是否过期（可选）
		if time.Since(task.ReceivedAt) > 30*time.Second {
			h.logger.Warn("消息处理超时，丢弃",
				zap.String("user_id", task.Connection.GetUserID()),
				zap.Duration("age", time.Since(task.ReceivedAt)),
			)
			h.messageStats.TotalErrors++
			continue
		}

		// 处理消息
		h.processBinaryMessage(task.Connection, task.Message, task.ReceivedAt)
	}

	h.logger.Debug("消息处理worker停止",
		zap.Int("worker_id", workerID),
	)
}

// HandleCrossServerMessage 处理跨服务器消息（对标 Java TransferC2CMsgService.transferC2CMsg）
func (h *MessageHandler) HandleCrossServerMessage(protoRequest *pb.ImProtoRequest) error {
	h.logger.Info("收到跨服务器消息",
		zap.String("msg_type", protoRequest.Type.String()),
		zap.Int("payload_size", len(protoRequest.Payload)),
	)

	// 查找处理策略
	msgStrategy, exists := h.strategies[protoRequest.Type]
	if !exists {
		err := fmt.Errorf("未找到跨服务器消息处理策略: %s", protoRequest.Type.String())
		h.logger.Error("跨服务器消息处理失败", zap.Error(err))
		return err
	}

	// 检查策略是否支持跨服务器处理
	crossServerStrategy, ok := msgStrategy.(strategy.CrossServerMessageHandler)
	if !ok {
		err := fmt.Errorf("策略不支持跨服务器处理: %s", protoRequest.Type.String())
		h.logger.Error("跨服务器消息处理失败", zap.Error(err))
		return err
	}

	// 执行跨服务器消息处理
	return crossServerStrategy.ReceiveAndSendMsg(protoRequest)
}

// GetStats 获取消息处理统计信息
func (h *MessageHandler) GetStats() MessageStats {
	return h.messageStats
}

// Stop 停止消息处理器
func (h *MessageHandler) Stop() {
	h.logger.Info("🔄 停止消息处理器...")

	// 关闭消息队列
	close(h.messageQueue)

	// 等待所有消息处理完成（简单等待）
	time.Sleep(2 * time.Second)

	h.logger.Info("✅ 消息处理器已停止")
}

// SendMessageToUser 向用户发送消息（工具方法）
func (h *MessageHandler) SendMessageToUser(userID string, msgType pb.MsgType, payload []byte) error {
	// 构建 Protobuf 响应
	response := &pb.ImProtoResponse{
		Type:    msgType,
		Payload: payload,
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 序列化消息
	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化响应消息失败: %w", err)
	}

	// 发送给用户
	if err := h.channelManager.BroadcastToUser(userID, responseData); err != nil {
		return fmt.Errorf("发送消息给用户失败: %w", err)
	}

	h.logger.Debug("消息已发送给用户",
		zap.String("user_id", userID),
		zap.String("msg_type", msgType.String()),
		zap.Int("message_size", len(responseData)),
	)

	return nil
}

// BroadcastMessage 广播消息给所有在线用户（工具方法）
func (h *MessageHandler) BroadcastMessage(msgType pb.MsgType, payload []byte) error {
	// 构建 Protobuf 响应
	response := &pb.ImProtoResponse{
		Type:    msgType,
		Payload: payload,
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 序列化消息
	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化广播消息失败: %w", err)
	}

	// 获取所有在线用户
	onlineUsers := h.channelManager.GetOnlineUsers()

	successCount := 0
	errorCount := 0

	// 发送给所有在线用户
	for _, userID := range onlineUsers {
		if err := h.channelManager.BroadcastToUser(userID, responseData); err != nil {
			h.logger.Warn("广播消息失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
			errorCount++
		} else {
			successCount++
		}
	}

	h.logger.Info("消息广播完成",
		zap.String("msg_type", msgType.String()),
		zap.Int("total_users", len(onlineUsers)),
		zap.Int("success_count", successCount),
		zap.Int("error_count", errorCount),
		zap.Int("message_size", len(responseData)),
	)

	return nil
}

// GetQueueStatus 获取队列状态（监控用）
func (h *MessageHandler) GetQueueStatus() QueueStatus {
	return QueueStatus{
		QueueSize:     len(h.messageQueue),
		QueueCapacity: cap(h.messageQueue),
		WorkerCount:   h.workerCount,
	}
}

// QueueStatus 队列状态
type QueueStatus struct {
	QueueSize     int `json:"queue_size"`
	QueueCapacity int `json:"queue_capacity"`
	WorkerCount   int `json:"worker_count"`
}

// HandleHeartbeat 处理心跳消息（特殊处理）
func (h *MessageHandler) HandleHeartbeat(conn channel.Connection) error {
	h.messageStats.Heartbeats++

	h.logger.Debug("收到心跳",
		zap.String("user_id", conn.GetUserID()),
	)

	// 发送 Pong 响应
	return conn.SendPong([]byte("heartbeat"))
}

// 消息验证相关方法

// ValidateMessage 验证消息格式和内容
func (h *MessageHandler) ValidateMessage(protoRequest *pb.ImProtoRequest) error {
	// 1. 检查消息类型是否支持
	_, exists := h.strategies[protoRequest.Type]
	if !exists {
		return fmt.Errorf("不支持的消息类型: %s", protoRequest.Type.String())
	}

	// 2. 检查 Payload 是否为空
	if len(protoRequest.Payload) == 0 {
		return fmt.Errorf("消息负载为空")
	}

	// 3. 检查 Payload 大小
	nettyConfig := h.config.GetNettyRuntimeConfig()
	if len(protoRequest.Payload) > int(nettyConfig.MaxMessageSize) {
		return fmt.Errorf("消息负载过大: %d > %d", len(protoRequest.Payload), nettyConfig.MaxMessageSize)
	}

	// 4. 可以添加更多验证逻辑
	// - 消息内容格式验证
	// - 用户权限验证
	// - 频率限制验证
	// 等等...

	return nil
}

// 性能优化相关方法

// FlushPendingMessages 刷新待处理消息（批量处理优化）
func (h *MessageHandler) FlushPendingMessages() {
	// 这里可以实现批量消息处理逻辑
	// 例如：将多个小消息合并为一个大消息发送
	// 或者：批量写入数据库

	h.logger.Debug("刷新待处理消息")
}

// OptimizeMemoryUsage 优化内存使用
func (h *MessageHandler) OptimizeMemoryUsage() {
	// 这里可以实现内存优化逻辑
	// 例如：清理过期的消息缓存
	// 或者：压缩消息数据

	h.logger.Debug("优化内存使用")
}
