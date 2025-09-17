package com.xzll.common.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友申请信息响应
 */
@Data
public class FriendRequestVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 申请ID
     */
    private String requestId;

    /**
     * 申请人用户ID
     */
    private String fromUserId;

    /**
     * 申请人用户名
     */
    private String fromUserName;

    /**
     * 申请人头像
     */
    private String fromUserAvatar;

    /**
     * 被申请人用户ID
     */
    private String toUserId;

    /**
     * 申请备注消息
     */
    private String requestMessage;

    /**
     * 申请状态：0-待处理，1-已同意，2-已拒绝，3-已过期
     */
    private Integer status;

    /**
     * 申请状态文本
     */
    private String statusText;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

}
