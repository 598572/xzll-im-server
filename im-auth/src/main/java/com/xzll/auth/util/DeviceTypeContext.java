package com.xzll.auth.util;

import com.xzll.common.constant.enums.ImTerminalType;
import lombok.extern.slf4j.Slf4j;

/**
 * @Author: hzz
 * @Date: 2024/9/23 21:50:00
 * @Description: 设备类型上下文工具类
 * 使用ThreadLocal存储设备类型信息，供JwtTokenEnhancer使用
 */
@Slf4j
public class DeviceTypeContext {
    
    private static final ThreadLocal<ImTerminalType> DEVICE_TYPE_CONTEXT = new ThreadLocal<>();
    
    /**
     * 设置设备类型到当前线程上下文
     * 
     * @param deviceType 设备类型
     */
    public static void setDeviceType(ImTerminalType deviceType) {
        DEVICE_TYPE_CONTEXT.set(deviceType);
        log.debug("设置设备类型到线程上下文: {}", deviceType != null ? deviceType.getDescription() : "null");
    }
    
    /**
     * 从当前线程上下文获取设备类型
     * 
     * @return 设备类型，如果未设置则返回null
     */
    public static ImTerminalType getDeviceType() {
        ImTerminalType deviceType = DEVICE_TYPE_CONTEXT.get();
        log.debug("从线程上下文获取设备类型: {}", deviceType != null ? deviceType.getDescription() : "null");
        return deviceType;
    }
    
    /**
     * 清除当前线程的设备类型上下文
     */
    public static void clear() {
        ImTerminalType deviceType = DEVICE_TYPE_CONTEXT.get();
        DEVICE_TYPE_CONTEXT.remove();
        log.debug("清除线程上下文中的设备类型: {}", deviceType != null ? deviceType.getDescription() : "null");
    }
}
