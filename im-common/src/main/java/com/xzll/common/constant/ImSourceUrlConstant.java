package com.xzll.common.constant;

public interface ImSourceUrlConstant {

    //基础路径
    String BASE_URL = "xzll/im";


    /**
     * 单聊消息相关
     */
    public static interface C2C {
        String C2C_BASE = BASE_URL + "/c2c";

        //获取msgId (一次请求将返回1000个msgId)
        public static final String GET_BATCH_MSG_ID = C2C_BASE + "/get/batch/msgId";

        //发送单聊消息
        public static final String SEND = C2C_BASE + "/send";
        //撤回单聊消息
        public static final String WITHDRAW = C2C_BASE + "/withdraw";


        //服务端接收到消息后的ack
        public static final String SERVER_RECEIVE_ACK = C2C_BASE + "/response/ack/server/received";
        //接收方响应未读ack
        public static final String TO_USER_UN_READ_ACK = C2C_BASE + "/response/ack/toUser/unread";
        //接收方响应 已读ack
        public static final String TO_USER_READ_ACK = C2C_BASE + "/response/ack/toUser/read";

    }

    /**
     * 群聊消息相关
     */
    public static interface Group {
        public static final String GROUP_BASE = BASE_URL + "/group";

    }


    /**
     * 最近会话列表
     */
    public static final String LAST_CHAT_LIST = BASE_URL + "/last/chat/list";

    /**
     * 好友相关
     */
    public static interface Friend {
        String FRIEND_BASE = BASE_URL + "/friend";

        //好友申请推送
        public static final String FRIEND_REQUEST_PUSH = FRIEND_BASE + "/request/push";
        //好友申请处理结果推送
        public static final String FRIEND_REQUEST_HANDLE_PUSH = FRIEND_BASE + "/request/handle/push";
    }


}
