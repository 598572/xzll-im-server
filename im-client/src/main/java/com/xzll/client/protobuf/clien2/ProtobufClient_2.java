package com.xzll.client.protobuf.clien2;

import java.net.URISyntaxException;

/**
 * @Author: hzz
 * @Date: 2025/10/28
 * @Description: Protobuf 协议客户端测试入口（用户2）
 */
public class ProtobufClient_2 {


    private static final String huawei = "120.46.85.43";
    private static final String XUNIJI = "172.30.128.65";
    private static final String XUNIJI_家 = "192.168.1.103";
    public static final String LOCAL = "127.0.0.1";
    public static final String GONGWANG = "1.92.82.32";

    public static void start(String[] args) {
//        ProtobufWebsocketClient2 websocketClient = new ProtobufWebsocketClient2(LOCAL, 10001);
        ProtobufWebsocketClient2 websocketClient = new ProtobufWebsocketClient2(huawei, 80);
        try {
            websocketClient.run();
        } catch (InterruptedException | URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        start(new String[0]);
    }
}


