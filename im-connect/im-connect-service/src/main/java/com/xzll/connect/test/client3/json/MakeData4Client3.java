package com.xzll.connect.test.client3.json;


import cn.hutool.json.JSONUtil;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.connect.pojo.enums.MsgStatusEnum;
import com.xzll.connect.pojo.enums.MsgTypeEnum;
import com.xzll.connect.pojo.response.dto.ClientReceivedMsgAckDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2022/2/24 15:11:50
 * @Description:
 */
public class MakeData4Client3 {

    public static void main(String[] args) {
        MsgBaseRequest msgBaseRequest = new MsgBaseRequest();

        MsgBaseRequest.MsgType msgType = new MsgBaseRequest.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.ACK_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.READ.getCode());

        msgBaseRequest.setMsgType(msgType);

        ClientReceivedMsgAckDTO ack = new ClientReceivedMsgAckDTO();

        List<String> msgIds = new ArrayList<>();
        msgIds.add("c31f0183-1ec5-4cc0-8872-ba60af889ad2");
        ack.setMsgIds(msgIds);
        ack.setReadStatus(MsgStatusEnum.MsgReadStatus.READED.getCode());

        ack.setFromUserId("1003");
        ack.setSessionId("9999");
        ack.setToUserId("1002");

        msgBaseRequest.setBody(ack);

        String s = JSONUtil.toJsonStr(msgBaseRequest);
        System.out.println(s);

    }
}
