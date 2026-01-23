package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI知识库实体类
 *
 * @author xzll
 */
@Data
@TableName("im_ai_knowledge")
public class AiKnowledgeDO {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 知识ID
     */
    private String knowledgeId;

    /**
     * 知识分类
     */
    private String category;

    /**
     * 问题
     */
    private String question;

    /**
     * 答案
     */
    private String answer;

    /**
     * 关键词（逗号分隔）
     */
    private String keywords;

    /**
     * 优先级（数值越大优先级越高）
     */
    private Integer priority;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

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
