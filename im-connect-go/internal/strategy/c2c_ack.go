package strategy

import (
	"context"
	"fmt"
	"sync/atomic"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	pb "im-connect-go/internal/proto"
	"im-connect-go/internal/service"
	"im-connect-go/internal/util"

	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// C2CMsgAckStrategy C2C 消息确认策略（对标 Java ClientReceivedMsgAckProtoStrategyImpl）
// 功能：
// 1. 处理客户端消息确认
// 2. 更新消息状态为已送达
// 3. 通知发送方消息已送达
// 4. 统计消息送达率
// 5. 从重试队列删除消息（对标 Java c2CMsgRetryService.removeFromRetryQueue）
type C2CMsgAckStrategy struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.NbioManager
	retryService   *service.C2CMsgRetryService // 消息重试服务

	// 统计信息
	totalAcks     int64
	ackErrorCount int64
}

// NewC2CMsgAckStrategy 创建消息确认策略
func NewC2CMsgAckStrategy(cfg *config.Config, logger *zap.Logger, cm *channel.NbioManager, retryService *service.C2CMsgRetryService) *C2CMsgAckStrategy {
	return &C2CMsgAckStrategy{
		config:         cfg,
		logger:         logger,
		channelManager: cm,
		retryService:   retryService,
	}
}

// SupportMsgType 返回支持的消息类型
// ✅ 修复：使用 C2C_ACK（对标 Java ClientReceivedMsgAckProtoStrategyImpl）
func (s *C2CMsgAckStrategy) SupportMsgType() pb.MsgType {
	return pb.MsgType_C2C_ACK
}

// Exchange 处理客户端消息确认（对标 Java exchange 方法）
func (s *C2CMsgAckStrategy) Exchange(conn channel.Connection, protoRequest *pb.ImProtoRequest) error {
	startTime := time.Now()
	userID := conn.GetUserID()

	s.logger.Debug("开始处理客户端消息确认",
		zap.String("user_id", userID),
		zap.Int("payload_size", len(protoRequest.Payload)),
	)

	// 1. 解析消息确认请求（对标 Java C2CAckReq.parseFrom）
	// ✅ 修复：使用 C2CAckReq（与 Java 保持一致）
	ackReq := &pb.C2CAckReq{}
	if err := proto.Unmarshal(protoRequest.Payload, ackReq); err != nil {
		s.logger.Error("解析消息确认请求失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		atomic.AddInt64(&s.ackErrorCount, 1)
		return fmt.Errorf("解析消息确认请求失败: %w", err)
	}

	// ✅ 优化：高频操作使用 Debug 级别，避免影响性能
	s.logger.Info("📬 处理消息确认",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", ackReq.MsgId),
		zap.String("client_msg_id", util.BytesToUUIDString(ackReq.ClientMsgId)),
		zap.Uint64("from", ackReq.From),
		zap.Uint64("to", ackReq.To),
		zap.Int32("status", ackReq.Status),
	)

	// 2. 验证确认消息
	if err := s.validateAckMessage(ackReq); err != nil {
		s.logger.Warn("消息确认验证失败",
			zap.String("user_id", userID),
			zap.Uint64("msg_id", ackReq.MsgId),
			zap.Error(err),
		)
		atomic.AddInt64(&s.ackErrorCount, 1)
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

	// 4. ✅ 优化：从 ACK 请求中获取发送方 ID（无需查询数据库）
	// ACK 消息的 To 字段就是原消息的发送方（接收方发 ACK 给发送方）
	senderID := fmt.Sprintf("%d", ackReq.To)

	s.logger.Info("🔍 获取原消息发送方",
		zap.String("sender_id", senderID),
		zap.Uint64("msg_id", ackReq.MsgId),
		zap.Int32("ack_status", ackReq.Status),
	)

	// 5. 如果发送方在线，通知消息已送达（对标 Java 送达通知）
	if s.channelManager.IsUserOnline(senderID) {
		s.logger.Info("📢 发送方在线，准备发送送达通知",
			zap.String("sender_id", senderID),
			zap.Uint64("msg_id", ackReq.MsgId),
			zap.Int32("status", ackReq.Status),
		)
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
		s.logger.Info("ℹ️ 发送方不在线，跳过送达通知",
			zap.String("sender_id", senderID),
			zap.Uint64("msg_id", ackReq.MsgId),
			zap.Int32("status", ackReq.Status),
		)
	}

	// 6. ✅ 从重试队列删除消息（对标 Java c2CMsgRetryService.removeFromRetryQueue）
	if s.retryService != nil {
		msgIDStr := fmt.Sprintf("%d", ackReq.MsgId)
		if err := s.retryService.RemoveFromRetryQueue(context.Background(), msgIDStr); err != nil {
			s.logger.Warn("从重试队列删除消息失败",
				zap.Uint64("msg_id", ackReq.MsgId),
				zap.Error(err),
			)
			// 删除失败不影响确认处理结果
		}
	}

	// 7. ✅ 优化：使用原子操作更新统计信息（并发安全）
	atomic.AddInt64(&s.totalAcks, 1)

	s.logger.Info("✅ 消息确认处理完成",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", ackReq.MsgId),
		zap.String("sender_id", senderID),
		zap.Duration("processing_time", time.Since(startTime)),
	)

	return nil
}

// validateAckMessage 验证确认消息
// ✅ 修复：使用 C2CAckReq（与 Java 保持一致）
func (s *C2CMsgAckStrategy) validateAckMessage(ackReq *pb.C2CAckReq) error {
	// 1. 检查消息ID
	if ackReq.MsgId <= 0 {
		return fmt.Errorf("无效的消息ID: %d", ackReq.MsgId)
	}

	// 2. 检查客户端消息ID
	if len(ackReq.ClientMsgId) == 0 {
		return fmt.Errorf("客户端消息ID不能为空")
	}

	// 3. 检查状态值（对标 Java：1=SERVER_RECEIVED, 3=UN_READ, 4=READED）
	if ackReq.Status < 1 || ackReq.Status > 4 {
		return fmt.Errorf("无效的状态值: %d", ackReq.Status)
	}

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

// getMessageSender 查找消息发送者
// ✅ 优化：此方法已不再使用，发送方 ID 直接从 ACK 请求的 To 字段获取
// 保留此方法供需要从数据库查询的场景使用
func (s *C2CMsgAckStrategy) getMessageSender(msgID uint64) (string, error) {
	// 注意：在当前实现中，发送方 ID 直接从 ACK 请求中获取（ackReq.To）
	// 此方法仅在需要从数据库查询时使用
	s.logger.Debug("查找消息发送者",
		zap.Uint64("msg_id", msgID),
	)

	// TODO: 如需从数据库查询，实现此逻辑
	// SELECT from_user_id FROM messages WHERE msg_id = ?
	return "", fmt.Errorf("请使用 ACK 请求中的 To 字段获取发送方 ID")
}

// notifyMessageDelivered 通知发送方消息已送达（对标 Java 送达通知）
// ✅ 修复：使用 C2C_ACK 类型（与 Java 客户端保持一致，Java 没有 MSG_DELIVERY_NOTIFICATION）
// 通过 status 来区分不同类型的 ACK：
// - status=1: SERVER_RECEIVED（服务端已收到）
// - status=3: UN_READ（客户端未读确认）
// - status=4: READED（客户端已读确认）
// 送达通知使用 status=3（对方收到但未读）通知发送方
func (s *C2CMsgAckStrategy) notifyMessageDelivered(senderID string, ackReq *pb.C2CAckReq) error {
	// 1. 构建送达通知消息（使用 C2CAckReq 格式，status 取原始值）
	// 对标 Java 客户端 InteractiveClientHandler.handleClientAck()
	deliveryAck := &pb.C2CAckReq{
		ClientMsgId: ackReq.ClientMsgId,
		MsgId:       ackReq.MsgId,
		From:        ackReq.From,
		To:          ackReq.To,
		Status:      ackReq.Status, // 保持原始状态（3=未读，4=已读）
	}

	// 2. 序列化通知消息
	notifyPayload, err := proto.Marshal(deliveryAck)
	if err != nil {
		return fmt.Errorf("序列化送达通知失败: %w", err)
	}

	// 3. 构建 Protobuf 响应（使用 C2C_ACK 类型，Java 客户端能识别）
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_C2C_ACK, // ✅ 修复：使用 C2C_ACK（Java 客户端能识别）
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

	s.logger.Info("✅ 送达通知已发送",
		zap.String("sender_id", senderID),
		zap.Uint64("msg_id", ackReq.MsgId),
		zap.Int32("status", ackReq.Status),
		zap.Int("notification_size", len(responseData)),
	)

	return nil
}

// GetAckStats 获取确认统计信息（并发安全）
func (s *C2CMsgAckStrategy) GetAckStats() AckStats {
	return AckStats{
		TotalAcks:   atomic.LoadInt64(&s.totalAcks),
		ErrorCount:  atomic.LoadInt64(&s.ackErrorCount),
		SuccessRate: s.calculateSuccessRate(),
	}
}

// AckStats 确认统计信息
type AckStats struct {
	TotalAcks   int64   `json:"total_acks"`
	ErrorCount  int64   `json:"error_count"`
	SuccessRate float64 `json:"success_rate"`
}

// calculateSuccessRate 计算成功率（并发安全）
func (s *C2CMsgAckStrategy) calculateSuccessRate() float64 {
	total := atomic.LoadInt64(&s.totalAcks)
	errors := atomic.LoadInt64(&s.ackErrorCount)
	if total+errors == 0 {
		return 0.0
	}
	return float64(total) / float64(total+errors) * 100
}

// 批量处理相关方法（性能优化）

// BatchUpdateDeliveryStatus 批量更新送达状态（性能优化）
// ✅ 修复：使用 C2CAckReq
func (s *C2CMsgAckStrategy) BatchUpdateDeliveryStatus(acks []*pb.C2CAckReq, receiverID string) error {
	// TODO: 实现批量数据库更新
	// 可以显著提高高并发场景下的性能

	s.logger.Debug("批量更新送达状态",
		zap.String("receiver_id", receiverID),
		zap.Int("ack_count", len(acks)),
	)

	return fmt.Errorf("批量更新功能未实现")
}

// BatchNotifyDelivered 批量通知送达（性能优化）
// ✅ 修复：使用 C2CAckReq
func (s *C2CMsgAckStrategy) BatchNotifyDelivered(senderID string, acks []*pb.C2CAckReq) error {
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
