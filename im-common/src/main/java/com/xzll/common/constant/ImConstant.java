package com.xzll.common.constant;

import io.netty.util.AttributeKey;

public interface ImConstant {
    public static final String USER_ID = "userId";

    public static final String TOKEN = "token";

    public static final String START_TIME = "startTime";

    public static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf(USER_ID);


    public static class TraceConstant {
        public static final String TRACE_ID = "im_trace_id";

        public static final String TRACE_ID_HTTP = "im_trace_id_http";

    }

    public static class TopicConstant {
        public static final String XZLL_TEST_TOPIC = "xzll-c2cmsg-topic";
    }

    public static class RedisKeyConstant {
        public static final String NETTY_IP_PORT = "NETTY_IP_PORT";

        public static final String IM_SERVER_ROUND_COUNTER_KEY = "IM_SERVER_ROUND_COUNTER_KEY";

        public static final String USER_TOKEN_KEY = "USER_TOKEN_KEY:";

    }


}
