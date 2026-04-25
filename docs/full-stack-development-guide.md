# 全栈开发离线实战手册

本文基于当前 Weblog 项目沉淀一套可复用的全栈开发方法。目标不是只讲本项目怎么改，而是让你以后开发其他项目时，即使不能联网、不能使用 AI，也能照着这份手册完成需求分析、架构设计、数据库设计、后端开发、前端开发、测试、部署和排错。

当前 Weblog 项目技术栈：

| 层级 | 技术 |
| --- | --- |
| 后端语言 | Java 21 |
| 后端框架 | Spring Boot 3.5.12、MyBatis-Plus、Sa-Token |
| 基础设施 | MySQL 8.0、Redis 7.2、Lucene、OSS/R2、本地上传 |
| 用户端 | Nuxt 4 SSR、Vue 3、TypeScript、SCSS |
| 管理端 | Nuxt 4 SPA、Vue 3、TypeScript、Element Plus |
| 部署 | Docker Compose、Nginx、GitHub Actions、GHCR |

本文按“从想法到上线”的顺序组织。

## 一、全栈开发的核心思路

全栈项目不是“前端写一点、后端写一点、数据库建几张表”。正确顺序是：

1. 明确业务目标。
2. 拆分用户角色和使用场景。
3. 画出核心业务流程。
4. 设计数据模型。
5. 设计接口契约。
6. 设计后端模块和权限边界。
7. 设计前端页面和状态流。
8. 编写最小可运行版本。
9. 补齐安全、校验、测试、日志、部署。
10. 逐步迭代，不一次性堆复杂功能。

离线开发时最重要的是“先写清楚，再写代码”。没有 AI 辅助时，越需要靠文档、模板、检查清单减少返工。

## 二、从零设计一个项目

### 1. 写项目目标

任何项目先写一句话目标。例如：

```text
我要做一个博客系统，普通用户可以浏览文章、评论、点赞，管理员可以发布文章、审核评论、管理用户和广告。
```

不要一开始写技术。先写业务。

### 2. 列用户角色

以 Weblog 为例：

| 角色 | 能做什么 |
| --- | --- |
| 游客 | 浏览文章、搜索、查看专题、查看友链 |
| 登录用户 | 评论、点赞、收藏、编辑个人资料 |
| 管理员 | 登录后台、管理文章、分类、标签、评论、用户、广告、公告、系统配置 |
| 系统任务 | 定时清理日志、同步统计、数据治理 |

你开发新项目时也按这个表写。

### 3. 列核心功能模块

Weblog 的模块划分可以作为模板：

| 模块 | 示例功能 |
| --- | --- |
| 认证模块 | 注册、登录、验证码、OAuth、退出登录 |
| 用户模块 | 用户资料、头像、密码、邮箱绑定 |
| 内容模块 | 文章、分类、标签、专题、搜索 |
| 互动模块 | 评论、点赞、收藏 |
| 管理模块 | 后台 CRUD、审核、批量操作 |
| 安全模块 | 限流、验证码、审计日志、黑名单 |
| 媒体模块 | 上传、图片管理、对象存储 |
| 配置模块 | 系统配置、运行时开关 |
| 部署模块 | Docker、Nginx、CI/CD |

### 4. 写业务流程

用文字流程就够，不一定要画图。

示例：用户发表评论流程：

```text
1. 用户在文章详情页输入评论。
2. 前端检查评论不能为空。
3. 前端调用 POST /api/portal/comment。
4. 后端校验登录态。
5. 后端清理 HTML 标签，防止 XSS。
6. 后端检查敏感词。
7. 后端根据评论审核开关决定状态：pending 或 approved。
8. 后端写入 t_comment。
9. 如果直接 approved，更新文章评论数缓存。
10. 前端提示发表成功，并刷新评论列表。
```

写流程的好处：你会自然发现需要哪些表、接口、校验和状态。

## 三、推荐架构

### 1. 前后端分离 + 管理端独立

Weblog 采用三端结构：

```text
weblog-backend/  后端 API
weblog-user/     用户端 SSR
weblog-admin/    管理端 SPA
```

这种结构适合大多数中小型项目：

| 部分 | 推荐模式 | 原因 |
| --- | --- | --- |
| 后端 | REST API | 简单、通用、方便前后端分离 |
| 用户端 | SSR 或 SSG | SEO 好，首屏内容可被搜索引擎抓取 |
| 管理端 | SPA | 不需要 SEO，更重交互和表格表单 |

### 2. 后端多模块架构

Weblog 后端目录：

```text
weblog-backend/
├── weblog-common/              公共类、统一响应、异常、分页工具
├── weblog-infra-redis/         Redis 基础设施
├── weblog-infra-oss/           OSS/R2/本地存储
├── weblog-infra-lucene/        搜索基础设施
├── weblog-infra-security/      安全、限流、审计、XSS、敏感词
├── weblog-infra-captcha/       滑块验证码
├── weblog-infra-ai/            AI 写作和配置
├── weblog-module-system/       用户、角色、认证相关业务
├── weblog-module-content/      文章、分类、标签、专题
├── weblog-module-interaction/  评论、点赞、收藏
└── weblog-api/                 启动模块、Controller 聚合、定时任务
```

可复用原则：

| 类型 | 放哪里 |
| --- | --- |
| 所有模块都会用的工具 | `common` |
| 连接外部系统的能力 | `infra-*` |
| 具体业务领域 | `module-*` |
| HTTP 接口入口 | `api` |

不要把所有代码都塞进一个 `service` 包。项目小的时候看似方便，后期会非常难维护。

### 3. 后端调用方向

推荐方向：

```text
Controller -> Service -> Mapper -> Database
```

跨模块调用尽量通过 Service，不要让 Controller 直接操作多个 Mapper。

示例：

```text
AdminCommentController
  -> AdminCommentService
    -> CommentMapper
    -> UserMapper
    -> PostMapper
```

### 4. 前端推荐结构

Nuxt 项目通用目录：

```text
pages/        页面路由
components/   可复用组件
composables/  组合式逻辑
api/          接口封装
stores/       全局状态
utils/        工具函数
assets/       静态资源和样式
middleware/   路由中间件
plugins/      插件
```

拆分原则：

| 内容 | 放哪里 |
| --- | --- |
| 和路由强绑定的页面 | `pages` |
| 多处复用的 UI | `components` |
| 多处复用的业务逻辑 | `composables` |
| 请求后端接口 | `api` |
| 登录用户信息等全局状态 | `stores` |
| 纯函数工具 | `utils` |

## 四、需求分析模板

每个功能开发前，先写以下内容。

### 1. 功能说明模板

```md
## 功能名称

一句话说明。

## 用户角色

- 游客：能做什么
- 登录用户：能做什么
- 管理员：能做什么

## 页面入口

- 用户端：路径
- 管理端：路径

## 主要流程

1. 第一步
2. 第二步
3. 第三步

## 数据变化

- 新增哪些表
- 修改哪些字段
- 是否需要缓存

## 接口列表

- GET /api/...
- POST /api/...

## 权限要求

- 是否登录
- 是否管理员
- 是否本人操作

## 风险点

- 安全风险
- 并发风险
- 性能风险
- 数据一致性风险
```

### 2. 示例：评论管理

```md
## 功能名称

评论管理。

## 用户角色

- 登录用户：发表、删除自己的评论。
- 管理员：查看所有评论、审核、置顶、删除。

## 页面入口

- 用户端：文章详情页。
- 管理端：/comment。

## 主要流程

1. 用户提交评论。
2. 后端过滤 HTML 和敏感词。
3. 根据审核开关设置 pending 或 approved。
4. 管理员在后台审核。
5. 审核通过后更新文章评论数。

## 数据变化

- t_comment 新增记录。
- Redis 更新 post:comment:{postId}。

## 接口列表

- POST /api/portal/comment
- DELETE /api/portal/comment/{id}
- GET /api/admin/comment
- PUT /api/admin/comment/{id}/status

## 权限要求

- 发评论必须登录。
- 删除自己的评论只能本人操作。
- 管理端接口必须管理员登录。

## 风险点

- XSS。
- 敏感词。
- 评论数缓存和数据库不一致。
- 批量删除数量过大。
```

## 五、数据库设计方法

### 1. 先识别实体

实体就是业务中的“东西”。博客系统实体示例：

| 实体 | 表 |
| --- | --- |
| 用户 | `t_user` |
| 文章 | `t_post` |
| 分类 | `t_category` |
| 标签 | `t_tag` |
| 评论 | `t_comment` |
| 点赞 | `t_like` |
| 收藏 | `t_favorite` |
| 登录日志 | `t_login_log` |
| 审计日志 | `t_audit_log` |

### 2. 表设计基本规范

推荐字段：

```sql
CREATE TABLE `t_example` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL COMMENT '名称',
  `status` varchar(32) NOT NULL DEFAULT 'active' COMMENT '状态',
  `is_deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '软删除',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  INDEX `idx_status` (`status`),
  INDEX `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='示例表';
```

### 3. 字段类型选择

| 场景 | 推荐类型 |
| --- | --- |
| 主键 | `bigint` |
| 短文本 | `varchar` |
| 长文本 | `text` |
| 富文本 | `mediumtext` |
| 金额 | `decimal(10,2)` |
| 布尔 | `tinyint(1)` |
| 时间 | `datetime` |
| 状态 | `varchar(32)` 或 `enum` |

经验：如果状态以后可能扩展，优先 `varchar(32)`。如果状态非常固定，也可用 `enum`。

### 4. 索引设计原则

索引不是越多越好。只给高频查询条件加。

常见索引：

```sql
INDEX `idx_user` (`user_id`),
INDEX `idx_post_status` (`post_id`, `status`),
INDEX `idx_create_time` (`create_time`),
INDEX `idx_status_time` (`status`, `create_time`)
```

联合索引顺序原则：

1. 等值查询字段放前面。
2. 范围查询字段放后面。
3. 排序字段尽量放在索引末尾。

示例：查询文章下已通过评论，按时间倒序：

```sql
INDEX `idx_comment_post_parent_status_time` (`post_id`, `parent_id`, `status`, `create_time` DESC)
```

### 5. 数据库迁移规则

当前项目强制规则：结构变更必须同时更新三处：

1. Flyway 增量迁移：`weblog-backend/weblog-api/src/main/resources/db/migration/Vx__*.sql`
2. 初始化结构：`database/sql/init/02-schema.sql`
3. 完整快照：`database/weblog.sql`

新增迁移示例：

```sql
ALTER TABLE `t_post`
  ADD COLUMN `reading_time` int NULL DEFAULT NULL COMMENT '预计阅读分钟数';

CREATE INDEX `idx_post_reading_time` ON `t_post` (`reading_time`);
```

命名示例：

```text
V9__add_post_reading_time.sql
```

### 6. 数据库设计踩坑

| 坑 | 后果 | 建议 |
| --- | --- | --- |
| 一开始不写唯一约束 | 后期出现重复数据 | 邮箱、用户名、slug 等加唯一索引 |
| 不加状态字段 | 删除和审核难做 | 重要业务都设计 status |
| 大字段和列表混查 | 查询慢 | 列表接口不要查正文大字段 |
| 索引乱加 | 写入变慢，占空间 | 只给查询条件和排序字段加 |
| 手动改生产表不写迁移 | 环境不一致 | 所有结构变更都写迁移 |
| 删除字段不评估旧数据 | 回滚困难 | 生产删除字段前先确认不需要历史数据 |

## 六、后端开发规范

### 1. 分层职责

| 层 | 职责 | 禁止做什么 |
| --- | --- | --- |
| Controller | 接收参数、鉴权、返回 Result | 不写复杂业务，不直接拼 SQL |
| Service | 业务规则、事务、缓存、跨表处理 | 不返回裸 Map 到处传 |
| Mapper | 数据库操作 | 不写业务判断 |
| Entity | 数据库表映射 | 不放接口专用字段 |
| DTO/VO | 请求和响应结构 | 不直接复用 Entity 给前端 |

### 2. 统一响应

推荐所有接口返回统一结构：

```java
return Result.success(data);
```

不要这样：

```java
return data;
```

原因：统一响应可以统一处理错误码、消息、前端拦截器。

### 3. Controller 示例

```java
@Tag(name = "管理端-分类管理", description = "分类增删改查")
@RestController
@RequestMapping("/api/admin/category")
@RequiredArgsConstructor
public class AdminCategoryController {

    private final CategoryService categoryService;

    @Operation(summary = "分页查询分类")
    @GetMapping
    public Result<IPage<CategoryVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                          @RequestParam(defaultValue = "20") int pageSize,
                                          @RequestParam(required = false) String keyword) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
        return Result.success(categoryService.listCategories(pageParams.pageNum(), pageParams.pageSize(), keyword));
    }

    @Operation(summary = "创建分类")
    @PostMapping
    @AuditLog(module = "分类管理", operation = "CREATE", description = "创建分类")
    public Result<Void> create(@Valid @RequestBody CreateCategoryRequest req) {
        categoryService.createCategory(req);
        return Result.success();
    }
}
```

要点：

1. 参数用 `@Valid`。
2. 管理端写操作加 `@AuditLog`。
3. 返回 `Result<T>`。
4. 分页参数统一 normalize。

### 4. DTO 示例

```java
@Data
public class CreateCategoryRequest {

    @NotBlank(message = "分类名称不能为空")
    @Size(max = 50, message = "分类名称不能超过 50 个字符")
    private String name;

    @Size(max = 100, message = "slug 不能超过 100 个字符")
    @Pattern(regexp = "^[a-z0-9-]*$", message = "slug 只能包含小写字母、数字和连字符")
    private String slug;
}
```

不要只在前端校验。后端必须再次校验。

### 5. Service 示例

```java
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public IPage<CategoryVO> listCategories(int pageNum, int pageSize, String keyword) {
        PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);

        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<Category>()
            .orderByAsc(Category::getSortOrder)
            .orderByDesc(Category::getCreateTime);

        if (keyword != null && !keyword.trim().isEmpty()) {
            wrapper.like(Category::getName, keyword.trim());
        }

        IPage<Category> page = categoryMapper.selectPage(
            new Page<>(pageParams.pageNum(), pageParams.pageSize()),
            wrapper
        );

        return page.convert(this::toVO);
    }

    @Transactional
    public void createCategory(CreateCategoryRequest req) {
        Category exists = categoryMapper.selectOne(
            new LambdaQueryWrapper<Category>().eq(Category::getName, req.getName().trim())
        );
        if (exists != null) {
            throw new BusinessException(ResultCode.BAD_REQUEST, "分类名称已存在");
        }

        Category category = new Category();
        category.setName(req.getName().trim());
        category.setSlug(req.getSlug());
        categoryMapper.insert(category);
    }

    private CategoryVO toVO(Category category) {
        CategoryVO vo = new CategoryVO();
        vo.setId(category.getId());
        vo.setName(category.getName());
        vo.setSlug(category.getSlug());
        return vo;
    }
}
```

要点：

1. 写操作涉及多表时加 `@Transactional`。
2. 不信任前端传来的任何数据。
3. 查询前处理分页参数。
4. Entity 转 VO，不直接暴露 Entity。

### 6. Mapper 示例

```java
@Mapper
public interface CategoryMapper extends BaseMapper<Category> {
}
```

MyBatis-Plus 能解决大部分简单 CRUD。复杂 SQL 再写 XML 或注解 SQL。

### 7. 异常处理

业务错误抛：

```java
throw new BusinessException(ResultCode.BAD_REQUEST, "分类名称已存在");
```

不要返回 `null` 表示失败，也不要在 Controller 到处 try/catch。

### 8. 分页规范

必须限制 pageSize，避免被恶意请求拖垮。

```java
PageParamUtil.PageParams pageParams = PageParamUtil.normalize(pageNum, pageSize);
```

### 9. 安全规范

| 场景 | 做法 |
| --- | --- |
| 文本输入 | 清理 HTML、限制长度 |
| Markdown | 用专门的 Markdown 清理逻辑 |
| 上传 | 校验扩展名、MIME、大小、每日限制 |
| 登录 | 限流、验证码、失败日志 |
| 管理端写操作 | 审计日志 |
| 敏感操作 | 二次验证或验证码 |
| 富文本展示 | 前后端都做 XSS 防护 |
| 外链 | 跳转中转页或白名单策略 |

### 10. 缓存规范

Redis 常用场景：

| 场景 | 示例 key |
| --- | --- |
| 文章评论数 | `post:comment:{postId}` |
| 脏数据集合 | `post:comment:dirty` |
| 验证码 | `captcha:data:{token}` |
| 一次性验证 Token | `captcha:verify:{tokenId}` |
| 限流 | `rate_limit:{key}` |

缓存原则：

1. 先保证数据库正确，再考虑缓存。
2. 写操作后要更新或删除缓存。
3. 缓存 key 命名要统一。
4. 不要缓存权限敏感数据，除非能正确失效。

### 11. 后端常见踩坑

| 坑 | 表现 | 解决 |
| --- | --- | --- |
| Controller 里写业务 | 后期难测难维护 | 业务放 Service |
| Entity 直接返回前端 | 泄露字段、结构耦合 | 使用 VO |
| 不限制 pageSize | 被大分页拖垮 | 统一分页工具 |
| 写操作不加事务 | 多表数据不一致 | `@Transactional` |
| 缓存只增不删 | 页面显示旧数据 | 写操作清缓存 |
| 日志打印密码/Token | 安全泄露 | 敏感字段脱敏 |
| 只前端校验 | 可被绕过 | 后端必须校验 |
| 直接 `readAllBytes` 上传 | 内存爆 | 限制读取大小 |

## 七、前端开发规范

### 1. TypeScript 类型优先

接口返回必须定义类型。

示例：

```ts
export interface CommentVO {
  id: number
  postId: number
  userId: number
  nickname: string
  avatar: string | null
  parentId: number
  content: string
  likeCount: number
  isTop: boolean
  status: string
  createTime: string
  postTitle: string
  replyToNickname: string | null
}
```

禁止用 `any` 糊弄。

### 2. API 封装

推荐每个业务模块一个 API 文件。

```ts
import { http } from '~/utils/network/http'

export interface CategoryVO {
  id: number
  name: string
  slug: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  current: number
  pages: number
}

export const categoryApi = {
  list: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    http.get<unknown, { data: PageResult<CategoryVO> }>('/admin/category', { params }),

  create: (data: { name: string; slug?: string }) =>
    http.post('/admin/category', data),
}
```

页面里不要直接写 axios/fetch。统一走 `utils/network/http.ts`。

### 3. Vue 页面结构

统一顺序：

```vue
<template>
  <div>...</div>
</template>

<script setup lang="ts">
// imports
// types
// state
// computed
// methods
// lifecycle
</script>

<style scoped lang="scss">
/* styles */
</style>
```

### 4. 管理端列表页模板

```vue
<template>
  <div class="page">
    <div class="page-header">
      <h2>分类管理</h2>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索名称" clearable @input="debouncedSearch" />
      </div>
      <div class="header-actions">
        <el-button type="primary" @click="openCreateDialog">新增</el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="name" label="名称" />
      <el-table-column prop="slug" label="Slug" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" @click="openEditDialog(row)">编辑</el-button>
          <el-button text type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      @current-change="loadData"
      @size-change="handleSizeChange"
    />
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { categoryApi, type CategoryVO } from '~/api/content/category'

const loading = ref(false)
const records = ref<CategoryVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')

let searchTimer: ReturnType<typeof setTimeout> | null = null

function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pageNum.value = 1
    loadData()
  }, 300)
}

function handleSizeChange() {
  pageNum.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await categoryApi.list({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
    })
    records.value = res.data.records
    total.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleDelete(row: CategoryVO) {
  await ElMessageBox.confirm(`确定删除 ${row.name}？`, '提示', { type: 'warning' })
  try {
    await categoryApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(loadData)

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>
```

### 5. 用户端 SSR 注意事项

Nuxt SSR 中不能随便使用浏览器对象。

错误写法：

```ts
const width = window.innerWidth
```

正确写法：

```ts
if (import.meta.client) {
  const width = window.innerWidth
}
```

或：

```ts
onMounted(() => {
  const width = window.innerWidth
})
```

### 6. 前端错误处理

统一用户可读错误：

```ts
try {
  await api.create(form)
  ElMessage.success('保存成功')
} catch (e: unknown) {
  ElMessage.error((e as Error).message || '保存失败')
}
```

不要把后端堆栈、原始异常对象直接展示给用户。

### 7. 表单开发规范

| 项 | 要求 |
| --- | --- |
| 必填字段 | 前端必填校验 + 后端 `@NotBlank` |
| 长度限制 | 前端限制 + 后端 `@Size` |
| 枚举值 | 前端 select + 后端白名单 |
| 文件上传 | 前端限制大小类型 + 后端再次检查 |
| 提交按钮 | 提交中 loading，防重复点击 |

### 8. 前端常见踩坑

| 坑 | 表现 | 解决 |
| --- | --- | --- |
| SSR 直接用 window | 构建或首屏报错 | `import.meta.client` 或 `onMounted` |
| 接口类型用 any | 后期改字段全崩 | 定义接口类型 |
| 页面里重复封装请求 | 错误处理不统一 | 统一 API 层 |
| 定时器不清理 | 切页后重复执行 | `onUnmounted` 清理 |
| 上传并发无限制 | 浏览器卡死/后端压力大 | 限制并发 |
| 表格不分页 | 数据多时卡顿 | 后端分页 |
| 只靠前端权限隐藏按钮 | 接口仍可被调用 | 后端必须鉴权 |

## 八、接口设计规范

### 1. REST 路径规范

| 操作 | 方法 | 路径示例 |
| --- | --- | --- |
| 列表 | GET | `/api/admin/post` |
| 详情 | GET | `/api/admin/post/{id}` |
| 新增 | POST | `/api/admin/post` |
| 修改 | PUT | `/api/admin/post/{id}` |
| 删除 | DELETE | `/api/admin/post/{id}` |
| 批量删除 | DELETE | `/api/admin/post/batch` |
| 状态变更 | PUT | `/api/admin/post/{id}/status` |

### 2. 前后台接口分组

| 端 | 路径前缀 |
| --- | --- |
| 用户端公开接口 | `/api/portal/...` |
| 管理端接口 | `/api/admin/...` |
| 认证接口 | `/api/portal/auth/...`、`/api/admin/auth/...` |

### 3. 响应格式

统一格式示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {}
}
```

分页格式示例：

```json
{
  "records": [],
  "total": 100,
  "current": 1,
  "pages": 5
}
```

### 4. 接口设计踩坑

| 坑 | 解决 |
| --- | --- |
| POST 参数一会儿 query 一会儿 body | 统一：复杂对象用 body，简单筛选用 query |
| 删除接口没有权限校验 | 后端校验本人或管理员 |
| 批量接口无限制 | 限制最大数量，例如 100 |
| 状态值随便传 | 后端 Set 白名单校验 |
| 返回字段不稳定 | 用明确 VO 固定契约 |

## 九、认证和权限设计

### 1. 登录态

当前项目使用 Sa-Token + HttpOnly Cookie：

| Cookie | 说明 |
| --- | --- |
| `Satoken` | 访问令牌 |
| `Satoken-Refresh` | 刷新令牌 |

生产环境 Cookie 要求 HTTPS。

### 2. 权限校验原则

| 接口 | 校验 |
| --- | --- |
| 用户资料 | 必须登录 |
| 删除自己的评论 | 登录 + userId 匹配 |
| 管理后台 | 管理员登录 |
| 上传 | 登录 + 类型/大小/频率限制 |
| 修改系统配置 | 管理员 + 审计日志 |

### 3. 验证码设计

当前项目滑块验证码做了场景绑定：

```text
login-password
login-code
register
send-code:register
send-code:forgot-password
forgot-password
admin-login
access-unlock
```

经验：验证码通过 token 必须绑定 IP、场景、过期时间，并且一次性消费。

## 十、日志和审计

### 1. 日志分类

| 日志 | 表或位置 | 用途 |
| --- | --- | --- |
| 应用日志 | `/var/log/weblog/application.log` | 排查异常 |
| 登录日志 | `t_login_log` | 登录成功/失败追踪 |
| 审计日志 | `t_audit_log` | 管理端操作追踪 |
| 归档登录日志 | `t_login_log_archive` | 长期追溯 |
| 归档审计日志 | `t_audit_log_archive` | 长期追溯 |

### 2. 写日志原则

应该记录：

```text
用户 ID、业务 ID、操作类型、结果、耗时、IP
```

禁止记录：

```text
密码、验证码、完整 Token、完整密钥、身份证、银行卡
```

### 3. 审计日志示例

```java
@AuditLog(module = "文章管理", operation = "DELETE", description = "删除文章")
@DeleteMapping("/{id}")
public Result<Void> delete(@PathVariable Long id) {
    postService.deletePost(id);
    return Result.success();
}
```

## 十一、测试策略

### 1. 后端单元测试

优先测试：

| 类型 | 示例 |
| --- | --- |
| 工具类 | 分页参数归一化、Token 签名 |
| 安全逻辑 | 验证码、限流、权限校验 |
| Service 规则 | 评论删除、状态变更、缓存更新 |
| Controller 限流 | 动态限流是否先于业务执行 |

运行指定测试：

```bash
mvn -f weblog-backend/pom.xml -pl weblog-api -am "-Dtest=AdminAiControllerDynamicRateLimitTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

运行模块测试：

```bash
mvn -f weblog-backend/pom.xml -pl weblog-api -am test
```

### 2. 前端验证

用户端：

```bash
pnpm --dir weblog-user lint
pnpm --dir weblog-user build
```

管理端：

```bash
pnpm --dir weblog-admin build
```

### 3. 回归脚本

P0 API 回归：

```powershell
pwsh -File scripts/p0-api-regression.ps1 -BaseUrl "http://127.0.0.1:9091"
```

验证码攻防回归：

```powershell
pwsh -File scripts/captcha-attack-regression.ps1 -BaseUrl "http://127.0.0.1:9091" -FailOnUnexpectedSuccess -RequireBlacklistActivation
```

### 4. 测试踩坑

| 坑 | 解决 |
| --- | --- |
| 只测成功路径 | 至少测失败、越权、空值、大值 |
| 本地过了 CI 不过 | 固定命令、固定 Node/JDK 版本 |
| 数据库测试污染 | 用事务回滚或测试库 |
| 前端只跑 dev | 必须跑 build，SSR 问题 dev 不一定暴露 |

## 十二、开发一个新功能的完整流程

以“新增文章预计阅读时间”为例。

### 1. 写需求

```text
管理员编辑文章时可填写预计阅读分钟数，用户端文章详情展示“预计阅读 5 分钟”。
```

### 2. 数据库设计

新增字段：

```sql
ALTER TABLE `t_post`
  ADD COLUMN `reading_time` int NULL DEFAULT NULL COMMENT '预计阅读分钟数';
```

同步更新：

1. `db/migration/Vx__add_post_reading_time.sql`
2. `database/sql/init/02-schema.sql`
3. `database/weblog.sql`

### 3. 后端 Entity

```java
private Integer readingTime;
```

### 4. 请求 DTO

```java
@Min(value = 1, message = "预计阅读时间不能小于 1 分钟")
@Max(value = 999, message = "预计阅读时间不能超过 999 分钟")
private Integer readingTime;
```

### 5. Service 写入

```java
post.setReadingTime(req.getReadingTime());
```

### 6. VO 返回

```java
vo.setReadingTime(post.getReadingTime());
```

### 7. 前端类型

```ts
export interface PostVO {
  id: number
  title: string
  readingTime: number | null
}
```

### 8. 管理端表单

```vue
<el-form-item label="预计阅读">
  <el-input-number v-model="form.readingTime" :min="1" :max="999" />
  <span>分钟</span>
</el-form-item>
```

### 9. 用户端展示

```vue
<span v-if="post.readingTime">预计阅读 {{ post.readingTime }} 分钟</span>
```

### 10. 验证

```bash
mvn -f weblog-backend/pom.xml -pl weblog-api -am "-Dtest=CommentServiceTest" "-Dsurefire.failIfNoSpecifiedTests=false" test
pnpm --dir weblog-admin build
pnpm --dir weblog-user build
```

## 十三、性能设计

### 1. 后端性能

| 场景 | 做法 |
| --- | --- |
| 列表页 | 分页、只查必要字段 |
| 热门数据 | Redis 缓存 |
| 搜索 | Lucene 或专门搜索引擎 |
| 统计计数 | Redis 计数 + 定时落库 |
| 大文件 | 流式读取，限制大小 |
| 外部接口 | 超时、重试、熔断思路 |

### 2. 前端性能

| 场景 | 做法 |
| --- | --- |
| 大列表 | 分页或虚拟滚动 |
| 图片 | 懒加载、压缩、CDN |
| 大依赖 | 动态导入、拆 chunk |
| 输入搜索 | 防抖 |
| 多文件上传 | 限制并发 |
| SSR | 避免首屏请求过多 |

### 3. 数据库性能

| 场景 | 做法 |
| --- | --- |
| 慢查询 | 加索引、减少返回列 |
| 计数慢 | 缓存计数或冗余字段 |
| 模糊搜索 | 小规模 like，大规模搜索引擎 |
| 历史日志大 | 归档表、定时清理 |

## 十四、安全设计清单

开发任何项目都检查：

| 类型 | 检查点 |
| --- | --- |
| 认证 | 是否所有敏感接口都要求登录 |
| 授权 | 是否校验本人、角色、管理员 |
| 输入 | 是否校验长度、格式、枚举 |
| XSS | 是否清理富文本和 Markdown |
| SQL 注入 | 是否使用参数绑定和 Wrapper |
| CSRF | Cookie 鉴权时要考虑来源校验 |
| 上传 | 是否限制类型、大小、频率 |
| 限流 | 登录、验证码、上传、发送邮件必须限流 |
| 日志 | 是否避免打印密码和 Token |
| 外链 | 是否有跳转确认或白名单 |
| 错误 | 是否避免暴露堆栈 |

## 十五、部署前检查

提交前：

```bash
git status
git diff --check
```

后端：

```bash
mvn -f weblog-backend/pom.xml clean install -DskipTests
```

用户端：

```bash
pnpm --dir weblog-user lint
pnpm --dir weblog-user build
```

管理端：

```bash
pnpm --dir weblog-admin build
```

数据库结构变更必须确认：

```text
db/migration 有增量脚本
database/sql/init/02-schema.sql 已同步
database/weblog.sql 已同步
```

## 十六、Git 开发流程

### 1. 日常流程

```bash
git status
git checkout -b feature/your-feature
# 开发
git add .
git commit -m "feat: add your feature"
git push github feature/your-feature
```

合并到主线后：

```bash
git checkout master
git pull github master
git push gitee master
```

### 2. 提交信息建议

| 前缀 | 用途 |
| --- | --- |
| `feat` | 新功能 |
| `fix` | 修 bug |
| `refactor` | 重构，不改变行为 |
| `docs` | 文档 |
| `test` | 测试 |
| `chore` | 构建、依赖、杂项 |
| `ci` | CI/CD |

示例：

```text
feat(content): add post reading time
fix(security): validate upload extension before signing
refactor(ai): remove unused comment review
docs: add deployment guide
```

## 十七、离线开发建议

如果以后只能内网开发，提前准备这些东西：

### 1. 本地文档包

保存以下文档：

```text
Spring Boot 官方文档
MyBatis-Plus 文档
Sa-Token 文档
Vue 3 文档
Nuxt 4 文档
Element Plus 文档
Docker Compose 文档
MySQL 8 文档
Redis 文档
```

### 2. 本地依赖缓存

提前在有网环境执行：

```bash
mvn -f weblog-backend/pom.xml dependency:go-offline
pnpm --dir weblog-user install
pnpm --dir weblog-admin install
```

保留：

```text
~/.m2/repository
pnpm store
node_modules 或 lockfile
```

查看 pnpm store：

```bash
pnpm store path
```

### 3. 本地模板库

建议维护一个 `templates/` 目录，保存：

```text
Controller 模板
Service 模板
DTO 模板
VO 模板
Vue 列表页模板
Vue 表单弹窗模板
SQL 建表模板
Flyway 迁移模板
Docker Compose 模板
Nginx 配置模板
GitHub Actions 模板
```

### 4. 离线排错方法

没有 AI 时按这个顺序排错：

1. 复现问题。
2. 记录输入、输出、错误日志。
3. 缩小范围：前端、后端、数据库、网络、部署。
4. 看最近改动：`git diff`。
5. 看日志：浏览器控制台、后端日志、Nginx 日志、Docker 日志。
6. 写最小测试或最小请求复现。
7. 修最小问题。
8. 跑相关测试。

## 十八、常用排错命令

### 1. Git

```bash
git status
git diff
git diff --check
git log --oneline -10
git show <commit>
```

### 2. Maven

```bash
mvn -f weblog-backend/pom.xml clean install -DskipTests
mvn -f weblog-backend/pom.xml -pl weblog-api -am test
mvn -f weblog-backend/pom.xml -pl weblog-api -am "-Dtest=TestClassName" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

### 3. pnpm

```bash
pnpm --dir weblog-user install
pnpm --dir weblog-user lint
pnpm --dir weblog-user build
pnpm --dir weblog-admin install
pnpm --dir weblog-admin build
```

### 4. Docker

```bash
docker ps
docker logs --tail=200 weblog-api
docker logs --tail=200 weblog-nginx
docker compose --env-file .env.prod -f docker-compose.prod.yml ps
docker compose --env-file .env.prod -f docker-compose.prod.yml up -d --remove-orphans
```

### 5. MySQL

```bash
docker exec -it weblog-mysql mysql -uroot -p
SHOW DATABASES;
USE weblog;
SHOW TABLES;
EXPLAIN SELECT * FROM t_post WHERE status = 'published';
```

### 6. Redis

```bash
docker exec -it weblog-redis redis-cli -a '<密码>' ping
docker exec -it weblog-redis redis-cli -a '<密码>' keys '*'
```

生产环境不要随便使用 `keys '*'`，数据多时会阻塞 Redis。排查时优先用 `scan`。

## 十九、最重要的开发原则

1. 先设计，再编码。
2. 先保证正确，再优化性能。
3. 后端永远不要信任前端。
4. 数据库结构变更必须有迁移脚本。
5. 接口返回结构要稳定。
6. 日志要能定位问题，但不能泄露敏感信息。
7. 复杂功能先做最小可用版本。
8. 每次只解决当前问题，不顺手大重构。
9. 每次修改后跑相关测试或构建。
10. 文档要跟代码同步，否则文档会变成误导。

## 二十、新项目从零到上线清单

如果你以后做一个新全栈项目，可以按这个清单执行：

### 1. 立项

- 写一句话目标。
- 列用户角色。
- 列核心功能。
- 写核心业务流程。

### 2. 架构

- 决定前后端是否分离。
- 决定用户端是否 SSR。
- 决定是否需要管理端。
- 决定数据库和缓存。
- 画目录结构。

### 3. 数据库

- 设计实体表。
- 设计状态字段。
- 设计索引。
- 写初始化 SQL。
- 写迁移脚本。

### 4. 后端

- 建模块。
- 写 Entity、Mapper。
- 写 DTO、VO。
- 写 Service。
- 写 Controller。
- 加权限、限流、审计。
- 写测试。

### 5. 前端

- 建页面路由。
- 写 API 类型。
- 写列表页、详情页、表单页。
- 处理 loading、empty、error。
- 加权限路由。
- 跑 build。

### 6. 联调

- 用浏览器测试主流程。
- 用接口工具测异常参数。
- 看后端日志。
- 看数据库数据。
- 看 Redis 缓存。

### 7. 安全

- 检查登录和权限。
- 检查 XSS。
- 检查上传限制。
- 检查限流。
- 检查敏感日志。
- 检查 Cookie 和 HTTPS。

### 8. 部署

- 写 Dockerfile。
- 写 docker-compose。
- 写 Nginx。
- 配域名和 SSL。
- 配 CI/CD。
- 配生产环境变量。

### 9. 上线后

- 看错误日志。
- 看慢查询。
- 看磁盘和内存。
- 配备份。
- 配日志清理。
- 记录常见问题。

## 二十一、可复制的功能开发检查表

每做一个功能，按下面勾：

```text
[ ] 需求说明已写清楚
[ ] 用户角色和权限已确认
[ ] 数据库表/字段已设计
[ ] 索引已设计
[ ] Flyway 迁移已添加
[ ] 初始化 SQL 已同步
[ ] Entity 已更新
[ ] DTO/VO 已定义
[ ] Service 已实现
[ ] Controller 已实现
[ ] 参数校验已添加
[ ] 权限校验已添加
[ ] 写操作审计日志已添加
[ ] 缓存更新/失效已处理
[ ] 前端 API 类型已定义
[ ] 页面 loading/error/empty 已处理
[ ] 表单防重复提交已处理
[ ] 后端测试已跑
[ ] 前端 build 已跑
[ ] 文档已更新
```

## 二十二、最后建议

全栈开发最难的不是写某一行代码，而是保持系统长期可维护。以后不能使用 AI 时，更要依赖固定流程、代码模板、检查清单和测试。

建议你把这份文档和当前项目一起作为“样板工程”。以后做新项目时，不要从空白开始，而是先参考这里的架构、目录、接口、数据库、部署和安全清单，再按新业务裁剪。
