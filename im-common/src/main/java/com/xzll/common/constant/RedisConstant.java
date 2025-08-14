package com.xzll.common.constant;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:45:04
 * @Description: Redis常量定义
 * <p>
 * 功能：
 * 1. 定义Redis中使用的键名常量
 * 2. 统一管理Redis键名，避免硬编码
 * 3. 便于权限管理和缓存管理
 */
public class RedisConstant {

    /**
     * 资源角色映射表
     * 存储格式：key=接口路径, value=权限列表
     */
    public static final String RESOURCE_ROLES_MAP = "AUTH:RESOURCE_ROLES_MAP";
}
