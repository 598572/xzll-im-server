package com.xzll.business.handler.c2c;

import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.constant.UserRedisConstant;
import com.xzll.common.pojo.BaseResponse;
import com.xzll.common.pojo.ClientReceivedMsgAckDTO;
import com.xzll.common.pojo.OffLineMsgDTO;
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
    public void offLineMsgDeal(OffLineMsgDTO dto) {
        //1. 更新消息状态为离线
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgOffLineStatus(dto);
        //存储离线消息，用于上线后主动push 根据score 取
        redisTemplate.opsForZSet().add("OFF_LINE_MSG_KEY_" + dto.getToUserId(), JSONUtil.toJsonStr(dto), System.currentTimeMillis());
        //2. 响应给 发送方客户端 未读ack
        if (updateResult) {
            //如果接收方离线 需要伪造未读ack给发送方
            //根据fromId找到他登录的机器并响应ack(rpc调用连接服务)
            ClientReceivedMsgAckDTO ackDTO = new ClientReceivedMsgAckDTO();
            //toUser是目标客户端也就是 发送方：fromUserId
            ackDTO.setToUserId(dto.getFromUserId());
            ackDTO.setChatId(dto.getChatId());
            ackDTO.setMsgId(dto.getMsgId());
            //固定为未读
            ackDTO.setMsgStatus(MsgStatusEnum.MsgStatus.UN_READ.getCode());
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(UserRedisConstant.ROUTE_PREFIX, dto.getToUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), 0, false));
            BaseResponse baseResponse = responseAck2ClientApi.responseClientAck2Client(ackDTO);
            log.info("接收方离线时，服务器伪造未读ack给发送方结果:{}", JSONUtil.toJsonStr(baseResponse));
        }
    }
}
