package com.xzll.datasync.consumer;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

import static com.xzll.common.constant.ImConstant.TopicConstant.XZLL_DATA_SYNC_TOPIC;

/**
 * 数据同步消息消费者初始化器
 * 负责启动批量消费者
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Component
@Slf4j
public class DataSyncMessageConsumer implements InitializingBean {
    
    @Resource
    private DataSyncConsumerWrap consumer;
    
    @Resource
    private BatchDataSyncConsumer batchConsumer;
    

    /**
     * 初始化该类要监听的topic 并且调用RocketMqCustomConsumer的subscribe方法，进行订阅和启动consumer
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() {
        List<String> topics = new ArrayList<>();
        topics.add(XZLL_DATA_SYNC_TOPIC);
        
        // 使用批量消费模式，性能最高
        consumer.subscribeByBatchConsumer(topics, batchConsumer);
        log.info("DataSyncMessageConsumer已启动，使用批量消费模式，订阅topic: {}", XZLL_DATA_SYNC_TOPIC);
    }
    

}
