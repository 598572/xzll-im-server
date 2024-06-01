package com.xzll.connect.pojo.dto;

import io.netty.channel.Channel;
import lombok.Builder;
import lombok.Data;

/**
 * @Author: hzz
 * @Date: 2022/2/18 16:50:04
 * @Description:
 */
@Data
@Builder
public class ReceiveUserDataDTO {

    private String channelIdByUserId;
    private Channel targetChannel;
    private String serverJson;
    //ip:port
    private String routeAddress;
    private String userStatus;
    private ServerInfoDTO serverInfoDTO;
    private String toUserId;

}
