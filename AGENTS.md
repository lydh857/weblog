# AGENTS.md

本文件为仓库内自动化编码代理提供执行规范，覆盖构建/测试命令与代码风格约束。

## 1. 项目快照

- 项目：Weblog（全栈博客系统）
- 后端：Java 21 + Spring Boot 3.4.13 + MyBatis-Plus + Sa-Token + Lucene
- 前端：Nuxt 4（`weblog-user` SSR，`weblog-admin` SPA）
- 数据库：MySQL 8.0，缓存：Redis 7.2
- 关键端口：API `9091`，用户端 `3000`，管理端 `3001`，MySQL 映射 `3307`，Redis 映射 `6380`

## 2. Cursor / Copilot 规则探测

仓库内未发现以下文件（已核查）：`.cursor/rules/`、`.cursorrules`、`.github/copilot-instructions.md`。
结论：当前仅遵循本 `AGENTS.md` 与仓库既有代码约定。

规则优先级：仓库内本文件高于全局规则；如存在冲突，以本文件为准。

## 3. 目录结构（核心）

- `weblog-backend/`：Maven 多模块后端
- `weblog-user/`：Nuxt SSR 用户端
- `weblog-admin/`：Nuxt SPA 管理端
- `database/sql/init/`：初始化 SQL（结构 + 数据）
- `database/weblog.sql`：当前系统完整快照（事实源）
- `scripts/`：CI/回归脚本
- `.github/workflows/`：CI 工作流

## 4. 构建 / 运行 / 测试命令

### 4.1 基础依赖

```bash
docker compose up -d
```

### 4.2 后端（Maven）

```bash
# 全量构建（跳过测试）
mvn -f weblog-backend/pom.xml clean install -DskipTests

# 启动 API 聚合模块
mvn -f weblog-backend/pom.xml -pl weblog-api -am spring-boot:run -DskipTests

# 打包 API（CI 常用）
mvn -f weblog-backend/pom.xml -pl weblog-api -am -Dmaven.test.skip=true package
```

### 4.3 后端测试（重点：单测执行）

```bash
# 运行某模块全部测试
mvn -f weblog-backend/pom.xml -pl weblog-api -am test

# 运行单个测试类（推荐模板）
mvn -f weblog-backend/pom.xml -pl weblog-api -am -Dtest=PageParamUtilSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test

# 运行单个测试方法（推荐模板）
mvn -f weblog-backend/pom.xml -pl weblog-api -am -Dtest=PageParamUtilSecurityTest#shouldClampPageSizeToMaxLimit -Dsurefire.failIfNoSpecifiedTests=false test

# 另一个模块的单测示例
mvn -f weblog-backend/pom.xml -pl weblog-infra-captcha -am -Dtest=TrackAnalyzerSecurityTest -Dsurefire.failIfNoSpecifiedTests=false test
```

### 4.4 前端（Nuxt）

```bash
# 安装依赖
pnpm --dir weblog-user install
pnpm --dir weblog-admin install

# 开发
pnpm --dir weblog-user dev
pnpm --dir weblog-admin dev

# 生产构建
pnpm --dir weblog-user build
pnpm --dir weblog-admin build
```

### 4.5 前端 lint / 质量门禁

```bash
# 用户端 lint（仓库内已配置）
pnpm --dir weblog-user lint
pnpm --dir weblog-user lint:fix

# 管理端当前无独立 lint script，默认以构建通过和 CI 门禁为准

# 构建告警阈值检查（CI 同款）
node scripts/check-build-warnings.mjs --log weblog-admin/build.log --max-circular 7 --forbid-empty true

# 最大 chunk 阈值检查（CI 同款）
node scripts/check-max-chunk.mjs --dir weblog-admin/.output/public/_nuxt --max-kb 300 --label weblog-admin
node scripts/check-max-chunk.mjs --dir weblog-user/.output/public/_nuxt --max-kb 300 --label weblog-user
```

### 4.6 安全回归脚本

```bash
# P0 API 回归
pwsh -File scripts/p0-api-regression.ps1 -BaseUrl "http://127.0.0.1:9091"

# 验证码攻防回归
pwsh -File scripts/captcha-attack-regression.ps1 -BaseUrl "http://127.0.0.1:9091" -FailOnUnexpectedSuccess -RequireBlacklistActivation
```

## 5. 数据库约定（重要）

- `database/weblog.sql`：完整快照，作为结构与数据的事实源。
- `database/sql/init/02-schema.sql`：结构初始化脚本（由快照同步生成）。
- `database/sql/init/03-data.sql`：数据初始化脚本（由快照同步生成）。
- `database/sql/init/01-grant-permissions.sql`：手动授权脚本（可选）。
- `weblog-backend/weblog-api/src/main/resources/db/migration/`：Flyway 增量迁移脚本目录（生产/开发环境启用）。

初始化顺序：`02-schema.sql` -> `03-data.sql`。

结构变更强制要求：凡涉及建表/改表/索引/枚举/约束，必须同时更新
1) Flyway 增量迁移脚本（`db/migration/Vx__*.sql`）
2) `database/sql/init/02-schema.sql`
3) `database/weblog.sql`
禁止仅修改其中一部分。

## 6. 代码风格总则

- 修改前先阅读同目录已有实现，优先复用现有工具类与模式。
- 只做与当前任务直接相关的最小修改，不做无关重构。
- 不引入 `any`、`@ts-ignore`、`eslint-disable` 规避检查。
- 新增代码注释仅用于解释非直观逻辑，避免注释噪音。

## 7. Java 风格约定（后端）

- 缩进 4 空格、UTF-8 编码、包名全小写。
- 命名：类名 `PascalCase`；方法/变量 `camelCase`；常量 `UPPER_SNAKE_CASE`；布尔字段优先 `isXxx/hasXxx`。
- 导入：优先显式导入，静态导入仅用于断言等高频场景。
- 分层：Controller（参数/鉴权）-> Service（业务）-> Mapper（SQL）。
- 参数校验：Controller 入参优先 `@Valid` + DTO；分页统一 `PageParamUtil.normalize(...)`。
- 安全：用户输入按场景调用 `XssUtil.cleanText/cleanMarkdown`；认证/风控接口复用 `@RateLimit`、`@AuditLog`。
- 异常：业务异常抛 `BusinessException`（带 `ResultCode`）；统一由全局异常处理器转 `Result`，不向前端暴露堆栈。
- 响应：统一 `Result<T>`；Controller 不返回裸对象。
- 日志：使用 `@Slf4j`，记录关键上下文，禁止打印密码/token/key。

## 8. Vue / TypeScript 风格约定（前端）

- 统一使用 `script setup lang="ts"`，缩进 2 空格，SFC 顺序保持 `template -> script -> style`。
- 导入路径优先 `~/` 别名，避免深层相对路径。
- 类型优先：API 返回值定义明确接口（如 `PostVO`、`PageResult<T>`），避免 `any`。
- 组合式写法：状态用 `ref/reactive/computed`，副作用用 `watch`。
- 涉及浏览器对象时增加 `import.meta.client` / `typeof window` 防护。
- 网络层统一复用 `utils/network/http.ts`，不要在业务代码重复封装 axios。
- 错误处理返回用户可读信息；认证失效按既有逻辑跳转登录或弹登录框。

## 9. SQL 与脚本风格约定

- 初始化 SQL 只维护 `database/sql/init/`；不再新增历史增量目录。
- 变更数据库结构或种子数据后，保持 `weblog.sql` 与 `init` 脚本一致。
- 脚本文件头写清楚用途、输入参数、输出路径。
- Node 脚本默认 ESM 风格，避免引入与现有风格冲突的运行时依赖。

## 10. 代理执行清单（提交前）

1. 仅修改任务相关文件，避免顺手改动。
2. 至少运行受影响模块的构建/测试。
3. 若改动后端接口，补跑对应回归脚本或关键单测。
4. 若改动前端构建配置，补跑 chunk/warning 门禁脚本。
5. 更新必要文档（命令、路径、行为变化）并保持与代码一致。
6. 若涉及数据库结构，逐项核对“`db/migration` + `02-schema.sql` + `weblog.sql`”三联一致。
7. 若按 OpenSpec 流程实施，完成实现后同步 `tasks.md` 勾选状态；变更完成后再执行 archive。
