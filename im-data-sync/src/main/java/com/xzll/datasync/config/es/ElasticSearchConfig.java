package com.xzll.datasync.config.es;

import com.xzll.datasync.config.nacos.ElasticSearchNacosConfig;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;

import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/12/20
 * @Description: ES配置类，使用 RestHighLevelClient 而不是 Spring 自带的客户端
 * 本项目使用 ES 版本 7.17.5
 */
@Configuration
public class ElasticSearchConfig {

    @Resource
    private ElasticSearchNacosConfig elasticSearchNacosConfig;

    /**
     * ES 集群配置 - 创建 RestHighLevelClient
     *
     * @return RestHighLevelClient
     */
    @Bean("restHighLevelClient")
    @Primary
    @DependsOn("elasticSearchNacosConfig")
    public RestHighLevelClient client() {
        HttpHost[] hosts = elasticSearchNacosConfig.getUris().stream()
                .map(this::createHttpHost)
                .toArray(HttpHost[]::new);
        return new RestHighLevelClient(RestClient.builder(hosts));
    }

    /**
     * 创建 HttpHost
     */
    private HttpHost createHttpHost(String uri) {
        return HttpHost.create(uri);
    }

    /**
     * 创建 ElasticsearchRestTemplate
     * 虽然 RestHighLevelClient 被标记为过时，但为了稳定可靠仍然使用它
     * 待到有新的被广泛验证的客户端出现时再修改
     *
     * @return ElasticsearchRestTemplate
     */
    @Bean
    @DependsOn(value = "restHighLevelClient")
    @Primary
    public ElasticsearchRestTemplate elasticsearchRestTemplate() {
        return new ElasticsearchRestTemplate(client());
    }

} 