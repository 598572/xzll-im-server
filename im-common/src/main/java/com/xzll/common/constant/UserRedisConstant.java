package com.xzll.common.constant;

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
        /**
         * 离线
         */
        OFF_LINE(0),

        //将来可能置忙

        /**
         * 在线
         */
        ON_LINE(5);

        private final Integer value;

        UserStatus(Integer value) {
            this.value = value;
        }

        public Integer getValue() {
            return value;
        }
    }


}
