package com.xzll.common.util.msgId;

/**
 * @Author: hzz
 * @Date: 2024/6/16 11:13:50
 * @Description: 策略接口
 */
public interface WorkerIdStrategy {
    /**
     * 获取workerId
     *
     * @return
     */
    long getWorkerId();
}
