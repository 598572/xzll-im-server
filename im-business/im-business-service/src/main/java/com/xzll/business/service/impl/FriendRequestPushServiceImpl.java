package com.xzll.business.service.impl;

import com.xzll.business.entity.mysql.ImFriendRequest;
import com.xzll.business.service.FriendRequestPushService;
import com.xzll.common.grpc.GrpcMessageService;
import com.xzll.common.util.ProtoConverterUtil;
import com.xzll.common.pojo.entity.ImUserDO;
import com.xzll.grpc.FriendRequestPush;
import com.xzll.grpc.FriendResponsePush;
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
            // 构建申请人信息
            String fromUserName = fromUser != null && StringUtils.hasText(fromUser.getUserFullName()) ?
                    fromUser.getUserFullName() : friendRequest.getFromUserId();
            String fromUserAvatar = fromUser != null && org.apache.commons.lang3.StringUtils.isNotBlank(fromUser.getHeadImage()) ? fromUser.getHeadImage() : org.apache.commons.lang3.StringUtils.EMPTY;
            
            // 构建 Protobuf Push 消息（优化后：string -> fixed64）
            FriendRequestPush push = FriendRequestPush.newBuilder()
                    .setToUserId(ProtoConverterUtil.snowflakeStringToLong(friendRequest.getToUserId())) // string -> fixed64
                    .setRequestId(ProtoConverterUtil.snowflakeStringToLong(friendRequest.getRequestId())) // string -> fixed64
                    .setFromUserId(ProtoConverterUtil.snowflakeStringToLong(friendRequest.getFromUserId())) // string -> fixed64
                    .setFromUserName(fromUserName)
                    .setFromUserAvatar(fromUserAvatar)
                    .setRequestMessage(friendRequest.getRequestMessage() != null ? friendRequest.getRequestMessage() : "")
                    .setStatus(friendRequest.getStatus())
                    .setCreateTime(friendRequest.getCreateTime() != null ? 
                            friendRequest.getCreateTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() 
                            : System.currentTimeMillis())
                    .setPushTitle("好友申请")
                    .setPushContent(fromUserName + " 请求添加您为好友")
                    .build();

            // 使用gRPC发送推送
            CompletableFuture<Boolean> future = grpcMessageService.pushFriendRequest(push);
            
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
            // 构建响应人信息（处理申请的人）
            String toUserName = (toUser != null && StringUtils.hasText(toUser.getUserFullName())) ?
                    toUser.getUserFullName() : friendRequest.getToUserId();
            String toUserAvatar = toUser != null && org.apache.commons.lang3.StringUtils.isNotBlank(toUser.getHeadImage()) ? toUser.getHeadImage() : "";
            
            // 构建推送内容
            String pushContent;
            if (friendRequest.getStatus() == 1) {
                pushContent = toUserName + " 同意了您的好友申请";
            } else if (friendRequest.getStatus() == 2) {
                pushContent = toUserName + " 拒绝了您的好友申请";
            } else {
                pushContent = "您的好友申请状态已更新";
            }
            
            // 构建 Protobuf Push 消息（推送给申请人，优化后：string -> fixed64）
            FriendResponsePush push = FriendResponsePush.newBuilder()
                    .setToUserId(ProtoConverterUtil.snowflakeStringToLong(friendRequest.getFromUserId()))  // 接收人是原申请人（string -> fixed64）
                    .setRequestId(ProtoConverterUtil.snowflakeStringToLong(friendRequest.getRequestId())) // string -> fixed64
                    .setFromUserId(ProtoConverterUtil.snowflakeStringToLong(friendRequest.getToUserId()))  // 响应人是原接收人（string -> fixed64）
                    .setFromUserName(toUserName)
                    .setFromUserAvatar(toUserAvatar)
                    .setStatus(friendRequest.getStatus())
                    .setResponseTime(friendRequest.getHandleTime() != null ? 
                            friendRequest.getHandleTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() 
                            : System.currentTimeMillis())
                    .setPushTitle("好友申请结果")
                    .setPushContent(pushContent)
                    .build();

            // 使用gRPC发送推送给申请人
            CompletableFuture<Boolean> future = grpcMessageService.pushFriendResponse(push);
            
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
