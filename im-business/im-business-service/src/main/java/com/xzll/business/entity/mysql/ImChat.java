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
@TableName("im_chat")
public class ImChat implements Serializable {


    private static final long serialVersionUID = -1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /**
     * 会话id：单聊时：（业务类型-会话类型-更小的userId-更大的userId） 群聊时（业务类型-会话类型-发起群聊的userId-时间戳）
     */
    private String chatId;

    /**
     * 发起会话方
     */
    private String fromUserId;

    /**
     * 被发起会话方,群聊的话固定为-1
     */
    private String toUserId;
    
    /**
     * 会话类型，1单聊，2群聊
     */
    private Integer chatType;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
