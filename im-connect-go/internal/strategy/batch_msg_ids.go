package strategy

import (
	"context"
	"fmt"
	"sync"
	"time"

	"im-connect-go/internal/channel"
	pb "im-connect-go/internal/proto"

	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// ClientGetBatchMsgIdsStrategy 批量获取消息ID策略
// 对标 Java ClientGetBatchMsgIdsProtoStrategyImpl
// 功能：
// 1. 客户端请求批量消息ID用于同步
// 2. 支持增量同步（基于lastMsgId）
// 3. 支持分页获取
type ClientGetBatchMsgIdsStrategy struct {
	channelManager *channel.Manager
	logger         *zap.Logger

	// 缓存最近的消息ID列表（减少数据库查询）
	msgIDCache sync.Map // conversationKey -> []uint64
	cacheTTL   time.Duration
}

// NewClientGetBatchMsgIdsStrategy 创建策略
func NewClientGetBatchMsgIdsStrategy(channelManager *channel.Manager, logger *zap.Logger) *ClientGetBatchMsgIdsStrategy {
	return &ClientGetBatchMsgIdsStrategy{
		channelManager: channelManager,
		logger:         logger,
		cacheTTL:       5 * time.Minute,
	}
}

// GetMsgType 返回处理的消息类型
func (s *ClientGetBatchMsgIdsStrategy) GetMsgType() pb.MsgType {
	return pb.MsgType_GET_BATCH_MSG_IDS
}

// Handle 处理批量获取消息ID请求
func (s *ClientGetBatchMsgIdsStrategy) Handle(ctx context.Context, userID string, request *pb.ImProtoRequest) (*pb.ImProtoResponse, error) {
	s.logger.Debug("收到批量获取消息ID请求",
		zap.String("user_id", userID),
	)

	// 1. 解析请求 (使用 GetBatchMsgIdsReq)
	var batchReq pb.GetBatchMsgIdsReq
	if err := proto.Unmarshal(request.Payload, &batchReq); err != nil {
		s.logger.Error("解析批量获取消息ID请求失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		return s.buildErrorResponse("请求格式错误"), nil
	}

	// 2. 参数校验
	if batchReq.UserId == 0 {
		return s.buildErrorResponse("用户ID不能为空"), nil
	}

	// 3. 获取消息ID列表
	msgIDs, err := s.getBatchMsgIds(ctx, userID, &batchReq)
	if err != nil {
		s.logger.Error("获取消息ID列表失败",
			zap.String("user_id", userID),
			zap.Error(err),
		)
		return s.buildErrorResponse(fmt.Sprintf("获取失败: %v", err)), nil
	}

	// 4. 构建响应
	response := s.buildSuccessResponse(msgIDs)

	s.logger.Info("批量获取消息ID成功",
		zap.String("user_id", userID),
		zap.Int("count", len(msgIDs)),
	)

	return response, nil
}

// getBatchMsgIds 获取批量消息ID
func (s *ClientGetBatchMsgIdsStrategy) getBatchMsgIds(ctx context.Context, userID string, req *pb.GetBatchMsgIdsReq) ([]uint64, error) {
	// 生成会话缓存key
	cacheKey := fmt.Sprintf("batch_msgs:%d", req.UserId)

	// 1. 先查缓存
	if cached, ok := s.msgIDCache.Load(cacheKey); ok {
		cachedIDs := cached.([]uint64)
		return cachedIDs, nil
	}

	// 2. 查询数据库（模拟，实际需要调用消息存储服务）
	msgIDs, err := s.queryMsgIDsFromDB(ctx, req)
	if err != nil {
		return nil, err
	}

	// 3. 缓存结果
	s.msgIDCache.Store(cacheKey, msgIDs)

	// 设置缓存过期（使用goroutine）
	go func() {
		time.Sleep(s.cacheTTL)
		s.msgIDCache.Delete(cacheKey)
	}()

	return msgIDs, nil
}

// queryMsgIDsFromDB 从数据库查询消息ID
func (s *ClientGetBatchMsgIdsStrategy) queryMsgIDsFromDB(ctx context.Context, req *pb.GetBatchMsgIdsReq) ([]uint64, error) {
	// TODO: 实际实现需要调用消息存储服务
	// 这里返回模拟数据
	s.logger.Debug("从数据库查询消息ID",
		zap.Uint64("user_id", req.UserId),
	)

	// 模拟返回一些消息ID
	// 实际应该调用 im-business 的消息存储服务
	var msgIDs []uint64
	baseID := uint64(time.Now().UnixMilli())
	for i := 0; i < 100; i++ {
		msgIDs = append(msgIDs, baseID+uint64(i))
	}

	return msgIDs, nil
}

// buildSuccessResponse 构建成功响应
func (s *ClientGetBatchMsgIdsStrategy) buildSuccessResponse(msgIDs []uint64) *pb.ImProtoResponse {
	resp := &pb.BatchMsgIdsPush{
		MsgIds: msgIDs,
	}

	payload, _ := proto.Marshal(resp)

	return &pb.ImProtoResponse{
		Type:    pb.MsgType_PUSH_BATCH_MSG_IDS,
		Code:    pb.ProtoResponseCode_SUCCESS,
		Payload: payload,
	}
}

// buildErrorResponse 构建错误响应
func (s *ClientGetBatchMsgIdsStrategy) buildErrorResponse(message string) *pb.ImProtoResponse {
	return &pb.ImProtoResponse{
		Type: pb.MsgType_PUSH_BATCH_MSG_IDS,
		Code: pb.ProtoResponseCode_INVALID_REQUEST,
		Msg:  message,
	}
}

// ClearCache 清除缓存
func (s *ClientGetBatchMsgIdsStrategy) ClearCache() {
	s.msgIDCache = sync.Map{}
	s.logger.Info("消息ID缓存已清除")
}

// ClearCacheForUser 清除指定用户的缓存
func (s *ClientGetBatchMsgIdsStrategy) ClearCacheForUser(userID uint64) {
	cacheKey := fmt.Sprintf("batch_msgs:%d", userID)
	s.msgIDCache.Delete(cacheKey)
	s.logger.Debug("清除用户消息ID缓存",
		zap.String("cache_key", cacheKey),
	)
}
