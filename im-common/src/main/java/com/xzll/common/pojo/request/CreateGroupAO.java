package com.xzll.common.pojo.request;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 创建群组请求AO
 */
@Data
public class CreateGroupAO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群名称
     */
    private String groupName;

    /**
     * 群头像（可选）
     */
    private String groupAvatar;

    /**
     * 群描述（可选）
     */
    private String groupDesc;

    /**
     * 群主ID（创建者）
     */
    private String ownerId;

    /**
     * 初始成员ID列表（包含群主）
     */
    private List<String> memberIds;

    /**
     * 群类型：1-普通群，2-企业群（默认1）
     */
    private Integer groupType;

    /**
     * 最大成员数（默认500）
     */
    private Integer maxMemberCount;
}
