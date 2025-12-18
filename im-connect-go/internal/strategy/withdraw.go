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

// WithdrawMsgStrategy 消息撤回策略（对标 Java WithdrawMsgSendProtoStrategyImpl）
// 功能：
// 1. 处理消息撤回请求
// 2. 验证撤回权限和时间限制
// 3. 更新消息状态为已撤回
// 4. 通知接收方消息被撤回
// 5. 跨服务器撤回通知
type WithdrawMsgStrategy struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.Manager

	// 撤回配置
	withdrawTimeLimit time.Duration // 撤回时间限制，超过此时间不能撤回

	// 统计信息
	totalWithdraws   int64
	withdrawErrors   int64
	timeoutWithdraws int64 // 超时无法撤回的数量
}

// NewWithdrawMsgStrategy 创建消息撤回策略
func NewWithdrawMsgStrategy(cfg *config.Config, logger *zap.Logger, cm *channel.Manager) *WithdrawMsgStrategy {
	return &WithdrawMsgStrategy{
		config:            cfg,
		logger:            logger,
		channelManager:    cm,
		withdrawTimeLimit: 2 * time.Minute, // 默认2分钟内可以撤回
	}
}

// SupportMsgType 返回支持的消息类型
func (s *WithdrawMsgStrategy) SupportMsgType() pb.MsgType {
	return pb.MsgType_WITHDRAW_MSG_SEND
}

// Exchange 处理消息撤回请求（对标 Java exchange 方法）
func (s *WithdrawMsgStrategy) Exchange(conn channel.Connection, protoRequest *pb.ImProtoRequest) error {
	startTime := time.Now()
	userID := conn.GetUserID()

	s.logger.Debug("开始处理消息撤回",
		zap.String("user_id", userID),
		zap.Int("payload_size", len(protoRequest.Payload)),
	)

	// 1. 解析撤回请求（对标 Java WithdrawMsgSend.parseFrom）
	withdrawReq := &pb.WithdrawMsgSend{}
	if err := proto.Unmarshal(protoRequest.Payload, withdrawReq); err != nil {
		s.logger.Error("解析撤回请求失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		s.withdrawErrors++
		return fmt.Errorf("解析撤回请求失败: %w", err)
	}

	s.logger.Info("处理消息撤回",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", withdrawReq.MsgId),
		zap.String("client_msg_id", string(withdrawReq.ClientMsgId)), // TODO: 需要实现 UUID bytes 转换
		zap.Uint64("withdraw_time", withdrawReq.Time),
	)

	// 2. 验证撤回请求
	if err := s.validateWithdrawRequest(userID, withdrawReq); err != nil {
		s.logger.Warn("撤回请求验证失败",
			zap.String("user_id", userID),
			zap.Uint64("msg_id", withdrawReq.MsgId),
			zap.Error(err),
		)

		// 根据失败原因更新统计
		if err.Error() == "撤回时间超限" {
			s.timeoutWithdraws++
		} else {
			s.withdrawErrors++
		}

		// 发送撤回失败通知
		s.sendWithdrawResponse(conn, withdrawReq, false, err.Error())
		return err
	}

	// 3. 获取原消息信息（对标 Java 查询原消息）
	originalMsg, err := s.getOriginalMessage(withdrawReq.MsgId)
	if err != nil {
		s.logger.Error("获取原消息失败",
			zap.String("user_id", userID),
			zap.Uint64("msg_id", withdrawReq.MsgId),
			zap.Error(err),
		)
		s.withdrawErrors++
		s.sendWithdrawResponse(conn, withdrawReq, false, "消息不存在")
		return fmt.Errorf("获取原消息失败: %w", err)
	}

	// 4. 更新消息状态为已撤回（对标 Java 数据库状态更新）
	if err := s.updateMessageWithdrawStatus(withdrawReq.MsgId, userID, withdrawReq.Time); err != nil {
		s.logger.Error("更新消息撤回状态失败",
			zap.String("user_id", userID),
			zap.Uint64("msg_id", withdrawReq.MsgId),
			zap.Error(err),
		)
		s.withdrawErrors++
		s.sendWithdrawResponse(conn, withdrawReq, false, "撤回失败")
		return fmt.Errorf("更新消息撤回状态失败: %w", err)
	}

	// 5. 通知接收方消息被撤回（对标 Java 撤回通知）
	receiverID := fmt.Sprintf("%d", originalMsg.To)
	if s.channelManager.IsUserOnline(receiverID) {
		// 接收方在线，直接通知
		if err := s.notifyMessageWithdrawn(receiverID, withdrawReq, originalMsg); err != nil {
			s.logger.Error("通知接收方消息撤回失败",
				zap.String("receiver_id", receiverID),
				zap.Uint64("msg_id", withdrawReq.MsgId),
				zap.Error(err),
			)
			// 通知失败不影响撤回成功
		}
	} else {
		// 接收方不在线，检查是否在其他服务器或保存离线通知
		if err := s.forwardWithdrawNotification(receiverID, protoRequest); err != nil {
			s.logger.Warn("转发撤回通知失败",
				zap.String("receiver_id", receiverID),
				zap.Error(err),
			)

			// 保存离线撤回通知
			if err := s.saveOfflineWithdrawNotification(receiverID, withdrawReq); err != nil {
				s.logger.Error("保存离线撤回通知失败", zap.Error(err))
			}
		}
	}

	// 6. 发送撤回成功确认给撤回发起者
	if err := s.sendWithdrawResponse(conn, withdrawReq, true, "撤回成功"); err != nil {
		s.logger.Error("发送撤回成功确认失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		// 确认发送失败不影响撤回结果
	}

	// 7. 更新统计信息
	s.totalWithdraws++

	s.logger.Info("消息撤回处理完成",
		zap.String("user_id", userID),
		zap.String("receiver_id", receiverID),
		zap.Uint64("msg_id", withdrawReq.MsgId),
		zap.Duration("processing_time", time.Since(startTime)),
	)

	return nil
}

// ReceiveAndSendMsg 处理跨服务器转发的撤回通知（对标 Java receiveAndSendMsg）
func (s *WithdrawMsgStrategy) ReceiveAndSendMsg(protoRequest *pb.ImProtoRequest) error {
	s.logger.Debug("处理跨服务器撤回通知")

	// 1. 解析撤回请求
	withdrawReq := &pb.WithdrawMsgSend{}
	if err := proto.Unmarshal(protoRequest.Payload, withdrawReq); err != nil {
		return fmt.Errorf("解析跨服务器撤回请求失败: %w", err)
	}

	// 2. 获取原消息（用于通知）
	originalMsg, err := s.getOriginalMessage(withdrawReq.MsgId)
	if err != nil {
		return fmt.Errorf("获取原消息失败: %w", err)
	}

	receiverID := fmt.Sprintf("%d", originalMsg.To)

	// 3. 二次校验用户是否在线
	if !s.channelManager.IsUserOnline(receiverID) {
		return fmt.Errorf("用户 %s 不在线", receiverID)
	}

	// 4. 直接通知本地用户消息被撤回
	if err := s.notifyMessageWithdrawn(receiverID, withdrawReq, originalMsg); err != nil {
		return fmt.Errorf("通知消息撤回失败: %w", err)
	}

	s.logger.Info("跨服务器撤回通知处理成功",
		zap.String("receiver_id", receiverID),
		zap.Uint64("msg_id", withdrawReq.MsgId),
	)

	return nil
}

// validateWithdrawRequest 验证撤回请求
func (s *WithdrawMsgStrategy) validateWithdrawRequest(userID string, withdrawReq *pb.WithdrawMsgSend) error {
	// 1. 检查消息ID
	if withdrawReq.MsgId <= 0 {
		return fmt.Errorf("无效的消息ID: %d", withdrawReq.MsgId)
	}

	// 2. 检查客户端消息ID
	if len(withdrawReq.ClientMsgId) == 0 {
		return fmt.Errorf("客户端消息ID不能为空")
	}

	// 3. 检查撤回时间
	if withdrawReq.Time <= 0 {
		return fmt.Errorf("无效的撤回时间: %d", withdrawReq.Time)
	}

	// 4. 检查消息是否存在且属于该用户
	originalMsg, err := s.getOriginalMessage(withdrawReq.MsgId)
	if err != nil {
		return fmt.Errorf("消息不存在: %w", err)
	}

	// 5. 验证撤回权限（只有发送者可以撤回）
	senderID := fmt.Sprintf("%d", originalMsg.From)
	if senderID != userID {
		return fmt.Errorf("无权撤回他人消息")
	}

	// 6. 检查消息是否已经被撤回
	if originalMsg.IsWithdrawn {
		return fmt.Errorf("消息已经被撤回")
	}

	// 7. 检查撤回时间限制
	msgTime := time.UnixMilli(int64(originalMsg.SendTime))
	if time.Since(msgTime) > s.withdrawTimeLimit {
		return fmt.Errorf("撤回时间超限")
	}

	// 8. 检查撤回时间不能早于发送时间
	if withdrawReq.Time < originalMsg.SendTime {
		return fmt.Errorf("撤回时间不能早于发送时间")
	}

	return nil
}

// getOriginalMessage 获取原消息信息（对标 Java 数据库查询）
func (s *WithdrawMsgStrategy) getOriginalMessage(msgID uint64) (*MessageInfo, error) {
	// TODO: 实现数据库查询逻辑
	// SELECT * FROM messages WHERE msg_id = ?

	s.logger.Debug("获取原消息信息",
		zap.Uint64("msg_id", msgID),
	)

	// 模拟查询结果（实际应从数据库查询）
	return &MessageInfo{
		MsgID:       msgID,
		From:        12345, // 发送者ID
		To:          67890, // 接收者ID
		Content:     "mock content",
		SendTime:    uint64(time.Now().Add(-30 * time.Second).UnixMilli()), // 30秒前发送
		IsWithdrawn: false,
	}, nil
}

// MessageInfo 消息信息结构体
type MessageInfo struct {
	MsgID       uint64 `json:"msg_id"`
	From        uint64 `json:"from"`
	To          uint64 `json:"to"`
	Content     string `json:"content"`
	SendTime    uint64 `json:"send_time"`
	IsWithdrawn bool   `json:"is_withdrawn"`
}

// updateMessageWithdrawStatus 更新消息撤回状态（对标 Java 数据库更新）
func (s *WithdrawMsgStrategy) updateMessageWithdrawStatus(msgID uint64, userID string, withdrawTime uint64) error {
	// TODO: 实现数据库更新逻辑
	// UPDATE messages SET is_withdrawn = true, withdraw_time = ?, withdraw_by = ?
	// WHERE msg_id = ?

	s.logger.Debug("更新消息撤回状态",
		zap.Uint64("msg_id", msgID),
		zap.String("user_id", userID),
		zap.Uint64("withdraw_time", withdrawTime),
	)

	// 模拟数据库更新
	return nil
}

// notifyMessageWithdrawn 通知接收方消息被撤回（对标 Java 撤回通知）
func (s *WithdrawMsgStrategy) notifyMessageWithdrawn(receiverID string, withdrawReq *pb.WithdrawMsgSend, originalMsg *MessageInfo) error {
	// 1. 构建撤回通知消息
	withdrawNotification := &pb.MessageWithdrawNotification{
		MsgId:           withdrawReq.MsgId,
		ClientMsgId:     withdrawReq.ClientMsgId,
		WithdrawnAt:     withdrawReq.Time,
		WithdrawnBy:     originalMsg.From,
		OriginalContent: "该消息已被撤回", // 可配置的撤回提示文本
	}

	// 2. 序列化通知消息
	notifyPayload, err := proto.Marshal(withdrawNotification)
	if err != nil {
		return fmt.Errorf("序列化撤回通知失败: %w", err)
	}

	// 3. 构建 Protobuf 响应
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_MSG_WITHDRAW_NOTIFICATION,
		Payload: notifyPayload,
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 4. 序列化响应
	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化撤回通知响应失败: %w", err)
	}

	// 5. 发送给接收方
	if err := s.channelManager.BroadcastToUser(receiverID, responseData); err != nil {
		return fmt.Errorf("发送撤回通知失败: %w", err)
	}

	s.logger.Debug("撤回通知已发送",
		zap.String("receiver_id", receiverID),
		zap.Uint64("msg_id", withdrawReq.MsgId),
		zap.Int("notification_size", len(responseData)),
	)

	return nil
}

// sendWithdrawResponse 发送撤回结果响应
func (s *WithdrawMsgStrategy) sendWithdrawResponse(conn channel.Connection, withdrawReq *pb.WithdrawMsgSend, success bool, message string) error {
	// 构建撤回响应
	response := &pb.WithdrawMsgResponse{
		MsgId:       withdrawReq.MsgId,
		ClientMsgId: withdrawReq.ClientMsgId,
		Success:     success,
		Message:     message,
		Timestamp:   uint64(time.Now().UnixMilli()),
	}

	// 序列化响应
	responsePayload, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化撤回响应失败: %w", err)
	}

	// 构建 Protobuf 响应
	protoResponse := &pb.ImProtoResponse{
		Type:    pb.MsgType_WITHDRAW_MSG_RESPONSE,
		Payload: responsePayload,
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 序列化最终响应
	responseData, err := proto.Marshal(protoResponse)
	if err != nil {
		return fmt.Errorf("序列化撤回最终响应失败: %w", err)
	}

	// 发送响应
	if err := conn.SendBinary(responseData); err != nil {
		return fmt.Errorf("发送撤回响应失败: %w", err)
	}

	s.logger.Debug("撤回响应已发送",
		zap.String("user_id", conn.GetUserID()),
		zap.Uint64("msg_id", withdrawReq.MsgId),
		zap.Bool("success", success),
		zap.String("message", message),
	)

	return nil
}

// forwardWithdrawNotification 转发撤回通知到其他服务器
func (s *WithdrawMsgStrategy) forwardWithdrawNotification(receiverID string, protoRequest *pb.ImProtoRequest) error {
	// TODO: 实现跨服务器撤回通知转发
	// 1. 查询用户所在服务器（Redis 查询）
	// 2. 调用目标服务器的 gRPC 接口
	// 3. 处理转发结果

	s.logger.Debug("转发撤回通知到其他服务器",
		zap.String("receiver_id", receiverID),
	)

	// 模拟转发失败
	return fmt.Errorf("用户 %s 不在任何服务器在线", receiverID)
}

// saveOfflineWithdrawNotification 保存离线撤回通知
func (s *WithdrawMsgStrategy) saveOfflineWithdrawNotification(userID string, withdrawReq *pb.WithdrawMsgSend) error {
	// TODO: 实现离线撤回通知保存
	// 用户上线后会收到撤回通知

	s.logger.Info("保存离线撤回通知",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", withdrawReq.MsgId),
	)

	// 模拟保存
	return nil
}

// GetWithdrawStats 获取撤回统计信息
func (s *WithdrawMsgStrategy) GetWithdrawStats() WithdrawStats {
	return WithdrawStats{
		TotalWithdraws: s.totalWithdraws,
		ErrorCount:     s.withdrawErrors,
		TimeoutCount:   s.timeoutWithdraws,
		SuccessRate:    s.calculateSuccessRate(),
		TimeoutRate:    s.calculateTimeoutRate(),
	}
}

// WithdrawStats 撤回统计信息
type WithdrawStats struct {
	TotalWithdraws int64   `json:"total_withdraws"`
	ErrorCount     int64   `json:"error_count"`
	TimeoutCount   int64   `json:"timeout_count"`
	SuccessRate    float64 `json:"success_rate"`
	TimeoutRate    float64 `json:"timeout_rate"`
}

// calculateSuccessRate 计算成功率
func (s *WithdrawMsgStrategy) calculateSuccessRate() float64 {
	total := s.totalWithdraws + s.withdrawErrors + s.timeoutWithdraws
	if total == 0 {
		return 0.0
	}
	return float64(s.totalWithdraws) / float64(total) * 100
}

// calculateTimeoutRate 计算超时率
func (s *WithdrawMsgStrategy) calculateTimeoutRate() float64 {
	total := s.totalWithdraws + s.withdrawErrors + s.timeoutWithdraws
	if total == 0 {
		return 0.0
	}
	return float64(s.timeoutWithdraws) / float64(total) * 100
}

// SetWithdrawTimeLimit 设置撤回时间限制
func (s *WithdrawMsgStrategy) SetWithdrawTimeLimit(limit time.Duration) {
	s.withdrawTimeLimit = limit
	s.logger.Info("撤回时间限制已更新",
		zap.Duration("new_limit", limit),
	)
}

// 扩展功能

// GetWithdrawHistory 获取撤回历史（扩展功能）
func (s *WithdrawMsgStrategy) GetWithdrawHistory(userID string, limit int) ([]*WithdrawRecord, error) {
	// TODO: 查询用户的撤回历史
	s.logger.Debug("获取撤回历史",
		zap.String("user_id", userID),
		zap.Int("limit", limit),
	)

	return nil, fmt.Errorf("撤回历史查询功能未实现")
}

// WithdrawRecord 撤回记录
type WithdrawRecord struct {
	MsgID       uint64 `json:"msg_id"`
	WithdrawnAt uint64 `json:"withdrawn_at"`
	Reason      string `json:"reason"`
}

// BatchWithdrawMessages 批量撤回消息（扩展功能）
func (s *WithdrawMsgStrategy) BatchWithdrawMessages(userID string, msgIDs []uint64) (*BatchWithdrawResult, error) {
	// TODO: 实现批量撤回功能
	s.logger.Debug("批量撤回消息",
		zap.String("user_id", userID),
		zap.Int("msg_count", len(msgIDs)),
	)

	return nil, fmt.Errorf("批量撤回功能未实现")
}

// BatchWithdrawResult 批量撤回结果
type BatchWithdrawResult struct {
	Total     int      `json:"total"`
	Success   int      `json:"success"`
	Failed    int      `json:"failed"`
	Timeout   int      `json:"timeout"`
	FailedIDs []uint64 `json:"failed_ids"`
}
