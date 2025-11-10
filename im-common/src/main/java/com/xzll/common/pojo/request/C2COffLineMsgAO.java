package com.xzll.common.pojo.request;

import com.xzll.common.pojo.request.base.CommonMsgAO;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class C2COffLineMsgAO extends CommonMsgAO {

    /**
     * 客户端消息ID（UUID，用于客户端关联）
     */
    private String clientMsgId;

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
     * 消息状态（-1：发送失败，1：到达服务器，2：离线，3：未读，4：已读）
     */
    private Integer msgStatus;


    /**
     * 消息格式
     * 1 文本
     * 2 图片
     * 3 语音
     */
    private Integer msgFormat;
}
