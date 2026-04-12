-- 用途：一次性回填历史审计日志用户名
-- 说明：仅回填 t_audit_log 中 username 为空且 user_id 不为空的数据
-- 回填规则：优先昵称，其次邮箱，最后回退为 UID:<user_id>

START TRANSACTION;

UPDATE t_audit_log a
LEFT JOIN t_user u ON u.id = a.user_id
SET a.username = CASE
  WHEN a.user_id IS NULL THEN NULL
  WHEN u.id IS NULL THEN CONCAT('UID:', a.user_id)
  WHEN u.nickname IS NOT NULL AND TRIM(u.nickname) <> '' THEN u.nickname
  WHEN u.email IS NOT NULL AND TRIM(u.email) <> '' THEN u.email
  ELSE CONCAT('UID:', a.user_id)
END
WHERE (a.username IS NULL OR TRIM(a.username) = '')
  AND a.user_id IS NOT NULL;

-- 可选：如果存在归档表，则同步回填归档审计日志用户名
SET @has_archive := (
  SELECT COUNT(*)
  FROM information_schema.tables
  WHERE table_schema = DATABASE()
    AND table_name = 't_audit_log_archive'
);

SET @archive_sql := IF(
  @has_archive > 0,
  'UPDATE t_audit_log_archive a LEFT JOIN t_user u ON u.id = a.user_id SET a.username = CASE WHEN a.user_id IS NULL THEN NULL WHEN u.id IS NULL THEN CONCAT(\'UID:\', a.user_id) WHEN u.nickname IS NOT NULL AND TRIM(u.nickname) <> \'\' THEN u.nickname WHEN u.email IS NOT NULL AND TRIM(u.email) <> \'\' THEN u.email ELSE CONCAT(\'UID:\', a.user_id) END WHERE (a.username IS NULL OR TRIM(a.username) = \'\') AND a.user_id IS NOT NULL',
  'SELECT 1'
);

PREPARE stmt_backfill_archive FROM @archive_sql;
EXECUTE stmt_backfill_archive;
DEALLOCATE PREPARE stmt_backfill_archive;

COMMIT;

-- 校验：仍然缺失用户名的记录数（理论应为 0）
SELECT COUNT(*) AS remaining_empty_username
FROM t_audit_log
WHERE user_id IS NOT NULL
  AND (username IS NULL OR TRIM(username) = '');
