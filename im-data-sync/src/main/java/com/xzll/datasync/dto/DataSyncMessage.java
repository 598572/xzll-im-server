package com.xzll.datasync.dto;

import lombok.Data;
import java.io.Serializable;
import java.util.Map;

/**
 * 数据同步消息DTO
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
public class DataSyncMessage implements Serializable {
    
    /**
     * 操作类型：SAVE, UPDATE_STATUS, UPDATE_WITHDRAW
     */
    private String operationType;
    
    /**
     * 数据类型：C2C_MSG_RECORD, GROUP_MSG_RECORD等
     */
    private String dataType;
    
    /**
     * 具体数据
     */
    private Map<String, Object> data;
    
    /**
     * 时间戳
     */
    private Long timestamp;
    
    /**
     * 聊天ID
     */
    private String chatId;
    
    /**
     * 消息ID
     */
    private String msgId;
} 