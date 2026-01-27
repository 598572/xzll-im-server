package com.xzll.console.config.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.xzll.console.config.nacos.ElasticSearchNacosConfig;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;

import jakarta.annotation.Resource;

/**
 * ES配置类（Spring Boot 3.x + Spring Data Elasticsearch 5.x）
 * 同时支持新版 ElasticsearchClient 和旧版 RestHighLevelClient
 *
 * - ElasticsearchClient: 用于现代化查询（推荐）
 * - RestHighLevelClient: 用于需要精细控制的批量操作
 * - ElasticsearchTemplate: 用于 Spring Data 风格的操作
 *
 * 条件加载：
 * - 只有在 im.elasticsearch.sync-enabled=true 时才会创建Bean
 * - 避免在不需要ES时创建ES连接
 *
 * @Author: hzz
 * @Date: 2024/12/20
 */
@Configuration
@ConditionalOnProperty(prefix = "im.elasticsearch", name = "sync-enabled", havingValue = "true")
public class ElasticSearchConfig {

    @Resource
    private ElasticSearchNacosConfig elasticSearchNacosConfig;

    /**
     * 创建 RestClient（低级别客户端）
     * 所有高级客户端的基础
     */
    @Bean("restClient")
    @Primary
    @DependsOn("elasticSearchNacosConfig")
    public RestClient restClient() {
        HttpHost[] hosts = elasticSearchNacosConfig.getUris().stream()
                .map(this::createHttpHost)
                .toArray(HttpHost[]::new);
        return RestClient.builder(hosts).build();
    }

    /**
     * 解析 URI 为 HttpHost
     */
    private HttpHost createHttpHost(String uri) {
        return HttpHost.create(uri);
    }

    /**
     * 创建 RestHighLevelClient（旧版高级客户端）
     * 用于需要精细控制的查询场景，如批量查询、scroll 查询等
     */
    @Bean("restHighLevelClient")
    @DependsOn("restClient")
    public RestHighLevelClient restHighLevelClient() {
        HttpHost[] hosts = elasticSearchNacosConfig.getUris().stream()
                .map(this::createHttpHost)
                .toArray(HttpHost[]::new);
        return new RestHighLevelClient(
                org.elasticsearch.client.RestClient.builder(hosts)
        );
    }

    /**
     * 创建 ElasticsearchTransport
     * 新版客户端的传输层
     */
    @Bean
    @DependsOn("restClient")
    public ElasticsearchTransport elasticsearchTransport() {
        return new RestClientTransport(restClient(), new JacksonJsonpMapper());
    }

    /**
     * 创建 ElasticsearchClient（新版ES Java客户端）
     * 推荐使用，类型安全，现代化API
     */
    @Bean
    @DependsOn("elasticsearchTransport")
    @Primary
    public ElasticsearchClient elasticsearchClient() {
        return new ElasticsearchClient(elasticsearchTransport());
    }

    /**
     * 创建 ElasticsearchTemplate（Spring Data Elasticsearch 5.x）
     * 用于 Spring Data 风格的 CRUD 操作
     */
    @Bean
    @DependsOn("elasticsearchClient")
    @Primary
    public ElasticsearchTemplate elasticsearchTemplate() {
        return new ElasticsearchTemplate(elasticsearchClient());
    }
}
