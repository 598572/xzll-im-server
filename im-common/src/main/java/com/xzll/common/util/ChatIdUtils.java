package com.xzll.common.util;

import cn.hutool.core.lang.Assert;
import com.xzll.common.constant.ImConstant;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

/**
 * @Author: hzz
 * @Date: 2024/6/16 16:56:48
 * @Description: 会话id 生成工具类
 */
public class ChatIdUtils {


    public static String buildChatId(Integer bizType, String chatType, Long fromUserId, Long toUserId) {
        Assert.isTrue(StringUtils.isNotBlank(chatType) && Objects.nonNull(fromUserId) && Objects.nonNull(toUserId));
        bizType = bizType == null ? ImConstant.DEFAULT_BIZ_TYPE : bizType;
        return String.format("%d-%s-%s-%s", bizType, ImConstant.ChatType.CHAT_TYPE_MAP.get(chatType), fromUserId, toUserId);
    }

    public static String buildC2CChatId(Integer bizType, Long fromUserId, Long toUserId) {
        //单聊时 第一个userId是小的 第二个userId是较大的
        Long smallUserId = null;
        Long bigUserId = null;
        if (fromUserId < toUserId) {
            smallUserId = fromUserId;
            bigUserId = toUserId;
        } else {
            smallUserId = toUserId;
            bigUserId = fromUserId;
        }
        return buildChatId(bizType, ImConstant.ChatType.C2C, smallUserId, bigUserId);
    }

    public static String buildGroupChatId(Integer bizType, Long fromUserId) {
        return buildChatId(bizType, ImConstant.ChatType.GROUP, fromUserId, System.currentTimeMillis() / 1000);
    }

    public static void main(String[] args) {
        String c2cChatId = buildC2CChatId(200, 111L, 222L);
        System.out.println("单聊会话id:" + c2cChatId);

        String groupChatId = buildGroupChatId(200, 111L);
        System.out.println("群聊会话id（其实就是群id）:" + groupChatId);
    }
}
