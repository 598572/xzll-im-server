package com.xzll.common.util;

import cn.hutool.core.lang.Assert;
import com.xzll.common.constant.ImConstant;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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

    /**
     * 解析chatId获取详细信息
     * 
     * @param chatId 会话ID，格式如：100-1-123-456
     * @return ChatIdInfo对象，包含解析后的信息
     * @throws IllegalArgumentException 当chatId格式无效时
     */
    public static ChatIdInfo parseChatId(String chatId) {
        if (StringUtils.isBlank(chatId)) {
            throw new IllegalArgumentException("chatId不能为空");
        }
        
        String[] parts = chatId.split("-");
        if (parts.length != 4) {
            throw new IllegalArgumentException("无效的chatId格式，期望格式：bizType-chatType-userId1-userId2，实际：" + chatId);
        }
        
        try {
            Integer bizType = Integer.parseInt(parts[0]);
            String chatType = parts[1];
            Long userId1 = Long.parseLong(parts[2]);
            Long userId2 = Long.parseLong(parts[3]);
            
            return new ChatIdInfo(bizType, chatType, userId1, userId2, chatId);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("chatId中包含无效的数字格式：" + chatId, e);
        }
    }

    /**
     * 获取会话的参与用户列表
     * 
     * @param chatId 会话ID
     * @return 参与用户ID列表
     */
    public static List<String> getParticipantUserIds(String chatId) {
        try {
            ChatIdInfo chatIdInfo = parseChatId(chatId);
            return Arrays.asList(
                String.valueOf(chatIdInfo.getUserId1()),
                String.valueOf(chatIdInfo.getUserId2())
            );
        } catch (Exception e) {
            // 如果解析失败，返回空列表
            return Collections.emptyList();
        }
    }

    /**
     * 检查用户是否有权访问指定会话
     * 
     * @param userId 用户ID
     * @param chatId 会话ID
     * @return 是否有权访问
     */
    public static boolean isUserAuthorizedForChat(String userId, String chatId) {
        if (StringUtils.isBlank(userId) || StringUtils.isBlank(chatId)) {
            return false;
        }
        
        try {
            List<String> participants = getParticipantUserIds(chatId);
            return participants.contains(userId);
        } catch (Exception e) {
            // 解析失败时返回false
            return false;
        }
    }

    /**
     * 检查chatId是否为单聊
     * 
     * @param chatId 会话ID
     * @return 是否为单聊
     */
    public static boolean isC2CChat(String chatId) {
        try {
            ChatIdInfo chatIdInfo = parseChatId(chatId);
            return ImConstant.ChatType.C2C.equals(chatIdInfo.getChatType());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查chatId是否为群聊
     * 
     * @param chatId 会话ID
     * @return 是否为群聊
     */
    public static boolean isGroupChat(String chatId) {
        try {
            ChatIdInfo chatIdInfo = parseChatId(chatId);
            return ImConstant.ChatType.GROUP.equals(chatIdInfo.getChatType());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * ChatId信息封装类
     */
    public static class ChatIdInfo {
        private final Integer bizType;
        private final String chatType;
        private final Long userId1;
        private final Long userId2;
        private final String originalChatId;

        public ChatIdInfo(Integer bizType, String chatType, Long userId1, Long userId2, String originalChatId) {
            this.bizType = bizType;
            this.chatType = chatType;
            this.userId1 = userId1;
            this.userId2 = userId2;
            this.originalChatId = originalChatId;
        }

        public Integer getBizType() {
            return bizType;
        }

        public String getChatType() {
            return chatType;
        }

        public Long getUserId1() {
            return userId1;
        }

        public Long getUserId2() {
            return userId2;
        }

        public String getOriginalChatId() {
            return originalChatId;
        }

        /**
         * 获取聊天类型描述
         */
        public String getChatTypeDesc() {
            if (ImConstant.ChatType.C2C.equals(chatType)) {
                return "单聊";
            } else if (ImConstant.ChatType.GROUP.equals(chatType)) {
                return "群聊";
            } else {
                return "未知类型";
            }
        }

        /**
         * 获取参与用户列表
         */
        public List<Long> getParticipants() {
            return Arrays.asList(userId1, userId2);
        }

        @Override
        public String toString() {
            return String.format("ChatIdInfo{bizType=%d, chatType='%s'(%s), userId1=%d, userId2=%d, chatId='%s'}", 
                bizType, chatType, getChatTypeDesc(), userId1, userId2, originalChatId);
        }
    }

    public static void main(String[] args) {
        // 构建测试
        String c2cChatId = buildC2CChatId(200, 111L, 222L);
        System.out.println("单聊会话id:" + c2cChatId);

        String groupChatId = buildGroupChatId(200, 111L);
        System.out.println("群聊会话id（其实就是群id）:" + groupChatId);

        // 解析测试
        System.out.println("\n=== 解析测试 ===");
        try {
            ChatIdInfo chatIdInfo = parseChatId(c2cChatId);
            System.out.println("解析结果: " + chatIdInfo);
            
            List<String> participants = getParticipantUserIds(c2cChatId);
            System.out.println("参与用户: " + participants);
            
            boolean isAuthorized = isUserAuthorizedForChat("111", c2cChatId);
            System.out.println("用户111是否有权限: " + isAuthorized);
            
            boolean isC2C = isC2CChat(c2cChatId);
            System.out.println("是否为单聊: " + isC2C);
            
        } catch (Exception e) {
            System.err.println("解析失败: " + e.getMessage());
        }
    }
}
