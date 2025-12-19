package channel

import (
	"errors"
	"sync"
	"sync/atomic"
	"time"

	"im-connect-go/internal/config"

	"github.com/lesismal/nbio"
	"github.com/lesismal/nbio/nbhttp/websocket"
	"go.uber.org/zap"
)

var (
	ErrConnClosed = errors.New("connection closed")
)

// NbioConnection nbio 连接包装器，实现 Connection 接口
type NbioConnection struct {
	conn   *nbio.Conn
	wsConn *websocket.Conn
	userID string
}

// SendBinary 发送二进制消息
func (c *NbioConnection) SendBinary(data []byte) error {
	if c.wsConn == nil {
		return ErrConnClosed
	}
	return c.wsConn.WriteMessage(websocket.BinaryMessage, data)
}

// SendPing 发送 Ping 消息
func (c *NbioConnection) SendPing(data []byte) error {
	if c.wsConn == nil {
		return ErrConnClosed
	}
	return c.wsConn.WriteMessage(websocket.PingMessage, data)
}

// SendPong 发送 Pong 消息
func (c *NbioConnection) SendPong(data []byte) error {
	if c.wsConn == nil {
		return ErrConnClosed
	}
	return c.wsConn.WriteMessage(websocket.PongMessage, data)
}

// GetUserID 获取用户ID
func (c *NbioConnection) GetUserID() string {
	return c.userID
}

// GetRemoteAddr 获取远程地址
func (c *NbioConnection) GetRemoteAddr() string {
	if c.wsConn != nil {
		return c.wsConn.RemoteAddr().String()
	}
	if c.conn != nil {
		return c.conn.RemoteAddr().String()
	}
	return ""
}

// IsActive 检查连接是否活跃
func (c *NbioConnection) IsActive() bool {
	return c.conn != nil && c.wsConn != nil
}

// Close 关闭连接
func (c *NbioConnection) Close() error {
	if c.wsConn != nil {
		return c.wsConn.Close()
	}
	return nil
}

// SetReadDeadline 设置读取超时
func (c *NbioConnection) SetReadDeadline(deadline time.Time) error {
	if c.wsConn == nil {
		return ErrConnClosed
	}
	return c.wsConn.SetReadDeadline(deadline)
}

// NbioManager nbio 版本的连接管理器
type NbioManager struct {
	config *config.Config
	logger *zap.Logger

	// 用户ID到连接的映射（使用 websocket.Conn 作为 key，因为 nbio websocket 的连接结构）
	userConnections map[string]map[*websocket.Conn]struct{}
	mutex           sync.RWMutex

	// 连接映射：websocket.Conn -> NbioConnection（因为 nbio websocket 使用 websocket.Conn）
	connMap sync.Map // map[*websocket.Conn]*NbioConnection

	// 统计信息
	currentConnections int64
}

// NewNbioManager 创建新的 nbio 连接管理器
func NewNbioManager(cfg *config.Config, logger *zap.Logger) *NbioManager {
	return &NbioManager{
		config:          cfg,
		logger:          logger,
		userConnections: make(map[string]map[*websocket.Conn]struct{}),
	}
}

// AddConnection 添加连接（使用 websocket.Conn）
func (m *NbioManager) AddConnection(userID string, wsConn *websocket.Conn) error {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	if _, ok := m.userConnections[userID]; !ok {
		m.userConnections[userID] = make(map[*websocket.Conn]struct{})
	}
	m.userConnections[userID][wsConn] = struct{}{}
	atomic.AddInt64(&m.currentConnections, 1)

	m.logger.Debug("➕ [nbio] 连接已添加",
		zap.String("user_id", userID),
		zap.String("remote_addr", wsConn.RemoteAddr().String()),
		zap.Int64("current_connections", m.GetConnectionCount()),
	)

	return nil
}

// RemoveConnection 移除连接（使用 websocket.Conn）
func (m *NbioManager) RemoveConnection(userID string, wsConn *websocket.Conn) {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	if conns, ok := m.userConnections[userID]; ok {
		if wsConn != nil {
			if _, ok := conns[wsConn]; ok {
				delete(conns, wsConn)
				if len(conns) == 0 {
					delete(m.userConnections, userID)
				}
				atomic.AddInt64(&m.currentConnections, -1)

				m.logger.Debug("➖ [nbio] 连接已移除",
					zap.String("user_id", userID),
					zap.String("remote_addr", wsConn.RemoteAddr().String()),
					zap.Int64("current_connections", m.GetConnectionCount()),
				)
			}
		} else {
			// 如果 wsConn 为 nil，移除该用户的所有连接
			for conn := range conns {
				delete(conns, conn)
				m.connMap.Delete(conn)
			}
			delete(m.userConnections, userID)
			atomic.AddInt64(&m.currentConnections, -int64(len(conns)))
		}
	}

	// 清理连接映射
	if wsConn != nil {
		m.connMap.Delete(wsConn)
	}
}

// GetConnection 获取连接包装器（使用 websocket.Conn）
func (m *NbioManager) GetConnection(userID string, wsConn *websocket.Conn) Connection {
	// 如果提供了 wsConn，直接查找
	if wsConn != nil {
		if connWrapper, ok := m.connMap.Load(wsConn); ok {
			return connWrapper.(*NbioConnection)
		}
		return nil
	}

	// 如果没有提供 wsConn，查找用户的第一个连接
	conns := m.GetUserConnections(userID)
	if len(conns) > 0 {
		if connWrapper, ok := m.connMap.Load(conns[0]); ok {
			return connWrapper.(*NbioConnection)
		}
	}
	return nil
}

// GetUserConnections 获取用户的所有连接（返回 websocket.Conn）
func (m *NbioManager) GetUserConnections(userID string) []*websocket.Conn {
	m.mutex.RLock()
	defer m.mutex.RUnlock()

	conns, ok := m.userConnections[userID]
	if !ok {
		return nil
	}

	connections := make([]*websocket.Conn, 0, len(conns))
	for conn := range conns {
		connections = append(connections, conn)
	}
	return connections
}

// BroadcastToUser 向用户广播消息
func (m *NbioManager) BroadcastToUser(userID string, message []byte) error {
	connections := m.GetUserConnections(userID)
	if len(connections) == 0 {
		return nil
	}

	for _, wsConn := range connections {
		if connWrapper, ok := m.connMap.Load(wsConn); ok {
			nbioConn := connWrapper.(*NbioConnection)
			if err := nbioConn.SendBinary(message); err != nil {
				m.logger.Warn("向用户发送消息失败",
					zap.String("user_id", userID),
					zap.Error(err),
				)
			}
		}
	}
	return nil
}

// IsUserOnline 检查用户是否在线
func (m *NbioManager) IsUserOnline(userID string) bool {
	m.mutex.RLock()
	defer m.mutex.RUnlock()
	conns, ok := m.userConnections[userID]
	return ok && len(conns) > 0
}

// GetConnectionCount 获取连接数
func (m *NbioManager) GetConnectionCount() int64 {
	return atomic.LoadInt64(&m.currentConnections)
}

// CanAcceptConnection 检查是否可以接受新连接
func (m *NbioManager) CanAcceptConnection(userID string) bool {
	maxConnections := m.config.Server.MaxConnections
	if maxConnections > 0 && m.GetConnectionCount() >= int64(maxConnections) {
		return false
	}
	return true
}

// CloseAllConnections 关闭所有连接
func (m *NbioManager) CloseAllConnections() {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	for userID, conns := range m.userConnections {
		for wsConn := range conns {
			wsConn.Close()
			m.logger.Debug("关闭连接",
				zap.String("user_id", userID),
				zap.String("remote_addr", wsConn.RemoteAddr().String()),
			)
		}
	}

	m.userConnections = make(map[string]map[*websocket.Conn]struct{})
	atomic.StoreInt64(&m.currentConnections, 0)
	m.connMap.Range(func(key, value interface{}) bool {
		m.connMap.Delete(key)
		return true
	})

	m.logger.Info("✅ [nbio] 所有连接已关闭")
}

// RegisterConnection 注册连接包装器（使用 websocket.Conn 作为 key）
func (m *NbioManager) RegisterConnection(conn *nbio.Conn, wsConn *websocket.Conn, userID string) {
	nbioConn := &NbioConnection{
		conn:   conn, // 可能为 nil
		wsConn: wsConn,
		userID: userID,
	}
	// 使用 websocket.Conn 作为 key，因为这是主要的连接对象
	m.connMap.Store(wsConn, nbioConn)
}

// GetConnectionWrapper 获取连接包装器（通过 websocket.Conn）
func (m *NbioManager) GetConnectionWrapper(wsConn *websocket.Conn) (*NbioConnection, bool) {
	if connWrapper, ok := m.connMap.Load(wsConn); ok {
		return connWrapper.(*NbioConnection), true
	}
	return nil, false
}
