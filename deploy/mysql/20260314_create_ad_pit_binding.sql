CREATE TABLE IF NOT EXISTS `t_ad_pit_binding` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `apply_ad_id` bigint NOT NULL COMMENT '申请广告ID（用户广告）',
  `pit_ad_id` bigint NOT NULL COMMENT '坑位广告ID（管理员广告）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_apply_ad_id` (`apply_ad_id`),
  UNIQUE KEY `uk_pit_ad_id` (`pit_ad_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='广告申请坑位占用';

SET @pit_bindings_json = (
  SELECT `config_value`
  FROM `t_system_config`
  WHERE `config_key` = 'ad_apply_pit_bindings'
  LIMIT 1
);

SET @pit_bindings_json = IFNULL(NULLIF(@pit_bindings_json, ''), '{}');

INSERT INTO `t_ad_pit_binding` (`apply_ad_id`, `pit_ad_id`)
SELECT
  CAST(k.`apply_ad_id` AS UNSIGNED) AS `apply_ad_id`,
  CAST(v.`pit_ad_id_raw` AS UNSIGNED) AS `pit_ad_id`
FROM JSON_TABLE(
  JSON_KEYS(@pit_bindings_json),
  '$[*]' COLUMNS (
    `ord` FOR ORDINALITY,
    `apply_ad_id` varchar(32) PATH '$'
  )
) AS k
JOIN JSON_TABLE(
  JSON_EXTRACT(@pit_bindings_json, '$.*'),
  '$[*]' COLUMNS (
    `ord` FOR ORDINALITY,
    `pit_ad_id_raw` varchar(32) PATH '$'
  )
) AS v ON v.`ord` = k.`ord`
WHERE CAST(k.`apply_ad_id` AS UNSIGNED) > 0
  AND CAST(v.`pit_ad_id_raw` AS UNSIGNED) > 0
ON DUPLICATE KEY UPDATE
  `pit_ad_id` = VALUES(`pit_ad_id`),
  `update_time` = CURRENT_TIMESTAMP;
