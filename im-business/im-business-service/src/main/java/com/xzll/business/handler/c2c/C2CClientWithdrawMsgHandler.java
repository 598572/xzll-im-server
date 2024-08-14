package com.xzll.business.handler.c2c;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.base.WebBaseResponse;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.pojo.response.C2CWithdrawMsgVO;
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
 * @Description: 客户端响应的ack消息 处理器
 */
@Slf4j
@Component
public class C2CClientWithdrawMsgHandler {

    @Resource
    private ImC2CMsgRecordService imC2CMsgRecordService;
    @Resource
    private RedisTemplate<String, String> redisTemplate;
    @DubboReference
    private RpcSendMsg2ClientApi rpcSendMsg2ClientApi;


    /**
     * 接收方响应的 ack消息
     *
     * @param ao
     */
    @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRED)
    public void clientWithdrawMsgDeal(C2CWithdrawMsgAO ao) {
        //1. 更新消息状态为：未读/已读
        boolean updateResult = imC2CMsgRecordService.updateC2CMsgWithdrawStatus(ao);
        //2. 撤回消息发送至接收方
        if (updateResult) {
            C2CWithdrawMsgVO c2CWithdrawMsgVo = getC2CWithdrawMsgVO(ao);
            //指定ip调用 与消息转发一样
            String ipPort = (String) redisTemplate.opsForHash().get(ImConstant.RedisKeyConstant.ROUTE_PREFIX, ao.getToUserId());
            UserSpecifiedAddressUtil.setAddress(new Address(NettyAttrUtil.getIpStr(ipPort), 0, false));
            WebBaseResponse webBaseResponse = rpcSendMsg2ClientApi.sendWithdrawMsg2Client(c2CWithdrawMsgVo);
            log.info("发送撤回消息至接收方结果:{}", JSONUtil.toJsonStr(webBaseResponse));
        }
    }


    /**
     * 后期有时间改为 mapstract
     *
     * @param packet
     * @return
     */
    public static C2CWithdrawMsgVO getC2CWithdrawMsgVO(C2CWithdrawMsgAO packet) {
        C2CWithdrawMsgVO c2CWithdrawMsgVo = new C2CWithdrawMsgVO();
        BeanUtil.copyProperties(packet, c2CWithdrawMsgVo);
        return c2CWithdrawMsgVo;
    }
}
