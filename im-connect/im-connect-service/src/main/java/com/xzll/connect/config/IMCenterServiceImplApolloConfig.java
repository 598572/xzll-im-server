package com.xzll.connect.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Setter
@Getter
@Component
public class IMCenterServiceImplApolloConfig {

    @Value("${im.server.port:8091}")
    private int imServerPort;

    @Value("${tomcat.httpPort:26020}")
    private int imHttpPort;

    @Value("${server.info.expire:604800}")
    private int serverExpire;

    @Value("${im.server.sobacklog:1024}")
    private int sobacklog;

    @Value("${socket.netty.debug:true}")
    private boolean debug;

    //心跳时间
    @Value("${im.heartbeat.time:10}")
    private long heartBeatTime ;

    //推送离线消息阈值
    @Value("${im.offlinemsg.pushthreshold:36000}")
    private long offlineMsgPushThreshold ;

}
