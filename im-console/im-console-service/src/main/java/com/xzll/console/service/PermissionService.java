package com.xzll.console.service;

import java.util.List;
import java.util.Map;

/**
 * 权限管理服务接口
 * 
 * 功能：
 * 1. 动态管理接口权限配置
 * 2. 查询接口所需权限
 * 3. 权限配置的增删改查
 *
 * @Author: hzz
 * @Date: 2024/6/10 09:52:49
 */
public interface PermissionService {

    /**
     * 获取接口所需权限
     *
     * @param path 接口路径
     * @return 权限列表，如果接口未配置权限则返回null
     */
    List<String> getRequiredPermissions(String path);

    /**
     * 设置接口权限配置
     *
     * @param path 接口路径
     * @param permissions 权限列表
     * @return 是否设置成功
     */
    boolean setRequiredPermissions(String path, List<String> permissions);

    /**
     * 移除接口权限配置
     *
     * @param path 接口路径
     * @return 是否移除成功
     */
    boolean removeRequiredPermissions(String path);

    /**
     * 获取所有接口权限配置
     *
     * @return 权限配置映射，key为接口路径，value为权限字符串
     */
    Map<String, String> getAllPermissions();

    /**
     * 检查接口是否需要权限验证
     *
     * @param path 接口路径
     * @return true表示需要权限验证，false表示不需要
     */
    boolean requiresPermission(String path);

    /**
     * 批量设置接口权限配置
     *
     * @param permissionsMap 权限配置映射，key为接口路径，value为权限列表
     * @return 成功设置的数量
     */
    int batchSetPermissions(Map<String, List<String>> permissionsMap);

    /**
     * 清空所有权限配置
     *
     * @return 是否清空成功
     */
    boolean clearAllPermissions();

    /**
     * 获取权限配置数量
     *
     * @return 权限配置数量
     */
    int getPermissionCount();
}
