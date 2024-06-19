package com.xzll.common.pojo.response;

import com.xzll.common.pojo.response.base.CommonMsgVO;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: hzz
 * @Date: 2022/2/18 15:35:28
 * @Description: 服务接收消息后的ack
 */
@Data
@Accessors(chain = true)
public class C2CServerReceivedMsgAckVO extends CommonMsgVO {

    private String toUserId;
    private String chatId;
    private Integer msgReceivedStatus;
    private String ackTextDesc;
    private Long receiveTime;

}
