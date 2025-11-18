local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)
-- JsonJacksonCodec传递的参数需要用cjson.decode解码
local msg_id = cjson.decode(ARGV[1])

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

