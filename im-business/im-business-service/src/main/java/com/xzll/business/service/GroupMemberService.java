package com.xzll.business.service;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群成员服务接口
 */
public interface GroupMemberService {

    /**
     * 查询用户加入的所有群ID（只查询正常状态的群）
     *
     * @param userId 用户ID
     * @return 群ID列表
     */
    List<String> selectGroupIdsByUserId(String userId);

    /**
     * 查询用户加入的所有群（包含群信息）
     *
     * @param userId 用户ID
     * @return 群成员信息列表
     */
    List<com.xzll.business.entity.mysql.ImGroupMember> selectGroupsWithInfoByUserId(String userId);

    /**
     * 批量添加群成员（建群时使用）
     *
     * @param groupId   群ID
     * @param memberIds 成员ID列表
     * @param ownerId   群主ID（第一个成员，角色为群主）
     * @return 成功添加的成员数量
     */
    int batchAddGroupMembers(String groupId, List<String> memberIds, String ownerId);
}
