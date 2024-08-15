package com.xzll.business.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/06/03 08:11:39
 * @Description: 好友关系表
 */
@Data
@TableName("im_friend_relation")
public class ImFriendRelation implements Serializable {

    private static final long serialVersionUID = -1L;

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
     * 0否（未拉黑） ，1是（拉黑）
     */
    private Boolean blackFlag;

    /**
     * 0否（未删除） ，1是（已删除）
     */
    private Boolean delFlag;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
