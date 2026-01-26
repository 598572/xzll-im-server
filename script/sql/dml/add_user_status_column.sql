-- =====================================================
-- 用户管理功能 - 添加用户状态字段
-- 创建时间: 2026-01-26
-- 说明: 为 im_user 表添加 status 字段以支持用户禁用/启用功能
-- =====================================================

-- 使用数据库
USE xzll_im_db_0;

-- 添加用户状态字段
ALTER TABLE im_user
ADD COLUMN status tinyint NOT NULL DEFAULT 0 COMMENT '用户状态：0-正常，1-禁用' AFTER register_terminal_type;

-- 为已有数据设置默认值（确保所有现有用户状态为正常）
UPDATE im_user SET status = 0 WHERE status IS NULL;

-- 添加索引以提高查询性能
CREATE INDEX idx_status ON im_user(status);

-- =====================================================
-- 说明：
-- 1. status = 0 表示用户状态正常，可以正常使用系统
-- 2. status = 1 表示用户被禁用，无法登录和使用系统
-- 3. 禁用用户后，系统会自动踢用户下线
-- 4. 所有操作都会记录到 im_admin_operation_log 表
-- =====================================================
