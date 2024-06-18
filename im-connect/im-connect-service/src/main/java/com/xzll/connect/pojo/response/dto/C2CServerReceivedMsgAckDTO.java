package com.xzll.connect.pojo.response.dto;

import com.xzll.common.pojo.base.BaseMsgResponseDTO;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: hzz
 * @Date: 2022/2/18 15:35:28
 * @Description: 服务接收消息后的ack
 */
@Data
@Accessors(chain = true)
public class C2CServerReceivedMsgAckDTO extends BaseMsgResponseDTO {

    private String chatId;
    private Integer msgReceivedStatus;
    private String ackTextDesc;
    private Long receiveTime;

}
