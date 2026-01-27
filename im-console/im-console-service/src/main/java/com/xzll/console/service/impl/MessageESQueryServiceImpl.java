package com.xzll.console.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.entity.es.ImC2CMsgRecordES;
import com.xzll.console.service.MessageESQueryService;
import com.xzll.console.vo.MessageSearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * ES消息查询服务实现类
 * 提供基于Elasticsearch的高性能消息查询能力
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Service
@ConditionalOnProperty(prefix = "im.elasticsearch", name = "sync-enabled", havingValue = "true")
public class MessageESQueryServiceImpl implements MessageESQueryService {

    @Resource
    private ElasticsearchClient elasticsearchClient;

    private static final String INDEX_NAME = "im_c2c_msg_record";

    @Override
    public MessageSearchResultVO search(MessageSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();

        try {
            // 构建查询条件
            BoolQuery.Builder boolQueryBuilder = new BoolQuery.Builder();

            // 会话ID（精确匹配）
            if (StrUtil.isNotBlank(searchDTO.getChatId())) {
                boolQueryBuilder.must(termQuery("chatId", searchDTO.getChatId())._toQuery());
            }

            // 发送者ID（精确匹配）
            if (StrUtil.isNotBlank(searchDTO.getFromUserId())) {
                boolQueryBuilder.must(termQuery("fromUserId", searchDTO.getFromUserId())._toQuery());
            }

            // 接收者ID（精确匹配）
            if (StrUtil.isNotBlank(searchDTO.getToUserId())) {
                boolQueryBuilder.must(termQuery("toUserId", searchDTO.getToUserId())._toQuery());
            }

            // 消息内容（全文搜索）
            if (StrUtil.isNotBlank(searchDTO.getContent())) {
                boolQueryBuilder.must(matchQuery("msgContent", searchDTO.getContent())._toQuery());
            }

            // 消息状态（精确匹配）
            if (searchDTO.getMsgStatus() != null) {
                boolQueryBuilder.must(termQuery("msgStatus", searchDTO.getMsgStatus().toString())._toQuery());
            }

            // 消息格式（精确匹配）
            if (searchDTO.getMsgFormat() != null) {
                boolQueryBuilder.must(termQuery("msgFormat", searchDTO.getMsgFormat().toString())._toQuery());
            }

            // 撤回标志（精确匹配）
            if (searchDTO.getWithdrawFlag() != null) {
                boolQueryBuilder.must(termQuery("withdrawFlag", searchDTO.getWithdrawFlag().toString())._toQuery());
            }

            // 时间范围查询
            if (searchDTO.getStartTime() != null && searchDTO.getEndTime() != null) {
                boolQueryBuilder.must(Query.of(q -> q
                        .range(r -> r
                                .field("msgCreateTime")
                                .gte(JsonData.fromJson(String.valueOf(searchDTO.getStartTime())))
                                .lte(JsonData.fromJson(String.valueOf(searchDTO.getEndTime())))
                        )
                ));
            } else if (searchDTO.getStartTime() != null) {
                boolQueryBuilder.must(Query.of(q -> q
                        .range(r -> r
                                .field("msgCreateTime")
                                .gte(JsonData.fromJson(String.valueOf(searchDTO.getStartTime())))
                        )
                ));
            } else if (searchDTO.getEndTime() != null) {
                boolQueryBuilder.must(Query.of(q -> q
                        .range(r -> r
                                .field("msgCreateTime")
                                .lte(JsonData.fromJson(String.valueOf(searchDTO.getEndTime())))
                        )
                ));
            }

            // 构建查询请求
            int pageNum = searchDTO.getPageNum() != null ? searchDTO.getPageNum() : 1;
            int pageSize = searchDTO.getPageSize() != null ? searchDTO.getPageSize() : 20;
            if (pageSize > 100) pageSize = 100;

            final int finalPageSize = pageSize;
            final int finalPageNum = pageNum;

            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(boolQueryBuilder.build()._toQuery())
                    .from((finalPageNum - 1) * finalPageSize)
                    .size(finalPageSize)
                    .sort(sortOption("msgCreateTime", SortOrder.Desc))  // 按时间倒序
            );

            // 执行查询
            SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(
                    searchRequest,
                    ImC2CMsgRecordES.class
            );

            // 获取总数
            long total = response.hits().total().value();

            // 转换结果
            List<ImC2CMsgRecord> records = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(ImC2CMsgRecordES::toRecord)
                    .collect(Collectors.toList());

            long costMs = System.currentTimeMillis() - startTime;
            log.info("ES查询完成: 条件={}, 命中={}, 返回={}, 耗时={}ms",
                    summarizeConditions(searchDTO), total, records.size(), costMs);

            MessageSearchResultVO result = MessageSearchResultVO.success(records, total, pageNum, pageSize);
            result.setDataSource("ES");
            result.setCostMs(costMs);

            return result;

        } catch (Exception e) {
            log.error("ES查询失败", e);
            return MessageSearchResultVO.fail("查询失败: " + e.getMessage());
        }
    }

    @Override
    public MessageSearchResultVO searchByFromUserId(String fromUserId, int pageNum, int pageSize) {
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setFromUserId(fromUserId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        return search(dto);
    }

    @Override
    public MessageSearchResultVO searchByToUserId(String toUserId, int pageNum, int pageSize) {
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setToUserId(toUserId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        return search(dto);
    }

    @Override
    public MessageSearchResultVO searchByChatId(String chatId, int pageNum, int pageSize) {
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setChatId(chatId);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        return search(dto);
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
    public MessageSearchResultVO searchByTimeRange(Long startTime, Long endTime, int pageNum, int pageSize) {
        MessageSearchDTO dto = new MessageSearchDTO();
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        dto.setPageNum(pageNum);
        dto.setPageSize(pageSize);
        return search(dto);
    }

    @Override
    public List<ImC2CMsgRecord> getLatestMessages(int limit) {
        try {
            if (limit > 100) limit = 100;
            final int finalLimit = limit;

            // 查询所有消息，按时间倒序
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(Query.of(q -> q.matchAll(m -> m)))
                    .size(finalLimit)
                    .sort(sortOption("msgCreateTime", SortOrder.Desc))
            );

            SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(
                    searchRequest,
                    ImC2CMsgRecordES.class
            );

            List<ImC2CMsgRecord> records = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(ImC2CMsgRecordES::toRecord)
                    .collect(Collectors.toList());

            log.info("ES获取最新消息: limit={}, 返回={}", limit, records.size());
            return records;

        } catch (Exception e) {
            log.error("ES获取最新消息失败", e);
            return new ArrayList<>();
        }
    }

    @Override
    public Map<String, Object> getMessageStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();

        try {
            // 总消息数
            long totalCount = countByQuery(Query.of(q -> q.matchAll(m -> m)));
            stats.put("totalCount", totalCount);

            // 按消息状态统计（使用聚合查询）
            for (int i = 0; i <= 3; i++) {
                final int status = i;
                long count = countByQuery(Query.of(q -> q
                        .term(t -> t
                                .field("msgStatus")
                                .value(status)
                        )
                ));
                stats.put("status_" + status, count);
            }

            // 按消息格式统计
            for (int i = 0; i <= 5; i++) {
                final int format = i;
                Query query = Query.of(q -> q
                        .term(t -> t
                                .field("msgFormat")
                                .value(format)
                        )
                );
                long count = countByQuery(query);
                if (count > 0) {
                    stats.put("format_" + format, count);
                }
            }

            // 撤回消息统计
            long withdrawCount = countByQuery(Query.of(q -> q
                    .term(t -> t
                            .field("withdrawFlag")
                            .value(1)
                    )
            ));
            stats.put("withdrawCount", withdrawCount);

            log.info("ES获取消息统计成功: 总数={}, 撤回={}", totalCount, withdrawCount);

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
            long sentCount = countByQuery(Query.of(q -> q
                    .term(t -> t
                            .field("fromUserId")
                            .value(userId)
                    )
            ));
            stats.put("sentCount", sentCount);

            // 接收的消息数
            long receivedCount = countByQuery(Query.of(q -> q
                    .term(t -> t
                            .field("toUserId")
                            .value(userId)
                    )
            ));
            stats.put("receivedCount", receivedCount);

            // 参与的会话数（使用聚合查询获取去重后的chatId数量）
            long chatCount = countDistinctChatIds(userId);
            stats.put("chatCount", chatCount);

            log.info("ES获取用户消息统计成功: userId={}, 发送={}, 接收={}, 会话={}",
                    userId, sentCount, receivedCount, chatCount);

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
            // 会话消息总数
            long totalCount = countByQuery(Query.of(q -> q
                    .term(t -> t
                            .field("chatId")
                            .value(chatId)
                    )
            ));
            stats.put("totalCount", totalCount);

            if (totalCount > 0) {
                // 最早消息时间
                SearchRequest minTimeRequest = SearchRequest.of(s -> s
                        .index(INDEX_NAME)
                        .query(Query.of(q -> q.term(t -> t.field("chatId").value(chatId))))
                        .size(1)
                        .sort(sortOption("msgCreateTime", SortOrder.Asc))
                );

                SearchResponse<ImC2CMsgRecordES> minResponse = elasticsearchClient.search(
                        minTimeRequest,
                        ImC2CMsgRecordES.class
                );

                if (!minResponse.hits().hits().isEmpty()) {
                    ImC2CMsgRecordES earliest = minResponse.hits().hits().get(0).source();
                    if (earliest != null) {
                        stats.put("firstMessageTime", earliest.getMsgCreateTime());
                    }
                }

                // 最新消息时间
                SearchRequest maxTimeRequest = SearchRequest.of(s -> s
                        .index(INDEX_NAME)
                        .query(Query.of(q -> q.term(t -> t.field("chatId").value(chatId))))
                        .size(1)
                        .sort(sortOption("msgCreateTime", SortOrder.Desc))
                );

                SearchResponse<ImC2CMsgRecordES> maxResponse = elasticsearchClient.search(
                        maxTimeRequest,
                        ImC2CMsgRecordES.class
                );

                if (!maxResponse.hits().hits().isEmpty()) {
                    ImC2CMsgRecordES latest = maxResponse.hits().hits().get(0).source();
                    if (latest != null) {
                        stats.put("lastMessageTime", latest.getMsgCreateTime());
                    }
                }
            }

            log.info("ES获取会话消息统计成功: chatId={}, 总数={}", chatId, totalCount);

        } catch (Exception e) {
            log.error("获取会话消息统计失败，chatId: {}", chatId, e);
            stats.put("error", e.getMessage());
        }

        return stats;
    }

    @Override
    public boolean isConnectionHealthy() {
        try {
            // 执行ping命令检查连接
            elasticsearchClient.ping();
            return true;
        } catch (Exception e) {
            log.warn("ES连接检查失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Map<String, Object> getIndexInfo() {
        Map<String, Object> info = new LinkedHashMap<>();

        try {
            // 获取索引信息
            // 注意：这里简化实现，实际可以使用 indices API 获取更详细的信息
            info.put("indexName", INDEX_NAME);
            info.put("status", "active");
            log.info("ES索引信息: {}", info);
        } catch (Exception e) {
            log.error("获取ES索引信息失败", e);
            info.put("error", e.getMessage());
        }

        return info;
    }

    @Override
    public Long getTodayMessageCount() {
        try {
            // 计算今天的时间范围（毫秒时间戳）
            long startTime = java.time.LocalDate.now()
                    .atStartOfDay()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            long endTime = java.time.LocalDate.now()
                    .plusDays(1)
                    .atStartOfDay()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();

            long count = countByQuery(Query.of(q -> q
                    .range(r -> r
                            .field("msgCreateTime")
                            .gte(JsonData.fromJson(String.valueOf(startTime)))
                            .lt(JsonData.fromJson(String.valueOf(endTime)))
                    )
            ));

            log.info("ES获取今日消息数: {}", count);
            return count;

        } catch (Exception e) {
            log.error("ES获取今日消息数失败", e);
            return 0L;
        }
    }

    @Override
    public Map<String, Long> getMessagesTrend(int days) {
        Map<String, Long> result = new LinkedHashMap<>();
        java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MM-dd");

        try {
            for (int i = days - 1; i >= 0; i--) {
                java.time.LocalDate date = java.time.LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);

                // 计算当天的时间范围（毫秒时间戳）
                long startTime = date.atStartOfDay()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();

                long endTime = date.plusDays(1).atStartOfDay()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli();

                // 从ES查询当天的消息数
                long count = countByQuery(Query.of(q -> q
                        .range(r -> r
                                .field("msgCreateTime")
                                .gte(JsonData.fromJson(String.valueOf(startTime)))
                                .lt(JsonData.fromJson(String.valueOf(endTime)))
                        )
                ));

                result.put(dateStr, count);
            }

            log.info("ES获取消息趋势成功: days={}, 数据={}", days, result);

        } catch (Exception e) {
            log.error("ES获取消息趋势失败", e);
            // 失败时返回0
            for (int i = days - 1; i >= 0; i--) {
                java.time.LocalDate date = java.time.LocalDate.now().minusDays(i);
                String dateStr = date.format(formatter);
                result.put(dateStr, 0L);
            }
        }

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建term查询
     */
    private TermQuery termQuery(String field, String value) {
        return TermQuery.of(t -> t.field(field).value(value));
    }

    /**
     * 构建match查询
     */
    private MatchQuery matchQuery(String field, String value) {
        return MatchQuery.of(m -> m.field(field).query(value));
    }

    /**
     * 构建range查询
     */
    private RangeQuery rangeQuery(String field) {
        return RangeQuery.of(r -> r.field(field));
    }

    /**
     * 构建排序选项
     */
    private co.elastic.clients.elasticsearch._types.SortOptions sortOption(String field, SortOrder order) {
        return co.elastic.clients.elasticsearch._types.SortOptions.of(s -> s
                .field(f -> f.field(field).order(order))
        );
    }

    /**
     * 按查询条件统计数量
     */
    private long countByQuery(Query query) {
        try {
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(query)
                    .size(0)  // 不需要返回文档，只要总数
            );

            SearchResponse<?> response = elasticsearchClient.search(
                    searchRequest,
                    Object.class
            );

            return response.hits().total().value();

        } catch (Exception e) {
            log.error("ES统计数量失败", e);
            return 0;
        }
    }

    /**
     * 统计用户参与的去重会话数
     */
    private long countDistinctChatIds(String userId) {
        try {
            // 使用terms聚合获取去重的chatId
            // 简化实现：这里使用bool查询（发送者或接收者匹配）+ cardinality聚合
            SearchRequest searchRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(Query.of(q -> q
                            .bool(b -> b
                                    .should(sh -> sh.term(t -> t.field("fromUserId").value(userId)))
                                    .should(sh -> sh.term(t -> t.field("toUserId").value(userId)))
                            )
                    ))
                    .size(0)
                    .aggregations("unique_chats", a -> a
                            .cardinality(c -> c.field("chatId"))
                    )
            );

            SearchResponse<?> response = elasticsearchClient.search(
                    searchRequest,
                    Object.class
            );

            return (long) response.aggregations().get("unique_chats")
                    .cardinality().value();

        } catch (Exception e) {
            log.error("ES统计去重会话数失败", e);
            return 0;
        }
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
