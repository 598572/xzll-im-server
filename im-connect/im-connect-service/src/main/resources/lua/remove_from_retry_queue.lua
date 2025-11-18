local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)
local msg_id = tostring(ARGV[1]) -- 服务端消息ID（雪花算法，确保是字符串）

-- 从Hash索引获取value
local value = redis.call('HGET', hash_key, msg_id)

if value then
    -- 原子性删除：同时从ZSet和Hash删除
    redis.call('ZREM', zset_key, value)
    redis.call('HDEL', hash_key, msg_id)
    return 1
else
    -- 消息不存在（可能已过期或已处理）
    return 0
end

