-- 用户下线：从群服务器成员分片移除
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

if not membersJson then
    -- 服务器无成员，直接返回
    return 0
end

-- 解析JSON数组
local members = cjson.decode(membersJson)
local found = false

-- 查找并移除用户
for i, id in ipairs(members) do
    if id == userId then
        table.remove(members, i)
        found = true
        break
    end
end

if not found then
    -- 用户不存在，直接返回
    return 0
end

-- 更新Hash
if #members > 0 then
    -- 还有其他成员，更新
    local newMembersJson = cjson.encode(members)
    redis.call('HSET', key, serverIp, newMembersJson)

    -- 设置过期时间
    if ttl and ttl > 0 then
        redis.call('EXPIRE', key, ttl)
    end

    return #members
else
    -- 无成员了，删除Field
    redis.call('HDEL', key, serverIp)

    return 0
end
