ALTER TABLE t_ip_blacklist
  ADD COLUMN block_type VARCHAR(20) NULL COMMENT '封禁类型: IP/USER' AFTER id,
  ADD COLUMN target_value VARCHAR(128) NULL COMMENT '封禁对象值: IP或用户ID' AFTER block_type,
  ADD COLUMN user_id BIGINT NULL COMMENT '封禁用户ID(仅USER类型)' AFTER target_value,
  ADD COLUMN subject VARCHAR(255) NULL COMMENT '封禁对象展示信息' AFTER user_id,
  ADD COLUMN update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER create_time;

UPDATE t_ip_blacklist
SET block_type = 'IP'
WHERE block_type IS NULL OR TRIM(block_type) = '';

UPDATE t_ip_blacklist
SET target_value = ip_address
WHERE target_value IS NULL OR TRIM(target_value) = '';

UPDATE t_ip_blacklist
SET subject = ip_address
WHERE (subject IS NULL OR TRIM(subject) = '')
  AND ip_address IS NOT NULL
  AND TRIM(ip_address) <> '';

ALTER TABLE t_ip_blacklist
  MODIFY COLUMN block_type VARCHAR(20) NOT NULL COMMENT '封禁类型: IP/USER',
  MODIFY COLUMN target_value VARCHAR(128) NOT NULL COMMENT '封禁对象值: IP或用户ID';

CREATE UNIQUE INDEX uk_block_type_target ON t_ip_blacklist (block_type, target_value);
CREATE INDEX idx_block_type_expire ON t_ip_blacklist (block_type, expire_time);
CREATE INDEX idx_user_id ON t_ip_blacklist (user_id);
