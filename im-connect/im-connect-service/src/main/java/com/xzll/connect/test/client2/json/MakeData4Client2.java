package com.xzll.connect.test.client2.json;



import cn.hutool.json.JSONUtil;
import com.xzll.common.constant.MsgFormatEnum;
import com.xzll.common.constant.MsgTypeEnum;
import com.xzll.common.pojo.base.ImBaseRequest;
import com.xzll.common.pojo.request.C2CSendMsgAO;


import java.util.UUID;

/**
 * @Author: hzz
 * @Date: 2022/2/24 15:11:50
 * @Description:
 */
public class MakeData4Client2 {

    public static void main(String[] args) {
        ImBaseRequest<C2CSendMsgAO> imBaseRequest = new ImBaseRequest<>();

        ImBaseRequest.MsgType msgType = new ImBaseRequest.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.CHAT_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.C2C.getCode());
        imBaseRequest.setMsgType(msgType);

        C2CSendMsgAO c2CMsgRequestDTO = new C2CSendMsgAO();
        c2CMsgRequestDTO.setMsgId(UUID.randomUUID().toString());
        c2CMsgRequestDTO.setMsgContent("hello我是黄壮壮_sender_1002");
        c2CMsgRequestDTO.setChatId("网约车业务线_9999");
        c2CMsgRequestDTO.setToUserId("1003");
        c2CMsgRequestDTO.setFromUserId("1002");
        c2CMsgRequestDTO.setMsgFormat(MsgFormatEnum.TEXT_MSG.getCode());
        c2CMsgRequestDTO.setMsgCreateTime(System.currentTimeMillis());

        imBaseRequest.setBody(c2CMsgRequestDTO);
        String s = JSONUtil.toJsonStr(imBaseRequest);
        System.out.println(s);
    }
}
