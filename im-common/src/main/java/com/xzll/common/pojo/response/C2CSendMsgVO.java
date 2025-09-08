package com.xzll.common.pojo.response;

import com.xzll.common.pojo.response.base.CommonMsgVO;
import lombok.Getter;
import lombok.Setter;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description:
 */
@Setter
@Getter
public class C2CSendMsgVO extends CommonMsgVO {

    /**
     * 发送人id
     */
    private String fromUserId;
    /**
     * 接收人id
     */
    private String toUserId;
    /**
     * 消息格式
     */
    private Integer msgFormat;
    /**
     * 消息内容
     */
    private String msgContent;
    /**
     * 发送人名称
     */
    private String firstUserName;

    //暂时保留 后期没什么用的话可能去掉
    private Integer firstUserType;
    private String secondUserName;
    private Integer secondUserType;

    /**
     * 是否重试消息
     */
    private Integer retryMsgFlag;

    /**
     * 会话ID - 用于客户端更新会话列表
     */
    private String chatId;
    
    /**
     * 会话最后消息时间 - 用于排序
     */
    private Long lastMsgTime;
    
    /**
     * 会话最后消息格式 - 用于显示
     */
    private Integer lastMsgFormat;
    
    /**
     * 会话最后消息内容 - 用于显示
     */
    private String lastMessageContent;


}
