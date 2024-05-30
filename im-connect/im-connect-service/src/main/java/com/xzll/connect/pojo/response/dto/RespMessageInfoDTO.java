package com.xzll.connect.pojo.response.dto;

import cn.hutool.core.lang.ObjectId;
import lombok.Data;

import java.io.Serializable;

@Data
public class RespMessageInfoDTO implements Serializable {

    private ObjectId _id;
    private Long createTime;
    private Long updateTime;

    /**
     * 消息Id
     */
    private String msgId;

    /**
     * 消息类型
     * 1、指令消息（撤回）
     * 2、通知消息（ack、已读）
     * 3、聊天消息（单聊、重新发送）
     * 4、系统消息（由系统发送的消息）
     */
    private Integer firstLevelMsgType;


    /**
     * 具体消息类型
     * 1-1 撤回
     * 2-1 ack
     * 2-2 已读
     * 3-1 单聊信息
     * 3-2 重新发送
     * 4-1 系统消息
     */
    private Integer secondLevelMsgType;

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
    private String sessionId;

    /**
     * 发送状态
     */
    private Integer sendStatus;

    /**
     * 是否撤回
     */
    private Integer isWithdraw;

    /**
     * 是否离线消息
     */
    private Integer isOffline;

    /**
     * 读取状态
     * 1 未读
     * 2 已读
     */
    private Integer readStatus;

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
