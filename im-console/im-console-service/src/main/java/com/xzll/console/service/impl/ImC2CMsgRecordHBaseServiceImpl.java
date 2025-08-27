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
        List<ImC2CMsgRecord> messages = new ArrayList<>();
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                Scan scan = new Scan();
                scan.setLimit(1000); // 限制查询数量，避免内存溢出
                
                ResultScanner scanner = table.getScanner(scan);
                for (Result result : scanner) {
                    ImC2CMsgRecord record = convertResultToImC2CMsgRecord(result);
                    if (record != null) {
                        messages.add(record);
                    }
                }
                scanner.close();
                
                log.info("查询到 {} 条消息记录", messages.size());
            } finally {
                table.close();
            }
        } catch (Exception e) {
            log.error("查询所有消息记录失败", e);
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
                
                scan.setLimit(1000);
                
                ResultScanner scanner = table.getScanner(scan);
                for (Result result : scanner) {
                    ImC2CMsgRecord record = convertResultToImC2CMsgRecord(result);
                    if (record != null && matchesCondition(record, fromUserId, toUserId, chatId)) {
                        messages.add(record);
                    }
                }
                scanner.close();
                
                log.info("根据条件查询到 {} 条消息记录", messages.size());
            } finally {
                table.close();
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
                scan.setLimit(limit);
                
                ResultScanner scanner = table.getScanner(scan);
                for (Result result : scanner) {
                    ImC2CMsgRecord record = convertResultToImC2CMsgRecord(result);
                    if (record != null) {
                        messages.add(record);
                    }
                    if (messages.size() >= limit) {
                        break;
                    }
                }
                scanner.close();
                
                log.info("根据会话ID查询到 {} 条消息记录", messages.size());
            } finally {
                table.close();
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
        
        for (Cell cell : result.listCells()) {
            String column = Bytes.toString(CellUtil.cloneQualifier(cell));
            byte[] value = CellUtil.cloneValue(cell);
            
            switch (column) {
                case COLUMN_FROM_USER_ID:
                    record.setFromUserId(Bytes.toString(value));
                    break;
                case COLUMN_TO_USER_ID:
                    record.setToUserId(Bytes.toString(value));
                    break;
                case COLUMN_MSG_ID:
                    record.setMsgId(Bytes.toString(value));
                    break;
                case COLUMN_MSG_FORMAT:
                    try {
                        record.setMsgFormat(Integer.parseInt(Bytes.toString(value)));
                    } catch (NumberFormatException e) {
                        record.setMsgFormat(0);
                    }
                    break;
                case COLUMN_MSG_CONTENT:
                    record.setMsgContent(Bytes.toString(value));
                    break;
                case COLUMN_MSG_CREATE_TIME:
                    try {
                        record.setMsgCreateTime(Long.parseLong(Bytes.toString(value)));
                    } catch (NumberFormatException e) {
                        record.setMsgCreateTime(System.currentTimeMillis());
                    }
                    break;
                case COLUMN_RETRY_COUNT:
                    try {
                        record.setRetryCount(Integer.parseInt(Bytes.toString(value)));
                    } catch (NumberFormatException e) {
                        record.setRetryCount(0);
                    }
                    break;
                case COLUMN_MSG_STATUS:
                    try {
                        record.setMsgStatus(Integer.parseInt(Bytes.toString(value)));
                    } catch (NumberFormatException e) {
                        record.setMsgStatus(0);
                    }
                    break;
                case COLUMN_CHAT_ID:
                    record.setChatId(Bytes.toString(value));
                    break;
                case COLUMN_CREATE_TIME:
                    try {
                        record.setCreateTime(LocalDateTime.parse(Bytes.toString(value)));
                    } catch (Exception e) {
                        record.setCreateTime(LocalDateTime.now());
                    }
                    break;
                case COLUMN_UPDATE_TIME:
                    try {
                        record.setUpdateTime(LocalDateTime.parse(Bytes.toString(value)));
                    } catch (Exception e) {
                        record.setUpdateTime(LocalDateTime.now());
                    }
                    break;
            }
        }
        
        return record;
    }
} 