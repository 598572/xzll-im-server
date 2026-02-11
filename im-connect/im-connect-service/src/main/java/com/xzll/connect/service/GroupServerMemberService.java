package com.xzll.connect.service;

import java.util.List;
import java.util.Set;

/**
 * @Author: hzz
 * @Date: 2026-02-09
 * @Description: 群服务器成员分片服务接口
 *
 * 核心功能：
 * 1. 维护Redis Hash结构：group:server:{groupId} -> {serverIp: [userIds]}
 * 2. 用户上线时更新分片信息
 * 3. 用户下线时更新分片信息
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
public interface GroupServerMemberService {

    /**
     * 用户上线，更新群分片信息
     *
     * @param userId 用户ID
     * @param serverIp 服务器IP（格式：ip:port）
     */
    void onUserOnline(String userId, String serverIp);

    /**
     * 用户下线，更新群分片信息
     *
     * @param userId 用户ID
     */
    void onUserOffline(String userId);

    /**
     * 批量更新群服务器成员分片信息
     *
     * @param groupIds 群ID列表
     * @param userId 用户ID
     * @param serverIp 服务器IP（格式：ip:port）
     * @param isOnline true-上线，false-下线
     */
    void batchUpdateGroupServerMember(List<String> groupIds, String userId, String serverIp, boolean isOnline);

    /**
     * 获取本地服务器的群成员列表
     *
     * @param groupId 群ID
     * @return 本地服务器的群成员ID集合
     */
    Set<String> getLocalGroupMembers(String groupId);

    /**
     * 重建群分片信息（用于服务重启后恢复分片数据）
     *
     * @param serverIp 服务器IP
     */
    void rebuildGroupSharding(String serverIp);

    /**
     * 获取本服务器IP
     *
     * @return 服务器IP（格式：ip:port）
     */
    String getServerIp();

    /**
     * 从缓存获取用户的群列表（供缓存更新使用）
     *
     * @param userId 用户ID
     * @return 群ID列表
     */
    List<String> getUserGroupIdsFromCache(String userId);

    /**
     * 清理过期的群分片信息（可选，定期任务调用）
     */
    void cleanupExpiredSharding();
}
