package com.xzll.connect.test.client2.json;



import cn.hutool.json.JSONUtil;
import com.xzll.common.pojo.MsgBaseRequest;
import com.xzll.connect.pojo.dto.C2CMsgRequestDTO;
import com.xzll.connect.pojo.enums.MsgFormatEnum;
import com.xzll.connect.pojo.enums.MsgTypeEnum;

import java.util.UUID;

/**
 * @Author: hzz
 * @Date: 2022/2/24 15:11:50
 * @Description:
 */
public class MakeData4Client2 {

    public static void main(String[] args) {
        MsgBaseRequest<C2CMsgRequestDTO> msgBaseRequest = new MsgBaseRequest<>();

        MsgBaseRequest.MsgType msgType = new MsgBaseRequest.MsgType();
        msgType.setFirstLevelMsgType(MsgTypeEnum.FirstLevelMsgType.CHAT_MSG.getCode());
        msgType.setSecondLevelMsgType(MsgTypeEnum.SecondLevelMsgType.C2C.getCode());
        msgBaseRequest.setMsgType(msgType);

        C2CMsgRequestDTO c2CMsgRequestDTO = new C2CMsgRequestDTO();
        c2CMsgRequestDTO.setMsgId(UUID.randomUUID().toString());
        c2CMsgRequestDTO.setMsgContent("hello我是黄壮壮_sender_1002");
        c2CMsgRequestDTO.setSessionId("网约车业务线_9999");
        c2CMsgRequestDTO.setToUserId("1003");
        c2CMsgRequestDTO.setFromUserId("1002");
        c2CMsgRequestDTO.setMsgFormat(MsgFormatEnum.TEXT_MSG.getCode());
        c2CMsgRequestDTO.setMsgCreateTime(System.currentTimeMillis());

        msgBaseRequest.setBody(c2CMsgRequestDTO);
        String s = JSONUtil.toJsonStr(msgBaseRequest);
        System.out.println(s);
    }
}
