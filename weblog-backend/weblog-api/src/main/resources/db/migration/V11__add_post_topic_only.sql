ALTER TABLE `t_post`
  ADD COLUMN `topic_only` tinyint(1) NULL DEFAULT 0 COMMENT '是否仅在专题内展示' AFTER `is_top`;

CREATE INDEX `idx_topic_only` ON `t_post` (`topic_only`);
