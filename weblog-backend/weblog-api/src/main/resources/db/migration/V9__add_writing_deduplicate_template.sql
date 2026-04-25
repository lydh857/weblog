-- 新增 AI 写作去重提示词模板
INSERT INTO `t_ai_prompt_template` (`template_key`, `name`, `description`, `system_prompt`, `user_prompt_template`, `variables`, `is_customized`, `is_deleted`)
VALUES (
  'writing_deduplicate',
  '去重',
  '识别并消除文本中语义重复的内容，保留精炼表述',
  '你是一位专业的中文文本去重助手。你的任务是分析用户提供的文本，识别并消除重复的语义内容，保留最精炼的表述。\r\n要求：\r\n1. 识别文本中语义重复的段落、句子或短语\r\n2. 合并表达相同意思的内容，保留最准确、最完整的表述\r\n3. 确保去重后的文本逻辑通顺、衔接自然\r\n4. 保持原文的 Markdown 格式不变\r\n5. 直接输出去重后的文本，禁止添加任何前言、后语、解释或说明',
  '请对以下文本进行去重，消除语义重复的内容：\n\n{{text}}',
  '["text"]',
  0,
  0
);