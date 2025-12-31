package handler

import (
	"context"
	"sync"
	"sync/atomic"
	"time"

	"im-connect-go/internal/channel"
	"im-connect-go/internal/config"
	"im-connect-go/internal/metrics"

	"go.uber.org/zap"
)

// messageTaskPool 对象池（对标 Netty PooledByteBufAllocator）
// 复用 MessageTask 对象，减少 GC 压力
var messageTaskPool = sync.Pool{
	New: func() interface{} {
		return &MessageTask{}
	},
}

// AsyncMessageHandler 异步消息处理器
// 对标 Java 的 ThreadPoolTaskExecutor 处理方式
type AsyncMessageHandler struct {
	config        *config.Config
	logger        *zap.Logger
	msgHandler    *MessageHandler
	metricsClient *metrics.Metrics

	// 处理队列
	queue      chan *MessageTask
	queueSize  int
	numWorkers int

	// 工作协程
	workers []*MessageWorker

	// 统计
	processedCount int64
	droppedCount   int64
	queueMaxLength int
	failureCount   int64
	processingTime int64 // 纳秒

	// 停止信号
	stopChan chan struct{}
	wg       sync.WaitGroup
	mu       sync.RWMutex
}

// MessageTask 消息处理任务
type MessageTask struct {
	UserID     string
	Connection channel.Connection
	Message    []byte
	Timestamp  time.Time
	RetryCount int
}

// MessageWorker 消息处理工作协程
type MessageWorker struct {
	id      int
	queue   chan *MessageTask
	handler *MessageHandler
	logger  *zap.Logger
	metrics *metrics.Metrics
}

// NewAsyncMessageHandler 创建异步消息处理器
func NewAsyncMessageHandler(
	cfg *config.Config,
	logger *zap.Logger,
	msgHandler *MessageHandler,
	queueSize int,
	numWorkers int,
) *AsyncMessageHandler {
	if queueSize <= 0 {
		queueSize = 10000
	}
	if numWorkers <= 0 {
		numWorkers = 16
	}

	handler := &AsyncMessageHandler{
		config:        cfg,
		logger:        logger,
		msgHandler:    msgHandler,
		metricsClient: metrics.GetMetrics(),
		queue:         make(chan *MessageTask, queueSize),
		queueSize:     queueSize,
		numWorkers:    numWorkers,
		workers:       make([]*MessageWorker, numWorkers),
		stopChan:      make(chan struct{}),
	}

	// 启动工作协程
	for i := 0; i < numWorkers; i++ {
		worker := &MessageWorker{
			id:      i,
			queue:   handler.queue,
			handler: msgHandler,
			logger:  logger,
			metrics: metrics.GetMetrics(),
		}
		handler.workers[i] = worker

		handler.wg.Add(1)
		go worker.run(&handler.wg, handler)
	}

	logger.Info("✅ 异步消息处理器初始化完成",
		zap.Int("queue_size", queueSize),
		zap.Int("num_workers", numWorkers),
	)

	return handler
}

// Submit 提交消息处理任务
func (h *AsyncMessageHandler) Submit(userID string, conn channel.Connection, message []byte) bool {
	// ✅ 从对象池获取（对标 Netty ctx.alloc().buffer()）
	task := messageTaskPool.Get().(*MessageTask)
	task.UserID = userID
	task.Connection = conn
	task.Message = message
	task.Timestamp = time.Now()
	task.RetryCount = 0

	select {
	case h.queue <- task:
		// 更新最大队列长度
		h.mu.Lock()
		currentLen := len(h.queue)
		if currentLen > h.queueMaxLength {
			h.queueMaxLength = currentLen
		}
		h.mu.Unlock()

		// 记录监控指标
		if h.metricsClient != nil {
			// h.metricsClient.RecordMessageQueued()
		}

		return true
	default:
		// 队列满，丢弃消息
		h.logger.Warn("⚠️ 消息处理队列满，丢弃消息",
			zap.String("user_id", userID),
			zap.Int("queue_size", len(h.queue)),
			zap.Int("max_queue_size", h.queueSize),
		)
		atomic.AddInt64(&h.droppedCount, 1)

		// ✅ 归还到池（对标 Netty ReferenceCountUtil.release()）
		messageTaskPool.Put(task)

		// 记录监控指标
		if h.metricsClient != nil {
			// h.metricsClient.RecordMessageDropped()
		}

		return false
	}
}

// MessageWorker.run 工作协程主循环
func (w *MessageWorker) run(wg *sync.WaitGroup, handler *AsyncMessageHandler) {
	defer wg.Done()

	for task := range w.queue {
		startTime := time.Now()

		// 调用原始 MessageHandler 处理
		if err := w.handler.HandleBinaryMessage(task.Connection, task.Message); err != nil {
			w.logger.Error("消息处理失败",
				zap.String("user_id", task.UserID),
				zap.Int("message_size", len(task.Message)),
				zap.Duration("duration", time.Since(startTime)),
				zap.Error(err),
			)
			atomic.AddInt64(&handler.failureCount, 1)

			// 记录失败监控
			if w.metrics != nil {
				// w.metrics.RecordMessageProcessingFailed()
			}
		} else {
			w.logger.Debug("消息处理成功",
				zap.String("user_id", task.UserID),
				zap.Int("message_size", len(task.Message)),
				zap.Duration("duration", time.Since(startTime)),
			)
			atomic.AddInt64(&handler.processedCount, 1)

			// 记录成功监控
			if w.metrics != nil {
				// w.metrics.RecordMessageProcessingSuccess(time.Since(startTime))
			}
		}

		// 累计处理时间
		atomic.AddInt64(&handler.processingTime, time.Since(startTime).Nanoseconds())

		// ✅ 归还到池（对标 Netty ReferenceCountUtil.release()）
		messageTaskPool.Put(task)
	}
}

// WaitUntilEmpty 等待队列为空（用于优雅关闭）
func (h *AsyncMessageHandler) WaitUntilEmpty(timeout time.Duration) bool {
	deadline := time.Now().Add(timeout)
	ticker := time.NewTicker(100 * time.Millisecond)
	defer ticker.Stop()

	for {
		if len(h.queue) == 0 {
			return true
		}

		select {
		case <-ticker.C:
			if time.Now().After(deadline) {
				h.logger.Warn("等待消息队列清空超时",
					zap.Int("remaining_messages", len(h.queue)),
				)
				return false
			}
		}
	}
}

// Shutdown 关闭异步处理器
func (h *AsyncMessageHandler) Shutdown(ctx context.Context) {
	h.logger.Info("🔄 关闭异步消息处理器...")

	// 等待队列中的任务处理完
	h.WaitUntilEmpty(30 * time.Second)

	// 关闭队列，通知所有工作协程停止
	close(h.queue)

	// 等待所有工作协程结束
	h.wg.Wait()

	h.logger.Info("✅ 异步消息处理器已关闭",
		zap.Int64("processed", atomic.LoadInt64(&h.processedCount)),
		zap.Int64("dropped", atomic.LoadInt64(&h.droppedCount)),
		zap.Int64("failures", atomic.LoadInt64(&h.failureCount)),
		zap.Int("max_queue_length", h.queueMaxLength),
	)
}

// GetStats 获取统计信息
func (h *AsyncMessageHandler) GetStats() map[string]interface{} {
	h.mu.RLock()
	defer h.mu.RUnlock()

	totalProcessed := atomic.LoadInt64(&h.processedCount)
	avgProcessingTime := int64(0)
	if totalProcessed > 0 {
		avgProcessingTime = atomic.LoadInt64(&h.processingTime) / totalProcessed
	}

	return map[string]interface{}{
		"processed_count":        totalProcessed,
		"dropped_count":          atomic.LoadInt64(&h.droppedCount),
		"failure_count":          atomic.LoadInt64(&h.failureCount),
		"current_queue_len":      len(h.queue),
		"max_queue_length":       h.queueMaxLength,
		"num_workers":            h.numWorkers,
		"queue_size":             h.queueSize,
		"avg_processing_time_ms": avgProcessingTime / 1_000_000,
	}
}

// GetQueueLength 获取当前队列长度
func (h *AsyncMessageHandler) GetQueueLength() int {
	return len(h.queue)
}

// GetSuccessRate 获取成功率
func (h *AsyncMessageHandler) GetSuccessRate() float64 {
	total := atomic.LoadInt64(&h.processedCount) + atomic.LoadInt64(&h.droppedCount) + atomic.LoadInt64(&h.failureCount)
	if total == 0 {
		return 1.0
	}
	return float64(atomic.LoadInt64(&h.processedCount)) / float64(total)
}
