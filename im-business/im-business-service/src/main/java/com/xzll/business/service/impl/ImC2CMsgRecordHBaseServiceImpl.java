package com.xzll.business.service.impl;

import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.business.config.HBaseTableUtil;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.rocketmq.ClusterEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import com.xzll.business.cluster.mq.RocketMqProducerWrap;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.xzll.common.constant.ImConstant.*;
import static com.xzll.common.constant.ImConstant.TopicConstant.XZLL_DATA_SYNC_TOPIC;
import static com.xzll.common.constant.ImConstant.TableConstant.IM_C2C_MSG_RECORD;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息HBase存储实现类
 * 
 * 数据流程说明：
 * 1. HBase写入/更新成功
 * 2. 发送消息到RocketMQ（使用顺序发送保证同一会话消息顺序）
 * 3. data-sync服务消费RocketMQ消息
 * 4. data-sync服务负责写入ES
 * 
 * 优势：
 * - 保证消息顺序性
 * - 支持高并发写入
 * - 数据持久化可靠
 * - 支持消息状态更新
 */
@Service
@Slf4j
public class ImC2CMsgRecordHBaseServiceImpl implements ImC2CMsgRecordHBaseService {

    // HBase表名
    private static final String TABLE_NAME = IM_C2C_MSG_RECORD;
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
    private static final String COLUMN_CHAT_ID = "chat_id";
    private static final String COLUMN_CREATE_TIME = "create_time";
    private static final String COLUMN_UPDATE_TIME = "update_time";

    // 线程池用于并发查询 - 使用动态线程池
    private static final ExecutorService QUERY_EXECUTOR = new ThreadPoolExecutor(
            5, // 核心线程数
            20, // 最大线程数  
            60L, TimeUnit.SECONDS, // 空闲线程存活时间
            new LinkedBlockingQueue<>(100), // 任务队列
            new ThreadFactory() {
                private final AtomicInteger threadNumber = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "hbase-query-" + threadNumber.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            new ThreadPoolExecutor.CallerRunsPolicy() // 拒绝策略
    );

    @Resource
    private Connection hbaseConnection;

    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;

    @Resource
    private ConversionService conversionService;

    @Override
    public boolean saveC2CMsg(C2CSendMsgAO dto) {
        log.info("保存C2C消息到HBase: {}", JSONUtil.toJsonStr(dto));
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 构建RowKey: chatId + "-" + msgId
                String rowKey = dto.getChatId() + "-" + dto.getMsgId();
                
                // 构建Put对象
                Put put = new Put(Bytes.toBytes(rowKey));
                
                // 添加列数据
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_FROM_USER_ID), Bytes.toBytes(dto.getFromUserId()));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_TO_USER_ID), Bytes.toBytes(dto.getToUserId()));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_ID), Bytes.toBytes(dto.getMsgId()));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_FORMAT), Bytes.toBytes(String.valueOf(dto.getMsgFormat())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_CONTENT), Bytes.toBytes(dto.getMsgContent()));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_CREATE_TIME), Bytes.toBytes(String.valueOf(dto.getMsgCreateTime())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_RETRY_COUNT), Bytes.toBytes("0"));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_STATUS), Bytes.toBytes(String.valueOf(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_CHAT_ID), Bytes.toBytes(dto.getChatId()));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_CREATE_TIME), Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                
                // 执行插入
                table.put(put);
                
                log.info("C2C消息保存到HBase成功: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId());
                
                // 发送到RocketMQ进行数据同步
                sendToRocketMQ(dto);
                
                return true;
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("保存C2C消息到HBase失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    @Override
    public boolean updateC2CMsgOffLineStatus(C2COffLineMsgAO dto) {
        log.info("更新C2C消息离线状态: {}", JSONUtil.toJsonStr(dto));
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 构建RowKey
                String rowKey = dto.getChatId() + "-" + dto.getMsgId();
                
                // 构建Put对象更新消息状态
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_STATUS), Bytes.toBytes(String.valueOf(dto.getMsgStatus())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                
                // 执行更新
                table.put(put);
                
                log.info("C2C消息离线状态更新成功: chatId={}, msgId={}, status={}", dto.getChatId(), dto.getMsgId(), dto.getMsgStatus());
                
                // 发送数据同步消息
                sendDataSyncMessage(OPERATION_TYPE_UPDATE_STATUS, dto.getChatId(), dto.getMsgId(), dto);
                
                return true;
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("更新C2C消息离线状态失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    @Override
    public boolean updateC2CMsgReceivedStatus(C2CReceivedMsgAckAO dto) {
        log.info("更新C2C消息接收状态: {}", JSONUtil.toJsonStr(dto));
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 构建RowKey
                String rowKey = dto.getChatId() + "-" + dto.getMsgId();
                
                // 构建Put对象更新消息状态
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_STATUS), Bytes.toBytes(String.valueOf(dto.getMsgStatus())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                
                // 执行更新
                table.put(put);
                
                log.info("C2C消息接收状态更新成功: chatId={}, msgId={}, status={}", dto.getChatId(), dto.getMsgId(), dto.getMsgStatus());
                
                // 发送数据同步消息
                sendDataSyncMessage(OPERATION_TYPE_UPDATE_STATUS, dto.getChatId(), dto.getMsgId(), dto);
                
                return true;
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("更新C2C消息接收状态失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    @Override
    public boolean updateC2CMsgWithdrawStatus(C2CWithdrawMsgAO dto) {
        log.info("更新C2C消息撤回状态: {}", JSONUtil.toJsonStr(dto));
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 构建RowKey
                String rowKey = dto.getChatId() + "-" + dto.getMsgId();
                
                // 构建Put对象更新消息状态
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_STATUS), Bytes.toBytes(String.valueOf(MsgStatusEnum.MsgStatus.FAIL.getCode())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                
                // 执行更新
                table.put(put);
                
                log.info("C2C消息撤回状态更新成功: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId());
                
                // 发送数据同步消息
                sendDataSyncMessage(OPERATION_TYPE_UPDATE_WITHDRAW, dto.getChatId(), dto.getMsgId(), dto);
                
                return true;
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("更新C2C消息撤回状态失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    @Override
    public ImC2CMsgRecord getMessageByMsgId(String chatId, String msgId) {
        log.info("根据消息ID查询消息记录: chatId={}, msgId={}", chatId, msgId);
        
        if (StringUtils.isBlank(chatId) || StringUtils.isBlank(msgId)) {
            log.warn("chatId或msgId为空，无法查询消息记录");
            return null;
        }
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 使用HBaseTableUtil创建Get请求
                Get get = HBaseTableUtil.createMessageGet(chatId, msgId);
                Result result = table.get(get);
                
                if (result.isEmpty()) {
                    log.warn("未找到消息记录: chatId={}, msgId={}", chatId, msgId);
                    return null;
                }
                
                ImC2CMsgRecord msgRecord = convertResultToImC2CMsgRecord(result);
                log.info("成功查询到消息记录: chatId={}, msgId={}", chatId, msgId);
                return msgRecord;
                
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("根据消息ID查询消息记录失败: chatId={}, msgId={}", chatId, msgId, e);
            return null;
        }
    }

    @Override
    public Map<String, ImC2CMsgRecord> batchGetLastMessages(Map<String, String> chatMsgIds) {
        log.info("批量查询最后一条消息记录: chatMsgIds={}", chatMsgIds);
        Map<String, ImC2CMsgRecord> lastMsgMap = new HashMap<>();
        
        if (chatMsgIds == null || chatMsgIds.isEmpty()) {
            return lastMsgMap;
        }
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 构建批量Get请求
                List<Get> getList = new ArrayList<>();
                for (Map.Entry<String, String> entry : chatMsgIds.entrySet()) {
                    String chatId = entry.getKey();
                    String msgId = entry.getValue();
                    if (StringUtils.isNotBlank(chatId) && StringUtils.isNotBlank(msgId)) {
                        Get get = HBaseTableUtil.createMessageGet(chatId, msgId);
                        getList.add(get);
                    }
                }
                
                if (getList.isEmpty()) {
                    return lastMsgMap;
                }
                
                // 批量查询
                Result[] results = table.get(getList);
                
                for (Result result : results) {
                    if (!result.isEmpty()) {
                        ImC2CMsgRecord msgRecord = convertResultToImC2CMsgRecord(result);
                        if (msgRecord != null) {
                            lastMsgMap.put(msgRecord.getChatId(), msgRecord);
                        }
                    }
                }
                
                log.info("批量查询最后一条消息记录完成，查询到{}条记录", lastMsgMap.size());
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("批量查询最后一条消息失败", e);
        }
        
        return lastMsgMap;
    }

    @Override
    public Map<String, ImC2CMsgRecord> batchGetLastMessagesByChatIds(List<String> chatIds) {
        log.info("批量查询每个会话的最后一条消息记录: chatIds={}", chatIds);
        Map<String, ImC2CMsgRecord> lastMsgMap = new HashMap<>();
        
        if (chatIds == null || chatIds.isEmpty()) {
            return lastMsgMap;
        }

        // 使用智能并发查询优化性能
        return batchGetLastMessagesConcurrent(chatIds);
    }

    /**
     * 并发查询每个会话的最后一条消息（推荐）
     * 性能：O(1) 时间复杂度，并发执行
     */
    private Map<String, ImC2CMsgRecord> batchGetLastMessagesConcurrent(List<String> chatIds) {
        log.info("使用并发查询方式，会话数量: {}", chatIds.size());
        
        // 根据会话数量选择最优策略
        if (chatIds.size() <= 5) {
            return batchGetLastMessagesOptimizedSmall(chatIds);
        } else if (chatIds.size() <= 50) {
            return batchGetLastMessagesOptimizedMedium(chatIds);
        } else {
            return batchGetLastMessagesOptimizedLarge(chatIds);
        }
    }
    
    /**
     * 小批量查询优化（1-5个会话）
     * 使用单个Table连接，减少连接开销
     */
    private Map<String, ImC2CMsgRecord> batchGetLastMessagesOptimizedSmall(List<String> chatIds) {
        Map<String, ImC2CMsgRecord> result = new HashMap<>();
        
        try (Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME))) {
            for (String chatId : chatIds) {
                if (StringUtils.isNotBlank(chatId)) {
                    try {
                        ImC2CMsgRecord lastMsg = getLastMessageByChatId(table, chatId);
                        if (lastMsg != null) {
                            result.put(chatId, lastMsg);
                        }
                    } catch (Exception e) {
                        log.error("查询会话最后消息失败: chatId={}", chatId, e);
                    }
                }
            }
        } catch (Exception e) {
            log.error("小批量查询失败", e);
        }
        
        log.info("小批量查询完成，查询到{}条记录", result.size());
        return result;
    }
    
    /**
     * 中等批量查询优化（6-50个会话）  
     * 使用ConcurrentHashMap + 连接复用
     */
    private Map<String, ImC2CMsgRecord> batchGetLastMessagesOptimizedMedium(List<String> chatIds) {
        ConcurrentHashMap<String, ImC2CMsgRecord> lastMsgMap = new ConcurrentHashMap<>();
        
        // 预先获取Table连接（在主线程中）
        Table table = null;
        try {
            table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            final Table finalTable = table;
            
            // 创建并发任务
            List<CompletableFuture<Void>> futures = chatIds.stream()
                    .filter(StringUtils::isNotBlank)
                    .map(chatId -> CompletableFuture.runAsync(() -> {
                        try {
                            ImC2CMsgRecord lastMsg = getLastMessageByChatId(finalTable, chatId);
                            if (lastMsg != null) {
                                lastMsgMap.put(chatId, lastMsg);
                            }
                        } catch (Exception e) {
                            log.error("查询会话最后消息失败: chatId={}", chatId, e);
                        }
                    }, QUERY_EXECUTOR))
                    .collect(Collectors.toList());

            // 等待所有任务完成
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            
        } catch (Exception e) {
            log.error("中等批量查询失败", e);
        } finally {
            if (table != null) {
                try {
                    table.close();
                } catch (IOException e) {
                    log.error("关闭Table连接失败", e);
                }
            }
        }

        log.info("中等批量查询完成，查询到{}条记录", lastMsgMap.size());
        return lastMsgMap;
    }
    
    /**
     * 大批量查询优化（50+个会话）
     * 使用分批 + 连接池 + CompletableFuture组合
     */
    private Map<String, ImC2CMsgRecord> batchGetLastMessagesOptimizedLarge(List<String> chatIds) {
        ConcurrentHashMap<String, ImC2CMsgRecord> lastMsgMap = new ConcurrentHashMap<>();
        
        // 分批处理，每批20个
        int batchSize = 20;
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < chatIds.size(); i += batchSize) {
            batches.add(chatIds.subList(i, Math.min(i + batchSize, chatIds.size())));
        }
        
        log.info("大批量查询分为{}批处理", batches.size());
        
        // 并发处理每批
        List<CompletableFuture<Void>> batchFutures = batches.stream()
                .map(batch -> CompletableFuture.runAsync(() -> {
                    try (Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME))) {
                        // 在每批内并发查询
                        List<CompletableFuture<Void>> futures = batch.stream()
                                .filter(StringUtils::isNotBlank)
                                .map(chatId -> CompletableFuture.runAsync(() -> {
                                    try {
                                        ImC2CMsgRecord lastMsg = getLastMessageByChatId(table, chatId);
                                        if (lastMsg != null) {
                                            lastMsgMap.put(chatId, lastMsg);
                                        }
                                    } catch (Exception e) {
                                        log.error("查询会话最后消息失败: chatId={}", chatId, e);
                                    }
                                }, QUERY_EXECUTOR))
                                .collect(Collectors.toList());
                        
                        // 等待当前批次完成
                        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
                        
                    } catch (Exception e) {
                        log.error("批次查询失败: batchSize={}", batch.size(), e);
                    }
                }, QUERY_EXECUTOR))
                .collect(Collectors.toList());
        
        // 等待所有批次完成
        try {
            CompletableFuture.allOf(batchFutures.toArray(new CompletableFuture[0])).join();
        } catch (Exception e) {
            log.error("大批量查询执行失败", e);
        }

        log.info("大批量查询完成，查询到{}条记录", lastMsgMap.size());
        return lastMsgMap;
    }
    
    /**
     * 获取指定会话的最后一条消息
     * 优化RowKey格式，正确扫描chatId开头的所有消息
     * 
     * @param table HBase表对象
     * @param chatId 会话ID
     * @return 最后一条消息记录
     */
    private ImC2CMsgRecord getLastMessageByChatId(Table table, String chatId) {
        try {
            // 使用反向扫描，从最新的消息开始扫描
            Scan scan = new Scan();
            
            // 正确的RowKey范围：chatId + "-" + msgId
            // 由于msgId是雪花算法，时间戳部分在开头，所以最新的消息RowKey最大
            scan.withStartRow(Bytes.toBytes(chatId + "-" + Long.MAX_VALUE)); // 从chatId-最大值开始
            scan.withStopRow(Bytes.toBytes(chatId + "-")); // 到chatId-结束
            
            scan.setReversed(true); // 反向扫描，获取最新的消息
            scan.setLimit(1); // 只取第一条（最新的）
            scan.setCaching(1);
            
            ResultScanner scanner = table.getScanner(scan);
            try {
                Result result = scanner.next();
                if (result != null && !result.isEmpty()) {
                    return convertResultToImC2CMsgRecord(result);
                }
            } finally {
                scanner.close();
            }
        } catch (Exception e) {
            log.error("获取会话最后一条消息失败: chatId={}", chatId, e);
        }
        
        return null;
    }

    /**
     * 将HBase查询结果转换为ImC2CMsgRecord对象
     */
    private ImC2CMsgRecord convertResultToImC2CMsgRecord(Result result) {
        try {
            ImC2CMsgRecord msgRecord = new ImC2CMsgRecord();
            
            // 从RowKey中提取chatId和msgId
            String rowKey = Bytes.toString(result.getRow());
            // RowKey格式：chatId + "-" + msgId
            String[] parts = rowKey.split("-", 2);
            if (parts.length >= 2) {
                msgRecord.setChatId(parts[0]);
                msgRecord.setMsgId(parts[1]);
            }
            
            for (Cell cell : result.rawCells()) {
                String column = Bytes.toString(CellUtil.cloneQualifier(cell));
                byte[] value = CellUtil.cloneValue(cell);
                
                switch (column) {
                    case COLUMN_FROM_USER_ID:
                        msgRecord.setFromUserId(Bytes.toString(value));
                        break;
                    case COLUMN_TO_USER_ID:
                        msgRecord.setToUserId(Bytes.toString(value));
                        break;
                    case COLUMN_MSG_ID:
                        msgRecord.setMsgId(Bytes.toString(value));
                        break;
                    case COLUMN_MSG_FORMAT:
                        msgRecord.setMsgFormat(Integer.valueOf(Bytes.toString(value)));
                        break;
                    case COLUMN_MSG_CONTENT:
                        msgRecord.setMsgContent(Bytes.toString(value));
                        break;
                    case COLUMN_MSG_CREATE_TIME:
                        msgRecord.setMsgCreateTime(Long.valueOf(Bytes.toString(value)));
                        break;
                    case COLUMN_RETRY_COUNT:
                        msgRecord.setRetryCount(Integer.valueOf(Bytes.toString(value)));
                        break;
                    case COLUMN_MSG_STATUS:
                        msgRecord.setMsgStatus(Integer.valueOf(Bytes.toString(value)));
                        break;
                    case COLUMN_CHAT_ID:
                        msgRecord.setChatId(Bytes.toString(value));
                        break;
                }
            }
            
            return msgRecord;
        } catch (Exception e) {
            log.error("转换HBase查询结果失败", e);
            return null;
        }
    }

    /**
     * 发送消息到RocketMQ进行数据同步
     */
    private void sendToRocketMQ(C2CSendMsgAO dto) {
        sendDataSyncMessage(dto, OPERATION_TYPE_SAVE);
    }

    /**
     * 发送数据同步消息到RocketMQ
     */
    private void sendDataSyncMessage(C2CSendMsgAO dto, String operationType) {
        try {
            log.info("开始发送数据同步消息，operationType: {}, chatId: {}, msgId: {}", 
                    operationType, dto.getChatId(), dto.getMsgId());
            
            // 构造数据同步消息格式
            Map<String, Object> dataSyncMessage = new HashMap<>();
            dataSyncMessage.put("operationType", operationType);
            dataSyncMessage.put("dataType", DATA_TYPE_C2C_MSG_RECORD);
            dataSyncMessage.put("data", dto);
            
            ClusterEvent event = new ClusterEvent();
            event.setClusterEventType(ClusterEventTypeConstant.C2C_DATA_SYNC);
            event.setData(JSONUtil.toJsonStr(dataSyncMessage));
            
            rocketMqProducerWrap.sendClusterEvent(XZLL_DATA_SYNC_TOPIC, event, dto.getChatId());
            
            log.info("数据同步消息发送成功，topic: {}, operationType: {}, chatId: {}, msgId: {}", 
                    XZLL_DATA_SYNC_TOPIC, operationType, dto.getChatId(), dto.getMsgId());
        } catch (Exception e) {
            log.error("数据同步消息发送失败，operationType: {}, chatId: {}, msgId: {}", 
                    operationType, dto.getChatId(), dto.getMsgId(), e);
        }
    }

    /**
     * 发送通用数据同步消息到RocketMQ
     */
    private void sendDataSyncMessage(String operationType, String chatId, String msgId, Object data) {
        try {
            log.info("开始发送数据同步消息，operationType: {}, chatId: {}, msgId: {}", 
                    operationType, chatId, msgId);
            
            // 构造数据同步消息格式
            Map<String, Object> dataSyncMessage = new HashMap<>();
            dataSyncMessage.put("operationType", operationType);
            dataSyncMessage.put("dataType", DATA_TYPE_C2C_MSG_RECORD);
            dataSyncMessage.put("data", data);
            
            ClusterEvent event = new ClusterEvent();
            event.setClusterEventType(ClusterEventTypeConstant.C2C_DATA_SYNC);
            event.setData(JSONUtil.toJsonStr(dataSyncMessage));
            
            rocketMqProducerWrap.sendClusterEvent(XZLL_DATA_SYNC_TOPIC, event, chatId);
            
            log.info("数据同步消息发送成功，topic: {}, operationType: {}, chatId: {}, msgId: {}", 
                    XZLL_DATA_SYNC_TOPIC, operationType, chatId, msgId);
        } catch (Exception e) {
            log.error("数据同步消息发送失败，operationType: {}, chatId: {}, msgId: {}", 
                    operationType, chatId, msgId, e);
        }
    }
}
