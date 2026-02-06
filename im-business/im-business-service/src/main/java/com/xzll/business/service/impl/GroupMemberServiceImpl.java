package com.xzll.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.xzll.business.entity.mysql.ImGroupMember;
import com.xzll.business.mapper.ImGroupMemberMapper;
import com.xzll.business.service.GroupMemberService;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import jakarta.annotation.Resource;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群成员服务实现类（支持Redis缓存）
 */
@Service
@Slf4j
public class GroupMemberServiceImpl implements GroupMemberService {

    @Resource
    private ImGroupMemberMapper groupMemberMapper;

    @Resource
    private RedissonUtils redissonUtils;

    private static final String USER_GROUPS_CACHE_PREFIX = "user:groups:";
    private static final int CACHE_TTL_SECONDS = 1800; // 30分钟

    @Override
    public List<String> selectGroupIdsByUserId(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                log.warn("【群成员服务】查询用户群列表失败 - userId为空");
                return Collections.emptyList();
            }

            List<String> groupIds = groupMemberMapper.selectGroupIdsByUserId(userId);

            if (CollectionUtils.isEmpty(groupIds)) {
                log.debug("【群成员服务】用户未加入任何群 - userId:{}", userId);
                return Collections.emptyList();
            }

            // 查询成功后，写入Redis缓存（供connect服务查询）
            writeUserGroupsCache(userId, groupIds);

            log.debug("【群成员服务】查询用户群列表成功 - userId:{}, count:{}", userId, groupIds.size());
            return groupIds;

        } catch (Exception e) {
            log.error("【群成员服务】查询用户群列表失败 - userId:{}", userId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<ImGroupMember> selectGroupsWithInfoByUserId(String userId) {
        try {
            if (userId == null || userId.isEmpty()) {
                log.warn("【群成员服务】查询用户群信息失败 - userId为空");
                return Collections.emptyList();
            }

            List<ImGroupMember> groups = groupMemberMapper.selectGroupsWithInfoByUserId(userId);

            if (CollectionUtils.isEmpty(groups)) {
                log.debug("【群成员服务】用户未加入任何群 - userId:{}", userId);
                return Collections.emptyList();
            }

            log.debug("【群成员服务】查询用户群信息成功 - userId:{}, count:{}", userId, groups.size());
            return groups;

        } catch (Exception e) {
            log.error("【群成员服务】查询用户群信息失败 - userId:{}", userId, e);
            return Collections.emptyList();
        }
    }

    /**
     * 添加群成员（同时维护Redis缓存）
     *
     * @param groupId   群ID
     * @param userId    用户ID
     * @param memberRole 成员角色：1-群主，2-管理员，3-普通成员
     */
    public void addGroupMember(String groupId, String userId, Integer memberRole) {
        try {
            // 1. 写数据库
            ImGroupMember member = new ImGroupMember();
            member.setGroupId(groupId);
            member.setUserId(userId);
            member.setMemberRole(memberRole);
            member.setJoinTime(System.currentTimeMillis());
            member.setJoinType(2); // 邀请加入
            member.setStatus(1); // 正常
            groupMemberMapper.insert(member);

            log.info("【群成员服务】添加群成员成功 - groupId:{}, userId:{}, role:{}", groupId, userId, memberRole);

            // 2. 删除Redis缓存（下次查询时会重新写入）
            deleteUserGroupsCache(userId);

        } catch (Exception e) {
            log.error("【群成员服务】添加群成员失败 - groupId:{}, userId:{}", groupId, userId, e);
            throw e;
        }
    }

    /**
     * 批量添加群成员（建群时使用）
     *
     * @param groupId   群ID
     * @param memberIds 成员ID列表
     * @param ownerId   群主ID
     * @return 成功添加的成员数量
     */
    @Override
    public int batchAddGroupMembers(String groupId, List<String> memberIds, String ownerId) {
        if (CollectionUtils.isEmpty(memberIds)) {
            log.warn("【群成员服务】批量添加群成员失败 - 成员列表为空");
            return 0;
        }

        long startTime = System.currentTimeMillis();
        long joinTime = System.currentTimeMillis();

        log.info("【群成员服务】开始批量添加群成员 - groupId:{}, count:{}", groupId, memberIds.size());

        // 1. 构建所有成员实体（只设置role，不插入数据库）
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        List<ImGroupMember> memberList = new java.util.ArrayList<>(memberIds.size());

        for (int i = 0; i < memberIds.size(); i++) {
            String userId = memberIds.get(i);

            // 第一个成员是群主，其他是普通成员
            Integer memberRole = (i == 0) ? 1 : 3;

            ImGroupMember member = new ImGroupMember();
            member.setGroupId(groupId);
            member.setUserId(userId);
            member.setMemberRole(memberRole);
            member.setJoinTime(joinTime);
            member.setJoinType(1); // 主动加入（建群时的成员）
            member.setStatus(1); // 正常
            member.setCreateTime(now);
            member.setUpdateTime(now);

            memberList.add(member);
        }

        // 2. 使用 MyBatis-Plus 批量插入（一次性插入所有成员）
        try {
            // 手动构造批量插入SQL
            // 使用SqlSessionFactory批量插入（性能最好）
            if (!memberList.isEmpty()) {
                batchInsert(memberList);
            }

            long costTime = System.currentTimeMillis() - startTime;
            log.info("【群成员服务】批量添加群成员完成 - groupId:{}, total:{}, cost:{}ms",
                    groupId, memberList.size(), costTime);

            return memberList.size();

        } catch (Exception e) {
            log.error("【群成员服务】批量添加群成员失败 - groupId:{}", groupId, e);
            throw e;
        }
    }

    /**
     * 批量插入群成员（使用MyBatis foreach批量插入）
     *
     * @param memberList 成员列表
     */
    private void batchInsert(List<ImGroupMember> memberList) {
        if (CollectionUtils.isEmpty(memberList)) {
            return;
        }

        try {
            // 使用MyBatis的批量插入（一条SQL插入所有成员）
            groupMemberMapper.batchInsert(memberList);

            log.debug("【群成员服务】批量插入成功 - count:{}", memberList.size());

        } catch (Exception e) {
            log.error("【群成员服务】批量插入失败", e);
            throw e;
        }
    }

    /**
     * 移除群成员（同时维护Redis缓存）
     *
     * @param groupId 群ID
     * @param userId  用户ID
     */
    public void removeGroupMember(String groupId, String userId) {
        try {
            // 1. 更新数据库（软删除，设置status=2）
            LambdaQueryWrapper<ImGroupMember> wrapper = Wrappers.lambdaQuery();
            wrapper.eq(ImGroupMember::getGroupId, groupId)
                    .eq(ImGroupMember::getUserId, userId);

            ImGroupMember member = new ImGroupMember();
            member.setStatus(2); // 已退出
            groupMemberMapper.update(member, wrapper);

            log.info("【群成员服务】移除群成员成功 - groupId:{}, userId:{}", groupId, userId);

            // 2. 删除Redis缓存
            deleteUserGroupsCache(userId);

        } catch (Exception e) {
            log.error("【群成员服务】移除群成员失败 - groupId:{}, userId:{}", groupId, userId, e);
            throw e;
        }
    }

    /**
     * 用户主动退出群
     *
     * @param groupId 群ID
     * @param userId  用户ID
     */
    public void quitGroup(String groupId, String userId) {
        try {
            // 1. 更新数据库
            LambdaQueryWrapper<ImGroupMember> wrapper = Wrappers.lambdaQuery();
            wrapper.eq(ImGroupMember::getGroupId, groupId)
                    .eq(ImGroupMember::getUserId, userId);

            ImGroupMember member = new ImGroupMember();
            member.setStatus(2); // 已退出
            groupMemberMapper.update(member, wrapper);

            log.info("【群成员服务】用户退群成功 - groupId:{}, userId:{}", groupId, userId);

            // 2. 删除Redis缓存
            deleteUserGroupsCache(userId);

        } catch (Exception e) {
            log.error("【群成员服务】用户退群失败 - groupId:{}, userId:{}", groupId, userId, e);
            throw e;
        }
    }

    /**
     * 写入用户群列表缓存
     *
     * @param userId   用户ID
     * @param groupIds 群ID列表
     */
    private void writeUserGroupsCache(String userId, List<String> groupIds) {
        try {
            String cacheKey = USER_GROUPS_CACHE_PREFIX + userId;

            // 写入Redis List
            redissonUtils.pushRight(cacheKey, groupIds.toArray(new String[0]));

            // 设置过期时间（30分钟）
            redissonUtils.expire(cacheKey, CACHE_TTL_SECONDS, TimeUnit.SECONDS);

            log.debug("【群成员服务】写入用户群列表缓存成功 - userId:{}, count:{}, ttl:{}s",
                    userId, groupIds.size(), CACHE_TTL_SECONDS);

        } catch (Exception e) {
            log.warn("【群成员服务】写入用户群列表缓存失败 - userId:{}", userId, e);
            // 缓存写入失败不影响主流程
        }
    }

    /**
     * 删除用户群列表缓存（用户加群/退群时调用）
     *
     * @param userId 用户ID
     */
    public void deleteUserGroupsCache(String userId) {
        try {
            String cacheKey = USER_GROUPS_CACHE_PREFIX + userId;
            boolean deleted = redissonUtils.delete(cacheKey);

            if (deleted) {
                log.info("【群成员服务】删除用户群列表缓存成功 - userId:{}", userId);
            } else {
                log.debug("【群成员服务】用户群列表缓存不存在 - userId:{}", userId);
            }

        } catch (Exception e) {
            log.warn("【群成员服务】删除用户群列表缓存失败 - userId:{}", userId, e);
            // 缓存删除失败不影响主流程
        }
    }
}
