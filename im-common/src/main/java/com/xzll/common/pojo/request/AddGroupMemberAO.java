package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 添加群成员请求AO
 */
@Data
public class AddGroupMemberAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 要添加的成员ID列表
     */
    private List<String> memberIds;

    /**
     * 操作人ID（群主或管理员）
     */
    private String operatorId;

    /**
     * 加入方式：1-主动加入，2-邀请加入
     */
    private Integer joinType;
}
