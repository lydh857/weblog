ALTER TABLE `t_oss_resource`
  MODIFY COLUMN `usage_type` enum('post','avatar','ad','other','content','cover','carousel','ad_apply')
  DEFAULT 'other' COMMENT '用途';
