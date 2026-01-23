package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统公告实体类
 *
 * @author xzll
 */
@Data
@TableName("im_system_notice")
public class SystemNoticeDO {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 公告ID
     */
    private String noticeId;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容
     */
    private String content;

    /**
     * 公告类型：1-系统公告，2-维护通知，3-活动通知
     */
    private Integer noticeType;

    /**
     * 目标用户：ALL-所有用户，或指定用户ID列表
     */
    private String targetUsers;

    /**
     * 推送方式：1-应用内推送，2-短信，3-邮件
     */
    private Integer pushType;

    /**
     * 状态：0-草稿，1-已发布，2-已撤回
     */
    private Integer status;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建人
     */
    private String createBy;

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
