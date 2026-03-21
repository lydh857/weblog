-- 下线 AI 语义向量推荐（数据库清理脚本）
-- 执行前请确认当前数据库为 weblog

USE weblog;

-- 1) 删除向量表
DROP TABLE IF EXISTS t_post_embedding;

-- 2) 删除 AI 配置中的向量/推荐字段
ALTER TABLE t_ai_config
  DROP COLUMN embedding_model,
  DROP COLUMN feature_recommend;

-- 3) 清理语义推荐历史 token 统计
DELETE FROM t_ai_token_log
WHERE feature = 'recommend';
