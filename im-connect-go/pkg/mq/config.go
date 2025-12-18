package mq

import "time"

// Config RocketMQ 配置（对应 Java 版本的配置）
type Config struct {
	// 服务器地址（支持多个，分号分隔）
	// 例如：192.168.1.100:9876;192.168.1.101:9876
	ServerAddr string `yaml:"server_addr"`

	// 生产者配置
	Producer ProducerConfig `yaml:"producer"`

	// 消费者配置（可选）
	Consumer ConsumerConfig `yaml:"consumer"`
}

// ProducerConfig 生产者配置
type ProducerConfig struct {
	// 生产者组名（对应 Java 的 producerGroupName）
	GroupName string `yaml:"group_name"`

	// 最大消息大小（字节）
	MaxMessageSize int `yaml:"max_message_size"`

	// 发送超时时间（秒）
	SendTimeout int `yaml:"send_timeout"`

	// 发送失败重试次数
	RetryTimes int `yaml:"retry_times"`
}

// ConsumerConfig 消费者配置
type ConsumerConfig struct {
	// 消费者组名
	GroupName string `yaml:"group_name"`

	// 消费线程数
	ThreadMin int `yaml:"thread_min"`
	ThreadMax int `yaml:"thread_max"`

	// 最大重试消费次数
	MaxReconsumeTimes int `yaml:"max_reconsume_times"`

	// 批量消费数量
	BatchSize int `yaml:"batch_size"`

	// 消费超时时间（分钟）
	ConsumeTimeout time.Duration `yaml:"consume_timeout"`
}

// DefaultConfig 默认配置
func DefaultConfig() *Config {
	return &Config{
		ServerAddr: "localhost:9876",
		Producer: ProducerConfig{
			GroupName:      "ImConnectGoProducerGroup",
			MaxMessageSize: 4096,
			SendTimeout:    10,
			RetryTimes:     3,
		},
		Consumer: ConsumerConfig{
			GroupName:         "ImConnectGoConsumer",
			ThreadMin:         10,
			ThreadMax:         20,
			MaxReconsumeTimes: 5,
			BatchSize:         1,
			ConsumeTimeout:    15 * time.Minute,
		},
	}
}
