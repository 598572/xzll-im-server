package com.xzll.gateway.config.nacos;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @Author: hzz
 * @Date: 2024/6/11 09:49:20
 * @Description: 需要给响应体 添加im-server地址的接口，不多 目前只有login接口
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Component
@RefreshScope
@ConfigurationProperties(prefix="im.need-add-server")
public class NeedAddImServerUrlsConfig {

    private List<String> urls;

}
