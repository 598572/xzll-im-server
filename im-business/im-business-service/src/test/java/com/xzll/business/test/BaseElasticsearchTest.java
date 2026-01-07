package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TypeMapping;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.elasticsearch.indices.DeleteIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: Elasticsearch 测试基类（Spring Boot 3.x + Elasticsearch Java Client）
 */
@Slf4j
@SpringBootTest
public abstract class BaseElasticsearchTest {

    @Resource
    protected ElasticsearchClient elasticsearchClient;

    // 测试索引前缀
    protected static final String TEST_INDEX_PREFIX = "test_";
    
    // 存储测试期间创建的索引，用于清理
    protected final Set<String> createdIndices = new HashSet<>();

    @BeforeEach
    public void setUp() throws Exception {
        log.info("开始 Elasticsearch 测试...");
        // 子类可以重写此方法进行特定设置
    }

    @AfterEach
    public void tearDown() throws Exception {
//        log.info("清理 Elasticsearch 测试数据...");
//        cleanupTestIndices();
//        log.info("Elasticsearch 测试清理完成");
    }

    /**
     * 创建测试索引
     */
    protected String createTestIndex(String indexName) throws IOException {
        String fullIndexName = TEST_INDEX_PREFIX + indexName + "_" + System.currentTimeMillis();
        
        // 创建索引映射JSON
        String mappingJson = createIndexMappingJson();
        
        CreateIndexResponse response = elasticsearchClient.indices().create(c -> c
                .index(fullIndexName)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )
                .mappings(TypeMapping.of(m -> m.withJson(new StringReader(mappingJson))))
        );
        
        if (response.acknowledged()) {
            createdIndices.add(fullIndexName);
            log.info("测试索引创建成功: {}", fullIndexName);
            return fullIndexName;
        } else {
            throw new RuntimeException("索引创建失败: " + fullIndexName);
        }
    }

    /**
     * 创建索引映射JSON
     */
    protected String createIndexMappingJson() {
        return """
            {
                "properties": {
                    "fromUserId": {"type": "keyword"},
                    "toUserId": {"type": "keyword"},
                    "msgId": {"type": "keyword"},
                    "chatId": {"type": "keyword"},
                    "rowkey": {"type": "keyword"},
                    "msgContent": {"type": "text", "analyzer": "standard"},
                    "msgFormat": {"type": "integer"},
                    "msgCreateTime": {"type": "long"},
                    "retryCount": {"type": "integer"},
                    "msgStatus": {"type": "integer"},
                    "withdrawFlag": {"type": "integer"},
                    "createTime": {"type": "date"},
                    "updateTime": {"type": "date"}
                }
            }
            """;
    }

    /**
     * 创建测试消息数据
     */
    protected ImC2CMsgRecordES createTestMessage(String chatId, String msgId) {
        ImC2CMsgRecordES message = new ImC2CMsgRecordES();
        message.setId(chatId + "_" + msgId);
        message.setFromUserId("test_user_" + RandomUtil.randomString(6));
        message.setToUserId("test_user_" + RandomUtil.randomString(6));
        message.setMsgId(msgId);
        message.setMsgFormat(RandomUtil.randomInt(1, 4));
        message.setMsgContent("测试消息内容 " + RandomUtil.randomString(10));
        message.setMsgCreateTime(System.currentTimeMillis());
        message.setRetryCount(0);
        message.setMsgStatus(RandomUtil.randomInt(1, 5));
        message.setWithdrawFlag(0);
        message.setChatId(chatId);
        message.setRowkey(chatId + "_" + msgId);
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        return message;
    }

    /**
     * 批量创建测试消息
     */
    protected List<ImC2CMsgRecordES> createBulkTestMessages(String chatId, int count) {
        List<ImC2CMsgRecordES> messages = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String msgId = "bulk_msg_" + String.format("%03d", i);
            messages.add(createTestMessage(chatId, msgId));
        }
        return messages;
    }

    /**
     * 索引单个文档
     */
    protected String indexDocument(String indexName, ImC2CMsgRecordES document) throws IOException {
        IndexResponse response = elasticsearchClient.index(i -> i
                .index(indexName)
                .id(document.getId())
                .document(document)
        );
        
        String result = response.result().name();
        if ("Created".equalsIgnoreCase(result) || "Updated".equalsIgnoreCase(result)) {
            log.debug("文档索引成功: ID={}, 结果={}", response.id(), result);
            return response.id();
        } else {
            throw new RuntimeException("文档索引失败: " + result);
        }
    }

    /**
     * 批量索引文档
     */
    protected BulkResponse bulkIndexDocuments(String indexName, List<ImC2CMsgRecordES> documents) throws IOException {
        BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
        
        for (ImC2CMsgRecordES document : documents) {
            bulkBuilder.operations(op -> op
                    .index(idx -> idx
                            .index(indexName)
                            .id(document.getId())
                            .document(document)
                    )
            );
        }

        BulkResponse bulkResponse = elasticsearchClient.bulk(bulkBuilder.build());
        
        if (bulkResponse.errors()) {
            log.error("批量索引存在失败项");
            bulkResponse.items().forEach(item -> {
                if (item.error() != null) {
                    log.error("失败项: {}, 原因: {}", item.id(), item.error().reason());
                }
            });
        } else {
            log.debug("批量索引成功，处理了 {} 个文档", bulkResponse.items().size());
        }
        
        return bulkResponse;
    }

    /**
     * 检查索引是否存在
     */
    protected boolean indexExists(String indexName) throws IOException {
        return elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
    }

    /**
     * 等待索引可用
     */
    protected void waitForIndexAvailable(String indexName, int maxWaitSeconds) throws InterruptedException {
        int waitCount = 0;
        while (waitCount < maxWaitSeconds) {
            try {
                if (indexExists(indexName)) {
                    // 额外等待让ES刷新
                    Thread.sleep(1000);
                    log.debug("索引 {} 已可用", indexName);
                    return;
                }
            } catch (IOException e) {
                log.debug("等待索引 {} 可用，第 {} 次检查", indexName, waitCount + 1);
            }
            
            Thread.sleep(1000);
            waitCount++;
        }
        
        log.warn("索引 {} 在 {} 秒内未变为可用状态", indexName, maxWaitSeconds);
    }

    /**
     * 清理测试索引
     */
    protected void cleanupTestIndices() {
        for (String indexName : createdIndices) {
            try {
                if (indexExists(indexName)) {
                    boolean acknowledged = elasticsearchClient.indices()
                            .delete(d -> d.index(indexName))
                            .acknowledged();
                    
                    if (acknowledged) {
                        log.debug("测试索引清理成功: {}", indexName);
                    } else {
                        log.warn("测试索引清理失败: {}", indexName);
                    }
                }
            } catch (IOException e) {
                log.error("清理测试索引时发生异常: {}", indexName, e);
            }
        }
        createdIndices.clear();
    }

    /**
     * 生成随机测试数据
     */
    protected String generateRandomChatId() {
        return "test_chat_" + RandomUtil.randomString(8);
    }

    protected String generateRandomMsgId() {
        return "test_msg_" + RandomUtil.randomString(8);
    }

    protected String generateRandomUserId() {
        return "test_user_" + RandomUtil.randomString(6);
    }

    /**
     * 验证测试环境
     */
    protected void validateTestEnvironment() {
        Assertions.assertNotNull(elasticsearchClient, "ES客户端不能为空");
        log.info("测试环境验证通过，ES客户端: {}", elasticsearchClient);
    }
}
