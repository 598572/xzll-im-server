package com.xzll.connect.test.client1.json;



import java.net.URISyntaxException;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:29:25
 * @Description:
 */
public class Starter {

    public static void start(String[] args) {
        WebsocketClient websocketClient = new WebsocketClient("127.0.0.1", 10001);
        try {
            websocketClient.run();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        start(new String[0]);
    }

}
