package com.xzll.console.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026/01/23
 * @Description: 会话列表VO
 */
@Data
public class SessionVO {
    
    /**
     * 主键ID
     */
    private Long id;
    
    /**
     * 会话ID
     */
    private String chatId;
    
    /**
     * 发起会话方用户ID
     */
    private String fromUserId;
    
    /**
     * 发起会话方用户名
     */
    private String fromUserName;
    
    /**
     * 发起会话方头像
     */
    private String fromUserAvatar;
    
    /**
     * 被发起会话方用户ID
     */
    private String toUserId;
    
    /**
     * 被发起会话方用户名
     */
    private String toUserName;
    
    /**
     * 被发起会话方头像
     */
    private String toUserAvatar;
    
    /**
     * 会话类型，1单聊，2群聊
     */
    private Integer chatType;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
