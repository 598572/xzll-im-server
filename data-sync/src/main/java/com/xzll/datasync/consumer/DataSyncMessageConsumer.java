package com.xzll.datasync.consumer;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.xzll.datasync.entity.ImC2CMsgRecord;
import com.xzll.common.rocketmq.RocketMQClusterEventListener;
import com.xzll.datasync.service.DataSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据同步消息消费者
 * 基于现有的RocketMQ架构实现，在启动时订阅指定topic
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Component
@Slf4j
public class DataSyncMessageConsumer implements RocketMQClusterEventListener, InitializingBean {
    
    @Resource
    private DataSyncService dataSyncService;
    
    @Resource
    private DataSyncConsumerWrap consumer;
    
    // 定义要订阅的主题
    private static final String DATA_SYNC_TOPIC = "im_c2cmsg-sync-es-topic";
    
    /**
     * 初始化该类要监听的topic 并且调用RocketMqCustomConsumer的subscribe方法，进行订阅和启动consumer
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() {
        List<String> topics = new ArrayList<>();
        topics.add(DATA_SYNC_TOPIC);
        // 使用并发消费模式，因为数据同步不需要严格顺序
        consumer.subscribeByConcurrentlyConsumer(topics, this);
        log.info("DataSyncMessageConsumer已启动，订阅topic: {}", DATA_SYNC_TOPIC);
    }
    
    @Override
    public void handleEvent(final String topicName, final com.xzll.common.rocketmq.ClusterEvent event) {
        try {
            log.info("收到数据同步消息，topic: {}, eventType: {}, balanceId: {}", 
                    topicName, event.getClusterEventType(), event.getBalanceId());
            
            // 解析业务数据
            String data = event.getData();
            if (data == null || data.trim().isEmpty()) {
                log.warn("数据同步消息内容为空");
                return;
            }
            
            // 解析消息内容
            Map<String, Object> messageData = JSONUtil.toBean(data, Map.class);
            String operationType = (String) messageData.get("operationType");
            String dataType = (String) messageData.get("dataType");
            String chatId = (String) messageData.get("chatId");
            String msgId = (String) messageData.get("msgId");
            
            log.info("解析数据同步消息，操作类型: {}, 数据类型: {}, chatId: {}, msgId: {}", 
                    operationType, dataType, chatId, msgId);
            
            // 根据数据类型进行不同的同步处理
            if ("C2C_MSG_RECORD".equals(dataType)) {
                handleC2CMsgRecordSync(messageData, operationType);
            } else {
                log.warn("未知的数据类型: {}", dataType);
            }
            
        } catch (Exception e) {
            log.error("处理数据同步消息失败，topic: {}, event: {}", topicName, event, e);
            // 这里可以实现重试机制
        }
    }
    
    /**
     * 处理C2C消息记录同步
     */
    private void handleC2CMsgRecordSync(Map<String, Object> message, String operationType) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> dataMap = (Map<String, Object>) message.get("data");
            
            // 将Map转换为ImC2CMsgRecord对象
            ImC2CMsgRecord record = BeanUtil.mapToBean(dataMap, ImC2CMsgRecord.class, false);
            
            // 调用同步服务
            boolean result = dataSyncService.syncC2CMsgRecord(record, operationType);
            
            if (result) {
                log.info("C2C消息记录同步处理成功，操作类型: {}, chatId: {}, msgId: {}", 
                        operationType, record.getChatId(), record.getMsgId());
            } else {
                log.error("C2C消息记录同步处理失败，操作类型: {}, chatId: {}, msgId: {}", 
                        operationType, record.getChatId(), record.getMsgId());
            }
            
        } catch (Exception e) {
            log.error("处理C2C消息记录同步失败", e);
        }
    }
}
