package com.xzll.connect.cluster.provider;


import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.connect.cluster.mq.RocketMqProducerWrap;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2021/9/3 12:47:09
 * @Description: rocketMq 生产者包装类
 */
@Slf4j
@Component
public class C2CMsgProvider {


    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;


    /**
     * 发送消息，根据指定topic和事件数据
     *
     * @param dto
     * @return
     */
    public boolean sendC2CMsg(C2CMsgRequestDTO dto) {
        boolean result = false;
        try {
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(dto));
            result = rocketMqProducerWrap.sendClusterEvent(ImConstant.TopicConstant.XZLL_TEST_TOPIC, clusterEvent);
            log.info("单聊发送消息结果:{}", result);
        } catch (Exception e) {
            log.error("rocketMq单聊消息发送失败:", e);
        }
        return result;
    }


}
