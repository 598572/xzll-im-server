package com.xzll.connect.test.client111;



import java.net.URISyntaxException;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:29:25
 * @Description:
 */
public class Client_111 {

    public static void start(String[] args) {
        WebsocketClient111 websocketClient111 = new WebsocketClient111("127.0.0.1", 10001);
        try {
            websocketClient111.run();
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
