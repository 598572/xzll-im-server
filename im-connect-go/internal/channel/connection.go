package channel

import "time"

// Connection 连接接口，统一不同网络框架的连接抽象
type Connection interface {
	// SendBinary 发送二进制消息
	SendBinary(data []byte) error

	// SendPing 发送 Ping 消息
	SendPing(data []byte) error

	// SendPong 发送 Pong 消息
	SendPong(data []byte) error

	// GetUserID 获取用户ID
	GetUserID() string

	// GetRemoteAddr 获取远程地址
	GetRemoteAddr() string

	// IsActive 检查连接是否活跃
	IsActive() bool

	// Close 关闭连接
	Close() error

	// SetReadDeadline 设置读取超时
	SetReadDeadline(deadline time.Time) error
}
