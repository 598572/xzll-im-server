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

	"github.com/panjf2000/gnet/v2"
	"go.uber.org/zap"
	"google.golang.org/protobuf/proto"
)

// MessageHandler 消息处理器 (nbio 版本)
type MessageHandler struct {
	config         *config.Config
	logger         *zap.Logger
	channelManager *channel.NbioManager
	mqProducer     *mq.Producer
	redisClient    *redis.RedisClient
	strategies     map[pb.MsgType]strategy.ProtoMsgHandlerStrategy
}

// NewMessageHandler 创建消息处理器
func NewMessageHandler(cfg *config.Config, logger *zap.Logger, channelManager *channel.NbioManager, mqProducer *mq.Producer, redisClient *redis.RedisClient) *MessageHandler {
	handler := &MessageHandler{
		config:         cfg,
		logger:         logger,
		channelManager: channelManager,
		mqProducer:     mqProducer,
		redisClient:    redisClient,
		strategies:     make(map[pb.MsgType]strategy.ProtoMsgHandlerStrategy),
	}
	handler.registerStrategies()
	logger.Info("✅ [nbio] 消息处理器初始化完成", zap.Int("strategy_count", len(handler.strategies)))
	return handler
}

// registerStrategies 注册消息处理策略
func (h *MessageHandler) registerStrategies() {
	// 使用 nbio 的 channelManager
	c2cSendStrategy := strategy.NewC2CMsgSendStrategy(h.config, h.logger, h.channelManager, h.mqProducer, h.redisClient)
	h.strategies[pb.MsgType_C2C_SEND] = c2cSendStrategy

	c2cAckStrategy := strategy.NewC2CMsgAckStrategy(h.config, h.logger, h.channelManager)
	h.strategies[pb.MsgType_CLIENT_RECEIVED_MSG_ACK] = c2cAckStrategy

	withdrawStrategy := strategy.NewWithdrawMsgStrategy(h.config, h.logger, h.channelManager)
	h.strategies[pb.MsgType_WITHDRAW_MSG_SEND] = withdrawStrategy
}

// HandleGnetPayload 处理来自 gnet 的 WebSocket 帧负载
// payload 是已经解帧后的纯业务数据
func (h *MessageHandler) HandleGnetPayload(c gnet.Conn, payload []byte) {
	startTime := time.Now()

	// 从 gnet.Conn 的 Context 中获取 userID
	userID, ok := c.Context().(string)
	if !ok || userID == "" {
		h.logger.Warn("无法从 gnet Context 获取 userID，关闭连接")
		c.Close()
		return
	}

	// 1. 解析 Protobuf
	protoRequest := &pb.ImProtoRequest{}
	if err := proto.Unmarshal(payload, protoRequest); err != nil {
		h.logger.Error("解析 Protobuf 消息失败", zap.String("user_id", userID), zap.Error(err))
		return
	}

	// 2. 查找并执行策略
	msgStrategy, exists := h.strategies[protoRequest.Type]
	if !exists {
		h.logger.Warn("未找到消息处理策略",
			zap.String("user_id", userID),
			zap.String("msg_type", protoRequest.Type.String()),
		)
		return
	}

	// 适配：策略模式的 Exchange 方法需要能处理 gnet.Conn
	if adaptedStrategy, ok := msgStrategy.(strategy.GnetProtoMsgHandlerStrategy); ok {
		if err := adaptedStrategy.Exchange(c, protoRequest); err != nil {
			h.logger.Error("消息处理失败",
				zap.String("user_id", userID),
				zap.String("msg_type", protoRequest.Type.String()),
				zap.Error(err),
			)
		}
	} else {
		h.logger.Error("策略未适配 gnet", zap.String("msg_type", protoRequest.Type.String()))
	}

	h.logger.Debug("消息处理完成",
		zap.String("user_id", userID),
		zap.String("msg_type", protoRequest.Type.String()),
		zap.Duration("processing_time", time.Since(startTime)),
	)
}

// HandleCrossServerMessage 处理跨服务器消息
func (h *MessageHandler) HandleCrossServerMessage(protoRequest *pb.ImProtoRequest) error {
	// ... (这部分逻辑暂时不变)
	return nil
}

// HandleBinaryMessage 处理二进制消息（支持 Connection 接口）
func (h *MessageHandler) HandleBinaryMessage(conn channel.Connection, message []byte) error {
	startTime := time.Now()
	userID := conn.GetUserID()

	// 1. 解析 Protobuf
	protoRequest := &pb.ImProtoRequest{}
	if err := proto.Unmarshal(message, protoRequest); err != nil {
		h.logger.Error("解析 Protobuf 消息失败", zap.String("user_id", userID), zap.Error(err))
		return err
	}

	// 2. 查找并执行策略
	msgStrategy, exists := h.strategies[protoRequest.Type]
	if !exists {
		h.logger.Warn("未找到消息处理策略",
			zap.String("user_id", userID),
			zap.String("msg_type", protoRequest.Type.String()),
		)
		return fmt.Errorf("未找到消息处理策略: %s", protoRequest.Type.String())
	}

	// 3. 调用策略处理消息
	if err := msgStrategy.Exchange(conn, protoRequest); err != nil {
		h.logger.Error("消息处理失败",
			zap.String("user_id", userID),
			zap.String("msg_type", protoRequest.Type.String()),
			zap.Error(err),
		)
		return err
	}

	h.logger.Debug("消息处理完成",
		zap.String("user_id", userID),
		zap.String("msg_type", protoRequest.Type.String()),
		zap.Duration("processing_time", time.Since(startTime)),
	)

	return nil
}
