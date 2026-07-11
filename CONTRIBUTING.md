# 贡献指南

感谢你对 Weblog 项目的关注！本文档描述了参与开发的标准流程。

## 开发环境搭建

### 前置要求

- JDK 21+
- Node.js 20+ / pnpm 8+
- Docker & Docker Compose
- MySQL 8.0+ / Redis 7.0+（或直接用 Docker）

### 启动步骤

```bash
# 1. 启动基础设施
docker compose up -d

# 2. 复制环境变量模板并配置
cp .env.example .env
# 编辑 .env 填入实际配置

# 3. 启动后端
cd weblog-backend
mvn clean install -DskipTests
mvn spring-boot:run -pl weblog-api -DskipTests

# 4. 启动用户端
cd weblog-user
pnpm install
pnpm dev

# 5. 启动管理端
cd weblog-admin
pnpm install
pnpm dev
```

## 代码风格

### Java（后端）

- 缩进 4 空格，UTF-8 编码，包名全小写
- 类名 `PascalCase`，方法/变量 `camelCase`，常量 `UPPER_SNAKE_CASE`
- 分层：Controller（参数/鉴权）→ Service（业务）→ Mapper（SQL）
- 统一响应 `Result<T>`，异常抛 `BusinessException`
- 使用 `@Slf4j`，禁止打印密码/Token

### Vue / TypeScript（前端）

- 使用 `script setup lang="ts"`，缩进 2 空格
- 导入路径优先 `~/` 别名
- API 返回值定义明确接口，避免 `any`
- 网络层复用 `utils/network/http.ts`

## 开发流程

1. 从最新 `master` 创建功能分支：`feature/xxx`、`fix/xxx`
2. 完成开发并运行受影响模块的构建/测试
3. 创建 Pull Request 合并到 `master`
4. PR 标题和描述使用中文，可保留 `feat/fix/docs` 类型前缀
5. 等待 CI 检查通过后合并

## 提交信息规范

```
feat(模块): 新功能简述
fix(模块): 修复问题描述
docs: 文档更新
refactor: 重构说明
```

## 数据库变更

- 结构变更必须新增 Flyway 迁移脚本（`Vx__desc.sql`）
- 同步更新 `database/sql/init/02-schema.sql` 和 `database/weblog.sql`
- 种子数据变更同步 `03-data.sql`
- 禁止修改已执行的 Flyway 历史迁移脚本

## 测试

```bash
# 后端单测
mvn -f weblog-backend/pom.xml -pl weblog-api -am test

# 前端 lint
pnpm --dir weblog-user lint
pnpm --dir weblog-admin build
```
