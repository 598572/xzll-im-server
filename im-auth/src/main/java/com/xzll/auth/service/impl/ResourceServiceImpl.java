package com.xzll.auth.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.xzll.auth.entity.ImResourceRole;
import com.xzll.auth.mapper.ImResourceRoleMapper;
import com.xzll.auth.service.ResourceService;
import com.xzll.common.constant.RedisConstant;
import com.xzll.common.utils.RedissonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;


/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 资源与角色匹配关系管理业务类
 */
@Slf4j
@Service
public class ResourceServiceImpl implements ResourceService {

    private Map<String, List<String>> resourceRolesMap;

    @Autowired
    private RedissonUtils redissonUtils;
    
    @Resource
    private ImResourceRoleMapper resourceRoleMapper;

    @PostConstruct
    public void initData() {
        try {
            log.info("开始从数据库加载资源权限配置...");
            
            // 从数据库读取启用的资源权限配置
            List<ImResourceRole> resourceRoles = resourceRoleMapper.selectEnabledResources();
            
            if (CollUtil.isEmpty(resourceRoles)) {
                log.warn("数据库中没有找到启用的资源权限配置，使用默认配置");
                initDefaultData();
                return;
            }
            
            resourceRolesMap = new TreeMap<>();
            
            // 转换数据库数据为内存Map
            for (ImResourceRole resourceRole : resourceRoles) {
                String resourcePath = resourceRole.getResourcePath();
                String rolesStr = resourceRole.getRoles();
                
                if (StrUtil.isBlank(resourcePath) || StrUtil.isBlank(rolesStr)) {
                    log.warn("跳过无效的资源权限配置: path={}, roles={}", resourcePath, rolesStr);
                    continue;
                }
                
                // 解析角色字符串为列表
                List<String> roleList = Arrays.stream(rolesStr.split(","))
                        .map(String::trim)
                        .filter(StrUtil::isNotBlank)
                        .collect(Collectors.toList());
                
                if (CollUtil.isNotEmpty(roleList)) {
                    resourceRolesMap.put(resourcePath, roleList);
                    log.debug("加载资源权限: {} -> {}", resourcePath, roleList);
                }
            }
            
            // 将配置存储到Redis
            saveToRedis();
            
            log.info("从数据库成功加载{}个资源权限配置", resourceRolesMap.size());
            
        } catch (Exception e) {
            log.error("从数据库加载资源权限配置失败，使用默认配置", e);
            initDefaultData();
        }
    }
    
    /**
     * 初始化默认配置（兜底方案）
     */
    private void initDefaultData() {
        log.info("使用默认的资源权限配置");
        
        resourceRolesMap = new TreeMap<>();
        resourceRolesMap.put("/xzll/im/login", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-business/api/chat/lastChatList", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-auth/oauth/logout", CollUtil.toList("ADMIN"));
        
        // 好友功能相关接口权限配置
        resourceRolesMap.put("/im-business/api/user/search", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-business/api/friend/request/send", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-business/api/friend/request/handle", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-business/api/friend/request/list", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-business/api/friend/list", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-business/api/friend/delete", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-business/api/friend/block", CollUtil.toList("ADMIN"));
        
        saveToRedis();
    }
    
    /**
     * 保存配置到Redis
     */
    private void saveToRedis() {
        try {
            // 将Map<String, List<String>>转换为Map<String, String>存储
            Map<String, String> stringMap = new TreeMap<>();
            for (Map.Entry<String, List<String>> entry : resourceRolesMap.entrySet()) {
                stringMap.put(entry.getKey(), String.join(",", entry.getValue()));
            }
            
            redissonUtils.setHash(RedisConstant.RESOURCE_ROLES_MAP, stringMap);
            log.info("资源权限配置已同步到Redis");
            
        } catch (Exception e) {
            log.error("保存资源权限配置到Redis失败", e);
        }
    }
    
    /**
     * 刷新配置（重新从数据库加载）
     */
    public void refreshConfig() {
        log.info("手动刷新资源权限配置");
        initData();
    }
    
    /**
     * 获取所有资源权限配置
     */
    public Map<String, List<String>> getAllResourceRoles() {
        return resourceRolesMap;
    }
}
