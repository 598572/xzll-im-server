package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户封禁记录实体类
 *
 * @author xzll
 */
@Data
@TableName("im_user_ban")
public class UserBanDO {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 被封禁用户ID
     */
    private String userId;

    /**
     * 封禁类型：1-账号封禁，2-IP封禁，3-设备封禁
     */
    private Integer banType;

    /**
     * 封禁值（IP地址或设备ID）
     */
    private String banValue;

    /**
     * 封禁原因
     */
    private String banReason;

    /**
     * 封禁开始时间
     */
    private LocalDateTime banStartTime;

    /**
     * 封禁结束时间（NULL表示永久）
     */
    private LocalDateTime banEndTime;

    /**
     * 封禁操作人
     */
    private String banBy;

    /**
     * 状态：0-已解封，1-封禁中
     */
    private Integer status;

    /**
     * 解封时间
     */
    private LocalDateTime unbanTime;

    /**
     * 解封操作人
     */
    private String unbanBy;

    /**
     * 解封原因
     */
    private String unbanReason;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
