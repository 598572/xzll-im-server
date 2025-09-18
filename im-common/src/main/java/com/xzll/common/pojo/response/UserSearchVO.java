package com.xzll.common.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 用户搜索结果响应
 */
@Data
public class UserSearchVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 用户名（登录账号）
     */
    private String userName;

    /**
     * 用户全称（显示名称）
     */
    private String userFullName;

    /**
     * 头像
     */
    private String headImage;

    /**
     * 性别：0-女，1-男，-1-未知
     */
    private Integer sex;

    /**
     * 手机号（部分隐藏）
     */
    private String phoneHidden;

    /**
     * 邮箱（部分隐藏）
     */
    private String emailHidden;

    /**
     * 好友关系状态：0-非好友，1-已是好友，2-已发送申请待处理，3-已被拉黑
     */
    private Integer friendStatus;

    /**
     * 好友关系状态文本
     */
    private String friendStatusText;

    /**
     * 注册时间
     */
    private LocalDateTime registerTime;

    /**
     * 是否可以发送好友申请
     */
    private Boolean canSendRequest;

    /**
     * 待处理的申请ID（如果有）
     */
    private String pendingRequestId;

}
