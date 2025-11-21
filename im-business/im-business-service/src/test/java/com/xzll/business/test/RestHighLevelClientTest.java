package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.common.constant.MsgFormatEnum;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.delete.DeleteResponse;
import org.elasticsearch.action.get.GetRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.action.index.IndexResponse;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.update.UpdateRequest;
import org.elasticsearch.action.update.UpdateResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.CreateIndexResponse;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.xcontent.XContentType;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.junit.jupiter.api.*;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: RestHighLevelClient 单元测试类
 */
@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestHighLevelClientTest {

    @Resource
    private RestHighLevelClient restHighLevelClient;

    // 测试索引配置
    private static final String TEST_INDEX_NAME = "test_c2c_msg_record";
    private static final String TEST_DOC_ID = "test_chat_001_test_msg_001";

    // 测试数据
    private static final ImC2CMsgRecordES testMessage = createTestMessage();

    /**
     * 创建测试消息数据
     */
    private static ImC2CMsgRecordES createTestMessage() {
        ImC2CMsgRecordES message = new ImC2CMsgRecordES();
        message.setId(TEST_DOC_ID);
        message.setFromUserId("user001");
        message.setToUserId("user002");
        message.setMsgId("test_msg_001");
        message.setMsgFormat(MsgFormatEnum.TEXT_MSG.getCode());
        message.setMsgContent("这是一条测试消息内容，用于测试ES功能");
        message.setMsgCreateTime(System.currentTimeMillis());
        message.setRetryCount(0);
        message.setMsgStatus(1);
        message.setWithdrawFlag(0);
        message.setChatId("test_chat_001");
        message.setRowkey("test_chat_001_test_msg_001");
        message.setCreateTime(LocalDateTime.now());
        message.setUpdateTime(LocalDateTime.now());
        return message;
    }

    @Test
    @Order(1)
    @DisplayName("测试ES连接")
    public void testESConnection() {
        Assertions.assertNotNull(restHighLevelClient, "ES客户端不能为空");
        log.info("ES客户端连接成功: {}", restHighLevelClient);
    }

    @Test
    @Order(2)
    @DisplayName("创建测试索引")
    public void testCreateIndex() throws IOException {
        // 检查索引是否已存在
        GetIndexRequest getIndexRequest = new GetIndexRequest(TEST_INDEX_NAME);
        boolean exists = restHighLevelClient.indices().exists(getIndexRequest, RequestOptions.DEFAULT);
        
        if (exists) {
            log.info("索引 {} 已存在，跳过创建", TEST_INDEX_NAME);
            return;
        }

        // 创建索引
        CreateIndexRequest createIndexRequest = new CreateIndexRequest(TEST_INDEX_NAME);
        createIndexRequest.settings(Settings.builder()
                .put("index.number_of_shards", 1)
                .put("index.number_of_replicas", 0)
        );

        CreateIndexResponse createIndexResponse = restHighLevelClient.indices()
                .create(createIndexRequest, RequestOptions.DEFAULT);
        
        Assertions.assertTrue(createIndexResponse.isAcknowledged(), "索引创建失败");
        log.info("测试索引 {} 创建成功", TEST_INDEX_NAME);
    }

    @Test
    @Order(3)
    @DisplayName("测试文档索引（创建）")
    public void testIndexDocument() throws IOException {
        IndexRequest indexRequest = new IndexRequest(TEST_INDEX_NAME)
                .id(TEST_DOC_ID)
                .source(JSONUtil.toJsonStr(testMessage), XContentType.JSON);

        IndexResponse indexResponse = restHighLevelClient.index(indexRequest, RequestOptions.DEFAULT);
        
        Assertions.assertEquals("created", indexResponse.getResult().name(), "文档创建失败");
        log.info("文档索引成功，ID: {}, 结果: {}", indexResponse.getId(), indexResponse.getResult());
    }

    @Test
    @Order(4)
    @DisplayName("测试文档获取")
    public void testGetDocument() throws IOException {
        GetRequest getRequest = new GetRequest(TEST_INDEX_NAME, TEST_DOC_ID);
        GetResponse getResponse = restHighLevelClient.get(getRequest, RequestOptions.DEFAULT);
        
        Assertions.assertTrue(getResponse.isExists(), "文档不存在");
        Assertions.assertEquals(TEST_DOC_ID, getResponse.getId(), "文档ID不匹配");
        
        String sourceAsString = getResponse.getSourceAsString();
        log.info("获取文档成功，内容: {}", sourceAsString);
        
        // 验证字段值
        Map<String, Object> source = getResponse.getSourceAsMap();
        Assertions.assertEquals("user001", source.get("fromUserId"), "发送者ID不匹配");
        Assertions.assertEquals("user002", source.get("toUserId"), "接收者ID不匹配");
        Assertions.assertEquals("这是一条测试消息内容，用于测试ES功能", source.get("msgContent"), "消息内容不匹配");
    }

    @Test
    @Order(5)
    @DisplayName("测试文档更新")
    public void testUpdateDocument() throws IOException {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("retryCount", 1);
        updateData.put("updateTime", LocalDateTime.now());
        
        UpdateRequest updateRequest = new UpdateRequest(TEST_INDEX_NAME, TEST_DOC_ID)
                .doc(updateData);

        UpdateResponse updateResponse = restHighLevelClient.update(updateRequest, RequestOptions.DEFAULT);
        
        Assertions.assertEquals("updated", updateResponse.getResult().name(), "文档更新失败");
        log.info("文档更新成功，ID: {}, 结果: {}", updateResponse.getId(), updateResponse.getResult());
        
        // 验证更新结果
        GetRequest getRequest = new GetRequest(TEST_INDEX_NAME, TEST_DOC_ID);
        GetResponse getResponse = restHighLevelClient.get(getRequest, RequestOptions.DEFAULT);
        Map<String, Object> source = getResponse.getSourceAsMap();
        Assertions.assertEquals(1, source.get("retryCount"), "重试次数更新失败");
    }

    @Test
    @Order(6)
    @DisplayName("测试搜索查询 - 精确匹配")
    public void testSearchExactMatch() throws IOException {
        SearchRequest searchRequest = new SearchRequest(TEST_INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        
        // 精确匹配查询
        searchSourceBuilder.query(QueryBuilders.termQuery("fromUserId", "user001"));
        searchSourceBuilder.size(10);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        
        Assertions.assertTrue(searchResponse.getHits().getTotalHits().value > 0, "未找到匹配的文档");
        log.info("精确匹配查询成功，找到 {} 个文档", searchResponse.getHits().getTotalHits().value);
        
        // 打印搜索结果
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            log.info("找到文档: ID={}, 分数={}", hit.getId(), hit.getScore());
        }
    }

    @Test
    @Order(7)
    @DisplayName("测试搜索查询 - 全文搜索")
    public void testSearchFullText() throws IOException {
        SearchRequest searchRequest = new SearchRequest(TEST_INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        
        // 全文搜索查询
        searchSourceBuilder.query(QueryBuilders.matchQuery("msgContent", "测试消息"));
        searchSourceBuilder.size(10);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        
        Assertions.assertTrue(searchResponse.getHits().getTotalHits().value > 0, "未找到匹配的文档");
        log.info("全文搜索查询成功，找到 {} 个文档", searchResponse.getHits().getTotalHits().value);
        
        // 打印搜索结果
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            log.info("找到文档: ID={}, 分数={}", hit.getId(), hit.getScore());
        }
    }

    @Test
    @Order(8)
    @DisplayName("测试搜索查询 - 复合查询")
    public void testSearchComplexQuery() throws IOException {
        SearchRequest searchRequest = new SearchRequest(TEST_INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        
        // 复合查询：Bool查询
        BoolQueryBuilder boolQuery = QueryBuilders.boolQuery()
                .must(QueryBuilders.termQuery("msgFormat", 1))  // 消息格式必须为1
                .must(QueryBuilders.termQuery("msgStatus", 1))  // 消息状态必须为1
                .should(QueryBuilders.matchQuery("msgContent", "测试"));  // 内容包含"测试"
        
        searchSourceBuilder.query(boolQuery);
        searchSourceBuilder.size(10);
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        
        log.info("复合查询成功，找到 {} 个文档", searchResponse.getHits().getTotalHits().value);
        
        // 打印搜索结果
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            log.info("找到文档: ID={}, 分数={}", hit.getId(), hit.getScore());
        }
    }

    @Test
    @Order(9)
    @DisplayName("测试搜索查询 - 分页和排序")
    public void testSearchPaginationAndSort() throws IOException {
        SearchRequest searchRequest = new SearchRequest(TEST_INDEX_NAME);
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        
        // 基础查询
        searchSourceBuilder.query(QueryBuilders.matchAllQuery());
        
        // 分页
        searchSourceBuilder.from(0);
        searchSourceBuilder.size(5);
        
        // 排序
        searchSourceBuilder.sort("msgCreateTime", SortOrder.DESC);
        
        searchRequest.source(searchSourceBuilder);

        SearchResponse searchResponse = restHighLevelClient.search(searchRequest, RequestOptions.DEFAULT);
        
        log.info("分页排序查询成功，找到 {} 个文档，当前页显示 {} 个", 
                searchResponse.getHits().getTotalHits().value, 
                searchResponse.getHits().getHits().length);
        
        // 打印搜索结果
        for (SearchHit hit : searchResponse.getHits().getHits()) {
            log.info("文档: ID={}, 分数={}", hit.getId(), hit.getScore());
        }
    }

    @Test
    @Order(10)
    @DisplayName("测试文档删除")
    public void testDeleteDocument() throws IOException {
        DeleteRequest deleteRequest = new DeleteRequest(TEST_INDEX_NAME, TEST_DOC_ID);
        DeleteResponse deleteResponse = restHighLevelClient.delete(deleteRequest, RequestOptions.DEFAULT);
        
        Assertions.assertEquals("deleted", deleteResponse.getResult().name(), "文档删除失败");
        log.info("文档删除成功，ID: {}, 结果: {}", deleteResponse.getId(), deleteResponse.getResult());
        
        // 验证文档已被删除
        GetRequest getRequest = new GetRequest(TEST_INDEX_NAME, TEST_DOC_ID);
        GetResponse getResponse = restHighLevelClient.get(getRequest, RequestOptions.DEFAULT);
        Assertions.assertFalse(getResponse.isExists(), "文档应该已被删除");
    }

    @Test
    @Order(11)
    @DisplayName("清理测试索引")
    public void testCleanupIndex() throws IOException {
        DeleteIndexRequest deleteIndexRequest = new DeleteIndexRequest(TEST_INDEX_NAME);
        boolean acknowledged = restHighLevelClient.indices()
                .delete(deleteIndexRequest, RequestOptions.DEFAULT)
                .isAcknowledged();
        
        Assertions.assertTrue(acknowledged, "索引删除失败");
        log.info("测试索引 {} 清理成功", TEST_INDEX_NAME);
    }

    /**
     * 测试异常处理
     */
    @Test
    @DisplayName("测试异常处理 - 索引不存在的文档")
    public void testExceptionHandling() {
        Assertions.assertThrows(IOException.class, () -> {
            GetRequest getRequest = new GetRequest("non_existent_index", "non_existent_id");
            restHighLevelClient.get(getRequest, RequestOptions.DEFAULT);
        }, "应该抛出IOException异常");
        
        log.info("异常处理测试通过");
    }
} 