local route_prefix = KEYS[1]         -- 用户登录的机器信息
local login_status_prefix = KEYS[2]  -- 用户登录状态
local uid = ARGV[1]
local ip_port = ARGV[2] -- 用户登录机器的ip端口
local status = ARGV[3]

redis.call('HSET', route_prefix, uid, ip_port)
redis.call('HSET', login_status_prefix, uid, status)
return 1