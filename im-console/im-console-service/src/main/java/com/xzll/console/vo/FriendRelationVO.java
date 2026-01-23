package com.xzll.console.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 好友关系VO
 */
@Data
public class FriendRelationVO {
    
    /**
     * 记录ID
     */
    private Long id;
    
    /**
     * 用户ID
     */
    private String userId;
    
    /**
     * 用户名
     */
    private String userName;
    
    /**
     * 好友ID
     */
    private String friendId;
    
    /**
     * 好友用户名
     */
    private String friendName;
    
    /**
     * 是否拉黑: 0否, 1是
     */
    private Integer blackFlag;
    
    /**
     * 拉黑状态描述
     */
    private String blackFlagDesc;
    
    /**
     * 是否删除: 0否, 1是
     */
    private Integer delFlag;
    
    /**
     * 创建时间（成为好友时间）
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}

