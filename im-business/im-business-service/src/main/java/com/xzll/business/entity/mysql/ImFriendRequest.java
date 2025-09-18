package com.xzll.business.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/09/17
 * @Description: 好友申请表
 */
@Data
@TableName("im_friend_request")
public class ImFriendRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 申请ID
     */
    private String requestId;

    /**
     * 申请人用户ID
     */
    private String fromUserId;

    /**
     * 被申请人用户ID
     */
    private String toUserId;

    /**
     * 申请备注消息
     */
    private String requestMessage;

    /**
     * 申请状态：0-待处理，1-已同意，2-已拒绝，3-已过期
     */
    private Integer status;

    /**
     * 处理时间
     */
    private LocalDateTime handleTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

}
