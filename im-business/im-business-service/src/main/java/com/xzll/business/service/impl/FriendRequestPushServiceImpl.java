package com.xzll.business.service.impl;

import cn.hutool.core.util.IdUtil;
import com.xzll.business.entity.mysql.ImFriendRequest;
import com.xzll.business.service.FriendRequestPushService;
import com.xzll.common.constant.ImSourceUrlConstant;
import com.xzll.common.grpc.GrpcMessageService;
import com.xzll.common.pojo.entity.ImUserDO;
import com.xzll.common.pojo.response.FriendRequestPushVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友申请推送服务实现
 */
@Service
@Slf4j
public class FriendRequestPushServiceImpl implements FriendRequestPushService {

    @Resource
    private GrpcMessageService grpcMessageService;

    @Override
    public void pushFriendRequest(ImFriendRequest friendRequest, ImUserDO fromUser) {
        log.info("推送新的好友申请，申请ID:{}, 申请人:{}, 被申请人:{}", 
                friendRequest.getRequestId(), friendRequest.getFromUserId(), friendRequest.getToUserId());

        try {
            // 构建推送消息
            FriendRequestPushVO pushVO = new FriendRequestPushVO();
            pushVO.setPushType(1); // 新的好友申请
            pushVO.setRequestId(friendRequest.getRequestId());
            pushVO.setFromUserId(friendRequest.getFromUserId());
            pushVO.setToUserId(friendRequest.getToUserId());
            pushVO.setRequestMessage(friendRequest.getRequestMessage());
            pushVO.setStatus(friendRequest.getStatus());
            pushVO.setStatusText(getStatusText(friendRequest.getStatus()));
            pushVO.setCreateTime(friendRequest.getCreateTime());
            pushVO.setUrl(ImSourceUrlConstant.Friend.FRIEND_REQUEST_PUSH);
            pushVO.setMsgId(IdUtil.simpleUUID());
            pushVO.setMsgCreateTime(System.currentTimeMillis());

            // 设置申请人信息
            if (fromUser != null) {
                pushVO.setFromUserName(fromUser.getUserFullName());
                pushVO.setFromUserAvatar(fromUser.getHeadImage());
            }

            // 设置推送内容
            String fromUserName = StringUtils.hasText(pushVO.getFromUserName()) ?
                    pushVO.getFromUserName() : pushVO.getFromUserId();
            pushVO.setPushTitle("好友申请");
            pushVO.setPushContent(fromUserName + " 请求添加您为好友");

            // 使用gRPC发送推送
            CompletableFuture<Boolean> future = grpcMessageService.sendToUserAsync(
                friendRequest.getToUserId(), pushVO, "FRIEND_REQUEST");
            
            // 异步处理结果
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("推送新的好友申请失败，申请ID:{}, 错误:{}", 
                            friendRequest.getRequestId(), throwable.getMessage(), throwable);
                } else if (success) {
                    log.info("推送新的好友申请成功，申请ID:{}", friendRequest.getRequestId());
                } else {
                    log.warn("推送新的好友申请失败，申请ID:{}, gRPC返回false", 
                            friendRequest.getRequestId());
                }
            });

        } catch (Exception e) {
            log.error("推送新的好友申请失败，申请ID:{}", friendRequest.getRequestId(), e);
        }
    }

    @Override
    public void pushFriendRequestHandleResult(ImFriendRequest friendRequest, ImUserDO fromUser, ImUserDO toUser) {
        log.info("推送好友申请处理结果，申请ID:{}, 申请人:{}, 被申请人:{}, 处理结果:{}", 
                friendRequest.getRequestId(), friendRequest.getFromUserId(), 
                friendRequest.getToUserId(), friendRequest.getStatus());

        try {
            // 构建推送消息 - 推送给申请人
            FriendRequestPushVO pushVO = new FriendRequestPushVO();
            pushVO.setPushType(2); // 好友申请处理结果
            pushVO.setRequestId(friendRequest.getRequestId());
            pushVO.setFromUserId(friendRequest.getFromUserId());
            pushVO.setToUserId(friendRequest.getToUserId());
            pushVO.setRequestMessage(friendRequest.getRequestMessage());
            pushVO.setStatus(friendRequest.getStatus());
            pushVO.setStatusText(getStatusText(friendRequest.getStatus()));
            pushVO.setHandleTime(friendRequest.getHandleTime());
            pushVO.setCreateTime(friendRequest.getCreateTime());
            pushVO.setUrl(ImSourceUrlConstant.Friend.FRIEND_REQUEST_HANDLE_PUSH);
            pushVO.setMsgId(IdUtil.simpleUUID());
            pushVO.setMsgCreateTime(System.currentTimeMillis());

            // 设置用户信息
            if (fromUser != null) {
                pushVO.setFromUserName(fromUser.getUserFullName());
                pushVO.setFromUserAvatar(fromUser.getHeadImage());
            }

            // 设置推送内容
            String toUserName = (toUser != null && StringUtils.hasText(toUser.getUserFullName())) ?
                    toUser.getUserFullName() : friendRequest.getToUserId();

            pushVO.setPushTitle("好友申请结果");
            if (friendRequest.getStatus() == 1) {
                pushVO.setPushContent(toUserName + " 同意了您的好友申请");
            } else if (friendRequest.getStatus() == 2) {
                pushVO.setPushContent(toUserName + " 拒绝了您的好友申请");
            } else {
                pushVO.setPushContent("您的好友申请状态已更新");
            }

            // 使用gRPC发送推送给申请人
            CompletableFuture<Boolean> future = grpcMessageService.sendToUserAsync(
                friendRequest.getFromUserId(), pushVO, "FRIEND_REQUEST_RESULT");
            
            // 异步处理结果
            future.whenComplete((success, throwable) -> {
                if (throwable != null) {
                    log.error("推送好友申请处理结果失败，申请ID:{}, 错误:{}", 
                            friendRequest.getRequestId(), throwable.getMessage(), throwable);
                } else if (success) {
                    log.info("推送好友申请处理结果成功，申请ID:{}", friendRequest.getRequestId());
                } else {
                    log.warn("推送好友申请处理结果失败，申请ID:{}, gRPC返回false", 
                            friendRequest.getRequestId());
                }
            });

        } catch (Exception e) {
            log.error("推送好友申请处理结果失败，申请ID:{}", friendRequest.getRequestId(), e);
        }
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
}
