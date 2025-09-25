-- 资源权限配置表
CREATE TABLE `im_resource_role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `resource_path` varchar(255) NOT NULL COMMENT '资源路径',
  `resource_name` varchar(100) DEFAULT NULL COMMENT '资源名称',
  `roles` varchar(500) NOT NULL COMMENT '允许访问的角色，多个角色用逗号分隔',
  `description` varchar(500) DEFAULT NULL COMMENT '资源描述',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：0-禁用，1-启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_resource_path` (`resource_path`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='资源权限配置表';

-- 初始化数据
INSERT INTO `im_resource_role` (`resource_path`, `resource_name`, `roles`, `description`, `status`) VALUES
('/xzll/im/login', '用户登录', 'ADMIN', 'IM系统登录接口', 1),
('/im-business/api/chat/lastChatList', '最近会话列表', 'ADMIN', '获取用户最近会话列表', 1),
('/im-auth/oauth/logout', '用户登出', 'ADMIN', 'OAuth登出接口', 1),
('/im-business/api/user/search', '用户搜索', 'ADMIN', '搜索用户功能', 1),
('/im-business/api/friend/request/send', '发送好友申请', 'ADMIN', '发送好友申请接口', 1),
('/im-business/api/friend/request/handle', '处理好友申请', 'ADMIN', '处理好友申请接口', 1),
('/im-business/api/friend/request/list', '好友申请列表', 'ADMIN', '获取好友申请列表', 1),
('/im-business/api/friend/list', '好友列表', 'ADMIN', '获取好友列表', 1),
('/im-business/api/friend/delete', '删除好友', 'ADMIN', '删除好友关系', 1),
('/im-business/api/friend/block', '拉黑好友', 'ADMIN', '拉黑好友功能', 1);
