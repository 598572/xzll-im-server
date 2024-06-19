package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordService;

import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.ClientReceivedMsgAckAO;
import com.xzll.common.pojo.response.C2CClientReceivedMsgAckVO;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.common.util.msgId.MsgIdUtilsService;
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
import java.util.Objects;

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
    public void clientReceivedAckMsgDeal(ClientReceivedMsgAckAO dto) {
        //1. 更新消息状态为：未读/已读
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgReceivedStatus(dto);
        //2. （收到未读/已读ack后）删除离线消息缓存
        long needDeleteMsgId = MsgIdUtilsService.getSnowflakeId(dto.getMsgId());
        redisTemplate.opsForZSet().removeRangeByScore(ImConstant.RedisKeyConstant.OFF_LINE_MSG_KEY + dto.getFromUserId(), needDeleteMsgId, needDeleteMsgId);

        //3. 接收方客户端ack发送至发送方
        if (updateResult) {
            C2CClientReceivedMsgAckVO ackVo = getClientReceivedMsgAckVO(dto);
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.ROUTE_PREFIX, dto.getToUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), 0, false));
            WebBaseResponse webBaseResponse = responseAck2ClientApi.responseClientAck2Client(ackVo);
            log.info("接收方客户端ack发送至发送方结果:{}", JSONUtil.toJsonStr(webBaseResponse));
        }
    }


    public static C2CClientReceivedMsgAckVO getClientReceivedMsgAckVO(ClientReceivedMsgAckAO packet) {
        C2CClientReceivedMsgAckVO clientReceivedMsgAckDTO = new C2CClientReceivedMsgAckVO();
        ImBaseResponse.MsgType msgType = new ImBaseResponse.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode());
        int secondLevelMsgType = 0;
        if (Objects.equals(packet.getMsgStatus(), MsgStatusEnum.MsgStatus.UN_READ.getCode())) {
            secondLevelMsgType = MsgTypeEnum.SecondLevelMsgType.UN_READ.getCode();
        }
        if (Objects.equals(packet.getMsgStatus(), MsgStatusEnum.MsgStatus.READED.getCode())) {
            secondLevelMsgType = MsgTypeEnum.SecondLevelMsgType.READ.getCode();
        }
        msgType.setSecondLevelMsgType(secondLevelMsgType);
        clientReceivedMsgAckDTO.setAckTextDesc(MsgStatusEnum.MsgStatus.getNameByCode(packet.getMsgStatus()))
                .setMsgReceivedStatus(packet.getMsgStatus())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                //toUser是目标客户端也就是 最初发消息的发送方，对于接收方响应ack时来说 发送方就变成：toUserId 了
                .setToUserId(packet.getToUserId())
                .setMsgType(msgType);
        clientReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return clientReceivedMsgAckDTO;
    }
}
