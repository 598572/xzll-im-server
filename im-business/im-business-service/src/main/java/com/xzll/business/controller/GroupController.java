package com.xzll.business.controller;

import com.xzll.common.controller.BaseController;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.dto.GroupDTO;
import com.xzll.common.pojo.dto.GroupMemberDTO;
import com.xzll.common.pojo.request.AddGroupMemberAO;
import com.xzll.common.pojo.request.CreateGroupAO;
import com.xzll.common.pojo.request.RemoveGroupMemberAO;
import com.xzll.business.service.ImGroupService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群组管理接口
 */
@Slf4j
@RestController
@RequestMapping("/group")
@CrossOrigin
public class GroupController extends BaseController {

    @Resource
    private ImGroupService imGroupService;

    /**
     * 创建群组
     *
     * @param request 创建群组请求
     * @return 群组ID
     */
    @PostMapping("/create")
    public WebBaseResponse<String> createGroup(@RequestBody CreateGroupAO request) {
        log.info("[创建群组] 收到创建群组请求 - groupName:{}, ownerId:{}",
                request.getGroupName(), request.getOwnerId());

        try {
            // 参数校验
            if (request.getGroupName() == null || request.getGroupName().trim().isEmpty()) {
                return WebBaseResponse.returnResultError("群名称不能为空");
            }
            if (request.getOwnerId() == null || request.getOwnerId().trim().isEmpty()) {
                return WebBaseResponse.returnResultError("群主ID不能为空");
            }

            // 创建群组
            String groupId = imGroupService.createGroup(request);

            log.info("[创建群组] 群组创建成功 - groupId:{}", groupId);
            return WebBaseResponse.returnResultSuccess(groupId);

        } catch (Exception e) {
            log.error("[创建群组] 创建群组失败", e);
            return WebBaseResponse.returnResultError("创建群组失败: " + e.getMessage());
        }
    }

    /**
     * 添加群成员
     *
     * @param request 添加群成员请求
     * @return 成功添加的成员数量
     */
    @PostMapping("/members/add")
    public WebBaseResponse<Integer> addGroupMembers(@RequestBody AddGroupMemberAO request) {
        log.info("[添加群成员] 收到添加群成员请求 - groupId:{}, memberCount:{}",
                request.getGroupId(), request.getMemberIds().size());

        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId().trim().isEmpty()) {
                return WebBaseResponse.returnResultError("群组ID不能为空");
            }
            if (request.getMemberIds() == null || request.getMemberIds().isEmpty()) {
                return WebBaseResponse.returnResultError("成员ID列表不能为空");
            }

            // 添加群成员
            int count = imGroupService.addGroupMembers(request);

            log.info("[添加群成员] 添加群成员成功 - groupId:{}, count:{}", request.getGroupId(), count);
            return WebBaseResponse.returnResultSuccess(count);

        } catch (Exception e) {
            log.error("[添加群成员] 添加群成员失败", e);
            return WebBaseResponse.returnResultError("添加群成员失败: " + e.getMessage());
        }
    }

    /**
     * 查询群组信息
     *
     * @param groupId 群组ID
     * @return 群组信息
     */
    @GetMapping("/info/{groupId}")
    public WebBaseResponse<GroupDTO> getGroupInfo(@PathVariable String groupId) {
        log.info("[查询群组] 收到查询群组请求 - groupId:{}", groupId);

        try {
            GroupDTO groupDTO = imGroupService.getGroupByGroupId(groupId);

            if (groupDTO == null) {
                return WebBaseResponse.returnResultError("群组不存在");
            }

            log.info("[查询群组] 查询群组成功 - groupId:{}", groupId);
            return WebBaseResponse.returnResultSuccess(groupDTO);

        } catch (Exception e) {
            log.error("[查询群组] 查询群组失败", e);
            return WebBaseResponse.returnResultError("查询群组失败: " + e.getMessage());
        }
    }

    /**
     * 查询用户加入的所有群组
     *
     * @param userId 用户ID
     * @return 群组列表
     */
    @GetMapping("/list/{userId}")
    public WebBaseResponse<List<GroupDTO>> getUserGroups(@PathVariable String userId) {
        log.info("[查询用户群组] 收到查询用户群组请求 - userId:{}", userId);

        try {
            List<GroupDTO> groups = imGroupService.getGroupsByUserId(userId);

            log.info("[查询用户群组] 查询用户群组成功 - userId:{}, count:{}", userId, groups.size());
            return WebBaseResponse.returnResultSuccess(groups);

        } catch (Exception e) {
            log.error("[查询用户群组] 查询用户群组失败", e);
            return WebBaseResponse.returnResultError("查询用户群组失败: " + e.getMessage());
        }
    }

    /**
     * 解散群组
     *
     * @param groupId 群组ID
     * @param operatorId 操作人ID（群主）
     * @return 是否成功
     */
    @DeleteMapping("/dissolve/{groupId}")
    public WebBaseResponse<Boolean> dissolveGroup(
            @PathVariable String groupId,
            @RequestParam String operatorId) {
        log.info("[解散群组] 收到解散群组请求 - groupId:{}, operatorId:{}", groupId, operatorId);

        try {
            boolean result = imGroupService.dissolveGroup(groupId, operatorId);

            if (result) {
                log.info("[解散群组] 解散群组成功 - groupId:{}", groupId);
                return WebBaseResponse.returnResultSuccess(true);
            } else {
                log.warn("[解散群组] 解散群组失败 - groupId:{}", groupId);
                return WebBaseResponse.returnResultError("解散群组失败");
            }

        } catch (Exception e) {
            log.error("[解散群组] 解散群组失败", e);
            return WebBaseResponse.returnResultError("解散群组失败: " + e.getMessage());
        }
    }

    /**
     * 退出群组
     *
     * @param groupId 群组ID
     * @param userId 用户ID
     * @return 是否成功
     */
    @DeleteMapping("/quit/{groupId}")
    public WebBaseResponse<Boolean> quitGroup(
            @PathVariable String groupId,
            @RequestParam String userId) {
        log.info("[退出群组] 收到退出群组请求 - groupId:{}, userId:{}", groupId, userId);

        try {
            boolean result = imGroupService.quitGroup(groupId, userId);

            if (result) {
                log.info("[退出群组] 退出群组成功 - groupId:{}, userId:{}", groupId, userId);
                return WebBaseResponse.returnResultSuccess(true);
            } else {
                log.warn("[退出群组] 退出群组失败 - groupId:{}, userId:{}", groupId, userId);
                return WebBaseResponse.returnResultError("退出群组失败");
            }

        } catch (Exception e) {
            log.error("[退出群组] 退出群组失败", e);
            return WebBaseResponse.returnResultError("退出群组失败: " + e.getMessage());
        }
    }

    /**
     * 移除群成员（踢人）
     *
     * @param request 移除群成员请求
     * @return 成功移除的成员数量
     */
    @PostMapping("/members/remove")
    public WebBaseResponse<Integer> removeGroupMembers(@RequestBody RemoveGroupMemberAO request) {
        log.info("[移除群成员] 收到移除群成员请求 - groupId:{}, memberCount:{}",
                request.getGroupId(), request.getMemberIds().size());

        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId().trim().isEmpty()) {
                return WebBaseResponse.returnResultError("群组ID不能为空");
            }
            if (request.getMemberIds() == null || request.getMemberIds().isEmpty()) {
                return WebBaseResponse.returnResultError("成员ID列表不能为空");
            }
            if (request.getOperatorId() == null || request.getOperatorId().trim().isEmpty()) {
                return WebBaseResponse.returnResultError("操作人ID不能为空");
            }

            // 移除群成员
            int count = imGroupService.removeGroupMembers(request);

            log.info("[移除群成员] 移除群成员成功 - groupId:{}, count:{}", request.getGroupId(), count);
            return WebBaseResponse.returnResultSuccess(count);

        } catch (Exception e) {
            log.error("[移除群成员] 移除群成员失败", e);
            return WebBaseResponse.returnResultError("移除群成员失败: " + e.getMessage());
        }
    }

    /**
     * 查询群成员列表
     *
     * @param groupId 群组ID
     * @return 群成员列表
     */
    @GetMapping("/members/{groupId}")
    public WebBaseResponse<List<GroupMemberDTO>> getGroupMembers(@PathVariable String groupId) {
        log.info("[查询群成员] 收到查询群成员请求 - groupId:{}", groupId);

        try {
            // 参数校验
            if (groupId == null || groupId.trim().isEmpty()) {
                return WebBaseResponse.returnResultError("群组ID不能为空");
            }

            // 查询群成员列表
            List<GroupMemberDTO> members = imGroupService.getGroupMembers(groupId);

            log.info("[查询群成员] 查询群成员成功 - groupId:{}, count:{}", groupId, members.size());
            return WebBaseResponse.returnResultSuccess(members);

        } catch (Exception e) {
            log.error("[查询群成员] 查询群成员失败", e);
            return WebBaseResponse.returnResultError("查询群成员失败: " + e.getMessage());
        }
    }
}
