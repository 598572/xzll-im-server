package com.xzll.business.dto.request;

import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

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
    @NotBlank(message = "chatId不能为空")
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
    @Min(value = 1, message = "pageSize最小为1")
    @Max(value = 100, message = "pageSize最大为100")
    @NotNull(message = "pageSize不能为空")
    private Integer pageSize = 50;

    /**
     * 开始时间戳 (可选)
     * 毫秒级时间戳，用于RowKey范围查询优化
     */
    private Long startTime;

    /**
     * 结束时间戳 (可选)
     * 毫秒级时间戳，用于RowKey范围查询优化
     */
    private Long endTime;

    /**
     * 是否倒序查询 (默认true，即最新消息在前)
     */
    private Boolean reverse = true;
}
