package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.XContentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import org.junit.jupiter.api.Assertions;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: Elasticsearch 测试基类
 */
@Slf4j
@SpringBootTest
public abstract class BaseElasticsearchTest {

    @Resource
    protected RestHighLevelClient restHighLevelClient;

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
        
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(fullIndexName);
        createIndexRequest.settings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.number_of_replicas", 0)
        );

        // 添加映射
        Map<String, Object> properties = createIndexMapping();
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("properties", properties);
        createIndexRequest.mapping(mapping);

        CreateIndexResponse createIndexResponse = restHighLevelClient.indices()
                .create(createIndexRequest, RequestOptions.DEFAULT);
        
        if (createIndexResponse.isAcknowledged()) {
            createdIndices.add(fullIndexName);
            log.info("测试索引创建成功: {}", fullIndexName);
            return fullIndexName;
        } else {
            throw new RuntimeException("索引创建失败: " + fullIndexName);
        }
    }

    /**
     * 创建索引映射
     */
    protected Map<String, Object> createIndexMapping() {
        Map<String, Object> properties = new HashMap<>();
        
        // 字符串字段 - Keyword类型
        properties.put("fromUserId", createFieldMapping("keyword"));
        properties.put("toUserId", createFieldMapping("keyword"));
        properties.put("msgId", createFieldMapping("keyword"));
        properties.put("chatId", createFieldMapping("keyword"));
        properties.put("rowkey", createFieldMapping("keyword"));
        
        // 消息内容字段 - Text类型，支持全文搜索
        Map<String, Object> msgContentMapping = new HashMap<>();
        msgContentMapping.put("type", "text");
        msgContentMapping.put("analyzer", "standard"); // 使用标准分词器，避免IK分词器依赖
        properties.put("msgContent", msgContentMapping);
        
        // 数值字段
        properties.put("msgFormat", createFieldMapping("integer"));
        properties.put("msgCreateTime", createFieldMapping("long"));
        properties.put("retryCount", createFieldMapping("integer"));
        properties.put("msgStatus", createFieldMapping("integer"));
        properties.put("withdrawFlag", createFieldMapping("integer"));
        
        // 时间字段
        properties.put("createTime", createFieldMapping("date"));
        properties.put("updateTime", createFieldMapping("date"));
        
        return properties;
    }

    /**
     * 创建字段映射
     */
    private Map<String, Object> createFieldMapping(String type) {
        Map<String, Object> mapping = new HashMap<>();
        mapping.put("type", type);
        return mapping;
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
        IndexRequest indexRequest = new IndexRequest(indexName)
                .id(document.getId())
                .source(JSONUtil.toJsonStr(document), XContentType.JSON);

        IndexResponse indexResponse = restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        
        if (indexResponse.getResult().name().equals("created") || 
            indexResponse.getResult().name().equals("updated")) {
            log.debug("文档索引成功: ID={}, 结果={}", indexResponse.getId(), indexResponse.getResult());
            return indexResponse.getId();
        } else {
            throw new RuntimeException("文档索引失败: " + indexResponse.getResult());
        }
    }

    /**
     * 批量索引文档
     */
    protected BulkResponse bulkIndexDocuments(String indexName, List<ImC2CMsgRecordES> documents) throws IOException {
        BulkRequest bulkRequest = new BulkRequest();
        
        for (ImC2CMsgRecordES document : documents) {
            IndexRequest indexRequest = new IndexRequest(indexName)
                    .id(document.getId())
                    .source(JSONUtil.toJsonStr(document), XContentType.JSON);
            bulkRequest.add(indexRequest);
        }

        BulkResponse bulkResponse = restHighLevelClient.bulk(bulkRequest, RequestOptions.DEFAULT);
        
        if (bulkResponse.hasFailures()) {
            log.error("批量索引存在失败项: {}", bulkResponse.buildFailureMessage());
        } else {
            log.debug("批量索引成功，处理了 {} 个文档", bulkResponse.getItems().length);
        }
        
        return bulkResponse;
    }

    /**
     * 检查索引是否存在
     */
    protected boolean indexExists(String indexName) throws IOException {
        GetIndexRequest getIndexRequest = new GetIndexRequest(indexName);
        return restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
    }

    /**
     * 等待索引可用
     */
    protected void waitForIndexAvailable(String indexName, int maxWaitSeconds) throws InterruptedException {
        int waitCount = 0;
        while (waitCount < maxWaitSeconds) {
            try {
                if (indexExists(indexName)) {
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
                    DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(indexName);
                    boolean acknowledged = restHighLevelClient.indices()
                            .delete(deleteIndexRequest, RequestOptions.DEFAULT)
                            .isAcknowledged();
                    
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
        Assertions.assertNotNull(restHighLevelClient, "ES客户端不能为空");
        log.info("测试环境验证通过，ES客户端: {}", restHighLevelClient);
    }
} 