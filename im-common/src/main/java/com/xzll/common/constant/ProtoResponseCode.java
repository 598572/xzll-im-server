package com.xzll.common.constant;

/**
 * Protobuf 响应码常量
 * 
 * <p>用于 ImProtoResponse 的 code 字段，避免魔法值</p>
 * 
 * @author hzz
 * @date 2025-10-29
 */
public final class ProtoResponseCode {

    /**
     * 成功
     */
    public static final int SUCCESS = 1;

    /**
     * 失败
     */
    public static final int FAILURE = 0;

    /**
     * 参数错误
     */
    public static final int PARAM_ERROR = -1;

    /**
     * 系统异常
     */
    public static final int SYSTEM_ERROR = -2;

    /**
     * 用户不在线
     */
    public static final int USER_OFFLINE = -3;

    /**
     * 消息推送失败
     */
    public static final int PUSH_FAILED = -4;

    private ProtoResponseCode() {
        // 工具类，禁止实例化
    }
}

