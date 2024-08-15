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
 * @Description: im_某用户对某个会话的个人操作，如（置顶该聊天、不显示该聊天、删除该聊天）
 */
@Data
@TableName("im_chat_user_opt")
public class ImChatUserOpt implements Serializable {


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
    private Boolean delChat;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;


}
