package com.xzll.connect.netty.channel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.commons.lang3.StringUtils;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class ChannelManager {
    private static ChannelGroup GlobalGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    private static ConcurrentMap<String, ChannelId> ChannelMap = new ConcurrentHashMap<String, ChannelId>();
    private static ConcurrentMap<String, String> UserChannelMap = new ConcurrentHashMap<String, String>();
    private static ConcurrentMap<String, String> ChannelUserMap = new ConcurrentHashMap<String, String>();

    public static void addUserChannel(String userId, String channelId) {
        UserChannelMap.put(userId, channelId);
        ChannelUserMap.put(channelId, userId);
    }

    public static void removeUserChannel(String userId, String channelId) {
        UserChannelMap.remove(userId, channelId);
        ChannelUserMap.remove(channelId, userId);
    }

    public static String getChannelIdByUserId(String userId) {
        return UserChannelMap.get(userId);
    }

    public static String getUserIdByChannelId(String channelId) {
        return ChannelUserMap.get(channelId);
    }

    public static void addChannel(Channel channel) {
        GlobalGroup.add(channel);
        ChannelMap.put(channel.id().asLongText(), channel.id());
    }

    public static void removeChannel(Channel channel) {
        GlobalGroup.remove(channel);
        ChannelMap.remove(channel.id().asLongText());
    }

    public static Channel findChannel(String id) {
        if (StringUtils.isEmpty(id)){
            return null;
        }
        ChannelId channelId = ChannelMap.get(id);
        if (null == channelId) {
            return null;
        } else {
            return GlobalGroup.find(channelId);
        }
    }

    public static void send2All(TextWebSocketFrame tws) {
        GlobalGroup.writeAndFlush(tws);
    }

}
