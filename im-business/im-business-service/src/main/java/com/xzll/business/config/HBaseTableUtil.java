package com.xzll.business.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.HColumnDescriptor;
import org.apache.hadoop.hbase.HTableDescriptor;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.io.compress.Compression;
import org.apache.hadoop.hbase.regionserver.BloomType;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.xzll.common.constant.ImConstant.TableConstant.IM_C2C_MSG_RECORD;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Delete;
import org.apache.hadoop.hbase.client.Scan;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: HBase表管理工具类
 */
@Component
@Slf4j
public class HBaseTableUtil {

    // 表名
    public static final String IM_C2C_MSG_RECORD_TABLE = IM_C2C_MSG_RECORD;
    // 列族名
    public static final String COLUMN_FAMILY_INFO = "info";
    
    // 列名常量
    public static final String COLUMN_FROM_USER_ID = "from_user_id";
    public static final String COLUMN_TO_USER_ID = "to_user_id";
    public static final String COLUMN_MSG_ID = "msg_id";
    public static final String COLUMN_MSG_FORMAT = "msg_format";
    public static final String COLUMN_MSG_CONTENT = "msg_content";
    public static final String COLUMN_MSG_CREATE_TIME = "msg_create_time";
    public static final String COLUMN_RETRY_COUNT = "retry_count";
    public static final String COLUMN_MSG_STATUS = "msg_status";
    public static final String COLUMN_WITHDRAW_FLAG = "withdraw_flag";
    public static final String COLUMN_CHAT_ID = "chat_id";
    public static final String COLUMN_CREATE_TIME = "create_time";
    public static final String COLUMN_UPDATE_TIME = "update_time";

    @Autowired
    private Connection hbaseConnection; // 注入HBase连接Bean

    @PostConstruct
    public void init() {
        try {
            log.info("HBase连接已注入，开始检查表是否存在");
            // 自动创建表
            createImC2CMsgRecordTableIfNotExists();
        } catch (Exception e) {
            log.error("HBase表初始化失败", e);
        }
    }

    /**
     * 创建单聊消息记录表（如果不存在）
     */
    public boolean createImC2CMsgRecordTableIfNotExists() {
        try (Admin admin = hbaseConnection.getAdmin()) {
            TableName tableName = TableName.valueOf(IM_C2C_MSG_RECORD_TABLE);

            if (admin.tableExists(tableName)) {
                log.info("表 {} 已存在", IM_C2C_MSG_RECORD_TABLE);
                return false;
            }

            HTableDescriptor tableDescriptor = new HTableDescriptor(tableName);

            // 创建列族
            HColumnDescriptor infoColumnFamily = new HColumnDescriptor(COLUMN_FAMILY_INFO);

            // 优化列族配置 - 使用GZ压缩，兼容性最好
            infoColumnFamily.setCompressionType(Compression.Algorithm.GZ);
            infoColumnFamily.setBlocksize(128 * 1024); // 128KB块大小
            infoColumnFamily.setBloomFilterType(BloomType.ROWCOL); // 行列级别布隆过滤器
            infoColumnFamily.setTimeToLive(365 * 24 * 60 * 60); // 1年TTL
            infoColumnFamily.setMaxVersions(1); // 只保留最新版本
            infoColumnFamily.setMinVersions(1); // 至少保留1个版本

            tableDescriptor.addFamily(infoColumnFamily);

            // 优化预分区策略 - 基于chat_id的哈希分布
            byte[][] splitKeys = new byte[15][]; // 16个分区
            for (int i = 1; i < 16; i++) {
                // 使用十六进制分割，更均匀分布
                splitKeys[i-1] = Bytes.toBytes(String.format("%02x", i));
            }

            // 创建表
            admin.createTable(tableDescriptor, splitKeys);
            log.info("表 {} 创建成功，包含16个预分区", IM_C2C_MSG_RECORD_TABLE);
            return true;
        } catch (IOException e) {
            log.error("创建表 {} 失败", IM_C2C_MSG_RECORD_TABLE, e);
            return false;
        }
    }

    /**
     * 删除表
     */
    public boolean deleteTable(String tableName) {
        try (Admin admin = hbaseConnection.getAdmin()) {
            TableName tn = TableName.valueOf(tableName);
            if (admin.tableExists(tn)) {
                admin.disableTable(tn);
                admin.deleteTable(tn);
                log.info("表 {} 删除成功", tableName);
                return true;
            }
            log.info("表 {} 不存在", tableName);
            return false;
        } catch (IOException e) {
            log.error("删除表 {} 失败", tableName, e);
            return false;
        }
    }

    /**
     * 获取HBase连接（供其他服务使用）
     */
    public Connection getConnection() {
        return hbaseConnection;
    }

    /**
     * 字节数组转换工具
     */
    public static byte[] toBytes(String str) {
        return str.getBytes();
    }

    public static String toString(byte[] bytes) {
        return new String(bytes);
    }

    /**
     * 生成RowKey - 方案1：chat_id + "_" + msg_id
     * 优势：
     * 1. 相同chat_id的消息聚集在一起
     * 2. 雪花算法本身具有时间顺序性，新消息的ID更大
     * 3. 简单直接，便于理解和维护
     */
    public static String generateRowKey(String chatId, String msgId) {
        return chatId + "_" + msgId;
    }

    /**
     * 从RowKey中解析信息
     */
    public static RowKeyInfo parseRowKey(String rowKey) {
        String[] parts = rowKey.split("_", 2);
        if (parts.length >= 2) {
            String chatId = parts[0];
            String msgId = parts[1];
            
            return new RowKeyInfo(chatId, msgId);
        }
        return null;
    }

    /**
     * RowKey信息类
     */
    public static class RowKeyInfo {
        private final String chatId;
        private final String msgId;

        public RowKeyInfo(String chatId, String msgId) {
            this.chatId = chatId;
            this.msgId = msgId;
        }

        public String getChatId() { return chatId; }
        public String getMsgId() { return msgId; }

        @Override
        public String toString() {
            return "RowKeyInfo{chatId='" + chatId + "', msgId='" + msgId + "'}";
        }
    }

    /**
     * 获取指定会话的消息范围扫描器
     * 用于查询特定会话的所有消息
     */
    public static Scan createChatMessageScan(String chatId) {
        Scan scan = new Scan();
        // 设置起始行键：chat_id + "_"
        scan.setStartRow(Bytes.toBytes(chatId + "_"));
        // 设置结束行键：chat_id + "_" + 最大字符
        scan.setStopRow(Bytes.toBytes(chatId + "_" + Character.MAX_VALUE));
        return scan;
    }

    /**
     * 获取指定会话的消息范围扫描器（限制数量）
     */
    public static Scan createChatMessageScan(String chatId, int limit) {
        Scan scan = createChatMessageScan(chatId);
        scan.setLimit(limit);
        return scan;
    }

    /**
     * 获取指定会话和消息ID范围的消息扫描器
     * 用于分页查询或时间范围查询
     */
    public static Scan createChatMessageScan(String chatId, String startMsgId, String endMsgId) {
        Scan scan = new Scan();
        // 设置起始行键：chat_id + "_" + startMsgId
        scan.setStartRow(Bytes.toBytes(chatId + "_" + startMsgId));
        // 设置结束行键：chat_id + "_" + endMsgId + 最大字符
        scan.setStopRow(Bytes.toBytes(chatId + "_" + endMsgId + Character.MAX_VALUE));
        return scan;
    }

    /**
     * 创建反向扫描器（获取最新消息）
     * 由于雪花算法ID递增，反向扫描可以获取最新消息
     */
    public static Scan createReverseChatMessageScan(String chatId, int limit) {
        Scan scan = createChatMessageScan(chatId);
        scan.setReversed(true); // 反向扫描
        scan.setLimit(limit);
        return scan;
    }

    /**
     * 创建Get请求 - 根据RowKey获取单条消息
     */
    public static Get createMessageGet(String chatId, String msgId) {
        String rowKey = generateRowKey(chatId, msgId);
        return new Get(Bytes.toBytes(rowKey));
    }

    /**
     * 创建Put请求 - 插入或更新消息
     */
    public static Put createMessagePut(String chatId, String msgId, String fromUserId, 
                                     String toUserId, int msgFormat, String msgContent, 
                                     long msgCreateTime, int retryCount, int msgStatus, 
                                     int withdrawFlag) {
        String rowKey = generateRowKey(chatId, msgId);
        Put put = new Put(Bytes.toBytes(rowKey));
        
        // 添加列数据
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_FROM_USER_ID), 
                     Bytes.toBytes(fromUserId));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_TO_USER_ID), 
                     Bytes.toBytes(toUserId));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_MSG_ID), 
                     Bytes.toBytes(msgId));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_MSG_FORMAT), 
                     Bytes.toBytes(msgFormat));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_MSG_CONTENT), 
                     Bytes.toBytes(msgContent));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_MSG_CREATE_TIME), 
                     Bytes.toBytes(msgCreateTime));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_RETRY_COUNT), 
                     Bytes.toBytes(retryCount));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_MSG_STATUS), 
                     Bytes.toBytes(msgStatus));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_WITHDRAW_FLAG), 
                     Bytes.toBytes(withdrawFlag));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_CHAT_ID), 
                     Bytes.toBytes(chatId));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_CREATE_TIME), 
                     Bytes.toBytes(String.valueOf(System.currentTimeMillis())));
        put.addColumn(Bytes.toBytes(COLUMN_FAMILY_INFO), 
                     Bytes.toBytes(COLUMN_UPDATE_TIME), 
                     Bytes.toBytes(String.valueOf(System.currentTimeMillis())));
        
        return put;
    }

    /**
     * 创建Delete请求 - 删除消息
     */
    public static Delete createMessageDelete(String chatId, String msgId) {
        String rowKey = generateRowKey(chatId, msgId);
        return new Delete(Bytes.toBytes(rowKey));
    }

    /**
     * 验证RowKey格式是否正确
     */
    public static boolean isValidRowKey(String rowKey) {
        if (rowKey == null || rowKey.isEmpty()) {
            return false;
        }
        String[] parts = rowKey.split("_", 2);
        return parts.length == 2 && !parts[0].isEmpty() && !parts[1].isEmpty();
    }

    /**
     * 获取会话ID前缀（用于范围查询）
     */
    public static String getChatIdPrefix(String chatId) {
        return chatId + "_";
    }

}