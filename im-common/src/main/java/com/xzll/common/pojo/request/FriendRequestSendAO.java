package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 发送好友申请请求
 */
@Data
public class FriendRequestSendAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 申请人用户ID
     */
    private String fromUserId;

    /**
     * 被申请人用户ID
     */
    private String toUserId;

    /**
     * 申请备注消息
     */
    private String requestMessage;

}
