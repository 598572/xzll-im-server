


use xzll_im_db_0;

create table im_chat
(
    id            bigint auto_increment comment '主键' primary key,
    chat_id       varchar(255)                           not null comment '会话id：单聊时：（业务类型-会话类型-更小的userId-更大的userId） 群聊时（业务类型-会话类型-发起群聊的userId-时间戳） ',
    from_user_id  varchar(100)                           not null comment '发起会话方',
    to_user_id    varchar(100)                           not null comment '被发起会话方,群聊的话固定为-1',
    
    
    chat_type     tinyint                                not null comment '会话类型，1单聊，2群聊',
    create_time   datetime     default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime     default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique idx_chat_id (chat_id),
    index idx_from_user_id_to_user_id (from_user_id, to_user_id),
    
) comment 'im_单聊会话表（单聊和群聊放一起）';



create table im_chat_user_opt
(
    id            bigint auto_increment comment '主键' primary key,
    chat_id       varchar(255) not null comment '会话id：单聊时：（业务类型-会话类型-更小的userId-更大的userId） 群聊时（业务类型-会话类型-发起群聊的userId-时间戳） ',
    user_id       varchar(100) not null comment '操作的用户id',
    to_top        tinyint      not null default 0 comment '0否 （不置顶） ，1是（置顶） ',
    un_show       tinyint      not null default 0 comment '0否（展示） ，1是（不展示） ',
    un_read_count int          not null default 0 comment '某人某会话的未读总数',
    del_chat      tinyint      not null default 0 comment '0否（不删除） ，1是（删除） ',
    create_time   datetime              default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time   datetime              default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_chat_id_user_id (chat_id, user_id)
) comment 'im_某用户对某个会话的个人操作，如（置顶该聊天、不显示该聊天、删除该聊天）';


create table im_friend_relation
(
    id          bigint auto_increment comment '主键' primary key,
    user_id     varchar(100) not null comment '我的用户id',
    friend_id   varchar(100) not null comment '好友用户id',
    black_flag  tinyint      not null default 0 comment '0否（未拉黑） ，1是（拉黑） ',
    del_flag    tinyint      not null default 0 comment '0否（未删除） ，1是（已删除） ',
    create_time datetime              default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time datetime              default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    index idx_chat_id_user_id (user_id, friend_id)
) comment '好友关系表';



create table im_user
(
    id                     bigint auto_increment comment '主键' primary key,
    user_id                varchar(100)                       not null comment '用户id 全局唯一',
    user_name              varchar(100)                       not null comment '用户账号 用于登录',
    user_full_name         varchar(100)                       not null comment '用户全称',
    `password`             varchar(100)                       not null comment '用户密码',
    phone             varchar(20)                        not null comment '用户手机号',
    head_image        varchar(500)                       null comment '用户头像',
    e_mail                 varchar(50)                        null comment '用户邮箱',
    sex               tinyint                            not null default -1 comment '0女 ，1男，-1 未知 ',
    register_terminal_type tinyint                            not null comment '注册时的终端类型，1:android, 2:ios，3:小程序，4:web',
    last_login_time        datetime default CURRENT_TIMESTAMP not null comment '最后一次登录时间',
    register_time          datetime default CURRENT_TIMESTAMP not null comment '注册时间',
    create_time            datetime default CURRENT_TIMESTAMP not null comment '创建时间',
    update_time            datetime default CURRENT_TIMESTAMP not null on update CURRENT_TIMESTAMP comment '更新时间',
    unique idx_user_id (user_id)
) comment 'im用户表';



-- 好友申请表
CREATE TABLE `im_friend_request` (
                                     `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
                                     `request_id` varchar(100) NOT NULL COMMENT '申请ID',
                                     `from_user_id` varchar(100) NOT NULL COMMENT '申请人用户ID',
                                     `to_user_id` varchar(100) NOT NULL COMMENT '被申请人用户ID',
                                     `request_message` varchar(500) DEFAULT '' COMMENT '申请备注消息',
                                     `status` tinyint NOT NULL DEFAULT '0' COMMENT '申请状态：0-待处理，1-已同意，2-已拒绝，3-已过期',
                                     `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_request_id` (`request_id`),
                                     KEY `idx_from_user_id` (`from_user_id`),
                                     KEY `idx_to_user_id` (`to_user_id`),
                                     KEY `idx_status_create_time` (`status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='好友申请表';
