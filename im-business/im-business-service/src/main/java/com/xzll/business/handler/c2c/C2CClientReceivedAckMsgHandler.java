package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.common.constant.UserRedisConstant;
import com.xzll.common.pojo.BaseResponse;
import com.xzll.common.pojo.ClientReceivedMsgAckDTO;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.api.ResponseAck2ClientApi;
import lombok.extern.slf4j.Slf4j;
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
 * @Description: 客户端响应的ack消息 处理器
 */
@Slf4j
@Component
public class C2CClientReceivedAckMsgHandler {

    @Resource
    private ImC2CMsgRecordService imC2CMsgRecordService;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @DubboReference
    private ResponseAck2ClientApi responseAck2ClientApi;


    /**
     * 接收方响应的 ack消息
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void clientReceivedAckMsgDeal(ClientReceivedMsgAckDTO dto) {
        //1. 更新消息状态为：未读/已读
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgReceivedStatus(dto);
        //2. 接收方客户端ack发送至发送方
        if (updateResult) {
            ClientReceivedMsgAckDTO ackDTO = new ClientReceivedMsgAckDTO();
            //toUser是目标客户端也就是 最初发消息的发送方，对于接收方响应ack时来说 发送方就变成：toUserId 了
            ackDTO.setToUserId(dto.getToUserId());
            ackDTO.setChatId(dto.getChatId());
            ackDTO.setMsgId(dto.getMsgId());
            ackDTO.setMsgStatus(dto.getMsgStatus());
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(UserRedisConstant.ROUTE_PREFIX, dto.getToUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), 0, false));
            BaseResponse baseResponse = responseAck2ClientApi.responseClientAck2Client(ackDTO);
            log.info("接收方客户端ack发送至发送方结果:{}", JSONUtil.toJsonStr(baseResponse));
        }
    }
}
