INSERT INTO `t_system_config` (`config_key`, `config_value`, `description`)
SELECT 'review_action_base_url', 'http://localhost:3000', '邮件快捷审核链接基础地址'
WHERE NOT EXISTS (SELECT 1 FROM `t_system_config` WHERE `config_key` = 'review_action_base_url');
