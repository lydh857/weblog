-- V17: 开源准备 — 统一种子数据为通用默认值
-- 对新安装的数据库是 no-op（V2 已使用通用值），对历史数据库覆盖为通用值

-- 1. 系统配置：审核链接基础地址
UPDATE `t_system_config`
SET `config_value` = 'http://localhost:3000'
WHERE `config_key` = 'review_action_base_url';

-- 2. 系统配置：评论审核链接基础地址
UPDATE `t_system_config`
SET `config_value` = 'http://localhost:3000'
WHERE `config_key` = 'comment_review_base_url';

-- 3. 系统配置：站点名称
UPDATE `t_system_config`
SET `config_value` = 'Weblog'
WHERE `config_key` = 'site_name';

-- 4. 系统配置：页脚版权
UPDATE `t_system_config`
SET `config_value` = '© 2026 Weblog. All rights reserved.'
WHERE `config_key` = 'site_footer_copyright';

-- 5. 系统配置：发件人名称
UPDATE `t_system_config`
SET `config_value` = 'Weblog'
WHERE `config_key` = 'mail_from_name';

-- 6. 系统配置：发件邮箱
UPDATE `t_system_config`
SET `config_value` = 'noreply@blog.test'
WHERE `config_key` = 'mail_username'
  AND `config_value` LIKE '%@%';

-- 7. 广告位标识：将以数字ID开头的标题统一为 demo 前缀
UPDATE `t_advertisement`
SET `title` = CONCAT('demo-', SUBSTRING_INDEX(`title`, '-', -1))
WHERE `title` REGEXP '^[0-9]+-';

-- 8. 登录日志：统一邮箱为测试值
UPDATE `t_login_log`
SET `email` = 'admin@blog.test'
WHERE `email` NOT IN ('admin@blog.test', 'user1@blog.test', 'user2@blog.test')
  AND `email` LIKE '%@%.%';

-- 9. 用户表：用 ID 生成唯一测试邮箱，避免 email 唯一约束冲突
UPDATE `t_user`
SET `email` = CASE
    WHEN `id` = 1 THEN 'admin@blog.test'
    ELSE CONCAT('user', `id`, '@blog.test')
END
WHERE `email` NOT LIKE '%@blog.test'
  AND `email` LIKE '%@%.%';
