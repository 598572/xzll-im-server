package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordService;

import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.common.util.msgId.SnowflakeIdService;
import com.xzll.connect.rpcapi.RpcSendMsg2ClientApi;
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
    private RpcSendMsg2ClientApi rpcSendMsg2ClientApi;


    /**
     * 接收方响应的 ack消息
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void clientReceivedAckMsgDeal(C2CReceivedMsgAckAO dto) {
        //1. 更新消息状态为：未读/已读
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgReceivedStatus(dto);
        //2. （收到未读/已读ack后）删除离线消息缓存
        long needDeleteMsgId = SnowflakeIdService.getSnowflakeId(dto.getMsgId());
        redisTemplate.opsForZSet().removeRangeByScore(ImConstant.RedisKeyConstant.OFF_LINE_MSG_KEY + dto.getFromUserId(), needDeleteMsgId, needDeleteMsgId);

        //3. 接收方客户端ack发送至发送方
        if (updateResult) {
            C2CClientReceivedMsgAckVO ackVo = getClientReceivedMsgAckVO(dto);
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.ROUTE_PREFIX, dto.getToUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), 0, false));
            WebBaseResponse webBaseResponse = rpcSendMsg2ClientApi.responseClientAck2Client(ackVo);
            log.info("接收方客户端ack发送至发送方结果:{}", JSONUtil.toJsonStr(webBaseResponse));
        }
    }


    public static C2CClientReceivedMsgAckVO getClientReceivedMsgAckVO(C2CReceivedMsgAckAO packet) {
        C2CClientReceivedMsgAckVO clientReceivedMsgAckDTO = new C2CClientReceivedMsgAckVO();
        clientReceivedMsgAckDTO.setAckTextDesc(MsgStatusEnum.MsgStatus.getNameByCode(packet.getMsgStatus()))
                .setMsgReceivedStatus(packet.getMsgStatus())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                //toUser是目标客户端也就是 最初发消息的发送方，对于接收方响应ack时来说 发送方就变成：toUserId 了
                .setToUserId(packet.getToUserId())
                .setUrl(packet.getUrl());
        clientReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return clientReceivedMsgAckDTO;
    }
}
