package com.xzll.business.service.impl;

import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.business.service.ImC2CMsgRecordESQueryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.json.JsonData;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息ES查询服务实现类（Spring Boot 3.x + Spring Data Elasticsearch 5.x）
 */
@Service
@Slf4j
public class ImC2CMsgRecordESQueryServiceImpl implements ImC2CMsgRecordESQueryService {

    @Resource
    private ElasticsearchTemplate elasticsearchTemplate;

    @Override
    public Page<ImC2CMsgRecordES> findByChatId(String chatId, Pageable pageable) {
        try {
            Query query = BoolQuery.of(b -> b
                    .must(m -> m.term(t -> t.field("chatId").value(chatId)))
            )._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据会话ID查询消息失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByFromUserId(String fromUserId, Pageable pageable) {
        try {
            Query query = BoolQuery.of(b -> b
                    .must(m -> m.term(t -> t.field("fromUserId").value(fromUserId)))
            )._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据发送者ID查询消息失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByToUserId(String toUserId, Pageable pageable) {
        try {
            Query query = BoolQuery.of(b -> b
                    .must(m -> m.term(t -> t.field("toUserId").value(toUserId)))
            )._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据接收者ID查询消息失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> searchByContent(String content, Pageable pageable) {
        try {
            Query query = BoolQuery.of(b -> b
                    .must(m -> m.match(mt -> mt.field("msgContent").query(content)))
            )._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据消息内容搜索失败", e);
            throw new RuntimeException("搜索失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> searchByChatIdAndContent(String chatId, String content, Pageable pageable) {
        try {
            Query query = BoolQuery.of(b -> b
                    .must(m -> m.term(t -> t.field("chatId").value(chatId)))
                    .must(m -> m.match(mt -> mt.field("msgContent").query(content)))
            )._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据会话ID和内容搜索失败", e);
            throw new RuntimeException("搜索失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByTimeRange(String chatId, Long startTime, Long endTime, Pageable pageable) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder()
                    .must(m -> m.term(t -> t.field("chatId").value(chatId)));

            if (startTime != null || endTime != null) {
                RangeQuery.Builder rangeBuilder = new RangeQuery.Builder().field("msgCreateTime");
                if (startTime != null) {
                    rangeBuilder.gte(JsonData.of(startTime));
                }
                if (endTime != null) {
                    rangeBuilder.lte(JsonData.of(endTime));
                }
                boolBuilder.must(m -> m.range(rangeBuilder.build()));
            }

            Query query = boolBuilder.build()._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据时间范围查询失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByMsgStatus(String chatId, Integer msgStatus, Pageable pageable) {
        try {
            Query query = BoolQuery.of(b -> b
                    .must(m -> m.term(t -> t.field("chatId").value(chatId)))
                    .must(m -> m.term(t -> t.field("msgStatus").value(msgStatus)))
            )._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据消息状态查询失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> complexSearch(String chatId, String fromUserId, String toUserId,
                                                String content, Integer msgStatus, Long startTime,
                                                Long endTime, Pageable pageable) {
        try {
            BoolQuery.Builder boolBuilder = new BoolQuery.Builder();

            // 添加各种查询条件
            if (chatId != null && !chatId.trim().isEmpty()) {
                boolBuilder.must(m -> m.term(t -> t.field("chatId").value(chatId)));
            }

            if (fromUserId != null && !fromUserId.trim().isEmpty()) {
                boolBuilder.must(m -> m.term(t -> t.field("fromUserId").value(fromUserId)));
            }

            if (toUserId != null && !toUserId.trim().isEmpty()) {
                boolBuilder.must(m -> m.term(t -> t.field("toUserId").value(toUserId)));
            }

            if (content != null && !content.trim().isEmpty()) {
                boolBuilder.must(m -> m.match(mt -> mt.field("msgContent").query(content)));
            }

            if (msgStatus != null) {
                boolBuilder.must(m -> m.term(t -> t.field("msgStatus").value(msgStatus)));
            }

            if (startTime != null || endTime != null) {
                RangeQuery.Builder rangeBuilder = new RangeQuery.Builder().field("msgCreateTime");
                if (startTime != null) {
                    rangeBuilder.gte(JsonData.of(startTime));
                }
                if (endTime != null) {
                    rangeBuilder.lte(JsonData.of(endTime));
                }
                boolBuilder.must(m -> m.range(rangeBuilder.build()));
            }

            Query query = boolBuilder.build()._toQuery();

            NativeQuery searchQuery = NativeQuery.builder()
                    .withQuery(query)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();

            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("复合查询失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    /**
     * 将SearchHits转换为Page对象
     */
    private Page<ImC2CMsgRecordES> convertToPage(SearchHits<ImC2CMsgRecordES> searchHits, Pageable pageable) {
        List<ImC2CMsgRecordES> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(Collectors.toList());

        return new PageImpl<>(content, pageable, searchHits.getTotalHits());
    }
}
