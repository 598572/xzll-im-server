package com.xzll.business.service.impl;

import cn.hutool.json.JSONUtil;
import com.xzll.business.dto.request.ChatHistoryQueryDTO;
import com.xzll.business.dto.response.ChatHistoryResponseDTO;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.business.entity.mongo.ImC2CMsgRecordMongo;
import com.xzll.business.entity.mysql.ImC2CMsgRecord;
import com.xzll.business.repository.ImC2CMsgRecordMongoRepository;
import com.xzll.business.service.ImC2CMsgRecordService;
import com.xzll.business.cluster.mq.RocketMqProducerWrap;
import com.xzll.common.constant.ImConstant;
import com.xzll.common.constant.MsgStatusEnum;
import com.xzll.common.rocketmq.ClusterEvent;
import com.xzll.common.pojo.request.C2COffLineMsgAO;
import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import com.xzll.common.pojo.request.C2CWithdrawMsgAO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.xzll.common.constant.ImConstant.*;
import static com.xzll.common.constant.ImConstant.TopicConstant.XZLL_DATA_SYNC_TOPIC;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: C2C消息记录 MongoDB 服务实现
 * 
 * 功能说明：
 * - 使用 MongoDB 存储消息
 * - 支持消息保存、状态更新、查询等操作
 * - 分片键: chatId（哈希分片）
 */
@Service
@Slf4j
public class ImC2CMsgRecordMongoServiceImpl implements ImC2CMsgRecordService {

    @Resource
    private ImC2CMsgRecordMongoRepository mongoRepository;

    @Resource
    private MongoTemplate mongoTemplate;

    @Resource
    private RocketMqProducerWrap rocketMqProducerWrap;

    /**
     * 保存C2C消息
     * 
     * 流程：
     * 1. 校验分片键（chatId）非空
     * 2. 将 AO 对象转换为 MongoDB 实体
     * 3. 保存到 MongoDB
     * 4. 发送 RocketMQ 消息进行数据同步（ES同步）
     * 
     * 分片说明：
     * - chatId 是分片键，必须非空
     * - 同一 chatId 的消息存储在同一分片
     */
    @Override
    public boolean saveC2CMsg(C2CSendMsgAO dto) {
        // 分片键非空校验（重要！）
        if (StringUtils.isBlank(dto.getChatId())) {
            log.error("保存C2C消息失败：分片键chatId不能为空, msgId={}", dto.getMsgId());
            return false;
        }
        
        log.info("保存C2C消息到MongoDB: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId());
        
        try {
            // 转换为 MongoDB 实体
            ImC2CMsgRecordMongo mongoEntity = convertToMongoEntity(dto);
            
            // 保存到 MongoDB
            mongoRepository.save(mongoEntity);
            
            log.info("C2C消息保存到MongoDB成功: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId());
            
            // 发送到RocketMQ进行数据同步（ES同步）
            sendToRocketMQ(dto);
            
            return true;
        } catch (Exception e) {
            log.error("保存C2C消息到MongoDB失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    /**
     * 更新消息离线状态
     * 
     * 分片说明：chatId是分片键，更新操作会路由到正确的分片
     */
    @Override
    public boolean updateC2CMsgOffLineStatus(C2COffLineMsgAO dto) {
        if (StringUtils.isBlank(dto.getChatId())) {
            log.error("更新离线状态失败：分片键chatId不能为空, msgId={}", dto.getMsgId());
            return false;
        }
        log.info("更新C2C消息离线状态: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId());
        
        try {
            String documentId = dto.getChatId() + "_" + dto.getMsgId();
            
            // 使用 MongoTemplate 进行部分更新
            Query query = new Query(Criteria.where("_id").is(documentId));
            Update update = new Update()
                    .set("msgStatus", dto.getMsgStatus())
                    .set("updateTime", new Date());
            
            mongoTemplate.updateFirst(query, update, ImC2CMsgRecordMongo.class);
            
            log.info("C2C消息离线状态更新成功: chatId={}, msgId={}, status={}", 
                    dto.getChatId(), dto.getMsgId(), dto.getMsgStatus());
            
            // 发送数据同步消息
            sendDataSyncMessage(OPERATION_TYPE_UPDATE_STATUS, dto.getChatId(), dto.getMsgId(), dto);
            
            return true;
        } catch (Exception e) {
            log.error("更新C2C消息离线状态失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    /**
     * 更新消息接收状态
     * 
     * 分片说明：chatId是分片键，更新操作会路由到正确的分片
     */
    @Override
    public boolean updateC2CMsgReceivedStatus(C2CReceivedMsgAckAO dto) {
        if (StringUtils.isBlank(dto.getChatId())) {
            log.error("更新接收状态失败：分片键chatId不能为空, msgId={}", dto.getMsgId());
            return false;
        }
        log.info("更新C2C消息接收状态: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId());
        
        try {
            String documentId = dto.getChatId() + "_" + dto.getMsgId();
            
            Query query = new Query(Criteria.where("_id").is(documentId));
            Update update = new Update()
                    .set("msgStatus", dto.getMsgStatus())
                    .set("updateTime", new Date());
            
            mongoTemplate.updateFirst(query, update, ImC2CMsgRecordMongo.class);
            
            log.info("C2C消息接收状态更新成功: chatId={}, msgId={}, status={}", 
                    dto.getChatId(), dto.getMsgId(), dto.getMsgStatus());
            
            // 发送数据同步消息
            sendDataSyncMessage(OPERATION_TYPE_UPDATE_STATUS, dto.getChatId(), dto.getMsgId(), dto);
            
            return true;
        } catch (Exception e) {
            log.error("更新C2C消息接收状态失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    /**
     * 更新消息撤回状态
     * 
     * 分片说明：chatId是分片键，更新操作会路由到正确的分片
     */
    @Override
    public boolean updateC2CMsgWithdrawStatus(C2CWithdrawMsgAO dto) {
        if (StringUtils.isBlank(dto.getChatId())) {
            log.error("更新撤回状态失败：分片键chatId不能为空, msgId={}", dto.getMsgId());
            return false;
        }
        log.info("更新C2C消息撤回状态: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId());
        
        try {
            String documentId = dto.getChatId() + "_" + dto.getMsgId();
            
            Integer withdrawFlag = dto.getWithdrawFlag() != null ? 
                    dto.getWithdrawFlag() : MsgStatusEnum.MsgWithdrawStatus.YES.getCode();
            
            Query query = new Query(Criteria.where("_id").is(documentId));
            Update update = new Update()
                    .set("withdrawFlag", withdrawFlag)
                    .set("updateTime", new Date());
            
            mongoTemplate.updateFirst(query, update, ImC2CMsgRecordMongo.class);
            
            log.info("C2C消息撤回状态更新成功: chatId={}, msgId={}, withdrawFlag={}", 
                    dto.getChatId(), dto.getMsgId(), withdrawFlag);
            
            // 发送数据同步消息
            sendDataSyncMessage(OPERATION_TYPE_UPDATE_WITHDRAW, dto.getChatId(), dto.getMsgId(), dto);
            
            return true;
        } catch (Exception e) {
            log.error("更新C2C消息撤回状态失败: chatId={}, msgId={}", dto.getChatId(), dto.getMsgId(), e);
            return false;
        }
    }

    /**
     * 根据消息ID查询消息记录（分片优化查询）
     * 
     * 分片说明：查询时必须带上chatId（分片键），确保路由到正确分片
     */
    @Override
    public ImC2CMsgRecord getMessageByMsgId(String chatId, String msgId) {
        // chatId是分片键，必须非空才能精确路由到对应分片
        if (StringUtils.isBlank(chatId)) {
            log.error("查询消息失败：分片键chatId不能为空, msgId={}", msgId);
            return null;
        }
        if (StringUtils.isBlank(msgId)) {
            log.warn("msgId为空，无法查询消息记录");
            return null;
        }
        
        log.info("根据消息ID查询消息记录: chatId={}, msgId={}", chatId, msgId);
        
        try {
            String documentId = chatId + "_" + msgId;
            Optional<ImC2CMsgRecordMongo> mongoRecord = mongoRepository.findById(documentId);
            
            if (mongoRecord.isPresent()) {
                ImC2CMsgRecord record = mongoRecord.get().toRecord();
                log.info("成功查询到消息记录: chatId={}, msgId={}", chatId, msgId);
                return record;
            } else {
                log.warn("未找到消息记录: chatId={}, msgId={}", chatId, msgId);
                return null;
            }
        } catch (Exception e) {
            log.error("根据消息ID查询消息记录失败: chatId={}, msgId={}", chatId, msgId, e);
            return null;
        }
    }

    /**
     * 批量查询最后一条消息记录
     */
    @Override
    public Map<String, ImC2CMsgRecord> batchGetLastMessages(Map<String, String> chatMsgIds) {
        log.info("批量查询最后一条消息记录: chatMsgIds={}", chatMsgIds);
        Map<String, ImC2CMsgRecord> lastMsgMap = new HashMap<>();
        
        if (chatMsgIds == null || chatMsgIds.isEmpty()) {
            return lastMsgMap;
        }
        
        try {
            // 构建文档ID列表
            List<String> documentIds = chatMsgIds.entrySet().stream()
                    .filter(e -> StringUtils.isNotBlank(e.getKey()) && StringUtils.isNotBlank(e.getValue()))
                    .map(e -> e.getKey() + "_" + e.getValue())
                    .collect(Collectors.toList());
            
            // 批量查询
            List<ImC2CMsgRecordMongo> mongoRecords = (List<ImC2CMsgRecordMongo>) mongoRepository.findAllById(documentIds);
            
            // 转换结果
            for (ImC2CMsgRecordMongo mongoRecord : mongoRecords) {
                ImC2CMsgRecord record = mongoRecord.toRecord();
                lastMsgMap.put(record.getChatId(), record);
            }
            
            log.info("批量查询最后一条消息记录完成，查询到{}条记录", lastMsgMap.size());
        } catch (Exception e) {
            log.error("批量查询最后一条消息失败", e);
        }
        
        return lastMsgMap;
    }

    /**
     * 批量查询每个会话的最后一条消息记录
     */
    @Override
    public Map<String, ImC2CMsgRecord> batchGetLastMessagesByChatIds(List<String> chatIds) {
        log.info("批量查询每个会话的最后一条消息记录: chatIds={}", chatIds);
        Map<String, ImC2CMsgRecord> lastMsgMap = new HashMap<>();
        
        if (chatIds == null || chatIds.isEmpty()) {
            return lastMsgMap;
        }
        
        try {
            // 对每个会话查询最后一条消息
            for (String chatId : chatIds) {
                if (StringUtils.isNotBlank(chatId)) {
                    Optional<ImC2CMsgRecordMongo> lastMsg = mongoRepository
                            .findFirstByChatIdOrderByMsgCreateTimeDesc(chatId);
                    lastMsg.ifPresent(msg -> lastMsgMap.put(chatId, msg.toRecord()));
                }
            }
            
            log.info("批量查询最后一条消息记录完成，查询到{}条记录", lastMsgMap.size());
        } catch (Exception e) {
            log.error("批量查询每个会话的最后一条消息失败", e);
        }
        
        return lastMsgMap;
    }

    /**
     * 批量查询消息记录（根据文档ID列表）
     */
    @Override
    public Map<String, ImC2CMsgRecord> batchGetMessages(List<String> rowKeys) {
        log.info("批量查询消息记录: rowKeys数量={}", rowKeys != null ? rowKeys.size() : 0);
        Map<String, ImC2CMsgRecord> result = new HashMap<>();
        
        if (rowKeys == null || rowKeys.isEmpty()) {
            return result;
        }
        
        try {
            // rowKeys 格式为 chatId_msgId，与 MongoDB 的 _id 格式一致
            List<ImC2CMsgRecordMongo> mongoRecords = (List<ImC2CMsgRecordMongo>) mongoRepository.findAllById(rowKeys);
            
            for (ImC2CMsgRecordMongo mongoRecord : mongoRecords) {
                result.put(mongoRecord.getId(), mongoRecord.toRecord());
            }
            
            log.info("批量查询消息完成，请求{}条，返回{}条", rowKeys.size(), result.size());
        } catch (Exception e) {
            log.error("批量查询消息失败, rowKeys数量={}", rowKeys.size(), e);
        }
        
        return result;
    }

    /**
     * 查询聊天历史记录
     * 
     * 支持功能：
     * - 按会话ID查询
     * - 支持正序/倒序
     * - 支持分页（游标分页）
     * - 支持时间范围过滤
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
            // 构建查询条件
            Criteria criteria = Criteria.where("chatId").is(queryDTO.getChatId());
            
            // 添加时间范围条件
            if (queryDTO.getStartTime() != null && queryDTO.getEndTime() != null) {
                criteria.and("msgCreateTime").gte(queryDTO.getStartTime()).lte(queryDTO.getEndTime());
            } else if (queryDTO.getStartTime() != null) {
                criteria.and("msgCreateTime").gte(queryDTO.getStartTime());
            } else if (queryDTO.getEndTime() != null) {
                criteria.and("msgCreateTime").lte(queryDTO.getEndTime());
            }
            
            // 游标分页：如果有lastMsgId，需要先查询该消息的时间，再基于时间过滤
            if (StringUtils.isNotBlank(queryDTO.getLastMsgId())) {
                String lastDocId = queryDTO.getChatId() + "_" + queryDTO.getLastMsgId();
                Optional<ImC2CMsgRecordMongo> lastMsg = mongoRepository.findById(lastDocId);
                if (lastMsg.isPresent()) {
                    Long lastMsgTime = lastMsg.get().getMsgCreateTime();
                    if (queryDTO.getReverse()) {
                        // 倒序查询：获取比 lastMsgTime 更早的消息
                        criteria.and("msgCreateTime").lt(lastMsgTime);
                    } else {
                        // 正序查询：获取比 lastMsgTime 更新的消息
                        criteria.and("msgCreateTime").gt(lastMsgTime);
                    }
                }
            }
            
            // 构建排序
            Sort sort = queryDTO.getReverse() ? 
                    Sort.by(Sort.Direction.DESC, "msgCreateTime") : 
                    Sort.by(Sort.Direction.ASC, "msgCreateTime");
            
            // 多查询一条用于判断是否还有更多数据
            Pageable pageable = PageRequest.of(0, queryDTO.getPageSize() + 1, sort);
            
            // 执行查询
            Query query = new Query(criteria).with(pageable);
            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            
            // 转换结果
            List<ChatHistoryResponseDTO.ChatMessageVO> messages = new ArrayList<>();
            int count = 0;
            for (ImC2CMsgRecordMongo mongoRecord : mongoRecords) {
                if (count >= queryDTO.getPageSize()) {
                    response.setHasMore(true);
                    break;
                }
                messages.add(convertToMessageVO(mongoRecord));
                count++;
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
            
        } catch (Exception e) {
            log.error("查询聊天历史记录失败: chatId={}, userId={}", 
                    queryDTO.getChatId(), queryDTO.getUserId(), e);
        }
        
        return response;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 将 C2CSendMsgAO 转换为 MongoDB 实体
     */
    private ImC2CMsgRecordMongo convertToMongoEntity(C2CSendMsgAO dto) {
        ImC2CMsgRecordMongo entity = new ImC2CMsgRecordMongo();
        entity.setChatId(dto.getChatId());
        entity.setMsgId(dto.getMsgId());
        entity.setFromUserId(dto.getFromUserId());
        entity.setToUserId(dto.getToUserId());
        entity.setMsgFormat(dto.getMsgFormat());
        entity.setMsgContent(dto.getMsgContent());
        entity.setMsgCreateTime(dto.getMsgCreateTime());
        entity.setMsgStatus(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
        entity.setRetryCount(0);
        entity.setWithdrawFlag(MsgStatusEnum.MsgWithdrawStatus.NO.getCode());
        entity.setCreateTime(new Date());
        entity.setUpdateTime(new Date());
        
        // 构建文档ID
        entity.buildId();
        
        return entity;
    }

    /**
     * 将 MongoDB 实体转换为 ChatMessageVO
     */
    private ChatHistoryResponseDTO.ChatMessageVO convertToMessageVO(ImC2CMsgRecordMongo mongoRecord) {
        ChatHistoryResponseDTO.ChatMessageVO vo = new ChatHistoryResponseDTO.ChatMessageVO();
        vo.setRowkey(mongoRecord.getId());
        vo.setChatId(mongoRecord.getChatId());
        vo.setMsgId(mongoRecord.getMsgId());
        vo.setFromUserId(mongoRecord.getFromUserId());
        vo.setToUserId(mongoRecord.getToUserId());
        vo.setMsgFormat(mongoRecord.getMsgFormat());
        vo.setMsgContent(mongoRecord.getMsgContent());
        vo.setMsgCreateTime(mongoRecord.getMsgCreateTime());
        vo.setMsgStatus(mongoRecord.getMsgStatus());
        vo.setWithdrawFlag(mongoRecord.getWithdrawFlag());
        
        if (mongoRecord.getCreateTime() != null) {
            vo.setCreateTime(mongoRecord.getCreateTime().getTime());
        }
        if (mongoRecord.getUpdateTime() != null) {
            vo.setUpdateTime(mongoRecord.getUpdateTime().getTime());
        }
        
        return vo;
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

            String jsonStr = JSONUtil.toJsonStr(dataSyncMessage);
            ClusterEvent event = new ClusterEvent();
            event.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_DATA_SYNC);
            event.setData(jsonStr);

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

            // 更新操作时，只发送必要字段
            // 因为 ACK 消息中的 from/to 是调换的，会覆盖 ES 中正确的字段
            if (OPERATION_TYPE_UPDATE_STATUS.equals(operationType) ||
                OPERATION_TYPE_UPDATE_WITHDRAW.equals(operationType)) {
                Map<String, Object> updateData = new HashMap<>();
                updateData.put("chatId", chatId);
                updateData.put("msgId", msgId);
                updateData.put("msgStatus", getMsgStatus(data));
                updateData.put("withdrawFlag", getWithdrawFlag(data));
                dataSyncMessage.put("data", updateData);

                log.info("【DEBUG-更新操作】只发送必要字段 - chatId={}, msgId={}, 不发送 fromUserId/toUserId",
                    chatId, msgId);
            } else {
                // 新增操作时，发送完整数据
                dataSyncMessage.put("data", data);
            }

            ClusterEvent event = new ClusterEvent();
            event.setClusterEventType(ImConstant.ClusterEventTypeConstant.C2C_DATA_SYNC);
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
     * 从 data 对象中提取 msgStatus
     */
    private Integer getMsgStatus(Object data) {
        if (data instanceof C2COffLineMsgAO) {
            return ((C2COffLineMsgAO) data).getMsgStatus();
        } else if (data instanceof C2CReceivedMsgAckAO) {
            return ((C2CReceivedMsgAckAO) data).getMsgStatus();
        }
        // C2CWithdrawMsgAO 没有 msgStatus 字段，返回 null
        return null;
    }

    /**
     * 从 data 对象中提取 withdrawFlag
     */
    private Integer getWithdrawFlag(Object data) {
        if (data instanceof C2CWithdrawMsgAO) {
            return ((C2CWithdrawMsgAO) data).getWithdrawFlag();
        }
        return null;
    }

    // ==================== 新增：分页查询方法（支持ES实体格式） ====================

    /**
     * 根据会话ID查询消息（分页）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryByChatId(
            String chatId, org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询 - chatId: {}, page: {}, size: {}",
            chatId, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Query query = new Query(Criteria.where("chatId").is(chatId));
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            // 添加分页
            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            // 转换为ES实体
            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成 - chatId: {}, 结果数: {}, 总数: {}",
                chatId, esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败 - chatId: {}", chatId, e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 根据发送者ID查询消息（分页）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryByFromUserId(
            String fromUserId, org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询 - fromUserId: {}, page: {}, size: {}",
            fromUserId, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Query query = new Query(Criteria.where("fromUserId").is(fromUserId));
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成 - fromUserId: {}, 结果数: {}, 总数: {}",
                fromUserId, esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败 - fromUserId: {}", fromUserId, e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 根据接收者ID查询消息（分页）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryByToUserId(
            String toUserId, org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询 - toUserId: {}, page: {}, size: {}",
            toUserId, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Query query = new Query(Criteria.where("toUserId").is(toUserId));
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成 - toUserId: {}, 结果数: {}, 总数: {}",
                toUserId, esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败 - toUserId: {}", toUserId, e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 根据消息内容查询（分页，模糊搜索）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryByContent(
            String content, org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询（模糊搜索） - content: {}, page: {}, size: {}",
            content, pageable.getPageNumber(), pageable.getPageSize());

        try {
            // 使用正则表达式进行模糊搜索
            Criteria criteria = Criteria.where("msgContent").regex(content, "i"); // i表示不区分大小写
            Query query = new Query(criteria);
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成 - content: {}, 结果数: {}, 总数: {}",
                content, esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败 - content: {}", content, e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 根据会话ID和内容查询（分页）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryByChatIdAndContent(
            String chatId, String content, org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询 - chatId: {}, content: {}, page: {}, size: {}",
            chatId, content, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Criteria criteria = Criteria.where("chatId").is(chatId)
                .and("msgContent").regex(content, "i");
            Query query = new Query(criteria);
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成 - chatId: {}, content: {}, 结果数: {}, 总数: {}",
                chatId, content, esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败 - chatId: {}, content: {}", chatId, content, e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 根据时间范围查询（分页）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryByTimeRange(
            String chatId, Long startTime, Long endTime, org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询 - chatId: {}, startTime: {}, endTime: {}, page: {}, size: {}",
            chatId, startTime, endTime, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Criteria criteria = Criteria.where("chatId").is(chatId);
            if (startTime != null && endTime != null) {
                criteria.and("msgCreateTime").gte(startTime).lte(endTime);
            } else if (startTime != null) {
                criteria.and("msgCreateTime").gte(startTime);
            } else if (endTime != null) {
                criteria.and("msgCreateTime").lte(endTime);
            }

            Query query = new Query(criteria);
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成 - chatId: {}, 结果数: {}, 总数: {}",
                chatId, esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败 - chatId: {}, startTime: {}, endTime: {}",
                chatId, startTime, endTime, e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 根据消息状态查询（分页）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryByMsgStatus(
            String chatId, Integer msgStatus, org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询 - chatId: {}, msgStatus: {}, page: {}, size: {}",
            chatId, msgStatus, pageable.getPageNumber(), pageable.getPageSize());

        try {
            Criteria criteria = Criteria.where("chatId").is(chatId)
                .and("msgStatus").is(msgStatus);
            Query query = new Query(criteria);
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成 - chatId: {}, msgStatus: {}, 结果数: {}, 总数: {}",
                chatId, msgStatus, esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败 - chatId: {}, msgStatus: {}", chatId, msgStatus, e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 复合查询（分页）
     */
    @Override
    public org.springframework.data.domain.Page<ImC2CMsgRecordES> queryComplex(
            String chatId, String fromUserId, String toUserId,
            String content, Integer msgStatus, Long startTime, Long endTime,
            org.springframework.data.domain.Pageable pageable) {
        log.info("MongoDB分页查询（复合） - chatId: {}, fromUserId: {}, toUserId: {}, content: {}, msgStatus: {}, startTime: {}, endTime: {}",
            chatId, fromUserId, toUserId, content, msgStatus, startTime, endTime);

        try {
            // 构建查询条件
            Criteria criteria = new Criteria();

            if (StringUtils.isNotBlank(chatId)) {
                criteria = Criteria.where("chatId").is(chatId);
            }

            if (StringUtils.isNotBlank(fromUserId)) {
                criteria = criteria.and("fromUserId").is(fromUserId);
            }

            if (StringUtils.isNotBlank(toUserId)) {
                criteria = criteria.and("toUserId").is(toUserId);
            }

            if (StringUtils.isNotBlank(content)) {
                criteria = criteria.and("msgContent").regex(content, "i");
            }

            if (msgStatus != null) {
                criteria = criteria.and("msgStatus").is(msgStatus);
            }

            if (startTime != null && endTime != null) {
                criteria = criteria.and("msgCreateTime").gte(startTime).lte(endTime);
            } else if (startTime != null) {
                criteria = criteria.and("msgCreateTime").gte(startTime);
            } else if (endTime != null) {
                criteria = criteria.and("msgCreateTime").lte(endTime);
            }

            Query query = new Query(criteria);
            query.with(org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Direction.DESC, "msgCreateTime"));

            query.skip((long) pageable.getPageSize() * pageable.getPageNumber());
            query.limit(pageable.getPageSize());

            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            long total = mongoTemplate.count(query.skip(-1).limit(-1), ImC2CMsgRecordMongo.class);

            List<ImC2CMsgRecordES> esRecords = mongoRecords.stream()
                .map(this::convertMongoToES)
                .collect(java.util.stream.Collectors.toList());

            log.info("MongoDB分页查询完成（复合） - 结果数: {}, 总数: {}", esRecords.size(), total);

            return new org.springframework.data.domain.PageImpl<>(esRecords, pageable, total);

        } catch (Exception e) {
            log.error("MongoDB分页查询失败（复合）", e);
            return org.springframework.data.domain.Page.empty();
        }
    }

    /**
     * 将 MongoDB 实体转换为 ES 实体
     */
    private ImC2CMsgRecordES convertMongoToES(ImC2CMsgRecordMongo mongoRecord) {
        if (mongoRecord == null) {
            return null;
        }

        ImC2CMsgRecordES esRecord = new ImC2CMsgRecordES();
        esRecord.setId(mongoRecord.getId());
        esRecord.setRowkey(mongoRecord.getId());
        esRecord.setChatId(mongoRecord.getChatId());
        esRecord.setMsgId(mongoRecord.getMsgId());
        esRecord.setFromUserId(mongoRecord.getFromUserId());
        esRecord.setToUserId(mongoRecord.getToUserId());
        esRecord.setMsgFormat(mongoRecord.getMsgFormat());
        esRecord.setMsgContent(mongoRecord.getMsgContent());
        esRecord.setMsgCreateTime(mongoRecord.getMsgCreateTime());
        esRecord.setMsgStatus(mongoRecord.getMsgStatus());
        esRecord.setRetryCount(mongoRecord.getRetryCount());
        esRecord.setWithdrawFlag(mongoRecord.getWithdrawFlag());

        // 转换时间
        if (mongoRecord.getCreateTime() != null) {
            esRecord.setCreateTime(java.time.LocalDateTime.ofInstant(
                mongoRecord.getCreateTime().toInstant(),
                java.time.ZoneId.systemDefault()
            ));
        }

        if (mongoRecord.getUpdateTime() != null) {
            esRecord.setUpdateTime(java.time.LocalDateTime.ofInstant(
                mongoRecord.getUpdateTime().toInstant(),
                java.time.ZoneId.systemDefault()
            ));
        }

        return esRecord;
    }
}
