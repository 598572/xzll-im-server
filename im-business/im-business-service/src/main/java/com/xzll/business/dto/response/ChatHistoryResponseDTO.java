package com.xzll.business.dto.response;

import lombok.Data;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2025/10/30
 * @Description: 聊天历史记录查询响应DTO
 */
@Data
public class ChatHistoryResponseDTO {

    /**
     * 消息记录列表
     */
    private List<ChatMessageVO> messages;

    /**
     * 是否还有更多数据
     */
    private Boolean hasMore;

    /**
     * 下一页的lastMsgId (用于下次查询的分页参数)
     */
    private String nextLastMsgId;

    /**
     * 当前页实际数量
     */
    private Integer currentPageSize;

    /**
     * 总查询数量 (本次查询到的记录数)
     */
    private Integer totalCount;

    /**
     * 聊天消息VO
     */
    @Data
    public static class ChatMessageVO {
        /**
         * 发送人id
         */
        private String fromUserId;

        /**
         * 接收人id
         */
        private String toUserId;

        /**
         * 消息唯一id
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
         * 消息的发送时间 精确到毫秒
         */
        private Long msgCreateTime;


        /**
         * 消息状态
         */
        private Integer msgStatus;

        /**
         * 撤回标志 0 未撤回 1 已撤回
         */
        private Integer withdrawFlag;

        /**
         * 会话id
         */
        private String chatId;

        /**
         * HBase的RowKey
         */
        private String rowkey;

        /**
         * 创建时间 (毫秒级时间戳)
         */
        private Long createTime;

        /**
         * 更新时间 (毫秒级时间戳)
         */
        private Long updateTime;
    }
}
