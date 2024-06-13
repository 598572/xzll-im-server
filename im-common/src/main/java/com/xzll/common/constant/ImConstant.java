package com.xzll.common.constant;

import io.netty.util.AttributeKey;

public interface ImConstant {
    public static final String USER_ID = "userId";

    public static final String START_TIME = "startTime";

    public static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf(USER_ID);


    public static class TraceConstant {
        public static final String TRACE_ID = "im_trace_id";

        public static final String TRACE_ID_HTTP = "im_trace_id_http";

    }

    public static class TopicConstant {
        public static final String XZLL_TEST_TOPIC = "xzll-c2cmsg-topic";
    }

    public static class RedisConstant {
        public static final String NETTY_IP_PORT = "netty_ip_port";

        public static final String IMSERVER_ROUND_COUNTER_KEY = "IMSERVER_ROUND_COUNTER_KEY";

    }


}
