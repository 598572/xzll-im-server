package com.xzll.connect.netty.channel;

import io.netty.channel.Channel;


import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class LocalChannelManager {
    private static ConcurrentMap<String, Channel> userIdChannelMap = new ConcurrentHashMap<String, Channel>();
    private static ConcurrentMap<String, String> channelIdUserIdMap = new ConcurrentHashMap<String, String>();

    public static void addUserChannel(String userId, Channel channel) {
        userIdChannelMap.put(userId, channel);
        channelIdUserIdMap.put(channel.id().asLongText(), userId);
    }

    public static void removeUserChannel(String userId) {
        if (userIdChannelMap.get(userId) == null) {
            return;
        }
        String channelId = userIdChannelMap.get(userId).id().asLongText();
        channelIdUserIdMap.remove(channelId);
        userIdChannelMap.remove(userId);
    }

    /**
     * 根据用户id获取channel
     *
     * @param userId
     * @return
     */
    public static Channel getChannelByUserId(String userId) {
        return userIdChannelMap.get(userId);
    }

    public static Set<String> getAllOnLineUserId() {
        return userIdChannelMap.keySet();
    }

    /**
     * 根据channelId 获取用户id
     *
     * @param channelId
     * @return
     */
    public static String getUserIdByChannelId(String channelId) {
        return channelIdUserIdMap.get(channelId);
    }

}
