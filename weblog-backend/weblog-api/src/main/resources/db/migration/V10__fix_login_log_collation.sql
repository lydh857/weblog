-- ============================================
-- 文件用途：统一 t_login_log / t_login_log_archive 排序规则为 utf8mb4_0900_ai_ci
-- 背景：这两张表原本使用 utf8mb4_unicode_ci，而其他表均使用 utf8mb4_0900_ai_ci
--       跨排序规则 JOIN 会触发 MySQL 报错 "Illegal mix of collations"
-- 使用方式：Flyway V9 增量迁移，在生产环境需在维护窗口内执行
-- ============================================

-- 逐个 ALTER COLUMN，避免 MODIFY 丢失其他属性；仅改 COLLATE
ALTER TABLE `t_login_log`
  MODIFY COLUMN `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户邮箱',
  MODIFY COLUMN `login_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '登录类型: user(用户端), admin(管理端)',
  MODIFY COLUMN `result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '登录结果: success, failed',
  MODIFY COLUMN `fail_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败原因',
  MODIFY COLUMN `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端IP',
  MODIFY COLUMN `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'User-Agent',
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

ALTER TABLE `t_login_log_archive`
  MODIFY COLUMN `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用户邮箱',
  MODIFY COLUMN `login_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '登录类型: user(用户端), admin(管理端)',
  MODIFY COLUMN `result` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '登录结果: success, failed',
  MODIFY COLUMN `fail_reason` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '失败原因',
  MODIFY COLUMN `ip` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端IP',
  MODIFY COLUMN `user_agent` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'User-Agent',
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
