-- 用户上线：添加到群服务器成员分片
-- KEYS[1]: group:server:{groupId}
-- ARGV[1]: serverIp (服务器IP:Port)
-- ARGV[2]: userId (用户ID)
-- ARGV[3]: ttl (过期时间，秒)

local key = KEYS[1]
local serverIp = ARGV[1]
local userId = ARGV[2]
local ttl = tonumber(ARGV[3])

-- 获取当前服务器的成员列表
local membersJson = redis.call('HGET', key, serverIp)
local members

if membersJson then
    -- 解析JSON数组
    members = cjson.decode(membersJson)

    -- 检查用户是否已存在
    for i, id in ipairs(members) do
        if id == userId then
            -- 用户已存在，直接返回
            return 0
        end
    end

    -- 添加用户
    table.insert(members, userId)
else
    -- 创建新数组
    members = {userId}
end

-- 更新Hash
local newMembersJson = cjson.encode(members)
redis.call('HSET', key, serverIp, newMembersJson)

-- 设置过期时间
if ttl and ttl > 0 then
    redis.call('EXPIRE', key, ttl)
end

-- 返回成员数量
return #members
