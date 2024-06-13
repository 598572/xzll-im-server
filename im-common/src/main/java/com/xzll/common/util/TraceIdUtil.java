package com.xzll.common.util;


import cn.hutool.core.lang.Assert;
import com.xzll.common.constant.ImConstant;
import org.apache.commons.lang3.StringUtils;

import org.slf4j.MDC;


import java.util.UUID;

/**
 * @Author: hzz
 * @Date: 2023/2/26 15:19:36
 * @Description:
 */
public class TraceIdUtil {

    public static final String REGEX = "-";

    public static final Object ipLock = new Object();

    public static String ip = NetUtils.getOneInnerIp();

    public static final String TRACE_ID = ImConstant.TraceConstant.TRACE_ID;


    public static String getTraceIdByLocal() {
        return MDC.get(ImConstant.TraceConstant.TRACE_ID);
    }

    /**
     * 传递traceId至MDC
     */
    public static void setTraceId() {
        MDC.put(ImConstant.TraceConstant.TRACE_ID, buildTraceId());
    }

    public static void setTraceId(String traceId) {
        Assert.notNull(traceId);
        if (StringUtils.isNotBlank(traceId)) {
            MDC.put(ImConstant.TraceConstant.TRACE_ID, traceId);
        }
    }


    /**
     * 构建traceId
     *
     * @return
     */
    public static String buildTraceId() {
        return UUID.randomUUID().toString().replaceAll(REGEX, StringUtils.EMPTY);
    }

    /**
     * 清理traceId
     */
    public static void cleanTraceId() {
        MDC.clear();
    }


    /**
     * 获取内网ip
     *
     * @return
     */
    public static String getIp() {
        if (null != ip) {
            return ip;
        }

        synchronized (ipLock) {
            if (null != ip) {
                return ip;
            }

            ip = NetUtils.getOneInnerIp();
            return ip;
        }
    }

}
