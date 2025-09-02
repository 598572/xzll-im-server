package com.xzll.business.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.stereotype.Service;
import com.xzll.common.rocketmq.ClusterEvent;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息HBase存储实现类
 */
@Service
@Slf4j
public class ImC2CMsgRecordHBaseServiceImpl implements ImC2CMsgRecordHBaseService {

    // HBase表名
    private static final String TABLE_NAME = "im_c2c_msg_record";
    // 列族名
    private static final String COLUMN_FAMILY = "info";
    // 列名
    private static final String COLUMN_FROM_USER_ID = "from_user_id";
    private static final String COLUMN_TO_USER_ID = "to_user_id";
    private static final String COLUMN_MSG_ID = "msg_id";
    private static final String COLUMN_MSG_FORMAT = "msg_format";
    private static final String COLUMN_MSG_CONTENT = "msg_content";
    private static final String COLUMN_MSG_CREATE_TIME = "msg_create_time";
    private static final String COLUMN_RETRY_COUNT = "retry_count";
    private static final String COLUMN_MSG_STATUS = "msg_status";
    private static final String COLUMN_WITHDRAW_FLAG = "withdraw_flag";
    private static final String COLUMN_CHAT_ID = "chat_id";
    private static final String COLUMN_CREATE_TIME = "create_time";
    private static final String COLUMN_UPDATE_TIME = "update_time";

    @Resource
    private Connection hbaseConnection;
    
    @Resource
    private ConversionService conversionService;
    
    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;
    
    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;

    /**
     * 构建RowKey
     * 格式：chatId + "_" + msgId
     * 雪花算法生成的 msgId 本身就具有时间顺序性，可以保证同一会话的消息按时间顺序排列
     */
    private String buildRowKey(String chatId, String msgId) {
        return chatId + "_" + msgId;
    }

    /**
     * 构建Put对象
     */
    private Put buildPut(ImC2CMsgRecord imC2CMsgRecord) {
        String rowKey = buildRowKey(imC2CMsgRecord.getChatId(), imC2CMsgRecord.getMsgId());
        Put put = new Put(Bytes.toBytes(rowKey));
        
        // 添加列数据
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_FROM_USER_ID), 
                Bytes.toBytes(imC2CMsgRecord.getFromUserId()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_TO_USER_ID), 
                Bytes.toBytes(imC2CMsgRecord.getToUserId()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_ID), 
                Bytes.toBytes(imC2CMsgRecord.getMsgId()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_FORMAT), 
                Bytes.toBytes(imC2CMsgRecord.getMsgFormat()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_CONTENT), 
                Bytes.toBytes(imC2CMsgRecord.getMsgContent()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_CREATE_TIME), 
                Bytes.toBytes(imC2CMsgRecord.getMsgCreateTime()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_RETRY_COUNT), 
                Bytes.toBytes(imC2CMsgRecord.getRetryCount()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_STATUS), 
                Bytes.toBytes(imC2CMsgRecord.getMsgStatus()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_WITHDRAW_FLAG), 
                Bytes.toBytes(imC2CMsgRecord.getWithdrawFlag()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_CHAT_ID), 
                Bytes.toBytes(imC2CMsgRecord.getChatId()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_CREATE_TIME), 
                Bytes.toBytes(imC2CMsgRecord.getCreateTime()));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), 
                Bytes.toBytes(imC2CMsgRecord.getUpdateTime()));
        
        return put;
    }

    /**
     * 从Result构建ImC2CMsgRecord对象
     */
    private ImC2CMsgRecord buildImC2CMsgRecord(Result result) {
        if (result == null || result.isEmpty()) {
            return null;
        }
        
        ImC2CMsgRecord record = new ImC2CMsgRecord();
        
        for (Cell cell : result.rawCells()) {
            String column = Bytes.toString(CellUtil.cloneQualifier(cell));
            String value = Bytes.toString(CellUtil.cloneValue(cell));
            
            switch (column) {
                case COLUMN_FROM_USER_ID:
                    record.setFromUserId(value);
                    break;
                case COLUMN_TO_USER_ID:
                    record.setToUserId(value);
                    break;
                case COLUMN_MSG_ID:
                    record.setMsgId(value);
                    break;
                case COLUMN_MSG_FORMAT:
                    record.setMsgFormat(value);
                    break;
                case COLUMN_MSG_CONTENT:
                    record.setMsgContent(value);
                    break;
                case COLUMN_MSG_CREATE_TIME:
                    record.setMsgCreateTime(value);
                    break;
                case COLUMN_RETRY_COUNT:
                    record.setRetryCount(Integer.parseInt(value));
                    break;
                case COLUMN_MSG_STATUS:
                    record.setMsgStatus(Integer.parseInt(value));
                    break;
                case COLUMN_WITHDRAW_FLAG:
                    record.setWithdrawFlag(Integer.parseInt(value));
                    break;
                case COLUMN_CHAT_ID:
                    record.setChatId(value);
                    break;
                case COLUMN_CREATE_TIME:
                    record.setCreateTime(value);
                    break;
                case COLUMN_UPDATE_TIME:
                    record.setUpdateTime(value);
                    break;
            }
        }
        
        return record;
    }

    @Override
    public boolean saveC2CMsgRecord(ImC2CMsgRecord imC2CMsgRecord) {
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            Put put = buildPut(imC2CMsgRecord);
            table.put(put);
            table.close();
            
            // 发送数据同步消息
            sendDataSyncMessage(imC2CMsgRecord, "SAVE");
            
            log.info("C2C消息记录保存成功，chatId: {}, msgId: {}", 
                    imC2CMsgRecord.getChatId(), imC2CMsgRecord.getMsgId());
            return true;
        } catch (IOException e) {
            log.error("保存C2C消息记录失败，chatId: {}, msgId: {}", 
                    imC2CMsgRecord.getChatId(), imC2CMsgRecord.getMsgId(), e);
            return false;
        }
    }

    @Override
    public ImC2CMsgRecord getC2CMsgRecordByChatIdAndMsgId(String chatId, String msgId) {
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            String rowKey = buildRowKey(chatId, msgId);
            Get get = new Get(Bytes.toBytes(rowKey));
            Result result = table.get(get);
            table.close();
            
            return buildImC2CMsgRecord(result);
        } catch (IOException e) {
            log.error("获取C2C消息记录失败，chatId: {}, msgId: {}", chatId, msgId, e);
            return null;
        }
    }

    @Override
    public boolean updateC2CMsgRecordStatus(String chatId, String msgId, Integer status) {
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            String rowKey = buildRowKey(chatId, msgId);
            
            // 先获取现有记录
            Get get = new Get(Bytes.toBytes(rowKey));
            Result result = table.get(get);
            
            if (result.isEmpty()) {
                log.warn("要更新的C2C消息记录不存在，chatId: {}, msgId: {}", chatId, msgId);
                table.close();
                return false;
            }
            
            // 构建更新
            Put put = new Put(Bytes.toBytes(rowKey));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_STATUS), 
                    Bytes.toBytes(status.toString()));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), 
                    Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            
            table.put(put);
            table.close();
            
            // 获取完整记录用于同步
            ImC2CMsgRecord record = buildImC2CMsgRecord(result);
            if (record != null) {
                record.setMsgStatus(status);
                record.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                // 发送数据同步消息
                sendDataSyncMessage(record, "UPDATE_STATUS");
            }
            
            log.info("C2C消息记录状态更新成功，chatId: {}, msgId: {}, status: {}", 
                    chatId, msgId, status);
            return true;
        } catch (IOException e) {
            log.error("更新C2C消息记录状态失败，chatId: {}, msgId: {}, status: {}", 
                    chatId, msgId, status, e);
            return false;
        }
    }

    @Override
    public boolean updateC2CMsgRecordWithdraw(String chatId, String msgId, Integer withdrawFlag) {
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            String rowKey = buildRowKey(chatId, msgId);
            
            // 先获取现有记录
            Get get = new Get(Bytes.toBytes(rowKey));
            Result result = table.get(get);
            
            if (result.isEmpty()) {
                log.warn("要更新的C2C消息记录不存在，chatId: {}, msgId: {}", chatId, msgId);
                table.close();
                return false;
            }
            
            // 构建更新
            Put put = new Put(Bytes.toBytes(rowKey));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_WITHDRAW_FLAG), 
                    Bytes.toBytes(withdrawFlag.toString()));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), 
                    Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
            
            table.put(put);
            table.close();
            
            // 获取完整记录用于同步
            ImC2CMsgRecord record = buildImC2CMsgRecord(result);
            if (record != null) {
                record.setWithdrawFlag(withdrawFlag);
                record.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                // 发送数据同步消息
                sendDataSyncMessage(record, "UPDATE_WITHDRAW");
            }
            
            log.info("C2C消息记录撤回状态更新成功，chatId: {}, msgId: {}, withdrawFlag: {}", 
                    chatId, msgId, withdrawFlag);
            return true;
        } catch (IOException e) {
            log.error("更新C2C消息记录撤回状态失败，chatId: {}, msgId: {}, withdrawFlag: {}", 
                    chatId, msgId, withdrawFlag, e);
            return false;
        }
    }

    /**
     * 发送数据同步消息到RocketMQ
     * 使用RocketMqProducerWrap统一处理
     * @param imC2CMsgRecord 消息记录
     * @param operationType 操作类型：SAVE, UPDATE_STATUS, UPDATE_WITHDRAW
     */
    private void sendDataSyncMessage(ImC2CMsgRecord imC2CMsgRecord, String operationType) {
        try {
            // 构建业务数据
            Map<String, Object> businessData = new HashMap<>();
            businessData.put("operationType", operationType);
            businessData.put("dataType", "C2C_MSG_RECORD");
            businessData.put("data", imC2CMsgRecord);
            businessData.put("timestamp", System.currentTimeMillis());
            businessData.put("chatId", imC2CMsgRecord.getChatId());
            businessData.put("msgId", imC2CMsgRecord.getMsgId());
            
            // 创建ClusterEvent
            ClusterEvent clusterEvent = new ClusterEvent();
            clusterEvent.setData(JSONUtil.toJsonStr(businessData));
            clusterEvent.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_DATA_SYNC);
            clusterEvent.setBalanceId(imC2CMsgRecord.getChatId()); // 使用chatId作为负载均衡ID
            
            // 发送消息到RocketMQ
            boolean result = rocketMqProducerWrap.sendClusterEvent(
                ImConstant.TopicConstant.XZLL_DATA_SYNC_TOPIC, 
                clusterEvent, 
                imC2CMsgRecord.getMsgId()
            );
            
            log.info("数据同步消息已发送到RocketMQ，操作类型: {}, chatId: {}, msgId: {}, 结果: {}", 
                    operationType, imC2CMsgRecord.getChatId(), imC2CMsgRecord.getMsgId(), result);
                    
        } catch (Exception e) {
            log.error("发送数据同步消息到RocketMQ失败，操作类型: {}, chatId: {}, msgId: {}", 
                    operationType, imC2CMsgRecord.getChatId(), imC2CMsgRecord.getMsgId(), e);
            // 这里可以考虑重试机制，但不影响主流程
        }
    }

    /**
     * 根据chatId和msgId获取消息
     */
    private ImC2CMsgRecord getC2CMsgRecordByChatIdAndMsgId(String chatId, String msgId) {
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            String rowKey = buildRowKey(chatId, msgId);
            Get get = new Get(Bytes.toBytes(rowKey));
            Result result = table.get(get);
            table.close();
            
            return buildImC2CMsgRecord(result);
        } catch (IOException e) {
            log.error("获取C2C消息记录失败，chatId: {}, msgId: {}", chatId, msgId, e);
            return null;
        }
    }
}