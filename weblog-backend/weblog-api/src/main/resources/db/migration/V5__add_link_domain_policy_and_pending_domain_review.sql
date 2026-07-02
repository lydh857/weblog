ALTER TABLE t_advertisement
  MODIFY COLUMN status ENUM('pending','pending_domain_review','approved','rejected','active','expired')
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'pending' COMMENT '状态';

ALTER TABLE t_friend_link
  MODIFY COLUMN status ENUM('active','inactive','broken','pending','pending_domain_review','rejected')
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT 'active' COMMENT '状态: active/inactive/broken/pending/pending_domain_review/rejected';

CREATE TABLE IF NOT EXISTS t_link_domain_policy (
  id BIGINT NOT NULL AUTO_INCREMENT,
  domain VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '归一化域名',
  status ENUM('pending','trusted','blocked') CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '策略状态',
  source VARCHAR(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'auto' COMMENT '来源: auto/seed/manual',
  reviewer VARCHAR(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核人',
  review_reason VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '审核备注',
  expire_at DATETIME NULL DEFAULT NULL COMMENT '过期时间（NULL 表示不过期）',
  create_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id) USING BTREE,
  UNIQUE KEY uk_domain (domain),
  KEY idx_status_expire (status, expire_at)
) ENGINE=InnoDB CHARACTER SET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='外链域名治理策略表' ROW_FORMAT=Dynamic;
