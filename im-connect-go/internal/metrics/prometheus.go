package metrics

import (
	"fmt"
	"net/http"
	"sync"
	"time"

	"github.com/prometheus/client_golang/prometheus"
	"github.com/prometheus/client_golang/prometheus/promhttp"
	"go.uber.org/zap"
)

// Metrics Prometheus监控指标（对标 Java MetricsHandler/MetricsConfig）
type Metrics struct {
	logger *zap.Logger

	// 连接相关
	ActiveConnections  prometheus.Gauge     // 当前活跃连接数
	TotalConnections   prometheus.Counter   // 总连接数
	ConnectionDuration prometheus.Histogram // 连接持续时间

	// 消息相关
	MessagesReceived      *prometheus.CounterVec // 接收消息数（按类型）
	MessagesSent          *prometheus.CounterVec // 发送消息数（按类型）
	MessageProcessingTime prometheus.Histogram   // 消息处理时间
	MessageSize           prometheus.Histogram   // 消息大小

	// 心跳相关
	HeartbeatReceived prometheus.Counter // 收到的心跳数
	HeartbeatSent     prometheus.Counter // 发送的心跳数
	HeartbeatTimeout  prometheus.Counter // 心跳超时数

	// 错误相关
	Errors *prometheus.CounterVec // 错误数（按类型）

	// 系统相关
	GoroutineCount prometheus.Gauge // 协程数
	MemoryUsage    prometheus.Gauge // 内存使用

	// 注册状态
	registered bool
	mutex      sync.Mutex
}

var (
	globalMetrics *Metrics
	once          sync.Once
)

// GetMetrics 获取全局监控实例
func GetMetrics() *Metrics {
	once.Do(func() {
		globalMetrics = newMetrics()
	})
	return globalMetrics
}

// newMetrics 创建监控指标
func newMetrics() *Metrics {
	m := &Metrics{
		// 连接相关
		ActiveConnections: prometheus.NewGauge(prometheus.GaugeOpts{
			Namespace: "im_connect",
			Name:      "active_connections",
			Help:      "当前活跃的WebSocket连接数",
		}),
		TotalConnections: prometheus.NewCounter(prometheus.CounterOpts{
			Namespace: "im_connect",
			Name:      "total_connections",
			Help:      "总WebSocket连接数",
		}),
		ConnectionDuration: prometheus.NewHistogram(prometheus.HistogramOpts{
			Namespace: "im_connect",
			Name:      "connection_duration_seconds",
			Help:      "WebSocket连接持续时间分布",
			Buckets:   []float64{1, 5, 10, 30, 60, 300, 600, 1800, 3600},
		}),

		// 消息相关
		MessagesReceived: prometheus.NewCounterVec(prometheus.CounterOpts{
			Namespace: "im_connect",
			Name:      "messages_received_total",
			Help:      "接收的消息总数",
		}, []string{"type"}),
		MessagesSent: prometheus.NewCounterVec(prometheus.CounterOpts{
			Namespace: "im_connect",
			Name:      "messages_sent_total",
			Help:      "发送的消息总数",
		}, []string{"type"}),
		MessageProcessingTime: prometheus.NewHistogram(prometheus.HistogramOpts{
			Namespace: "im_connect",
			Name:      "message_processing_seconds",
			Help:      "消息处理时间分布",
			Buckets:   []float64{0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1},
		}),
		MessageSize: prometheus.NewHistogram(prometheus.HistogramOpts{
			Namespace: "im_connect",
			Name:      "message_size_bytes",
			Help:      "消息大小分布",
			Buckets:   []float64{64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384},
		}),

		// 心跳相关
		HeartbeatReceived: prometheus.NewCounter(prometheus.CounterOpts{
			Namespace: "im_connect",
			Name:      "heartbeat_received_total",
			Help:      "收到的心跳总数",
		}),
		HeartbeatSent: prometheus.NewCounter(prometheus.CounterOpts{
			Namespace: "im_connect",
			Name:      "heartbeat_sent_total",
			Help:      "发送的心跳总数",
		}),
		HeartbeatTimeout: prometheus.NewCounter(prometheus.CounterOpts{
			Namespace: "im_connect",
			Name:      "heartbeat_timeout_total",
			Help:      "心跳超时总数",
		}),

		// 错误相关
		Errors: prometheus.NewCounterVec(prometheus.CounterOpts{
			Namespace: "im_connect",
			Name:      "errors_total",
			Help:      "错误总数",
		}, []string{"type"}),

		// 系统相关
		GoroutineCount: prometheus.NewGauge(prometheus.GaugeOpts{
			Namespace: "im_connect",
			Name:      "goroutine_count",
			Help:      "当前协程数",
		}),
		MemoryUsage: prometheus.NewGauge(prometheus.GaugeOpts{
			Namespace: "im_connect",
			Name:      "memory_usage_bytes",
			Help:      "内存使用量（字节）",
		}),
	}

	return m
}

// Register 注册所有指标到Prometheus
func (m *Metrics) Register() error {
	m.mutex.Lock()
	defer m.mutex.Unlock()

	if m.registered {
		return nil
	}

	collectors := []prometheus.Collector{
		m.ActiveConnections,
		m.TotalConnections,
		m.ConnectionDuration,
		m.MessagesReceived,
		m.MessagesSent,
		m.MessageProcessingTime,
		m.MessageSize,
		m.HeartbeatReceived,
		m.HeartbeatSent,
		m.HeartbeatTimeout,
		m.Errors,
		m.GoroutineCount,
		m.MemoryUsage,
	}

	for _, c := range collectors {
		if err := prometheus.Register(c); err != nil {
			// 如果已经注册过，忽略错误
			if _, ok := err.(prometheus.AlreadyRegisteredError); !ok {
				return err
			}
		}
	}

	m.registered = true
	return nil
}

// SetLogger 设置日志
func (m *Metrics) SetLogger(logger *zap.Logger) {
	m.logger = logger
}

// --- 连接相关方法 ---

// OnConnect 连接建立时调用
func (m *Metrics) OnConnect() {
	m.ActiveConnections.Inc()
	m.TotalConnections.Inc()
}

// OnDisconnect 连接断开时调用
func (m *Metrics) OnDisconnect(duration time.Duration) {
	m.ActiveConnections.Dec()
	m.ConnectionDuration.Observe(duration.Seconds())
}

// --- 消息相关方法 ---

// OnMessageReceived 收到消息时调用
func (m *Metrics) OnMessageReceived(msgType string, size int) {
	m.MessagesReceived.WithLabelValues(msgType).Inc()
	m.MessageSize.Observe(float64(size))
}

// OnMessageSent 发送消息时调用
func (m *Metrics) OnMessageSent(msgType string) {
	m.MessagesSent.WithLabelValues(msgType).Inc()
}

// RecordMessageProcessingTime 记录消息处理时间
func (m *Metrics) RecordMessageProcessingTime(duration time.Duration) {
	m.MessageProcessingTime.Observe(duration.Seconds())
}

// --- 心跳相关方法 ---

// OnHeartbeatReceived 收到心跳时调用
func (m *Metrics) OnHeartbeatReceived() {
	m.HeartbeatReceived.Inc()
}

// OnHeartbeatSent 发送心跳时调用
func (m *Metrics) OnHeartbeatSent() {
	m.HeartbeatSent.Inc()
}

// OnHeartbeatTimeout 心跳超时时调用
func (m *Metrics) OnHeartbeatTimeout() {
	m.HeartbeatTimeout.Inc()
}

// --- 错误相关方法 ---

// OnError 发生错误时调用
func (m *Metrics) OnError(errorType string) {
	m.Errors.WithLabelValues(errorType).Inc()
}

// --- 系统相关方法 ---

// UpdateSystemMetrics 更新系统指标
func (m *Metrics) UpdateSystemMetrics(goroutines int, memBytes uint64) {
	m.GoroutineCount.Set(float64(goroutines))
	m.MemoryUsage.Set(float64(memBytes))
}

// Handler 返回Prometheus HTTP处理器
func (m *Metrics) Handler() http.Handler {
	return promhttp.Handler()
}

// StartMetricsServer 启动独立的metrics HTTP服务器
func StartMetricsServer(port int, logger *zap.Logger) *http.Server {
	metrics := GetMetrics()
	metrics.SetLogger(logger)

	if err := metrics.Register(); err != nil {
		logger.Error("注册Prometheus指标失败", zap.Error(err))
	}

	mux := http.NewServeMux()
	mux.Handle("/metrics", metrics.Handler())
	mux.HandleFunc("/health", func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("OK"))
	})

	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", port),
		Handler: mux,
	}

	go func() {
		logger.Info("📊 Prometheus metrics 服务启动",
			zap.Int("port", port),
			zap.String("endpoint", "/metrics"),
		)
		if err := server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Error("Metrics服务启动失败", zap.Error(err))
		}
	}()

	return server
}

// Shutdown 关闭服务
func (m *Metrics) Shutdown() {
	if m.logger != nil {
		m.logger.Info("📊 Prometheus metrics 已关闭")
	}
}
