package com.xzll.business.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * @Author: hzz
 * @Date: 2024/06/03 08:11:39
 * @Description:
 */
@Data
@TableName("im_c2c_msg_record")
public class ImC2CMsgRecord implements Serializable {


    private static final long serialVersionUID = -1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 发送人id
     */
    private String fromUserId;

    /**
     * 接收人id
     */
    private String toUserId;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 消息内容
     */
    private String msgContent;

    /**
     * 消息唯一id
     */
    private String msgId;

    /**
     * 消息的发送时间 精确到毫秒
     */
    private Long msgCreateTime;

    /**
     * 消息格式
     */
    private Integer msgFormat;

    /**
     * 消息类型
     */
    private Integer msgType;
    /**
     * 重试次数
     */
    private Integer retryCount;

    

}
