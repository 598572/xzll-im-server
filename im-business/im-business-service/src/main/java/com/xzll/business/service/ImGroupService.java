package com.xzll.business.service;

import com.xzll.common.pojo.dto.GroupDTO;
import com.xzll.common.pojo.dto.GroupMemberDTO;
import com.xzll.common.pojo.request.AddGroupMemberAO;
import com.xzll.common.pojo.request.CreateGroupAO;
import com.xzll.common.pojo.request.RemoveGroupMemberAO;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群组服务接口
 */
public interface ImGroupService {

    /**
     * 创建群组
     *
     * @param request 创建群组请求
     * @return 群组ID
     */
    String createGroup(CreateGroupAO request);

    /**
     * 添加群成员
     *
     * @param request 添加群成员请求
     * @return 成功添加的成员数量
     */
    int addGroupMembers(AddGroupMemberAO request);

    /**
     * 移除群成员（踢人）
     *
     * @param request 移除群成员请求
     * @return 成功移除的成员数量
     */
    int removeGroupMembers(RemoveGroupMemberAO request);

    /**
     * 查询群组成员列表
     *
     * @param groupId 群组ID
     * @return 群成员列表
     */
    List<GroupMemberDTO> getGroupMembers(String groupId);

    /**
     * 根据群组ID查询群组信息
     *
     * @param groupId 群组ID
     * @return 群组信息DTO
     */
    GroupDTO getGroupByGroupId(String groupId);

    /**
     * 查询用户加入的所有群组
     *
     * @param userId 用户ID
     * @return 群组列表
     */
    List<GroupDTO> getGroupsByUserId(String userId);

    /**
     * 解散群组
     *
     * @param groupId 群组ID
     * @param operatorId 操作人ID（必须是群主）
     * @return true-成功，false-失败
     */
    boolean dissolveGroup(String groupId, String operatorId);

    /**
     * 退出群组
     *
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return true-成功，false-失败
     */
    boolean quitGroup(String groupId, String userId);
}
