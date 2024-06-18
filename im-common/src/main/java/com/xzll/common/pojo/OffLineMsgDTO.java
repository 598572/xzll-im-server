package com.xzll.common.pojo;

import com.xzll.common.pojo.base.BaseMsgRequestDTO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OffLineMsgDTO extends BaseMsgRequestDTO {

    /**
     * 消息内容
     */
    private String msgContent;

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
     * 消息状态（-1：发送失败，1：到达服务器，2：离线，3：未读，4：已读）
     */
    private Integer msgStatus;


    /**
     * 消息创建时间
     */
    private Long msgCreateTime;

    /**
     * 消息格式
     * 1 文本
     * 2 图片
     * 3 语音
     */
    private Integer msgFormat;
}
