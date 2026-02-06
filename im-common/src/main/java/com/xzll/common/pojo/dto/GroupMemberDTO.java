package com.xzll.common.pojo.dto;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2026-02-05
 * @Description: 群成员信息DTO
 */
@Data
public class GroupMemberDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 成员ID
     */
    private String userId;

    /**
     * 群组ID
     */
    private String groupId;

    /**
     * 成员角色：1-群主，2-管理员，3-普通成员
     */
    private Integer memberRole;

    /**
     * 群昵称
     */
    private String nickname;

    /**
     * 加入时间（毫秒时间戳）
     */
    private Long joinTime;

    /**
     * 加入方式：1-主动加入，2-邀请加入
     */
    private Integer joinType;

    /**
     * 状态：1-正常，2-已退出
     */
    private Integer status;

    /**
     * 加入时间（格式化）
     */
    private LocalDateTime createTime;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 获取角色名称
     */
    public String getRoleName() {
        if (memberRole == null) {
            return "未知";
        }
        switch (memberRole) {
            case 1:
                return "群主";
            case 2:
                return "管理员";
            case 3:
                return "普通成员";
            default:
                return "未知";
        }
    }
}
