package com.xzll.business.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.json.JSONUtil;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
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
import org.apache.hadoop.hbase.CompareOperator;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.SingleColumnValueFilter;
import org.apache.hadoop.hbase.util.Bytes;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

    /**
     * 构建RowKey
     * 格式：chatId + "_" + msgId
     * 雪花算法生成的 msgId 本身就具有时间顺序性，可以保证同一会话的消息按时间顺序排列
     */
    private String buildRowKey(String chatId, String msgId) {
        return chatId + "_" + msgId;
    }

    /**
     * 从RowKey中解析出chatId
     */
    private String parseChatIdFromRowKey(String rowKey) {
        if (rowKey == null || !rowKey.contains("_")) {
            return null;
        }
        return rowKey.split("_")[0];
    }

    /**
     * 根据chatId和msgId获取消息
     */
    private ImC2CMsgRecord getC2CMsgRecordByChatIdAndMsgId(String chatId, String msgId) throws IOException {
        Table table = null;
        try {
            table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            
            // 直接通过 chatId + "_" + msgId 构建 rowkey 进行精确查询
            String rowKey = buildRowKey(chatId, msgId);
            Get get = new Get(Bytes.toBytes(rowKey));
            Result result = table.get(get);
            
            if (result != null && !result.isEmpty()) {
                return convertResultToImC2CMsgRecord(result);
            }
            return null;
        } finally {
            // 确保table被正确关闭
            if (table != null) {
                try {
                    table.close();
                } catch (Exception e) {
                    log.warn("关闭HBase表时发生异常", e);
                }
            }
        }
    }

    /**
     * 将HBase Result转换为ImC2CMsgRecord对象
     */
    private ImC2CMsgRecord convertResultToImC2CMsgRecord(Result result) {
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
                    // 处理Base64编码的内容
                    record.setMsgContent(stringValue);
                    break;
                case COLUMN_MSG_CREATE_TIME:
                    try {
                        record.setMsgCreateTime(Long.parseLong(stringValue));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析msg_create_time值: {}, 设置默认值0", stringValue);
                        record.setMsgCreateTime(0L);
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
                case COLUMN_WITHDRAW_FLAG:
                    try {
                        record.setWithdrawFlag(Integer.parseInt(stringValue));
                    } catch (NumberFormatException e) {
                        log.warn("无法解析withdraw_flag值: {}, 设置默认值0", stringValue);
                        record.setWithdrawFlag(0);
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

    @Override
    public boolean saveC2CMsg(C2CSendMsgAO dto) {
        log.info("保存单聊消息入参:{}", JSONUtil.toJsonStr(dto));
        
        int maxRetries = 3;
        int retryCount = 0;
        
        while (retryCount < maxRetries) {
            try {
                return doSaveC2CMsg(dto);
            } catch (Exception e) {
                retryCount++;
                log.warn("保存单聊消息失败，第{}次重试: {}", retryCount, e.getMessage());
                
                if (retryCount >= maxRetries) {
                    log.error("保存单聊消息失败，已重试{}次", maxRetries, e);
                    return false;
                }
                
                // 等待一段时间后重试
                try {
                    Thread.sleep(1000 * retryCount); // 递增等待时间
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("重试等待被中断", ie);
                    return false;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 实际执行保存消息的方法
     */
    private boolean doSaveC2CMsg(C2CSendMsgAO dto) throws Exception {
        Table table = null;
        try {
            table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            
            // 如果是重试消息，需要特殊处理
            if (Boolean.TRUE.equals(dto.getRetryMsgFlag())) {
                ImC2CMsgRecord existingRecord = getC2CMsgRecordByChatIdAndMsgId(dto.getChatId(), dto.getMsgId());
                if (existingRecord != null) {
                    // 更新重试次数
                    String rowKey = buildRowKey(dto.getChatId(), existingRecord.getMsgId());
                    Put put = new Put(Bytes.toBytes(rowKey));
                    put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                                  Bytes.toBytes(COLUMN_RETRY_COUNT), 
                                  Bytes.toBytes(String.valueOf(existingRecord.getRetryCount() + 1)));
                    put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                                  Bytes.toBytes(COLUMN_UPDATE_TIME), 
                                  Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
                    table.put(put);
                    log.info("更新重试次数成功");
                    return true;
                }
            }
            
            // 转换并设置所有字段
            ImC2CMsgRecord imC2CMsgRecord = conversionService.convert(dto, ImC2CMsgRecord.class);
            imC2CMsgRecord.setMsgStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
            
            // 设置当前时间
            LocalDateTime now = LocalDateTime.now();
            String nowStr = now.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            
            // 构建RowKey
            String rowKey = buildRowKey(dto.getChatId(), dto.getMsgId());
            Put put = new Put(Bytes.toBytes(rowKey));
            
            // 添加列值
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_FROM_USER_ID), Bytes.toBytes(imC2CMsgRecord.getFromUserId()));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_TO_USER_ID), Bytes.toBytes(imC2CMsgRecord.getToUserId()));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_ID), Bytes.toBytes(imC2CMsgRecord.getMsgId()));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_FORMAT), Bytes.toBytes(String.valueOf(imC2CMsgRecord.getMsgFormat())));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_CONTENT), Bytes.toBytes(imC2CMsgRecord.getMsgContent()));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_CREATE_TIME), Bytes.toBytes(String.valueOf(imC2CMsgRecord.getMsgCreateTime())));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_RETRY_COUNT), Bytes.toBytes(String.valueOf(imC2CMsgRecord.getRetryCount())));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_MSG_STATUS), Bytes.toBytes(String.valueOf(imC2CMsgRecord.getMsgStatus())));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_WITHDRAW_FLAG), Bytes.toBytes(String.valueOf(imC2CMsgRecord.getWithdrawFlag())));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_CHAT_ID), Bytes.toBytes(imC2CMsgRecord.getChatId()));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_CREATE_TIME), Bytes.toBytes(nowStr));
            put.addColumn(Bytes.toBytes(COLUMN_FAMILY), Bytes.toBytes(COLUMN_UPDATE_TIME), Bytes.toBytes(nowStr));
            
            // 执行插入
            table.put(put);
            log.info("保存单聊消息成功");
            return true;
        } finally {
            // 确保table被正确关闭
            if (table != null) {
                try {
                    table.close();
                } catch (Exception e) {
                    log.warn("关闭HBase表时发生异常", e);
                }
            }
        }
    }

    @Override
    public boolean updateC2CMsgOffLineStatus(C2COffLineMsgAO dto) {
        log.info("更新消息为离线入参:{}", JSONUtil.toJsonStr(dto));
        
        try {
            ImC2CMsgRecord dbResult = getC2CMsgRecordByChatIdAndMsgId(dto.getChatId(), dto.getMsgId());
            Assert.isTrue(Objects.nonNull(dbResult), "数据为空抛出异常待mq重试消费,一般顺序消费情况下，不会出现此异常");
            
            // 检查当前状态是否为SERVER_RECEIVED
            if (!Objects.equals(dbResult.getMsgStatus(), MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode())) {
                log.info("当前消息状态不支持更新为离线，当前状态:{}", MsgStatusEnum.MsgStatus.getNameByCode(dbResult.getMsgStatus()));
                return true;
            }
            
            // 更新状态
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                String rowKey = buildRowKey(dto.getChatId(), dto.getMsgId());
                Put put = new Put(Bytes.toBytes(rowKey));
                
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                             Bytes.toBytes(COLUMN_MSG_STATUS), 
                             Bytes.toBytes(String.valueOf(MsgStatusEnum.MsgStatus.OFF_LINE.getCode())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                             Bytes.toBytes(COLUMN_UPDATE_TIME), 
                             Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
                
                table.put(put);
                log.info("更新离线消息成功");
                return true;
            } finally {
                table.close();
            }
        } catch (Exception e) {
            log.error("更新消息为离线失败", e);
            return false;
        }
    }

    @Override
    public boolean updateC2CMsgReceivedStatus(C2CReceivedMsgAckAO dto) {
        String currentName = MsgStatusEnum.MsgStatus.getNameByCode(dto.getMsgStatus());
        log.info("更新消息状态为:{}, 入参:{}", currentName, JSONUtil.toJsonStr(dto));
        
        try {
            ImC2CMsgRecord dbResult = getC2CMsgRecordByChatIdAndMsgId(dto.getChatId(), dto.getMsgId());
            if (Objects.isNull(dbResult)) {
                log.error("数据为空,可能发送消息时数据未落库成功，或者顺序消费出现了乱序，需排查具体原因");
                // 与原有逻辑保持一致，返回true让消息送达ack到达发送方
                return true;
            }
            
            // 如果db中已经是已读且当前要更新为未读，则将此未读忽略掉
            if (Objects.equals(dbResult.getMsgStatus(), MsgStatusEnum.MsgStatus.READED.getCode()) 
                && Objects.equals(dto.getMsgStatus(), MsgStatusEnum.MsgStatus.UN_READ.getCode())) {
                log.info("db中已经是已读状态不再更新为未读");
                return true;
            }
            
            // 状态更新条件检查
            if (Objects.equals(dto.getMsgStatus(), MsgStatusEnum.MsgStatus.UN_READ.getCode())) {
                if (!ImConstant.MsgStatusUpdateCondition.CAN_UPDATE_UN_READ.contains(dbResult.getMsgStatus())) {
                    log.info("当前消息状态不支持更新为:{}, 当前状态:{}", currentName, MsgStatusEnum.MsgStatus.getNameByCode(dbResult.getMsgStatus()));
                    return true;
                }
            } else if (Objects.equals(dto.getMsgStatus(), MsgStatusEnum.MsgStatus.READED.getCode())) {
                if (!ImConstant.MsgStatusUpdateCondition.CAN_UPDATE_READED.contains(dbResult.getMsgStatus())) {
                    log.info("当前消息状态不支持更新为:{}, 当前状态:{}", currentName, MsgStatusEnum.MsgStatus.getNameByCode(dbResult.getMsgStatus()));
                    return true;
                }
            }
            
            // 执行更新
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                String rowKey = buildRowKey(dto.getChatId(), dto.getMsgId());
                Put put = new Put(Bytes.toBytes(rowKey));
                
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                             Bytes.toBytes(COLUMN_MSG_STATUS), 
                             Bytes.toBytes(String.valueOf(dto.getMsgStatus())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                             Bytes.toBytes(COLUMN_UPDATE_TIME), 
                             Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
                
                table.put(put);
                log.info("更新消息状态为:{} 成功", currentName);
                return true;
            } finally {
                table.close();
            }
        } catch (Exception e) {
            log.error("更新消息状态失败", e);
            return false;
        }
    }

    @Override
    public boolean updateC2CMsgWithdrawStatus(C2CWithdrawMsgAO dto) {
        log.info("更新消息为撤回状态_入参:{}", JSONUtil.toJsonStr(dto));
        
        try {
            ImC2CMsgRecord dbResult = getC2CMsgRecordByChatIdAndMsgId(dto.getChatId(), dto.getMsgId());
            if (Objects.isNull(dbResult)) {
                log.error("数据为空,可能发送消息时数据未落库成功，或者顺序消费出现了乱序，需排查具体原因");
                // 与原有逻辑保持一致，返回true
                return true;
            }
            
            // 检查是否已经撤回
            if (Objects.equals(dbResult.getWithdrawFlag(), MsgStatusEnum.MsgWithdrawStatus.YES.getCode())) {
                log.info("db中消息已撤回");
                return true;
            }
            
            // 执行更新
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                String rowKey = buildRowKey(dto.getChatId(), dto.getMsgId());
                Put put = new Put(Bytes.toBytes(rowKey));
                
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                             Bytes.toBytes(COLUMN_WITHDRAW_FLAG), 
                             Bytes.toBytes(String.valueOf(MsgStatusEnum.MsgWithdrawStatus.YES.getCode())));
                put.addColumn(Bytes.toBytes(COLUMN_FAMILY), 
                             Bytes.toBytes(COLUMN_UPDATE_TIME), 
                             Bytes.toBytes(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
                
                table.put(put);
                log.info("更新消息状态为撤回成功");
                return true;
            } finally {
                table.close();
            }
        } catch (Exception e) {
            log.error("更新消息为撤回状态失败", e);
            return false;
        }
    }

}