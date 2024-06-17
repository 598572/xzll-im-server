package com.xzll.common.constant;

import io.netty.util.AttributeKey;
import org.checkerframework.checker.units.qual.C;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public interface ImConstant {
    public static final String USER_ID = "userId";

    public static final String TOKEN = "token";

    public static final String START_TIME = "startTime";


    /**
     * 默认的业务类型 方便后期区分不同业务线
     */
    public static final Integer DEFAULT_BIZ_TYPE = 100;

    public static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf(USER_ID);


    public static class TraceConstant {
        public static final String TRACE_ID = "im_trace_id";

        public static final String TRACE_ID_HTTP = "im_trace_id_http";

    }


    /**
     * 会话类型
     */
    public static class ChatType {
        //单聊
        public static final String C2C = "1";
        //群聊
        public static final String GROUP = "2";

        public static final Map<String, String> CHAT_TYPE_MAP = new ConcurrentHashMap();

        static {
            CHAT_TYPE_MAP.put(C2C, C2C);
            CHAT_TYPE_MAP.put(GROUP, GROUP);
        }
    }

    public static class TopicConstant {
        public static final String XZLL_C2CMSG_TOPIC = "xzll-c2cmsg-topic";
    }

    public static class RedisKeyConstant {
        public static final String NETTY_IP_PORT = "NETTY_IP_PORT";

        public static final String IM_SERVER_ROUND_COUNTER_KEY = "IM_SERVER_ROUND_COUNTER_KEY";

        public static final String USER_TOKEN_KEY = "USER_TOKEN_KEY:";

    }


}
