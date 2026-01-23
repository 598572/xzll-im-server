-- ================================
-- IM 后台管理系统相关表
-- ================================

-- -----------------------------
-- 管理员相关表
-- -----------------------------

-- 管理员表
CREATE TABLE `im_admin` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admin_id` varchar(100) NOT NULL COMMENT '管理员ID',
    `username` varchar(50) NOT NULL COMMENT '登录用户名',
    `password` varchar(255) NOT NULL COMMENT '密码（BCrypt加密）',
    `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
    `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
    `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
    `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
    `role_id` bigint DEFAULT NULL COMMENT '角色ID',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
    `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
    `create_by` varchar(100) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_admin_id` (`admin_id`),
    UNIQUE KEY `uk_username` (`username`),
    KEY `idx_status` (`status`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员表';

-- 角色表
CREATE TABLE `im_admin_role` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id` varchar(100) NOT NULL COMMENT '角色ID',
    `role_name` varchar(50) NOT NULL COMMENT '角色名称',
    `role_code` varchar(50) NOT NULL COMMENT '角色编码',
    `description` varchar(255) DEFAULT NULL COMMENT '角色描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_by` varchar(100) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_id` (`role_id`),
    UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';

-- 权限表
CREATE TABLE `im_admin_permission` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `permission_id` varchar(100) NOT NULL COMMENT '权限ID',
    `permission_name` varchar(50) NOT NULL COMMENT '权限名称',
    `permission_code` varchar(100) NOT NULL COMMENT '权限编码（如：user:manage）',
    `resource_type` tinyint NOT NULL DEFAULT 1 COMMENT '资源类型：1-菜单，2-按钮，3-接口',
    `parent_id` varchar(100) DEFAULT NULL COMMENT '父权限ID',
    `path` varchar(255) DEFAULT NULL COMMENT '菜单路径',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `sort_order` int DEFAULT 0 COMMENT '排序',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_permission_id` (`permission_id`),
    UNIQUE KEY `uk_permission_code` (`permission_code`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';

-- 角色权限关联表
CREATE TABLE `im_admin_role_permission` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id` varchar(100) NOT NULL COMMENT '角色ID',
    `permission_id` varchar(100) NOT NULL COMMENT '权限ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关联表';

-- -----------------------------
-- AI功能相关表
-- -----------------------------

-- AI智能对话记录表
CREATE TABLE `im_ai_chat` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `chat_id` varchar(100) NOT NULL COMMENT '对话ID',
    `user_id` varchar(100) NOT NULL COMMENT '用户ID',
    `session_id` varchar(100) NOT NULL COMMENT '会话ID',
    `message_type` tinyint NOT NULL COMMENT '消息类型：1-用户消息，2-AI回复',
    `content` text NOT NULL COMMENT '消息内容',
    `msg_id` varchar(100) DEFAULT NULL COMMENT '关联的消息ID',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_chat_id` (`chat_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_session_id` (`session_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI智能对话记录表';

-- AI知识库表
CREATE TABLE `im_ai_knowledge` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `knowledge_id` varchar(100) NOT NULL COMMENT '知识ID',
    `category` varchar(50) NOT NULL COMMENT '知识分类',
    `question` text NOT NULL COMMENT '问题',
    `answer` text NOT NULL COMMENT '答案',
    `keywords` varchar(500) DEFAULT NULL COMMENT '关键词（逗号分隔）',
    `priority` int DEFAULT 0 COMMENT '优先级（数值越大优先级越高）',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_by` varchar(100) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_knowledge_id` (`knowledge_id`),
    KEY `idx_category` (`category`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI知识库表';

-- AI配置表
CREATE TABLE `im_ai_config` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `config_key` varchar(100) NOT NULL COMMENT '配置键',
    `config_value` text NOT NULL COMMENT '配置值',
    `config_type` varchar(50) NOT NULL COMMENT '配置类型：MODEL/API/PROMPT等',
    `description` varchar(255) DEFAULT NULL COMMENT '配置描述',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `create_by` varchar(100) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_config_key` (`config_key`),
    KEY `idx_config_type` (`config_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI配置表';

-- 敏感词表
CREATE TABLE `im_sensitive_word` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `word` varchar(100) NOT NULL COMMENT '敏感词',
    `word_type` tinyint NOT NULL DEFAULT 5 COMMENT '敏感词类型：1-政治敏感，2-色情，3-暴力，4-广告，5-其他',
    `replace_word` varchar(100) DEFAULT NULL COMMENT '替换词（可选）',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-禁用，1-启用',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注',
    `create_by` varchar(100) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_word` (`word`),
    KEY `idx_word_type` (`word_type`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感词表';

-- 操作日志表
CREATE TABLE `im_admin_operation_log` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `admin_id` varchar(100) NOT NULL COMMENT '管理员ID',
    `admin_name` varchar(100) DEFAULT NULL COMMENT '管理员名称',
    `operation_type` varchar(50) NOT NULL COMMENT '操作类型：USER_DISABLE/USER_ENABLE/USER_KICK/WORD_ADD等',
    `target_type` varchar(50) DEFAULT NULL COMMENT '操作目标类型：USER/MESSAGE/WORD等',
    `target_id` varchar(100) DEFAULT NULL COMMENT '操作目标ID',
    `operation_desc` varchar(500) DEFAULT NULL COMMENT '操作描述',
    `request_ip` varchar(50) DEFAULT NULL COMMENT '请求IP',
    `request_params` text COMMENT '请求参数（JSON）',
    `response_result` varchar(50) DEFAULT NULL COMMENT '响应结果：SUCCESS/FAIL',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_admin_id` (`admin_id`),
    KEY `idx_operation_type` (`operation_type`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='管理员操作日志表';

-- 用户封禁记录表
CREATE TABLE `im_user_ban` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` varchar(100) NOT NULL COMMENT '被封禁用户ID',
    `ban_type` tinyint NOT NULL DEFAULT 1 COMMENT '封禁类型：1-账号封禁，2-IP封禁，3-设备封禁',
    `ban_value` varchar(255) DEFAULT NULL COMMENT '封禁值（IP地址或设备ID）',
    `ban_reason` varchar(500) DEFAULT NULL COMMENT '封禁原因',
    `ban_start_time` datetime NOT NULL COMMENT '封禁开始时间',
    `ban_end_time` datetime DEFAULT NULL COMMENT '封禁结束时间（NULL表示永久）',
    `ban_by` varchar(100) DEFAULT NULL COMMENT '封禁操作人',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：0-已解封，1-封禁中',
    `unban_time` datetime DEFAULT NULL COMMENT '解封时间',
    `unban_by` varchar(100) DEFAULT NULL COMMENT '解封操作人',
    `unban_reason` varchar(500) DEFAULT NULL COMMENT '解封原因',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_ban_end_time` (`ban_end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户封禁记录表';

-- 举报记录表
CREATE TABLE `im_report` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `report_id` varchar(100) NOT NULL COMMENT '举报ID',
    `reporter_id` varchar(100) NOT NULL COMMENT '举报人用户ID',
    `reported_user_id` varchar(100) NOT NULL COMMENT '被举报人用户ID',
    `report_type` tinyint NOT NULL COMMENT '举报类型：1-色情，2-欺诈，3-骚扰，4-广告，5-其他',
    `report_content` text COMMENT '举报内容描述',
    `evidence_msg_ids` text COMMENT '证据消息ID列表（JSON数组）',
    `evidence_images` text COMMENT '证据图片URL列表（JSON数组）',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '处理状态：0-待处理，1-处理中，2-已处理，3-已驳回',
    `handle_result` varchar(500) DEFAULT NULL COMMENT '处理结果',
    `handle_by` varchar(100) DEFAULT NULL COMMENT '处理人',
    `handle_time` datetime DEFAULT NULL COMMENT '处理时间',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_report_id` (`report_id`),
    KEY `idx_reporter_id` (`reporter_id`),
    KEY `idx_reported_user_id` (`reported_user_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='举报记录表';

-- 系统公告表
CREATE TABLE `im_system_notice` (
    `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
    `notice_id` varchar(100) NOT NULL COMMENT '公告ID',
    `title` varchar(200) NOT NULL COMMENT '公告标题',
    `content` text NOT NULL COMMENT '公告内容',
    `notice_type` tinyint NOT NULL DEFAULT 1 COMMENT '公告类型：1-系统公告，2-维护通知，3-活动通知',
    `target_users` varchar(50) DEFAULT 'ALL' COMMENT '目标用户：ALL-所有用户，或指定用户ID列表',
    `push_type` tinyint NOT NULL DEFAULT 1 COMMENT '推送方式：1-应用内推送，2-短信，3-邮件',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '状态：0-草稿，1-已发布，2-已撤回',
    `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
    `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
    `create_by` varchar(100) DEFAULT NULL COMMENT '创建人',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notice_id` (`notice_id`),
    KEY `idx_status` (`status`),
    KEY `idx_publish_time` (`publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统公告表';

-- 插入一些示例敏感词
INSERT INTO `im_sensitive_word` (`word`, `word_type`, `status`, `remark`) VALUES
('测试敏感词1', 5, 1, '测试用'),
('测试敏感词2', 5, 1, '测试用');

-- ================================
-- 初始化数据
-- ================================

-- -----------------------------
-- 初始化角色
-- -----------------------------
INSERT INTO `im_admin_role` (`role_id`, `role_name`, `role_code`, `description`, `status`, `create_by`) VALUES
('ROLE_SUPER_ADMIN', '超级管理员', 'SUPER_ADMIN', '拥有所有权限', 1, 'SYSTEM'),
('ROLE_ADMIN', '管理员', 'ADMIN', '普通管理员', 1, 'SYSTEM'),
('ROLE_OPERATOR', '运营人员', 'OPERATOR', '运营人员，只能查看和处理', 1, 'SYSTEM');

-- -----------------------------
-- 初始化权限
-- -----------------------------
INSERT INTO `im_admin_permission` (`permission_id`, `permission_name`, `permission_code`, `resource_type`, `parent_id`, `path`, `icon`, `sort_order`, `status`) VALUES
-- 一级菜单
('PERM_DASHBOARD', '数据看板', 'dashboard', 1, NULL, '/dashboard', 'DataBoard', 1, 1),
('PERM_USER', '用户管理', 'user', 1, NULL, '/user', 'User', 2, 1),
('PERM_FRIEND', '好友管理', 'friend', 1, NULL, '/friend', 'UserFilled', 3, 1),
('PERM_GROUP', '群组管理', 'group', 1, NULL, '/group', 'ChatDotRound', 4, 1),
('PERM_MESSAGE', '消息管理', 'message', 1, NULL, '/message', 'ChatLineSquare', 5, 1),
('PERM_SENSITIVE', '敏感词管理', 'sensitive', 1, NULL, '/sensitive', 'Warning', 6, 1),
('PERM_REPORT', '举报管理', 'report', 1, NULL, '/report', 'WarningFilled', 7, 1),
('PERM_BAN', '封禁管理', 'ban', 1, NULL, '/ban', 'Lock', 8, 1),
('PERM_NOTICE', '系统公告', 'notice', 1, NULL, '/notice', 'Bell', 9, 1),
('PERM_AI', 'AI管理', 'ai', 1, NULL, '/ai', 'MagicStick', 10, 1),
('PERM_LOG', '操作日志', 'log', 1, NULL, '/log', 'Document', 11, 1),
('PERM_ADMIN', '管理员管理', 'admin', 1, NULL, '/admin', 'UserFilled', 12, 1),
('PERM_PERMISSION', '权限管理', 'permission', 1, NULL, '/permission', 'Lock', 13, 1);

-- 二级菜单和按钮权限
INSERT INTO `im_admin_permission` (`permission_id`, `permission_name`, `permission_code`, `resource_type`, `parent_id`, `path`, `sort_order`, `status`) VALUES
-- 用户管理子权限
('PERM_USER_LIST', '用户列表', 'user:list', 2, 'PERM_USER', NULL, 1, 1),
('PERM_USER_DETAIL', '用户详情', 'user:detail', 2, 'PERM_USER', NULL, 2, 1),
('PERM_USER_DISABLE', '禁用用户', 'user:disable', 2, 'PERM_USER', NULL, 3, 1),
('PERM_USER_ENABLE', '启用用户', 'user:enable', 2, 'PERM_USER', NULL, 4, 1),
('PERM_USER_KICK', '踢下线', 'user:kick', 2, 'PERM_USER', NULL, 5, 1),
-- 好友管理子权限
('PERM_FRIEND_LIST', '好友列表', 'friend:list', 2, 'PERM_FRIEND', NULL, 1, 1),
('PERM_FRIEND_CHECK', '好友检测', 'friend:check', 2, 'PERM_FRIEND', NULL, 2, 1),
-- 群组管理子权限
('PERM_GROUP_LIST', '群组列表', 'group:list', 2, 'PERM_GROUP', NULL, 1, 1),
('PERM_GROUP_DISMISS', '解散群组', 'group:dismiss', 2, 'PERM_GROUP', NULL, 2, 1),
-- 消息管理子权限
('PERM_MESSAGE_HISTORY', '历史消息', 'message:history', 2, 'PERM_MESSAGE', NULL, 1, 1),
-- 敏感词管理子权限
('PERM_SENSITIVE_LIST', '敏感词列表', 'sensitive:list', 2, 'PERM_SENSITIVE', NULL, 1, 1),
('PERM_SENSITIVE_ADD', '添加敏感词', 'sensitive:add', 2, 'PERM_SENSITIVE', NULL, 2, 1),
('PERM_SENSITIVE_UPDATE', '更新敏感词', 'sensitive:update', 2, 'PERM_SENSITIVE', NULL, 3, 1),
('PERM_SENSITIVE_DELETE', '删除敏感词', 'sensitive:delete', 2, 'PERM_SENSITIVE', NULL, 4, 1),
-- 举报管理子权限
('PERM_REPORT_LIST', '举报列表', 'report:list', 2, 'PERM_REPORT', NULL, 1, 1),
('PERM_REPORT_HANDLE', '处理举报', 'report:handle', 2, 'PERM_REPORT', NULL, 2, 1),
-- 封禁管理子权限
('PERM_BAN_LIST', '封禁列表', 'ban:list', 2, 'PERM_BAN', NULL, 1, 1),
('PERM_BAN_USER', '封禁用户', 'ban:user', 2, 'PERM_BAN', NULL, 2, 1),
('PERM_BAN_UNBAN', '解封', 'ban:unban', 2, 'PERM_BAN', NULL, 3, 1),
-- 系统公告子权限
('PERM_NOTICE_LIST', '公告列表', 'notice:list', 2, 'PERM_NOTICE', NULL, 1, 1),
('PERM_NOTICE_ADD', '添加公告', 'notice:add', 2, 'PERM_NOTICE', NULL, 2, 1),
('PERM_NOTICE_PUBLISH', '发布公告', 'notice:publish', 2, 'PERM_NOTICE', NULL, 3, 1),
('PERM_NOTICE_REVOKE', '撤回公告', 'notice:revoke', 2, 'PERM_NOTICE', NULL, 4, 1),
-- AI管理子权限
('PERM_AI_CHAT', 'AI对话', 'ai:chat', 2, 'PERM_AI', NULL, 1, 1),
('PERM_AI_KNOWLEDGE', 'AI知识库', 'ai:knowledge', 2, 'PERM_AI', NULL, 2, 1),
('PERM_AI_CONFIG', 'AI配置', 'ai:config', 2, 'PERM_AI', NULL, 3, 1),
-- 操作日志子权限
('PERM_LOG_LIST', '日志列表', 'log:list', 2, 'PERM_LOG', NULL, 1, 1),
-- 管理员管理子权限
('PERM_ADMIN_LIST', '管理员列表', 'admin:list', 2, 'PERM_ADMIN', NULL, 1, 1),
('PERM_ADMIN_ADD', '添加管理员', 'admin:add', 2, 'PERM_ADMIN', NULL, 2, 1),
('PERM_ADMIN_UPDATE', '更新管理员', 'admin:update', 2, 'PERM_ADMIN', NULL, 3, 1),
('PERM_ADMIN_DELETE', '删除管理员', 'admin:delete', 2, 'PERM_ADMIN', NULL, 4, 1),
-- 权限管理子权限
('PERM_PERMISSION_LIST', '权限列表', 'permission:list', 2, 'PERM_PERMISSION', NULL, 1, 1),
('PERM_PERMISSION_ASSIGN', '分配权限', 'permission:assign', 2, 'PERM_PERMISSION', NULL, 2, 1);

-- -----------------------------
-- 初始化角色权限关联（超级管理员拥有所有权限）
-- -----------------------------
INSERT INTO `im_admin_role_permission` (`role_id`, `permission_id`)
SELECT 'ROLE_SUPER_ADMIN', permission_id FROM `im_admin_permission`;

-- 普通管理员权限（除管理员管理和权限管理外）
INSERT INTO `im_admin_role_permission` (`role_id`, `permission_id`)
SELECT 'ROLE_ADMIN', permission_id FROM `im_admin_permission`
WHERE permission_code NOT IN ('admin', 'permission');

-- 运营人员权限（只能查看和处理）
INSERT INTO `im_admin_role_permission` (`role_id`, `permission_id`)
SELECT 'ROLE_OPERATOR', permission_id FROM `im_admin_permission`
WHERE permission_code IN ('dashboard', 'user', 'friend', 'message', 'report');

-- -----------------------------
-- 初始化管理员账号（密码：admin123，使用BCrypt加密）
-- -----------------------------
INSERT INTO `im_admin` (`admin_id`, `username`, `password`, `real_name`, `email`, `role_id`, `status`, `create_by`) VALUES
('ADMIN001', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '超级管理员', 'admin@example.com', 1, 1, 'SYSTEM'),
('ADMIN002', 'operator', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '运营人员', 'operator@example.com', 3, 1, 'SYSTEM');

-- -----------------------------
-- 初始化AI配置
-- -----------------------------
INSERT INTO `im_ai_config` (`config_key`, `config_value`, `config_type`, `description`, `status`, `create_by`) VALUES
('ai.model.provider', 'openai', 'MODEL', 'AI模型提供商：openai/azure/anthropic等', 1, 'SYSTEM'),
('ai.model.name', 'gpt-4', 'MODEL', '使用的模型名称', 1, 'SYSTEM'),
('ai.api.key', '', 'API', 'API密钥（请在此处填写）', 1, 'SYSTEM'),
('ai.api.url', 'https://api.openai.com/v1/chat/completions', 'API', 'API地址', 1, 'SYSTEM'),
('ai.prompt.system', '你是一个智能客服助手，请友好、专业地回答用户问题。', 'PROMPT', '系统提示词', 1, 'SYSTEM'),
('ai.chat.max_tokens', '2000', 'MODEL', '最大token数', 1, 'SYSTEM'),
('ai.chat.temperature', '0.7', 'MODEL', '温度参数（0-2，越高越随机）', 1, 'SYSTEM'),
('ai.knowledge.enabled', 'true', 'CONFIG', '是否启用知识库', 1, 'SYSTEM');

-- -----------------------------
-- 初始化AI知识库示例数据
-- -----------------------------
INSERT INTO `im_ai_knowledge` (`knowledge_id`, `category`, `question`, `answer`, `keywords`, `priority`, `status`, `create_by`) VALUES
('KB001', '常见问题', '如何注册账号？', '您可以通过以下方式注册账号：\n1. 点击APP首页的"注册"按钮\n2. 输入手机号\n3. 获取并输入验证码\n4. 设置登录密码\n5. 完成注册', '注册,账号,signup', 10, 1, 'SYSTEM'),
('KB002', '常见问题', '如何修改密码？', '修改密码步骤：\n1. 进入"设置"页面\n2. 点击"账号与安全"\n3. 选择"修改密码"\n4. 输入原密码和新密码\n5. 确认修改', '修改密码,重置密码,忘记密码', 10, 1, 'SYSTEM'),
('KB003', '常见问题', '如何添加好友？', '添加好友的方法：\n1. 知道对方账号：点击"+"号，输入对方账号搜索\n2. 扫二维码：点击扫一扫，扫描对方二维码\n3. 通讯录推荐：在"推荐好友"中查看', '添加好友,好友,联系', 10, 1, 'SYSTEM'),
('KB004', '功能使用', '如何创建群聊？', '创建群聊步骤：\n1. 点击消息页面的"+"号\n2. 选择"发起群聊"\n3. 选择要邀请的好友（至少2人）\n4. 设置群名称和群头像\n5. 点击"创建"完成', '群聊,建群,群组', 10, 1, 'SYSTEM'),
('KB005', '功能使用', '消息发送失败怎么办？', '消息发送失败解决方法：\n1. 检查网络连接是否正常\n2. 确认对方账号是否正常\n3. 尝试重新发送消息\n4. 如果仍失败，可能是服务器繁忙，请稍后重试\n5. 联系客服获取帮助', '发送失败,消息问题,故障', 10, 1, 'SYSTEM'),
('KB006', '账户安全', '账号被禁用了怎么办？', '如果账号被禁用，可能的原因：\n1. 违反了用户协议或社区规范\n2. 被其他用户举报并核实\n3. 系统检测到异常行为\n\n解封方法：\n1. 登录官网查看禁用原因\n2. 如果是误封，可提交申诉\n3. 联系客服：support@example.com\n4. 等待人工审核处理', '封号,禁用,申诉,解封', 10, 1, 'SYSTEM');
