package com.xzll.common.pojo.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友信息响应
 */
@Data
public class FriendInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 好友用户ID
     */
    private String friendId;

    /**
     * 好友用户名
     */
    private String friendName;

    /**
     * 好友全称
     */
    private String friendFullName;

    /**
     * 好友头像
     */
    private String friendAvatar;

    /**
     * 好友性别：0-女，1-男，-1-未知
     */
    private Integer friendSex;

    /**
     * 是否拉黑：false-未拉黑，true-已拉黑
     */
    private Boolean blackFlag;

    /**
     * 成为好友时间
     */
    private LocalDateTime createTime;

}
