INSERT INTO `t_system_config` (`config_key`, `config_value`, `description`)
SELECT 'allowed_email_domains', 'qq.com,foxmail.com,163.com,126.com,yeah.net,gmail.com,outlook.com,hotmail.com,icloud.com,sina.com,aliyun.com', '允许注册/绑定的邮箱后缀白名单（逗号分隔）'
WHERE NOT EXISTS (SELECT 1 FROM `t_system_config` WHERE `config_key` = 'allowed_email_domains');
