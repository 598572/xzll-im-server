package com.xzll.business.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群成员关系表
 */
@Data
@TableName("im_group_member")
public class ImGroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 成员角色：1-群主，2-管理员，3-普通成员
     */
    private Integer memberRole;

    /**
     * 加入时间（毫秒时间戳）
     */
    private Long joinTime;

    /**
     * 加入方式：1-主动加入，2-邀请加入
     */
    private Integer joinType;

    /**
     * 邀请人ID
     */
    private String inviterId;

    /**
     * 群昵称
     */
    private String nickname;

    /**
     * 禁言标识：0-未禁言，1-已禁言
     */
    private Integer muteFlag;

    /**
     * 状态：1-正常，2-已退出
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
