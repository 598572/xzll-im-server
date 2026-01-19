package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.GetResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: 单聊消息ES服务测试类（Spring Boot 3.x + Elasticsearch Java Client）
 */
@Slf4j
@DisplayName("单聊消息ES服务测试")
public class ImC2CMsgRecordESServiceTest extends BaseElasticsearchTest {

    private static final String TEST_CHAT_ID = "test_chat_001";
    private static final String TEST_MSG_ID = "test_msg_001";

    @Test
    @DisplayName("测试消息CRUD操作")
    public void testMessageCRUD() throws IOException, InterruptedException {
        // 1. 验证测试环境
        validateTestEnvironment();
        
        // 2. 创建测试索引
        String indexName = createTestIndex("c2c_msg_record");
        waitForIndexAvailable(indexName, 10);
        
        // 3. 创建测试消息
        ImC2CMsgRecordES testMessage = createTestMessage(TEST_CHAT_ID, TEST_MSG_ID);
        
        // 4. 索引消息
        String indexedId = indexDocument(indexName, testMessage);
        Assertions.assertEquals(testMessage.getId(), indexedId, "索引的文档ID应该匹配");
        
        // 5. 获取消息
        GetResponse<ImC2CMsgRecordES> getResponse = elasticsearchClient.get(g -> g
                .index(indexName)
                .id(indexedId),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertTrue(getResponse.found(), "文档应该存在");
        Assertions.assertEquals(testMessage.getFromUserId(), 
                getResponse.source().getFromUserId(), "发送者ID应该匹配");
        
        log.info("消息CRUD测试通过，索引: {}, 文档ID: {}", indexName, indexedId);
    }

    @Test
    @DisplayName("测试批量消息操作")
    public void testBulkMessageOperations() throws IOException, InterruptedException {
        // 1. 创建测试索引
        String indexName = createTestIndex("c2c_msg_bulk");
        waitForIndexAvailable(indexName, 10);
        
        // 2. 创建批量测试消息
        List<ImC2CMsgRecordES> messages = createBulkTestMessages(TEST_CHAT_ID, 10);
        
        // 3. 批量索引
        bulkIndexDocuments(indexName, messages);
        
        // 4. 刷新索引
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
        
        // 5. 验证批量索引结果
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q.matchAll(m -> m))
                .size(20),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertEquals(10, response.hits().total().value(), 
                "应该找到10个文档");
        
        log.info("批量消息操作测试通过，索引: {}, 文档数量: {}", indexName, 
                response.hits().total().value());
    }

    @Test
    @DisplayName("测试消息搜索 - 精确匹配")
    public void testMessageSearchExactMatch() throws IOException, InterruptedException {
        // 1. 准备测试数据
        String indexName = createTestIndex("c2c_msg_search");
        waitForIndexAvailable(indexName, 10);
        
        // 创建多个测试消息
        List<ImC2CMsgRecordES> messages = createBulkTestMessages(TEST_CHAT_ID, 5);
        bulkIndexDocuments(indexName, messages);
        
        // 刷新索引
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
        
        // 2. 精确匹配查询 - 按发送者ID
        String targetUserId = messages.get(0).getFromUserId();
        
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q.term(t -> t.field("fromUserId").value(targetUserId)))
                .size(10),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertTrue(response.hits().total().value() > 0, 
                "应该找到匹配的文档");
        
        log.info("精确匹配搜索测试通过，发送者ID: {}, 找到文档数: {}", targetUserId,
                response.hits().total().value());
    }

    @Test
    @DisplayName("测试消息搜索 - 全文搜索")
    public void testMessageSearchFullText() throws IOException, InterruptedException {
        // 1. 准备测试数据
        String indexName = createTestIndex("c2c_msg_fulltext");
        waitForIndexAvailable(indexName, 10);
        
        // 创建包含特定关键词的消息
        List<ImC2CMsgRecordES> messages = Arrays.asList(
                createTestMessageWithContent(TEST_CHAT_ID, "msg_001", "这是一条重要的测试消息"),
                createTestMessageWithContent(TEST_CHAT_ID, "msg_002", "这是另一条测试消息"),
                createTestMessageWithContent(TEST_CHAT_ID, "msg_003", "普通消息内容")
        );
        
        bulkIndexDocuments(indexName, messages);
        
        // 刷新索引
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
        
        // 2. 全文搜索查询
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q.match(m -> m.field("msgContent").query("重要")))
                .size(10),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertTrue(response.hits().total().value() > 0, 
                "应该找到包含关键词的文档");
        
        log.info("全文搜索测试通过，关键词: '重要', 找到文档数: {}", 
                response.hits().total().value());
    }

    @Test
    @DisplayName("测试消息搜索 - 复合查询")
    public void testMessageSearchComplexQuery() throws IOException, InterruptedException {
        // 1. 准备测试数据
        String indexName = createTestIndex("c2c_msg_complex");
        waitForIndexAvailable(indexName, 10);
        
        // 创建不同状态和格式的消息
        List<ImC2CMsgRecordES> messages = Arrays.asList(
                createTestMessageWithStatus(TEST_CHAT_ID, "msg_001", 1, 1), // 格式1，状态1
                createTestMessageWithStatus(TEST_CHAT_ID, "msg_002", 2, 1), // 格式2，状态1
                createTestMessageWithStatus(TEST_CHAT_ID, "msg_003", 1, 2), // 格式1，状态2
                createTestMessageWithStatus(TEST_CHAT_ID, "msg_004", 2, 2)  // 格式2，状态2
        );
        
        bulkIndexDocuments(indexName, messages);
        
        // 刷新索引
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
        
        // 2. 复合查询：格式=1 AND 状态=1
        Query query = BoolQuery.of(b -> b
                .must(m -> m.term(t -> t.field("msgFormat").value(1)))
                .must(m -> m.term(t -> t.field("msgStatus").value(1)))
        )._toQuery();

        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .query(query)
                .size(10),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertEquals(1, response.hits().total().value(), 
                "应该找到1个符合条件的文档");
        
        log.info("复合查询测试通过，条件: 格式=1 AND 状态=1, 找到文档数: {}", 
                response.hits().total().value());
    }

    @Test
    @DisplayName("测试消息搜索 - 分页和排序")
    public void testMessageSearchPaginationAndSort() throws IOException, InterruptedException {
        // 1. 准备测试数据
        String indexName = createTestIndex("c2c_msg_pagination");
        waitForIndexAvailable(indexName, 10);
        
        // 创建多个测试消息
        List<ImC2CMsgRecordES> messages = createBulkTestMessages(TEST_CHAT_ID, 15);
        bulkIndexDocuments(indexName, messages);
        
        // 刷新索引
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
        
        // 2. 分页查询，按时间倒序
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q.matchAll(m -> m))
                .from(0)
                .size(5)
                .sort(so -> so.field(f -> f.field("msgCreateTime").order(SortOrder.Desc))),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertEquals(15, response.hits().total().value(), 
                "总文档数应该是15");
        Assertions.assertEquals(5, response.hits().hits().size(), 
                "当前页应该显示5个文档");
        
        log.info("分页排序测试通过，总文档数: {}, 当前页文档数: {}", 
                response.hits().total().value(),
                response.hits().hits().size());
    }

    @Test
    @DisplayName("测试消息搜索 - 时间范围查询")
    public void testMessageSearchTimeRange() throws IOException, InterruptedException {
        // 1. 准备测试数据
        String indexName = createTestIndex("c2c_msg_timerange");
        waitForIndexAvailable(indexName, 10);
        
        // 创建不同时间的消息
        long currentTime = System.currentTimeMillis();
        List<ImC2CMsgRecordES> messages = Arrays.asList(
                createTestMessageWithTime(TEST_CHAT_ID, "msg_001", currentTime - 3600000),      // 1小时前
                createTestMessageWithTime(TEST_CHAT_ID, "msg_002", currentTime - 1800000),      // 30分钟前
                createTestMessageWithTime(TEST_CHAT_ID, "msg_003", currentTime - 600000),       // 10分钟前
                createTestMessageWithTime(TEST_CHAT_ID, "msg_004", currentTime)                 // 当前时间
        );
        
        bulkIndexDocuments(indexName, messages);
        
        // 刷新索引
        elasticsearchClient.indices().refresh(r -> r.index(indexName));
        
        // 2. 时间范围查询：最近30分钟
        long thirtyMinutesAgo = currentTime - 1800000;
        
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(indexName)
                .query(q -> q.range(r -> r
                        .field("msgCreateTime")
                        .gte(JsonData.of(thirtyMinutesAgo))))
                .size(10),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertEquals(3, response.hits().total().value(), 
                "应该找到3个最近30分钟内的文档");
        
        log.info("时间范围查询测试通过，时间范围: 最近30分钟, 找到文档数: {}", 
                response.hits().total().value());
    }

    // ==================== 辅助方法 ====================

    /**
     * 创建包含特定内容的消息
     */
    private ImC2CMsgRecordES createTestMessageWithContent(String chatId, String msgId, String content) {
        ImC2CMsgRecordES message = createTestMessage(chatId, msgId);
        message.setMsgContent(content);
        return message;
    }

    /**
     * 创建包含特定状态的消息
     */
    private ImC2CMsgRecordES createTestMessageWithStatus(String chatId, String msgId, int msgFormat, int msgStatus) {
        ImC2CMsgRecordES message = createTestMessage(chatId, msgId);
        message.setMsgFormat(msgFormat);
        message.setMsgStatus(msgStatus);
        return message;
    }

    /**
     * 创建包含特定时间的消息
     */
    private ImC2CMsgRecordES createTestMessageWithTime(String chatId, String msgId, long msgCreateTime) {
        ImC2CMsgRecordES message = createTestMessage(chatId, msgId);
        message.setMsgCreateTime(msgCreateTime);
        return message;
    }
}
