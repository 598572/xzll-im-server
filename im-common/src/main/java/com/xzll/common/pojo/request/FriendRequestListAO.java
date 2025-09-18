package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 查询好友申请列表请求
 */
@Data
public class FriendRequestListAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 申请类型：1-我发出的申请，2-我收到的申请
     */
    private Integer requestType = 2;

    /**
     * 当前页码（从1开始）
     */
    private Integer currentPage = 1;

    /**
     * 每页数量
     */
    private Integer pageSize = 20;

}
