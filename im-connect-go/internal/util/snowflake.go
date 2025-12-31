package util

import (
	"sync"
	"time"
)

// SnowflakeIDGenerator 雪花算法ID生成器（对标 Java im-common/SnowflakeIdGenerator）
// 64位ID结构：
// - 1位：符号位，始终为0
// - 41位：时间戳差值（毫秒），可用69年
// - 10位：机器ID（5位数据中心ID + 5位机器ID）
// - 12位：序列号，每毫秒可生成4096个ID
type SnowflakeIDGenerator struct {
	mu            sync.Mutex
	epoch         int64 // 起始时间戳（毫秒）
	dataCenterID  int64 // 数据中心ID
	machineID     int64 // 机器ID
	sequence      int64 // 序列号
	lastTimestamp int64 // 上次生成ID的时间戳
}

const (
	// 各部分位数
	timestampBits    = 41 // 时间戳位数
	dataCenterIDBits = 5  // 数据中心ID位数
	machineIDBits    = 5  // 机器ID位数
	sequenceBits     = 12 // 序列号位数

	// 最大值
	maxDataCenterID = -1 ^ (-1 << dataCenterIDBits) // 31
	maxMachineID    = -1 ^ (-1 << machineIDBits)    // 31
	maxSequence     = -1 ^ (-1 << sequenceBits)     // 4095

	// 左移位数
	machineIDShift    = sequenceBits                                    // 12
	dataCenterIDShift = sequenceBits + machineIDBits                    // 17
	timestampShift    = sequenceBits + machineIDBits + dataCenterIDBits // 22
)

// 默认起始时间：2024-01-01 00:00:00 UTC
var defaultEpoch = time.Date(2024, 1, 1, 0, 0, 0, 0, time.UTC).UnixMilli()

// 全局单例
var (
	defaultGenerator *SnowflakeIDGenerator
	once             sync.Once
)

// GetDefaultGenerator 获取默认生成器（单例）
func GetDefaultGenerator() *SnowflakeIDGenerator {
	once.Do(func() {
		// 默认使用数据中心ID=1，机器ID=1
		// 生产环境应该从配置或环境变量获取
		defaultGenerator = NewSnowflakeIDGenerator(1, 1)
	})
	return defaultGenerator
}

// NewSnowflakeIDGenerator 创建新的雪花ID生成器
func NewSnowflakeIDGenerator(dataCenterID, machineID int64) *SnowflakeIDGenerator {
	if dataCenterID < 0 || dataCenterID > maxDataCenterID {
		panic("dataCenterID out of range")
	}
	if machineID < 0 || machineID > maxMachineID {
		panic("machineID out of range")
	}

	return &SnowflakeIDGenerator{
		epoch:         defaultEpoch,
		dataCenterID:  dataCenterID,
		machineID:     machineID,
		sequence:      0,
		lastTimestamp: -1,
	}
}

// NextID 生成下一个ID
func (g *SnowflakeIDGenerator) NextID() uint64 {
	g.mu.Lock()
	defer g.mu.Unlock()

	timestamp := time.Now().UnixMilli()

	// 时钟回拨检查
	if timestamp < g.lastTimestamp {
		// 等待时钟追上
		for timestamp <= g.lastTimestamp {
			time.Sleep(time.Millisecond)
			timestamp = time.Now().UnixMilli()
		}
	}

	if timestamp == g.lastTimestamp {
		// 同一毫秒内，序列号递增
		g.sequence = (g.sequence + 1) & maxSequence
		if g.sequence == 0 {
			// 序列号溢出，等待下一毫秒
			for timestamp <= g.lastTimestamp {
				time.Sleep(time.Microsecond)
				timestamp = time.Now().UnixMilli()
			}
		}
	} else {
		// 新的毫秒，序列号归零
		g.sequence = 0
	}

	g.lastTimestamp = timestamp

	// 组装ID
	id := uint64((timestamp-g.epoch)<<timestampShift) |
		uint64(g.dataCenterID<<dataCenterIDShift) |
		uint64(g.machineID<<machineIDShift) |
		uint64(g.sequence)

	return id
}

// NextIDString 生成下一个ID（字符串格式）
func (g *SnowflakeIDGenerator) NextIDString() string {
	return FormatID(g.NextID())
}

// FormatID 格式化ID为字符串
func FormatID(id uint64) string {
	return formatUint64(id)
}

// ParseID 解析ID，返回时间戳、数据中心ID、机器ID、序列号
func ParseID(id uint64) (timestamp time.Time, dataCenterID, machineID, sequence int64) {
	timestampMillis := int64(id>>timestampShift) + defaultEpoch
	timestamp = time.UnixMilli(timestampMillis)
	dataCenterID = int64((id >> dataCenterIDShift) & maxDataCenterID)
	machineID = int64((id >> machineIDShift) & maxMachineID)
	sequence = int64(id & maxSequence)
	return
}

// GenerateMessageID 生成消息ID（便捷方法）
func GenerateMessageID() uint64 {
	return GetDefaultGenerator().NextID()
}

// GenerateMessageIDString 生成消息ID字符串（便捷方法）
func GenerateMessageIDString() string {
	return GetDefaultGenerator().NextIDString()
}

// formatUint64 将 uint64 转换为字符串（避免科学计数法）
func formatUint64(n uint64) string {
	if n == 0 {
		return "0"
	}

	var buf [20]byte
	i := len(buf)
	for n > 0 {
		i--
		buf[i] = byte(n%10) + '0'
		n /= 10
	}
	return string(buf[i:])
}
