package com.xzll.datasync.consumer;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.xzll.datasync.entity.ImC2CMsgRecordES;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.xcontent.XContentType;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static com.xzll.common.constant.ImConstant.*;
import static com.xzll.common.constant.ImConstant.TableConstant.*;

/**
 * 批量数据同步消费者
 * 直接利用RocketMQ的批量消费能力，实现高性能批量写入ES
 * 
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Component
public class BatchDataSyncConsumer implements MessageListenerConcurrently {
    
    @Resource
    private RestHighLevelClient restHighLevelClient;
    
    // 统计信息
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong totalBatches = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    
    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> msgs, ConsumeConcurrentlyContext context) {
        if (msgs == null || msgs.isEmpty()) {
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        }
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int errorCount = 0;
        
        try {
            log.info("开始批量消费消息，消息数量: {}, 批次ID: {}", msgs.size(), context.getMessageQueue().getQueueId());
            
            // 按操作类型分组，预分配容量以提高性能
            List<ImC2CMsgRecordES> insertRecords = new ArrayList<>(msgs.size());
            List<ImC2CMsgRecordES> updateRecords = new ArrayList<>(msgs.size());
            
            // 批量解析所有消息
            for (MessageExt msg : msgs) {
                try {
                    // 解析消息内容
                    String messageBody = new String(msg.getBody());
                    Map<String, Object> messageData = JSONUtil.toBean(messageBody, Map.class);
                    
                    // 解析ClusterEvent
                    String eventData = (String) messageData.get("data");
                    Map<String, Object> eventMap = JSONUtil.toBean(eventData, Map.class);
                    
                    String operationType = (String) eventMap.get("operationType");
                    String dataType = (String) eventMap.get("dataType");
                    
                    if (DATA_TYPE_C2C_MSG_RECORD.equals(dataType)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> recordData = (Map<String, Object>) eventMap.get("data");
                        
                        // 转换为ES实体
                        ImC2CMsgRecordES esRecord = new ImC2CMsgRecordES();
                        BeanUtil.copyProperties(recordData, esRecord);
                        esRecord.buildId();
                        
                        // 按操作类型分组，减少ES操作次数
                        if (OPERATION_TYPE_SAVE.equals(operationType)) {
                            insertRecords.add(esRecord);
                        } else {
                            updateRecords.add(esRecord);
                        }
                        
                        successCount++;
                    } else {
                        log.warn("未知的数据类型: {}, 消息ID: {}", dataType, msg.getMsgId());
                    }
                    
                } catch (Exception e) {
                    log.error("解析消息失败，消息ID: {}, 消息内容: {}", msg.getMsgId(), new String(msg.getBody()), e);
                    errorCount++;
                }
            }
            
            // 批量写入ES - 使用 RestHighLevelClient 的 BulkRequest，性能更高
            if (!insertRecords.isEmpty()) {
                try {
                    BulkRequest bulkRequest = new BulkRequest();
                    for (ImC2CMsgRecordES record : insertRecords) {
                        IndexRequest indexRequest = new IndexRequest(IM_C2C_MSG_RECORD)
                                .id(record.getId())
                                .source(JSONUtil.toJsonStr(record), XContentType.JSON);
                        bulkRequest.add(indexRequest);
                    }
                    
                    BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                    if (bulkResponse.hasFailures()) {
                        log.warn("批量插入ES存在部分失败，总数: {}, 失败数: {}", 
                                insertRecords.size(), bulkResponse.getItems().length - bulkResponse.getItems().length);
                    }
                    log.debug("批量插入ES成功，数量: {}, 索引: im_c2c_msg_record", insertRecords.size());
                } catch (Exception e) {
                    log.error("批量插入ES失败，数量: {}", insertRecords.size(), e);
                    errorCount += insertRecords.size();
                }
            }
            
            if (!updateRecords.isEmpty()) {
                try {
                    BulkRequest bulkRequest = new BulkRequest();
                    for (ImC2CMsgRecordES record : updateRecords) {
                        UpdateRequest updateRequest = new UpdateRequest(IM_C2C_MSG_RECORD, record.getId())
                                .doc(JSONUtil.toJsonStr(record), XContentType.JSON)
                                .docAsUpsert(true);
                        bulkRequest.add(updateRequest);
                    }
                    
                    BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
                    if (bulkResponse.hasFailures()) {
                        log.warn("批量更新ES存在部分失败，总数: {}, 失败数: {}", 
                                updateRecords.size(), bulkResponse.getItems().length - bulkResponse.getItems().length);
                    }
                    log.debug("批量更新ES成功，数量: {}, 索引: im_c2c_msg_record", updateRecords.size());
                } catch (Exception e) {
                    log.error("批量更新ES失败，数量: {}", updateRecords.size(), e);
                    errorCount += updateRecords.size();
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            totalProcessed.addAndGet(successCount);
            totalBatches.incrementAndGet();
            
            // 性能统计日志
            if (msgs.size() > 10) { // 只对大批量记录详细日志
                log.info("大批量消费完成，消息数量: {}, 成功: {}, 失败: {}, 耗时: {}ms, 平均每条: {}ms", 
                        msgs.size(), successCount, errorCount, duration, 
                        duration / (double) msgs.size());
            } else {
                log.debug("批量消费完成，消息数量: {}, 成功: {}, 失败: {}, 耗时: {}ms", 
                        msgs.size(), successCount, errorCount, duration);
            }
            
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
            
        } catch (Exception e) {
            log.error("批量消费消息失败，消息数量: {}, 批次ID: {}", msgs.size(), 
                    context.getMessageQueue().getQueueId(), e);
            totalErrors.addAndGet(msgs.size());
            return ConsumeConcurrentlyStatus.RECONSUME_LATER;
        }
    }
    
    /**
     * 获取统计信息
     */
    public String getStats() {
        return String.format("已处理消息: %d, 批次: %d, 错误: %d", 
                totalProcessed.get(), totalBatches.get(), totalErrors.get());
    }
} 