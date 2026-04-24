DROP TABLE IF EXISTS `t_ai_comment_review`;

DROP TABLE IF EXISTS `t_ai_chat_log`;

DELETE FROM `t_ai_prompt_template`
WHERE `template_key` = 'comment_review';

DELETE FROM `t_ai_token_log`
WHERE `feature` = 'commentReview';

ALTER TABLE `t_ai_config`
  DROP COLUMN `feature_comment_review`;

ALTER TABLE `t_comment`
  DROP INDEX `idx_ai_review`,
  DROP COLUMN `ai_review_status`,
  DROP COLUMN `ai_review_reason`;
