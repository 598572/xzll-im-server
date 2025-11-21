package com.xzll.connect.service.dto;

import lombok.Data;
import java.io.Serializable;

/**
 * C2C消息重试事件
 * 用于Redis ZSet延迟队列
 * 
 * @Author: hzz
 * @Date: 2025-11-14
 */
@Data
public class C2CMsgRetryEvent implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 客户端消息ID（UUID）
     */
    private String clientMsgId;
    
    /**
     * 服务端消息ID（雪花ID）
     */
    private String msgId;
    
    /**
     * 发送人ID
     */
    private String fromUserId;
    
    /**
     * 接收人ID
     */
    private String toUserId;
    
    /**
     * 会话ID
     */
    private String chatId;
    
    /**
     * 重试次数（0-3）
     */
    private Integer retryCount;
    
    /**
     * 消息内容
     */
    private String msgContent;
    
    /**
     * 消息格式
     */
    private Integer msgFormat;
    
    /**
     * 消息创建时间
     */
    private Long msgCreateTime;
    
    /**
     * 事件创建时间
     */
    private String createTime;
    
    /**
     * 最大重试次数
     */
    private Integer maxRetries;
    
    /**
     * 原始JSON值（用于从ZSet删除，不序列化）
     */
    private transient String originalValue;
}

