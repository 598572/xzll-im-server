package com.xzll.common.pojo.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群消息 MQ DTO (用于RocketMQ广播)
 */
@Data
public class GroupMsgDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 消息ID（雪花算法）
     */
    private String msgId;

    /**
     * 群ID
     */
    private String groupId;

    /**
     * 群名称（冗余，避免客户端再次查询）
     */
    private String groupName;

    /**
     * 发送人ID
     */
    private String fromUserId;

    /**
     * 发送人昵称（冗余）
     */
    private String fromNickname;

    /**
     * 发送人头像（冗余）
     */
    private String fromAvatar;

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
     * 群成员数
     */
    private Integer memberCount;

    /**
     * 客户端消息ID（UUID，用于去重）
     */
    private String clientMsgId;
}
