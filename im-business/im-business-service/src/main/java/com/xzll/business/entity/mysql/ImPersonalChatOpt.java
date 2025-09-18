package com.xzll.business.entity.mysql;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
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
@TableName("im_chat_user_opt")  // 修正表名
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
    @TableField("chat_id")
    private String chatId;

    /**
     * 操作的用户id
     */
    @TableField("user_id")
    private String userId;

    /**
     * 0否 （不置顶） ，1是（置顶）
     */
    @TableField("to_top")
    private Integer toTop;  // 改为Integer类型，与DDL中的tinyint对应

    /**
     * 0否（展示） ，1是（不展示）
     */
    @TableField("un_show")
    private Integer unShow;  // 改为Integer类型

    /**
     * 某人某会话的未读总数
     */
    @TableField("un_read_count")
    private Integer unReadCount;

    /**
     * 0否（不删除） ，1是（删除）
     */
    @TableField("del_chat")
    private Integer delChat;  // 改为Integer类型，字段名改为delChat

    /**
//     * 此会话最后一条消息时间
//     */
//    private Long lastMsgTime;

    /**
     * 创建时间
     */
    @TableField("create_time")
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField("update_time")
    private LocalDateTime updateTime;


}
