package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 移除群成员请求AO
 */
@Data
public class RemoveGroupMemberAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 要移除的成员ID列表
     */
    private List<String> memberIds;

    /**
     * 操作人ID（群主或管理员）
     */
    private String operatorId;
}
