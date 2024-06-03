package com.xzll.common.constant;

import io.netty.util.AttributeKey;

public interface ImConstant {
    public static final String USER_ID = "userId";

    public static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf(USER_ID);


    public static class TraceConstant {
        public static final String TRACE_ID = "xzll_trace_id";

        public static final String TRACE_ID_HTTP = "xzll_trace_id_http";

    }

    public static class TopicConstant {
        public static final String XZLL_TEST_TOPIC = "xzll-c2cmsg-topic";
    }

}
