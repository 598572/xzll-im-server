package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.google.common.collect.Lists;
import com.xzll.business.entity.mysql.ImFriendRelation;
import com.xzll.business.entity.mysql.ImFriendRequest;
import com.xzll.business.mapper.ImFriendRelationMapper;
import com.xzll.business.mapper.ImFriendRequestMapper;
import com.xzll.business.mapper.ImUserMapper;
import com.xzll.business.service.UserSearchService;
import com.xzll.common.pojo.entity.ImUserDO;
import com.xzll.common.pojo.request.UserSearchAO;
import com.xzll.common.pojo.response.UserSearchVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 用户搜索服务实现
 */
@Service
@Slf4j
public class UserSearchServiceImpl implements UserSearchService {

    @Resource
    private ImUserMapper userMapper;

    @Resource
    private ImFriendRelationMapper friendRelationMapper;

    @Resource
    private ImFriendRequestMapper friendRequestMapper;

    @Override
    public List<UserSearchVO> searchUsers(UserSearchAO ao) {
        log.info("搜索用户_入参:{}", JSONUtil.toJsonStr(ao));

        try {
            // 1. 参数校验
            if (!StringUtils.hasText(ao.getKeyword()) || !StringUtils.hasText(ao.getCurrentUserId())) {
                log.warn("搜索用户失败，搜索关键词或当前用户ID为空");
                return Lists.newArrayList();
            }

            // 关键词长度限制，防止性能问题
            if (ao.getKeyword().length() < 2) {
                log.warn("搜索用户失败，搜索关键词太短");
                return Lists.newArrayList();
            }

            // 设置默认值
            if (ao.getCurrentPage() == null || ao.getCurrentPage() <= 0) {
                ao.setCurrentPage(1);
            }
            if (ao.getPageSize() == null || ao.getPageSize() <= 0) {
                ao.setPageSize(10);
            }
            // 限制每页最大数量，防止性能问题
            if (ao.getPageSize() > 50) {
                ao.setPageSize(50);
            }

            // 2. 搜索用户
            List<ImUserDO> users = searchUsersByKeyword(ao);
            
            if (CollectionUtils.isEmpty(users)) {
                log.info("搜索用户无结果，关键词:{}", ao.getKeyword());
                return Lists.newArrayList();
            }

            // 3. 过滤掉当前用户自己
            users = users.stream()
                    .filter(user -> !ao.getCurrentUserId().equals(user.getUserId()))
                    .collect(Collectors.toList());

            if (CollectionUtils.isEmpty(users)) {
                return Lists.newArrayList();
            }

            // 4. 获取好友关系状态
            List<String> targetUserIds = users.stream()
                    .map(ImUserDO::getUserId)
                    .collect(Collectors.toList());

            Map<String, Integer> friendStatusMap = getFriendStatusMap(ao.getCurrentUserId(), targetUserIds);
            Map<String, String> pendingRequestMap = getPendingRequestMap(ao.getCurrentUserId(), targetUserIds);

            // 5. 构建返回结果
            List<UserSearchVO> result = users.stream()
                    .map(user -> buildUserSearchVO(user, friendStatusMap, pendingRequestMap))
                    .collect(Collectors.toList());

            log.info("搜索用户成功，关键词:{}, 当前用户:{}, 返回{}条结果", 
                    ao.getKeyword(), ao.getCurrentUserId(), result.size());

            return result;

        } catch (Exception e) {
            log.error("搜索用户异常，关键词:{}, 当前用户:{}", ao.getKeyword(), ao.getCurrentUserId(), e);
            return Lists.newArrayList();
        }
    }

    /**
     * 根据关键词搜索用户
     */
    private List<ImUserDO> searchUsersByKeyword(UserSearchAO ao) {
        LambdaQueryWrapper<ImUserDO> queryWrapper = Wrappers.lambdaQuery(ImUserDO.class);

        String keyword = ao.getKeyword().trim();
        
        if (ao.getSearchType() != null && ao.getSearchType() == 1) {
            // 精确搜索
            queryWrapper.and(wrapper -> wrapper
                    .eq(ImUserDO::getUserName, keyword)
                    .or()
                    .eq(ImUserDO::getUserFullName, keyword)
                    .or()
                    .eq(ImUserDO::getPhone, keyword)
                    .or()
                    .eq(ImUserDO::getEmail, keyword)
            );
        } else {
            // 模糊搜索
            queryWrapper.and(wrapper -> wrapper
                    .like(ImUserDO::getUserName, keyword)
                    .or()
                    .like(ImUserDO::getUserFullName, keyword)
                    .or()
                    .like(ImUserDO::getPhone, keyword)
                    .or()
                    .like(ImUserDO::getEmail, keyword)
            );
        }

        // 按注册时间倒序排列
        queryWrapper.orderByDesc(ImUserDO::getRegisterTime);

        // 分页查询
        Page<ImUserDO> page = new Page<>(ao.getCurrentPage(), ao.getPageSize());
        Page<ImUserDO> resultPage = userMapper.selectPage(page, queryWrapper);

        return resultPage.getRecords();
    }

    /**
     * 获取好友关系状态映射
     */
    private Map<String, Integer> getFriendStatusMap(String currentUserId, List<String> targetUserIds) {
        if (CollectionUtils.isEmpty(targetUserIds)) {
            return Map.of();
        }

        // 查询好友关系
        LambdaQueryWrapper<ImFriendRelation> friendQuery = Wrappers.lambdaQuery(ImFriendRelation.class)
                .eq(ImFriendRelation::getUserId, currentUserId)
                .in(ImFriendRelation::getFriendId, targetUserIds)
                .eq(ImFriendRelation::getDelFlag, false);

        List<ImFriendRelation> friendRelations = friendRelationMapper.selectList(friendQuery);

        return friendRelations.stream()
                .collect(Collectors.toMap(
                        ImFriendRelation::getFriendId,
                        relation -> {
                            if (relation.getBlackFlag()) {
                                return 3; // 已被拉黑
                            } else {
                                return 1; // 已是好友
                            }
                        }
                ));
    }

    /**
     * 获取待处理的好友申请映射
     */
    private Map<String, String> getPendingRequestMap(String currentUserId, List<String> targetUserIds) {
        if (CollectionUtils.isEmpty(targetUserIds)) {
            return Map.of();
        }

        // 查询当前用户发出的待处理申请
        LambdaQueryWrapper<ImFriendRequest> requestQuery = Wrappers.lambdaQuery(ImFriendRequest.class)
                .eq(ImFriendRequest::getFromUserId, currentUserId)
                .in(ImFriendRequest::getToUserId, targetUserIds)
                .eq(ImFriendRequest::getStatus, 0); // 待处理状态

        List<ImFriendRequest> pendingRequests = friendRequestMapper.selectList(requestQuery);

        return pendingRequests.stream()
                .collect(Collectors.toMap(
                        ImFriendRequest::getToUserId,
                        ImFriendRequest::getRequestId,
                        (existing, replacement) -> existing // 如果有重复，保留第一个
                ));
    }

    /**
     * 构建用户搜索结果VO
     */
    private UserSearchVO buildUserSearchVO(ImUserDO user, Map<String, Integer> friendStatusMap, 
                                          Map<String, String> pendingRequestMap) {
        UserSearchVO vo = new UserSearchVO();
        
        vo.setUserId(user.getUserId());
        vo.setUserName(user.getUserName());
        vo.setUserFullName(user.getUserFullName());
        vo.setHeadImage(user.getHeadImage());
        vo.setSex(user.getSex());
        vo.setRegisterTime(user.getRegisterTime());

        // 手机号脱敏处理
        vo.setPhoneHidden(maskPhone(user.getPhone()));
        // 邮箱脱敏处理
        vo.setEmailHidden(maskEmail(user.getEmail()));

        // 获取好友关系状态
        String userId = user.getUserId();
        Integer friendStatus = friendStatusMap.get(userId);
        String pendingRequestId = pendingRequestMap.get(userId);

        if (friendStatus != null) {
            vo.setFriendStatus(friendStatus);
            vo.setFriendStatusText(getFriendStatusText(friendStatus));
            vo.setCanSendRequest(false); // 已是好友或已拉黑，不能再发申请
        } else if (pendingRequestId != null) {
            vo.setFriendStatus(2); // 已发送申请待处理
            vo.setFriendStatusText("待处理");
            vo.setPendingRequestId(pendingRequestId);
            vo.setCanSendRequest(false); // 已有待处理申请，不能重复发送
        } else {
            vo.setFriendStatus(0); // 非好友
            vo.setFriendStatusText("非好友");
            vo.setCanSendRequest(true); // 可以发送好友申请
        }

        return vo;
    }

    /**
     * 手机号脱敏处理
     */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() < 7) {
            return "";
        }
        // 显示前3位和后4位，中间用*替换
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 邮箱脱敏处理
     */
    private String maskEmail(String email) {
        if (!StringUtils.hasText(email) || !email.contains("@")) {
            return "";
        }
        
        String[] parts = email.split("@");
        if (parts.length != 2 || parts[0].length() < 2) {
            return "";
        }
        
        String username = parts[0];
        String domain = parts[1];
        
        // 显示前2位和@后的域名，中间用*替换
        String maskedUsername = username.substring(0, 2) + "***";
        return maskedUsername + "@" + domain;
    }

    /**
     * 获取好友状态文本
     */
    private String getFriendStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }

        switch (status) {
            case 0:
                return "非好友";
            case 1:
                return "已是好友";
            case 2:
                return "待处理";
            case 3:
                return "已拉黑";
            default:
                return "未知";
        }
    }
}
