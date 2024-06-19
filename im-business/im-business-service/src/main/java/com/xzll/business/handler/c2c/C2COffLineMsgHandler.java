package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.OffLineMsgAO;
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
 * @Description: 离线消息处理器
 */
@Slf4j
@Component
public class C2COffLineMsgHandler {


    @Resource
    private ImC2CMsgRecordService imC2CMsgRecordService;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @DubboReference
    private ResponseAck2ClientApi responseAck2ClientApi;


    /**
     * 离线消息
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void offLineMsgDeal(OffLineMsgAO dto) {
        //1. 更新消息状态为离线
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgOffLineStatus(dto);

        //2. 往redis存储离线消息(防止大量用户同时上线造成db压力)，用于上线后主动push 根据score 取，此处的score取msgId中的 雪花算法id，
        redisTemplate.opsForZSet().add(ImConstant.RedisKeyConstant.OFF_LINE_MSG_KEY + dto.getToUserId(), JSONUtil.toJsonStr(dto), MsgIdUtilsService.getSnowflakeId(dto.getMsgId()));

        //3. 响应给 发送方客户端 未读ack
        if (updateResult) {
            //如果接收方离线 需要伪造未读ack给发送方
            //根据fromId找到他登录的机器并响应ack(rpc调用连接服务)
            C2CClientReceivedMsgAckVO ackDTO = getClientReceivedMsgAckVO(dto);
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.ROUTE_PREFIX, dto.getToUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), 0, false));
            WebBaseResponse webBaseResponse = responseAck2ClientApi.responseClientAck2Client(ackDTO);
            log.info("接收方离线时，服务器伪造未读ack给发送方结果:{}", JSONUtil.toJsonStr(webBaseResponse));
        }
    }

    /**
     * 目标用户离线，构建伪造ack 给发送方
     * @param packet
     * @return
     */
    public static C2CClientReceivedMsgAckVO getClientReceivedMsgAckVO(OffLineMsgAO packet) {
        //目标用户离线，响应给发送者的ack固定为未读
        packet.setMsgStatus(MsgStatusEnum.MsgStatus.UN_READ.getCode());

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
                .setToUserId(packet.getFromUserId())
                .setMsgType(msgType);
        clientReceivedMsgAckDTO.setMsgId(packet.getMsgId());
        return clientReceivedMsgAckDTO;
    }
}
