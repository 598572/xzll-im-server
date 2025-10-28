package com.xzll.client.protobuf.clien2;

import java.net.URISyntaxException;

/**
 * @Author: hzz
 * @Date: 2025/10/28
 * @Description: Protobuf 协议客户端测试入口（用户2）
 */
public class ProtobufClient_2 {

    public static final String LOCAL = "127.0.0.1";

    public static void start(String[] args) {
        ProtobufWebsocketClient2 websocketClient = new ProtobufWebsocketClient2(LOCAL, 10001);
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


