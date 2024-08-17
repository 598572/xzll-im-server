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
 * @Description: im_个人对会话的操作，如（置顶该聊天、不显示该聊天、删除该聊天） todo 此表数据量较大 需要根据userId 进行分表
 */
@Data
@TableName("im_personal_chat_opt")
public class ImPersonalChatOpt implements Serializable {



    private static final long serialVersionUID = -1L;

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 会话id
     */
    private String chatId;

    /**
     * 操作的用户id
     */
    private String userId;

    /**
     * 0否 （不置顶） ，1是（置顶）
     */
    private Boolean toTop;

    /**
     * 0否（展示） ，1是（不展示）
     */
    private Boolean unShow;

    /**
     * 某人某会话的未读总数
     */
    private Integer unReadCount;

    /**
     * 0否（不删除） ，1是（删除）
     */
    private Boolean delFlag;

    /**
     * 此会话最后一条消息时间
     */
    private Long lastMsgTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
