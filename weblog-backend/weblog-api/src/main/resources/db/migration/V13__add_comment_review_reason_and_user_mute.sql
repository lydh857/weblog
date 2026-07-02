ALTER TABLE `t_comment`
  ADD COLUMN `reject_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '拒绝原因' AFTER `status`;

ALTER TABLE `t_user`
  ADD COLUMN `muted_permanent` tinyint(1) NULL DEFAULT 0 COMMENT '是否永久禁言' AFTER `lock_until`,
  ADD COLUMN `muted_until` datetime NULL DEFAULT NULL COMMENT '禁言截止时间',
  ADD COLUMN `muted_reason` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '禁言原因';

INSERT INTO `t_system_config` (`config_key`, `config_value`, `description`)
SELECT 'comment_review_base_url', 'http://localhost:3000', '评论审核邮件快捷链接基础地址'
WHERE NOT EXISTS (SELECT 1 FROM `t_system_config` WHERE `config_key` = 'comment_review_base_url');
