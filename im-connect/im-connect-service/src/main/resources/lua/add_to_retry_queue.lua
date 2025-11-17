local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)
local value = ARGV[1]         -- JSON字符串
local score_str = tostring(ARGV[2]) -- 执行时间戳（确保是字符串）
local client_msg_id = ARGV[3] -- 客户端消息ID

-- 原子性操作：同时添加到ZSet和Hash
-- ZADD 可以接受字符串格式的数字，Redis会自动转换
redis.call('ZADD', zset_key, score_str, value)
redis.call('HSET', hash_key, client_msg_id, value)

return 1

