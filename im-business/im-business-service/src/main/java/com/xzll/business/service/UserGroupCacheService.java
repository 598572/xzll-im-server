package com.xzll.business.service;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-09
 * @Description: 用户群组缓存管理服务 - 统一管理 Redis 中的用户群列表缓存
 *
 * 功能说明：
 * 1. 维护 user:groups:{userId} 缓存（用户加入的所有群）
 * 2. 在加群、退群、解散群等操作时自动更新缓存
 * 3. im-connect 服务直接读取此缓存，无需查询数据库
 *
 * 架构设计：
 * - im-business: 操作数据库 + 更新缓存（写入者）
 * - im-connect: 直接读取缓存（读取者）
 * - im-console: 通过 Feign 调用 im-business
 */
public interface UserGroupCacheService {

    /**
     * 用户加入群 - 添加缓存
     *
     * @param userId  用户ID
     * @param groupId 群组ID
     */
    void onUserJoinGroup(String userId, String groupId);

    /**
     * 用户退出群 - 删除缓存
     *
     * @param userId  用户ID
     * @param groupId 群组ID
     */
    void onUserQuitGroup(String userId, String groupId);

    /**
     * 批量用户加入群
     *
     * @param userIds 用户ID列表
     * @param groupId 群组ID
     */
    void onBatchUserJoinGroup(List<String> userIds, String groupId);

    /**
     * 批量用户退出群（如踢人、解散群）
     *
     * @param userIds 用户ID列表
     * @param groupId 群组ID
     */
    void onBatchUserQuitGroup(List<String> userIds, String groupId);

    /**
     * 群解散 - 清除所有成员的缓存
     *
     * @param groupId    群组ID
     * @param memberIds 所有成员ID列表
     */
    void onGroupDissolve(String groupId, List<String> memberIds);

    /**
     * 刷新用户群列表缓存（用于缓存预热或修复）
     *
     * @param userId 用户ID
     * @return 缓存的群列表
     */
    List<String> refreshUserGroupsCache(String userId);

    /**
     * 获取用户群列表缓存（仅供查询，不修改缓存）
     *
     * @param userId 用户ID
     * @return 群组ID列表
     */
    List<String> getUserGroupsFromCache(String userId);

    /**
     * 清空用户群列表缓存（用于修复数据）
     *
     * @param userId 用户ID
     */
    void clearUserGroupsCache(String userId);
}
