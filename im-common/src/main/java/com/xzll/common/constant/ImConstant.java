package com.xzll.common.constant;

import io.netty.util.AttributeKey;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public interface ImConstant {
    public static final String USER_ID = "userId";

    public static final String TOKEN = "token";

    public static final String START_TIME = "startTime";

    public static String WEBSOCKET_PATH = "/websocket";


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
     * im系统共工常量： 0否 ，1是
     */
    public static class CommonConstant {
        public static final Integer NO = 0;
        public static final Integer YES = 1;
    }



    // 数据同步相关常量
    public static final String DATA_TYPE_C2C_MSG_RECORD = "C2C_MSG_RECORD";
    public static final String OPERATION_TYPE_SAVE = "SAVE";
    public static final String OPERATION_TYPE_UPDATE_STATUS = "UPDATE_STATUS";
    public static final String OPERATION_TYPE_UPDATE_WITHDRAW = "UPDATE_WITHDRAW";

    /**
     * 表名/索引名常量
     */
    public static class TableConstant {
        /**
         * C2C消息记录表名/索引名（用于 MySQL、HBase、ES）
         */
        public static final String IM_C2C_MSG_RECORD = "im_c2c_msg_record";
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

    /**
     * rocketMq topic
     */
    public static class TopicConstant {
        public static final String XZLL_C2CMSG_TOPIC = "xzll-c2cmsg-topic";
        public static final String XZLL_DATA_SYNC_TOPIC = "im_c2cmsg-sync-es-topic";
    }

    public static class RedisKeyConstant {

        //=======================im服务器信息相关 key=======================
        /**
         * 服务器信息，每一个imConnect服务启动 都会往redis（后期可能改成zookeeper）注册 ip和端口 key就是NETTY_IP_PORT
         */
        public static final String NETTY_IP_PORT = "imServer:nettyIpPort:";
        /**
         * 给登录用户分配im服务时，的负载均衡算法的：轮询key
         */
        public static final String IM_SERVER_ROUND_COUNTER_KEY = "imServer:roundCounter:";


        //=======================用户登录相关消息相关 key =======================
        /**
         * 用户登录的token key
         */
        public static final String USER_TOKEN_KEY = "userLogin:token:";
        /**
         * 路由信息前缀
         */
        public final static String ROUTE_PREFIX = "userLogin:server:";

        /**
         * 用户登录状态前缀
         */
        public final static String LOGIN_STATUS_PREFIX = "userLogin:status:";


        //=======================离线消息相关 key =======================
        /**
         * 离线消息缓存key
         */
        public static final String OFF_LINE_MSG_KEY = "offLine:msgKey:";
        
        /**
         * 离线好友请求推送缓存key
         */
        public static final String OFF_LINE_FRIEND_REQUEST_KEY = "offLine:friendRequest:";
        
        /**
         * 离线好友响应推送缓存key
         */
        public static final String OFF_LINE_FRIEND_RESPONSE_KEY = "offLine:friendResponse:";


    }

    public static class ClusterEventTypeConstant {
        //发送单聊消息
        public static final int C2C_SEND_MSG = 10;
        //离线消息
        public static final int C2C_OFF_LINE_MSG = 20;
        //客户端响应的 ack消息
        public static final int C2C_CLIENT_RECEIVED_ACK_MSG = 30;
        //撤回消息
        public static final int C2C_CLIENT_WITHDRAW_MSG = 40;
        //数据同步消息
        public static final int C2C_DATA_SYNC = 50;
        //好友申请推送
        public static final int FRIEND_REQUEST_PUSH = 60;
        //好友申请处理结果推送
        public static final int FRIEND_REQUEST_HANDLE_PUSH = 70;
    }


    public enum UserStatus {
        /**
         * 离线
         */
        OFF_LINE(0),

        //将来可能置忙

        /**
         * 在线
         */
        ON_LINE(5);

        private final Integer value;

        UserStatus(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }

    /**
     * 消息状态更新条件，防止状态回退
     */
    public static class MsgStatusUpdateCondition {

        /**
         * 可以更新为未读的条件
         */
        public static Set<Integer> CAN_UPDATE_UN_READ = new HashSet<Integer>();

        /**
         * 可以更新为已读的条件
         */
        public static Set<Integer> CAN_UPDATE_READED = new HashSet<Integer>();

        static {
            CAN_UPDATE_UN_READ.add(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
            CAN_UPDATE_UN_READ.add(MsgStatusEnum.MsgStatus.OFF_LINE.getCode());

            CAN_UPDATE_READED.add(MsgStatusEnum.MsgStatus.SERVER_RECEIVED.getCode());
            CAN_UPDATE_READED.add(MsgStatusEnum.MsgStatus.OFF_LINE.getCode());
            CAN_UPDATE_READED.add(MsgStatusEnum.MsgStatus.UN_READ.getCode());
        }
    }


}
