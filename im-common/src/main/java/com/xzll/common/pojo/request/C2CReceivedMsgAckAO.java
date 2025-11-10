package com.xzll.common.pojo.request;


import com.xzll.common.pojo.request.base.CommonMsgAO;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2022/1/18 17:33:02
 * @Description:
 */
@Setter
@Getter
@Accessors(chain = true)
public class C2CReceivedMsgAckAO extends CommonMsgAO {

    /**
     * 客户端消息ID（UUID，用于客户端关联）
     */
    private String clientMsgId;

    private String fromUserId;

    private String toUserId;

    /**
     * 批量已读，未读消息id 暂时单个已读吧， 批量已读后期规划实现
     */
    private List<String> msgIds;


    /**
     * 消息状态（-1：发送失败，1：到达服务器，2：离线，3：未读，4：已读）
     */
    private Integer msgStatus;


}
