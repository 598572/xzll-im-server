package com.xzll.console.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI对话记录实体类
 *
 * @author xzll
 */
@Data
@TableName("im_ai_chat")
public class AiChatDO {

    /**
     * 主键
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 对话ID
     */
    private String chatId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 消息类型：1-用户消息，2-AI回复
     */
    private Integer messageType;

    /**
     * 消息内容
     */
    private String content;

    /**
     * 关联的消息ID
     */
    private String msgId;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
