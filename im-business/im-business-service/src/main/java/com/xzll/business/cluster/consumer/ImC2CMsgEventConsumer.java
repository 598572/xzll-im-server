package com.xzll.business.cluster.consumer;

import cn.hutool.json.JSONUtil;
import com.xzll.business.config.mq.RocketMqConsumerWrap;
import com.xzll.business.handler.C2CMsgHandler;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.pojo.C2CMsgRequestDTO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.rocketmq.RocketMQClusterEventListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2023/3/1 11:25:10
 * @Description: 某一批topic订阅与消费
 */
@Slf4j
@Component
public class ImC2CMsgEventConsumer implements RocketMQClusterEventListener, InitializingBean {


    @Resource
    private RocketMqConsumerWrap consumer;
    @Resource
    private C2CMsgHandler c2CMsgHandler;

    /**
     * 初始化该类要监听的topic 并且调用RocketMqCustomConsumer的subscribe方法，进行订阅和启动consumer
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        List<String> topics = new ArrayList<>();
        topics.add(ImConstant.TopicConstant.XZLL_C2CMSG_TOPIC);
        consumer.subscribe(topics, this);
    }

    @Override
    public void handleEvent(String topicName, ClusterEvent clusterEvent) {
        log.info("当前topic:{},接收到的data数据:{}", topicName, JSONUtil.toJsonStr(clusterEvent));
        if (ImConstant.TopicConstant.XZLL_C2CMSG_TOPIC.equals(topicName)) {
            C2CMsgRequestDTO c2CMsgRequestDTO = JSONUtil.toBean(clusterEvent.getData(), C2CMsgRequestDTO.class);
            c2CMsgHandler.sendC2CMsgDeal(c2CMsgRequestDTO);
        }
    }
}
