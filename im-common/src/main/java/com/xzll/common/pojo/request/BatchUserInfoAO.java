package com.xzll.common.pojo.request;

import lombok.Data;
import java.util.List;

/**
 * 批量查询用户信息请求参数
 *
 * @Author: hzz
 * @Date: 2025/11/20
 * @Description: 支持客户端批量查询未缓存的用户信息
 */
@Data
public class BatchUserInfoAO {

    /**
     * 需要查询的用户ID列表
     * 限制最大50个，避免单次查询过多
     */
    private List<String> userIds;

    /**
     * 是否只查询基础信息（昵称、头像）
     * true: 只返回昵称、头像、用户ID
     * false: 返回完整用户信息
     */
    private Boolean onlyBasicInfo = true;
}
