package com.xzll.auth.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.xzll.common.constant.RedisConstant;
import com.xzll.common.utils.RedissonUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * @Author: hzz
 * @Date: 2024/6/10 11:05:14
 * @Description: 资源与角色匹配关系管理业务类
 */
@Service
public class ResourceServiceImpl {

    private Map<String, List<String>> resourceRolesMap;

    @Autowired
    private RedissonUtils redissonUtils;

    @PostConstruct
    public void initData() {
        resourceRolesMap = new TreeMap<>();
        //暂时在代码中只配一个 后期读库
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

        // 将Map<String, List<String>>转换为Map<String, String>存储
        Map<String, String> stringMap = new TreeMap<>();
        for (Map.Entry<String, List<String>> entry : resourceRolesMap.entrySet()) {
            stringMap.put(entry.getKey(), String.join(",", entry.getValue()));
        }
        redissonUtils.setHash(RedisConstant.RESOURCE_ROLES_MAP, stringMap);
    }
}
