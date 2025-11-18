local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)
local value = tostring(ARGV[1])         -- JSON字符串（确保是字符串）
local score_str = tostring(ARGV[2])     -- 执行时间戳（确保是字符串）
local msg_id = tostring(ARGV[3])        -- 服务端消息ID（雪花算法，确保是字符串）

-- 原子性操作：同时添加到ZSet和Hash
-- ZADD 可以接受字符串格式的数字，Redis会自动转换
redis.call('ZADD', zset_key, score_str, value)
redis.call('HSET', hash_key, msg_id, value)

return 1

