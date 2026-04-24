# 管理端 UI 迁移验收清单（阶段）

## 1. 自动化检查

- 构建命令：`pnpm --dir weblog-admin build`
- 最近一次结果：通过（2026-04-18 本地执行）

## 2. 手工回归清单（待执行）

以下项为每批次发布前的最小回归集合：

- 亮/暗主题切换：布局、卡片、弹层、表格头部样式无割裂
- 登录流程：输入校验、验证码、记住我、自动登录、错误提示
- 列表页：筛选、分页、批量操作、危险操作确认
- 弹层交互：新增/编辑/删除确认、关闭行为、禁用态
- 兼容回滚：
  - `NUXT_PUBLIC_UI_MIGRATION_ROLLBACK_ALL=true`
  - `NUXT_PUBLIC_UI_MIGRATION_ROLLBACK_FEATURES=form-dialog,batch-action-dropdown`

## 3. 已迁移范围（当前工作区证据）

- 基础设施：`components.json`、`assets/css/tailwind.css`、`components/ui/*`、`lib/utils.ts`
- 适配层：`components/common/FormDialog.vue`、`components/common/BatchActionDropdown.vue`
- 回滚机制：`composables/ui/useUiMigrationRollback.ts`
- 登录页：`pages/login.vue`
- 已纳入迁移改造页面（存在迁移中的 UI 变更）：
  - `pages/index.vue`
  - `pages/media/index.vue`
  - `pages/post/index.vue`
  - `pages/post/create.vue`
  - `pages/user/index.vue`
  - `pages/system-config/index.vue`
  - `pages/advertisement/index.vue`
  - `pages/friend-link/index.vue`
  - `pages/comment/index.vue`
  - `pages/announcement/index.vue`
  - `pages/category/index.vue`
  - `pages/tag/index.vue`
  - `pages/topic/index.vue`
  - `pages/carousel/index.vue`
  - `pages/logs.vue`

## 4. 未完全收敛项

- 尚未完成浏览器手工回归记录（对应任务 5.1）：亮/暗切换、表单、弹层、表格操作需逐页实测并留档
- 双栈并存期仍保留必要 Element Plus 依赖用于未迁移或兼容场景；后续批次按页面迁移进度继续收敛

## 5. 后续批次计划

### 批次 A（布局收口）
- 完成侧边栏、顶部栏、TabBar、主内容容器的 shadcn 风格统一
- 目标对应任务：3.1、3.2、3.3

### 批次 B（高频页面收口）
- 仪表盘、媒体管理、文章列表/编辑、用户管理、系统配置的交互一致性收口
- 目标对应任务：4.2、4.3、4.4

### 批次 C（收尾与清理）
- 完成手工回归执行记录
- 持续跟踪并存期 Element Plus 依赖收敛（仅保留必要兼容依赖）
- 目标对应任务：5.1
