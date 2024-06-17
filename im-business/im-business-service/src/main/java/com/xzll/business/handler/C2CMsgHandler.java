package com.xzll.business.handler;

import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.business.service.ImChatService;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import com.xzll.common.pojo.C2CServerAckDTO;
import com.xzll.common.constant.UserRedisConstant;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.api.ServerResponseAck2ClientApi;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.rpc.cluster.specifyaddress.Address;
import org.apache.dubbo.rpc.cluster.specifyaddress.UserSpecifiedAddressUtil;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/6/16 20:23:33
 * @Description:
 */
@Component
public class C2CMsgHandler {
    @Resource
    private ImChatService imChatService;
    @Resource
    private ImC2CMsgRecordService imC2CMsgRecordService;
    @DubboReference(check = false)
    private ServerResponseAck2ClientApi serverResponseAck2ClientApi;
    @Resource
    private RedisTemplate<String, String> redisTemplate;


    /**
     * 单聊消息
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendC2CMsgDeal(C2CMsgRequestDTO dto) {
        boolean writeChat = imChatService.saveOrUpdateC2CChat(dto);
        boolean writeMsg = imC2CMsgRecordService.saveC2CMsg(dto);
        if (writeChat && writeMsg) {
            //发送server_ack消息 告诉发送方此消息服务端已收到（想要可靠，必须落库后在ack）
            //根据fromId找到他登录的机器并响应ack(rpc调用连接服务)
            C2CServerAckDTO ackDTO = new C2CServerAckDTO();
            ackDTO.setToUserId(dto.getFromUserId());
            ackDTO.setChatId(dto.getChatId());
            ackDTO.setMsgId(dto.getMsgId());
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(UserRedisConstant.ROUTE_PREFIX, dto.getFromUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), NettyAttrUtil.getPortInt(ipPort), false));
            serverResponseAck2ClientApi.serverResponseAck2Client(ackDTO);
        }
    }
}
