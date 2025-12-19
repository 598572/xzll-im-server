package mq

import (
	"context"
	"encoding/json"
	"fmt"
	"strings"
	"time"

	"github.com/apache/rocketmq-client-go/v2"
	"github.com/apache/rocketmq-client-go/v2/primitive"
	"github.com/apache/rocketmq-client-go/v2/producer"
	"go.uber.org/zap"
)

// Topic 常量（对应 Java 的 ImConstant.TopicConstant）
const (
	C2C_MSG_TOPIC = "XZLL_C2CMSG_TOPIC" // 单聊消息主题
)

// ClusterEventType 事件类型常量（对应 Java 的 ImConstant.ClusterEventTypeConstant）
const (
	ClusterEventType_C2C_SEND_MSG            = "C2C_SEND_MSG"            // 单聊消息发送
	ClusterEventType_C2C_OFF_LINE_MSG        = "C2C_OFF_LINE_MSG"        // 离线消息
	ClusterEventType_C2C_CLIENT_RECEIVED_ACK = "C2C_CLIENT_RECEIVED_ACK" // 客户端ACK
	ClusterEventType_C2C_WITHDRAW_MSG        = "C2C_WITHDRAW_MSG"        // 撤回消息
)

// Producer RocketMQ 生产者
type Producer struct {
	producer rocketmq.Producer
	config   *Config
	logger   *zap.Logger
}

// NewProducer 创建 RocketMQ 生产者
func NewProducer(cfg *Config, logger *zap.Logger) (*Producer, error) {
	if cfg == nil {
		cfg = DefaultConfig()
	}

	// 解析 NameServer 地址（支持分号分隔，对应 Java 配置格式）
	nameServers := strings.Split(cfg.ServerAddr, ";")
	for i, ns := range nameServers {
		nameServers[i] = strings.TrimSpace(ns)
	}

	logger.Info("初始化 RocketMQ Producer",
		zap.Strings("name_servers", nameServers),
		zap.String("group", cfg.Producer.GroupName),
	)

	// 创建生产者（对应 Java 的 RocketMQTemplate）
	p, err := rocketmq.NewProducer(
		producer.WithGroupName(cfg.Producer.GroupName),
		producer.WithNameServer(nameServers),
		producer.WithRetry(cfg.Producer.RetryTimes),
		producer.WithSendMsgTimeout(time.Duration(cfg.Producer.SendTimeout)*time.Second),
		producer.WithDefaultTopicQueueNums(4), // 默认队列数
	)

	if err != nil {
		return nil, fmt.Errorf("创建 RocketMQ Producer 失败: %w", err)
	}

	// 启动生产者
	if err := p.Start(); err != nil {
		return nil, fmt.Errorf("启动 RocketMQ Producer 失败: %w", err)
	}

	logger.Info("✅ RocketMQ Producer 启动成功",
		zap.Strings("name_servers", nameServers),
		zap.String("group", cfg.Producer.GroupName),
		zap.Int("retry_times", cfg.Producer.RetryTimes),
		zap.Int("send_timeout", cfg.Producer.SendTimeout),
	)

	return &Producer{
		producer: p,
		config:   cfg,
		logger:   logger,
	}, nil
}

// ClusterEvent 集群事件（对应 Java 的 ClusterEvent）
type ClusterEvent struct {
	ClusterEventType string `json:"clusterEventType"` // 事件类型
	Data             string `json:"data"`             // 事件数据（JSON字符串）
}

// C2CMsgEvent C2C 消息事件（对应 Java 的 C2CSendMsgAO）
type C2CMsgEvent struct {
	ClientMsgID   string `json:"clientMsgId"`   // 客户端消息ID（UUID字符串）
	MsgID         string `json:"msgId"`         // 服务器消息ID（雪花ID字符串）
	FromUserID    string `json:"fromUserId"`    // 发送人ID（雪花ID字符串）
	ToUserID      string `json:"toUserId"`      // 接收人ID（雪花ID字符串）
	ChatID        string `json:"chatId"`        // 会话ID
	MsgContent    string `json:"msgContent"`    // 消息内容
	MsgFormat     int32  `json:"msgFormat"`     // 消息格式
	MsgCreateTime int64  `json:"msgCreateTime"` // 消息创建时间（毫秒时间戳）
}

// C2COffLineMsgEvent 离线消息事件（对应 Java 的 C2COffLineMsgAO）
type C2COffLineMsgEvent struct {
	ClientMsgID   string `json:"clientMsgId"` // 客户端消息ID
	MsgID         string `json:"msgId"`       // 服务器消息ID
	FromUserID    string `json:"fromUserId"`  // 发送人ID
	ToUserID      string `json:"toUserId"`    // 接收人ID
	ChatID        string `json:"chatId"`      // 会话ID
	MsgContent    string `json:"msgContent"`  // 消息内容
	MsgFormat     int32  `json:"msgFormat"`   // 消息格式
	MsgStatus     int32  `json:"msgStatus"`   // 消息状态（1-离线）
	MsgCreateTime int64  `json:"msgCreateTime"`
}

// C2CReceivedMsgAckEvent 客户端ACK事件（对应 Java 的 C2CReceivedMsgAckAO）
type C2CReceivedMsgAckEvent struct {
	ClientMsgID string `json:"clientMsgId"` // 客户端消息ID
	MsgID       string `json:"msgId"`       // 服务器消息ID
	FromUserID  string `json:"fromUserId"`  // 发送人ID
	ToUserID    string `json:"toUserId"`    // 接收人ID
	ChatID      string `json:"chatId"`      // 会话ID
	AckTime     int64  `json:"ackTime"`     // ACK时间
}

// SendC2CMsg 发送单聊消息事件（对应 Java 的 C2CMsgProvider.sendC2CMsg()）
func (p *Producer) SendC2CMsg(event *C2CMsgEvent) error {
	// 1. 序列化消息数据
	dataJSON, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("序列化消息失败: %w", err)
	}

	// 2. 构建 ClusterEvent（对应 Java 的 ClusterEvent）
	clusterEvent := &ClusterEvent{
		ClusterEventType: ClusterEventType_C2C_SEND_MSG,
		Data:             string(dataJSON),
	}

	// 3. 发送到 RocketMQ
	return p.sendClusterEvent(C2C_MSG_TOPIC, clusterEvent, event.MsgID, "C2C_SEND")
}

// SendOffLineMsg 发送离线消息事件（对应 Java 的 C2CMsgProvider.offLineMsg()）
func (p *Producer) SendOffLineMsg(event *C2COffLineMsgEvent) error {
	// 1. 序列化消息数据
	dataJSON, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("序列化离线消息失败: %w", err)
	}

	// 2. 构建 ClusterEvent
	clusterEvent := &ClusterEvent{
		ClusterEventType: ClusterEventType_C2C_OFF_LINE_MSG,
		Data:             string(dataJSON),
	}

	// 3. 发送到 RocketMQ
	return p.sendClusterEvent(C2C_MSG_TOPIC, clusterEvent, event.MsgID, "C2C_OFFLINE")
}

// SendClientReceivedAck 发送客户端ACK事件（对应 Java 的 C2CMsgProvider.clientResponseAck()）
func (p *Producer) SendClientReceivedAck(event *C2CReceivedMsgAckEvent) error {
	// 1. 序列化消息数据
	dataJSON, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("序列化ACK事件失败: %w", err)
	}

	// 2. 构建 ClusterEvent
	clusterEvent := &ClusterEvent{
		ClusterEventType: ClusterEventType_C2C_CLIENT_RECEIVED_ACK,
		Data:             string(dataJSON),
	}

	// 3. 发送到 RocketMQ
	return p.sendClusterEvent(C2C_MSG_TOPIC, clusterEvent, event.MsgID, "C2C_ACK")
}

// sendClusterEvent 发送集群事件到 RocketMQ（对应 Java 的 RocketMqProducerWrap.sendClusterEvent()）
func (p *Producer) sendClusterEvent(topic string, clusterEvent *ClusterEvent, msgID string, tag string) error {
	// 1. 序列化 ClusterEvent
	eventJSON, err := json.Marshal(clusterEvent)
	if err != nil {
		return fmt.Errorf("序列化 ClusterEvent 失败: %w", err)
	}

	// 2. 构建 RocketMQ 消息
	msg := &primitive.Message{
		Topic: topic,
		Body:  eventJSON,
	}
	msg.WithTag(tag)
	msg.WithKeys([]string{msgID})

	// 3. 异步发送（对应 Java 的 asyncSend）
	err = p.producer.SendAsync(context.Background(),
		func(ctx context.Context, result *primitive.SendResult, err error) {
			if err != nil {
				p.logger.Error("❌ 消息投递 RocketMQ 失败",
					zap.String("topic", topic),
					zap.String("tag", tag),
					zap.String("msg_id", msgID),
					zap.String("event_type", clusterEvent.ClusterEventType),
					zap.Error(err),
				)
			} else {
				p.logger.Info("✅ 消息投递 RocketMQ 成功",
					zap.String("topic", topic),
					zap.String("tag", tag),
					zap.String("msg_id", msgID),
					zap.String("event_type", clusterEvent.ClusterEventType),
					zap.String("queue", result.MessageQueue.String()),
					zap.Int64("offset", result.QueueOffset),
				)
			}
		},
		msg,
	)

	if err != nil {
		p.logger.Error("❌ 提交 RocketMQ 发送任务失败",
			zap.String("msg_id", msgID),
			zap.Error(err),
		)
		return fmt.Errorf("提交 RocketMQ 发送任务失败: %w", err)
	}

	return nil
}

// Stop 停止生产者
func (p *Producer) Stop() error {
	p.logger.Info("🔄 停止 RocketMQ Producer...")

	if err := p.producer.Shutdown(); err != nil {
		p.logger.Error("停止 RocketMQ Producer 失败", zap.Error(err))
		return err
	}

	p.logger.Info("✅ RocketMQ Producer 已停止")
	return nil
}

// GetProducer 获取原生生产者（用于高级操作）
func (p *Producer) GetProducer() rocketmq.Producer {
	return p.producer
}
