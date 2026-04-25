# Weblog 开发经验教程

本文记录 Weblog 项目中已经落地、验证过或排查过的开发经验，重点覆盖性能优化、前端构建拆包、后端缓存、安全防护、CI 门禁和线上故障排查。它不是抽象规范，而是面向本项目的实战手册。

## 1. 性能优化经验

### 1.1 先定位瓶颈，再动代码

性能优化不要凭感觉修改。每次优化前先确认问题属于哪一类：接口慢、数据库慢、缓存缺失、前端首屏慢、chunk 过大、重复请求、资源阻塞、内存占用过高。

本项目常用检查入口：

```bash
pnpm --dir weblog-user build
pnpm --dir weblog-admin build
node scripts/check-max-chunk.mjs --dir weblog-admin/.output/public/_nuxt --max-kb 300 --label weblog-admin
node scripts/check-max-chunk.mjs --dir weblog-user/.output/public/_nuxt --max-kb 300 --label weblog-user
mvn -f weblog-backend/pom.xml -pl weblog-api -am test
```

排查顺序建议：

1. 先看用户可感知的问题：页面是否白屏、首屏是否慢、接口是否超时。
2. 再看构建产物：是否出现超大 chunk、空 chunk、循环 chunk 警告。
3. 再看后端日志：是否有慢查询、缓存未命中、异常堆栈。
4. 最后再改实现，不要一上来重构。

### 1.2 首页和详情页优先优化首屏

用户端是 SSR，首屏内容要尽量服务端可输出。文章详情页不要只依赖客户端组件渲染 Markdown，否则搜索引擎和慢设备会看到空内容或加载占位。

当前项目的实践是：

1. 服务端优先输出已经净化过的 `post.htmlContent`。
2. 客户端再用 Markdown 预览组件增强交互。
3. 浏览器专属逻辑必须加 `import.meta.client` 或 `typeof window` 防护。

这种方式比“全部交给客户端渲染”更稳，也更利于 SEO。

### 1.3 管理端大组件按需加载

管理端功能多，编辑器、图表、裁剪器、代码高亮都可能很重。不要把这些库放进应用初始化阶段。

本项目已经采用的经验：

1. `md-editor-v3` 按页面需要加载，不在全局插件里直接注册重型功能。
2. ECharts 不在首页模块顶层静态初始化，而是滚动到图表区域后再动态加载。
3. 图片裁剪、Markdown 编辑器等只在对应功能出现时加载。

判断是否应该懒加载的标准：

1. 是否只在少数页面使用。
2. 是否体积明显偏大。
3. 是否依赖浏览器对象。
4. 是否可能影响登录页、首页初始化。

满足其中两项，就优先考虑按需加载。

### 1.4 图片上传控制并发

批量上传图片时，不要无限并发。浏览器并发太高会导致请求拥塞、后端线程压力上升、对象存储签名请求排队。

当前管理端多图上传采用小并发队列，推荐并发数为 2。这样用户感知速度足够，后端和对象存储也更稳定。

经验规则：

1. 小文件多图上传：并发 2 到 3。
2. 大文件上传：并发 1 到 2。
3. 每个文件上传前都必须先校验类型、大小和业务限制。
4. 签名接口不要绕过上传类型配置。

## 2. 前端构建与拆包经验

### 2.1 不要把强内部依赖库拆得太碎

ECharts 和 ZRender 这类库内部存在继承、注册和副作用初始化。如果按每个内部目录拆成很多 chunk，可能出现运行时加载顺序问题，例如：

```text
TypeError: Class extends value undefined is not a constructor or null
```

干净做法不是简单合成一个超大包，也不是放大 chunk 阈值，而是按稳定边界拆分：

1. `zrender` 单独分组。
2. `echarts` 按 `charts`、`components`、`coord`、`core` 等粗粒度分组。
3. 不按 `lib/chart/bar`、`lib/component/grid` 这种过细路径继续拆。
4. 页面侧对图表库使用动态加载，避免影响登录页和应用初始化。

当前管理端已经按这个思路处理。

### 2.2 空 chunk 不是小问题

构建产物里如果出现 0KB 或 1 字节 JS，通常说明 `manualChunks` 分组定义了一个实际无内容的 chunk。虽然浏览器可能能加载，但它会污染 manifest，也会让 CI 门禁失去可信度。

处理方式：

1. 找到空 chunk 的 manifest 名称。
2. 回到 `nuxt.config.ts` 查对应 `manualChunks` 返回值。
3. 删除没有实际输出的兜底分组。
4. 重新运行构建警告门禁。

本项目使用：

```bash
node scripts/check-build-warnings.mjs --log weblog-admin/build.log --max-circular 7 --forbid-empty true
```

### 2.3 不要靠调大阈值解决 chunk 问题

如果超过 300KB，不要第一反应改阈值。正确顺序是：

1. 判断是不是库被错误静态引入。
2. 判断是不是桶文件导入导致整包进入产物。
3. 判断是不是 `manualChunks` 过粗或过细。
4. 判断是否可以按页面动态加载。
5. 仍然合理超限时，才考虑调整阈值，并写明原因。

管理端曾遇到 ECharts 合并后单 chunk 超过 500KB，最终没有放大阈值，而是改成动态加载加粗粒度拆包。

## 3. 后端性能经验

### 3.1 高频读取接口优先加缓存

文章详情、上下篇导航、热门文章、统计类接口都容易被重复访问。缓存设计要注意两点：命中率和失效时机。

本项目实践：

1. 文章详情有缓存。
2. 上下篇导航增加独立缓存。
3. 文章更新、删除、状态变化时清理相关缓存。

缓存命名建议：

```text
post:detail:{id}
post:navigation:{id}
post:hot
```

缓存不是越多越好。只有读多写少、数据允许短暂延迟的场景才适合缓存。

### 3.2 分页参数必须统一规范化

分页接口不要每个 Controller 自己写边界判断，容易遗漏最大页大小限制。

本项目后端统一使用：

```java
PageParamUtil.normalize(...)
```

经验规则：

1. 页码不能小于 1。
2. 每页数量要有上限。
3. 排序字段不能直接信任前端输入。
4. 管理端和用户端分页约束要一致。

### 3.3 文件上传必须限制读取

上传文件不能先完整读入内存再判断大小。正确做法是限制读取，超过上限立即拒绝。

当前 R2 上传已经使用限制读取方式，避免大文件造成内存压力。

经验规则：

1. 先校验业务配置中的文件类型和大小。
2. 再进行对象存储签名或上传。
3. 读取时限制最大字节数。
4. 日志里不要打印真实对象存储密钥和签名。

## 4. 安全开发经验

### 4.1 富文本必须统一净化

富文本展示不能信任数据库内容，因为内容可能来自历史导入、管理员复制粘贴或接口漏洞。

当前项目实践：

1. 用户端和管理端都使用 DOMPurify 处理富文本。
2. 外链自动补 `target="_blank"` 和 `rel="noopener noreferrer"`。
3. Markdown 和普通文本使用不同净化策略。

经验规则：

1. 普通标题、摘要、昵称用文本净化。
2. Markdown 正文用 Markdown 场景净化。
3. HTML 展示前再做最终净化。
4. 不使用 `v-html` 展示未经处理的内容。

### 4.2 CSP 先 report-only，再 enforce

CSP 能提高安全性，但直接强制可能破坏 Nuxt 内联脚本、第三方资源、图片和样式。

本项目用户端默认使用 `Content-Security-Policy-Report-Only`。建议上线顺序：

1. 本地和测试环境先开启 report-only。
2. 收集浏览器控制台和上报日志。
3. 修正误拦截资源。
4. 再切换到 enforce。

环境变量建议：

```text
NUXT_CSP_STAGE=report-only
NUXT_CSP_STAGE=dual
NUXT_CSP_STAGE=enforce
```

### 4.3 验证码通过令牌必须绑定场景

滑块验证码不能只证明“这个人刚刚滑过一次”，还要证明“这次滑块用于当前操作”。否则登录、注册、敏感操作之间可能复用同一个通过令牌。

当前项目已经让验证码通过令牌绑定：

1. `tokenId`
2. `clientIp`
3. `scene`
4. `createTime`

经验规则：

1. 登录验证码使用登录场景。
2. 注册验证码使用注册场景。
3. 敏感操作使用独立场景。
4. 通过令牌有效期不宜过长，默认 120 秒更安全。

## 5. 稳定性经验

### 5.1 登录页不能被重型初始化拖垮

管理端登录页是所有后台功能的入口。登录页不应该依赖图表、编辑器、裁剪器等业务重型库。

如果登录页控制台出现应用初始化错误，优先检查：

1. 是否有全局插件静态引入重型库。
2. 是否有 `manualChunks` 拆坏依赖顺序。
3. 是否有浏览器对象在服务端或初始化阶段被访问。
4. 是否有路由守卫在未登录时触发了过多业务请求。

### 5.2 启动动画只做反馈，不做阻塞

管理端启动动画应该提供“正在进入后台”的反馈，但不能挡住登录页，也不能在路由切换时重复播放。

当前项目实践：

1. 登录页直接关闭启动页。
2. 已播放过的启动动画不重复播放。
3. 计时器需要避免重复创建。

### 5.3 浏览器事件监听必须成对清理

在 Vue 页面中添加 `window.addEventListener` 时，必须使用具名函数，并在 `onUnmounted` 中移除。

错误做法：

```ts
window.addEventListener('keydown', () => {
  // ...
})
```

推荐做法：

```ts
function handleKeydown(event: KeyboardEvent) {
  // ...
}

onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

onUnmounted(() => {
  window.removeEventListener('keydown', handleKeydown)
})
```

## 6. CI 与质量门禁经验

### 6.1 本地先跑同款门禁

不要等 GitHub Actions 报错再修。涉及前端构建配置时，本地至少运行：

```bash
pnpm --dir weblog-admin build
node scripts/check-build-warnings.mjs --log weblog-admin/build.log --max-circular 7 --forbid-empty true
node scripts/check-max-chunk.mjs --dir weblog-admin/.output/public/_nuxt --max-kb 300 --label weblog-admin
```

涉及用户端时运行：

```bash
pnpm --dir weblog-user lint
pnpm --dir weblog-user build
node scripts/check-max-chunk.mjs --dir weblog-user/.output/public/_nuxt --max-kb 300 --label weblog-user
```

涉及后端时运行：

```bash
mvn -f weblog-backend/pom.xml -pl weblog-api -am test
```

### 6.2 CI 初始化数据库和 Flyway 不要重复执行

P0 回归如果已经导入完整初始化 SQL，就不要再让 Flyway 对同一套结构执行历史迁移，否则可能出现重复字段或重复索引。

经验规则：

1. 完整初始化 SQL 用于一次性得到当前结构。
2. Flyway 用于从旧版本迁移到新版本。
3. 两者不能在同一个空库流程里重复表达同一批结构变更。
4. CI 中要明确 `SPRING_FLYWAY_ENABLED` 的取值。

### 6.3 包管理器版本要在 CI 中明确

前端 CI 中，`setup-node` 的 pnpm cache 会在 pnpm 可执行文件存在前检查 pnpm。如果 pnpm 还没安装，会直接失败。

经验规则：

1. 先启用或安装 pnpm。
2. 再使用 pnpm 安装依赖。
3. 不要在 pnpm 可用前启用依赖它的 cache 配置。
4. 本地和 CI 尽量使用同一套 pnpm lockfile。

## 7. 数据库变更经验

### 7.1 结构变更必须三联同步

本项目涉及表结构、索引、枚举、约束时，必须同时更新：

1. Flyway 增量迁移脚本。
2. `database/sql/init/02-schema.sql`。
3. `database/weblog.sql`。

如果涉及种子数据，还要同步：

1. `database/sql/init/03-data.sql`。
2. `database/weblog.sql`。

不要只改 Java 实体或只改初始化 SQL。

### 7.2 删除功能时要连数据一起清理

删除一个无用功能，不只删页面入口。要从这些层面检查：

1. 前端页面和 API 类型。
2. 后端 Controller、Service、Mapper、DTO、VO。
3. 配置项。
4. 数据库表、字段、索引和种子数据。
5. 测试用例。
6. 文档和脚本。

本项目删除 AI 评论审核功能时，就是按这个顺序处理，避免留下半截功能。

## 8. 线上故障排查经验

### 8.1 前端初始化崩溃和接口 401 要分开看

管理端未登录时出现 `/api/admin/user/me` 401 是正常现象，不一定是白屏根因。真正需要优先处理的是应用初始化阶段的 JavaScript 错误。

排查顺序：

1. 打开浏览器控制台，看第一个 JavaScript 异常。
2. 记录 chunk 文件名和堆栈位置。
3. 在本地 `.output/public/_nuxt` 查对应产物。
4. 结合 `manualChunks` 判断是否是拆包导致。
5. 本地生产构建和预览验证。

### 8.2 修复线上前端问题必须跑生产构建

开发模式和生产构建的 chunk 行为不同。只跑 `pnpm dev` 不能证明线上问题解决。

本项目修复管理端初始化错误时，必须运行：

```bash
pnpm --dir weblog-admin build
```

然后用生产输出验证：

```bash
node weblog-admin/.output/server/index.mjs
```

浏览器访问 `/admin`，确认能正常进入 `/admin/login`，控制台没有错误。

## 9. 提交前检查清单

每次提交前至少检查：

1. `git status --short`，确认没有无关文件。
2. `git diff --check`，确认没有空白错误。
3. 前端改动跑对应 build 或 lint。
4. 后端改动跑对应 Maven 测试。
5. 数据库结构改动检查 Flyway、初始化 SQL、完整快照三联一致。
6. 文档改动检查是否写入敏感信息。
7. 不提交日志、缓存、构建产物、索引目录、临时文件。

## 10. 最重要的经验

1. 优先做最小正确修改，不为了“顺手优化”扩大影响面。
2. 先复用项目已有模式，再考虑新增工具或抽象。
3. 构建警告不是噪音，尤其是空 chunk 和循环 chunk。
4. 安全策略先灰度，再强制。
5. 前端重型库不要污染登录页和应用初始化。
6. 数据库结构变更必须同时维护迁移、初始化和快照。
7. 每个线上问题都要形成可复用经验，避免下次重复踩坑。
