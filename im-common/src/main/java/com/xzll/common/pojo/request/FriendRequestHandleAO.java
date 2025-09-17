package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 处理好友申请请求
 */
@Data
public class FriendRequestHandleAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 申请ID
     */
    private String requestId;

    /**
     * 操作用户ID（处理申请的用户）
     */
    private String userId;

    /**
     * 处理结果：1-同意，2-拒绝
     */
    private Integer handleResult;

}
