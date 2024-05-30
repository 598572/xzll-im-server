package com.xzll.connect.pojo.response.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RespUserSessionDTO implements Serializable {

    /**
     * 会话Id
     */
    private String sessionId;

    /**
     * 用户ID 1
     */
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
     * 修改时间
     */
    private Long updateTime;

    /**
     * 创建时间
     */
    private Long createTime;

    /**
     * 消息内容
     */
    private List<RespMessageInfoDTO> messageList;

}
