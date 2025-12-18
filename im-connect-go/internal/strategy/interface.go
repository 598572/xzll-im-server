package strategy

import (
	"im-connect-go/internal/channel"
	pb "im-connect-go/internal/proto"
)

// ProtoMsgHandlerStrategy Protobuf 消息处理策略接口（对标 Java ProtoMsgHandlerStrategy）
// 实现策略模式，每个消息类型对应一个具体策略
type ProtoMsgHandlerStrategy interface {
	// SupportMsgType 返回该策略支持的消息类型
	SupportMsgType() pb.MsgType

	// Exchange 处理客户端直连的 Protobuf 消息（对标 Java exchange 方法）
	// 职责：
	// 1. 保存消息到数据库
	// 2. 查找接收人并推送/转发
	Exchange(conn channel.Connection, protoRequest *pb.ImProtoRequest) error
}

// CrossServerMessageHandler 跨服务器消息处理接口（对标 Java receiveAndSendMsg）
// 用于处理从其他服务器转发过来的消息
type CrossServerMessageHandler interface {
	// ReceiveAndSendMsg 接收并转发跨服务器的 Protobuf 消息
	// 职责：
	// 1. 不保存消息（避免重复，消息已在源服务器保存）
	// 2. 二次校验接收人在线状态
	// 3. 直接推送给本地客户端
	ReceiveAndSendMsg(protoRequest *pb.ImProtoRequest) error
}

// MessageValidator 消息验证接口（扩展）
type MessageValidator interface {
	// ValidateMessage 验证消息内容和格式
	ValidateMessage(conn channel.Connection, payload []byte) error
}

// MessagePersistence 消息持久化接口（扩展）
type MessagePersistence interface {
	// SaveMessage 保存消息到数据库
	SaveMessage(messageData interface{}) error

	// GetOfflineMessages 获取离线消息
	GetOfflineMessages(userID string, limit int) ([]interface{}, error)
}

// MessageNotifier 消息通知接口（扩展）
type MessageNotifier interface {
	// NotifyUser 通知用户有新消息（推送通知、邮件等）
	NotifyUser(userID string, messageData interface{}) error
}

// MessageReliability 消息可靠性保证接口（扩展）
type MessageReliability interface {
	// EnsureDelivery 确保消息投递（重试机制）
	EnsureDelivery(userID string, messageData []byte, maxRetries int) error

	// HandleDeliveryFailure 处理投递失败（离线消息、推送通知等）
	HandleDeliveryFailure(userID string, messageData interface{}) error
}
