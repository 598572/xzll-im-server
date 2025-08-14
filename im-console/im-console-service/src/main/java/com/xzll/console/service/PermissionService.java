package com.xzll.console.service;

import cn.hutool.core.convert.Convert;
import com.xzll.common.utils.RedissonUtils;
import com.xzll.common.constant.RedisConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/6/10 09:52:49
 * @Description: 权限管理服务
 * <p>
 * 功能：
 * 1. 动态管理接口权限配置
 * 2. 查询接口所需权限
 * 3. 权限配置的增删改查
 */
@Slf4j
@Service
public class PermissionService {

    @Autowired
    private RedissonUtils redissonUtils;

    /**
     * 获取接口所需权限
     *
     * @param path 接口路径
     * @return 权限列表
     */
    public List<String> getRequiredPermissions(String path) {
        try {
            Object obj = redissonUtils.getHash(RedisConstant.RESOURCE_ROLES_MAP, path);
            if (obj == null) {
                log.debug("接口未配置权限: {}", path);
                return null;
            }
            
            List<String> permissions = Convert.toList(String.class, obj);
            log.debug("接口权限配置: {} -> {}", path, permissions);
            return permissions;
        } catch (Exception e) {
            log.error("获取接口权限配置失败: {}", path, e);
            return null;
        }
    }

    /**
     * 设置接口权限配置
     *
     * @param path 接口路径
     * @param permissions 权限列表
     * @return 是否设置成功
     */
    public boolean setRequiredPermissions(String path, List<String> permissions) {
        try {
            if (permissions == null || permissions.isEmpty()) {
                log.warn("权限列表为空，移除接口权限配置: {}", path);
                redissonUtils.deleteHash(RedisConstant.RESOURCE_ROLES_MAP, path);
                return true;
            }
            
            String permissionsStr = String.join(",", permissions);
            redissonUtils.setHash(RedisConstant.RESOURCE_ROLES_MAP, path, permissionsStr);
            log.info("设置接口权限配置成功: {} -> {}", path, permissions);
            return true;
        } catch (Exception e) {
            log.error("设置接口权限配置失败: {} -> {}", path, permissions, e);
            return false;
        }
    }

    /**
     * 移除接口权限配置
     *
     * @param path 接口路径
     * @return 是否移除成功
     */
    public boolean removeRequiredPermissions(String path) {
        try {
            redissonUtils.deleteHash(RedisConstant.RESOURCE_ROLES_MAP, path);
            log.info("移除接口权限配置成功: {}", path);
            return true;
        } catch (Exception e) {
            log.error("移除接口权限配置失败: {}", path, e);
            return false;
        }
    }

    /**
     * 获取所有接口权限配置
     *
     * @return 权限配置映射
     */
    public Map<String, String> getAllPermissions() {
        try {
            // 注意：这里需要根据RedissonUtils的实际方法签名来调用
            // 暂时返回null，需要根据实际API调整
            log.warn("getAllPermissions方法需要根据RedissonUtils实际API调整");
            return null;
        } catch (Exception e) {
            log.error("获取所有权限配置失败", e);
            return null;
        }
    }

    /**
     * 检查接口是否需要权限验证
     *
     * @param path 接口路径
     * @return true表示需要权限验证，false表示不需要
     */
    public boolean requiresPermission(String path) {
        List<String> permissions = getRequiredPermissions(path);
        return permissions != null && !permissions.isEmpty();
    }
} 