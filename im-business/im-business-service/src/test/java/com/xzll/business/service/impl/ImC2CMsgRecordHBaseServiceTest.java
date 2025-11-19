package com.xzll.business.service.impl;

import com.xzll.business.config.HBaseTableUtil;
import com.xzll.business.service.ImC2CMsgRecordHBaseService;
import com.xzll.common.constant.MsgFormatEnum;
import com.xzll.common.pojo.request.C2CSendMsgAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.UUID;

@SpringBootTest
public class ImC2CMsgRecordHBaseServiceTest {

    @Autowired
    private ImC2CMsgRecordHBaseService imC2CMsgRecordHBaseService;

    @Autowired
    private HBaseTableUtil hBaseTableUtil;

    @Test
    public void testTableCreation() {
        // 测试表是否能够正确创建
        boolean created = hBaseTableUtil.createImC2CMsgRecordTableIfNotExists();
        System.out.println("HBase表创建状态: " + (created ? "已创建" : "已存在"));
    }

    @Test
    public void testSaveAndQueryMsg() {
        // 创建测试数据
        C2CSendMsgAO sendMsgAO = new C2CSendMsgAO();
        sendMsgAO.setFromUserId("user1");
        sendMsgAO.setToUserId("user2");
        sendMsgAO.setMsgId(UUID.randomUUID().toString());
        sendMsgAO.setMsgFormat(MsgFormatEnum.TEXT_MSG.getCode());
        sendMsgAO.setMsgContent("测试消息内容");
        sendMsgAO.setMsgCreateTime(System.currentTimeMillis());
        sendMsgAO.setRetryMsgFlag(0);
        // 构造chatId：按照业务类型-会话类型-更小的userId-更大的userId格式
        sendMsgAO.setChatId("biz-type-1-user1-user2");

        // 保存消息
        boolean saved = imC2CMsgRecordHBaseService.saveC2CMsg(sendMsgAO);
        System.out.println("消息保存" + (saved ? "成功" : "失败") + ": " + sendMsgAO.getMsgId());

        // 这里可以添加查询消息的测试逻辑
        System.out.println("HBase消息存储测试完成");
    }
}