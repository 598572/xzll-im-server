package com.xzll.console.service.impl;

import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.service.ImC2CMsgRecordHBaseService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息HBase存储服务实现类
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
    private static final String COLUMN_CHAT_ID = "chat_id";
    private static final String COLUMN_CREATE_TIME = "create_time";
    private static final String COLUMN_UPDATE_TIME = "update_time";

    @Resource
    private Connection hbaseConnection;

    @Override
    public List<ImC2CMsgRecord> getAllMessages() {
        log.info("查询所有消息记录");
        return getAllMessagesWithPagination(10, null); // 默认查询10条
    }

    /**
     * 分页查询所有消息记录
     * @param limit 每页数量
     * @param lastRowKey 上一页的最后一个RowKey，用于分页
     * @return 消息记录列表
     */
    public List<ImC2CMsgRecord> getAllMessagesWithPagination(int limit, String lastRowKey) {
        log.info("分页查询消息记录: limit={}, lastRowKey={}", limit, lastRowKey);
        List<ImC2CMsgRecord> messages = new ArrayList<>();
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                Scan scan = new Scan();
                
                // 设置分页参数
                scan.setLimit(limit);
                scan.setCaching(limit); // 设置缓存大小，提高性能
                scan.setBatch(limit);   // 设置批处理大小
                
                // 如果指定了lastRowKey，从该位置开始查询
                if (lastRowKey != null && !lastRowKey.isEmpty()) {
                    scan.withStartRow(Bytes.toBytes(lastRowKey), false); // false表示不包含起始行
                }
                
                ResultScanner scanner = table.getScanner(scan);
                try {
                    int count = 0;
                    for (Result result : scanner) {
                        ImC2CMsgRecord record = convertResultToImC2CMsgRecord(result);
                        if (record != null) {
                            messages.add(record);
                            count++;
                        }
                        
                        // 达到限制数量后停止
                        if (count >= limit) {
                            break;
                        }
                    }
                    log.info("分页查询到 {} 条消息记录", messages.size());
                } finally {
                    // 确保scanner被正确关闭
                    if (scanner != null) {
                        scanner.close();
                    }
                }
            } finally {
                // 确保table被正确关闭
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("分页查询消息记录失败", e);
        }
        
        return messages;
    }

    /**
     * 获取指定数量的最新消息
     * @param limit 消息数量限制
     * @return 消息记录列表
     */
    public List<ImC2CMsgRecord> getLatestMessages(int limit) {
        log.info("获取最新 {} 条消息记录", limit);
        List<ImC2CMsgRecord> messages = new ArrayList<>();
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                Scan scan = new Scan();
                scan.setLimit(limit);
                scan.setCaching(limit);
                scan.setBatch(limit);
                scan.setReversed(true); // 反向扫描，获取最新消息
                
                ResultScanner scanner = table.getScanner(scan);
                try {
                    int count = 0;
                    for (Result result : scanner) {
                        ImC2CMsgRecord record = convertResultToImC2CMsgRecord(result);
                        if (record != null) {
                            messages.add(record);
                            count++;
                        }
                        
                        if (count >= limit) {
                            break;
                        }
                    }
                    log.info("获取到最新 {} 条消息记录", messages.size());
                } finally {
                    if (scanner != null) {
                        scanner.close();
                    }
                }
            } finally {
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("获取最新消息记录失败", e);
        }
        
        return messages;
    }

    @Override
    public List<ImC2CMsgRecord> getMessagesByCondition(String fromUserId, String toUserId, String chatId) {
        log.info("根据条件查询消息记录: fromUserId={}, toUserId={}, chatId={}", fromUserId, toUserId, chatId);
        List<ImC2CMsgRecord> messages = new ArrayList<>();
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                Scan scan = new Scan();
                
                // 如果指定了chatId，使用范围扫描
                if (chatId != null && !chatId.isEmpty()) {
                    scan.withStartRow(Bytes.toBytes(chatId + "_"));
                    scan.withStopRow(Bytes.toBytes(chatId + "_" + Long.MAX_VALUE));
                }
                
                // 设置分页和性能参数
                scan.setLimit(100); // 限制单次查询数量
                scan.setCaching(100);
                scan.setBatch(100);
                
                ResultScanner scanner = table.getScanner(scan);
                try {
                    int count = 0;
                    for (Result result : scanner) {
                        ImC2CMsgRecord record = convertResultToImC2CMsgRecord(result);
                        if (record != null && matchesCondition(record, fromUserId, toUserId, chatId)) {
                            messages.add(record);
                            count++;
                        }
                        
                        // 限制总数量，避免内存溢出
                        if (count >= 1000) {
                            log.warn("查询结果数量达到上限1000，停止查询");
                            break;
                        }
                    }
                    log.info("根据条件查询到 {} 条消息记录", messages.size());
                } finally {
                    // 确保scanner被正确关闭
                    if (scanner != null) {
                        scanner.close();
                    }
                }
            } finally {
                // 确保table被正确关闭
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("根据条件查询消息记录失败", e);
        }
        
        return messages;
    }

    @Override
    public List<ImC2CMsgRecord> getMessagesByChatId(String chatId, int limit) {
        log.info("根据会话ID查询消息记录: chatId={}, limit={}", chatId, limit);
        List<ImC2CMsgRecord> messages = new ArrayList<>();
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                Scan scan = new Scan();
                scan.withStartRow(Bytes.toBytes(chatId + "_"));
                scan.withStopRow(Bytes.toBytes(chatId + "_" + Long.MAX_VALUE));
                scan.setReversed(true); // 反向扫描，获取最新消息
                
                // 设置分页和性能参数
                scan.setLimit(limit);
                scan.setCaching(limit);
                scan.setBatch(limit);
                
                ResultScanner scanner = table.getScanner(scan);
                try {
                    int count = 0;
                    for (Result result : scanner) {
                        ImC2CMsgRecord record = convertResultToImC2CMsgRecord(result);
                        if (record != null) {
                            messages.add(record);
                            count++;
                        }
                        if (count >= limit) {
                            break;
                        }
                    }
                    log.info("根据会话ID查询到 {} 条消息记录", messages.size());
                } finally {
                    // 确保scanner被正确关闭
                    if (scanner != null) {
                        scanner.close();
                    }
                }
            } finally {
                // 确保table被正确关闭
                if (table != null) {
                    table.close();
                }
            }
        } catch (Exception e) {
            log.error("根据会话ID查询消息记录失败", e);
        }
        
        return messages;
    }

    /**
     * 检查消息记录是否匹配查询条件
     */
    private boolean matchesCondition(ImC2CMsgRecord record, String fromUserId, String toUserId, String chatId) {
        if (fromUserId != null && !fromUserId.isEmpty() && !fromUserId.equals(record.getFromUserId())) {
            return false;
        }
        if (toUserId != null && !toUserId.isEmpty() && !toUserId.equals(record.getToUserId())) {
            return false;
        }
        if (chatId != null && !chatId.isEmpty() && !chatId.equals(record.getChatId())) {
            return false;
        }
        return true;
    }

    /**
     * 将HBase Result转换为ImC2CMsgRecord对象
     */
    private ImC2CMsgRecord convertResultToImC2CMsgRecord(Result result) {
        if (result == null || result.isEmpty()) {
            return null;
        }
        
        ImC2CMsgRecord record = new ImC2CMsgRecord();
        
        // 设置RowKey
        String rowKey = Bytes.toString(result.getRow());
        record.setRowkey(rowKey);
        
        for (Cell cell : result.listCells()) {
            String column = Bytes.toString(CellUtil.cloneQualifier(cell));
            byte[] value = CellUtil.cloneValue(cell);
            String stringValue = Bytes.toString(value);
            
            // 跳过null值
            if ("null".equals(stringValue)) {
                continue;
            }
            
            switch (column) {
                case COLUMN_FROM_USER_ID:
                    record.setFromUserId(stringValue);
                    break;
                case COLUMN_TO_USER_ID:
                    record.setToUserId(stringValue);
                    break;
                case COLUMN_MSG_ID:
                    record.setMsgId(stringValue);
                    break;
                case COLUMN_MSG_FORMAT:
                    try {
                        record.setMsgFormat(Integer.parseInt(stringValue));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析msg_format值: {}, 设置默认值0", stringValue);
                        record.setMsgFormat(0);
                    }
                    break;
                case COLUMN_MSG_CONTENT:
                    record.setMsgContent(stringValue);
                    break;
                case COLUMN_MSG_CREATE_TIME:
                    try {
                        record.setMsgCreateTime(Long.parseLong(stringValue));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析msg_create_time值: {}, 设置默认值", stringValue);
                        record.setMsgCreateTime(System.currentTimeMillis());
                    }
                    break;
                case COLUMN_RETRY_COUNT:
                    try {
                        record.setRetryCount(Integer.parseInt(stringValue));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析retry_count值: {}, 设置默认值0", stringValue);
                        record.setRetryCount(0);
                    }
                    break;
                case COLUMN_MSG_STATUS:
                    try {
                        record.setMsgStatus(Integer.parseInt(stringValue));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析msg_status值: {}, 设置默认值0", stringValue);
                        record.setMsgStatus(0);
                    }
                    break;
                case COLUMN_CHAT_ID:
                    record.setChatId(stringValue);
                    break;
                case COLUMN_CREATE_TIME:
                    try {
                        record.setCreateTime(LocalDateTime.parse(stringValue));
                    } catch (Exception e) {
                        log.warn("无法解析create_time值: {}, 设置默认值", stringValue);
                        record.setCreateTime(LocalDateTime.now());
                    }
                    break;
                case COLUMN_UPDATE_TIME:
                    try {
                        record.setUpdateTime(LocalDateTime.parse(stringValue));
                    } catch (Exception e) {
                        log.warn("无法解析update_time值: {}, 设置默认值", stringValue);
                        record.setUpdateTime(LocalDateTime.now());
                    }
                    break;
            }
        }
        
        return record;
    }
} 