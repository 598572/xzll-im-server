package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 用户搜索请求
 */
@Data
public class UserSearchAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 搜索关键词（支持用户名、用户全称、手机号、邮箱）
     */
    private String keyword;

    /**
     * 搜索类型：1-精确搜索，2-模糊搜索
     */
    private Integer searchType = 2;

    /**
     * 当前用户ID（用于判断好友关系）
     */
    private String currentUserId;

    /**
     * 当前页码（从1开始）
     */
    private Integer currentPage = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 10;

}
