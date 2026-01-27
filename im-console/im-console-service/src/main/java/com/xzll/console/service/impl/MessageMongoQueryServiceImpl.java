package com.xzll.console.service.impl;

import cn.hutool.core.util.StrUtil;
import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.entity.mongo.ImC2CMsgRecordMongo;
import com.xzll.console.service.MessageMongoQueryService;
import com.xzll.console.vo.MessageSearchResultVO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * MongoDB 消息查询服务实现类
 * 
 * 功能说明：
 * - 替代 ES 进行消息查询
 * - 支持多条件复合查询
 * - 支持分页和排序
 * - 支持模糊搜索
 * 
 * 分片说明（重要）：
 * - 分片键: chatId（哈希分片）
 * - 查询时尽量带上 chatId 参数，避免跨分片 scatter-gather 查询
 * - 不带 chatId 的查询会扫描所有分片，性能较差
 * 
 * 开关说明：
 * - mongodb.enabled=true 时启用（默认启用）
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "mongodb.enabled", havingValue = "true", matchIfMissing = true)
public class MessageMongoQueryServiceImpl implements MessageMongoQueryService {

    @Resource
    private MongoTemplate mongoTemplate;

    @Override
    public MessageSearchResultVO search(MessageSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();
        
        // 分片查询性能警告
        boolean hasShardKey = StrUtil.isNotBlank(searchDTO.getChatId());
        if (!hasShardKey) {
            log.warn("查询未带分片键chatId，将触发跨分片scatter-gather查询，性能可能较差");
        }
        
        try {
            // 构建查询条件
            Criteria criteria = buildCriteria(searchDTO);
            Query query = new Query(criteria);
            
            // 统计总数
            long total = mongoTemplate.count(query, ImC2CMsgRecordMongo.class);
            
            // 分页参数
            int pageNum = searchDTO.getPageNum() != null ? searchDTO.getPageNum() : 1;
            int pageSize = searchDTO.getPageSize() != null ? searchDTO.getPageSize() : 20;
            if (pageSize > 100) pageSize = 100;
            
            // 排序（按消息创建时间倒序）
            Sort sort = Sort.by(Sort.Direction.DESC, "msgCreateTime");
            Pageable pageable = PageRequest.of(pageNum - 1, pageSize, sort);
            query.with(pageable);
            
            // 执行查询
            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            
            // 转换结果
            List<ImC2CMsgRecord> records = mongoRecords.stream()
                    .map(ImC2CMsgRecordMongo::toRecord)
                    .collect(Collectors.toList());
            
            long costMs = System.currentTimeMillis() - startTime;
            log.info("MongoDB查询完成: 条件={}, 命中={}, 返回={}, 耗时={}ms, 分片优化={}", 
                    summarizeConditions(searchDTO), total, records.size(), costMs, hasShardKey ? "是" : "否");
            
            MessageSearchResultVO result = MessageSearchResultVO.success(records, total, pageNum, pageSize);
            result.setDataSource("MongoDB");
            result.setCostMs(costMs);
            
            return result;
            
        } catch (Exception e) {
            log.error("MongoDB查询失败", e);
            return MessageSearchResultVO.fail("查询失败: " + e.getMessage());
        }
    }

    @Override
    public MessageSearchResultVO searchByContent(String content, int pageNum, int pageSize) {
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setContent(content);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        return search(dto);
    }

    @Override
    public List<ImC2CMsgRecord> getLatestMessages(int limit) {
        log.warn("获取最新消息未带分片键，将扫描所有分片");
        try {
            if (limit > 100) limit = 100;
            
            Query query = new Query();
            query.with(Sort.by(Sort.Direction.DESC, "msgCreateTime"));
            query.limit(limit);
            
            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            
            log.info("MongoDB获取最新消息: limit={}, 返回={}", limit, mongoRecords.size());
            
            return mongoRecords.stream()
                    .map(ImC2CMsgRecordMongo::toRecord)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("MongoDB获取最新消息失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<ImC2CMsgRecord> getMessagesByChatId(String chatId, int limit) {
        if (StrUtil.isBlank(chatId)) {
            log.warn("chatId为空，无法执行分片优化查询");
            return new ArrayList<>();
        }
        try {
            if (limit > 100) limit = 100;
            
            Query query = new Query(Criteria.where("chatId").is(chatId));
            query.with(Sort.by(Sort.Direction.DESC, "msgCreateTime"));
            query.limit(limit);
            
            List<ImC2CMsgRecordMongo> mongoRecords = mongoTemplate.find(query, ImC2CMsgRecordMongo.class);
            
            log.info("MongoDB按会话查询: chatId={}, limit={}, 返回={}", chatId, limit, mongoRecords.size());
            
            return mongoRecords.stream()
                    .map(ImC2CMsgRecordMongo::toRecord)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("MongoDB按会话查询失败: chatId={}", chatId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public boolean isConnectionHealthy() {
        try {
            // 执行一个简单的查询来检查连接
            mongoTemplate.getDb().runCommand(new org.bson.Document("ping", 1));
            return true;
        } catch (Exception e) {
            log.warn("MongoDB连接检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Long getTodayMessageCount() {
        try {
            // 计算今天的时间范围（毫秒时间戳）
            LocalDate today = LocalDate.now();
            LocalDateTime startOfDay = today.atStartOfDay();
            LocalDateTime endOfDay = today.plusDays(1).atStartOfDay();

            long startTime = startOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long endTime = endOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

            Query query = new Query(Criteria.where("msgCreateTime").gte(startTime).lt(endTime));
            long count = mongoTemplate.count(query, ImC2CMsgRecordMongo.class);

            log.info("MongoDB获取今日消息数: {}", count);
            return count;
        } catch (Exception e) {
            log.error("MongoDB获取今日消息数失败", e);
            return 0L;
        }
    }

    @Override
    public Map<String, Long> getMessagesTrend(int days) {
        Map<String, Long> result = new LinkedHashMap<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        try {
            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);

                // 计算当天的时间范围（毫秒时间戳）
                LocalDateTime startOfDay = date.atStartOfDay();
                LocalDateTime endOfDay = date.plusDays(1).atStartOfDay();

                long startTime = startOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                long endTime = endOfDay.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();

                // 从MongoDB查询当天的消息数
                Query query = new Query(Criteria.where("msgCreateTime").gte(startTime).lt(endTime));
                long count = mongoTemplate.count(query, ImC2CMsgRecordMongo.class);

                result.put(dateStr, count);
            }

            log.info("MongoDB获取消息趋势成功: days={}, 数据={}", days, result);
        } catch (Exception e) {
            log.error("MongoDB获取消息趋势失败", e);
            // 失败时返回0
            for (int i = days - 1; i >= 0; i--) {
                LocalDate date = LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);
                result.put(dateStr, 0L);
            }
        }

        return result;
    }

    @Override
    public Map<String, Object> getMessageStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 总消息数
            long totalCount = mongoTemplate.count(new Query(), ImC2CMsgRecordMongo.class);
            stats.put("totalCount", totalCount);

            // 按消息状态统计
            Map<Integer, Long> statusStats = new LinkedHashMap<>();
            for (int status = 0; status <= 3; status++) {
                Query statusQuery = new Query(Criteria.where("msgStatus").is(status));
                long count = mongoTemplate.count(statusQuery, ImC2CMsgRecordMongo.class);
                statusStats.put(status, count);
            }
            stats.put("statusStats", statusStats);

            // 按消息格式统计
            Map<Integer, Long> formatStats = new LinkedHashMap<>();
            for (int format = 0; format <= 5; format++) {
                Query formatQuery = new Query(Criteria.where("msgFormat").is(format));
                long count = mongoTemplate.count(formatQuery, ImC2CMsgRecordMongo.class);
                if (count > 0) {
                    formatStats.put(format, count);
                }
            }
            stats.put("formatStats", formatStats);

            // 撤回消息统计
            Query withdrawQuery = new Query(Criteria.where("withdrawFlag").is(1));
            long withdrawCount = mongoTemplate.count(withdrawQuery, ImC2CMsgRecordMongo.class);
            stats.put("withdrawCount", withdrawCount);

            log.info("MongoDB获取消息统计成功: 总数={}, 撤回={}", totalCount, withdrawCount);

        } catch (Exception e) {
            log.error("获取消息统计失败", e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    @Override
    public Map<String, Object> getUserMessageStatistics(String userId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 发送的消息数
            Query sentQuery = new Query(Criteria.where("fromUserId").is(userId));
            long sentCount = mongoTemplate.count(sentQuery, ImC2CMsgRecordMongo.class);
            stats.put("sentCount", sentCount);

            // 接收的消息数
            Query receivedQuery = new Query(Criteria.where("toUserId").is(userId));
            long receivedCount = mongoTemplate.count(receivedQuery, ImC2CMsgRecordMongo.class);
            stats.put("receivedCount", receivedCount);

            // 参与的会话数（使用distinct查询）
            Query chatQuery = new Query(new Criteria()
                    .orOperator(Criteria.where("fromUserId").is(userId),
                              Criteria.where("toUserId").is(userId)));
            List<String> chatIds = mongoTemplate.getCollection("im_c2c_msg_record")
                    .distinct("chatId", chatQuery.getQueryObject(), String.class)
                    .into(new ArrayList<>());
            stats.put("chatCount", chatIds.size());

            log.info("MongoDB获取用户消息统计成功: userId={}, 发送={}, 接收={}, 会话={}",
                    userId, sentCount, receivedCount, chatIds.size());

        } catch (Exception e) {
            log.error("获取用户消息统计失败，userId: {}", userId, e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    @Override
    public Map<String, Object> getChatMessageStatistics(String chatId) {
        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            Query chatQuery = new Query(Criteria.where("chatId").is(chatId));

            // 会话消息总数
            long totalCount = mongoTemplate.count(chatQuery, ImC2CMsgRecordMongo.class);
            stats.put("totalCount", totalCount);

            if (totalCount > 0) {
                // 最早消息时间
                Query minTimeQuery = chatQuery;
                minTimeQuery.with(Sort.by(Sort.Direction.ASC, "msgCreateTime"));
                minTimeQuery.limit(1);
                ImC2CMsgRecordMongo earliest = mongoTemplate.findOne(minTimeQuery, ImC2CMsgRecordMongo.class);
                if (earliest != null) {
                    stats.put("firstMessageTime", earliest.getMsgCreateTime());
                }

                // 最新消息时间
                Query maxTimeQuery = chatQuery;
                maxTimeQuery.with(Sort.by(Sort.Direction.DESC, "msgCreateTime"));
                maxTimeQuery.limit(1);
                ImC2CMsgRecordMongo latest = mongoTemplate.findOne(maxTimeQuery, ImC2CMsgRecordMongo.class);
                if (latest != null) {
                    stats.put("lastMessageTime", latest.getMsgCreateTime());
                }
            }

            log.info("MongoDB获取会话消息统计成功: chatId={}, 总数={}", chatId, totalCount);

        } catch (Exception e) {
            log.error("获取会话消息统计失败，chatId: {}", chatId, e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建查询条件
     */
    private Criteria buildCriteria(MessageSearchDTO dto) {
        Criteria criteria = new Criteria();
        List<Criteria> criteriaList = new ArrayList<>();
        
        // 会话ID（精确匹配）
        if (StrUtil.isNotBlank(dto.getChatId())) {
            criteriaList.add(Criteria.where("chatId").is(dto.getChatId()));
        }
        
        // 发送者ID（精确匹配）
        if (StrUtil.isNotBlank(dto.getFromUserId())) {
            criteriaList.add(Criteria.where("fromUserId").is(dto.getFromUserId()));
        }
        
        // 接收者ID（精确匹配）
        if (StrUtil.isNotBlank(dto.getToUserId())) {
            criteriaList.add(Criteria.where("toUserId").is(dto.getToUserId()));
        }
        
        // 消息内容（模糊搜索，不区分大小写）
        if (StrUtil.isNotBlank(dto.getContent())) {
            // 使用正则表达式实现模糊搜索
            Pattern pattern = Pattern.compile(Pattern.quote(dto.getContent()), Pattern.CASE_INSENSITIVE);
            criteriaList.add(Criteria.where("msgContent").regex(pattern));
        }
        
        // 消息状态
        if (dto.getMsgStatus() != null) {
            criteriaList.add(Criteria.where("msgStatus").is(dto.getMsgStatus()));
        }
        
        // 消息格式
        if (dto.getMsgFormat() != null) {
            criteriaList.add(Criteria.where("msgFormat").is(dto.getMsgFormat()));
        }
        
        // 撤回标志
        if (dto.getWithdrawFlag() != null) {
            criteriaList.add(Criteria.where("withdrawFlag").is(dto.getWithdrawFlag()));
        }
        
        // 时间范围
        if (dto.getStartTime() != null && dto.getEndTime() != null) {
            criteriaList.add(Criteria.where("msgCreateTime").gte(dto.getStartTime()).lte(dto.getEndTime()));
        } else if (dto.getStartTime() != null) {
            criteriaList.add(Criteria.where("msgCreateTime").gte(dto.getStartTime()));
        } else if (dto.getEndTime() != null) {
            criteriaList.add(Criteria.where("msgCreateTime").lte(dto.getEndTime()));
        }
        
        // 组合所有条件
        if (!criteriaList.isEmpty()) {
            criteria.andOperator(criteriaList.toArray(new Criteria[0]));
        }
        
        return criteria;
    }

    /**
     * 条件摘要（用于日志）
     */
    private String summarizeConditions(MessageSearchDTO dto) {
        StringBuilder sb = new StringBuilder("[");
        if (StrUtil.isNotBlank(dto.getChatId())) sb.append("chatId,");
        if (StrUtil.isNotBlank(dto.getFromUserId())) sb.append("fromUserId,");
        if (StrUtil.isNotBlank(dto.getToUserId())) sb.append("toUserId,");
        if (StrUtil.isNotBlank(dto.getContent())) sb.append("content,");
        if (dto.getMsgStatus() != null) sb.append("msgStatus,");
        if (dto.getMsgFormat() != null) sb.append("msgFormat,");
        if (dto.getStartTime() != null) sb.append("startTime,");
        if (dto.getEndTime() != null) sb.append("endTime,");
        if (sb.length() > 1) sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }
}
