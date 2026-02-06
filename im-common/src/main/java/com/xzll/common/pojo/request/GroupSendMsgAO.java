package com.xzll.common.pojo.request;

import com.xzll.common.pojo.request.base.CommonMsgAO;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * @Author: hzz
 * @Date: 2026-02-04
 * @Description: 群聊消息发送 AO (Application Object)
 */
@Setter
@Getter
@ToString(callSuper = true)
public class GroupSendMsgAO extends CommonMsgAO {

    private static final long serialVersionUID = 1L;

    /**
     * 客户端消息ID（UUID，客户端生成，用于去重和客户端关联）
     */
    private String clientMsgId;

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
     * 群成员数（冗余，用于客户端显示）
     */
    private Integer memberCount;

    /**
     * 是否重试消息
     */
    private Integer retryMsgFlag;
}
