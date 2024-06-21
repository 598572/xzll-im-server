package com.xzll.connect.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;


/**
 * @Author: hzz
 * @Date: 2024/5/30 13:27:55
 * @Description:
 */
@Slf4j
@Getter
@Setter
@RefreshScope
@ConfigurationProperties(prefix = "im.msg")
public class ImMsgConfig {

    /**
     * 单聊消息配置
     */
    private C2CMsgConfig c2cMsgConfig;
    /**
     * 群聊消息相关配置
     */
    private GroupMsgConfig groupMsgConfig;


    @Setter
    @Getter
    public static class C2CMsgConfig {
        // 用户上线后 推送的离线消息数量
        private Integer pushOfflineMsgCount;
    }

    @Setter
    @Getter
    public static class GroupMsgConfig {
        //群聊人数限制
        private Integer groupMaxUserCount;
    }


}
