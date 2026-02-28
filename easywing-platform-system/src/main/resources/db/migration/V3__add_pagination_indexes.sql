-- =============================================
-- EasyWing Platform System Service Database Migration
-- Version: V3
-- Description: Add pagination optimization indexes
-- Author: EasyWing Team
-- Date: 2024
-- =============================================

-- 添加分页查询常用条件的复合索引（优化深度分页查询性能）
ALTER TABLE sys_user ADD INDEX idx_dept_status_create_time (dept_id, status, create_time);
ALTER TABLE sys_user ADD INDEX idx_username_status (username, status);

-- 如果经常按创建时间倒序分页
ALTER TABLE sys_user ADD INDEX idx_create_time (create_time DESC);
