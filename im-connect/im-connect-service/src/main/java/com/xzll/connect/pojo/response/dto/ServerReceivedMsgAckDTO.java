package com.xzll.connect.pojo.response.dto;

import com.xzll.common.pojo.base.BaseMsgRequestDTO;
import com.xzll.connect.pojo.enums.MsgStatusEnum;
import com.xzll.connect.pojo.response.dto.base.BaseMsgResponseDTO;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * @Author: hzz
 * @Date: 2022/2/18 15:35:28
 * @Description: 服务接收消息后的ack
 */
@Data
@Accessors(chain = true)
public class ServerReceivedMsgAckDTO extends BaseMsgResponseDTO {

    private String sessionId;
    private Integer msgReceivedStatus;
    private String ackTextDesc;
    private Long receiveTime;


    /**
     * 构建响应体
     *
     * @param packet
     * @param <T>
     * @return
     */
    public static <T extends BaseMsgRequestDTO> ServerReceivedMsgAckDTO getServerReceivedMsgAckVO(T packet, boolean receiveStatus) {
        ServerReceivedMsgAckDTO serverReceivedMsgAckDTO = new ServerReceivedMsgAckDTO();
        serverReceivedMsgAckDTO.setAckTextDesc(receiveStatus ? MsgStatusEnum.MsgSendStatus.SERVER_RECEIVED.getDesc() : MsgStatusEnum.MsgSendStatus.FAIL.getDesc())
                .setMsgReceivedStatus(receiveStatus ? MsgStatusEnum.MsgSendStatus.SERVER_RECEIVED.getCode() : MsgStatusEnum.MsgSendStatus.FAIL.getCode())
                .setReceiveTime(System.currentTimeMillis())
                .setSessionId(packet.getSessionId())
                .setMsgType(packet.getMsgType());
        serverReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return serverReceivedMsgAckDTO;
    }

}
