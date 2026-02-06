package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群消息查询请求AO
 */
@Data
public class GroupMsgQueryAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 当前用户ID（用于权限校验）
     */
    private String userId;

    /**
     * 查询方向：prev-向前查询（更旧的消息），next-向后查询（更新的消息）
     */
    private String direction;

    /**
     * 基准消息ID（用于分页）
     */
    private String baseMsgId;

    /**
     * 每页数量（默认20，最大100）
     */
    private Integer limit;

    /**
     * 开始时间戳（毫秒，可选）
     */
    private Long startTime;

    /**
     * 结束时间戳（毫秒，可选）
     */
    private Long endTime;
}
