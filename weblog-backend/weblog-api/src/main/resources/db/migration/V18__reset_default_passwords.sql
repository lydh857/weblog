-- V18: 开源准备 — 重置所有种子用户密码为统一默认值 Weblog@123
-- 对新安装的数据库是 no-op（V2 已使用统一哈希），对历史数据库覆盖为默认密码

UPDATE `t_user`
SET `password` = '$2b$12$RWmW0MtwpzcT/u/b5tQZX.qsl99F1H2mQY3R54eDHgkoI2eS6FEGe'
WHERE `id` IN (1, 2, 3, 4, 5, 6, 18, 23, 24);
