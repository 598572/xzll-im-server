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
}
