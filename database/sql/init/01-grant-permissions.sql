-- ============================================
-- 文件用途：创建应用账户 blog_user 并授予最小权限
-- 执行方式：root 登录后 source 此文件
-- 安全要求：执行前必须将 <CHANGE_ME_DB_PASSWORD> 替换为强密码
-- 说明：仅用于手动授权；若通过 Docker 环境变量自动建库建用户，可跳过本文件
-- ============================================

-- 创建用户（如已存在则跳过）
CREATE USER IF NOT EXISTS 'blog_user'@'%' IDENTIFIED BY '<CHANGE_ME_DB_PASSWORD>';

-- 只授予 DML 权限（最小权限原则）
GRANT SELECT, INSERT, UPDATE, DELETE
  ON weblog.* TO 'blog_user'@'%';

FLUSH PRIVILEGES;
