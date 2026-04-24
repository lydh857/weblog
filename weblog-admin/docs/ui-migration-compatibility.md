# Element Plus 与 shadcn 并存策略

本文档用于约束 `weblog-admin` 在 UI 迁移期间的并存规则，避免样式冲突与交互回归。

## 1. 组件选型规则

- 新增页面或新增交互优先使用 `~/components/ui/*`（shadcn 风格）。
- 存量页面允许继续使用 Element Plus，但涉及改造时优先替换为 `components/common` 适配组件：
  - `BatchActionDropdown`
  - `FormDialog`
- 迁移期允许双栈并存，不做一次性替换。

## 2. 命名域与导入约定

- shadcn 组件统一从 `~/components/ui/*` 导入。
- 业务适配层统一从 `~/components/common/*` 与 `~/composables/ui/*` 导入。
- 禁止在页面内重复封装等价基础组件（例如重复封装 Select/Dialog）。

## 3. 样式优先级与冲突处理

- 全局基线顺序固定：`~/assets/css/tailwind.css` 在前，`~/assets/scss/main.scss` 在后。
- 页面样式优先使用语义变量与 Tailwind/shadcn 类，避免对 Element Plus 全局类名做高优先级覆盖。
- 若必须覆盖 Element Plus 样式：
  - 仅限页面局部 `scoped` 范围内处理；
  - 需说明原因并附带可删除时机（迁移完成后可移除）。

## 4. 禁止项

- 禁止新增 `!important` 全局样式补丁覆盖整个后台。
- 禁止在不同页面定义同名但行为不一致的“通用弹窗/批量操作”实现。
- 禁止把敏感配置（如密钥）通过 UI 明文输入后落库（应走环境变量/Secrets）。

## 5. 回滚与发布策略

- 采用“按页面批次发布”策略，每批次仅包含有限页面改造。
- 回滚粒度为“批次版本回滚”，出现高优先级 UI 回归时直接回退该批次。
- 提交策略：迁移改造与业务功能修改分离提交，确保可独立回滚。

运行时回滚开关（页面/组件级）：

- `NUXT_PUBLIC_UI_MIGRATION_ROLLBACK_ALL=true`：全局启用兼容回滚模式。
- `NUXT_PUBLIC_UI_MIGRATION_ROLLBACK_FEATURES=form-dialog,batch-action-dropdown`：按功能点启用回滚。
  - 支持的特性键：`form-dialog`、`batch-action-dropdown`。
  - 也支持 `all`，效果等同全量回滚。

## 6. 验收基线

- 每个迁移批次至少验证：
  - 亮/暗主题切换
  - 表单提交流程
  - 弹层开关与确认流程
  - 表格行操作与批量操作
  - 管理端构建通过（`pnpm --dir weblog-admin build`）
