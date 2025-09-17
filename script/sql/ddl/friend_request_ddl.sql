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
