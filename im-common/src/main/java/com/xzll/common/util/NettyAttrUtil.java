package com.xzll.common.util;

import cn.hutool.core.lang.Assert;
import io.netty.channel.Channel;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class NettyAttrUtil {

    private static final AttributeKey<String> ATTR_KEY_READER_TIME = AttributeKey.valueOf("readerTime");
    public static final String LINK = ":";
    private static final ConcurrentHashMap<String, Integer> IP_PORT_MAP = new ConcurrentHashMap<>(1);

    public static void updateReaderTime(Channel channel, Long time) {
        channel.attr(ATTR_KEY_READER_TIME).set(time.toString());
    }

    public static Long getReaderTime(Channel channel) {
        String value = getAttribute(channel, ATTR_KEY_READER_TIME);

        if (value != null) {
            return Long.valueOf(value);
        }
        return null;
    }


    private static String getAttribute(Channel channel, AttributeKey<String> key) {
        Attribute<String> attr = channel.attr(key);
        return attr.get();
    }

    public static void setIpPort(String ip, Integer port) {
        IP_PORT_MAP.put(ip, port);
    }


    public static Map.Entry<String, Integer> getIpPortMap() {
        return IP_PORT_MAP.entrySet().stream().findFirst().orElseThrow();

    }

    public static String getIpPortStr() {
        return IP_PORT_MAP.entrySet().stream().map(x -> x.getKey() + LINK + x.getValue()).findFirst().orElse(null);
    }

    public static String getIpStr(String ipPortStr) {
        if (StringUtils.isBlank(ipPortStr)) {
            return null;
        }
        String[] split = StringUtils.split(ipPortStr, LINK);
        return split[0];
    }

    public static Integer getPortInt(String ipPortStr) {
        Assert.isTrue(StringUtils.isNotBlank(ipPortStr), "ip端口为空");
        String[] split = StringUtils.split(ipPortStr, LINK);
        String port = split[1];
        Assert.isTrue(StringUtils.isNotBlank(port), "解析不到端口");
        return Integer.valueOf(port);
    }
}
