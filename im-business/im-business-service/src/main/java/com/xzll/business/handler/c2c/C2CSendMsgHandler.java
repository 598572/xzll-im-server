package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.business.service.ImChatService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.base.ImBaseResponse;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.response.C2CServerReceivedMsgAckVO;

import com.xzll.common.util.NettyAttrUtil;
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
 * @Description: 发送单聊消息处理器
 */
@Slf4j
@Component
public class C2CSendMsgHandler {
    @Resource
    private ImChatService imChatService;
    @Resource
    private ImC2CMsgRecordService imC2CMsgRecordService;
    //配置check = false 后生产者不启动也无所谓，不会报错影响本服务启动，当然也可全局配(这里全局配置了)
    @DubboReference
    private RpcSendMsg2ClientApi rpcSendMsg2ClientApi;
    @Resource
    private RedisTemplate<String, String> redisTemplate;


    /**
     * 单聊消息
     *
     * @param dto
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void sendC2CMsgDeal(C2CSendMsgAO dto) {
        boolean writeChat = imChatService.saveOrUpdateC2CChat(dto);
        boolean writeMsg = imC2CMsgRecordService.saveC2CMsg(dto);
        if (writeChat && writeMsg) {
            //发送server_ack消息 告诉发送方此消息服务端已收到（想要可靠，必须落库后在ack）
            //根据fromId找到他登录的机器并响应ack(rpc调用连接服务)
            C2CServerReceivedMsgAckVO ackVo = getServerReceivedMsgAckVO(dto);
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.ROUTE_PREFIX, dto.getFromUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), 0, false));
            WebBaseResponse webBaseResponse = rpcSendMsg2ClientApi.responseServerAck2Client(ackVo);
            log.info("服务端ack发送至发送方结果:{}", JSONUtil.toJsonStr(webBaseResponse));
        }
    }

    /**
     * 构建响应给客户端的服务端ack
     *
     * @param packet
     * @return
     */
    public static C2CServerReceivedMsgAckVO getServerReceivedMsgAckVO(C2CSendMsgAO packet) {
        C2CServerReceivedMsgAckVO c2CServerReceivedMsgAckVO = new C2CServerReceivedMsgAckVO();
        ImBaseResponse.MsgType msgType = new ImBaseResponse.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.SERVER_RECEIVE_ACK.getCode());
        c2CServerReceivedMsgAckVO.setAckTextDesc(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getDesc())
                .setMsgReceivedStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())
                .setReceiveTime(System.currentTimeMillis())
                .setChatId(packet.getChatId())
                //toUser是目标客户端也就是 发送方：fromUserId
                .setToUserId(packet.getFromUserId())
                .setMsgType(msgType);
        c2CServerReceivedMsgAckVO.setMsgId(packet.getMsgId());
        return c2CServerReceivedMsgAckVO;
    }
}
