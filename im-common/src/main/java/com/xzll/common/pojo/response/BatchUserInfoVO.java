package com.xzll.common.pojo.response;

import lombok.Data;
import java.util.List;

/**
 * 批量查询用户信息返回参数
 *
 * @Author: hzz
 * @Date: 2025/11/20
 * @Description: 客户端缓存未命中时的批量查询结果
 */
@Data
public class BatchUserInfoVO {

    /**
     * 查询到的用户信息列表
     */
    private List<UserBasicInfo> users;

    /**
     * 未找到的用户ID列表（可能是无效用户或已注销）
     */
    private List<String> notFoundUserIds;

    /**
     * 用户基础信息
     */
    @Data
    public static class UserBasicInfo {
        /**
         * 用户ID
         */
        private String userId;
        
        /**
         * 用户昵称
         */
        private String userName;
        
        /**
         * 用户全名
         */
        private String userFullName;
        
        /**
         * 头像URL
         */
        private String headImage;
        
        /**
         * 性别 (0女, 1男, -1未知)
         */
        private Integer sex;
    }
}
