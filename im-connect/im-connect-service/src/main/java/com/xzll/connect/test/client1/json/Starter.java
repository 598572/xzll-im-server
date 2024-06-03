package com.xzll.connect.test.client1.json;



import java.net.URISyntaxException;

/**
 * @Author: hzz
 * @Date: 2022/1/14 10:29:25
 * @Description:
 */
public class Starter {

    public static void start(String[] args) {
        WebsocketClient websocketClient = new WebsocketClient("127.0.0.1", 16059);
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

//        String requestUrl = MessageFormat.format("http://{0}:{1}/msg/receiveAndSendMsg", "127.0.0.1", "26017");
//        ReqServerInnerP2PMsgDTO mrd = new ReqServerInnerP2PMsgDTO();
//
//        mrd.setId(1L);
//        mrd.setCreateTime(System.currentTimeMillis());
//        mrd.setType(2);
//        mrd.setFromId(100L);
//        mrd.setToId(101L);
//        mrd.setMsgContent("我去无情!!!!!!!!");
//        String reqMsg = JsonUtil.toJson(mrd);
//
//        String response = HttpComponet.doPost(requestUrl, reqMsg);
//        System.out.println("response:" + response);
    }

}
