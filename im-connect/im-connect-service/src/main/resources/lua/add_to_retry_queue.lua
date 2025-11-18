local zset_key = KEYS[1]      -- C2C_MSG_RETRY_QUEUE (ZSet)
local hash_key = KEYS[2]      -- C2C_MSG_RETRY_INDEX (Hash)

-- JsonJacksonCodec对String参数添加了JSON引号，需要decode去掉
-- cjson.decode会解析JSON并去掉最外层的引号
local value_decoded = cjson.decode(ARGV[1])
local score = cjson.decode(ARGV[2])
local msg_id = cjson.decode(ARGV[3])

-- 原子性操作：同时添加到ZSet和Hash
redis.call('ZADD', zset_key, tonumber(score), value_decoded)
redis.call('HSET', hash_key, msg_id, value_decoded)

return 1

