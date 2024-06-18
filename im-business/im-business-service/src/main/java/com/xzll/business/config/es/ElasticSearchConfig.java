package com.xzll.business.config.es;


import com.xzll.business.config.nacos.ElasticSearchNacosConfig;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;


import javax.annotation.Resource;

/**
 * @Author: hzz
 * @Date: 2024/6/16 14:08:54
 * @Description: es配置 本项目使用 es版本 7.17.5
 */
@Configuration
public class ElasticSearchConfig {

    @Resource
    private ElasticSearchNacosConfig elasticSearchNacosConfig;


    /**
     * es 集群配置
     *
     * @return
     */
    @Bean
    public RestHighLevelClient client() {
        HttpHost[] hosts = elasticSearchNacosConfig.getUris().stream()
                .map(this::createHttpHost)
                .toArray(HttpHost[]::new);
        return new RestHighLevelClient(RestClient.builder(hosts));
    }

    private HttpHost createHttpHost(String uri) {
        return HttpHost.create(uri);
    }

    /**
     * 虽 RestHighLevelClient被标记为过时， 但是为了稳定可靠 这里任然使用他，待到有新的 被广泛验证的客户端出现时 再修改。
     *
     * @return
     */
    @Bean
    public ElasticsearchRestTemplate elasticsearchRestTemplate() {
        return new ElasticsearchRestTemplate(client());
    }

}
