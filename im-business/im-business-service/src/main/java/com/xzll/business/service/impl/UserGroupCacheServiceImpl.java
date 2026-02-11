package com.xzll.business.service.impl;

import com.xzll.business.entity.mysql.ImGroupMember;
import com.xzll.business.mapper.ImGroupMemberMapper;
import com.xzll.business.service.UserGroupCacheService;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2026-02-09
 * @Description: 用户群组缓存管理服务实现类
 */
@Slf4j
@Service
public class UserGroupCacheServiceImpl implements UserGroupCacheService {

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private ImGroupMemberMapper imGroupMemberMapper;

    /**
     * Redis Key 前缀
     */
    private static final String USER_GROUPS_KEY_PREFIX = "user:groups:";

    /**
     * 缓存过期时间（24小时）
     *
     * 设计原则：
     * 1. 缓存由业务层（im-business）维护，加群/退群时主动更新
     * 2. 用户上线时触发缓存查询，未命中则从数据库重建
     * 3. 设置24小时TTL，覆盖大部分用户单次在线时长
     * 4. im-connect 不调用 RPC，直接读缓存，校验用户是否在群中
     * 5. 正常情况下加群/退群会主动删除缓存，数据一致性有保障
     * 6. TTL过期后会自动重建，异常情况也能快速恢复
     *
     * 注意：不使用定时任务刷新，采用懒加载+主动更新模式
     */
    private static final int CACHE_TTL_SECONDS = 86400; // 24小时

    /**
     * 用户加入群 - 添加缓存
     */
    @Override
    public void onUserJoinGroup(String userId, String groupId) {
        try {
            String key = USER_GROUPS_KEY_PREFIX + userId;
            RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

            // 添加 groupId，score 为当前时间戳
            sortedSet.add(System.currentTimeMillis(), groupId);

            // 设置过期时间（防止僵尸数据）
            sortedSet.expire(CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            log.info("【缓存管理】用户加入群 - userId:{}, groupId:{}, key:{}",
                    userId, groupId, key);

        } catch (Exception e) {
            log.error("【缓存管理】用户加入群失败 - userId:{}, groupId:{}", userId, groupId, e);
        }
    }

    /**
     * 用户退出群 - 删除缓存
     */
    @Override
    public void onUserQuitGroup(String userId, String groupId) {
        try {
            String key = USER_GROUPS_KEY_PREFIX + userId;
            RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

            // 删除指定的 groupId
            boolean removed = sortedSet.remove(groupId);

            log.info("【缓存管理】用户退出群 - userId:{}, groupId:{}, removed:{}",
                    userId, groupId, removed);

        } catch (Exception e) {
            log.error("【缓存管理】用户退出群失败 - userId:{}, groupId:{}", userId, groupId, e);
        }
    }

    /**
     * 批量用户加入群
     */
    @Override
    public void onBatchUserJoinGroup(List<String> userIds, String groupId) {
        if (userIds == null || userIds.isEmpty()) {
            log.warn("【缓存管理】批量用户加入群 - 用户列表为空，groupId:{}", groupId);
            return;
        }

        try {
            long currentTime = System.currentTimeMillis();

            for (String userId : userIds) {
                String key = USER_GROUPS_KEY_PREFIX + userId;
                RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

                // 添加 groupId
                sortedSet.add(currentTime, groupId);

                // 设置过期时间
                sortedSet.expire(CACHE_TTL_SECONDS, TimeUnit.SECONDS);
            }

            log.info("【缓存管理】批量用户加入群 - count:{}, groupId:{}, userIds:{}",
                    userIds.size(), groupId, userIds);

        } catch (Exception e) {
            log.error("【缓存管理】批量用户加入群失败 - groupId:{}", groupId, e);
        }
    }

    /**
     * 批量用户退出群（如踢人、解散群）
     */
    @Override
    public void onBatchUserQuitGroup(List<String> userIds, String groupId) {
        if (userIds == null || userIds.isEmpty()) {
            log.warn("【缓存管理】批量用户退出群 - 用户列表为空，groupId:{}", groupId);
            return;
        }

        try {
            for (String userId : userIds) {
                String key = USER_GROUPS_KEY_PREFIX + userId;
                RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

                // 删除指定的 groupId
                sortedSet.remove(groupId);
            }

            log.info("【缓存管理】批量用户退出群 - count:{}, groupId:{}, userIds:{}",
                    userIds.size(), groupId, userIds);

        } catch (Exception e) {
            log.error("【缓存管理】批量用户退出群失败 - groupId:{}", groupId, e);
        }
    }

    /**
     * 群解散 - 清除所有成员的缓存
     */
    @Override
    public void onGroupDissolve(String groupId, List<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            log.warn("【缓存管理】群解散 - 成员列表为空，groupId:{}", groupId);
            return;
        }

        try {
            for (String userId : memberIds) {
                String key = USER_GROUPS_KEY_PREFIX + userId;
                RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

                // 删除指定的 groupId
                boolean removed = sortedSet.remove(groupId);

                log.debug("【缓存管理】群解散 - 清除成员缓存 - userId:{}, groupId:{}, removed:{}",
                        userId, groupId, removed);
            }

            log.info("【缓存管理】群解散 - 清除所有成员缓存 - groupId:{}, count:{}",
                    groupId, memberIds.size());

        } catch (Exception e) {
            log.error("【缓存管理】群解散失败 - groupId:{}", groupId, e);
        }
    }

    /**
     * 刷新用户群列表缓存（从数据库查询并更新缓存）
     * 用于缓存预热或修复
     */
    @Override
    public List<String> refreshUserGroupsCache(String userId) {
        try {
            // 1. 从数据库查询用户加入的所有群
            List<ImGroupMember> members = imGroupMemberMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ImGroupMember>()
                            .eq(ImGroupMember::getUserId, userId)
                            .eq(ImGroupMember::getStatus, 1) // 只查询正常状态的群
            );

            if (members == null || members.isEmpty()) {
                log.info("【缓存管理】刷新用户群列表缓存 - 用户未加入任何群 - userId:{}", userId);
                return new ArrayList<>();
            }

            // 2. 提取群ID列表
            List<String> groupIds = members.stream()
                    .map(ImGroupMember::getGroupId)
                    .collect(Collectors.toList());

            // 3. 更新缓存
            String key = USER_GROUPS_KEY_PREFIX + userId;
            RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

            // 清空旧数据
            sortedSet.delete();

            // 添加新数据
            long currentTime = System.currentTimeMillis();
            for (String groupId : groupIds) {
                sortedSet.add(currentTime, groupId);
            }

            // 设置过期时间
            sortedSet.expire(CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            log.info("【缓存管理】刷新用户群列表缓存成功 - userId:{}, count:{}, groupIds:{}",
                    userId, groupIds.size(), groupIds);

            return groupIds;

        } catch (Exception e) {
            log.error("【缓存管理】刷新用户群列表缓存失败 - userId:{}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 获取用户群列表缓存（仅供查询，不修改缓存）
     */
    @Override
    public List<String> getUserGroupsFromCache(String userId) {
        try {
            String key = USER_GROUPS_KEY_PREFIX + userId;
            RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

            // 读取所有数据（按 score 倒序，即加入时间倒序）
            Collection<String> groupIds = sortedSet.readAll();

            if (groupIds == null || groupIds.isEmpty()) {
                log.debug("【缓存管理】获取用户群列表缓存 - 缓存未命中 - userId:{}", userId);
                return new ArrayList<>();
            }

            // 转换为 List
            List<String> result = new ArrayList<>(groupIds);

            log.debug("【缓存管理】获取用户群列表缓存 - userId:{}, count:{}", userId, result.size());

            return result;

        } catch (Exception e) {
            log.error("【缓存管理】获取用户群列表缓存失败 - userId:{}", userId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 清空用户群列表缓存（用于修复数据）
     */
    @Override
    public void clearUserGroupsCache(String userId) {
        try {
            String key = USER_GROUPS_KEY_PREFIX + userId;
            RScoredSortedSet<String> sortedSet = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);

            sortedSet.delete();

            log.info("【缓存管理】清空用户群列表缓存 - userId:{}, key:{}", userId, key);

        } catch (Exception e) {
            log.error("【缓存管理】清空用户群列表缓存失败 - userId:{}", userId, e);
        }
    }
}
