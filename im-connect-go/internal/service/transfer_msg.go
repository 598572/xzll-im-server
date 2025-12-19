package service

import (
	"context"
	"fmt"
	"sync"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	pb "im-connect-go/internal/proto"
	"im-connect-go/pkg/redis"

	"go.uber.org/zap"
	"google.golang.org/grpc"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/protobuf/proto"
)

// TransferC2CMsgService 跨服务器消息转发服务
// 对标 Java TransferC2CMsgServiceImpl
// 功能：
// 1. 当接收者不在本地服务器时，转发消息到目标服务器
// 2. 通过gRPC调用目标服务器的推送接口
// 3. 连接池管理，复用gRPC连接
type TransferC2CMsgService struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.NbioManager

	// gRPC客户端连接池
	clientPool  sync.Map // serverAddr -> *grpc.ClientConn
	clientMutex sync.Mutex

	// 本地服务器地址
	localServerAddr string

	// 统计
	totalTransferred int64
	successCount     int64
	failureCount     int64
}

// NewTransferC2CMsgService 创建跨服务器消息转发服务
func NewTransferC2CMsgService(cfg *config.Config, logger *zap.Logger, channelManager *channel.NbioManager, localAddr string) *TransferC2CMsgService {
	service := &TransferC2CMsgService{
		config:          cfg,
		logger:          logger,
		channelManager:  channelManager,
		localServerAddr: localAddr,
	}

	logger.Info("✅ 跨服务器消息转发服务初始化完成",
		zap.String("local_server", localAddr),
	)

	return service
}

// TransferMessage 转发消息到目标服务器
// 对标 Java TransferC2CMsgServiceImpl.transferC2cMsgToTargetServer
func (s *TransferC2CMsgService) TransferMessage(ctx context.Context, toUserID string, message *pb.C2CMsgPush) error {
	s.totalTransferred++

	// 1. 查询接收者所在服务器
	targetServer, err := s.getTargetServer(toUserID)
	if err != nil {
		s.failureCount++
		return fmt.Errorf("获取目标服务器失败: %w", err)
	}

	// 2. 如果在本地服务器，直接发送
	if targetServer == s.localServerAddr {
		return s.sendToLocalUser(toUserID, message)
	}

	// 3. 跨服务器转发
	err = s.transferToRemoteServer(ctx, targetServer, toUserID, message)
	if err != nil {
		s.failureCount++
		return err
	}

	s.successCount++
	s.logger.Debug("消息跨服务器转发成功",
		zap.String("to_user", toUserID),
		zap.String("target_server", targetServer),
	)

	return nil
}

// getTargetServer 获取用户所在的目标服务器
func (s *TransferC2CMsgService) getTargetServer(userID string) (string, error) {
	// 从Redis获取用户所在服务器（对标 Java ROUTE_PREFIX）
	serverAddr, err := redis.HGet("userLogin:server:", userID)
	if err != nil {
		return "", err
	}
	if serverAddr == "" {
		return "", fmt.Errorf("用户 %s 不在线", userID)
	}
	return serverAddr, nil
}

// sendToLocalUser 发送给本地用户
func (s *TransferC2CMsgService) sendToLocalUser(userID string, message *pb.C2CMsgPush) error {
	// 构建响应
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_C2C_MSG_PUSH,
		Code:    pb.ProtoResponseCode_SUCCESS,
		Payload: mustMarshalProto(message),
	}

	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化消息失败: %w", err)
	}

	return s.channelManager.BroadcastToUser(userID, responseData)
}

// transferToRemoteServer 转发到远程服务器
func (s *TransferC2CMsgService) transferToRemoteServer(ctx context.Context, targetServer, toUserID string, message *pb.C2CMsgPush) error {
	// 1. 获取或创建gRPC连接
	client, err := s.getGRPCClient(targetServer)
	if err != nil {
		return fmt.Errorf("获取gRPC客户端失败: %w", err)
	}

	// 2. 构建推送请求（使用 ServerAckPush）
	pushReq := &pb.ServerAckPush{
		ToUserId:          parseUint64(toUserID),
		ClientMsgId:       message.ClientMsgId,
		MsgId:             message.MsgId,
		MsgReceivedStatus: 1, // SERVER_RECEIVED
		ReceiveTime:       uint64(time.Now().UnixMilli()),
	}

	// 3. 调用远程服务器
	timeoutCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	resp, err := client.ResponseServerAck2Client(timeoutCtx, pushReq)
	if err != nil {
		// 连接可能失效，清除缓存
		s.removeGRPCClient(targetServer)
		return fmt.Errorf("调用远程服务器失败: %w", err)
	}

	if resp.Code != 0 {
		return fmt.Errorf("远程服务器返回错误: %s", resp.Message)
	}

	return nil
}

// getGRPCClient 获取gRPC客户端（带连接池）
func (s *TransferC2CMsgService) getGRPCClient(serverAddr string) (pb.MessageServiceClient, error) {
	// 1. 先查缓存
	if conn, ok := s.clientPool.Load(serverAddr); ok {
		return pb.NewMessageServiceClient(conn.(*grpc.ClientConn)), nil
	}

	// 2. 加锁创建新连接
	s.clientMutex.Lock()
	defer s.clientMutex.Unlock()

	// 双重检查
	if conn, ok := s.clientPool.Load(serverAddr); ok {
		return pb.NewMessageServiceClient(conn.(*grpc.ClientConn)), nil
	}

	// 3. 创建新连接
	conn, err := grpc.Dial(
		serverAddr,
		grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithDefaultCallOptions(
			grpc.MaxCallRecvMsgSize(10*1024*1024), // 10MB
			grpc.MaxCallSendMsgSize(10*1024*1024),
		),
	)
	if err != nil {
		return nil, fmt.Errorf("创建gRPC连接失败: %w", err)
	}

	s.clientPool.Store(serverAddr, conn)
	s.logger.Info("创建新的gRPC连接",
		zap.String("target_server", serverAddr),
	)

	return pb.NewMessageServiceClient(conn), nil
}

// removeGRPCClient 移除gRPC客户端（连接失效时）
func (s *TransferC2CMsgService) removeGRPCClient(serverAddr string) {
	if conn, ok := s.clientPool.LoadAndDelete(serverAddr); ok {
		conn.(*grpc.ClientConn).Close()
		s.logger.Info("移除失效的gRPC连接",
			zap.String("target_server", serverAddr),
		)
	}
}

// TransferWithdrawMessage 转发撤回消息
func (s *TransferC2CMsgService) TransferWithdrawMessage(ctx context.Context, toUserID string, notification *pb.MessageWithdrawNotification) error {
	// 1. 查询接收者所在服务器
	targetServer, err := s.getTargetServer(toUserID)
	if err != nil {
		return fmt.Errorf("获取目标服务器失败: %w", err)
	}

	// 2. 如果在本地服务器，直接发送
	if targetServer == s.localServerAddr {
		return s.sendWithdrawToLocalUser(toUserID, notification)
	}

	// 3. 跨服务器转发
	client, err := s.getGRPCClient(targetServer)
	if err != nil {
		return fmt.Errorf("获取gRPC客户端失败: %w", err)
	}

	pushReq := &pb.WithdrawPush{
		ToUserId:   parseUint64(toUserID),
		MsgId:      notification.MsgId,
		FromUserId: notification.WithdrawnBy,
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	resp, err := client.SendWithdrawMsg2Client(timeoutCtx, pushReq)
	if err != nil {
		s.removeGRPCClient(targetServer)
		return fmt.Errorf("调用远程服务器失败: %w", err)
	}

	if resp.Code != 0 {
		return fmt.Errorf("远程服务器返回错误: %s", resp.Message)
	}

	return nil
}

// sendWithdrawToLocalUser 发送撤回通知给本地用户
func (s *TransferC2CMsgService) sendWithdrawToLocalUser(userID string, notification *pb.MessageWithdrawNotification) error {
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_MSG_WITHDRAW_NOTIFICATION,
		Code:    pb.ProtoResponseCode_SUCCESS,
		Payload: mustMarshalProto(notification),
	}

	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化消息失败: %w", err)
	}

	return s.channelManager.BroadcastToUser(userID, responseData)
}

// TransferFriendRequest 转发好友请求
func (s *TransferC2CMsgService) TransferFriendRequest(ctx context.Context, toUserID string, request *pb.FriendRequestPush) error {
	targetServer, err := s.getTargetServer(toUserID)
	if err != nil {
		return fmt.Errorf("获取目标服务器失败: %w", err)
	}

	if targetServer == s.localServerAddr {
		return s.sendFriendRequestToLocalUser(toUserID, request)
	}

	client, err := s.getGRPCClient(targetServer)
	if err != nil {
		return fmt.Errorf("获取gRPC客户端失败: %w", err)
	}

	timeoutCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()

	resp, err := client.PushFriendRequest2Client(timeoutCtx, request)
	if err != nil {
		s.removeGRPCClient(targetServer)
		return fmt.Errorf("调用远程服务器失败: %w", err)
	}

	if resp.Code != 0 {
		return fmt.Errorf("远程服务器返回错误: %s", resp.Message)
	}

	return nil
}

// sendFriendRequestToLocalUser 发送好友请求给本地用户
func (s *TransferC2CMsgService) sendFriendRequestToLocalUser(userID string, request *pb.FriendRequestPush) error {
	response := &pb.ImProtoResponse{
		Type:    pb.MsgType_FRIEND_REQUEST,
		Code:    pb.ProtoResponseCode_SUCCESS,
		Payload: mustMarshalProto(request),
	}

	responseData, err := proto.Marshal(response)
	if err != nil {
		return fmt.Errorf("序列化消息失败: %w", err)
	}

	return s.channelManager.BroadcastToUser(userID, responseData)
}

// GetStats 获取统计信息
func (s *TransferC2CMsgService) GetStats() TransferStats {
	return TransferStats{
		TotalTransferred: s.totalTransferred,
		SuccessCount:     s.successCount,
		FailureCount:     s.failureCount,
	}
}

// TransferStats 转发统计
type TransferStats struct {
	TotalTransferred int64 `json:"total_transferred"`
	SuccessCount     int64 `json:"success_count"`
	FailureCount     int64 `json:"failure_count"`
}

// Shutdown 关闭服务
func (s *TransferC2CMsgService) Shutdown(ctx context.Context) {
	s.logger.Info("🔄 关闭跨服务器消息转发服务...")

	// 关闭所有gRPC连接
	s.clientPool.Range(func(key, value interface{}) bool {
		conn := value.(*grpc.ClientConn)
		conn.Close()
		return true
	})

	s.logger.Info("✅ 跨服务器消息转发服务已关闭")
}

// mustMarshalProto proto序列化
func mustMarshalProto(m proto.Message) []byte {
	data, _ := proto.Marshal(m)
	return data
}
