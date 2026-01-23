package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 举报记录实体类
 *
 * @author xzll
 */
@Data
@TableName("im_report")
public class ReportDO {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 举报ID
     */
    private String reportId;

    /**
     * 举报人用户ID
     */
    private String reporterId;

    /**
     * 被举报人用户ID
     */
    private String reportedUserId;

    /**
     * 举报类型：1-色情，2-欺诈，3-骚扰，4-广告，5-其他
     */
    private Integer reportType;

    /**
     * 举报内容描述
     */
    private String reportContent;

    /**
     * 证据消息ID列表（JSON数组）
     */
    private String evidenceMsgIds;

    /**
     * 证据图片URL列表（JSON数组）
     */
    private String evidenceImages;

    /**
     * 处理状态：0-待处理，1-处理中，2-已处理，3-已驳回
     */
    private Integer status;

    /**
     * 处理结果
     */
    private String handleResult;

    /**
     * 处理人
     */
    private String handleBy;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

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
