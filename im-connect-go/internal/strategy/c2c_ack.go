package strategy

import (
	"fmt"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	pb "im-connect-go/internal/proto"

	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// C2CMsgAckStrategy C2C 消息确认策略（对标 Java ClientReceivedMsgAckProtoStrategyImpl）
// 功能：
// 1. 处理客户端消息确认
// 2. 更新消息状态为已送达
// 3. 通知发送方消息已送达
// 4. 统计消息送达率
type C2CMsgAckStrategy struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.NbioManager

	// 统计信息
	totalAcks     int64
	ackErrorCount int64
}

// NewC2CMsgAckStrategy 创建消息确认策略
func NewC2CMsgAckStrategy(cfg *config.Config, logger *zap.Logger, cm *channel.NbioManager) *C2CMsgAckStrategy {
	return &C2CMsgAckStrategy{
		config:         cfg,
		logger:         logger,
		channelManager: cm,
	}
}

// SupportMsgType 返回支持的消息类型
func (s *C2CMsgAckStrategy) SupportMsgType() pb.MsgType {
	return pb.MsgType_CLIENT_RECEIVED_MSG_ACK
}

// Exchange 处理客户端消息确认（对标 Java exchange 方法）
func (s *C2CMsgAckStrategy) Exchange(conn channel.Connection, protoRequest *pb.ImProtoRequest) error {
	startTime := time.Now()
	userID := conn.GetUserID()

	s.logger.Debug("开始处理客户端消息确认",
		zap.String("user_id", userID),
		zap.Int("payload_size", len(protoRequest.Payload)),
	)

	// 1. 解析消息确认请求（对标 Java ClientReceivedMsgAck.parseFrom）
	ackReq := &pb.ClientReceivedMsgAck{}
	if err := proto.Unmarshal(protoRequest.Payload, ackReq); err != nil {
		s.logger.Error("解析消息确认请求失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		s.ackErrorCount++
		return fmt.Errorf("解析消息确认请求失败: %w", err)
	}

	s.logger.Info("处理消息确认",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", ackReq.MsgId),
		zap.String("client_msg_id", string(ackReq.ClientMsgId)), // TODO: 需要实现 UUID bytes 转换
		zap.Uint64("ack_time", ackReq.Time),
	)

	// 2. 验证确认消息
	if err := s.validateAckMessage(ackReq); err != nil {
		s.logger.Warn("消息确认验证失败",
			zap.String("user_id", userID),
			zap.Uint64("msg_id", ackReq.MsgId),
			zap.Error(err),
		)
		s.ackErrorCount++
		return err
	}

	// 3. 更新消息状态为已送达（对标 Java 数据库状态更新）
	if err := s.updateMessageDeliveryStatus(ackReq.MsgId, userID); err != nil {
		s.logger.Error("更新消息送达状态失败",
			zap.String("user_id", userID),
			zap.Uint64("msg_id", ackReq.MsgId),
			zap.Error(err),
		)
		// 状态更新失败但继续处理，确保通知发送方
	}

	// 4. 查找原消息的发送方（对标 Java 查询消息发送者）
	senderID, err := s.getMessageSender(ackReq.MsgId)
	if err != nil {
		s.logger.Error("查找消息发送者失败",
			zap.Uint64("msg_id", ackReq.MsgId),
			zap.Error(err),
		)
		s.ackErrorCount++
		return fmt.Errorf("查找消息发送者失败: %w", err)
	}

	// 5. 如果发送方在线，通知消息已送达（对标 Java 送达通知）
	if s.channelManager.IsUserOnline(senderID) {
		if err := s.notifyMessageDelivered(senderID, ackReq); err != nil {
			s.logger.Error("通知发送方消息已送达失败",
				zap.String("sender_id", senderID),
				zap.Uint64("msg_id", ackReq.MsgId),
				zap.Error(err),
			)
			// 通知失败不影响确认处理结果
		}
	} else {
		// 发送方不在线，可以考虑：
		// 1. 保存送达通知为离线消息
		// 2. 通过推送服务通知
		// 3. 或者忽略（发送方下次上线时主动查询）
		s.logger.Debug("发送方不在线，跳过送达通知",
			zap.String("sender_id", senderID),
			zap.Uint64("msg_id", ackReq.MsgId),
		)
	}

	// 6. 更新统计信息
	s.totalAcks++

	s.logger.Debug("消息确认处理完成",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", ackReq.MsgId),
		zap.String("sender_id", senderID),
		zap.Duration("processing_time", time.Since(startTime)),
	)

	return nil
}

// validateAckMessage 验证确认消息
func (s *C2CMsgAckStrategy) validateAckMessage(ackReq *pb.ClientReceivedMsgAck) error {
	// 1. 检查消息ID
	if ackReq.MsgId <= 0 {
		return fmt.Errorf("无效的消息ID: %d", ackReq.MsgId)
	}

	// 2. 检查客户端消息ID
	if len(ackReq.ClientMsgId) == 0 {
		return fmt.Errorf("客户端消息ID不能为空")
	}

	// 3. 检查确认时间
	if ackReq.Time <= 0 {
		return fmt.Errorf("无效的确认时间: %d", ackReq.Time)
	}

	// 4. 检查确认时间是否合理（不能太早或太晚）
	now := uint64(time.Now().UnixMilli())
	if ackReq.Time > now+5000 { // 不能超过当前时间5秒
		return fmt.Errorf("确认时间过晚: %d > %d", ackReq.Time, now)
	}

	// 5. 检查消息是否存在且属于该用户
	// TODO: 实现消息存在性和权限验证

	return nil
}

// updateMessageDeliveryStatus 更新消息送达状态（对标 Java 数据库更新）
func (s *C2CMsgAckStrategy) updateMessageDeliveryStatus(msgID uint64, receiverID string) error {
	// TODO: 实现数据库更新逻辑
	// UPDATE messages SET delivery_status = 'delivered', delivery_time = NOW()
	// WHERE msg_id = ? AND to_user_id = ?

	s.logger.Debug("更新消息送达状态",
		zap.Uint64("msg_id", msgID),
		zap.String("receiver_id", receiverID),
	)

	// 模拟数据库更新
	return nil
}

// getMessageSender 查找消息发送者（对标 Java 数据库查询）
func (s *C2CMsgAckStrategy) getMessageSender(msgID uint64) (string, error) {
	// TODO: 实现数据库查询逻辑
	// SELECT from_user_id FROM messages WHERE msg_id = ?

	s.logger.Debug("查找消息发送者",
		zap.Uint64("msg_id", msgID),
	)

	// 模拟查询结果（实际应从数据库查询）
	return "mock_sender_id", nil
}

// notifyMessageDelivered 通知发送方消息已送达（对标 Java 送达通知）
func (s *C2CMsgAckStrategy) notifyMessageDelivered(senderID string, ackReq *pb.ClientReceivedMsgAck) error {
	// 1. 构建送达通知消息
	deliveryNotification := &pb.MessageDeliveryNotification{
		MsgId:       ackReq.MsgId,
		ClientMsgId: ackReq.ClientMsgId,
		DeliveredAt: ackReq.Time,
		ReceiverAck: true,
	}

	// 2. 序列化通知消息
	notifyPayload, err := proto.Marshal(deliveryNotification)
	if err != nil {
		return fmt.Errorf("序列化送达通知失败: %w", err)
	}

	// 3. 构建 Protobuf 响应
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_MSG_DELIVERY_NOTIFICATION,
		Payload: notifyPayload,
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 4. 序列化响应
	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化送达通知响应失败: %w", err)
	}

	// 5. 发送给发送方
	if err := s.channelManager.BroadcastToUser(senderID, responseData); err != nil {
		return fmt.Errorf("发送送达通知失败: %w", err)
	}

	s.logger.Debug("送达通知已发送",
		zap.String("sender_id", senderID),
		zap.Uint64("msg_id", ackReq.MsgId),
		zap.Int("notification_size", len(responseData)),
	)

	return nil
}

// GetAckStats 获取确认统计信息
func (s *C2CMsgAckStrategy) GetAckStats() AckStats {
	return AckStats{
		TotalAcks:   s.totalAcks,
		ErrorCount:  s.ackErrorCount,
		SuccessRate: s.calculateSuccessRate(),
	}
}

// AckStats 确认统计信息
type AckStats struct {
	TotalAcks   int64   `json:"total_acks"`
	ErrorCount  int64   `json:"error_count"`
	SuccessRate float64 `json:"success_rate"`
}

// calculateSuccessRate 计算成功率
func (s *C2CMsgAckStrategy) calculateSuccessRate() float64 {
	if s.totalAcks+s.ackErrorCount == 0 {
		return 0.0
	}
	return float64(s.totalAcks) / float64(s.totalAcks+s.ackErrorCount) * 100
}

// 批量处理相关方法（性能优化）

// BatchUpdateDeliveryStatus 批量更新送达状态（性能优化）
func (s *C2CMsgAckStrategy) BatchUpdateDeliveryStatus(acks []*pb.ClientReceivedMsgAck, receiverID string) error {
	// TODO: 实现批量数据库更新
	// 可以显著提高高并发场景下的性能

	s.logger.Debug("批量更新送达状态",
		zap.String("receiver_id", receiverID),
		zap.Int("ack_count", len(acks)),
	)

	return fmt.Errorf("批量更新功能未实现")
}

// BatchNotifyDelivered 批量通知送达（性能优化）
func (s *C2CMsgAckStrategy) BatchNotifyDelivered(senderID string, acks []*pb.ClientReceivedMsgAck) error {
	// TODO: 实现批量通知逻辑
	// 可以将多个送达通知合并为一个消息发送

	s.logger.Debug("批量通知送达",
		zap.String("sender_id", senderID),
		zap.Int("ack_count", len(acks)),
	)

	return fmt.Errorf("批量通知功能未实现")
}

// 扩展功能

// GetUnackedMessages 获取未确认消息（扩展功能）
func (s *C2CMsgAckStrategy) GetUnackedMessages(userID string, limit int) ([]*pb.C2CMessage, error) {
	// TODO: 查询用户的未确认消息
	// 可用于客户端重连后的状态同步

	s.logger.Debug("获取未确认消息",
		zap.String("user_id", userID),
		zap.Int("limit", limit),
	)

	return nil, fmt.Errorf("未确认消息查询功能未实现")
}

// ResendUnackedMessages 重发未确认消息（扩展功能）
func (s *C2CMsgAckStrategy) ResendUnackedMessages(userID string, maxAge time.Duration) error {
	// TODO: 重发超过一定时间未确认的消息
	// 确保消息可靠送达

	s.logger.Debug("重发未确认消息",
		zap.String("user_id", userID),
		zap.Duration("max_age", maxAge),
	)

	return fmt.Errorf("重发未确认消息功能未实现")
}

// SetMessageReadStatus 设置消息已读状态（扩展功能）
func (s *C2CMsgAckStrategy) SetMessageReadStatus(userID string, msgID uint64, readTime uint64) error {
	// TODO: 更新消息已读状态
	// 这与送达确认不同，送达确认表示客户端收到了消息，已读表示用户看到了消息

	s.logger.Debug("设置消息已读状态",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", msgID),
		zap.Uint64("read_time", readTime),
	)

	return fmt.Errorf("消息已读状态功能未实现")
}
