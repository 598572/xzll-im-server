package grpc

import (
	"context"
	"fmt"

	"im-connect-go/internal/channel"
	pb "im-connect-go/internal/proto"
	"im-connect-go/internal/strategy"

	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// MessageServiceImpl gRPC 服务实现（对标 Java MessageServiceGrpcImpl）
// 功能：
// 1. 跨服务器消息转发
// 2. 好友请求推送
// 3. 消息状态推送
type MessageServiceImpl struct {
	pb.UnimplementedMessageServiceServer
	logger         *zap.Logger
	channelManager *channel.NbioManager
	c2cStrategy    *strategy.C2CMsgSendStrategy
}

// NewMessageServiceImpl 创建 gRPC 服务实现
func NewMessageServiceImpl(
	logger *zap.Logger,
	channelManager *channel.NbioManager,
	c2cStrategy *strategy.C2CMsgSendStrategy,
) *MessageServiceImpl {
	return &MessageServiceImpl{
		logger:         logger,
		channelManager: channelManager,
		c2cStrategy:    c2cStrategy,
	}
}

// ResponseServerAck2Client 发送服务器确认到客户端（对标 Java responseServerAck2Client）
func (s *MessageServiceImpl) ResponseServerAck2Client(ctx context.Context, req *pb.ServerAckPush) (*pb.WebBaseResponse, error) {
	s.logger.Debug("[gRPC] 处理服务器确认推送",
		zap.Uint64("to_user_id", req.ToUserId),
		zap.Uint64("msg_id", req.MsgId),
	)

	toUserID := fmt.Sprintf("%d", req.ToUserId)

	// 检查用户是否在线
	if !s.channelManager.IsUserOnline(toUserID) {
		return &pb.WebBaseResponse{
			Code:    404,
			Message: "用户不在线",
		}, nil
	}

	// 构建响应消息
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_SERVER_ACK,
		Payload: mustMarshal(req),
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 发送给用户
	if err := s.broadcastToUser(toUserID, response); err != nil {
		s.logger.Error("[gRPC] 发送服务器确认失败",
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		return &pb.WebBaseResponse{
			Code:    500,
			Message: err.Error(),
		}, nil
	}

	return &pb.WebBaseResponse{
		Code:    200,
		Message: "success",
	}, nil
}

// ResponseClientAck2Client 发送客户端确认到客户端（对标 Java responseClientAck2Client）
func (s *MessageServiceImpl) ResponseClientAck2Client(ctx context.Context, req *pb.ClientAckPush) (*pb.WebBaseResponse, error) {
	s.logger.Debug("[gRPC] 处理客户端确认推送",
		zap.Uint64("to_user_id", req.ToUserId),
		zap.Uint64("msg_id", req.MsgId),
	)

	toUserID := fmt.Sprintf("%d", req.ToUserId)

	// 检查用户是否在线
	if !s.channelManager.IsUserOnline(toUserID) {
		return &pb.WebBaseResponse{
			Code:    404,
			Message: "用户不在线",
		}, nil
	}

	// 构建响应消息
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_MSG_DELIVERY_NOTIFICATION,
		Payload: mustMarshal(req),
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 发送给用户
	if err := s.broadcastToUser(toUserID, response); err != nil {
		s.logger.Error("[gRPC] 发送客户端确认失败",
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		return &pb.WebBaseResponse{
			Code:    500,
			Message: err.Error(),
		}, nil
	}

	return &pb.WebBaseResponse{
		Code:    200,
		Message: "success",
	}, nil
}

// SendWithdrawMsg2Client 发送撤回消息到客户端（对标 Java sendWithdrawMsg2Client）
func (s *MessageServiceImpl) SendWithdrawMsg2Client(ctx context.Context, req *pb.WithdrawPush) (*pb.WebBaseResponse, error) {
	s.logger.Info("[gRPC] 处理撤回消息推送",
		zap.Uint64("to_user_id", req.ToUserId),
		zap.Uint64("msg_id", req.MsgId),
	)

	toUserID := fmt.Sprintf("%d", req.ToUserId)

	// 检查用户是否在线
	if !s.channelManager.IsUserOnline(toUserID) {
		return &pb.WebBaseResponse{
			Code:    404,
			Message: "用户不在线",
		}, nil
	}

	// 构建响应消息
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_WITHDRAW_MSG_RESPONSE, // 撤回消息响应（下行）
		Payload: mustMarshal(req),
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 发送给用户
	if err := s.broadcastToUser(toUserID, response); err != nil {
		s.logger.Error("[gRPC] 发送撤回消息失败",
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		return &pb.WebBaseResponse{
			Code:    500,
			Message: err.Error(),
		}, nil
	}

	return &pb.WebBaseResponse{
		Code:    200,
		Message: "success",
	}, nil
}

// PushFriendRequest2Client 推送好友请求到客户端（对标 Java pushFriendRequest2Client）
func (s *MessageServiceImpl) PushFriendRequest2Client(ctx context.Context, req *pb.FriendRequestPush) (*pb.WebBaseResponse, error) {
	s.logger.Info("[gRPC] 处理好友请求推送",
		zap.Uint64("to_user_id", req.ToUserId),
		zap.Uint64("from_user_id", req.FromUserId),
	)

	toUserID := fmt.Sprintf("%d", req.ToUserId)

	// 检查用户是否在线
	if !s.channelManager.IsUserOnline(toUserID) {
		s.logger.Debug("[gRPC] 用户不在线，跳过好友请求推送",
			zap.String("to_user_id", toUserID),
		)
		return &pb.WebBaseResponse{
			Code:    404,
			Message: "用户不在线",
		}, nil
	}

	// 构建响应消息
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_FRIEND_REQUEST, // 好友请求推送（下行）
		Payload: mustMarshal(req),
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 发送给用户
	if err := s.broadcastToUser(toUserID, response); err != nil {
		s.logger.Error("[gRPC] 发送好友请求推送失败",
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		return &pb.WebBaseResponse{
			Code:    500,
			Message: err.Error(),
		}, nil
	}

	s.logger.Info("[gRPC] 好友请求推送成功",
		zap.String("to_user_id", toUserID),
		zap.Uint64("from_user_id", req.FromUserId),
	)

	return &pb.WebBaseResponse{
		Code:    200,
		Message: "success",
	}, nil
}

// PushFriendResponse2Client 推送好友申请处理结果到客户端（对标 Java pushFriendResponse2Client）
func (s *MessageServiceImpl) PushFriendResponse2Client(ctx context.Context, req *pb.FriendResponsePush) (*pb.WebBaseResponse, error) {
	s.logger.Info("[gRPC] 处理好友申请处理结果推送",
		zap.Uint64("to_user_id", req.ToUserId),
		zap.Int32("status", req.Status),
	)

	toUserID := fmt.Sprintf("%d", req.ToUserId)

	// 检查用户是否在线
	if !s.channelManager.IsUserOnline(toUserID) {
		return &pb.WebBaseResponse{
			Code:    404,
			Message: "用户不在线",
		}, nil
	}

	// 构建响应消息
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_FRIEND_RESPONSE, // 好友响应推送（下行）
		Payload: mustMarshal(req),
		Code:    pb.ProtoResponseCode_SUCCESS,
	}

	// 发送给用户
	if err := s.broadcastToUser(toUserID, response); err != nil {
		s.logger.Error("[gRPC] 发送好友申请处理结果失败",
			zap.String("to_user_id", toUserID),
			zap.Error(err),
		)
		return &pb.WebBaseResponse{
			Code:    500,
			Message: err.Error(),
		}, nil
	}

	return &pb.WebBaseResponse{
		Code:    200,
		Message: "success",
	}, nil
}

// TransferC2CMsg 跨服务器C2C消息转发（对标 Java transferC2CMsg）
func (s *MessageServiceImpl) TransferC2CMsg(ctx context.Context, req *pb.ImProtoRequest) (*pb.WebBaseResponse, error) {
	s.logger.Info("[gRPC] 处理跨服务器C2C消息转发",
		zap.String("msg_type", req.Type.String()),
	)

	// 调用 C2C 策略的跨服务器接收方法
	if s.c2cStrategy != nil {
		if err := s.c2cStrategy.ReceiveAndSendMsg(req); err != nil {
			s.logger.Error("[gRPC] 跨服务器消息转发失败",
				zap.Error(err),
			)
			return &pb.WebBaseResponse{
				Code:    500,
				Message: err.Error(),
			}, nil
		}
	} else {
		s.logger.Warn("[gRPC] C2C策略未设置，跳过消息转发")
		return &pb.WebBaseResponse{
			Code:    500,
			Message: "C2C策略未设置",
		}, nil
	}

	s.logger.Info("[gRPC] 跨服务器消息转发成功")

	return &pb.WebBaseResponse{
		Code:    200,
		Message: "success",
	}, nil
}

// broadcastToUser 广播消息给用户
func (s *MessageServiceImpl) broadcastToUser(userID string, response *pb.ImProtoResponse) error {
	// 序列化响应
	data, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化响应失败: %w", err)
	}

	// 广播给用户
	return s.channelManager.BroadcastToUser(userID, data)
}

// mustMarshal 序列化（忽略错误）
func mustMarshal(m proto.Message) []byte {
	data, _ := proto.Marshal(m)
	return data
}
