package com.xzll.business.service.impl;

import cn.hutool.core.util.IdUtil;
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
import com.xzll.business.service.ImFriendService;
import com.xzll.business.service.FriendRequestPushService;
import com.xzll.common.pojo.entity.ImUserDO;
import com.xzll.common.pojo.request.*;
import com.xzll.common.pojo.response.FriendInfoVO;
import com.xzll.common.pojo.response.FriendRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友服务实现类
 */
@Service
@Slf4j
public class ImFriendServiceImpl implements ImFriendService {

    @Resource
    private ImFriendRequestMapper friendRequestMapper;

    @Resource
    private ImFriendRelationMapper friendRelationMapper;

    @Resource
    private ImUserMapper userMapper;

    @Resource
    private FriendRequestPushService friendRequestPushService;

    // 文件服务基础URL配置
    @Value("${minio.file.upload.base-url:http://localhost:8080/im-business/api/file/}")
    private String fileBaseUrl;

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public String sendFriendRequest(FriendRequestSendAO ao) {
        log.info("发送好友申请_入参:{}", JSONUtil.toJsonStr(ao));

        try {
            // 1. 参数校验
            if (!StringUtils.hasText(ao.getFromUserId()) || !StringUtils.hasText(ao.getToUserId())) {
                log.error("发送好友申请失败，用户ID不能为空");
                throw new IllegalArgumentException("用户ID不能为空");
            }

            if (ao.getFromUserId().equals(ao.getToUserId())) {
                log.error("发送好友申请失败，不能向自己发送好友申请");
                throw new IllegalArgumentException("不能向自己发送好友申请");
            }

            // 2. 检查是否已经是好友
            if (isAlreadyFriend(ao.getFromUserId(), ao.getToUserId())) {
                log.error("发送好友申请失败，{}和{}已经是好友关系", ao.getFromUserId(), ao.getToUserId());
                throw new IllegalArgumentException("已经是好友关系，无需重复申请");
            }

            // 3. 检查是否已有待处理的申请
            ImFriendRequest existingRequest = getExistingPendingRequest(ao.getFromUserId(), ao.getToUserId());
            if (existingRequest != null) {
                log.error("发送好友申请失败，{}向{}的好友申请已存在且待处理", ao.getFromUserId(), ao.getToUserId());
                throw new IllegalArgumentException("已有待处理的好友申请，请勿重复发送");
            }

            // 4. 创建好友申请记录
            String requestId = java.util.UUID.randomUUID().toString(); // 使用标准UUID格式（带横杠）
            ImFriendRequest friendRequest = new ImFriendRequest();
            friendRequest.setRequestId(requestId);
            friendRequest.setFromUserId(ao.getFromUserId());
            friendRequest.setToUserId(ao.getToUserId());
            friendRequest.setRequestMessage(ao.getRequestMessage() != null ? ao.getRequestMessage() : "");
            friendRequest.setStatus(0); // 0-待处理
            friendRequest.setCreateTime(LocalDateTime.now());
            friendRequest.setUpdateTime(LocalDateTime.now());

            int insertResult = friendRequestMapper.insert(friendRequest);
            
            if (insertResult > 0) {
                log.info("发送好友申请成功，申请ID:{}, 从{}到{}", requestId, ao.getFromUserId(), ao.getToUserId());
                
                // 推送好友申请消息
                try {
                    ImUserDO fromUser = getUserById(ao.getFromUserId());
                    friendRequestPushService.pushFriendRequest(friendRequest, fromUser);
                } catch (Exception e) {
                    log.error("推送好友申请消息失败，申请ID:{}", requestId, e);
                    // 推送失败不影响主流程
                }
                
                return requestId;
            } else {
                log.error("发送好友申请失败，数据库插入失败");
                throw new RuntimeException("发送好友申请失败");
            }

        } catch (Exception e) {
            log.error("发送好友申请异常", e);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean handleFriendRequest(FriendRequestHandleAO ao) {
        log.info("处理好友申请_入参:{}", JSONUtil.toJsonStr(ao));

        try {
            // 1. 参数校验
            if (!StringUtils.hasText(ao.getRequestId()) || !StringUtils.hasText(ao.getUserId())) {
                log.error("处理好友申请失败，申请ID或用户ID不能为空");
                throw new IllegalArgumentException("申请ID或用户ID不能为空");
            }

            if (ao.getHandleResult() == null || (ao.getHandleResult() != 1 && ao.getHandleResult() != 2)) {
                log.error("处理好友申请失败，处理结果只能是1(同意)或2(拒绝)");
                throw new IllegalArgumentException("处理结果只能是1(同意)或2(拒绝)");
            }

            // 2. 查询好友申请记录
            LambdaQueryWrapper<ImFriendRequest> queryWrapper = Wrappers.lambdaQuery(ImFriendRequest.class)
                    .eq(ImFriendRequest::getRequestId, ao.getRequestId());
            ImFriendRequest friendRequest = friendRequestMapper.selectOne(queryWrapper);

            if (friendRequest == null) {
                log.error("处理好友申请失败，申请记录不存在，申请ID:{}", ao.getRequestId());
                throw new IllegalArgumentException("好友申请记录不存在");
            }

            // 3. 验证操作权限（只有被申请人可以处理）
            if (!friendRequest.getToUserId().equals(ao.getUserId())) {
                log.error("处理好友申请失败，{}无权处理申请{}", ao.getUserId(), ao.getRequestId());
                throw new IllegalArgumentException("您无权处理此好友申请");
            }

            // 4. 检查申请状态
            if (friendRequest.getStatus() != 0) {
                log.error("处理好友申请失败，申请已被处理，当前状态:{}", friendRequest.getStatus());
                throw new IllegalArgumentException("该好友申请已被处理");
            }

            // 5. 更新申请状态
            friendRequest.setStatus(ao.getHandleResult());
            friendRequest.setHandleTime(LocalDateTime.now());
            friendRequest.setUpdateTime(LocalDateTime.now());

            int updateResult = friendRequestMapper.updateById(friendRequest);
            if (updateResult <= 0) {
                log.error("处理好友申请失败，更新申请状态失败");
                throw new RuntimeException("处理好友申请失败");
            }

            // 6. 如果同意申请，建立好友关系
            if (ao.getHandleResult() == 1) {
                boolean createResult = createFriendRelation(friendRequest.getFromUserId(), friendRequest.getToUserId());
                if (!createResult) {
                    log.error("处理好友申请失败，建立好友关系失败");
                    throw new RuntimeException("建立好友关系失败");
                }
                log.info("好友申请处理成功，{}和{}成为好友", friendRequest.getFromUserId(), friendRequest.getToUserId());
            } else {
                log.info("好友申请被拒绝，申请ID:{}", ao.getRequestId());
            }

            // 7. 推送处理结果给申请人
            try {
                ImUserDO fromUser = getUserById(friendRequest.getFromUserId());
                ImUserDO toUser = getUserById(friendRequest.getToUserId());
                friendRequestPushService.pushFriendRequestHandleResult(friendRequest, fromUser, toUser);
            } catch (Exception e) {
                log.error("推送好友申请处理结果失败，申请ID:{}", ao.getRequestId(), e);
                // 推送失败不影响主流程
            }

            return true;

        } catch (Exception e) {
            log.error("处理好友申请异常", e);
            throw e;
        }
    }

    @Override
    public List<FriendRequestVO> findFriendRequestList(FriendRequestListAO ao) {
        log.info("查询好友申请列表_入参:{}", JSONUtil.toJsonStr(ao));

        try {
            // 1. 参数校验和设置默认值
            if (!StringUtils.hasText(ao.getUserId())) {
                log.error("查询好友申请列表失败，用户ID不能为空");
                throw new IllegalArgumentException("用户ID不能为空");
            }

            if (ao.getCurrentPage() == null || ao.getCurrentPage() <= 0) {
                ao.setCurrentPage(1);
            }
            if (ao.getPageSize() == null || ao.getPageSize() <= 0) {
                ao.setPageSize(20);
            }
            if (ao.getPageSize() > 100) {
                ao.setPageSize(100);
            }

            // 2. 构建查询条件
            LambdaQueryWrapper<ImFriendRequest> queryWrapper = Wrappers.lambdaQuery(ImFriendRequest.class);
            
            if (ao.getRequestType() == 1) {
                // 我发出的申请
                queryWrapper.eq(ImFriendRequest::getFromUserId, ao.getUserId());
            } else {
                // 我收到的申请（默认）
                queryWrapper.eq(ImFriendRequest::getToUserId, ao.getUserId());
            }

            queryWrapper.orderByDesc(ImFriendRequest::getCreateTime);

            // 3. 分页查询
            Page<ImFriendRequest> page = new Page<>(ao.getCurrentPage(), ao.getPageSize());
            Page<ImFriendRequest> resultPage = friendRequestMapper.selectPage(page, queryWrapper);

            if (CollectionUtils.isEmpty(resultPage.getRecords())) {
                log.info("用户{}的好友申请列表为空", ao.getUserId());
                return Lists.newArrayList();
            }

            // 4. 获取相关用户信息
            List<String> userIds = Lists.newArrayList();
            for (ImFriendRequest request : resultPage.getRecords()) {
                userIds.add(request.getFromUserId());
                userIds.add(request.getToUserId());
            }

            List<ImUserDO> userList = getUsersByIds(userIds);
            Map<String, ImUserDO> userMap = userList.stream()
                    .collect(Collectors.toMap(ImUserDO::getUserId, user -> user));

            // 5. 组装返回结果
            List<FriendRequestVO> result = resultPage.getRecords().stream()
                    .map(request -> {
                        FriendRequestVO vo = new FriendRequestVO();
                        vo.setRequestId(request.getRequestId());
                        vo.setFromUserId(request.getFromUserId());
                        vo.setToUserId(request.getToUserId());
                        vo.setRequestMessage(request.getRequestMessage());
                        vo.setStatus(request.getStatus());
                        vo.setStatusText(getStatusText(request.getStatus()));
                        vo.setHandleTime(request.getHandleTime());
                        vo.setCreateTime(request.getCreateTime());

                        // 设置申请人信息
                        ImUserDO fromUser = userMap.get(request.getFromUserId());
                        if (fromUser != null) {
                            vo.setFromUserName(fromUser.getUserFullName());
                            vo.setFromUserAvatar(fromUser.getHeadImage());
                        }

                        return vo;
                    })
                    .collect(Collectors.toList());

            log.info("查询好友申请列表成功，用户ID:{}, 返回{}条记录", ao.getUserId(), result.size());
            return result;

        } catch (Exception e) {
            log.error("查询好友申请列表异常", e);
            throw e;
        }
    }

    @Override
    public List<FriendInfoVO> findFriendList(FriendListAO ao) {
        log.info("查询好友列表_入参:{}", JSONUtil.toJsonStr(ao));

        try {
            // 1. 参数校验和设置默认值
            if (!StringUtils.hasText(ao.getUserId())) {
                log.error("查询好友列表失败，用户ID不能为空");
                throw new IllegalArgumentException("用户ID不能为空");
            }

            if (ao.getCurrentPage() == null || ao.getCurrentPage() <= 0) {
                ao.setCurrentPage(1);
            }
            if (ao.getPageSize() == null || ao.getPageSize() <= 0) {
                ao.setPageSize(20);
            }
            if (ao.getPageSize() > 100) {
                ao.setPageSize(100);
            }

            // 2. 查询好友关系
            LambdaQueryWrapper<ImFriendRelation> queryWrapper = Wrappers.lambdaQuery(ImFriendRelation.class)
                    .eq(ImFriendRelation::getUserId, ao.getUserId())
                    .eq(ImFriendRelation::getDelFlag, false) // 未删除
                    .orderByDesc(ImFriendRelation::getCreateTime);

            Page<ImFriendRelation> page = new Page<>(ao.getCurrentPage(), ao.getPageSize());
            Page<ImFriendRelation> resultPage = friendRelationMapper.selectPage(page, queryWrapper);

            if (CollectionUtils.isEmpty(resultPage.getRecords())) {
                log.info("用户{}的好友列表为空", ao.getUserId());
                return Lists.newArrayList();
            }

            // 3. 获取好友用户信息
            List<String> friendIds = resultPage.getRecords().stream()
                    .map(ImFriendRelation::getFriendId)
                    .collect(Collectors.toList());

            List<ImUserDO> friendUsers = getUsersByIds(friendIds);
            Map<String, ImUserDO> userMap = friendUsers.stream()
                    .collect(Collectors.toMap(ImUserDO::getUserId, user -> user));

            // 4. 组装返回结果
            List<FriendInfoVO> result = resultPage.getRecords().stream()
                    .map(relation -> {
                        FriendInfoVO vo = new FriendInfoVO();
                        vo.setFriendId(relation.getFriendId());
                        vo.setBlackFlag(relation.getBlackFlag());
                        vo.setCreateTime(relation.getCreateTime());

                        // 设置好友用户信息
                        ImUserDO friendUser = userMap.get(relation.getFriendId());
                        if (friendUser != null) {
                            vo.setFriendName(friendUser.getUserName());
                            vo.setFriendFullName(friendUser.getUserFullName());
                            
                            // 转换头像路径为完整访问URL（短链接）
                            if (StringUtils.hasText(friendUser.getHeadImage())) {
                                String shortCode = generateShortCode(friendUser.getHeadImage());
                                String fullAvatarUrl = fileBaseUrl + "s/" + shortCode;
                                vo.setFriendAvatar(fullAvatarUrl);
                                log.debug("好友头像URL转换 - 用户ID: {}, 原路径: {}, 短码: {}, 完整URL: {}", 
                                    friendUser.getUserId(), friendUser.getHeadImage(), shortCode, fullAvatarUrl);
                            } else {
                                vo.setFriendAvatar(null);
                            }
                            
                            vo.setFriendSex(friendUser.getSex());
                        }

                        return vo;
                    })
                    .collect(Collectors.toList());

            log.info("查询好友列表成功，用户ID:{}, 返回{}条记录", ao.getUserId(), result.size());
            return result;

        } catch (Exception e) {
            log.error("查询好友列表异常", e);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean deleteFriend(String userId, String friendId) {
        log.info("删除好友，用户ID:{}, 好友ID:{}", userId, friendId);

        try {
            // 删除双向好友关系
            boolean result1 = deleteFriendRelation(userId, friendId);
            boolean result2 = deleteFriendRelation(friendId, userId);

            boolean success = result1 && result2;
            if (success) {
                log.info("删除好友成功，用户ID:{}, 好友ID:{}", userId, friendId);
            } else {
                log.error("删除好友失败，用户ID:{}, 好友ID:{}", userId, friendId);
            }

            return success;

        } catch (Exception e) {
            log.error("删除好友异常", e);
            throw e;
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public boolean blockFriend(String userId, String friendId, boolean blackFlag) {
        log.info("{}好友，用户ID:{}, 好友ID:{}", blackFlag ? "拉黑" : "取消拉黑", userId, friendId);

        try {
            LambdaQueryWrapper<ImFriendRelation> queryWrapper = Wrappers.lambdaQuery(ImFriendRelation.class)
                    .eq(ImFriendRelation::getUserId, userId)
                    .eq(ImFriendRelation::getFriendId, friendId)
                    .eq(ImFriendRelation::getDelFlag, false);

            ImFriendRelation relation = friendRelationMapper.selectOne(queryWrapper);
            if (relation == null) {
                log.error("{}好友失败，好友关系不存在", blackFlag ? "拉黑" : "取消拉黑");
                throw new IllegalArgumentException("好友关系不存在");
            }

            relation.setBlackFlag(blackFlag);
            relation.setUpdateTime(LocalDateTime.now());

            int updateResult = friendRelationMapper.updateById(relation);
            boolean success = updateResult > 0;

            if (success) {
                log.info("{}好友成功，用户ID:{}, 好友ID:{}", blackFlag ? "拉黑" : "取消拉黑", userId, friendId);
            } else {
                log.error("{}好友失败，用户ID:{}, 好友ID:{}", blackFlag ? "拉黑" : "取消拉黑", userId, friendId);
            }

            return success;

        } catch (Exception e) {
            log.error("{}好友异常", blackFlag ? "拉黑" : "取消拉黑", e);
            throw e;
        }
    }

    /**
     * 检查是否已经是好友
     */
    private boolean isAlreadyFriend(String userId, String friendId) {
        LambdaQueryWrapper<ImFriendRelation> queryWrapper = Wrappers.lambdaQuery(ImFriendRelation.class)
                .eq(ImFriendRelation::getUserId, userId)
                .eq(ImFriendRelation::getFriendId, friendId)
                .eq(ImFriendRelation::getDelFlag, false);

        return friendRelationMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 获取已存在的待处理申请
     */
    private ImFriendRequest getExistingPendingRequest(String fromUserId, String toUserId) {
        LambdaQueryWrapper<ImFriendRequest> queryWrapper = Wrappers.lambdaQuery(ImFriendRequest.class)
                .eq(ImFriendRequest::getFromUserId, fromUserId)
                .eq(ImFriendRequest::getToUserId, toUserId)
                .eq(ImFriendRequest::getStatus, 0); // 待处理状态

        return friendRequestMapper.selectOne(queryWrapper);
    }

    /**
     * 创建双向好友关系
     */
    private boolean createFriendRelation(String userId1, String userId2) {
        try {
            // 创建 userId1 -> userId2 的关系
            ImFriendRelation relation1 = new ImFriendRelation();
            relation1.setUserId(userId1);
            relation1.setFriendId(userId2);
            relation1.setBlackFlag(false);
            relation1.setDelFlag(false);
            relation1.setCreateTime(LocalDateTime.now());
            relation1.setUpdateTime(LocalDateTime.now());

            // 创建 userId2 -> userId1 的关系
            ImFriendRelation relation2 = new ImFriendRelation();
            relation2.setUserId(userId2);
            relation2.setFriendId(userId1);
            relation2.setBlackFlag(false);
            relation2.setDelFlag(false);
            relation2.setCreateTime(LocalDateTime.now());
            relation2.setUpdateTime(LocalDateTime.now());

            int insert1 = friendRelationMapper.insert(relation1);
            int insert2 = friendRelationMapper.insert(relation2);

            return insert1 > 0 && insert2 > 0;

        } catch (Exception e) {
            log.error("创建好友关系失败，用户1:{}, 用户2:{}", userId1, userId2, e);
            return false;
        }
    }

    /**
     * 删除好友关系（标记删除）
     */
    private boolean deleteFriendRelation(String userId, String friendId) {
        try {
            LambdaQueryWrapper<ImFriendRelation> queryWrapper = Wrappers.lambdaQuery(ImFriendRelation.class)
                    .eq(ImFriendRelation::getUserId, userId)
                    .eq(ImFriendRelation::getFriendId, friendId);

            ImFriendRelation relation = friendRelationMapper.selectOne(queryWrapper);
            if (relation != null) {
                relation.setDelFlag(true);
                relation.setUpdateTime(LocalDateTime.now());
                return friendRelationMapper.updateById(relation) > 0;
            }

            return true; // 关系不存在也认为删除成功

        } catch (Exception e) {
            log.error("删除好友关系失败，用户ID:{}, 好友ID:{}", userId, friendId, e);
            return false;
        }
    }

    /**
     * 根据用户ID列表获取用户信息
     */
    private List<ImUserDO> getUsersByIds(List<String> userIds) {
        if (CollectionUtils.isEmpty(userIds)) {
            return Lists.newArrayList();
        }

        // 去重
        List<String> distinctUserIds = userIds.stream().distinct().collect(Collectors.toList());

        LambdaQueryWrapper<ImUserDO> queryWrapper = Wrappers.lambdaQuery(ImUserDO.class)
                .in(ImUserDO::getUserId, distinctUserIds);

        return userMapper.selectList(queryWrapper);
    }

    /**
     * 根据用户ID获取用户信息
     */
    private ImUserDO getUserById(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }

        LambdaQueryWrapper<ImUserDO> queryWrapper = Wrappers.lambdaQuery(ImUserDO.class)
                .eq(ImUserDO::getUserId, userId);

        return userMapper.selectOne(queryWrapper);
    }

    /**
     * 获取状态文本
     */
    private String getStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }

        switch (status) {
            case 0:
                return "待处理";
            case 1:
                return "已同意";
            case 2:
                return "已拒绝";
            case 3:
                return "已过期";
            default:
                return "未知";
        }
    }

    /**
     * 生成短码（Base64编码文件路径）
     */
    private String generateShortCode(String filePath) {
        return java.util.Base64.getEncoder().encodeToString(filePath.getBytes());
    }
}
