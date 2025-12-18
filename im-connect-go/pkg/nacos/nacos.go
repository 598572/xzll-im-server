package nacos

import (
	"fmt"
	"regexp"
	"strings"
	"time"

	"im-connect-go/internal/config"

	"github.com/nacos-group/nacos-sdk-go/clients"
	"github.com/nacos-group/nacos-sdk-go/clients/config_client"
	"github.com/nacos-group/nacos-sdk-go/common/constant"
	"github.com/nacos-group/nacos-sdk-go/vo"
	"go.uber.org/zap"
	"gopkg.in/yaml.v3"
)

var (
	configClient config_client.IConfigClient
	logger       *zap.Logger
	appConfig    *config.Config
	// 共享配置变量（类似 Java 的 global.yaml）
	sharedVars map[string]string
)

// InitNacosConfig 初始化 Nacos 配置中心
func InitNacosConfig(cfg *config.Config, log *zap.Logger) error {
	if log != nil {
		logger = log
	} else {
		logger = zap.NewNop() // 回退到空日志
	}
	appConfig = cfg

	// 服务器配置
	serverConfigs := []constant.ServerConfig{
		{
			IpAddr:      parseNacosAddress(cfg.Nacos.ServerAddr),
			Port:        parseNacosPort(cfg.Nacos.ServerAddr),
			ContextPath: cfg.Nacos.ContextPath,
		},
	}

	// 客户端配置
	clientConfig := constant.ClientConfig{
		NamespaceId:         cfg.Nacos.Namespace,
		TimeoutMs:           uint64(cfg.Nacos.Timeout),
		NotLoadCacheAtStart: true,
		LogDir:              "/tmp/nacos/log",
		CacheDir:            "/tmp/nacos/cache",
		LogLevel:            "info",
		Username:            cfg.Nacos.Username,
		Password:            cfg.Nacos.Password,
	}

	// 创建配置客户端
	client, err := clients.NewConfigClient(
		vo.NacosClientParam{
			ClientConfig:  &clientConfig,
			ServerConfigs: serverConfigs,
		},
	)
	if err != nil {
		return fmt.Errorf("创建 Nacos 配置客户端失败: %w", err)
	}

	configClient = client

	// 初始化共享变量
	sharedVars = make(map[string]string)

	// 1. 先加载共享配置（类似 Java shared-configs）
	if err := loadSharedConfigs(cfg); err != nil {
		logger.Warn("加载共享配置失败", zap.Error(err))
		// 不返回错误，继续使用默认配置
	}

	// 2. 再加载服务配置
	if err := loadInitialConfig(cfg); err != nil {
		logger.Warn("加载初始 Nacos 配置失败", zap.Error(err))
		// 不返回错误，使用默认配置继续运行
	}

	// 监听配置变化
	if err := listenConfigChange(cfg); err != nil {
		logger.Warn("监听 Nacos 配置变化失败", zap.Error(err))
		// 不返回错误，使用静态配置继续运行
	}

	logger.Info("✅ Nacos 配置中心初始化成功",
		zap.String("server_addr", cfg.Nacos.ServerAddr),
		zap.String("namespace", cfg.Nacos.Namespace),
		zap.String("group", cfg.Nacos.Group),
		zap.String("data_id", cfg.Nacos.DataID),
	)

	return nil
}

// parseNacosAddress 解析 Nacos 地址
func parseNacosAddress(addr string) string {
	// 简单解析，实际可能需要更复杂的逻辑
	// 格式：host:port
	if addr == "" {
		return "127.0.0.1"
	}

	// 查找冒号位置
	for i := len(addr) - 1; i >= 0; i-- {
		if addr[i] == ':' {
			return addr[:i]
		}
	}

	return addr // 没有端口，返回整个字符串作为地址
}

// parseNacosPort 解析 Nacos 端口
func parseNacosPort(addr string) uint64 {
	// 查找冒号位置
	for i := len(addr) - 1; i >= 0; i-- {
		if addr[i] == ':' {
			portStr := addr[i+1:]
			// 简单转换，实际应该更严格
			if portStr == "8848" {
				return 8848
			}
			// 其他端口处理...
			return 8848
		}
	}

	return 8848 // 默认端口
}

// loadInitialConfig 加载初始配置
func loadInitialConfig(cfg *config.Config) error {
	content, err := configClient.GetConfig(vo.ConfigParam{
		DataId: cfg.Nacos.DataID,
		Group:  cfg.Nacos.Group,
	})
	if err != nil {
		return fmt.Errorf("获取 Nacos 配置失败: %w", err)
	}

	if content == "" {
		logger.Info("Nacos 配置为空，使用默认配置")
		return nil
	}

	return updateConfigFromNacos(content)
}

// listenConfigChange 监听配置变化
func listenConfigChange(cfg *config.Config) error {
	return configClient.ListenConfig(vo.ConfigParam{
		DataId: cfg.Nacos.DataID,
		Group:  cfg.Nacos.Group,
		OnChange: func(namespace, group, dataId, data string) {
			logger.Info("Nacos 配置发生变化",
				zap.String("namespace", namespace),
				zap.String("group", group),
				zap.String("data_id", dataId),
			)

			if err := updateConfigFromNacos(data); err != nil {
				logger.Error("更新 Nacos 配置失败", zap.Error(err))
			} else {
				logger.Info("✅ Nacos 配置更新成功")
			}
		},
	})
}

// updateConfigFromNacos 从 Nacos 更新配置
func updateConfigFromNacos(content string) error {
	if appConfig == nil {
		return fmt.Errorf("应用配置未初始化")
	}

	// 替换配置中的变量占位符（如 ${global.host}）
	content = replaceVariables(content)

	// 解析 YAML 配置
	var nacosConfig NacosConfig
	if err := yaml.Unmarshal([]byte(content), &nacosConfig); err != nil {
		return fmt.Errorf("解析 Nacos 配置失败: %w", err)
	}

	// 更新应用配置（只更新支持动态修改的部分）
	updateDynamicConfig(&nacosConfig)

	logger.Info("✅ 配置已从 Nacos 更新",
		zap.Int("server_port", appConfig.Server.Port),
		zap.String("redis_address", appConfig.Redis.Address),
		zap.String("rocketmq_address", appConfig.RocketMQ.ServerAddr),
		zap.Int("netty_worker_threads", appConfig.Netty.WorkerThreads),
	)

	return nil
}

// updateDynamicConfig 更新动态配置
func updateDynamicConfig(nacosConfig *NacosConfig) {
	// ========== 更新 Server 配置 ==========
	if nacosConfig.Server.Port > 0 {
		appConfig.Server.Port = nacosConfig.Server.Port
		logger.Info("更新 WebSocket 端口", zap.Int("port", nacosConfig.Server.Port))
	}
	if nacosConfig.Server.PrometheusPort > 0 {
		appConfig.Server.PrometheusPort = nacosConfig.Server.PrometheusPort
	}
	if nacosConfig.Server.MaxConnections > 0 {
		appConfig.Server.MaxConnections = nacosConfig.Server.MaxConnections
	}

	// ========== 更新 Redis 配置 ==========
	if nacosConfig.Redis.Address != "" {
		appConfig.Redis.Address = nacosConfig.Redis.Address
		logger.Info("更新 Redis 地址", zap.String("address", nacosConfig.Redis.Address))
	}
	if nacosConfig.Redis.Password != "" {
		appConfig.Redis.Password = nacosConfig.Redis.Password
	}
	if nacosConfig.Redis.DB >= 0 {
		appConfig.Redis.DB = nacosConfig.Redis.DB
	}
	if nacosConfig.Redis.PoolSize > 0 {
		appConfig.Redis.PoolSize = nacosConfig.Redis.PoolSize
	}
	if nacosConfig.Redis.MinIdleConns >= 0 {
		appConfig.Redis.MinIdleConns = nacosConfig.Redis.MinIdleConns
	}
	if nacosConfig.Redis.DialTimeout > 0 {
		appConfig.Redis.DialTimeout = nacosConfig.Redis.DialTimeout
	}
	if nacosConfig.Redis.ReadTimeout > 0 {
		appConfig.Redis.ReadTimeout = nacosConfig.Redis.ReadTimeout
	}
	if nacosConfig.Redis.WriteTimeout > 0 {
		appConfig.Redis.WriteTimeout = nacosConfig.Redis.WriteTimeout
	}

	// ========== 更新 RocketMQ 配置 ==========
	if nacosConfig.RocketMQ.ServerAddr != "" {
		appConfig.RocketMQ.ServerAddr = nacosConfig.RocketMQ.ServerAddr
		logger.Info("更新 RocketMQ 地址", zap.String("address", nacosConfig.RocketMQ.ServerAddr))
	}
	if nacosConfig.RocketMQ.Producer.GroupName != "" {
		appConfig.RocketMQ.Producer.GroupName = nacosConfig.RocketMQ.Producer.GroupName
	}
	if nacosConfig.RocketMQ.Producer.MaxMessageSize > 0 {
		appConfig.RocketMQ.Producer.MaxMessageSize = nacosConfig.RocketMQ.Producer.MaxMessageSize
	}
	if nacosConfig.RocketMQ.Producer.SendTimeout > 0 {
		appConfig.RocketMQ.Producer.SendTimeout = nacosConfig.RocketMQ.Producer.SendTimeout
	}
	if nacosConfig.RocketMQ.Producer.RetryTimes > 0 {
		appConfig.RocketMQ.Producer.RetryTimes = nacosConfig.RocketMQ.Producer.RetryTimes
	}

	// ========== 更新 Netty 配置（支持运行时动态调整）==========
	if nacosConfig.Netty.BossThreads > 0 {
		appConfig.Netty.BossThreads = nacosConfig.Netty.BossThreads
	}
	if nacosConfig.Netty.WorkerThreads > 0 {
		appConfig.Netty.WorkerThreads = nacosConfig.Netty.WorkerThreads
	}
	if nacosConfig.Netty.SoBackLog > 0 {
		appConfig.Netty.SoBackLog = nacosConfig.Netty.SoBackLog
	}
	if nacosConfig.Netty.SocketBufferSize > 0 {
		appConfig.Netty.SocketBufferSize = nacosConfig.Netty.SocketBufferSize
	}
	if nacosConfig.Netty.WriteBufferLowWaterMark > 0 {
		appConfig.Netty.WriteBufferLowWaterMark = nacosConfig.Netty.WriteBufferLowWaterMark
	}
	if nacosConfig.Netty.WriteBufferHighWaterMark > 0 {
		appConfig.Netty.WriteBufferHighWaterMark = nacosConfig.Netty.WriteBufferHighWaterMark
	}
	if nacosConfig.Netty.PingInterval > 0 {
		// 配置单位为秒，转换为 time.Duration（对标 Java activeHeartbeatInterval）
		appConfig.Netty.PingInterval = time.Duration(nacosConfig.Netty.PingInterval) * time.Second
	}
	if nacosConfig.Netty.PongTimeout > 0 {
		// 配置单位为秒，转换为 time.Duration
		appConfig.Netty.PongTimeout = time.Duration(nacosConfig.Netty.PongTimeout) * time.Second
	}
	if nacosConfig.Netty.MaxMessageSize > 0 {
		appConfig.Netty.MaxMessageSize = nacosConfig.Netty.MaxMessageSize
	}
	if nacosConfig.Netty.HeartbeatTimeout > 0 {
		appConfig.Netty.HeartbeatTimeout = nacosConfig.Netty.HeartbeatTimeout
	}
	if nacosConfig.Netty.MaxHeartbeatFailures > 0 {
		appConfig.Netty.MaxHeartbeatFailures = nacosConfig.Netty.MaxHeartbeatFailures
	}
	if nacosConfig.Netty.IdleStateCheckInterval > 0 {
		appConfig.Netty.IdleStateCheckInterval = nacosConfig.Netty.IdleStateCheckInterval
	}

	// ========== 更新认证配置 ==========
	// 注意：Enabled 默认值为 false，需要显式设置
	appConfig.Auth.Enabled = nacosConfig.Auth.Enabled
	appConfig.Auth.StressTestEnabled = nacosConfig.Auth.StressTestEnabled

	if nacosConfig.Auth.StressTestToken != "" {
		appConfig.Auth.StressTestToken = nacosConfig.Auth.StressTestToken
	}
	if nacosConfig.Auth.JWTSecret != "" {
		appConfig.Auth.JWTSecret = nacosConfig.Auth.JWTSecret
	}

	logger.Info("更新认证配置",
		zap.Bool("enabled", appConfig.Auth.Enabled),
		zap.Bool("stress_test_enabled", appConfig.Auth.StressTestEnabled),
	)

	// ========== 更新日志级别 ==========
	if nacosConfig.Logging.Level != "" {
		appConfig.Logging.Level = nacosConfig.Logging.Level
	}
}

// NacosConfig Nacos 动态配置结构体
type NacosConfig struct {
	// Server 配置
	Server struct {
		Port           int `yaml:"port"`            // WebSocket 端口
		PrometheusPort int `yaml:"prometheus_port"` // Prometheus 端口
		MaxConnections int `yaml:"max_connections"` // 最大连接数
	} `yaml:"server"`

	// Redis 配置
	Redis struct {
		Address      string        `yaml:"addr"`           // Redis 地址
		Password     string        `yaml:"password"`       // 密码
		DB           int           `yaml:"db"`             // 数据库索引
		PoolSize     int           `yaml:"pool_size"`      // 连接池大小
		MinIdleConns int           `yaml:"min_idle_conns"` // 最小空闲连接数
		DialTimeout  time.Duration `yaml:"dial_timeout"`   // 连接超时
		ReadTimeout  time.Duration `yaml:"read_timeout"`   // 读超时
		WriteTimeout time.Duration `yaml:"write_timeout"`  // 写超时
	} `yaml:"redis"`

	// RocketMQ 配置
	RocketMQ struct {
		ServerAddr string `yaml:"server_addr"` // MQ 服务器地址
		Producer   struct {
			GroupName      string `yaml:"group_name"`       // 生产者组名
			MaxMessageSize int    `yaml:"max_message_size"` // 最大消息大小
			SendTimeout    int    `yaml:"send_timeout"`     // 发送超时（秒）
			RetryTimes     int    `yaml:"retry_times"`      // 重试次数
		} `yaml:"producer"`
	} `yaml:"rocketmq"`

	// Netty 优化配置（支持动态调整）
	// 注意：所有时间配置统一为秒（对标 Java 版本）
	Netty struct {
		BossThreads              int   `yaml:"boss_threads"`                 // Boss 线程数
		WorkerThreads            int   `yaml:"worker_threads"`               // Worker 线程数
		SoBackLog                int   `yaml:"so_backlog"`                   // 连接队列大小
		SocketBufferSize         int   `yaml:"socket_buffer_size"`           // Socket 缓冲区大小
		WriteBufferLowWaterMark  int   `yaml:"write_buffer_low_water_mark"`  // 写缓冲区低水位
		WriteBufferHighWaterMark int   `yaml:"write_buffer_high_water_mark"` // 写缓冲区高水位
		EnableCompression        bool  `yaml:"enable_compression"`           // 启用压缩
		PingInterval             int   `yaml:"ping_interval"`                // 主动心跳间隔（秒，对标 Java activeHeartbeatInterval）
		PongTimeout              int   `yaml:"pong_timeout"`                 // Pong 超时（秒）
		MaxMessageSize           int64 `yaml:"max_message_size"`             // 最大消息大小（字节）
		HeartbeatTimeout         int   `yaml:"heartbeat_timeout"`            // 心跳超时（秒，对标 Java heartBeatTime）
		MaxHeartbeatFailures     int   `yaml:"max_heartbeat_failures"`       // 最大心跳失败次数
		IdleStateCheckInterval   int   `yaml:"idle_state_check_interval"`    // 空闲检测间隔（秒，对标 Java idleStateCheckInterval）
	} `yaml:"netty"`

	// 认证配置
	Auth struct {
		Enabled           bool   `yaml:"enabled"`             // 是否启用认证
		StressTestEnabled bool   `yaml:"stress_test_enabled"` // 启用压测后门
		StressTestToken   string `yaml:"stress_test_token"`   // 压测后门 Token
		JWTSecret         string `yaml:"jwt_secret"`          // JWT 密钥
	} `yaml:"auth"`

	// 日志配置
	Logging struct {
		Level string `yaml:"level"` // 日志级别
	} `yaml:"logging"`
}

// PublishConfig 发布配置到 Nacos
func PublishConfig(dataId, group string, content string) error {
	success, err := configClient.PublishConfig(vo.ConfigParam{
		DataId:  dataId,
		Group:   group,
		Content: content,
	})

	if err != nil {
		return fmt.Errorf("发布配置到 Nacos 失败: %w", err)
	}

	if !success {
		return fmt.Errorf("发布配置到 Nacos 失败: 返回 false")
	}

	logger.Info("✅ 配置已发布到 Nacos",
		zap.String("data_id", dataId),
		zap.String("group", group),
	)

	return nil
}

// GetConfig 获取配置
func GetConfig(dataId, group string) (string, error) {
	return configClient.GetConfig(vo.ConfigParam{
		DataId: dataId,
		Group:  group,
	})
}

// DeleteConfig 删除配置
func DeleteConfig(dataId, group string) error {
	success, err := configClient.DeleteConfig(vo.ConfigParam{
		DataId: dataId,
		Group:  group,
	})

	if err != nil {
		return fmt.Errorf("删除 Nacos 配置失败: %w", err)
	}

	if !success {
		return fmt.Errorf("删除 Nacos 配置失败: 返回 false")
	}

	logger.Info("✅ 配置已从 Nacos 删除",
		zap.String("data_id", dataId),
		zap.String("group", group),
	)

	return nil
}

// GetDefaultConfig 获取默认的 Nacos 配置内容（用于初始化）
func GetDefaultConfig() string {
	defaultConfig := `# IM-Connect-Go 动态配置
# 支持运行时热更新，无需重启服务

# Netty 优化配置（百万连接 + 高QPS）
netty:
  # 线程池配置
  boss_threads: 2              # Boss 线程数（接受连接）
  worker_threads: 16           # Worker 线程数（处理IO）
  
  # TCP 配置  
  so_backlog: 65535           # 连接队列大小，建议 65535
  
  # 缓冲区配置（每连接内存：buffer_size * 2）
  socket_buffer_size: 16384   # Socket 缓冲区大小（16KB）
  write_buffer_low_water_mark: 8192   # 写缓冲区低水位（8KB）
  write_buffer_high_water_mark: 32768 # 写缓冲区高水位（32KB）
  
  # WebSocket 优化
  enable_compression: false    # 启用压缩（CPU vs 带宽权衡）
  ping_interval: 30000        # 心跳间隔（毫秒，30秒）
  pong_timeout: 60000         # Pong 超时（毫秒，60秒）
  max_message_size: 10240     # 最大消息大小（10KB）

# 认证配置
auth:
  stress_test_enabled: true                    # 启用压测后门
  stress_test_token: "STRESS_TEST_BYPASS_TOKEN" # 压测后门 Token

# 日志配置
logging:
  level: "info"               # 日志级别：debug, info, warn, error

# 性能调优说明：
# 1. 百万连接场景：
#    - boss_threads: 1-2 个足够
#    - worker_threads: CPU 核心数 * 2
#    - socket_buffer_size: 16KB（内存占用：100万连接 * 16KB * 2 = 32GB）
#
# 2. 高QPS场景：
#    - worker_threads: 可适当增加到 CPU 核心数 * 4
#    - enable_compression: false（减少CPU开销）
#    - write_buffer_high_water_mark: 增大到64KB
#
# 3. 内存优化：
#    - socket_buffer_size: 降到8KB（内存占用减半）
#    - max_message_size: 根据业务调整
`
	return defaultConfig
}

// CreateDefaultConfigIfNotExists 如果配置不存在，创建默认配置
func CreateDefaultConfigIfNotExists(dataId, group string) error {
	// 检查配置是否存在
	content, err := GetConfig(dataId, group)
	if err != nil {
		return fmt.Errorf("检查 Nacos 配置失败: %w", err)
	}

	// 如果配置不存在，创建默认配置
	if content == "" {
		defaultConfig := GetDefaultConfig()
		if err := PublishConfig(dataId, group, defaultConfig); err != nil {
			return fmt.Errorf("创建默认 Nacos 配置失败: %w", err)
		}

		logger.Info("✅ 已创建默认 Nacos 配置",
			zap.String("data_id", dataId),
			zap.String("group", group),
		)
	} else {
		logger.Info("Nacos 配置已存在，跳过创建",
			zap.String("data_id", dataId),
			zap.String("group", group),
		)
	}

	return nil
}

// GetCurrentConfig 获取当前生效的配置
func GetCurrentConfig() *config.Config {
	return appConfig
}

// ValidateConfig 验证配置
func ValidateConfig(content string) error {
	var nacosConfig NacosConfig
	if err := yaml.Unmarshal([]byte(content), &nacosConfig); err != nil {
		return fmt.Errorf("配置格式错误: %w", err)
	}

	// 验证 Netty 配置
	if nacosConfig.Netty.BossThreads < 0 || nacosConfig.Netty.BossThreads > 32 {
		return fmt.Errorf("无效的 boss_threads: %d，范围应在 0-32", nacosConfig.Netty.BossThreads)
	}

	if nacosConfig.Netty.WorkerThreads < 0 || nacosConfig.Netty.WorkerThreads > 256 {
		return fmt.Errorf("无效的 worker_threads: %d，范围应在 0-256", nacosConfig.Netty.WorkerThreads)
	}

	if nacosConfig.Netty.SoBackLog < 1024 {
		return fmt.Errorf("so_backlog 太小: %d，建议至少 1024", nacosConfig.Netty.SoBackLog)
	}

	if nacosConfig.Netty.SocketBufferSize < 1024 || nacosConfig.Netty.SocketBufferSize > 1024*1024 {
		return fmt.Errorf("无效的 socket_buffer_size: %d，范围应在 1KB-1MB", nacosConfig.Netty.SocketBufferSize)
	}

	// 验证心跳配置（单位：秒）
	if nacosConfig.Netty.PingInterval <= 0 || nacosConfig.Netty.PingInterval > 300 { // 最大5分钟
		return fmt.Errorf("无效的 ping_interval: %d，范围应在 1-300秒", nacosConfig.Netty.PingInterval)
	}

	if nacosConfig.Netty.PongTimeout <= 0 {
		return fmt.Errorf("无效的 pong_timeout: %d，必须大于0", nacosConfig.Netty.PongTimeout)
	}

	// 验证 idleStateCheckInterval < heartbeatTimeout（对标 Java 配置验证）
	if nacosConfig.Netty.IdleStateCheckInterval > 0 && nacosConfig.Netty.HeartbeatTimeout > 0 {
		if nacosConfig.Netty.IdleStateCheckInterval >= nacosConfig.Netty.HeartbeatTimeout {
			return fmt.Errorf("idle_state_check_interval (%d秒) 必须 < heartbeat_timeout (%d秒)",
				nacosConfig.Netty.IdleStateCheckInterval, nacosConfig.Netty.HeartbeatTimeout)
		}
	}

	// 验证日志级别
	validLogLevels := []string{"debug", "info", "warn", "error"}
	validLevel := false
	for _, level := range validLogLevels {
		if nacosConfig.Logging.Level == level {
			validLevel = true
			break
		}
	}
	if !validLevel && nacosConfig.Logging.Level != "" {
		return fmt.Errorf("无效的日志级别: %s，支持的级别: debug, info, warn, error", nacosConfig.Logging.Level)
	}

	return nil
}

// ReloadConfig 手动重新加载配置
func ReloadConfig() error {
	if appConfig == nil {
		return fmt.Errorf("应用配置未初始化")
	}

	return loadInitialConfig(appConfig)
}

// loadSharedConfigs 加载共享配置（类似 Java shared-configs）
// 共享配置优先级低于服务配置，先加载共享配置，解析变量
func loadSharedConfigs(cfg *config.Config) error {
	// 共享配置列表（类似 Java shared-configs）
	sharedConfigs := []struct {
		DataID string
		Group  string
	}{
		{DataID: "global.yaml", Group: "DEFAULT_GROUP"},     // 全局配置
		{DataID: "log-config.yaml", Group: cfg.Nacos.Group}, // 日志配置
	}

	for _, shared := range sharedConfigs {
		content, err := configClient.GetConfig(vo.ConfigParam{
			DataId: shared.DataID,
			Group:  shared.Group,
		})

		if err != nil {
			logger.Warn("加载共享配置失败",
				zap.String("data_id", shared.DataID),
				zap.String("group", shared.Group),
				zap.Error(err),
			)
			continue
		}

		if content == "" {
			logger.Info("共享配置为空，跳过",
				zap.String("data_id", shared.DataID),
				zap.String("group", shared.Group),
			)
			continue
		}

		// 解析共享配置中的变量
		parseSharedVariables(content)

		logger.Info("✅ 加载共享配置成功",
			zap.String("data_id", shared.DataID),
			zap.String("group", shared.Group),
			zap.Int("variables_count", len(sharedVars)),
		)
	}

	return nil
}

// parseSharedVariables 解析共享配置中的变量
// 例如：global.host: 120.46.85.43
func parseSharedVariables(content string) {
	var data map[string]interface{}
	if err := yaml.Unmarshal([]byte(content), &data); err != nil {
		logger.Warn("解析共享配置变量失败", zap.Error(err))
		return
	}

	// 递归解析变量
	extractVariables("", data)
}

// extractVariables 递归提取变量
func extractVariables(prefix string, data map[string]interface{}) {
	for key, value := range data {
		fullKey := key
		if prefix != "" {
			fullKey = prefix + "." + key
		}

		switch v := value.(type) {
		case map[string]interface{}:
			extractVariables(fullKey, v)
		case string:
			sharedVars[fullKey] = v
			logger.Debug("提取共享变量",
				zap.String("key", fullKey),
				zap.String("value", v),
			)
		case int, int64, float64, bool:
			sharedVars[fullKey] = fmt.Sprintf("%v", v)
		}
	}
}

// replaceVariables 替换配置中的变量占位符
// 例如：${global.host} -> 120.46.85.43
func replaceVariables(content string) string {
	result := content

	// 正则匹配 ${variable.name}
	re := regexp.MustCompile(`\$\{([^}]+)\}`)
	matches := re.FindAllStringSubmatch(content, -1)

	for _, match := range matches {
		if len(match) < 2 {
			continue
		}

		placeholder := match[0] // ${global.host}
		varName := match[1]     // global.host

		// 从共享变量中查找
		if value, exists := sharedVars[varName]; exists {
			result = strings.ReplaceAll(result, placeholder, value)
			logger.Debug("替换配置变量",
				zap.String("placeholder", placeholder),
				zap.String("value", value),
			)
		} else {
			logger.Warn("未找到共享变量",
				zap.String("placeholder", placeholder),
				zap.String("var_name", varName),
			)
		}
	}

	return result
}
