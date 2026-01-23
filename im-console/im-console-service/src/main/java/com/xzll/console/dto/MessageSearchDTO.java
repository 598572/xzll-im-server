package com.xzll.console.dto;

import lombok.Data;

/**
 * 消息搜索请求DTO
 * 支持多条件组合查询
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Data
public class MessageSearchDTO {

    /**
     * 发送方用户ID
     * 精确匹配
     */
    private String fromUserId;

    /**
     * 接收方用户ID
     * 精确匹配
     */
    private String toUserId;

    /**
     * 会话ID
     * 精确匹配
     */
    private String chatId;

    /**
     * 消息内容关键词
     * 支持全文搜索（中文分词）
     */
    private String content;

    /**
     * 消息状态
     * 0-待发送 1-已发送 2-已送达 3-已读
     */
    private Integer msgStatus;

    /**
     * 消息格式
     * 1-文本 2-图片 3-语音 4-视频 等
     */
    private Integer msgFormat;

    /**
     * 撤回标志
     * 0-正常 1-已撤回
     */
    private Integer withdrawFlag;

    /**
     * 开始时间（时间戳，毫秒）
     */
    private Long startTime;

    /**
     * 结束时间（时间戳，毫秒）
     */
    private Long endTime;

    /**
     * 页码（从1开始）
     */
    private Integer pageNum = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 20;

    /**
     * 是否只统计数量（不返回数据）
     */
    private Boolean countOnly = false;

    /**
     * 获取起始位置
     */
    public int getFrom() {
        return (pageNum - 1) * pageSize;
    }

    /**
     * 检查是否有任何搜索条件
     */
    public boolean hasAnyCondition() {
        return fromUserId != null || toUserId != null || chatId != null
                || content != null || msgStatus != null || msgFormat != null
                || withdrawFlag != null || startTime != null || endTime != null;
    }

    /**
     * 检查是否只有chatId条件
     * 用于判断是否应该走HBase查询
     */
    public boolean isOnlyChatIdCondition() {
        return chatId != null
                && fromUserId == null && toUserId == null
                && content == null && msgStatus == null && msgFormat == null
                && withdrawFlag == null && startTime == null && endTime == null;
    }
}
