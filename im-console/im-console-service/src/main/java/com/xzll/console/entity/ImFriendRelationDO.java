package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026/01/20
 * @Description: 好友关系实体类
 */
@Data
@TableName("im_friend_relation")
public class ImFriendRelationDO {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    /**
     * 我的用户id
     */
    private String userId;
    
    /**
     * 好友用户id
     */
    private String friendId;
    
    /**
     * 0否（未拉黑），1是（拉黑）
     */
    private Integer blackFlag;
    
    /**
     * 0否（未删除），1是（已删除）
     */
    private Integer delFlag;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
