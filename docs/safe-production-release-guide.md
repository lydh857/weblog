# Weblog 线上安全更新流程

本文说明如何在不影响线上项目的前提下，安全开发、验证、推送和发布新功能。适用于 Weblog 当前仓库、GitHub Actions、GHCR 镜像、自托管生产 Runner 和 `docker-compose.prod.yml` 部署方式。

## 1. 核心原则

1. GitHub 是唯一开发主线，Gitee 只做镜像分发。
2. 功能开发不要直接改生产服务器。
3. 功能分支推送不部署生产，`master` 是发布分支。
4. 合并或推送 `master` 前必须确认本次提交可以上线。
5. 数据库结构变更必须有 Flyway 迁移脚本，并同步初始化 SQL 和完整快照。
6. 发布前必须有备份，发布后必须有验证，失败必须能回滚。

## 2. 当前安全发布模式

当前 `.github/workflows/deploy.yml` 的发布模式为：

1. 推送功能分支：只用于开发验证，不部署生产。
2. 推送或合并到 `master`：自动构建后端、用户端、管理端镜像，推送到 GHCR，并部署生产服务器。
3. 手动运行工作流：也会执行同一套构建和部署流程。
4. Gitee 只做镜像分发，不作为生产部署触发来源。

因此 `master` 必须保持可发布状态。没有完成验证的功能只能停留在功能分支，不能推入 `master`。

## 3. GitHub 分支保护建议

建议在 GitHub 仓库中给 `master` 配置分支保护：

1. 进入 GitHub 仓库。
2. 打开 `Settings`。
3. 打开 `Branches`。
4. 为 `master` 添加保护规则。
5. 要求 PR 合并前通过必要检查。
6. 禁止未验证代码直接进入 `master`。
7. 保存配置。

这样可以形成两道防线：

1. 功能先在分支开发和验证。
2. 只有通过检查的代码才能进入会自动部署的 `master`。

## 4. 日常功能开发流程

### 4.1 从最新主线创建分支

```bash
git checkout master
git pull github master
git checkout -b feature/your-feature-name
```

分支命名建议：

1. 新功能：`feature/xxx`
2. 修复问题：`fix/xxx`
3. 安全修复：`security/xxx`
4. 文档更新：`docs/xxx`

### 4.2 本地开发

开发时遵守最小修改原则：

1. 只改当前功能需要的文件。
2. 优先复用已有组件、工具函数和 API 封装。
3. 不顺手重构无关代码。
4. 不提交日志、缓存、构建产物、索引目录。

### 4.3 本地验证

按改动范围选择命令。

后端改动：

```bash
mvn -f weblog-backend/pom.xml -pl weblog-api -am test
```

用户端改动：

```bash
pnpm --dir weblog-user lint
pnpm --dir weblog-user build
node scripts/check-max-chunk.mjs --dir weblog-user/.output/public/_nuxt --max-kb 300 --label weblog-user
```

管理端改动：

```bash
pnpm --dir weblog-admin build
node scripts/check-build-warnings.mjs --log weblog-admin/build.log --max-circular 7 --forbid-empty true
node scripts/check-max-chunk.mjs --dir weblog-admin/.output/public/_nuxt --max-kb 300 --label weblog-admin
```

空白检查：

```bash
git diff --check
```

## 5. 数据库变更流程

凡涉及建表、改表、删表、索引、枚举、约束，必须同时更新：

1. Flyway 增量迁移脚本：`weblog-backend/weblog-api/src/main/resources/db/migration/`
2. 初始化结构脚本：`database/sql/init/02-schema.sql`
3. 完整数据库快照：`database/weblog.sql`

如果涉及初始化数据，还要同步：

1. 初始化数据脚本：`database/sql/init/03-data.sql`
2. 完整数据库快照：`database/weblog.sql`

数据库变更提交前检查：

1. 新迁移脚本版本号不能和已有版本重复。
2. 迁移脚本必须可重复在旧库上执行一次。
3. 初始化 SQL 必须能创建当前最新结构。
4. Java 实体、Mapper、DTO、VO 与数据库字段一致。
5. 删除字段前确认前端、后端、脚本和文档都不再使用。

## 6. 合并到主线前检查

合并前执行：

```bash
git status --short
git diff --check
```

确认：

1. 没有未跟踪的临时文件。
2. 没有 `.env`、密钥、Token、数据库密码。
3. 没有构建产物、日志、缓存。
4. 本地测试已通过。
5. 数据库三联一致。
6. 文档与行为一致。

## 7. 推送和 CI 验证

推送分支：

```bash
git push github feature/your-feature-name
```

如果直接合并到 `master`，推送后会触发构建和生产部署。只有确认本次代码可以上线时，才允许进入 `master`。

需要等待以下检查通过：

1. 后端构建或测试。
2. 用户端构建和 lint。
3. 管理端构建和 chunk 门禁。
4. 安全扫描。
5. P0 API 回归。

## 8. 发布前备份

生产部署前必须备份。

### 8.1 备份数据库

在生产服务器执行：

```bash
cd /opt/weblog
mkdir -p backups/mysql
docker exec weblog-mysql sh -lc 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --single-transaction --routines --triggers weblog' > backups/mysql/weblog-$(date +%Y%m%d-%H%M%S).sql
```

### 8.2 备份上传文件

```bash
cd /opt/weblog
mkdir -p backups/uploads
tar -czf backups/uploads/uploads-$(date +%Y%m%d-%H%M%S).tar.gz uploads
```

### 8.3 备份配置

```bash
cd /opt/weblog
mkdir -p backups/config
cp .env.prod backups/config/.env.prod-$(date +%Y%m%d-%H%M%S)
cp docker-compose.prod.yml backups/config/docker-compose.prod-$(date +%Y%m%d-%H%M%S).yml
```

备份完成后，至少确认文件不是空文件：

```bash
ls -lh backups/mysql backups/uploads backups/config
```

## 9. 发布生产

推荐发布方式是合并或推送到 `master`，由 GitHub Actions 自动发布生产。

```bash
git checkout master
git pull github master
git merge --no-ff feature/your-feature-name
git push github master
```

如果需要在没有新提交时重新部署，也可以在 GitHub 页面手动触发：

1. 打开仓库。
2. 进入 `Actions`。
3. 选择 `Build & Deploy to Server`。
4. 点击 `Run workflow`。
5. 选择 `master` 分支。
6. 点击运行。

## 10. 发布后验证

部署完成后执行以下检查。

### 10.1 容器状态

```bash
cd /opt/weblog
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
```

确认关键容器正常：

1. `weblog-api`
2. `weblog-user`
3. `weblog-nginx`
4. `weblog-mysql`
5. `weblog-redis`

### 10.2 后端健康检查

```bash
docker exec weblog-user sh -lc 'wget -qO- http://weblog-api:9091/actuator/health/liveness'
```

### 10.3 关键页面检查

浏览器检查：

1. 用户端首页。
2. 文章详情页。
3. 管理端 `/admin`。
4. 管理端登录页。
5. 管理端首页数据图表。
6. 文章列表、评论列表、系统配置等关键页面。

### 10.4 浏览器控制台检查

打开 DevTools，确认：

1. 没有应用初始化 JavaScript 错误。
2. 没有 chunk 加载失败。
3. 未登录状态下 `/api/admin/user/me` 返回 401 属于正常登录态探测。
4. 登录后不应持续出现 401。

## 11. 回滚流程

如果发布后出现严重问题，优先回滚到上一版镜像。

### 11.1 快速回滚代码镜像

如果 GHCR 中保留了上一版 tag，将 `docker-compose.prod.yml` 中镜像 tag 改回上一版，然后执行：

```bash
cd /opt/weblog
docker compose --env-file .env.prod -f docker-compose.prod.yml pull
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --remove-orphans
```

当前工作流默认使用 `latest`，建议后续增强为同时推送提交 SHA tag，便于精确回滚。

### 11.2 回滚数据库

只有在确认数据库变更造成严重问题且无法兼容时才回滚数据库。回滚前先停止写入流量或进入维护窗口。

恢复示例：

```bash
cd /opt/weblog
docker exec -i weblog-mysql sh -lc 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" weblog' < backups/mysql/你的备份文件.sql
```

数据库回滚风险较高，优先设计向前兼容迁移，尽量避免必须回滚数据库。

## 12. 推荐发布策略

### 12.1 小步发布

不要把大量无关功能一次性上线。推荐：

1. 一个功能一个分支。
2. 一个修复一个提交或一组相关提交。
3. 高风险功能单独发布。
4. 数据库结构变更单独评审。

### 12.2 向前兼容数据库变更

高风险数据库变更拆成多次发布：

1. 第一次发布：新增字段或新表，旧代码仍可运行。
2. 第二次发布：代码开始读写新字段。
3. 第三次发布：确认稳定后再删除旧字段。

不要在同一次发布中既删除旧字段又上线依赖新字段的大改动。

### 12.3 高风险功能加开关

如果功能影响用户路径，建议加配置开关：

1. 默认关闭。
2. 发布后后台开启。
3. 异常时立即关闭开关。
4. 稳定后再考虑移除开关。

## 13. 每次发布检查清单

发布前：

1. 本地构建和测试通过。
2. CI 通过。
3. 数据库迁移检查完成。
4. 文档更新完成。
5. 已备份数据库。
6. 已备份上传文件。
7. 已确认发布提交号。
8. 已选择低峰期。

发布中：

1. 合并或推送到 `master`。
2. 观察 GitHub Actions 构建和部署日志。
3. 确认镜像构建成功。
4. 确认 Docker Compose 启动成功。

发布后：

1. 检查容器状态。
2. 检查后端健康接口。
3. 检查用户端首页和文章详情页。
4. 检查管理端登录和首页。
5. 检查浏览器控制台。
6. 检查后端日志。
7. 记录本次发布时间、提交号和验证结果。

## 14. 常见问题

### 14.1 推送 master 后会不会影响线上

会。当前配置下，推送或合并到 `master` 会自动构建镜像并部署生产。因此只有完成验证、确认可上线的代码才能进入 `master`。

### 14.2 为什么还会推送 latest 镜像

推送 `latest` 后，生产部署任务会拉取最新镜像并重启容器。功能分支不会触发这套生产部署流程。

### 14.3 如果只想构建验证但不想上线

不要推送到 `master`。请使用功能分支或临时分支，并等待对应 CI 验证。

### 14.4 如果误推 master 后想阻止部署

立即进入 GitHub Actions 取消正在运行的 `Build & Deploy to Server` 工作流。如果部署已经完成，按回滚流程恢复上一版镜像。

### 14.5 管理端未登录时 401 是否异常

未登录访问 `/admin` 时，请求 `/api/admin/user/me` 返回 401 是正常登录态探测。随后 `/api/admin/auth/refresh` 返回 401 代表 refresh token 也不可用，前端会进入登录页。只要页面正常显示、控制台没有 JavaScript 异常，就不是线上故障。
