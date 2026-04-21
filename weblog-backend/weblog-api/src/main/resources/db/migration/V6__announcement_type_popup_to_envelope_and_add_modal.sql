-- 公告类型升级：popup -> envelope，并新增 modal 类型
-- 注意：需先扩容枚举再更新数据，避免 strict mode 下“Data truncated”
ALTER TABLE `t_announcement`
MODIFY COLUMN `type` enum('popup','envelope','modal','banner') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'banner' COMMENT '公告类型';

UPDATE `t_announcement`
SET `type` = 'envelope'
WHERE `type` = 'popup';

ALTER TABLE `t_announcement`
MODIFY COLUMN `type` enum('envelope','modal','banner') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'banner' COMMENT '公告类型';
