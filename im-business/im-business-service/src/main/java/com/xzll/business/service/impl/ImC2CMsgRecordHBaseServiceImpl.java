package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.xzll.business.config.HBaseTableUtil;
import com.xzll.business.dto.request.ChatHistoryQueryDTO;
import com.xzll.business.dto.response.ChatHistoryResponseDTO;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.util.msgId.SnowflakeIdService;
import com.xzll.business.util.C2CMessageRowKeyUtil;
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
import java.util.ArrayList;
import java.util.NavigableMap;
import org.apache.commons.lang3.StringUtils;
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
 * 
 * 本地开发支持：
 * - 当 hbase.enabled=false 时，该 Bean 不会被创建
 * - 业务逻辑应使用 @Autowired(required = false) 注入
 */
@Service
@Slf4j
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
    name = "hbase.enabled", 
    havingValue = "true", 
    matchIfMissing = false
)
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
    private static final String COLUMN_WITHDRAW_FLAG = "withdraw_flag"; // 撤回标志：0-未撤回，1-已撤回
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
                // 构建RowKey
                String rowKey = C2CMessageRowKeyUtil.generateRowKey(dto.getChatId(), dto.getMsgId());
                
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
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_WITHDRAW_FLAG), Bytes.toBytes(String.valueOf(MsgStatusEnum.MsgWithdrawStatus.NO.getCode()))); // 默认未撤回
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
                String rowKey = C2CMessageRowKeyUtil.generateRowKey(dto.getChatId(), dto.getMsgId());
                
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
                String rowKey = C2CMessageRowKeyUtil.generateRowKey(dto.getChatId(), dto.getMsgId());
                
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
                String rowKey = C2CMessageRowKeyUtil.generateRowKey(dto.getChatId(), dto.getMsgId());
                
                // 构建Put对象更新撤回标志
                //使用单独的withdraw_flag字段
                //撤回状态和消息状态是独立的维度，可以同时存在（如：已读+已撤回）
                Put put = new Put(Bytes.toBytes(rowKey));
                Integer withdrawFlag = dto.getWithdrawFlag() != null ? dto.getWithdrawFlag() : MsgStatusEnum.MsgWithdrawStatus.YES.getCode();
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_WITHDRAW_FLAG), Bytes.toBytes(String.valueOf(withdrawFlag)));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                
                // 执行更新
                table.put(put);
                
                log.info("C2C消息撤回状态更新成功: chatId={}, msgId={}, withdrawFlag={}", dto.getChatId(), dto.getMsgId(), withdrawFlag);
                
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
            
            // 正确的RowKey范围：使用工具类生成标准格式
            // 由于msgId是雪花算法，时间戳部分在开头，所以最新的消息RowKey最大
            scan.withStartRow(Bytes.toBytes(C2CMessageRowKeyUtil.generateRowKey(chatId, String.valueOf(Long.MAX_VALUE)))); // 从chatId_最大值开始
            scan.withStopRow(Bytes.toBytes(C2CMessageRowKeyUtil.generateChatPrefix(chatId))); // 到chatId_前缀结束
            
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
            try {
                C2CMessageRowKeyUtil.RowKeyInfo rowKeyInfo = C2CMessageRowKeyUtil.parseRowKey(rowKey);
                msgRecord.setChatId(rowKeyInfo.getChatId());
                msgRecord.setMsgId(rowKeyInfo.getMsgId());
            } catch (Exception e) {
                log.warn("解析RowKey失败: {}", rowKey, e);
                // 降级处理：设置原始rowKey作为msgId，避免数据丢失
                msgRecord.setMsgId(rowKey);
            }
            
            for (Cell cell : result.rawCells()) {
                String column = Bytes.toString(CellUtil.cloneQualifier(cell));
                byte[] value = CellUtil.cloneValue(cell);
                String stringValue = Bytes.toString(value);
                
                switch (column) {
                    case COLUMN_FROM_USER_ID:
                        msgRecord.setFromUserId(stringValue);
                        break;
                    case COLUMN_TO_USER_ID:
                        msgRecord.setToUserId(stringValue);
                        break;
                    case COLUMN_MSG_ID:
                        msgRecord.setMsgId(stringValue);
                        break;
                    case COLUMN_MSG_FORMAT:
                        msgRecord.setMsgFormat(safeParseInteger(stringValue, 1)); // 默认文本格式
                        break;
                    case COLUMN_MSG_CONTENT:
                        msgRecord.setMsgContent(stringValue);
                        break;
                    case COLUMN_MSG_CREATE_TIME:
                        msgRecord.setMsgCreateTime(safeParseLong(stringValue, System.currentTimeMillis()));
                        break;
                    case COLUMN_RETRY_COUNT:
                        msgRecord.setRetryCount(safeParseInteger(stringValue, 0)); // 默认重试次数为0
                        break;
                    case COLUMN_MSG_STATUS:
                        msgRecord.setMsgStatus(safeParseInteger(stringValue, 1)); // 默认消息状态为已发送
                        break;
                    case COLUMN_WITHDRAW_FLAG:
                        msgRecord.setWithdrawFlag(safeParseInteger(stringValue, 0)); // 默认未撤回
                        break;
                    case COLUMN_CHAT_ID:
                        msgRecord.setChatId(stringValue);
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
     * 安全地将字符串转换为Integer，处理null和"null"字符串
     */
    private Integer safeParseInteger(String value, Integer defaultValue) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return defaultValue;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("无法将字符串 '{}' 转换为Integer，使用默认值 {}", value, defaultValue);
            return defaultValue;
        }
    }
    
    /**
     * 安全地将字符串转换为Long，处理null和"null"字符串
     */
    private Long safeParseLong(String value, Long defaultValue) {
        if (value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim())) {
            return defaultValue;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException e) {
            log.warn("无法将字符串 '{}' 转换为Long，使用默认值 {}", value, defaultValue);
            return defaultValue;
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

    /**
     * 根据会话id 查询聊天记录 用于c端点击进会话后的查询
     * 注意：   （点击进会话时   最近50条先查本地数据库，下拉 / 本地没消息时（保证多端同步） 才查此接口）
     *
     * @param queryDTO 查询条件
     * @return
     */
    @Override
    public ChatHistoryResponseDTO queryChatHistory(ChatHistoryQueryDTO queryDTO) {
        log.info("开始查询聊天历史记录: chatId={}, userId={}, lastMsgId={}, pageSize={}, startTime={}, endTime={}", 
                queryDTO.getChatId(), queryDTO.getUserId(), queryDTO.getLastMsgId(), 
                queryDTO.getPageSize(), queryDTO.getStartTime(), queryDTO.getEndTime());

        ChatHistoryResponseDTO response = new ChatHistoryResponseDTO();
        response.setMessages(new ArrayList<>());
        response.setHasMore(false);
        response.setCurrentPageSize(0);
        response.setTotalCount(0);

        if (StringUtils.isBlank(queryDTO.getChatId())) {
            log.warn("chatId为空，无法查询聊天历史");
            return response;
        }

        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 创建Scan对象
                Scan scan = createHistoryScan(queryDTO);
                
                // 执行扫描
                ResultScanner scanner = table.getScanner(scan);
                List<ChatHistoryResponseDTO.ChatMessageVO> messages = new ArrayList<>();
                
                try {
                    for (Result result : scanner) {
                        if (messages.size() >= queryDTO.getPageSize()) {
                            response.setHasMore(true);
                            break;
                        }
                        
                        ChatHistoryResponseDTO.ChatMessageVO messageVO = convertToMessageVO(result);
                        if (messageVO != null) {
                            messages.add(messageVO);
                        }
                    }
                } finally {
                    scanner.close();
                }
                
                // 设置响应数据
                response.setMessages(messages);
                response.setCurrentPageSize(messages.size());
                response.setTotalCount(messages.size());
                
                // 设置下一页的lastMsgId
                if (!messages.isEmpty() && response.getHasMore()) {
                    ChatHistoryResponseDTO.ChatMessageVO lastMsg = messages.get(messages.size() - 1);
                    response.setNextLastMsgId(lastMsg.getMsgId());
                }
                
                log.info("聊天历史查询完成: chatId={}, 查询到{}条记录, hasMore={}", 
                        queryDTO.getChatId(), messages.size(), response.getHasMore());
                
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("查询聊天历史记录失败: chatId={}, userId={}", 
                    queryDTO.getChatId(), queryDTO.getUserId(), e);
        }
        
        return response;
    }

    /**
     * 创建历史记录扫描器 - 支持基于RowKey的时间范围过滤优化
     */
    private Scan createHistoryScan(ChatHistoryQueryDTO queryDTO) {
        Scan scan;
        String chatIdPrefix = C2CMessageRowKeyUtil.generateChatPrefix(queryDTO.getChatId());
        
        // 计算基于时间范围的RowKey边界
        String timeBasedStartRow = null;
        String timeBasedEndRow = null;
        
        if (queryDTO.getStartTime() != null) {
            String startMsgId = SnowflakeIdService.generateMsgIdLowerBound(queryDTO.getStartTime());
            timeBasedStartRow = C2CMessageRowKeyUtil.generateTimeRangeRowKey(queryDTO.getChatId(), startMsgId);
        }
        
        if (queryDTO.getEndTime() != null) {
            String endMsgId = SnowflakeIdService.generateMsgIdUpperBound(queryDTO.getEndTime());
            timeBasedEndRow = C2CMessageRowKeyUtil.generateTimeRangeRowKey(queryDTO.getChatId(), endMsgId);
        }
        
        if (StringUtils.isNotBlank(queryDTO.getLastMsgId())) {
            // 分页查询：从指定消息ID开始，同时考虑时间范围
            if (queryDTO.getReverse()) {
                // 倒序查询：查询比lastMsgId小的消息
                // 在HBase倒序扫描中，startRow应该是较大的值，stopRow应该是较小的值
                String stopRow = C2CMessageRowKeyUtil.generateRowKey(queryDTO.getChatId(), queryDTO.getLastMsgId());
                String startRow = timeBasedEndRow != null ? timeBasedEndRow : C2CMessageRowKeyUtil.generateChatEndRow(queryDTO.getChatId());
                scan = new Scan()
                        .withStartRow(Bytes.toBytes(startRow))
                        .withStopRow(Bytes.toBytes(stopRow))
                        .setReversed(queryDTO.getReverse());
            } else {
                // 正序查询：查询比lastMsgId大的消息
                String startRow = C2CMessageRowKeyUtil.generateRowKey(queryDTO.getChatId(), queryDTO.getLastMsgId() + "0");
                String stopRow = timeBasedEndRow != null ? timeBasedEndRow : C2CMessageRowKeyUtil.generateChatEndRow(queryDTO.getChatId());
                scan = new Scan()
                        .withStartRow(Bytes.toBytes(startRow))
                        .withStopRow(Bytes.toBytes(stopRow));
            }
        } else {
            // 首次查询：基于时间范围构造RowKey范围
            String startRow, stopRow;
            
            if (queryDTO.getReverse()) {
                // 倒序查询：从时间范围的结束位置开始向前扫描
                // 在HBase倒序扫描中，startRow应该是较大的值，stopRow应该是较小的值
                startRow = timeBasedEndRow != null ? timeBasedEndRow : C2CMessageRowKeyUtil.generateChatEndRow(queryDTO.getChatId());
                stopRow = timeBasedStartRow != null ? timeBasedStartRow : chatIdPrefix;
                scan = new Scan()
                        .withStartRow(Bytes.toBytes(startRow))
                        .withStopRow(Bytes.toBytes(stopRow))
                        .setReversed(queryDTO.getReverse());
            } else {
                // 正序查询：从时间范围的开始位置向后扫描
                startRow = timeBasedStartRow != null ? timeBasedStartRow : chatIdPrefix;
                stopRow = timeBasedEndRow != null ? timeBasedEndRow : C2CMessageRowKeyUtil.generateChatEndRow(queryDTO.getChatId());
                scan = new Scan()
                        .withStartRow(Bytes.toBytes(startRow))
                        .withStopRow(Bytes.toBytes(stopRow));
            }
        }
        
        // 设置查询数量限制（多查询一条用于判断是否还有更多数据）
        scan.setLimit(queryDTO.getPageSize() + 1);
        
        log.debug("HBase扫描范围: startRow={}, stopRow={}, reverse={}", 
                new String(scan.getStartRow()), new String(scan.getStopRow()), queryDTO.getReverse());
        
        return scan;
    }


    /**
     * 将HBase Result转换为ChatMessageVO
     */
    private ChatHistoryResponseDTO.ChatMessageVO convertToMessageVO(Result result) {
        if (result == null || result.isEmpty()) {
            return null;
        }
        
        try {
            ChatHistoryResponseDTO.ChatMessageVO messageVO = new ChatHistoryResponseDTO.ChatMessageVO();
            
            // 设置RowKey
            String rowKey = Bytes.toString(result.getRow());
            messageVO.setRowkey(rowKey);
            
            // 解析各个字段
            NavigableMap<byte[], byte[]> familyMap = result.getFamilyMap(Bytes.toBytes(COLUMN_FAMILY));
            for (Map.Entry<byte[], byte[]> entry : familyMap.entrySet()) {
                String columnName = Bytes.toString(entry.getKey());
                String stringValue = Bytes.toString(entry.getValue());
                
                switch (columnName) {
                    case COLUMN_FROM_USER_ID:
                        messageVO.setFromUserId(stringValue);
                        break;
                    case COLUMN_TO_USER_ID:
                        messageVO.setToUserId(stringValue);
                        break;
                    case COLUMN_MSG_ID:
                        messageVO.setMsgId(stringValue);
                        break;
                    case COLUMN_MSG_FORMAT:
                        messageVO.setMsgFormat(safeParseInteger(stringValue, 1));
                        break;
                    case COLUMN_MSG_CONTENT:
                        messageVO.setMsgContent(stringValue);
                        break;
                    case COLUMN_MSG_CREATE_TIME:
                        messageVO.setMsgCreateTime(safeParseLong(stringValue, System.currentTimeMillis()));
                        break;
                    case COLUMN_MSG_STATUS:
                        messageVO.setMsgStatus(safeParseInteger(stringValue, 1));
                        break;
                    case COLUMN_WITHDRAW_FLAG:
                        messageVO.setWithdrawFlag(safeParseInteger(stringValue, 0)); // 默认未撤回
                        break;
                    case COLUMN_CHAT_ID:
                        messageVO.setChatId(stringValue);
                        break;
                    case COLUMN_CREATE_TIME:
                        messageVO.setCreateTime(safeParseLong(stringValue, System.currentTimeMillis()));
                        break;
                    case COLUMN_UPDATE_TIME:
                        messageVO.setUpdateTime(safeParseLong(stringValue, System.currentTimeMillis()));
                        break;
                }
            }
            
            return messageVO;
        } catch (Exception e) {
            log.error("转换消息VO失败", e);
            return null;
        }
    }

    /**
     * 批量查询消息记录（根据rowKey列表）
     * 使用HBase批量Get操作，性能优于scan
     * 
     * @param rowKeys rowKey列表，格式：chatId_msgId
     * @return 消息记录映射 Map<rowKey, ImC2CMsgRecord>
     */
    @Override
    public Map<String, ImC2CMsgRecord> batchGetMessages(List<String> rowKeys) {
        log.info("批量查询消息记录: rowKeys数量={}", rowKeys != null ? rowKeys.size() : 0);
        Map<String, ImC2CMsgRecord> result = new HashMap<>();
        
        if (rowKeys == null || rowKeys.isEmpty()) {
            return result;
        }
        
        try (Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME))) {
            // 构建批量Get请求
            List<Get> gets = new ArrayList<>();
            for (String rowKey : rowKeys) {
                if (StringUtils.isNotBlank(rowKey)) {
                    Get get = new Get(Bytes.toBytes(rowKey));
                    // 只获取info列族
                    get.addFamily(Bytes.toBytes(COLUMN_FAMILY));
                    gets.add(get);
                }
            }
            
            if (gets.isEmpty()) {
                return result;
            }
            
            // 执行批量Get
            Result[] results = table.get(gets);
            
            // 转换结果
            for (int i = 0; i < results.length; i++) {
                Result hbaseResult = results[i];
                if (hbaseResult != null && !hbaseResult.isEmpty()) {
                    String rowKey = rowKeys.get(i);
                    ImC2CMsgRecord msgRecord = convertResultToImC2CMsgRecord(hbaseResult);
                    if (msgRecord != null) {
                        result.put(rowKey, msgRecord);
                    }
                }
            }
            
            log.info("批量查询消息完成，请求{}条，返回{}条", rowKeys.size(), result.size());
            
        } catch (Exception e) {
            log.error("批量查询消息失败, rowKeys数量={}", rowKeys.size(), e);
        }
        
        return result;
    }

}
