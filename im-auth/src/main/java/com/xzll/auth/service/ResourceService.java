package com.xzll.auth.service;

import java.util.List;
import java.util.Map;

/**
 * @Author: hzz
 * @Date: 2024/12/19
 * @Description: 资源权限服务接口
 */
public interface ResourceService {

    /**
     * 刷新配置（重新从数据库加载）
     */
    void refreshConfig();

    /**
     * 获取所有资源权限配置
     *
     * @return 资源路径 -> 角色列表的映射
     */
    Map<String, List<String>> getAllResourceRoles();
}
