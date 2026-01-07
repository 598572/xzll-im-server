package com.xzll.business.test;

import cn.hutool.core.util.RandomUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import com.xzll.business.entity.es.ImC2CMsgRecordES;
import com.xzll.common.constant.MsgFormatEnum;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: Elasticsearch Client 单元测试类（Spring Boot 3.x + Elasticsearch Java Client）
 */
@Slf4j
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RestHighLevelClientTest {

    @Resource
    private ElasticsearchClient elasticsearchClient;

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
        Assertions.assertNotNull(elasticsearchClient, "ES客户端不能为空");
        log.info("ES客户端连接成功: {}", elasticsearchClient);
    }

    @Test
    @Order(2)
    @DisplayName("创建测试索引")
    public void testCreateIndex() throws IOException {
        // 检查索引是否已存在
        boolean exists = elasticsearchClient.indices().exists(e -> e.index(TEST_INDEX_NAME)).value();
        
        if (exists) {
            log.info("索引 {} 已存在，跳过创建", TEST_INDEX_NAME);
            return;
        }

        // 创建索引映射
        String mappingJson = """
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

        // 创建索引
        CreateIndexResponse response = elasticsearchClient.indices().create(c -> c
                .index(TEST_INDEX_NAME)
                .settings(s -> s
                        .numberOfShards("1")
                        .numberOfReplicas("0")
                )
                .mappings(m -> m.withJson(new StringReader(mappingJson)))
        );
        
        Assertions.assertTrue(response.acknowledged(), "索引创建失败");
        log.info("测试索引 {} 创建成功", TEST_INDEX_NAME);
    }

    @Test
    @Order(3)
    @DisplayName("测试文档索引（创建）")
    public void testIndexDocument() throws IOException {
        IndexResponse response = elasticsearchClient.index(i -> i
                .index(TEST_INDEX_NAME)
                .id(TEST_DOC_ID)
                .document(testMessage)
        );
        
        Assertions.assertEquals("Created", response.result().name(), "文档创建失败");
        log.info("文档索引成功，ID: {}, 结果: {}", response.id(), response.result());
    }

    @Test
    @Order(4)
    @DisplayName("测试文档获取")
    public void testGetDocument() throws IOException {
        GetResponse<ImC2CMsgRecordES> response = elasticsearchClient.get(g -> g
                .index(TEST_INDEX_NAME)
                .id(TEST_DOC_ID),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertTrue(response.found(), "文档不存在");
        Assertions.assertEquals(TEST_DOC_ID, response.id(), "文档ID不匹配");
        
        ImC2CMsgRecordES source = response.source();
        Assertions.assertNotNull(source, "文档内容不能为空");
        log.info("获取文档成功，内容: {}", source);
        
        // 验证字段值
        Assertions.assertEquals("user001", source.getFromUserId(), "发送者ID不匹配");
        Assertions.assertEquals("user002", source.getToUserId(), "接收者ID不匹配");
        Assertions.assertEquals("这是一条测试消息内容，用于测试ES功能", source.getMsgContent(), "消息内容不匹配");
    }

    @Test
    @Order(5)
    @DisplayName("测试文档更新")
    public void testUpdateDocument() throws IOException {
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("retryCount", 1);
        updateData.put("updateTime", LocalDateTime.now().toString());
        
        UpdateResponse<ImC2CMsgRecordES> response = elasticsearchClient.update(u -> u
                .index(TEST_INDEX_NAME)
                .id(TEST_DOC_ID)
                .doc(updateData),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertEquals("Updated", response.result().name(), "文档更新失败");
        log.info("文档更新成功，ID: {}, 结果: {}", response.id(), response.result());
        
        // 验证更新结果
        GetResponse<ImC2CMsgRecordES> getResponse = elasticsearchClient.get(g -> g
                .index(TEST_INDEX_NAME)
                .id(TEST_DOC_ID),
                ImC2CMsgRecordES.class
        );
        Assertions.assertEquals(1, getResponse.source().getRetryCount(), "重试次数更新失败");
    }

    @Test
    @Order(6)
    @DisplayName("测试搜索查询 - 精确匹配")
    public void testSearchExactMatch() throws IOException {
        // 先刷新索引确保数据可搜索
        elasticsearchClient.indices().refresh(r -> r.index(TEST_INDEX_NAME));
        
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(TEST_INDEX_NAME)
                .query(q -> q.term(t -> t.field("fromUserId").value("user001")))
                .size(10),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertTrue(response.hits().total().value() > 0, "未找到匹配的文档");
        log.info("精确匹配查询成功，找到 {} 个文档", response.hits().total().value());
        
        // 打印搜索结果
        for (Hit<ImC2CMsgRecordES> hit : response.hits().hits()) {
            log.info("找到文档: ID={}, 分数={}", hit.id(), hit.score());
        }
    }

    @Test
    @Order(7)
    @DisplayName("测试搜索查询 - 全文搜索")
    public void testSearchFullText() throws IOException {
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(TEST_INDEX_NAME)
                .query(q -> q.match(m -> m.field("msgContent").query("测试消息")))
                .size(10),
                ImC2CMsgRecordES.class
        );
        
        Assertions.assertTrue(response.hits().total().value() > 0, "未找到匹配的文档");
        log.info("全文搜索查询成功，找到 {} 个文档", response.hits().total().value());
        
        // 打印搜索结果
        for (Hit<ImC2CMsgRecordES> hit : response.hits().hits()) {
            log.info("找到文档: ID={}, 分数={}", hit.id(), hit.score());
        }
    }

    @Test
    @Order(8)
    @DisplayName("测试搜索查询 - 复合查询")
    public void testSearchComplexQuery() throws IOException {
        // 复合查询：Bool查询
        Query query = BoolQuery.of(b -> b
                .must(m -> m.term(t -> t.field("msgFormat").value(1)))
                .must(m -> m.term(t -> t.field("msgStatus").value(1)))
                .should(sh -> sh.match(mt -> mt.field("msgContent").query("测试")))
        )._toQuery();

        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(TEST_INDEX_NAME)
                .query(query)
                .size(10),
                ImC2CMsgRecordES.class
        );
        
        log.info("复合查询成功，找到 {} 个文档", response.hits().total().value());
        
        // 打印搜索结果
        for (Hit<ImC2CMsgRecordES> hit : response.hits().hits()) {
            log.info("找到文档: ID={}, 分数={}", hit.id(), hit.score());
        }
    }

    @Test
    @Order(9)
    @DisplayName("测试搜索查询 - 分页和排序")
    public void testSearchPaginationAndSort() throws IOException {
        SearchResponse<ImC2CMsgRecordES> response = elasticsearchClient.search(s -> s
                .index(TEST_INDEX_NAME)
                .query(q -> q.matchAll(m -> m))
                .from(0)
                .size(5)
                .sort(so -> so.field(f -> f.field("msgCreateTime").order(SortOrder.Desc))),
                ImC2CMsgRecordES.class
        );
        
        log.info("分页排序查询成功，找到 {} 个文档，当前页显示 {} 个", 
                response.hits().total().value(), 
                response.hits().hits().size());
        
        // 打印搜索结果
        for (Hit<ImC2CMsgRecordES> hit : response.hits().hits()) {
            log.info("文档: ID={}, 分数={}", hit.id(), hit.score());
        }
    }

    @Test
    @Order(10)
    @DisplayName("测试文档删除")
    public void testDeleteDocument() throws IOException {
        DeleteResponse response = elasticsearchClient.delete(d -> d
                .index(TEST_INDEX_NAME)
                .id(TEST_DOC_ID)
        );
        
        Assertions.assertEquals("Deleted", response.result().name(), "文档删除失败");
        log.info("文档删除成功，ID: {}, 结果: {}", response.id(), response.result());
        
        // 验证文档已被删除
        GetResponse<ImC2CMsgRecordES> getResponse = elasticsearchClient.get(g -> g
                .index(TEST_INDEX_NAME)
                .id(TEST_DOC_ID),
                ImC2CMsgRecordES.class
        );
        Assertions.assertFalse(getResponse.found(), "文档应该已被删除");
    }

    @Test
    @Order(11)
    @DisplayName("清理测试索引")
    public void testCleanupIndex() throws IOException {
        boolean acknowledged = elasticsearchClient.indices()
                .delete(d -> d.index(TEST_INDEX_NAME))
                .acknowledged();
        
        Assertions.assertTrue(acknowledged, "索引删除失败");
        log.info("测试索引 {} 清理成功", TEST_INDEX_NAME);
    }

    /**
     * 测试异常处理
     */
    @Test
    @DisplayName("测试异常处理 - 索引不存在的文档")
    public void testExceptionHandling() {
        Assertions.assertThrows(Exception.class, () -> {
            elasticsearchClient.get(g -> g
                    .index("non_existent_index")
                    .id("non_existent_id"),
                    ImC2CMsgRecordES.class
            );
        }, "应该抛出异常");
        
        log.info("异常处理测试通过");
    }
}
