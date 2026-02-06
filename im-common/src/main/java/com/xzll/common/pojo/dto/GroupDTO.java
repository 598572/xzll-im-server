package com.xzll.common.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群组信息DTO
 */
@Data
public class GroupDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 群名称
     */
    private String groupName;

    /**
     * 群头像
     */
    private String groupAvatar;

    /**
     * 群描述
     */
    private String groupDesc;

    /**
     * 群主ID
     */
    private String ownerId;

    /**
     * 最大成员数
     */
    private Integer maxMemberCount;

    /**
     * 当前成员数
     */
    private Integer currentCount;

    /**
     * 群类型：1-普通群，2-企业群
     */
    private Integer groupType;

    /**
     * 状态：1-正常，2-已解散
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}
