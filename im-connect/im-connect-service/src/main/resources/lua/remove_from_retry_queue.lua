local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)

-- 使用StringCodec直接传递参数，无需cjson.decode（性能更优）
local msg_id = ARGV[1]

-- 检查Hash中是否存在
local exists = redis.call('HEXISTS', hash_key, msg_id)

if exists == 1 then
    -- 原子性删除：
    -- 1. 从ZSet删除msgId（现在ZSet只存msgId，不存value）
    -- 2. 从Hash删除完整数据
    redis.call('ZREM', zset_key, msg_id)
    redis.call('HDEL', hash_key, msg_id)
    return 1
else
    -- 消息不存在（可能已过期或已处理）
    return 0
end

