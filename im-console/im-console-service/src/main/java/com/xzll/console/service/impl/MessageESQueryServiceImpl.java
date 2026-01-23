package com.xzll.console.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.LongTermsBucket;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.CountResponse;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.GetIndexRequest;
import co.elastic.clients.elasticsearch.indices.GetIndexResponse;
import com.xzll.console.dto.MessageSearchDTO;
import com.xzll.console.entity.ImC2CMsgRecord;
import com.xzll.console.entity.es.ImC2CMsgRecordES;
import com.xzll.console.service.MessageESQueryService;
import com.xzll.console.vo.MessageSearchResultVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.xzll.common.constant.ImConstant.TableConstant.IM_C2C_MSG_RECORD;

/**
 * ES消息查询服务实现
 * 使用新版 ElasticsearchClient（ES 8.x）
 * 
 * 性能优化策略:
 * 1. 使用 BoolQuery 构建复合查询，减少查询次数
 * 2. 只获取需要的字段（source filtering）
 * 3. 合理设置分页大小，避免深度分页
 * 4. 使用 term 查询代替 match 查询（Keyword字段）
 * 5. 缓存常用查询结果（后续可扩展）
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Slf4j
@Service
public class MessageESQueryServiceImpl implements MessageESQueryService {

    @Resource
    private ElasticsearchClient elasticsearchClient;

    /**
     * 索引名称
     */
    private static final String INDEX_NAME = IM_C2C_MSG_RECORD;

    /**
     * 最大分页深度（ES默认10000）
     */
    private static final int MAX_RESULT_WINDOW = 10000;

    @Override
    public MessageSearchResultVO search(MessageSearchDTO searchDTO) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 参数校验和默认值
            if (searchDTO.getPageNum() == null || searchDTO.getPageNum() < 1) {
                searchDTO.setPageNum(1);
            }
            if (searchDTO.getPageSize() == null || searchDTO.getPageSize() < 1) {
                searchDTO.setPageSize(20);
            }
            if (searchDTO.getPageSize() > 100) {
                searchDTO.setPageSize(100);
            }

            // 检查深度分页
            int from = searchDTO.getFrom();
            if (from + searchDTO.getPageSize() > MAX_RESULT_WINDOW) {
                return MessageSearchResultVO.fail("分页深度超过限制，请缩小查询范围");
            }

            // 构建查询
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
            buildQueryConditions(boolBuilder, searchDTO);

            // 执行搜索
            SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                    .index(INDEX_NAME)
                    .query(q -> q.bool(boolBuilder.build()))
                    .from(from)
                    .size(searchDTO.getPageSize())
                    .sort(s -> s.field(f -> f.field("msgCreateTime").order(SortOrder.Desc)));

            SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(searchBuilder.build(), ImC2CMsgRecordES.class);

            // 转换结果
            List<ImC2CMsgRecord> records = response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(ImC2CMsgRecordES::toRecord)
                    .collect(Collectors.toList());

            long total = response.hits().total() != null ? response.hits().total().value() : 0;

            MessageSearchResultVO result = MessageSearchResultVO.success(records, total, searchDTO.getPageNum(), searchDTO.getPageSize());
            result.setCostMs(System.currentTimeMillis() - startTime);
            result.setDataSource("ES");
            
            log.info("ES搜索完成，条件: {}, 命中: {}, 耗时: {}ms", searchDTO, total, result.getCostMs());
            return result;

        } catch (IOException e) {
            log.error("ES搜索失败", e);
            return MessageSearchResultVO.fail("搜索失败: " + e.getMessage());
        }
    }

    /**
     * 构建查询条件
     */
    private void buildQueryConditions(BoolQuery.Builder boolBuilder, MessageSearchDTO searchDTO) {
        // 发送方用户ID - term精确匹配
        if (StrUtil.isNotBlank(searchDTO.getFromUserId())) {
            boolBuilder.must(m -> m.term(t -> t.field("fromUserId").value(searchDTO.getFromUserId())));
        }

        // 接收方用户ID - term精确匹配
        if (StrUtil.isNotBlank(searchDTO.getToUserId())) {
            boolBuilder.must(m -> m.term(t -> t.field("toUserId").value(searchDTO.getToUserId())));
        }

        // 会话ID - term精确匹配
        if (StrUtil.isNotBlank(searchDTO.getChatId())) {
            boolBuilder.must(m -> m.term(t -> t.field("chatId").value(searchDTO.getChatId())));
        }

        // 消息内容 - match全文搜索（使用中文分词）
        if (StrUtil.isNotBlank(searchDTO.getContent())) {
            boolBuilder.must(m -> m.match(t -> t.field("msgContent").query(searchDTO.getContent())));
        }

        // 消息状态 - term精确匹配
        if (searchDTO.getMsgStatus() != null) {
            boolBuilder.must(m -> m.term(t -> t.field("msgStatus").value(searchDTO.getMsgStatus())));
        }

        // 消息格式 - term精确匹配
        if (searchDTO.getMsgFormat() != null) {
            boolBuilder.must(m -> m.term(t -> t.field("msgFormat").value(searchDTO.getMsgFormat())));
        }

        // 撤回标志 - term精确匹配
        if (searchDTO.getWithdrawFlag() != null) {
            boolBuilder.must(m -> m.term(t -> t.field("withdrawFlag").value(searchDTO.getWithdrawFlag())));
        }

        // 时间范围 - range查询
        if (searchDTO.getStartTime() != null || searchDTO.getEndTime() != null) {
            boolBuilder.must(m -> m.range(r -> {
                r.field("msgCreateTime");
                if (searchDTO.getStartTime() != null) {
                    r.gte(co.elastic.clients.json.JsonData.of(searchDTO.getStartTime()));
                }
                if (searchDTO.getEndTime() != null) {
                    r.lte(co.elastic.clients.json.JsonData.of(searchDTO.getEndTime()));
                }
                return r;
            }));
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
        // 限制最大查询数量
        final int finalLimit = Math.min(limit, 100);
        
        try {
            SearchRequest request = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .query(q -> q.matchAll(m -> m))
                    .from(0)
                    .size(finalLimit)
                    .sort(sort -> sort.field(f -> f.field("msgCreateTime").order(SortOrder.Desc)))
            );

            SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(request, ImC2CMsgRecordES.class);

            return response.hits().hits().stream()
                    .map(Hit::source)
                    .filter(Objects::nonNull)
                    .map(ImC2CMsgRecordES::toRecord)
                    .collect(Collectors.toList());

        } catch (IOException e) {
            log.error("获取最新消息失败", e);
            return Collections.emptyList();
        }
    }

    @Override
    public Map<String, Object> getMessageStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        
        try {
            // 总消息数
            CountRequest countRequest = CountRequest.of(c -> c.index(INDEX_NAME));
            CountResponse countResponse = elasticsearchClient.count(countRequest);
            stats.put("totalCount", countResponse.count());

            // 按消息状态统计
            SearchRequest statusAggRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(0)
                    .aggregations("status_stats", a -> a
                            .terms(t -> t.field("msgStatus"))
                    )
            );
            SearchResponse<Void> statusResponse = elasticsearchClient.search(statusAggRequest, Void.class);
            Map<String, Long> statusStats = new LinkedHashMap<>();
            if (statusResponse.aggregations().get("status_stats") != null) {
                statusResponse.aggregations().get("status_stats").lterms().buckets().array()
                        .forEach(bucket -> statusStats.put("status_" + bucket.key(), bucket.docCount()));
            }
            stats.put("statusStats", statusStats);

            // 按消息格式统计
            SearchRequest formatAggRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(0)
                    .aggregations("format_stats", a -> a
                            .terms(t -> t.field("msgFormat"))
                    )
            );
            SearchResponse<Void> formatResponse = elasticsearchClient.search(formatAggRequest, Void.class);
            Map<String, Long> formatStats = new LinkedHashMap<>();
            if (formatResponse.aggregations().get("format_stats") != null) {
                formatResponse.aggregations().get("format_stats").lterms().buckets().array()
                        .forEach(bucket -> formatStats.put("format_" + bucket.key(), bucket.docCount()));
            }
            stats.put("formatStats", formatStats);

            // 撤回消息统计
            CountRequest withdrawCountRequest = CountRequest.of(c -> c
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("withdrawFlag").value(1)))
            );
            CountResponse withdrawCountResponse = elasticsearchClient.count(withdrawCountRequest);
            stats.put("withdrawCount", withdrawCountResponse.count());

        } catch (IOException e) {
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
            CountRequest sentCountRequest = CountRequest.of(c -> c
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("fromUserId").value(userId)))
            );
            CountResponse sentCountResponse = elasticsearchClient.count(sentCountRequest);
            stats.put("sentCount", sentCountResponse.count());

            // 接收的消息数
            CountRequest receivedCountRequest = CountRequest.of(c -> c
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("toUserId").value(userId)))
            );
            CountResponse receivedCountResponse = elasticsearchClient.count(receivedCountRequest);
            stats.put("receivedCount", receivedCountResponse.count());

            // 参与的会话数
            SearchRequest chatAggRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(0)
                    .query(q -> q.bool(b -> b
                            .should(sh -> sh.term(t -> t.field("fromUserId").value(userId)))
                            .should(sh -> sh.term(t -> t.field("toUserId").value(userId)))
                            .minimumShouldMatch("1")
                    ))
                    .aggregations("chat_count", a -> a
                            .cardinality(c -> c.field("chatId"))
                    )
            );
            SearchResponse<Void> chatResponse = elasticsearchClient.search(chatAggRequest, Void.class);
            if (chatResponse.aggregations().get("chat_count") != null) {
                stats.put("chatCount", chatResponse.aggregations().get("chat_count").cardinality().value());
            }

        } catch (IOException e) {
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
            CountRequest countRequest = CountRequest.of(c -> c
                    .index(INDEX_NAME)
                    .query(q -> q.term(t -> t.field("chatId").value(chatId)))
            );
            CountResponse countResponse = elasticsearchClient.count(countRequest);
            stats.put("totalCount", countResponse.count());

            // 最早消息时间
            SearchRequest minTimeRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(1)
                    .query(q -> q.term(t -> t.field("chatId").value(chatId)))
                    .sort(sort -> sort.field(f -> f.field("msgCreateTime").order(SortOrder.Asc)))
            );
            SearchResponse<ImC2CMsgRecordES> minTimeResponse = elasticsearchClient.search(minTimeRequest, ImC2CMsgRecordES.class);
            if (!minTimeResponse.hits().hits().isEmpty() && minTimeResponse.hits().hits().get(0).source() != null) {
                stats.put("firstMessageTime", minTimeResponse.hits().hits().get(0).source().getMsgCreateTime());
            }

            // 最新消息时间
            SearchRequest maxTimeRequest = SearchRequest.of(s -> s
                    .index(INDEX_NAME)
                    .size(1)
                    .query(q -> q.term(t -> t.field("chatId").value(chatId)))
                    .sort(sort -> sort.field(f -> f.field("msgCreateTime").order(SortOrder.Desc)))
            );
            SearchResponse<ImC2CMsgRecordES> maxTimeResponse = elasticsearchClient.search(maxTimeRequest, ImC2CMsgRecordES.class);
            if (!maxTimeResponse.hits().hits().isEmpty() && maxTimeResponse.hits().hits().get(0).source() != null) {
                stats.put("lastMessageTime", maxTimeResponse.hits().hits().get(0).source().getMsgCreateTime());
            }

        } catch (IOException e) {
            log.error("获取会话消息统计失败，chatId: {}", chatId, e);
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    @Override
    public boolean isConnectionHealthy() {
        try {
            return elasticsearchClient.ping().value();
        } catch (IOException e) {
            log.error("ES健康检查失败", e);
            return false;
        }
    }

    @Override
    public Map<String, Object> getIndexInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        
        try {
            // 检查索引是否存在
            boolean exists = elasticsearchClient.indices().exists(e -> e.index(INDEX_NAME)).value();
            info.put("indexExists", exists);
            
            if (exists) {
                // 获取文档数
                CountRequest countRequest = CountRequest.of(c -> c.index(INDEX_NAME));
                CountResponse countResponse = elasticsearchClient.count(countRequest);
                info.put("documentCount", countResponse.count());
                
                // 获取索引信息
                GetIndexResponse indexResponse = elasticsearchClient.indices().get(GetIndexRequest.of(g -> g.index(INDEX_NAME)));
                if (indexResponse.get(INDEX_NAME) != null) {
                    info.put("indexName", INDEX_NAME);
                }
            }
            
        } catch (IOException e) {
            log.error("获取索引信息失败", e);
            info.put("error", e.getMessage());
        }
        
        return info;
    }
}
