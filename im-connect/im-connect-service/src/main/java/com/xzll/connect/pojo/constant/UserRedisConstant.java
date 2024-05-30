package com.xzll.connect.pojo.constant;

import lombok.Getter;

/**
 * @Author: hzz
 * @Date: 2022/1/14 16:24:08
 * @Description:
 */
@Getter
public class UserRedisConstant {


    /**
     * 路由信息前缀
     */
    public final static String ROUTE_PREFIX = "user_login_server:";

    /**
     * 登录状态前缀
     */
    public final static String LOGIN_STATUS_PREFIX = "user_login_status:";

    public enum UserStatus {

        ON_LINE,
        /**
         * 聊天消息
         */
        OFF_LINE;

        UserStatus() {
        }



    }


}
