package com.xzll.datasync.config.es;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.xzll.datasync.config.nacos.ElasticSearchNacosConfig;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;

import jakarta.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: ES配置类（Spring Boot 3.x + Spring Data Elasticsearch 5.x）
 * 使用新的 ElasticsearchClient 替代已废弃的 RestHighLevelClient
 */
@Configuration
public class ElasticSearchConfig {

    @Resource
    private ElasticSearchNacosConfig elasticSearchNacosConfig;

    /**
     * 创建 RestClient（低级别客户端）
     *
     * @return RestClient
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
     * 创建 HttpHost
     */
    private HttpHost createHttpHost(String uri) {
        return HttpHost.create(uri);
    }

    /**
     * 创建 ElasticsearchTransport
     *
     * @return ElasticsearchTransport
     */
    @Bean
    @DependsOn("restClient")
    public ElasticsearchTransport elasticsearchTransport() {
        return new RestClientTransport(restClient(), new JacksonJsonpMapper());
    }

    /**
     * 创建 ElasticsearchClient（新版ES Java客户端）
     *
     * @return ElasticsearchClient
     */
    @Bean
    @DependsOn("elasticsearchTransport")
    @Primary
    public ElasticsearchClient elasticsearchClient() {
        return new ElasticsearchClient(elasticsearchTransport());
    }

    /**
     * 创建 ElasticsearchTemplate（Spring Data Elasticsearch 5.x）
     * 替代原来的 ElasticsearchRestTemplate
     *
     * @return ElasticsearchTemplate
     */
    @Bean
    @DependsOn("elasticsearchClient")
    @Primary
    public ElasticsearchTemplate elasticsearchTemplate() {
        return new ElasticsearchTemplate(elasticsearchClient());
    }
}
