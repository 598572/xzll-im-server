package com.xzll.auth.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.xzll.auth.constant.RedisConstant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
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
    @Qualifier(value = "secondaryRedisTemplate")
    private RedisTemplate<String, Object> redisTemplate;

    @PostConstruct
    public void initData() {
        resourceRolesMap = new TreeMap<>();
        //暂时在代码中只配一个 后期读库
        resourceRolesMap.put("/xzll/im/login", CollUtil.toList("ADMIN"));
        resourceRolesMap.put("/im-auth/oauth/logout", CollUtil.toList("ADMIN"));

        redisTemplate.opsForHash().putAll(RedisConstant.RESOURCE_ROLES_MAP, resourceRolesMap);
    }
}
