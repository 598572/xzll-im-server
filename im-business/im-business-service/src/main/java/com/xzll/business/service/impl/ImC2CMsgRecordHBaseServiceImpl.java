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
     * 格式：chatId + "_" + (Long.MAX_VALUE - msgCreateTime) + "_" + msgId
     * 这样设计可以保证同一会话的消息按时间倒序排列
     */
    private String buildRowKey(String chatId, Long msgCreateTime, String msgId) {
        return chatId + "_" + (Long.MAX_VALUE - msgCreateTime) + "_" + msgId;
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
        Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
        try {
            // 由于我们不知道msgCreateTime，需要使用Scan来查找
            Scan scan = new Scan();
            scan.withStartRow(Bytes.toBytes(chatId + "_"));
            scan.withStopRow(Bytes.toBytes(chatId + "_" + Long.MAX_VALUE));
            scan.setFilter(new SingleColumnValueFilter(
                    Bytes.toBytes(COLUMN_FAMILY),
                    Bytes.toBytes(COLUMN_MSG_ID),
                    CompareOperator.EQUAL,
                    Bytes.toBytes(msgId)
            ));
            
            ResultScanner scanner = table.getScanner(scan);
            Result result = scanner.next();
            scanner.close();
            
            if (result != null && !result.isEmpty()) {
                return convertResultToImC2CMsgRecord(result);
            }
            return null;
        } finally {
            table.close();
        }
    }

    /**
     * 将HBase Result转换为ImC2CMsgRecord对象
     */
    private ImC2CMsgRecord convertResultToImC2CMsgRecord(Result result) {
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
                    record.setMsgFormat(Integer.parseInt(Bytes.toString(value)));
                    break;
                case COLUMN_MSG_CONTENT:
                    // 处理Base64编码的内容
                    record.setMsgContent(Bytes.toString(value));
                    break;
                case COLUMN_MSG_CREATE_TIME:
                    record.setMsgCreateTime(Long.parseLong(Bytes.toString(value)));
                    break;
                case COLUMN_RETRY_COUNT:
                    record.setRetryCount(Integer.parseInt(Bytes.toString(value)));
                    break;
                case COLUMN_MSG_STATUS:
                    record.setMsgStatus(Integer.parseInt(Bytes.toString(value)));
                    break;
                case COLUMN_WITHDRAW_FLAG:
                    record.setWithdrawFlag(Integer.parseInt(Bytes.toString(value)));
                    break;
                case COLUMN_CHAT_ID:
                    record.setChatId(Bytes.toString(value));
                    break;
                case COLUMN_CREATE_TIME:
                    record.setCreateTime(LocalDateTime.parse(Bytes.toString(value)));
                    break;
                case COLUMN_UPDATE_TIME:
                    record.setUpdateTime(LocalDateTime.parse(Bytes.toString(value)));
                    break;
            }
        }
        
        return record;
    }

    @Override
    public boolean saveC2CMsg(C2CSendMsgAO dto) {
        log.info("保存单聊消息入参:{}", JSONUtil.toJsonStr(dto));
        
        try {
            Table table = hbaseConnection.getTable(TableName.valueOf(TABLE_NAME));
            try {
                // 如果是重试消息，需要特殊处理
                if (Boolean.TRUE.equals(dto.getRetryMsgFlag())) {
                    ImC2CMsgRecord existingRecord = getC2CMsgRecordByChatIdAndMsgId(dto.getChatId(), dto.getMsgId());
                    if (existingRecord != null) {
                        // 更新重试次数
                        String rowKey = buildRowKey(dto.getChatId(), existingRecord.getMsgCreateTime(), dto.getMsgId());
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
                String rowKey = buildRowKey(dto.getChatId(), dto.getMsgCreateTime(), dto.getMsgId());
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
                table.close();
            }
        } catch (Exception e) {
            log.error("保存单聊消息失败", e);
            return false;
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
                String rowKey = buildRowKey(dto.getChatId(), dbResult.getMsgCreateTime(), dto.getMsgId());
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
                String rowKey = buildRowKey(dto.getChatId(), dbResult.getMsgCreateTime(), dto.getMsgId());
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
                String rowKey = buildRowKey(dto.getChatId(), dbResult.getMsgCreateTime(), dto.getMsgId());
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

    @Override
    public void testEs(C2CSendMsgAO dto) {
        // 保留原有接口方法，但HBase实现不需要ES相关操作
        log.info("HBase实现不执行ES测试操作");
    }
}