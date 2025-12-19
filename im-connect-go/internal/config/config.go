package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
	"time"

	"gopkg.in/yaml.v3"
)

// Config 应用配置结构体，对标 Java 版本的 IMConnectServerConfig
// 支持环境变量和 Nacos 动态配置
type Config struct {
	// 服务器基础配置
	Server ServerConfig `yaml:"server" json:"server"`

	// Netty 优化配置（对标 Java 版本）
	Netty NettyConfig `yaml:"netty" json:"netty"`

	// 安全配置（对标 Java im.netty.security）
	Security SecurityConfig `yaml:"security" json:"security"`

	// 流量控制配置（对标 Java im.netty.flow-control）
	FlowControl FlowControlConfig `yaml:"flow_control" json:"flow_control"`

	// 重试配置（对标 Java im-server.c2c.retry）
	Retry RetryConfig `yaml:"retry" json:"retry"`

	// gRPC 配置
	GRPC GRPCConfig `yaml:"grpc" json:"grpc"`

	// Redis 配置
	Redis RedisConfig `yaml:"redis" json:"redis"`

	// RocketMQ 配置（对标 Java im.rocket）
	RocketMQ RocketMQConfig `yaml:"rocketmq" json:"rocketmq"`

	// Nacos 配置
	Nacos NacosConfig `yaml:"nacos" json:"nacos"`

	// 认证配置
	Auth AuthConfig `yaml:"auth" json:"auth"`

	// 日志配置
	Logging LoggingConfig `yaml:"logging" json:"logging"`

	// 应用信息
	App AppConfig `yaml:"app" json:"app"`
}

// ServerConfig 服务器配置
type ServerConfig struct {
	Host            string        `yaml:"host" json:"host"`                         // 绑定主机
	Port            int           `yaml:"port" json:"port"`                         // WebSocket 端口
	PrometheusPort  int           `yaml:"prometheus_port" json:"prometheus_port"`   // Prometheus 监控端口
	ReadTimeout     time.Duration `yaml:"read_timeout" json:"read_timeout"`         // 读取超时
	WriteTimeout    time.Duration `yaml:"write_timeout" json:"write_timeout"`       // 写入超时
	MaxConnections  int           `yaml:"max_connections" json:"max_connections"`   // 最大连接数
	GracefulTimeout time.Duration `yaml:"graceful_timeout" json:"graceful_timeout"` // 优雅关闭超时
}

// NettyConfig Netty 优化配置（对标 Java 版本的 IMConnectServerConfig）
// 所有参数均可通过 Nacos 动态调整，实现百万连接 + 高QPS 优化
type NettyConfig struct {
	// 线程池配置（对标 Java Netty EventLoopGroup）
	BossThreads   int `yaml:"boss_threads" json:"boss_threads"`     // Boss 线程数（接受连接）
	WorkerThreads int `yaml:"worker_threads" json:"worker_threads"` // Worker 线程数（处理IO）

	// TCP 配置（对标 Java SO_BACKLOG）
	SoBackLog int `yaml:"so_backlog" json:"so_backlog"` // 连接队列大小，建议 65535

	// 缓冲区配置（对标 Java SO_RCVBUF/SO_SNDBUF）
	SocketBufferSize int `yaml:"socket_buffer_size" json:"socket_buffer_size"` // Socket 缓冲区大小（字节）

	// 写缓冲区水位线（对标 Java WRITE_BUFFER_WATER_MARK）
	WriteBufferLowWaterMark  int `yaml:"write_buffer_low_water_mark" json:"write_buffer_low_water_mark"`   // 写缓冲区低水位
	WriteBufferHighWaterMark int `yaml:"write_buffer_high_water_mark" json:"write_buffer_high_water_mark"` // 写缓冲区高水位

	// WebSocket 优化
	EnableCompression bool          `yaml:"enable_compression" json:"enable_compression"` // 启用压缩
	PingInterval      time.Duration `yaml:"ping_interval" json:"ping_interval"`           // 心跳间隔（对应 Java activeHeartbeatInterval）
	PongTimeout       time.Duration `yaml:"pong_timeout" json:"pong_timeout"`             // Pong 超时
	MaxMessageSize    int64         `yaml:"max_message_size" json:"max_message_size"`     // 最大消息大小

	// 心跳配置（对标 Java heartBeatTime 等）
	HeartbeatTimeout       int `yaml:"heartbeat_timeout" json:"heartbeat_timeout"`                 // 心跳超时时间（秒）
	MaxHeartbeatFailures   int `yaml:"max_heartbeat_failures" json:"max_heartbeat_failures"`       // 最大心跳失败次数
	IdleStateCheckInterval int `yaml:"idle_state_check_interval" json:"idle_state_check_interval"` // 空闲检测间隔（秒）
}

// SecurityConfig 安全配置（对标 Java im.netty.security）
type SecurityConfig struct {
	MaxConnectionsPerIP     int `yaml:"max_connections_per_ip" json:"max_connections_per_ip"`         // 单IP最大连接数
	MaxTotalConnections     int `yaml:"max_total_connections" json:"max_total_connections"`           // 全局最大连接数
	MaxConnectionsPerMinute int `yaml:"max_connections_per_minute" json:"max_connections_per_minute"` // 单IP每分钟最大新连接数
}

// FlowControlConfig 流量控制配置（对标 Java im.netty.flow-control）
type FlowControlConfig struct {
	MaxMessagesPerSecond int   `yaml:"max_messages_per_second" json:"max_messages_per_second"` // 单用户每秒最大消息数
	MaxMessageSize       int64 `yaml:"max_message_size" json:"max_message_size"`               // 单条消息最大字节数
	MaxBytesPerSecond    int64 `yaml:"max_bytes_per_second" json:"max_bytes_per_second"`       // 单用户每秒最大字节数
}

// RetryConfig 重试配置（对标 Java im-server.c2c.retry）
type RetryConfig struct {
	Enabled      bool  `yaml:"enabled" json:"enabled"`             // 是否启用重试
	MaxRetries   int   `yaml:"max_retries" json:"max_retries"`     // 最大重试次数
	Delays       []int `yaml:"delays" json:"delays"`               // 重试延迟（秒）
	BatchSize    int   `yaml:"batch_size" json:"batch_size"`       // 批处理大小
	ScanInterval int   `yaml:"scan_interval" json:"scan_interval"` // 扫描间隔（毫秒）
}

// GRPCConfig gRPC 配置
type GRPCConfig struct {
	Port                  int           `yaml:"port" json:"port"`                                         // gRPC 端口
	MaxRecvMsgSize        int           `yaml:"max_recv_msg_size" json:"max_recv_msg_size"`               // 最大接收消息大小
	MaxSendMsgSize        int           `yaml:"max_send_msg_size" json:"max_send_msg_size"`               // 最大发送消息大小
	ConnectionTimeout     time.Duration `yaml:"connection_timeout" json:"connection_timeout"`             // 连接超时
	MaxConnectionIdle     time.Duration `yaml:"max_connection_idle" json:"max_connection_idle"`           // 最大空闲时间
	MaxConnectionAge      time.Duration `yaml:"max_connection_age" json:"max_connection_age"`             // 最大连接时间
	MaxConnectionAgeGrace time.Duration `yaml:"max_connection_age_grace" json:"max_connection_age_grace"` // 优雅关闭时间
}

// RedisConfig Redis 配置
type RedisConfig struct {
	Address      string        `yaml:"address" json:"address"`               // Redis 地址
	Password     string        `yaml:"password" json:"password"`             // 密码
	DB           int           `yaml:"db" json:"db"`                         // 数据库
	MaxRetries   int           `yaml:"max_retries" json:"max_retries"`       // 最大重试次数
	PoolSize     int           `yaml:"pool_size" json:"pool_size"`           // 连接池大小
	MinIdleConns int           `yaml:"min_idle_conns" json:"min_idle_conns"` // 最小空闲连接
	DialTimeout  time.Duration `yaml:"dial_timeout" json:"dial_timeout"`     // 连接超时
	ReadTimeout  time.Duration `yaml:"read_timeout" json:"read_timeout"`     // 读取超时
	WriteTimeout time.Duration `yaml:"write_timeout" json:"write_timeout"`   // 写入超时
}

// NacosConfig Nacos 配置
type NacosConfig struct {
	ServerAddr  string `yaml:"server_addr" json:"server_addr"`   // Nacos 服务器地址
	Namespace   string `yaml:"namespace" json:"namespace"`       // 命名空间
	Group       string `yaml:"group" json:"group"`               // 配置组
	DataID      string `yaml:"data_id" json:"data_id"`           // 配置 ID
	Username    string `yaml:"username" json:"username"`         // 用户名
	Password    string `yaml:"password" json:"password"`         // 密码
	ContextPath string `yaml:"context_path" json:"context_path"` // 上下文路径
	Timeout     int64  `yaml:"timeout" json:"timeout"`           // 超时时间
}

// AuthConfig 认证配置（对标 Java im.netty.auth）
type AuthConfig struct {
	// 是否启用认证（对应 Java enabled）
	Enabled bool `yaml:"enabled" json:"enabled"`
	// 是否检查Token过期时间（对应 Java token-expire-check）
	TokenExpireCheck bool `yaml:"token_expire_check" json:"token_expire_check"`
	// 认证失败最大次数（对应 Java max-auth-failures）
	MaxAuthFailures int `yaml:"max_auth_failures" json:"max_auth_failures"`
	// IP锁定时长（分钟）（对应 Java lockout-duration-minutes）
	LockoutDurationMinutes int `yaml:"lockout_duration_minutes" json:"lockout_duration_minutes"`
	// JWT 密钥
	JWTSecret string `yaml:"jwt_secret" json:"jwt_secret"`
	// Token 过期时间
	TokenExpiry time.Duration `yaml:"token_expiry" json:"token_expiry"`
	// 启用压测后门（对标 Java im.netty.auth.stress-test-enabled）
	StressTestEnabled bool `yaml:"stress_test_enabled" json:"stress_test_enabled"`
	// 压测后门 Token
	StressTestToken string `yaml:"stress_test_token" json:"stress_test_token"`
}

// RocketMQConfig RocketMQ 配置（对标 Java im.rocket）
type RocketMQConfig struct {
	// MQ 服务器地址（对应 Java serverAddr）
	// 支持多个，分号分隔：192.168.1.100:9876;192.168.1.101:9876
	ServerAddr string `yaml:"server_addr" json:"server_addr"`

	// 生产者配置
	Producer RocketMQProducerConfig `yaml:"producer" json:"producer"`

	// 消费者配置
	Consumer RocketMQConsumerConfig `yaml:"consumer" json:"consumer"`
}

// RocketMQProducerConfig 生产者配置
type RocketMQProducerConfig struct {
	// 生产者组名（对应 Java producerGroupName）
	GroupName string `yaml:"group_name" json:"group_name"`

	// 最大消息大小（字节，对应 Java maxMessageSize）
	MaxMessageSize int `yaml:"max_message_size" json:"max_message_size"`

	// 发送超时时间（秒，对应 Java sendMsgTimeout）
	SendTimeout int `yaml:"send_timeout" json:"send_timeout"`

	// 发送失败重试次数（对应 Java retryTimesWhenSendFailed）
	RetryTimes int `yaml:"retry_times" json:"retry_times"`
}

// RocketMQConsumerConfig 消费者配置
type RocketMQConsumerConfig struct {
	// 消费者组名（对应 Java consumerGroupName）
	GroupName string `yaml:"group_name" json:"group_name"`

	// 消费线程配置（对应 Java consumeThreadMin/Max）
	ThreadMin int `yaml:"thread_min" json:"thread_min"`
	ThreadMax int `yaml:"thread_max" json:"thread_max"`

	// 最大重试消费次数（对应 Java maxReconsumeTimes）
	MaxReconsumeTimes int `yaml:"max_reconsume_times" json:"max_reconsume_times"`

	// 批量消费数量（对应 Java consumeMessageBatchMaxSize）
	BatchSize int `yaml:"batch_size" json:"batch_size"`

	// 消费超时时间（分钟，对应 Java consumeTimeout）
	ConsumeTimeout int `yaml:"consume_timeout" json:"consume_timeout"`
}

// LoggingConfig 日志配置
type LoggingConfig struct {
	Level      string `yaml:"level" json:"level"`             // 日志级别
	Format     string `yaml:"format" json:"format"`           // 日志格式
	Output     string `yaml:"output" json:"output"`           // 输出路径
	MaxSize    int    `yaml:"max_size" json:"max_size"`       // 单文件最大大小（MB）
	MaxBackups int    `yaml:"max_backups" json:"max_backups"` // 最大备份文件数
	MaxAge     int    `yaml:"max_age" json:"max_age"`         // 最大保留天数
	Compress   bool   `yaml:"compress" json:"compress"`       // 是否压缩
}

// AppConfig 应用信息
type AppConfig struct {
	Name        string `yaml:"name" json:"name"`               // 应用名称
	Version     string `yaml:"version" json:"version"`         // 版本号
	Environment string `yaml:"environment" json:"environment"` // 环境
	MachineID   int    `yaml:"machine_id" json:"machine_id"`   // 机器 ID（雪花算法用）
}

// LoadOptions 加载配置的选项
type LoadOptions struct {
	ConfigFile string // 配置文件路径
	Env        string // 运行环境 (dev/test/pre/prod)
	Namespace  string // Nacos 命名空间（覆盖配置文件）
}

// LoadConfig 加载配置
// 优先级：环境变量 > 配置文件 > 默认值
// 后续通过 Nacos 动态更新配置
func LoadConfig() (*Config, error) {
	return LoadConfigWithOptions(nil)
}

// LoadConfigWithOptions 使用选项加载配置
// 优先级：命令行参数 > 环境变量 > 配置文件 > 默认值
func LoadConfigWithOptions(opts *LoadOptions) (*Config, error) {
	if opts == nil {
		opts = &LoadOptions{}
	}

	// 1. 确定配置文件路径
	configPath := determineConfigPath(opts)

	// 2. 从配置文件加载
	var config *Config
	if configPath != "" {
		config = loadConfigFromFileWithPath(configPath)
		if config == nil {
			return nil, fmt.Errorf("无法加载配置文件: %s", configPath)
		}
	} else {
		// 尝试默认路径
		config = loadConfigFromFile()
		if config == nil {
			// 使用环境变量和默认值
			var err error
			config, err = loadConfigFromEnv()
			if err != nil {
				return nil, err
			}
		}
	}

	// 3. 命令行参数覆盖配置
	if opts.Namespace != "" {
		config.Nacos.Namespace = opts.Namespace
	}

	// 4. 用环境变量覆盖
	mergeEnvConfig(config)

	return config, nil
}

// determineConfigPath 确定配置文件路径
func determineConfigPath(opts *LoadOptions) string {
	// 1. 优先使用 --config 参数
	if opts.ConfigFile != "" {
		return opts.ConfigFile
	}

	// 2. 如果指定了 --env，自动查找对应环境的配置文件
	if opts.Env != "" {
		envConfigPath := fmt.Sprintf("configs/bootstrap-%s.yaml", opts.Env)
		if _, err := os.Stat(envConfigPath); err == nil {
			return envConfigPath
		}
		// 如果环境配置文件不存在，打印警告但继续
		fmt.Printf("⚠️  警告: 环境配置文件不存在: %s，将尝试使用默认配置\n", envConfigPath)
	}

	return ""
}

// loadConfigFromFile 从配置文件加载
func loadConfigFromFile() *Config {
	// 尝试多个配置文件路径
	configPaths := []string{
		"configs/bootstrap.yaml",
		"bootstrap.yaml",
		"/etc/im-connect-go/bootstrap.yaml",
		filepath.Join(os.Getenv("HOME"), ".im-connect-go/bootstrap.yaml"),
	}

	for _, path := range configPaths {
		if data, err := os.ReadFile(path); err == nil {
			var config Config
			if err := yaml.Unmarshal(data, &config); err == nil {
				fmt.Printf("✅ 从配置文件加载: %s\n", path)
				// 用环境变量覆盖配置文件中的值
				mergeEnvConfig(&config)
				return &config
			}
		}
	}

	return nil
}

// loadConfigFromFileWithPath 从指定路径加载配置文件
func loadConfigFromFileWithPath(path string) *Config {
	data, err := os.ReadFile(path)
	if err != nil {
		fmt.Printf("❌ 读取配置文件失败: %s, 错误: %v\n", path, err)
		return nil
	}

	var config Config
	if err := yaml.Unmarshal(data, &config); err != nil {
		fmt.Printf("❌ 解析配置文件失败: %s, 错误: %v\n", path, err)
		return nil
	}

	fmt.Printf("✅ 从配置文件加载: %s\n", path)
	return &config
}

// mergeEnvConfig 用环境变量覆盖配置
func mergeEnvConfig(config *Config) {
	// Nacos 配置（环境变量优先）
	if v := os.Getenv("NACOS_SERVER_ADDR"); v != "" {
		config.Nacos.ServerAddr = v
	}
	if v := os.Getenv("NACOS_NAMESPACE"); v != "" {
		config.Nacos.Namespace = v
	}
	if v := os.Getenv("NACOS_GROUP"); v != "" {
		config.Nacos.Group = v
	}
	if v := os.Getenv("NACOS_DATA_ID"); v != "" {
		config.Nacos.DataID = v
	}
	if v := os.Getenv("NACOS_USERNAME"); v != "" {
		config.Nacos.Username = v
	}
	if v := os.Getenv("NACOS_PASSWORD"); v != "" {
		config.Nacos.Password = v
	}

	// Redis 配置
	if v := os.Getenv("REDIS_ADDRESS"); v != "" {
		config.Redis.Address = v
	}
	if v := os.Getenv("REDIS_PASSWORD"); v != "" {
		config.Redis.Password = v
	}

	// 服务器配置
	if v := os.Getenv("SERVER_PORT"); v != "" {
		if port, err := strconv.Atoi(v); err == nil {
			config.Server.Port = port
		}
	}
	if v := os.Getenv("GRPC_PORT"); v != "" {
		if port, err := strconv.Atoi(v); err == nil {
			config.GRPC.Port = port
		}
	}
}

// loadConfigFromEnv 从环境变量加载配置
func loadConfigFromEnv() (*Config, error) {
	config := &Config{
		Server: ServerConfig{
			Host:            getEnvOrDefault("SERVER_HOST", "0.0.0.0"),
			Port:            getEnvAsIntOrDefault("SERVER_PORT", 10001),
			ReadTimeout:     getEnvAsDurationOrDefault("SERVER_READ_TIMEOUT", 60*time.Second),
			WriteTimeout:    getEnvAsDurationOrDefault("SERVER_WRITE_TIMEOUT", 60*time.Second),
			MaxConnections:  getEnvAsIntOrDefault("SERVER_MAX_CONNECTIONS", 1000000),
			GracefulTimeout: getEnvAsDurationOrDefault("SERVER_GRACEFUL_TIMEOUT", 30*time.Second),
		},

		// Netty 配置（百万连接 + 高QPS 优化）
		Netty: NettyConfig{
			BossThreads:              getEnvAsIntOrDefault("NETTY_BOSS_THREADS", 0),                    // 0 = 自动计算
			WorkerThreads:            getEnvAsIntOrDefault("NETTY_WORKER_THREADS", 0),                  // 0 = 自动计算
			SoBackLog:                getEnvAsIntOrDefault("NETTY_SO_BACKLOG", 65535),                  // 连接队列大小
			SocketBufferSize:         getEnvAsIntOrDefault("NETTY_SOCKET_BUFFER_SIZE", 32*1024),        // 32KB 缓冲区
			WriteBufferLowWaterMark:  getEnvAsIntOrDefault("NETTY_WRITE_BUFFER_LOW", 32*1024),          // 32KB 低水位
			WriteBufferHighWaterMark: getEnvAsIntOrDefault("NETTY_WRITE_BUFFER_HIGH", 128*1024),        // 128KB 高水位
			EnableCompression:        getEnvAsBoolOrDefault("NETTY_ENABLE_COMPRESSION", false),         // WebSocket 压缩
			PingInterval:             getEnvAsDurationOrDefault("NETTY_PING_INTERVAL", 25*time.Second), // 心跳间隔（对应 activeHeartbeatInterval: 25）
			PongTimeout:              getEnvAsDurationOrDefault("NETTY_PONG_TIMEOUT", 10*time.Second),  // Pong 超时
			MaxMessageSize:           getEnvAsInt64OrDefault("NETTY_MAX_MESSAGE_SIZE", 8*1024),         // 8KB 消息限制
			HeartbeatTimeout:         getEnvAsIntOrDefault("NETTY_HEARTBEAT_TIMEOUT", 45),              // 心跳超时（对应 heartBeatTime: 45）
			MaxHeartbeatFailures:     getEnvAsIntOrDefault("NETTY_MAX_HEARTBEAT_FAILURES", 3),          // 最大心跳失败次数
			IdleStateCheckInterval:   getEnvAsIntOrDefault("NETTY_IDLE_CHECK_INTERVAL", 30),            // 空闲检测间隔
		},

		// 安全配置
		Security: SecurityConfig{
			MaxConnectionsPerIP:     getEnvAsIntOrDefault("SECURITY_MAX_CONN_PER_IP", 1000000),
			MaxTotalConnections:     getEnvAsIntOrDefault("SECURITY_MAX_TOTAL_CONN", 100000000),
			MaxConnectionsPerMinute: getEnvAsIntOrDefault("SECURITY_MAX_CONN_PER_MIN", 600000000),
		},

		// 流量控制配置
		FlowControl: FlowControlConfig{
			MaxMessagesPerSecond: getEnvAsIntOrDefault("FLOW_MAX_MSG_PER_SEC", 100000),
			MaxMessageSize:       getEnvAsInt64OrDefault("FLOW_MAX_MSG_SIZE", 8192000),
			MaxBytesPerSecond:    getEnvAsInt64OrDefault("FLOW_MAX_BYTES_PER_SEC", 102400000),
		},

		// 重试配置
		Retry: RetryConfig{
			Enabled:      getEnvAsBoolOrDefault("RETRY_ENABLED", true),
			MaxRetries:   getEnvAsIntOrDefault("RETRY_MAX_RETRIES", 3),
			Delays:       []int{2, 5, 20}, // 默认重试延迟
			BatchSize:    getEnvAsIntOrDefault("RETRY_BATCH_SIZE", 10000),
			ScanInterval: getEnvAsIntOrDefault("RETRY_SCAN_INTERVAL", 10000),
		},

		GRPC: GRPCConfig{
			Port:                  getEnvAsIntOrDefault("GRPC_PORT", 9090),
			MaxRecvMsgSize:        getEnvAsIntOrDefault("GRPC_MAX_RECV_MSG_SIZE", 4*1024*1024), // 4MB
			MaxSendMsgSize:        getEnvAsIntOrDefault("GRPC_MAX_SEND_MSG_SIZE", 4*1024*1024), // 4MB
			ConnectionTimeout:     getEnvAsDurationOrDefault("GRPC_CONNECTION_TIMEOUT", 10*time.Second),
			MaxConnectionIdle:     getEnvAsDurationOrDefault("GRPC_MAX_CONNECTION_IDLE", 30*time.Minute),
			MaxConnectionAge:      getEnvAsDurationOrDefault("GRPC_MAX_CONNECTION_AGE", 30*time.Minute),
			MaxConnectionAgeGrace: getEnvAsDurationOrDefault("GRPC_MAX_CONNECTION_AGE_GRACE", 5*time.Second),
		},

		Redis: RedisConfig{
			Address:      getEnvOrDefault("REDIS_ADDRESS", "localhost:6379"),
			Password:     getEnvOrDefault("REDIS_PASSWORD", ""),
			DB:           getEnvAsIntOrDefault("REDIS_DB", 0),
			MaxRetries:   getEnvAsIntOrDefault("REDIS_MAX_RETRIES", 3),
			PoolSize:     getEnvAsIntOrDefault("REDIS_POOL_SIZE", 100),
			MinIdleConns: getEnvAsIntOrDefault("REDIS_MIN_IDLE_CONNS", 20),
			DialTimeout:  getEnvAsDurationOrDefault("REDIS_DIAL_TIMEOUT", 5*time.Second),
			ReadTimeout:  getEnvAsDurationOrDefault("REDIS_READ_TIMEOUT", 3*time.Second),
			WriteTimeout: getEnvAsDurationOrDefault("REDIS_WRITE_TIMEOUT", 3*time.Second),
		},

		RocketMQ: RocketMQConfig{
			ServerAddr: getEnvOrDefault("ROCKETMQ_SERVER_ADDR", "localhost:9876"),
			Producer: RocketMQProducerConfig{
				GroupName:      getEnvOrDefault("ROCKETMQ_PRODUCER_GROUP", "ImConnectGoProducerGroup"),
				MaxMessageSize: getEnvAsIntOrDefault("ROCKETMQ_PRODUCER_MAX_MESSAGE_SIZE", 4096),
				SendTimeout:    getEnvAsIntOrDefault("ROCKETMQ_PRODUCER_SEND_TIMEOUT", 10),
				RetryTimes:     getEnvAsIntOrDefault("ROCKETMQ_PRODUCER_RETRY_TIMES", 3),
			},
			Consumer: RocketMQConsumerConfig{
				GroupName:         getEnvOrDefault("ROCKETMQ_CONSUMER_GROUP", "ImConnectGoConsumer"),
				ThreadMin:         getEnvAsIntOrDefault("ROCKETMQ_CONSUMER_THREAD_MIN", 10),
				ThreadMax:         getEnvAsIntOrDefault("ROCKETMQ_CONSUMER_THREAD_MAX", 20),
				MaxReconsumeTimes: getEnvAsIntOrDefault("ROCKETMQ_CONSUMER_MAX_RECONSUME_TIMES", 5),
				BatchSize:         getEnvAsIntOrDefault("ROCKETMQ_CONSUMER_BATCH_SIZE", 1),
				ConsumeTimeout:    getEnvAsIntOrDefault("ROCKETMQ_CONSUMER_CONSUME_TIMEOUT", 15),
			},
		},

		Nacos: NacosConfig{
			ServerAddr:  getEnvOrDefault("NACOS_SERVER_ADDR", "localhost:8848"),
			Namespace:   getEnvOrDefault("NACOS_NAMESPACE", "im"),
			Group:       getEnvOrDefault("NACOS_GROUP", "DEFAULT_GROUP"),
			DataID:      getEnvOrDefault("NACOS_DATA_ID", "im-connect-go.yaml"),
			Username:    getEnvOrDefault("NACOS_USERNAME", "nacos"),
			Password:    getEnvOrDefault("NACOS_PASSWORD", "nacos"),
			ContextPath: getEnvOrDefault("NACOS_CONTEXT_PATH", "/nacos"),
			Timeout:     getEnvAsInt64OrDefault("NACOS_TIMEOUT", 5000),
		},

		Auth: AuthConfig{
			Enabled:                getEnvAsBoolOrDefault("AUTH_ENABLED", false),            // 对应 Java auth.enabled: false
			TokenExpireCheck:       getEnvAsBoolOrDefault("AUTH_TOKEN_EXPIRE_CHECK", false), // 对应 Java token-expire-check: false
			MaxAuthFailures:        getEnvAsIntOrDefault("AUTH_MAX_FAILURES", 5),            // 对应 Java max-auth-failures: 5
			LockoutDurationMinutes: getEnvAsIntOrDefault("AUTH_LOCKOUT_DURATION", 30),       // 对应 Java lockout-duration-minutes: 30
			JWTSecret:              getEnvOrDefault("AUTH_JWT_SECRET", "your-jwt-secret-key"),
			TokenExpiry:            getEnvAsDurationOrDefault("AUTH_TOKEN_EXPIRY", 24*time.Hour),
			StressTestEnabled:      getEnvAsBoolOrDefault("AUTH_STRESS_TEST_ENABLED", true), // 默认启用压测后门
			StressTestToken:        getEnvOrDefault("AUTH_STRESS_TEST_TOKEN", "STRESS_TEST_BYPASS_TOKEN"),
		},

		Logging: LoggingConfig{
			Level:      getEnvOrDefault("LOG_LEVEL", "info"),
			Format:     getEnvOrDefault("LOG_FORMAT", "console"),
			Output:     getEnvOrDefault("LOG_OUTPUT", "logs/im-connect-go.log"),
			MaxSize:    getEnvAsIntOrDefault("LOG_MAX_SIZE", 100),  // 100MB
			MaxBackups: getEnvAsIntOrDefault("LOG_MAX_BACKUPS", 7), // 7个备份文件
			MaxAge:     getEnvAsIntOrDefault("LOG_MAX_AGE", 7),     // 保留7天
			Compress:   getEnvAsBoolOrDefault("LOG_COMPRESS", true),
		},

		App: AppConfig{
			Name:        getEnvOrDefault("APP_NAME", "im-connect-go"),
			Version:     getEnvOrDefault("APP_VERSION", "1.0.0"),
			Environment: getEnvOrDefault("APP_ENVIRONMENT", "development"),
			MachineID:   getEnvAsIntOrDefault("APP_MACHINE_ID", 1), // 雪花算法机器 ID
		},
	}

	return config, nil
}

// GetNettyRuntimeConfig 获取运行时 Netty 配置（自动计算线程数）
// 对标 Java 版本的 initEventLoopGroups 方法
func (c *Config) GetNettyRuntimeConfig() NettyConfig {
	config := c.Netty
	cpuCores := getEnvAsIntOrDefault("GOMAXPROCS", 0)
	if cpuCores == 0 {
		// 这里可以用 runtime.NumCPU() 或其他方式获取 CPU 核心数
		cpuCores = 4 // 默认值
	}

	// 自动计算线程数（配置为0时）
	if config.BossThreads <= 0 {
		config.BossThreads = max(1, cpuCores/4)
	}
	if config.WorkerThreads <= 0 {
		config.WorkerThreads = cpuCores * 2
	}

	return config
}

// GetRocketMQConfig 获取 RocketMQ 配置
func (c *Config) GetRocketMQConfig() RocketMQConfig {
	return c.RocketMQ
}

// 工具函数：环境变量获取

func getEnvOrDefault(key, defaultValue string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return defaultValue
}

func getEnvAsIntOrDefault(key string, defaultValue int) int {
	if value := os.Getenv(key); value != "" {
		if intValue, err := strconv.Atoi(value); err == nil {
			return intValue
		}
	}
	return defaultValue
}

func getEnvAsInt64OrDefault(key string, defaultValue int64) int64 {
	if value := os.Getenv(key); value != "" {
		if int64Value, err := strconv.ParseInt(value, 10, 64); err == nil {
			return int64Value
		}
	}
	return defaultValue
}

func getEnvAsBoolOrDefault(key string, defaultValue bool) bool {
	if value := os.Getenv(key); value != "" {
		if boolValue, err := strconv.ParseBool(value); err == nil {
			return boolValue
		}
	}
	return defaultValue
}

func getEnvAsDurationOrDefault(key string, defaultValue time.Duration) time.Duration {
	if value := os.Getenv(key); value != "" {
		if duration, err := time.ParseDuration(value); err == nil {
			return duration
		}
	}
	return defaultValue
}

func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}

// Validate 验证配置
func (c *Config) Validate() error {
	if c.Server.Port <= 0 || c.Server.Port > 65535 {
		return fmt.Errorf("无效的服务器端口: %d", c.Server.Port)
	}

	if c.GRPC.Port <= 0 || c.GRPC.Port > 65535 {
		return fmt.Errorf("无效的 gRPC 端口: %d", c.GRPC.Port)
	}

	if c.Server.Port == c.GRPC.Port {
		return fmt.Errorf("服务器端口和 gRPC 端口不能相同: %d", c.Server.Port)
	}

	if c.Netty.SoBackLog < 1024 {
		return fmt.Errorf("SO_BACKLOG 太小: %d，建议至少 1024", c.Netty.SoBackLog)
	}

	return nil
}
