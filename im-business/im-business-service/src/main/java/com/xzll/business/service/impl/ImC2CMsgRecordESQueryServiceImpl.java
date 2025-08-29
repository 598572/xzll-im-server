package com.xzll.business.service.impl;

import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.business.service.ImC2CMsgRecordESQueryService;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息ES查询服务实现类
 */
@Service
@Slf4j
public class ImC2CMsgRecordESQueryServiceImpl implements ImC2CMsgRecordESQueryService {

    @Resource
    private ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Override
    public Page<ImC2CMsgRecordES> findByChatId(String chatId, Pageable pageable) {
        try {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("chatId", chatId));
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据会话ID查询消息失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByFromUserId(String fromUserId, Pageable pageable) {
        try {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("fromUserId", fromUserId));
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据发送者ID查询消息失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByToUserId(String toUserId, Pageable pageable) {
        try {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("toUserId", toUserId));
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据接收者ID查询消息失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> searchByContent(String content, Pageable pageable) {
        try {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.matchQuery("msgContent", content));
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据消息内容搜索失败", e);
            throw new RuntimeException("搜索失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> searchByChatIdAndContent(String chatId, String content, Pageable pageable) {
        try {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("chatId", chatId))
                    .must(QueryBuilders.matchQuery("msgContent", content));
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据会话ID和内容搜索失败", e);
            throw new RuntimeException("搜索失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByTimeRange(String chatId, Long startTime, Long endTime, Pageable pageable) {
        try {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("chatId", chatId));
            
            if (startTime != null || endTime != null) {
                RangeQueryBuilder timeRangeQuery = QueryBuilders.rangeQuery("msgCreateTime");
                if (startTime != null) {
                    timeRangeQuery.gte(startTime);
                }
                if (endTime != null) {
                    timeRangeQuery.lte(endTime);
                }
                queryBuilder.must(timeRangeQuery);
            }
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
            return convertToPage(searchHits, pageable);
        } catch (Exception e) {
            log.error("根据时间范围查询失败", e);
            throw new RuntimeException("查询失败", e);
        }
    }

    @Override
    public Page<ImC2CMsgRecordES> findByMsgStatus(String chatId, Integer msgStatus, Pageable pageable) {
        try {
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery()
                    .must(QueryBuilders.termQuery("chatId", chatId))
                    .must(QueryBuilders.termQuery("msgStatus", msgStatus));
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
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
            BoolQueryBuilder queryBuilder = QueryBuilders.boolQuery();
            
            // 添加各种查询条件
            if (chatId != null && !chatId.trim().isEmpty()) {
                queryBuilder.must(QueryBuilders.termQuery("chatId", chatId));
            }
            
            if (fromUserId != null && !fromUserId.trim().isEmpty()) {
                queryBuilder.must(QueryBuilders.termQuery("fromUserId", fromUserId));
            }
            
            if (toUserId != null && !toUserId.trim().isEmpty()) {
                queryBuilder.must(QueryBuilders.termQuery("toUserId", toUserId));
            }
            
            if (content != null && !content.trim().isEmpty()) {
                queryBuilder.must(QueryBuilders.matchQuery("msgContent", content));
            }
            
            if (msgStatus != null) {
                queryBuilder.must(QueryBuilders.termQuery("msgStatus", msgStatus));
            }
            
            if (startTime != null || endTime != null) {
                RangeQueryBuilder timeRangeQuery = QueryBuilders.rangeQuery("msgCreateTime");
                if (startTime != null) {
                    timeRangeQuery.gte(startTime);
                }
                if (endTime != null) {
                    timeRangeQuery.lte(endTime);
                }
                queryBuilder.must(timeRangeQuery);
            }
            
            NativeSearchQuery searchQuery = new NativeSearchQueryBuilder()
                    .withQuery(queryBuilder)
                    .withPageable(pageable)
                    .withSort(Sort.by(Sort.Direction.DESC, "msgCreateTime"))
                    .build();
            
            SearchHits<ImC2CMsgRecordES> searchHits = elasticsearchRestTemplate.search(searchQuery, ImC2CMsgRecordES.class);
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
        // 这里简化处理，实际项目中可能需要更复杂的转换逻辑
        java.util.List<ImC2CMsgRecordES> content = searchHits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .collect(java.util.stream.Collectors.toList());
        
        return new org.springframework.data.domain.PageImpl<>(content, pageable, searchHits.getTotalHits());
    }
} 