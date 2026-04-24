# Weblog 安全与性能待优化清单（不改变功能与样式）

## 范围与约束

- 范围：`weblog-backend`、`weblog-user`、`weblog-admin`、`database/sql/init/02-schema.sql`
- 目标：提升上线安全基线与高并发稳定性，不改变现有业务功能和 UI 样式
- 原则：优先做参数边界、索引与查询、限流与日志治理、配置硬化；避免改动交互语义

## 完成状态（2026-04-04）

- P0：1/2/3/4 已完成
- P1：5/6/7/8 已完成
- P2：9/10 已完成（以“巡检+告警+治理入口”方式落地，不做破坏性在线改表）

## P0（上线前必须完成）

### 1) 验证令牌消费需改为原子操作（防重放窗口）

- 证据：`weblog-backend/weblog-infra-captcha/src/main/java/com/blog/infra/captcha/service/CaptchaServiceImpl.java:210` 先 `get`，`weblog-backend/weblog-infra-captcha/src/main/java/com/blog/infra/captcha/service/CaptchaServiceImpl.java:216` 再 `delete`
- 风险：并发下同一 `verifyToken` 可能在极短时间内被重复消费
- 优化建议：将 token 校验改为 Redis 原子“取并删”（Lua 或 `GETDEL` 语义封装）
- 验收：压测并发重复提交同一 token，成功消费次数必须恒为 1

### 2) 登录失败热点 IP 统计查询补齐匹配索引

- 证据：`weblog-backend/weblog-module-system/src/main/java/com/blog/system/mapper/LoginLogMapper.java:20`~`29` 按 `result + create_time` 过滤并 `GROUP BY ip`
- 现状：`database/sql/init/02-schema.sql:341` 仅有 `idx_login_type_result_time(login_type, result, create_time)`，不覆盖 `ip` 聚合路径
- 风险：登录失败激增时扫描放大，告警任务抖动，影响实时性
- 优化建议：新增复合索引（建议）`(result, create_time, ip)`，保留现有索引用于其它查询
- 验收：`EXPLAIN` 命中新增索引，扫描行数显著下降；告警任务执行时间稳定

### 3) 前端 CSP 默认阶段改为生产强制生效

- 证据：`weblog-user/nuxt.config.ts:16` 与 `weblog-admin/nuxt.config.ts:15` 默认 `NUXT_CSP_STAGE` 为 `report-only`
- 风险：生产环境若未显式覆盖，CSP 仅上报不拦截，XSS 防护强度不足
- 优化建议：生产环境配置强制 `enforce`（或 `dual` 短期过渡），并在发布流程加入校验
- 验收：生产响应头包含 `Content-Security-Policy`，且关键页面无阻断误报

### 4) SQL 注释与字符集编码异常需统一清理

- 证据：`database/sql/init/02-schema.sql:638`~`653`、`database/sql/init/02-schema.sql:357` 出现注释乱码（如 `ç”¨æˆ·ID`）
- 风险：运维审计、数据字典与迁移脚本可读性下降，易引入误操作
- 优化建议：统一以 UTF-8 重新导出并修复异常注释，确保 `weblog.sql` 与 `init` 脚本一致
- 验收：全文无乱码注释，初始化后 `SHOW CREATE TABLE` 注释可读

## P1（高收益，建议近期完成）

### 5) 标签筛选查询避免“先全量取 ID 再 IN”

- 状态：已完成
- 落地：`weblog-backend/weblog-module-content/src/main/java/com/blog/content/service/PostService.java:346` 使用 `EXISTS` 子查询在数据库侧过滤 tag

- 证据：`weblog-backend/weblog-module-content/src/main/java/com/blog/content/service/PostService.java:331`~`334` 先查 `postIds`，再在 `PostService.java:359` 执行 `IN`
- 风险：大标签场景会产生超长 `IN` 列表，SQL 体积和内存占用放大
- 优化建议：改为 `JOIN/EXISTS` 或分批 ID 子查询；保持返回结果与排序逻辑不变
- 验收：大标签压测下 SQL 长度、响应耗时、JVM 内存占用下降

### 6) 安全日志表引入归档策略（仅保留热数据）

- 状态：已完成
- 落地：
  - 归档清理流程：`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/SecurityLogCleanupScheduler.java:273`、`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/SecurityLogCleanupScheduler.java:324`
  - 归档表结构：`database/sql/init/02-schema.sql:214`、`database/sql/init/02-schema.sql:376`（并同步到 `database/weblog.sql`）
  - 可配置开关/批次：`weblog-backend/weblog-api/src/main/resources/application.yml:139`

- 证据：`SecurityLogCleanupScheduler` 已做按天删除，见 `weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/SecurityLogCleanupScheduler.java:72` 与 `:168`
- 现状：`t_login_log`、`t_audit_log` 当前以“定期删除”为主，长期审计与查询性能目标冲突
- 优化建议：按月分表或冷归档（对象存储/归档库），在线库保留近期窗口
- 验收：在线库数据量稳定，后台查询与定时任务耗时稳定

### 7) 高增长统计表补充容量治理

- 状态：已完成
- 落地：`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/DataGovernanceScheduler.java:140` 对 `t_user_view_log`、`t_post_ranking`、`t_post_read_daily` 执行保留清理，并在 `DataGovernanceScheduler.java:173` 做容量阈值告警

- 证据：`t_post_ranking` 自增已较高（`database/sql/init/02-schema.sql:442`），`t_user_view_log` 为持续增长日志表（`database/sql/init/02-schema.sql:656`~`675`）
- 风险：索引膨胀、备份与恢复窗口拉长
- 优化建议：建立“保留周期 + 归档 + 清理审计”三段式策略；对读多写多表做定期索引健康检查
- 验收：表体量与索引体量可预测增长，备份窗口可控

### 8) 安全告警链路增加慢查询观测与阈值自校准

- 状态：已完成
- 落地：
  - 慢查询观测：`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/SecurityLogCleanupScheduler.java:118`、`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/SecurityLogCleanupScheduler.java:137`
  - 阈值自校准：`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/SecurityLogCleanupScheduler.java:206`
  - 配置项：`weblog-backend/weblog-api/src/main/resources/application-prod.yml:117`

- 证据：攻击告警依赖 `countFailedLoginsSince` 与 `listTopFailedIps`（`SecurityLogCleanupScheduler.java:89`、`:99`）
- 风险：流量突增或索引偏移时，告警可能延迟或漏发
- 优化建议：为该链路添加执行耗时指标、慢查询日志标签、阈值自适应观察面板
- 验收：可观测平台可定位瓶颈，告警延迟在目标窗口内

## P2（中长期治理）

### 9) 统一日志表与业务表字符集/排序规则

- 状态：已完成（先治理入口）
- 落地：`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/DataGovernanceScheduler.java:201` 增加字符集基线巡检与告警，基线配置在 `application*.yml` 的 `blog.security.data-governance.collation-baseline`

- 证据：`t_login_log` 使用 `utf8mb4_unicode_ci`（`database/sql/init/02-schema.sql:328`~`342`），多数表为 `utf8mb4_0900_ai_ci`
- 风险：跨表联查与排序行为存在潜在差异，迁移与诊断复杂度上升
- 优化建议：制定统一 collation 基线，在低峰窗口逐表校准
- 验收：核心表 collation 与字符集基线一致

### 10) 评估关键关系是否需要弱约束校验机制

- 状态：已完成
- 落地：`weblog-backend/weblog-api/src/main/java/com/blog/api/scheduler/DataGovernanceScheduler.java:190` 增加弱约束孤儿数据巡检（post_tag / user_view_log / post_ranking / comment 等）并告警

- 证据：`database/sql/init/02-schema.sql` 未定义外键约束（全文件未出现 `FOREIGN KEY`）
- 风险：应用层异常或批处理失误可能积累脏引用数据
- 优化建议：不强制引入数据库外键；增加离线一致性巡检任务与告警
- 验收：巡检任务可定期输出孤儿数据并闭环处理

## 实施顺序建议（不改功能/样式）

1. 先做 P0-1、P0-2（最直接降低安全与稳定性风险）
2. 同步完成 P0-3、P0-4（上线配置与数据字典可维护性）
3. 推进 P1-5、P1-6、P1-7（吞吐与容量治理）
4. 最后纳入 P2 治理项进入季度计划

## 变更边界声明

- 本清单所有项均限定在：后端校验/查询/索引/任务/配置层面
- 明确不涉及：页面样式改造、交互流程改造、业务功能语义变更
