package com.xzll.common.pojo.request;

import com.xzll.common.constant.MsgStatusEnum;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群离线消息 AO
 */
@Setter
@Getter
@Builder
public class GroupOffLineMsgAO {

    /**
     * 消息ID
     */
    private String msgId;

    /**
     * 群ID
     */
    private String groupId;

    /**
     * 发送人ID
     */
    private String fromUserId;

    /**
     * 目标用户ID（接收离线消息的用户）
     */
    private String toUserId;

    /**
     * 客户端消息ID
     */
    private String clientMsgId;

    /**
     * 消息状态
     */
    private Integer msgStatus;

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
     * 会话ID
     */
    private String chatId;
}
