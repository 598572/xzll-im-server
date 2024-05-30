package com.xzll.connect.pojo.response.dto;


import com.xzll.connect.pojo.response.dto.base.BaseMsgResponseDTO;
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
public class ClientReceivedMsgAckDTO extends BaseMsgResponseDTO {

    private String fromUserId;
    private String toUserId;

    /**
     * 批量已读，未读消息id
     */
    private List<String> msgIds;

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

}
