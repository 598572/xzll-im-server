package com.xzll.common.pojo.response;

import com.xzll.common.pojo.response.base.CommonMsgVO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description: 最近会话列表反参
 */
@Setter
@Getter
public class LastChatListVO extends CommonMsgVO {

    /**
     * 会话ID
     */
    private String chatId;
    
    /**
     * 当前用户id
     */
    private String userId;

    /**
     * 最后一条消息的 消息格式
     */
    private Integer lastMsgFormat;
    
    /**
     * 最后一条消息内容
     */
    private String lastMessageContent;
    
    /**
     * 最后一条消息id
     */
    private String lastMsgId;
    
    /**
     * 最后一条消息时间
     */
    private Long lastMsgTime;
    
    /**
     * 未读消息数量
     */
    private Integer unReadCount;
    
    /**
     * 对方用户ID (便于客户端映射)
     */
    private String otherUserId;
    
    /**
     * 对方用户名 (服务端兜底，客户端缓存失效时使用)
     */
    private String otherUserName;
    
    /**
     * 对方用户头像 (服务端兜底，客户端缓存失效时使用)
     */
    private String otherUserAvatar;
    
    /**
     * 对方用户全名 (服务端兜底，客户端缓存失效时使用)
     */
    private String otherUserFullName;
}
