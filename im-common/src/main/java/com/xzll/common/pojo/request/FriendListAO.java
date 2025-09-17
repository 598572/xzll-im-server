package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 查询好友列表请求
 */
@Data
public class FriendListAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 当前页码（从1开始）
     */
    private Integer currentPage = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 20;

}
