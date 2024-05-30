package com.xzll.connect.pojo.dto;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class UserSessionDTO {


    /**
     * 会话Id
     */
    private String sessionId;


    private String firstUserId;

    /**
     * 用户名称 1
     */
    private String firstUserName;

    /**
     * 用户类型 1
     * 1 乘客
     * 2 司机
     */
    private Integer firstUserType;


    /**
     * 用户ID 2
     */
    private String secondUserId;

    /**
     * 用户名称 2
     */
    private String secondUserName;

    /**
     * 用户类型 2
     * 1 乘客
     * 2 司机
     */
    private Integer secondUserType;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 修改时间
     */
    private Long updateTime;

}
