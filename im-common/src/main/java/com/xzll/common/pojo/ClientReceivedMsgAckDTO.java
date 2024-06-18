package com.xzll.common.pojo;


import com.xzll.common.pojo.base.BaseMsgRequestDTO;
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
public class ClientReceivedMsgAckDTO extends BaseMsgRequestDTO {

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
