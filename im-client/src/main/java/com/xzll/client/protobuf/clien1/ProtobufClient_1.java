//package com.xzll.client.protobuf.clien1;
//
//import java.net.URISyntaxException;
//
///**
// * @Author: hzz
// * @Date: 2025/10/27
// * @Description: Protobuf 协议客户端测试入口
// */
//public class ProtobufClient_1 {
//
//    private static final String huawei = "120.46.85.43";
//    private static final String XUNIJI = "172.30.128.65";
//    private static final String XUNIJI_家 = "192.168.1.103";
//    public static final String LOCAL = "127.0.0.1";
//    public static final String GONGWANG = "1.92.82.32";
//
//    public static void start(String[] args) {
//        // 根据需要选择服务器地址
//        ProtobufWebsocketClient1 websocketClient = new ProtobufWebsocketClient1(LOCAL, 10001);
////        ProtobufWebsocketClient1 websocketClient = new ProtobufWebsocketClient1(huawei, 80);
//        try {
//            websocketClient.run();
//        } catch (InterruptedException e) {
//            e.printStackTrace();
//        } catch (URISyntaxException e) {
//            e.printStackTrace();
//        }
//    }
//
//    public static void main(String[] args) {
//        start(new String[0]);
//    }
//
//}
//
//
