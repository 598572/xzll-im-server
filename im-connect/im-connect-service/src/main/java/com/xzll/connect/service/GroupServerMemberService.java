package com.xzll.connect.service;

import cn.hutool.json.JSONUtil;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.connect.netty.channel.LocalChannelManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.InputStream;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.springframework.core.io.ClassPathResource;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群服务器成员分片服务 - Hash分片方案（Lua优化版）
 *
 * 核心功能：
 * 1. 维护Redis Hash结构：group:server:{groupId} -> {serverIp: [userIds]}
 * 2. 用户上线时更新分片信息（Lua脚本保证原子性）
 * 3. 用户下线时更新分片信息（Lua脚本保证原子性）
 * 4. 提供查询本地服务器群成员的接口
 * 5. 支持批量更新（Pipeline减少网络往返）
 *
 * 数据结构：
 * Key: group:server:{groupId}
 * Type: HASH
 * Field: {serverIp}
 * Value: JSON数组字符串 ["userId1", "userId2", ...]
 * TTL: 3600秒（1小时）
 *
 * 性能优势：
 * - 查询O(1)：HGET直接获取目标成员
 * - 网络传输小：只传本地服务器的成员
 * - 零计算开销：无需retainAll交集运算
 * - 零本地内存：完全依赖Redis
 * - 原子更新：Lua脚本避免并发问题
 * - 批量优化：Pipeline批量更新多个群
 */
@Slf4j
@Service
public class GroupServerMemberService implements org.springframework.beans.factory.InitializingBean {

    private static final String TAG = "[群服务器成员分片服务]_";
    private static final String GROUP_SERVER_PREFIX = "group:server:";
    private static final String USER_SERVER_PREFIX = "user:server:";
    private static final String USER_GROUPS_CACHE_PREFIX = "user:groups:"; // 用户群列表缓存前缀
    private static final int CACHE_TTL = 3600; // 1小时

    // Lua脚本路径
    private static final String LUA_ADD_MEMBER = "lua/update_group_server_member_add.lua";
    private static final String LUA_REMOVE_MEMBER = "lua/update_group_server_member_remove.lua";

    // Lua脚本内容
    private String addMemberScript;
    private String removeMemberScript;

    @Resource
    private RedissonUtils redissonUtils;

    @Resource
    private LocalChannelManager localChannelManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        // 加载Lua脚本
        addMemberScript = loadLuaScript(LUA_ADD_MEMBER);
        removeMemberScript = loadLuaScript(LUA_REMOVE_MEMBER);
        log.info("{}群成员分片Lua脚本加载完成", TAG);
    }

    /**
     * 加载Lua脚本
     */
    private String loadLuaScript(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        InputStream in = resource.getInputStream();
        return IOUtils.toString(in, StandardCharsets.UTF_8);
    }

    /**
     * 查询用户加入的群ID列表（优先从Redis缓存查询）
     *
     * @param userId 用户ID
     * @return 群ID列表
     */
    private List<String> getUserGroupIds(String userId) {
        // 1. 先查Redis缓存
        String cacheKey = USER_GROUPS_CACHE_PREFIX + userId;
        try {
            List<String> cachedGroups = redissonUtils.getListRange(cacheKey, 0, -1);
            if (cachedGroups != null && !cachedGroups.isEmpty()) {
                log.debug("{}【Redis缓存】用户群列表缓存命中 - userId:{}, count:{}", TAG, userId, cachedGroups.size());
                return cachedGroups;
            }
        } catch (Exception e) {
            log.warn("{}【Redis缓存】查询用户群列表失败 - userId:{}", TAG, userId, e);
        }

        // 2. Redis未命中或查询失败
        log.warn("{}【Redis缓存】用户群列表缓存未命中 - userId:{}，建议预热缓存（调用business服务查询）", TAG, userId);
        return Collections.emptyList();
    }

    /**
     * 用户上线，更新群分片信息
     *
     * @param userId 用户ID
     * @param serverIp 服务器IP
     */
    public void onUserOnline(String userId, String serverIp) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 更新 user:server:{userId}
                String userServerKey = USER_SERVER_PREFIX + userId;
                redissonUtils.setString(userServerKey, serverIp, CACHE_TTL, java.util.concurrent.TimeUnit.SECONDS);
                log.debug("{}【用户上线】更新服务器信息 - userId:{}, server:{}", TAG, userId, serverIp);

                // 2. 查询用户加入的所有群（优先从Redis缓存查询）
                List<String> groupIds = getUserGroupIds(userId);

                if (groupIds.isEmpty()) {
                    log.debug("{}【用户上线】用户未加入任何群或缓存未预热 - userId:{}", TAG, userId);
                    return;
                }

                log.info("{}【用户上线】查询到用户群列表 - userId:{}, count:{}", TAG, userId, groupIds.size());

                // 3. 批量更新群分片信息（Pipeline批量执行Lua脚本）
                batchUpdateGroupServerMember(groupIds, userId, serverIp, true);

            } catch (Exception e) {
                log.error("{}【用户上线】更新分片信息失败 - userId:{}, server:{}",
                    TAG, userId, serverIp, e);
            }
        });
    }

    /**
     * 用户下线，更新群分片信息
     *
     * @param userId 用户ID
     */
    public void onUserOffline(String userId) {
        CompletableFuture.runAsync(() -> {
            try {
                // 1. 查询用户所在的服务器
                String userServerKey = USER_SERVER_PREFIX + userId;
                String serverIp = redissonUtils.getString(userServerKey);

                if (serverIp == null || serverIp.isEmpty()) {
                    log.warn("{}【用户下线】用户服务器信息不存在 - userId:{}", TAG, userId);
                    return;
                }

                // 2. 查询用户加入的所有群（优先从Redis缓存查询）
                List<String> groupIds = getUserGroupIds(userId);

                if (!groupIds.isEmpty()) {
                    // 3. 批量更新群分片信息（Pipeline批量执行Lua脚本）
                    batchUpdateGroupServerMember(groupIds, userId, serverIp, false);
                } else {
                    log.debug("{}【用户下线】用户未加入任何群或缓存未预热 - userId:{}", TAG, userId);
                }

                // 4. 删除用户服务器信息
                redissonUtils.delete(userServerKey);

                log.info("{}【用户下线】处理完成 - userId:{}, server:{}", TAG, userId, serverIp);

            } catch (Exception e) {
                log.error("{}【用户下线】更新分片信息失败 - userId:{}", TAG, userId, e);
            }
        });
    }

    /**
     * 更新群服务器成员分片信息（Lua脚本版本 - 原子操作）
     *
     * @param groupId 群ID
     * @param userId 用户ID
     * @param serverIp 服务器IP
     * @param isOnline true-上线，false-下线
     */
    private void updateGroupServerMember(String groupId, String userId, String serverIp, boolean isOnline) {
        try {
            String hashKey = GROUP_SERVER_PREFIX + groupId;

            // 准备Lua脚本参数
            String script = isOnline ? addMemberScript : removeMemberScript;
            List<String> keys = Collections.singletonList(hashKey);

            // 执行Lua脚本（原子操作）
            Long result = redissonUtils.executeLuaScriptAsLongWithStringCodec(
                script,
                keys,
                serverIp,  // ARGV[1]
                userId,    // ARGV[2]
                String.valueOf(CACHE_TTL)  // ARGV[3]
            );

            if (result != null && result > 0) {
                log.debug("{}【Lua分片更新】更新完成 - groupId:{}, server:{}, userId:{}, online:{}, count:{}",
                    TAG, groupId, serverIp, userId, isOnline, result);
            } else if (result != null && result == 0) {
                // 用户已存在或不存在，或服务器无成员
                log.debug("{}【Lua分片更新】无变化 - groupId:{}, server:{}, userId:{}, online:{}",
                    TAG, groupId, serverIp, userId, isOnline);
            }

        } catch (Exception e) {
            log.error("{}【Lua分片更新】更新失败 - groupId:{}, userId:{}, server:{}",
                TAG, groupId, userId, serverIp, e);
        }
    }

    /**
     * 批量更新群服务器成员分片信息（Pipeline批量执行Lua脚本）
     * 适用场景：用户上线/下线时，需要同时更新多个群的分片信息
     *
     * @param groupIds 群ID列表
     * @param userId 用户ID
     * @param serverIp 服务器IP
     * @param isOnline true-上线，false-下线
     */
    public void batchUpdateGroupServerMember(List<String> groupIds, String userId, String serverIp, boolean isOnline) {
        if (groupIds == null || groupIds.isEmpty()) {
            log.debug("{}【批量分片更新】群列表为空，跳过更新 - userId:{}", TAG, userId);
            return;
        }

        try {
            // 准备批量任务
            String script = isOnline ? addMemberScript : removeMemberScript;
            List<RedissonUtils.LuaScriptTask> tasks = groupIds.stream()
                .map(groupId -> {
                    String hashKey = GROUP_SERVER_PREFIX + groupId;
                    List<String> keys = Collections.singletonList(hashKey);
                    return new RedissonUtils.LuaScriptTask(
                        script,
                        keys,
                        serverIp,           // ARGV[1]
                        userId,             // ARGV[2]
                        String.valueOf(CACHE_TTL)  // ARGV[3]
                    );
                })
                .collect(Collectors.toList());

            // 批量执行（Pipeline）
            long startTime = System.currentTimeMillis();
            List<Long> results = redissonUtils.executeLuaScriptsBatch(tasks);
            long cost = System.currentTimeMillis() - startTime;

            // 统计结果
            int successCount = 0;
            int skipCount = 0;
            for (Long result : results) {
                if (result != null && result > 0) {
                    successCount++;
                } else if (result != null && result == 0) {
                    skipCount++;
                }
            }

            log.info("{}【Pipeline批量更新】完成 - userId:{}, server:{}, online:{}, total:{}, success:{}, skip:{}, cost:{}ms",
                TAG, userId, serverIp, isOnline, groupIds.size(), successCount, skipCount, cost);

        } catch (Exception e) {
            log.error("{}【Pipeline批量更新】失败 - userId:{}, server:{}, groups count:{}",
                TAG, userId, serverIp, groupIds.size(), e);
        }
    }

    /**
     * 获取本地服务器的群成员（Hash分片查询）
     *
     * @param groupId 群ID
     * @return 本地服务器的群成员ID集合
     */
    public Set<String> getLocalGroupMembers(String groupId) {
        long startTime = System.currentTimeMillis();

        try {
            String serverIp = getLocalServerIp();
            String hashKey = GROUP_SERVER_PREFIX + groupId;

            // 直接查询Redis Hash
            String membersJson = redissonUtils.getHash(hashKey, serverIp);

            if (membersJson == null || membersJson.isEmpty()) {
                log.debug("{}【Hash查询】无本地成员 - groupId:{}, server:{}",
                    TAG, groupId, serverIp);
                return Collections.emptySet();
            }

            // JSON反序列化
            List<String> members = JSONUtil.toList(membersJson, String.class);

            long cost = System.currentTimeMillis() - startTime;
            log.info("{}【Hash查询】groupId:{}, server:{}, count:{}, cost:{}ms",
                TAG, groupId, serverIp, members.size(), cost);

            return new HashSet<>(members);

        } catch (Exception e) {
            log.error("{}【Hash查询】查询失败 - groupId:{}", TAG, groupId, e);
            return Collections.emptySet();
        }
    }

    /**
     * 重建群分片信息（服务器启动时调用，可选）
     *
     * @param serverIp 服务器IP
     */
    public void rebuildGroupSharding(String serverIp) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("{}【重建分片】开始重建 - server:{}", TAG, serverIp);

                // 1. 获取本地所有在线成员
                Set<String> localOnlineUserIds = localChannelManager.getAllOnLineUserId();

                if (localOnlineUserIds == null || localOnlineUserIds.isEmpty()) {
                    log.info("{}【重建分片】无本地在线成员 - server:{}", TAG, serverIp);
                    return;
                }

                log.info("{}【重建分片】本地在线成员数 - server:{}, count:{}",
                    TAG, serverIp, localOnlineUserIds.size());

                // TODO: 查询每个用户所在的群
                // TODO: 批量更新 group:server:{groupId}

                log.info("{}【重建分片】重建完成 - server:{}, count:{}",
                    TAG, serverIp, localOnlineUserIds.size());

            } catch (Exception e) {
                log.error("{}【重建分片】重建失败 - server:{}", TAG, serverIp, e);
            }
        });
    }

    /**
     * 获取本地服务器IP
     *
     * @return 服务器IP:Port
     */
    private String getLocalServerIp() {
        try {
            // 方式1：从配置文件读取（如果有）
            // return serverConfig.getServerIp();

            // 方式2：自动获取（推荐）
            String ip = InetAddress.getLocalHost().getHostAddress();

            // TODO: 从配置文件获取端口号
            // int port = serverConfig.getPort();
            // return ip + ":" + port;

            // 临时：使用默认端口
            return ip + ":10001";

        } catch (Exception e) {
            log.warn("{}【获取服务器IP】获取失败，使用默认值", TAG, e);
            return "127.0.0.1:10001";
        }
    }

    /**
     * 清理过期的群分片信息（定期任务调用，可选）
     */
    public void cleanupExpiredSharding() {
        // TODO: 扫描 group:server:*，清理超过TTL的Field
        // Redis会自动过期，这个方法可以不实现
        log.debug("{}【清理分片】Redis会自动过期，无需手动清理", TAG);
    }
}
