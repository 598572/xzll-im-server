package com.xzll.business.dto.request;

import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2025/10/30
 * @Description: 聊天历史记录查询请求DTO
 */
@Data
public class ChatHistoryQueryDTO {

    /**
     * 聊天ID (必填)
     */
    private String chatId;

    /**
     * 当前用户ID
     */
    private String userId;

    /**
     * 上一条消息ID (分页参数，可选)
     * 为空时从最新消息开始查询
     */
    private String lastMsgId;

    /**
     * 每页大小 (默认50，最大100)
     */
    private Integer pageSize = 50;

    /**
     * 开始时间戳 (可选)
     * 毫秒级时间戳
     */
    private Long startTime;

    /**
     * 结束时间戳 (可选)
     * 毫秒级时间戳
     */
    private Long endTime;

    /**
     * 是否倒序查询 (默认true，即最新消息在前)
     */
    private Boolean reverse = true;
}
