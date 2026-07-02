-- V19: 修复 t_user.password 默认值 + 清理 t_post 冗余索引

-- 1. 将 t_user.password 默认值从空字符串改为 NULL
--    空字符串语义不明确（"密码为空" vs "未设置密码"），NULL 明确表示未设置密码（如 OAuth 用户）
UPDATE `t_user` SET `password` = NULL WHERE `password` = '';
ALTER TABLE `t_user` MODIFY COLUMN `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'BCrypt加密密码（NULL表示未设置密码，如OAuth用户）';

-- 2. 清理 t_post 表冗余索引
--    idx_status 仅索引 (status)，被复合索引 idx_post_deleted_status_time (is_deleted, status, create_time) 覆盖
--    由于 MyBatis-Plus @TableLogic 自动追加 is_deleted = 0 条件，所有按 status 过滤的查询都会使用复合索引
ALTER TABLE `t_post` DROP INDEX `idx_status`;

--    idx_published 仅索引 (is_published, status)，被复合索引 idx_post_deleted_published_status_time (is_deleted, is_published, status, create_time) 覆盖
ALTER TABLE `t_post` DROP INDEX `idx_published`;
