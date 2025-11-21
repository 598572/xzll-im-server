local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)

-- 使用StringCodec直接传递参数，无需cjson.decode（性能更优）
local compressed_data = ARGV[1]  -- LZ4压缩后的Base64数据
local score = ARGV[2]            -- 执行时间戳（字符串）
local msg_id = ARGV[3]           -- 消息ID

-- 原子性操作：
-- 1. ZSet存msgId（轻量级索引，约20字节）
-- 2. Hash存压缩后的完整数据（LZ4压缩，减少50-70%体积）
redis.call('ZADD', zset_key, tonumber(score), msg_id)
redis.call('HSET', hash_key, msg_id, compressed_data)

return 1

