local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)
local client_msg_id = ARGV[1] -- 客户端消息ID

-- 从Hash索引获取value
local value = redis.call('HGET', hash_key, client_msg_id)

if value then
    -- 原子性删除：同时从ZSet和Hash删除
    redis.call('ZREM', zset_key, value)
    redis.call('HDEL', hash_key, client_msg_id)
    return 1
else
    -- 消息不存在（可能已过期或已处理）
    return 0
end

