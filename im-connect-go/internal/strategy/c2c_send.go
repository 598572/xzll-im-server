package strategy

import (
	"context"
	"fmt"
	"strings"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	pb "im-connect-go/internal/proto"
	"im-connect-go/pkg/mq"
	"im-connect-go/pkg/redis"

	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/protobuf/proto"
)

// C2CMsgSendStrategy C2C 消息发送策略（对标 Java C2CMsgSendProtoStrategyImpl）
// 功能：
// 1. 处理单聊消息发送
// 2. 消息持久化（通过 RocketMQ → im-business）
// 3. 在线用户推送
// 4. 离线消息处理
// 5. 跨服务器转发
// 6. 消息确认机制
type C2CMsgSendStrategy struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.Manager
	mqProducer     *mq.Producer       // RocketMQ 生产者
	redisClient    *redis.RedisClient // Redis 客户端（用于查询用户状态和路由信息）
}

// NewC2CMsgSendStrategy 创建 C2C 消息发送策略
func NewC2CMsgSendStrategy(cfg *config.Config, logger *zap.Logger, cm *channel.Manager, mqProducer *mq.Producer, redisClient *redis.RedisClient) *C2CMsgSendStrategy {
	return &C2CMsgSendStrategy{
		config:         cfg,
		logger:         logger,
		channelManager: cm,
		mqProducer:     mqProducer,
		redisClient:    redisClient,
	}
}

// SupportMsgType 返回支持的消息类型
func (s *C2CMsgSendStrategy) SupportMsgType() pb.MsgType {
	return pb.MsgType_C2C_SEND
}

// Exchange 处理客户端直连的 C2C 消息发送（对标 Java exchange 方法）
func (s *C2CMsgSendStrategy) Exchange(conn channel.Connection, protoRequest *pb.ImProtoRequest) error {
	startTime := time.Now()
	fromUserID := conn.GetUserID()

	s.logger.Debug("开始处理 C2C 消息发送",
		zap.String("from_user_id", fromUserID),
		zap.Int("payload_size", len(protoRequest.Payload)),
	)

	// 1. 解析 C2C 发送请求（对标 Java C2CSendReq.parseFrom）
	sendReq := &pb.C2CSendReq{}
	if err := proto.Unmarshal(protoRequest.Payload, sendReq); err != nil {
		s.logger.Error("解析 C2C 发送请求失败",
			zap.String("from_user_id", fromUserID),
			zap.Error(err),
		)
		return fmt.Errorf("解析 C2C 发送请求失败: %w", err)
	}

	toUserID := fmt.Sprintf("%d", sendReq.To)
	clientMsgID := string(sendReq.ClientMsgId) // TODO: 需要实现 UUID bytes 转换

	s.logger.Info("处理 C2C 消息",
		zap.String("from_user_id", fromUserID),
		zap.String("to_user_id", toUserID),
		zap.String("client_msg_id", clientMsgID),
		zap.String("content", sendReq.Content),
		zap.Int32("format", sendReq.Format),
	)

	// 2. 消息验证
	if err := s.validateC2CMessage(sendReq); err != nil {
		s.logger.Warn("C2C 消息验证失败",
			zap.String("from_user_id", fromUserID),
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		return err
	}

	// 3. 生成服务器消息ID（对标 Java 雪花算法）
	serverMsgID := s.generateMessageID()
	sendReq.MsgId = serverMsgID

	// 4. 发送到 RocketMQ（对标 Java c2CMsgProvider.sendC2CMsg()）
	// im-business 服务会消费 MQ 消息，负责：
	// - 消息持久化到 MySQL
	// - 更新会话记录
	// - 消息审核、敏感词过滤
	// - 消息统计、分析
	chatID := s.generateChatID(fromUserID, toUserID)
	msgEvent := &mq.C2CMsgEvent{
		ClientMsgID:   clientMsgID,
		MsgID:         fmt.Sprintf("%d", serverMsgID),
		FromUserID:    fromUserID,
		ToUserID:      toUserID,
		ChatID:        chatID,
		MsgContent:    sendReq.Content,
		MsgFormat:     sendReq.Format,
		MsgCreateTime: time.Now().UnixMilli(),
	}

	// 异步发送到 RocketMQ（不阻塞主流程）
	if err := s.mqProducer.SendC2CMsg(msgEvent); err != nil {
		s.logger.Error("❌ 发送消息到 RocketMQ 失败",
			zap.String("from_user_id", fromUserID),
			zap.String("to_user_id", toUserID),
			zap.Uint64("msg_id", serverMsgID),
			zap.Error(err),
		)
		// 注意：MQ 发送失败不影响消息推送，im-business 可以通过其他方式补偿
	} else {
		s.logger.Info("✅ 消息已发送到 RocketMQ",
			zap.String("msg_id", msgEvent.MsgID),
			zap.String("from_user_id", fromUserID),
			zap.String("to_user_id", toUserID),
		)
	}

	// 5. 获取接收人登录、服务信息，根据状态进行处理（对标 Java getReceiveUserDataTemplate）
	receiveUserData, err := s.getReceiveUserData(toUserID)
	if err != nil {
		s.logger.Error("获取接收人数据失败",
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		// 获取失败，发送离线消息
		s.sendOfflineMsgToMQ(msgEvent, "获取接收人数据失败")
		return nil
	}

	s.logger.Info("接收者状态信息",
		zap.String("to_user_id", toUserID),
		zap.String("user_status", receiveUserData.UserStatus),
		zap.Bool("has_local_channel", receiveUserData.HasLocalChannel),
		zap.String("route_address", receiveUserData.RouteAddress),
	)

	// 6. 根据接收人状态做对应的处理（对标 Java 三种情况判断）
	if receiveUserData.HasLocalChannel && receiveUserData.UserStatus == "5" {
		// 情况1：用户在线且在本台机器上，直接发送（对标 Java targetChannel != null && userStatus == ON_LINE(5)）
		s.logger.Debug("【步骤3-本地发送】用户在线且在本台机器上，将直接发送",
			zap.String("to_user_id", toUserID),
			zap.String("client_msg_id", clientMsgID),
			zap.String("msg_id", msgEvent.MsgID),
		)

		if err := s.pushMessageToUser(toUserID, sendReq); err != nil {
			s.logger.Error("推送消息给在线用户失败",
				zap.String("to_user_id", toUserID),
				zap.Error(err),
			)
			// 推送失败，发送离线消息
			s.sendOfflineMsgToMQ(msgEvent, "推送失败")
		} else {
			s.logger.Debug("消息已推送给在线用户",
				zap.String("to_user_id", toUserID),
			)
			// TODO: 添加到重试队列，等待客户端 ACK（对标 Java c2CMsgRetryService.addToRetryQueue）
		}

	} else if !receiveUserData.HasLocalChannel && receiveUserData.UserStatus == "5" && receiveUserData.RouteAddress != "" {
		// 情况2：用户在线但不在本机器上，跨服务器转发（对标 Java targetChannel == null && userStatus == ON_LINE(5) && ipPortStr != blank）
		s.logger.Debug("【步骤3-跨服务器转发】用户在线但不在该机器上，跨服务器转发",
			zap.String("to_user_id", toUserID),
			zap.String("target_server", receiveUserData.RouteAddress),
			zap.String("client_msg_id", clientMsgID),
			zap.String("msg_id", msgEvent.MsgID),
		)

		if err := s.forwardToOtherServer(toUserID, receiveUserData.RouteAddress, protoRequest); err != nil {
			s.logger.Error("【跨服务器转发-失败】gRPC 转发消息失败",
				zap.String("to_user_id", toUserID),
				zap.String("target_server", receiveUserData.RouteAddress),
				zap.Error(err),
			)
			// 转发失败，发送离线消息
			s.sendOfflineMsgToMQ(msgEvent, "跨服务器转发失败")
		}

	} else if receiveUserData.UserStatus == "" || receiveUserData.UserStatus == "0" {
		// 情况3：用户不在线，保存为离线消息（对标 Java userStatus == null）
		s.logger.Debug("【步骤3-离线处理】用户不在线，将消息保存至离线表中",
			zap.String("to_user_id", toUserID),
			zap.String("client_msg_id", clientMsgID),
			zap.String("msg_id", msgEvent.MsgID),
		)

		// 发送离线消息到 RocketMQ
		s.sendOfflineMsgToMQ(msgEvent, "用户不在线")

	} else {
		// 情况4：异常状态，帮助诊断问题（对标 Java else 分支）
		s.logger.Warn("【步骤3-异常状态】用户状态不一致",
			zap.String("to_user_id", toUserID),
			zap.Bool("has_local_channel", receiveUserData.HasLocalChannel),
			zap.String("user_status", receiveUserData.UserStatus),
			zap.String("route_address", receiveUserData.RouteAddress),
			zap.String("client_msg_id", clientMsgID),
			zap.String("msg_id", msgEvent.MsgID),
		)

		// 状态不一致，发送离线消息
		s.sendOfflineMsgToMQ(msgEvent, "用户状态不一致")
	}

	// 6. 发送确认给发送者（对标 Java ServerAck）
	if err := s.sendServerAck(conn, sendReq, true); err != nil {
		s.logger.Error("发送服务器确认失败",
			zap.String("from_user_id", fromUserID),
			zap.Error(err),
		)
	}

	s.logger.Debug("C2C 消息处理完成",
		zap.String("from_user_id", fromUserID),
		zap.String("to_user_id", toUserID),
		zap.Uint64("msg_id", serverMsgID),
		zap.Duration("processing_time", time.Since(startTime)),
	)

	return nil
}

// ReceiveAndSendMsg 处理跨服务器转发的 C2C 消息（对标 Java receiveAndSendMsg）
func (s *C2CMsgSendStrategy) ReceiveAndSendMsg(protoRequest *pb.ImProtoRequest) error {
	s.logger.Debug("处理跨服务器转发的 C2C 消息")

	// 1. 解析消息
	sendReq := &pb.C2CSendReq{}
	if err := proto.Unmarshal(protoRequest.Payload, sendReq); err != nil {
		return fmt.Errorf("解析跨服务器 C2C 消息失败: %w", err)
	}

	toUserID := fmt.Sprintf("%d", sendReq.To)

	// 2. 二次校验用户是否在线（对标 Java 二次校验逻辑）
	if !s.channelManager.IsUserOnline(toUserID) {
		return fmt.Errorf("用户 %s 不在线", toUserID)
	}

	// 3. 直接推送给本地用户（不再保存消息，避免重复）
	if err := s.pushMessageToUser(toUserID, sendReq); err != nil {
		s.logger.Error("跨服务器消息推送失败",
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		return err
	}

	s.logger.Info("跨服务器 C2C 消息推送成功",
		zap.String("to_user_id", toUserID),
		zap.Uint64("msg_id", sendReq.MsgId),
	)

	return nil
}

// ReceiveUserData 接收人数据（对标 Java ReceiveUserDataDTO）
type ReceiveUserData struct {
	UserStatus      string // 用户状态：5=在线（ON_LINE）, 0=离线（OFF_LINE）, ""=不存在
	HasLocalChannel bool   // 本地是否有 Channel
	RouteAddress    string // 路由地址（格式：ip:port）
}

// getReceiveUserData 获取接收人数据（对标 Java getReceiveUserDataTemplate）
func (s *C2CMsgSendStrategy) getReceiveUserData(userID string) (*ReceiveUserData, error) {
	data := &ReceiveUserData{
		UserStatus:      "",
		HasLocalChannel: false,
		RouteAddress:    "",
	}

	// 1. 检查本地是否有 Channel
	data.HasLocalChannel = s.channelManager.IsUserOnline(userID)

	// 2. 从 Redis 查询用户状态和路由信息
	ctx := context.Background()

	// 2.1 查询用户状态（对标 Java LOGIN_STATUS_PREFIX）
	userStatusKey := "userLogin:status:" + userID
	userStatus, err := s.redisClient.Get(ctx, userStatusKey)
	if err != nil {
		if err.Error() != "redis: nil" {
			s.logger.Warn("从 Redis 查询用户状态失败",
				zap.String("user_id", userID),
				zap.Error(err),
			)
		}
		// Redis 中没有状态，认为离线
		data.UserStatus = ""
	} else {
		data.UserStatus = userStatus
	}

	// 2.2 如果用户在线，查询路由信息（对标 Java ROUTE_PREFIX）
	// 注意：在线状态是 "5"（对标 Java UserStatus.ON_LINE = 5）
	if data.UserStatus == "5" {
		userRouteKey := "userLogin:server:" + userID
		routeAddress, err := s.redisClient.Get(ctx, userRouteKey)
		if err != nil {
			if err.Error() != "redis: nil" {
				s.logger.Warn("从 Redis 查询路由信息失败",
					zap.String("user_id", userID),
					zap.Error(err),
				)
			}
		} else {
			data.RouteAddress = routeAddress
		}
	}

	return data, nil
}

// validateC2CMessage 验证 C2C 消息
func (s *C2CMsgSendStrategy) validateC2CMessage(sendReq *pb.C2CSendReq) error {
	// 1. 检查接收人
	if sendReq.To <= 0 {
		return fmt.Errorf("无效的接收人ID: %d", sendReq.To)
	}

	// 2. 检查消息内容
	if len(sendReq.Content) == 0 {
		return fmt.Errorf("消息内容不能为空")
	}

	// 3. 检查消息长度
	if len(sendReq.Content) > 10000 { // 10KB 限制
		return fmt.Errorf("消息内容过长: %d > 10000", len(sendReq.Content))
	}

	// 4. 检查消息格式
	if sendReq.Format < 0 || sendReq.Format > 10 { // 假设支持的格式范围
		return fmt.Errorf("不支持的消息格式: %d", sendReq.Format)
	}

	// 5. 检查客户端消息ID
	if len(sendReq.ClientMsgId) == 0 {
		return fmt.Errorf("客户端消息ID不能为空")
	}

	return nil
}

// generateMessageID 生成服务器消息ID（简化版雪花算法）
func (s *C2CMsgSendStrategy) generateMessageID() uint64 {
	// TODO: 实现完整的雪花算法
	// 这里使用简化版本：时间戳 + 随机数
	return uint64(time.Now().UnixNano() / 1000000) // 毫秒时间戳
}

// generateChatID 生成会话ID（对标 Java ChatIdUtils.buildC2CChatId）
func (s *C2CMsgSendStrategy) generateChatID(fromUserID, toUserID string) string {
	// 简化版：from_to 格式
	// TODO: 可以改为与 Java 版本一致的算法
	if fromUserID < toUserID {
		return fmt.Sprintf("%s_%s", fromUserID, toUserID)
	}
	return fmt.Sprintf("%s_%s", toUserID, fromUserID)
}

// sendOfflineMsgToMQ 发送离线消息到 RocketMQ（对标 Java c2CMsgProvider.offLineMsg()）
func (s *C2CMsgSendStrategy) sendOfflineMsgToMQ(msgEvent *mq.C2CMsgEvent, reason string) {
	offlineEvent := &mq.C2COffLineMsgEvent{
		ClientMsgID:   msgEvent.ClientMsgID,
		MsgID:         msgEvent.MsgID,
		FromUserID:    msgEvent.FromUserID,
		ToUserID:      msgEvent.ToUserID,
		ChatID:        msgEvent.ChatID,
		MsgContent:    msgEvent.MsgContent,
		MsgFormat:     msgEvent.MsgFormat,
		MsgStatus:     1, // 1 = 离线（对应 Java MsgStatusEnum.MsgStatus.OFF_LINE）
		MsgCreateTime: msgEvent.MsgCreateTime,
	}

	if err := s.mqProducer.SendOffLineMsg(offlineEvent); err != nil {
		s.logger.Error("❌ 发送离线消息到 RocketMQ 失败",
			zap.String("msg_id", msgEvent.MsgID),
			zap.String("to_user_id", msgEvent.ToUserID),
			zap.String("reason", reason),
			zap.Error(err),
		)
	} else {
		s.logger.Info("✅ 离线消息已发送到 RocketMQ",
			zap.String("msg_id", msgEvent.MsgID),
			zap.String("to_user_id", msgEvent.ToUserID),
			zap.String("reason", reason),
		)
	}
}

// pushMessageToUser 推送消息给用户（对标 Java sendProtoMsg）
func (s *C2CMsgSendStrategy) pushMessageToUser(userID string, sendReq *pb.C2CSendReq) error {
	// 1. 构建推送消息（对标 Java C2CMsgPush）
	pushMsg := &pb.C2CMsgPush{
		ClientMsgId: sendReq.ClientMsgId,
		MsgId:       sendReq.MsgId,
		From:        sendReq.From,
		To:          sendReq.To,
		Format:      sendReq.Format,
		Content:     sendReq.Content,
		Time:        sendReq.Time,
	}

	// 2. 序列化推送消息
	pushPayload, err := proto.Marshal(pushMsg)
	if err != nil {
		return fmt.Errorf("序列化推送消息失败: %w", err)
	}

	// 3. 构建 Protobuf 响应（对标 Java ImProtoResponse）
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_C2C_MSG_PUSH,
		Payload: pushPayload,
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 4. 序列化响应
	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化响应失败: %w", err)
	}

	// 5. 发送给用户的所有连接
	if err := s.channelManager.BroadcastToUser(userID, responseData); err != nil {
		return fmt.Errorf("广播消息失败: %w", err)
	}

	s.logger.Debug("消息已推送给用户",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", sendReq.MsgId),
		zap.Int("response_size", len(responseData)),
	)

	return nil
}

// sendServerAck 发送服务器确认（对标 Java ServerAck）
func (s *C2CMsgSendStrategy) sendServerAck(conn channel.Connection, sendReq *pb.C2CSendReq, success bool) error {
	// 构建服务器确认消息
	ack := &pb.ServerAck{
		ClientMsgId: sendReq.ClientMsgId,
		MsgId:       sendReq.MsgId,
		Success:     success,
		Timestamp:   uint64(time.Now().UnixMilli()),
	}

	// 序列化确认消息
	ackPayload, err := proto.Marshal(ack)
	if err != nil {
		return fmt.Errorf("序列化服务器确认失败: %w", err)
	}

	// 构建响应
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_SERVER_ACK,
		Payload: ackPayload,
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 序列化响应
	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化确认响应失败: %w", err)
	}

	// 发送确认
	if err := conn.SendBinary(responseData); err != nil {
		return fmt.Errorf("发送服务器确认失败: %w", err)
	}

	s.logger.Debug("服务器确认已发送",
		zap.String("user_id", conn.GetUserID()),
		zap.Uint64("msg_id", sendReq.MsgId),
		zap.Bool("success", success),
	)

	return nil
}

// forwardToOtherServer 转发消息到其他服务器（对标 Java 跨服务器转发）
// forwardToOtherServer 跨服务器转发消息（对标 Java gRPC 转发逻辑）
func (s *C2CMsgSendStrategy) forwardToOtherServer(toUserID string, routeAddress string, protoRequest *pb.ImProtoRequest) error {
	// 1. 解析目标服务器地址
	// routeAddress 格式：ip:port 或 ip:port:grpcPort
	parts := strings.Split(routeAddress, ":")
	if len(parts) < 2 {
		return fmt.Errorf("无效的路由地址格式: %s", routeAddress)
	}

	targetIP := parts[0]
	// 使用配置的 gRPC 端口（对标 Java grpcClientConfig.getDefaultPort()）
	targetGrpcPort := s.config.GRPC.Port
	if targetGrpcPort == 0 {
		targetGrpcPort = 9091 // 默认 gRPC 端口
	}

	targetAddr := fmt.Sprintf("%s:%d", targetIP, targetGrpcPort)

	s.logger.Info("【跨服务器转发-准备】",
		zap.String("to_user_id", toUserID),
		zap.String("route_address", routeAddress),
		zap.String("target_addr", targetAddr),
	)

	// 2. 建立 gRPC 连接（对标 Java grpcClientManager.getStubByIP）
	conn, err := grpc.Dial(
		targetAddr,
		grpc.WithInsecure(),
		grpc.WithTimeout(5*time.Second),
		grpc.WithBlock(),
	)
	if err != nil {
		return fmt.Errorf("连接目标服务器失败: %w", err)
	}
	defer conn.Close()

	// 3. 创建 gRPC 客户端
	client := pb.NewMessageServiceClient(conn)

	// 4. 调用目标服务器的 transferC2CMsg 接口（对标 Java stub.transferC2CMsg）
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	response, err := client.TransferC2CMsg(ctx, protoRequest)
	if err != nil {
		s.logger.Error("【跨服务器转发-gRPC调用失败】",
			zap.String("to_user_id", toUserID),
			zap.String("target_addr", targetAddr),
			zap.Error(err),
		)
		return fmt.Errorf("gRPC 调用失败: %w", err)
	}

	// 5. 处理响应
	if response.Code != 200 {
		s.logger.Warn("【跨服务器转发-失败】",
			zap.String("to_user_id", toUserID),
			zap.Int32("code", response.Code),
			zap.String("message", response.Message),
		)
		return fmt.Errorf("跨服务器转发失败: code=%d, msg=%s", response.Code, response.Message)
	}

	s.logger.Info("【跨服务器转发-成功】",
		zap.String("to_user_id", toUserID),
		zap.String("target_addr", targetAddr),
		zap.Int32("code", response.Code),
		zap.String("message", response.Message),
	)

	return nil
}

// 扩展功能

// GetMessageHistory 获取消息历史（扩展功能）
func (s *C2CMsgSendStrategy) GetMessageHistory(userID, otherUserID string, limit, offset int) ([]*pb.C2CMessage, error) {
	// TODO: 实现消息历史查询
	s.logger.Debug("获取消息历史",
		zap.String("user_id", userID),
		zap.String("other_user_id", otherUserID),
		zap.Int("limit", limit),
		zap.Int("offset", offset),
	)

	return nil, fmt.Errorf("消息历史查询功能未实现")
}

// DeleteMessage 删除消息（扩展功能）
func (s *C2CMsgSendStrategy) DeleteMessage(userID string, msgID uint64) error {
	// TODO: 实现消息删除逻辑
	s.logger.Debug("删除消息",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", msgID),
	)

	return fmt.Errorf("消息删除功能未实现")
}

// MarkMessageAsRead 标记消息为已读（扩展功能）
func (s *C2CMsgSendStrategy) MarkMessageAsRead(userID string, msgID uint64) error {
	// TODO: 实现消息已读标记
	s.logger.Debug("标记消息为已读",
		zap.String("user_id", userID),
		zap.Uint64("msg_id", msgID),
	)

	return fmt.Errorf("消息已读标记功能未实现")
}
