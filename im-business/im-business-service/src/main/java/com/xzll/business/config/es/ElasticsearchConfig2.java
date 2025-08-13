//package com.xzll.business.config.es;
//
//import org.apache.http.HttpHost;
//import org.apache.http.client.CredentialsProvider;
//import org.apache.http.impl.client.BasicCredentialsProvider;
//import org.elasticsearch.client.RestClient;
//import org.elasticsearch.client.RestClientBuilder;
//import org.elasticsearch.client.RestHighLevelClient;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.data.elasticsearch.config.AbstractElasticsearchConfiguration;
//
///**
// * @Author: hzz
// * @Date: 2025/8/12 14:20:52
// * @Description:
// */
//@Configuration
//public class ElasticsearchConfig2 extends AbstractElasticsearchConfiguration {
//
//
//
//    @Value("${im.elasticsearch.uris}")
//    private String[] esUris;
//
////    @Value("${spring.elasticsearch.rest.username}")
////    private String username;
////
////    @Value("${spring.elasticsearch.rest.password}")
////    private String password;
//
//    @Override
//    @Bean
//    public RestHighLevelClient elasticsearchClient() {
//        // 解析 URI 列表
//        HttpHost[] hosts = new HttpHost[esUris.length];
//        for (int i = 0; i < esUris.length; i++) {
//            String uri = esUris[i];
//            java.net.URI parsedUri = java.net.URI.create(uri);
//            hosts[i] = new HttpHost(
//                    parsedUri.getHost(),
//                    parsedUri.getPort(),
//                    parsedUri.getScheme()
//            );
//        }
//
//        // 创建低级 REST 客户端
//        RestClientBuilder restClientBuilder = RestClient.builder(hosts);
//
//        // 添加基本认证
//        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
////        credentialsProvider.setCredentials(AuthScope.ANY,
////                new UsernamePasswordCredentials(username, password));
//
//        restClientBuilder.setHttpClientConfigCallback(httpClientBuilder ->
//                httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider)
//        );
//
//        // 配置超时
//        restClientBuilder.setRequestConfigCallback(requestConfigBuilder ->
//                requestConfigBuilder
//                        .setConnectTimeout(5000)
//                        .setSocketTimeout(60000)
//        );
//
//        // 创建高级客户端
//        return new RestHighLevelClient(restClientBuilder);
//    }
//
//}
