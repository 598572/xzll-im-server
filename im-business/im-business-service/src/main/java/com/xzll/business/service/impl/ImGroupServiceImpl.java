package com.xzll.business.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.xzll.business.entity.mysql.ImGroup;
import com.xzll.business.entity.mysql.ImGroupMember;
import com.xzll.business.mapper.ImGroupMapper;
import com.xzll.business.mapper.ImGroupMemberMapper;
import com.xzll.business.service.GroupMemberService;
import com.xzll.business.service.ImGroupService;
import com.xzll.business.service.UserGroupCacheService;
import com.xzll.common.pojo.dto.GroupDTO;
import com.xzll.common.pojo.dto.GroupMemberDTO;
import com.xzll.common.pojo.request.AddGroupMemberAO;
import com.xzll.common.pojo.request.CreateGroupAO;
import com.xzll.common.pojo.request.RemoveGroupMemberAO;
import com.xzll.common.util.msgId.SnowflakeIdService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群组服务实现类
 */
@Slf4j
@Service
public class ImGroupServiceImpl implements ImGroupService {

    @Resource
    private ImGroupMapper imGroupMapper;

    @Resource
    private ImGroupMemberMapper imGroupMemberMapper;

    @Resource
    private GroupMemberService groupMemberService;

    @Resource
    private SnowflakeIdService snowflakeIdService;

    @Resource
    private UserGroupCacheService userGroupCacheService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createGroup(CreateGroupAO request) {
        log.info("[创建群组] 开始创建群组 - groupName:{}, ownerId:{}",
                request.getGroupName(), request.getOwnerId());

        // 1. 生成群组ID（使用雪花算法生成消息ID作为群ID）
        String groupId = snowflakeIdService.generateSimpleMessageId();

        // 2. 创建群组实体
        ImGroup imGroup = new ImGroup();
        imGroup.setGroupId(groupId);
        imGroup.setGroupName(request.getGroupName());
        imGroup.setGroupAvatar(request.getGroupAvatar());
        imGroup.setGroupDesc(request.getGroupDesc());
        imGroup.setOwnerId(request.getOwnerId());
        imGroup.setMaxMemberCount(request.getMaxMemberCount() != null ? request.getMaxMemberCount() : 500);
        imGroup.setCurrentCount(request.getMemberIds() != null ? request.getMemberIds().size() : 1);
        imGroup.setGroupType(request.getGroupType() != null ? request.getGroupType() : 1);
        imGroup.setStatus(1); // 正常状态
        imGroup.setCreateTime(new java.util.Date());
        imGroup.setUpdateTime(new java.util.Date());

        // 3. 保存群组信息
        imGroupMapper.insert(imGroup);

        log.info("[创建群组] 群组信息保存成功 - groupId:{}", groupId);

        // 4. 添加群成员（调用GroupMemberService）
        if (request.getMemberIds() != null && !request.getMemberIds().isEmpty()) {
            try {
                int memberCount = groupMemberService.batchAddGroupMembers(
                        groupId,
                        request.getMemberIds(),
                        request.getOwnerId()
                );

                log.info("[创建群组] 批量添加群成员成功 - groupId:{}, count:{}", groupId, memberCount);

                // 更新群组的当前成员数
                imGroup.setCurrentCount(memberCount);
                imGroup.setUpdateTime(new java.util.Date());
                imGroupMapper.updateById(imGroup);

            } catch (Exception e) {
                log.error("[创建群组] 批量添加群成员失败 - groupId:{}", groupId, e);
                // 群成员添加失败不影响群组创建，只记录日志
            }
        } else {
            log.warn("[创建群组] 成员列表为空，只创建群组不添加成员 - groupId:{}", groupId);
        }

        // 5. 更新缓存：群主加入群
        try {
            userGroupCacheService.onUserJoinGroup(request.getOwnerId(), groupId);
            log.info("[创建群组] 更新缓存成功 - ownerId:{}, groupId:{}", request.getOwnerId(), groupId);
        } catch (Exception e) {
            log.error("[创建群组] 更新缓存失败 - ownerId:{}, groupId:{}", request.getOwnerId(), groupId, e);
        }

        log.info("[创建群组] 群组创建完成 - groupId:{}", groupId);
        return groupId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int addGroupMembers(AddGroupMemberAO request) {
        log.info("[添加群成员] 开始添加群成员 - groupId:{}, memberCount:{}",
                request.getGroupId(), request.getMemberIds().size());

        // 1. 查询群组信息并验证状态
        ImGroup imGroup = imGroupMapper.selectOne(
                new LambdaQueryWrapper<ImGroup>()
                        .eq(ImGroup::getGroupId, request.getGroupId())
        );

        if (imGroup == null) {
            log.warn("[添加群成员] 群组不存在 - groupId:{}", request.getGroupId());
            throw new RuntimeException("群组不存在");
        }

        // 2. 验证群组状态（只有正常状态才能加人）
        if (imGroup.getStatus() != 1) {
            log.warn("[添加群成员] 群组状态异常，无法添加成员 - groupId:{}, status:{}",
                    request.getGroupId(), imGroup.getStatus());
            throw new RuntimeException("群组状态异常，无法添加成员");
        }

        // 3. 验证成员数量限制
        if (imGroup.getMaxMemberCount() != null && imGroup.getCurrentCount() != null) {
            int currentCount = imGroup.getCurrentCount();
            int maxCount = imGroup.getMaxMemberCount();
            int addCount = request.getMemberIds().size();

            if (currentCount + addCount > maxCount) {
                log.warn("[添加群成员] 群成员数量已达上限 - groupId:{}, current:{}, max:{}, add:{}",
                        request.getGroupId(), currentCount, maxCount, addCount);
                throw new RuntimeException("群成员数量已达上限");
            }
        }

        int successCount = 0;
        long joinTime = System.currentTimeMillis();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();

        // 4. 检查哪些成员已经在群中
        List<String> existingMembers = imGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<ImGroupMember>()
                                .eq(ImGroupMember::getGroupId, request.getGroupId())
                                .in(ImGroupMember::getUserId, request.getMemberIds())
                                .eq(ImGroupMember::getStatus, 1)
                ).stream()
                .map(ImGroupMember::getUserId)
                .collect(java.util.stream.Collectors.toList());

        if (!existingMembers.isEmpty()) {
            log.warn("[添加群成员] 部分成员已在群中，将被跳过 - groupId:{}, existingMembers:{}",
                    request.getGroupId(), existingMembers);
        }

        // 5. 过滤掉已存在的成员，只添加新成员
        List<String> newMemberIds = request.getMemberIds().stream()
                .filter(memberId -> !existingMembers.contains(memberId))
                .collect(java.util.stream.Collectors.toList());

        if (newMemberIds.isEmpty()) {
            log.warn("[添加群成员] 所有成员都已在群中，无需添加 - groupId:{}", request.getGroupId());
            return 0;
        }

        // 6. 构建所有新成员的实体列表
        List<ImGroupMember> memberList = new java.util.ArrayList<>(newMemberIds.size());
        Integer joinType = request.getJoinType() != null ? request.getJoinType() : 2;

        for (String memberId : newMemberIds) {
            ImGroupMember groupMember = new ImGroupMember();
            groupMember.setGroupId(request.getGroupId());
            groupMember.setUserId(memberId);
            groupMember.setMemberRole(3); // 默认为普通成员
            groupMember.setJoinTime(joinTime);
            groupMember.setJoinType(joinType);
            groupMember.setStatus(1); // 正常状态
            groupMember.setCreateTime(now);
            groupMember.setUpdateTime(now);
            memberList.add(groupMember);
        }

        // 7. 批量插入所有新成员
        try {
            imGroupMemberMapper.batchInsert(memberList);
            successCount = memberList.size();
            log.info("[添加群成员] 批量添加成员成功 - groupId:{}, count:{}", request.getGroupId(), successCount);
        } catch (Exception e) {
            log.error("[添加群成员] 批量添加成员失败 - groupId:{}", request.getGroupId(), e);
            throw new RuntimeException("批量添加成员失败", e);
        }

        // 更新群组的当前成员数
        if (successCount > 0) {
            try {
                // 增加成员数
                int newCount = (imGroup.getCurrentCount() != null ? imGroup.getCurrentCount() : 0) + successCount;
                imGroup.setCurrentCount(newCount);
                imGroup.setUpdateTime(new java.util.Date());
                imGroupMapper.updateById(imGroup);
                log.info("[添加群成员] 更新群成员数成功 - groupId:{}, newCount:{}",
                        request.getGroupId(), newCount);
            } catch (Exception e) {
                log.error("[添加群成员] 更新群成员数失败 - groupId:{}", request.getGroupId(), e);
                throw new RuntimeException("更新群成员数失败", e);
            }

            // 8. 更新缓存：新成员加入群
            try {
                userGroupCacheService.onBatchUserJoinGroup(newMemberIds, request.getGroupId());
                log.info("[添加群成员] 更新缓存成功 - groupId:{}, count:{}", request.getGroupId(), newMemberIds.size());
            } catch (Exception e) {
                log.error("[添加群成员] 更新缓存失败 - groupId:{}", request.getGroupId(), e);
                // 缓存更新失败不影响业务，只记录日志
            }
        }

        log.info("[添加群成员] 添加完成 - groupId:{}, successCount:{}",
                request.getGroupId(), successCount);
        return successCount;
    }

    @Override
    public GroupDTO getGroupByGroupId(String groupId) {
        log.info("[查询群组] 查询群组信息 - groupId:{}", groupId);

        LambdaQueryWrapper<ImGroup> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ImGroup::getGroupId, groupId);
        queryWrapper.eq(ImGroup::getStatus, 1); // 只查询正常状态的群

        ImGroup imGroup = imGroupMapper.selectOne(queryWrapper);

        if (imGroup == null) {
            log.warn("[查询群组] 群组不存在 - groupId:{}", groupId);
            return null;
        }

        return convertToDTO(imGroup);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int removeGroupMembers(RemoveGroupMemberAO request) {
        log.info("[移除群成员] 开始移除群成员 - groupId:{}, memberCount:{}",
                request.getGroupId(), request.getMemberIds().size());

        int successCount = 0;

        // 1. 查询群组信息
        ImGroup imGroup = imGroupMapper.selectOne(
                new LambdaQueryWrapper<ImGroup>()
                        .eq(ImGroup::getGroupId, request.getGroupId())
                        .eq(ImGroup::getStatus, 1)
        );

        if (imGroup == null) {
            log.warn("[移除群成员] 群组不存在 - groupId:{}", request.getGroupId());
            return 0;
        }

        // 2. 校验操作人权限（只有群主可以踢人）
        if (!imGroup.getOwnerId().equals(request.getOperatorId())) {
            log.warn("[移除群成员] 操作人不是群主，无权移除成员 - groupId:{}, operatorId:{}, ownerId:{}",
                    request.getGroupId(), request.getOperatorId(), imGroup.getOwnerId());
            return 0;
        }

        // 3. 移除群成员
        for (String memberId : request.getMemberIds()) {
            try {
                // 不能踢群主
                if (imGroup.getOwnerId().equals(memberId)) {
                    log.warn("[移除群成员] 不能移除群主 - groupId:{}, memberId:{}",
                            request.getGroupId(), memberId);
                    continue;
                }

                // 查询群成员记录
                LambdaQueryWrapper<ImGroupMember> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(ImGroupMember::getGroupId, request.getGroupId());
                queryWrapper.eq(ImGroupMember::getUserId, memberId);
                queryWrapper.eq(ImGroupMember::getStatus, 1);

                ImGroupMember groupMember = imGroupMemberMapper.selectOne(queryWrapper);

                if (groupMember == null) {
                    log.warn("[移除群成员] 成员不在群中 - groupId:{}, memberId:{}",
                            request.getGroupId(), memberId);
                    continue;
                }

                // 更新状态为已退出
                groupMember.setStatus(2); // 已退出
                groupMember.setUpdateTime(java.time.LocalDateTime.now());
                int updateResult = imGroupMemberMapper.updateById(groupMember);

                if (updateResult > 0) {
                    successCount++;
                    log.info("[移除群成员] 成功移除成员 - groupId:{}, memberId:{}",
                            request.getGroupId(), memberId);
                }

            } catch (Exception e) {
                log.error("[移除群成员] 移除成员失败 - groupId:{}, memberId:{}",
                        request.getGroupId(), memberId, e);
            }
        }

        // 4. 更新群组的当前成员数
        if (successCount > 0) {
            int newCount = imGroup.getCurrentCount() - successCount;
            imGroup.setCurrentCount(newCount >= 0 ? newCount : 0);
            imGroup.setUpdateTime(new java.util.Date());
            imGroupMapper.updateById(imGroup);

            // 5. 更新缓存：被踢成员退出群
            try {
                userGroupCacheService.onBatchUserQuitGroup(request.getMemberIds(), request.getGroupId());
                log.info("[移除群成员] 更新缓存成功 - groupId:{}, count:{}", request.getGroupId(), request.getMemberIds().size());
            } catch (Exception e) {
                log.error("[移除群成员] 更新缓存失败 - groupId:{}", request.getGroupId(), e);
                // 缓存更新失败不影响业务，只记录日志
            }
        }

        log.info("[移除群成员] 移除完成 - groupId:{}, successCount:{}", request.getGroupId(), successCount);
        return successCount;
    }

    @Override
    public List<GroupMemberDTO> getGroupMembers(String groupId) {
        log.info("[查询群成员] 查询群成员列表 - groupId:{}", groupId);

        // 查询群成员列表
        List<ImGroupMember> members = imGroupMemberMapper.selectMembersByGroupId(groupId);

        if (members == null || members.isEmpty()) {
            log.warn("[查询群成员] 群成员列表为空 - groupId:{}", groupId);
            return new ArrayList<>();
        }

        // 转换为DTO
        List<GroupMemberDTO> dtoList = members.stream()
                .map(this::convertToMemberDTO)
                .collect(Collectors.toList());

        log.info("[查询群成员] 查询完成 - groupId:{}, count:{}", groupId, dtoList.size());
        return dtoList;
    }

    @Override
    public List<GroupDTO> getGroupsByUserId(String userId) {
        log.info("[查询用户群组] 查询用户加入的所有群组 - userId:{}", userId);

        // TODO: 通过群成员表查询用户加入的群组ID列表
        // 然后批量查询群组信息
        // 暂时返回空列表

        return new ArrayList<>();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean dissolveGroup(String groupId, String operatorId) {
        log.info("[解散群组] 开始解散群组 - groupId:{}, operatorId:{}", groupId, operatorId);

        // 1. 查询群组信息
        ImGroup imGroup = imGroupMapper.selectOne(
                new LambdaQueryWrapper<ImGroup>()
                        .eq(ImGroup::getGroupId, groupId)
                        .eq(ImGroup::getStatus, 1)
        );

        if (imGroup == null) {
            log.warn("[解散群组] 群组不存在 - groupId:{}", groupId);
            return false;
        }

        // 2. 校验操作人是否为群主
        if (!imGroup.getOwnerId().equals(operatorId)) {
            log.warn("[解散群组] 操作人不是群主，无权解散 - groupId:{}, operatorId:{}, ownerId:{}",
                    groupId, operatorId, imGroup.getOwnerId());
            return false;
        }

        // 3. 更新群组状态为已解散
        imGroup.setStatus(2); // 已解散
        imGroup.setUpdateTime(new java.util.Date());
        int updateResult = imGroupMapper.updateById(imGroup);

        if (updateResult > 0) {
            log.info("[解散群组] 群组解散成功 - groupId:{}", groupId);

            // 4. 更新缓存：清除所有成员的群列表
            try {
                // 查询所有成员
                List<ImGroupMember> allMembers = imGroupMemberMapper.selectList(
                        new LambdaQueryWrapper<ImGroupMember>()
                                .eq(ImGroupMember::getGroupId, groupId)
                                .eq(ImGroupMember::getStatus, 1)
                );

                if (allMembers != null && !allMembers.isEmpty()) {
                    List<String> allMemberIds = allMembers.stream()
                            .map(ImGroupMember::getUserId)
                            .collect(Collectors.toList());

                    userGroupCacheService.onGroupDissolve(groupId, allMemberIds);
                    log.info("[解散群组] 更新缓存成功 - groupId:{}, memberCount:{}", groupId, allMemberIds.size());
                }
            } catch (Exception e) {
                log.error("[解散群组] 更新缓存失败 - groupId:{}", groupId, e);
                // 缓存更新失败不影响业务，只记录日志
            }

            return true;
        } else {
            log.error("[解散群组] 群组解散失败 - groupId:{}", groupId);
            return false;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean quitGroup(String groupId, String userId) {
        log.info("[退出群组] 用户退出群组 - groupId:{}, userId:{}", groupId, userId);

        // 1. 查询群组信息
        ImGroup imGroup = imGroupMapper.selectOne(
                new LambdaQueryWrapper<ImGroup>()
                        .eq(ImGroup::getGroupId, groupId)
                        .eq(ImGroup::getStatus, 1)
        );

        if (imGroup == null) {
            log.warn("[退出群组] 群组不存在 - groupId:{}", groupId);
            return false;
        }

        // 2. 校验是否是群主（群主不能直接退出）
        if (imGroup.getOwnerId().equals(userId)) {
            log.warn("[退出群组] 群主不能直接退出，需要先转让群主或解散群组 - groupId:{}, userId:{}",
                    groupId, userId);
            return false;
        }

        // 3. 查询群成员记录
        ImGroupMember groupMember = imGroupMemberMapper.selectOne(
                new LambdaQueryWrapper<ImGroupMember>()
                        .eq(ImGroupMember::getGroupId, groupId)
                        .eq(ImGroupMember::getUserId, userId)
                        .eq(ImGroupMember::getStatus, 1)
        );

        if (groupMember == null) {
            log.warn("[退出群组] 用户不在群中 - groupId:{}, userId:{}", groupId, userId);
            return false;
        }

        // 4. 更新群成员状态为已退出
        groupMember.setStatus(2); // 已退出
        groupMember.setUpdateTime(java.time.LocalDateTime.now());
        int updateResult = imGroupMemberMapper.updateById(groupMember);

        if (updateResult > 0) {
            log.info("[退出群组] 退出群组成功 - groupId:{}, userId:{}", groupId, userId);

            // 5. 更新群组的当前成员数
            int newCount = imGroup.getCurrentCount() - 1;
            imGroup.setCurrentCount(newCount >= 0 ? newCount : 0);
            imGroup.setUpdateTime(new java.util.Date());
            imGroupMapper.updateById(imGroup);

            // 6. 更新缓存：用户退出群
            try {
                userGroupCacheService.onUserQuitGroup(userId, groupId);
                log.info("[退出群组] 更新缓存成功 - userId:{}, groupId:{}", userId, groupId);
            } catch (Exception e) {
                log.error("[退出群组] 更新缓存失败 - userId:{}, groupId:{}", userId, groupId, e);
                // 缓存更新失败不影响业务，只记录日志
            }

            return true;
        } else {
            log.error("[退出群组] 退出群组失败 - groupId:{}, userId:{}", groupId, userId);
            return false;
        }
    }

    /**
     * 实体类转DTO
     */
    private GroupDTO convertToDTO(ImGroup imGroup) {
        GroupDTO dto = new GroupDTO();
        BeanUtils.copyProperties(imGroup, dto);
        return dto;
    }

    /**
     * 群成员实体类转DTO
     */
    private GroupMemberDTO convertToMemberDTO(ImGroupMember member) {
        GroupMemberDTO dto = new GroupMemberDTO();
        dto.setUserId(member.getUserId());
        dto.setGroupId(member.getGroupId());
        dto.setMemberRole(member.getMemberRole());
        dto.setNickname(member.getNickname());
        dto.setJoinTime(member.getJoinTime());
        dto.setJoinType(member.getJoinType());
        dto.setStatus(member.getStatus());
        dto.setCreateTime(member.getCreateTime());
        return dto;
    }

    /**
     * 批量实体类转DTO
     */
    private List<GroupDTO> convertToDTOList(List<ImGroup> imGroups) {
        return imGroups.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
}
