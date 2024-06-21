package com.xzll.connect.test.client3.json;


import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.constant.MsgStatusEnum;

import com.xzll.common.pojo.request.C2CReceivedMsgAckAO;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2022/2/24 15:11:50
 * @Description:
 */
public class MakeData4Client3 {

    public static void main(String[] args) {
        ImBaseRequest imBaseRequest = new ImBaseRequest();

        ImBaseRequest.MsgType msgType = new ImBaseRequest.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.READ.getCode());

        imBaseRequest.setMsgType(msgType);

        C2CReceivedMsgAckAO ack = new C2CReceivedMsgAckAO();

        List<String> msgIds = new ArrayList<>();
        msgIds.add("c31f0183-1ec5-4cc0-8872-ba60af889ad2");
        ack.setMsgIds(msgIds);
        ack.setMsgStatus(MsgStatusEnum.MsgStatus.READED.getCode());

        ack.setFromUserId("1003");
        ack.setChatId("9999");
        ack.setToUserId("1002");

        imBaseRequest.setBody(ack);

        String s = JSONUtil.toJsonStr(imBaseRequest);
        System.out.println(s);

    }
}
