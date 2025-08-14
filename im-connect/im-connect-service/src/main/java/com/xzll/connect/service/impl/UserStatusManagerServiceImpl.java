package com.xzll.connect.service.impl;


import com.xzll.common.constant.ImConstant;
import com.xzll.common.util.NettyAttrUtil;
import com.xzll.connect.service.UserStatusManagerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import com.xzll.common.utils.RedissonUtils;
import org.springframework.stereotype.Service;
import javax.annotation.Resource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * @Author: hzz
 * @Date: 2024/6/18 16:04:34
 * @Description: 用户状态管理 使用lua 保证原子性
 */
@Slf4j
@Service
public class UserStatusManagerServiceImpl implements UserStatusManagerService, InitializingBean {




    private static final String LUA_CLEAR_USER_STATUS_DISCONNECT_AFTER_LUA = "lua/clear_user_status_disconnect_after.lua";
    private static final String LUA_SET_USER_STATUS_CONNECT_AFTER_LUA = "lua/set_user_status_connect_after.lua";

    private String clearUserStatusScript;
    private String setUserStatusScript;

    @Resource
    private RedissonUtils redissonUtils;

    /**
     * 放到这里，确保只加载一次
     *
     * @throws Exception
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        // 加载 Lua 脚本
        clearUserStatusScript = loadLuaScript(LUA_CLEAR_USER_STATUS_DISCONNECT_AFTER_LUA);
        setUserStatusScript = loadLuaScript(LUA_SET_USER_STATUS_CONNECT_AFTER_LUA);
    }

    private String loadLuaScript(String path) throws Exception {
        ClassPathResource resource = new ClassPathResource(path);
        InputStream in = resource.getInputStream();
        return IOUtils.toString(in, StandardCharsets.UTF_8);
    }

    /**
     * 握手成功后 设置用户登录机器 以及用户信息
     *
     * @param status
     * @param uidStr
     */
    @Override
    public void userConnectSuccessAfter(Integer status, String uidStr) {
        try {
            Long execute = redissonUtils.executeLuaScriptAsLong(setUserStatusScript, 
                    Arrays.asList(ImConstant.RedisKeyConstant.ROUTE_PREFIX, ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX),
                    uidStr, NettyAttrUtil.getIpPortStr(), status.toString());
            log.info("客户端握手成功后设置用户状态结果:{}", execute);
        } catch (Exception e) {
            log.error("客户端握手成功后设置用户状态异常:", e);
        }
    }


    /**
     * 连接关闭后，清除用户登录和对应机器信息
     *
     * @param uid
     */
    @Override
    public void userDisconnectAfter(String uid) {
        try {
            Long execute = redissonUtils.executeLuaScriptAsLong(clearUserStatusScript,
                    Arrays.asList(ImConstant.RedisKeyConstant.ROUTE_PREFIX, ImConstant.RedisKeyConstant.LOGIN_STATUS_PREFIX), uid);
            log.info("客户端断连后清除用户状态结果:{}", execute);
        } catch (Exception e) {
            log.error("客户端断连后清除用户状态异常:", e);
        }
    }
}
