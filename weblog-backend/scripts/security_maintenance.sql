-- zhhhkl 安全与性能维护脚本（MySQL 8.0）
-- 执行前请先备份数据库。
-- 建议在业务低峰期执行，并先在测试环境验证。

USE weblog;

-- ============================================================
-- A. 立即风险收敛：清理演示/泄露风险数据
-- ============================================================

-- 1) 清空 AI 配置中的 API Key（改为从环境变量注入）
UPDATE t_ai_config
SET api_key = ''
WHERE id IS NOT NULL;

-- 2) 清空系统配置中的邮箱授权码
UPDATE t_system_config
SET config_value = ''
WHERE config_key = 'mail_password';

-- 3) 可选：清理 Remember Token 历史记录（建议）
-- DELETE FROM t_remember_token;

-- 4) 一次性迁移 Remember Token 历史明文为 SHA-256（建议先备份）
-- 说明：当前新逻辑优先按哈希查询；该迁移完成后可在生产关闭明文兼容开关。
UPDATE t_remember_token
SET token = LOWER(SHA2(token, 256))
WHERE token IS NOT NULL
  AND CHAR_LENGTH(token) <> 64;

-- ============================================================
-- B. 冗余索引瘦身（降低写放大与维护开销）
-- ============================================================

-- t_user: email 唯一索引重复
ALTER TABLE t_user DROP INDEX email_2;
ALTER TABLE t_user DROP INDEX idx_email;

-- t_post: slug 唯一索引已覆盖等值查询
ALTER TABLE t_post DROP INDEX idx_slug;

-- t_system_config: config_key 唯一索引已覆盖
ALTER TABLE t_system_config DROP INDEX idx_key;

-- ============================================================
-- C. 运营日志生命周期（控制表增长）
-- ============================================================

-- 登录日志保留 90 天
DELETE FROM t_login_log
WHERE create_time < DATE_SUB(NOW(), INTERVAL 90 DAY);

-- 审计日志保留 180 天
DELETE FROM t_audit_log
WHERE create_time < DATE_SUB(NOW(), INTERVAL 180 DAY);

-- ============================================================
-- D. 日志查询性能增强（后台筛选）
-- ============================================================

CREATE INDEX idx_login_type_result_time
  ON t_login_log(login_type, result, create_time);

CREATE INDEX idx_audit_module_op_time
  ON t_audit_log(module, operation, create_time);
