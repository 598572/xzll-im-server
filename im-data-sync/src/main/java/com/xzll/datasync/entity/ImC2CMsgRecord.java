package com.xzll.datasync.entity;

import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * C2C消息记录实体类
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
public class ImC2CMsgRecord implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * RowKey
     */
    private String rowkey;
    
    /**
     * 发送方用户ID
     */
    private String fromUserId;
    
    /**
     * 接收方用户ID
     */
    private String toUserId;
    
    /**
     * 消息ID
     */
    private String msgId;
    
    /**
     * 消息格式
     */
    private Integer msgFormat;
    
    /**
     * 消息内容
     */
    private String msgContent;
    
    /**
     * 消息创建时间
     */
    private Long msgCreateTime;
    
    /**
     * 重试次数
     */
    private Integer retryCount;
    
    /**
     * 消息状态
     */
    private Integer msgStatus;
    
    /**
     * 撤回标志
     */
    private Integer withdrawFlag;
    
    /**
     * 聊天ID
     */
    private String chatId;
    
    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
} 