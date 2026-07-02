ALTER TABLE `t_ip_blacklist`
  MODIFY COLUMN `block_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '封禁类型: IP/USER/MUTE',
  MODIFY COLUMN `target_value` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '封禁对象值: IP或用户ID',
  MODIFY COLUMN `user_id` bigint NULL DEFAULT NULL COMMENT '封禁用户ID(USER/MUTE类型)',
  COMMENT = '统一黑名单表（IP/用户/禁言）';

INSERT INTO `t_ip_blacklist` (`block_type`, `target_value`, `user_id`, `subject`, `ip_address`, `reason`, `expire_time`, `create_time`, `update_time`)
SELECT
  'MUTE',
  CAST(u.`id` AS CHAR),
  u.`id`,
  COALESCE(NULLIF(u.`email`, ''), CONCAT('UID:', u.`id`)),
  NULL,
  COALESCE(NULLIF(u.`muted_reason`, ''), 'manual_mute'),
  CASE WHEN u.`muted_permanent` = 1 THEN NULL ELSE u.`muted_until` END,
  COALESCE(u.`update_time`, u.`create_time`, CURRENT_TIMESTAMP),
  COALESCE(u.`update_time`, u.`create_time`, CURRENT_TIMESTAMP)
FROM `t_user` u
WHERE (u.`muted_permanent` = 1 OR u.`muted_until` > CURRENT_TIMESTAMP)
  AND NOT EXISTS (
    SELECT 1
    FROM `t_ip_blacklist` b
    WHERE b.`block_type` = 'MUTE'
      AND b.`target_value` = CAST(u.`id` AS CHAR)
  );
