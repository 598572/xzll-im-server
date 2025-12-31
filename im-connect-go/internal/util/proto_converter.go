package util

import (
	"encoding/binary"
	"fmt"
	"strconv"
	"strings"
)

// ============= UUID 与 bytes 转换（对标 Java ProtoConverterUtil） =============

// UUIDStringToBytes 将 UUID 字符串转换为 16 字节的 bytes
// 格式：UUID "550e8400-e29b-41d4-a716-446655440000" -> 16字节bytes
// 对标 Java: ProtoConverterUtil.uuidStringToBytes()
func UUIDStringToBytes(uuidStr string) []byte {
	if uuidStr == "" {
		return nil
	}

	// 移除连字符：550e8400-e29b-41d4-a716-446655440000 -> 550e8400e29b41d4a716446655440000
	cleanUUID := strings.ReplaceAll(uuidStr, "-", "")
	if len(cleanUUID) != 32 {
		return nil
	}

	// 解析高64位和低64位
	mostSigBits, err := strconv.ParseUint(cleanUUID[:16], 16, 64)
	if err != nil {
		return nil
	}
	leastSigBits, err := strconv.ParseUint(cleanUUID[16:], 16, 64)
	if err != nil {
		return nil
	}

	// 转换为16字节
	bytes := make([]byte, 16)
	binary.BigEndian.PutUint64(bytes[0:8], mostSigBits)
	binary.BigEndian.PutUint64(bytes[8:16], leastSigBits)

	return bytes
}

// BytesToUUIDString 将 16 字节的 bytes 转换回 UUID 字符串
// 对标 Java: ProtoConverterUtil.bytesToUuidString()
func BytesToUUIDString(bytes []byte) string {
	if len(bytes) == 0 {
		return ""
	}

	if len(bytes) != 16 {
		// 如果不是16字节，尝试直接当作字符串处理（兼容旧数据）
		return string(bytes)
	}

	// 解析高64位和低64位
	mostSigBits := binary.BigEndian.Uint64(bytes[0:8])
	leastSigBits := binary.BigEndian.Uint64(bytes[8:16])

	// 格式化为 UUID 字符串：xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
	return fmt.Sprintf("%08x-%04x-%04x-%04x-%012x",
		mostSigBits>>32,             // 8位
		(mostSigBits>>16)&0xFFFF,    // 4位
		mostSigBits&0xFFFF,          // 4位
		(leastSigBits>>48)&0xFFFF,   // 4位
		leastSigBits&0xFFFFFFFFFFFF, // 12位
	)
}

// ============= 雪花ID 转换 =============

// SnowflakeStringToUint64 将雪花ID字符串转换为 uint64
// 对标 Java: ProtoConverterUtil.snowflakeStringToLong()
func SnowflakeStringToUint64(snowflakeIDStr string) uint64 {
	if snowflakeIDStr == "" {
		return 0
	}

	id, err := strconv.ParseUint(snowflakeIDStr, 10, 64)
	if err != nil {
		return 0
	}
	return id
}

// Uint64ToSnowflakeString 将 uint64 转换为雪花ID字符串
// 对标 Java: ProtoConverterUtil.longToSnowflakeString()
func Uint64ToSnowflakeString(snowflakeID uint64) string {
	if snowflakeID == 0 {
		return ""
	}
	return strconv.FormatUint(snowflakeID, 10)
}

// ============= ChatID 生成（对标 Java ChatIdUtils） =============

const (
	DefaultBizType = 100 // 默认业务类型
	ChatTypeC2C    = "1" // 单聊类型
	ChatTypeGroup  = "2" // 群聊类型
)

// GenerateChatID 生成会话ID
// 格式：{bizType}-{chatType}-{smallerUserId}-{largerUserId}
// 例如：100-1-111-222
// 对标 Java: ChatIdUtils.buildC2CChatId() / ProtoConverterUtil.generateChatId()
func GenerateChatID(fromUserID, toUserID string) string {
	if fromUserID == "" || toUserID == "" {
		return ""
	}

	// 将两个ID按字符串大小排序，保证chatId的唯一性
	// 较小的ID在前面
	var smaller, larger string
	if compareUserID(fromUserID, toUserID) < 0 {
		smaller = fromUserID
		larger = toUserID
	} else {
		smaller = toUserID
		larger = fromUserID
	}

	// 格式：100-1-{smaller_userId}-{larger_userId}
	return fmt.Sprintf("%d-%s-%s-%s", DefaultBizType, ChatTypeC2C, smaller, larger)
}

// GenerateChatIDFromUint64 从 uint64 类型的用户ID生成会话ID
func GenerateChatIDFromUint64(fromUserID, toUserID uint64) string {
	return GenerateChatID(
		strconv.FormatUint(fromUserID, 10),
		strconv.FormatUint(toUserID, 10),
	)
}

// compareUserID 比较两个用户ID
// 优先按数值比较，如果无法解析则按字符串比较
func compareUserID(a, b string) int {
	// 尝试按数值比较（雪花ID是数字）
	aNum, aErr := strconv.ParseUint(a, 10, 64)
	bNum, bErr := strconv.ParseUint(b, 10, 64)

	if aErr == nil && bErr == nil {
		// 都是数字，按数值比较
		if aNum < bNum {
			return -1
		} else if aNum > bNum {
			return 1
		}
		return 0
	}

	// 非数字，按字符串比较
	return strings.Compare(a, b)
}

// ParseChatID 解析会话ID，返回 bizType, chatType, userId1, userId2
func ParseChatID(chatID string) (bizType int, chatType string, userID1, userID2 string, err error) {
	if chatID == "" {
		return 0, "", "", "", fmt.Errorf("chatID不能为空")
	}

	parts := strings.Split(chatID, "-")
	if len(parts) != 4 {
		return 0, "", "", "", fmt.Errorf("无效的chatID格式：%s", chatID)
	}

	bizTypeInt, err := strconv.Atoi(parts[0])
	if err != nil {
		return 0, "", "", "", fmt.Errorf("无效的bizType：%s", parts[0])
	}

	return bizTypeInt, parts[1], parts[2], parts[3], nil
}

// IsC2CChat 检查是否为单聊
func IsC2CChat(chatID string) bool {
	_, chatType, _, _, err := ParseChatID(chatID)
	if err != nil {
		return false
	}
	return chatType == ChatTypeC2C
}
