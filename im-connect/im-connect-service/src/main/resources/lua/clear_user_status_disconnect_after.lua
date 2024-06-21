local route_prefix = KEYS[1]        -- 用户登录的机器信息
local login_status_prefix = KEYS[2] -- 用户登录状态
local uid = ARGV[1]                 -- 用户id

redis.call("HDEL", route_prefix, uid)
redis.call("HDEL", login_status_prefix, uid)
return 1