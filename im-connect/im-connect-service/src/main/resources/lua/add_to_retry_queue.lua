local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)
local value = tostring(ARGV[1])         -- JSON字符串（确保是字符串）
local score = tonumber(ARGV[2])         -- 执行时间戳（转换为数字，ZADD需要数字类型）
local msg_id = tostring(ARGV[3])        -- 服务端消息ID（雪花算法，确保是字符串）

-- 原子性操作：同时添加到ZSet和Hash
-- ZADD 的 score 参数在 Lua 脚本中必须是数字类型，不能是字符串
redis.call('ZADD', zset_key, score, value)
redis.call('HSET', hash_key, msg_id, value)

return 1

