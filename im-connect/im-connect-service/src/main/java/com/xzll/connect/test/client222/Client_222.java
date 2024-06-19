package com.xzll.connect.test.client222;



import java.net.URISyntaxException;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:29:25
 * @Description:
 */
public class Client_222 {

    public static void start(String[] args) {
        WebsocketClient222 websocketClient222 = new WebsocketClient222("127.0.0.1", 10001);
        try {
            websocketClient222.run();
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
