export type ReleaseNoteType = 'feature' | 'fix' | 'security' | 'performance'

export interface ReleaseNoteItem {
  type: ReleaseNoteType
  title: string
  description: string
}

export interface ReleaseVersion {
  version: string
  releaseDate: string
  title: string
  summary: string
  items: ReleaseNoteItem[]
}

export interface AppReleaseInfo {
  productName: string
  currentVersion: string
  updatedAt: string
  releaseNotes: ReleaseVersion[]
}

export const appReleaseInfo: AppReleaseInfo = {
  productName: 'Weblog 用户端',
  currentVersion: '1.12.0',
  updatedAt: '2026-04-25',
  releaseNotes: [
    {
      version: '1.12.0',
      releaseDate: '2026-04-25',
      title: '版本更新页上线',
      summary: '新增专门的版本更新页面，用户可以直接查看最近新增了哪些能力、修复了哪些问题，以及当前正在使用的系统版本。',
      items: [
        {
          type: 'feature',
          title: '新增版本更新页面',
          description: '在页脚进入“版本更新”后，可以按时间查看每次更新内容，不再需要从公告或文章里寻找变更说明。',
        },
        {
          type: 'feature',
          title: '支持按更新类型筛选',
          description: '可以快速筛选“新增”“修复”“安全”“优化”，更容易找到自己关心的变化。',
        },
        {
          type: 'feature',
          title: '页脚展示当前版本',
          description: '页面底部展示当前版本号，反馈问题时可以直接说明所处版本，方便定位问题。',
        },
      ],
    },
    {
      version: '1.11.2',
      releaseDate: '2026-04-25',
      title: '访问稳定性修复',
      summary: '修复近期影响访问稳定性的细节问题，降低页面初始化失败、登录页遮挡和重复加载带来的干扰。',
      items: [
        {
          type: 'fix',
          title: '提升页面初始化稳定性',
          description: '修复部分页面打开时可能出现初始化异常的问题，让页面加载更加稳定。',
        },
        {
          type: 'fix',
          title: '优化启动加载体验',
          description: '减少启动动画在登录和跳转场景中的干扰，页面进入速度更自然。',
        },
        {
          type: 'performance',
          title: '优化构建与资源拆分',
          description: '调整前端资源组织方式，降低更新后出现资源加载失败的概率。',
        },
      ],
    },
    {
      version: '1.11.1',
      releaseDate: '2026-04-24',
      title: '内容安全与验证码增强',
      summary: '加强文章内容、外链打开和验证码校验的安全性，减少异常链接和恶意请求对用户体验的影响。',
      items: [
        {
          type: 'security',
          title: '增强富文本内容安全',
          description: '文章中的外部链接和富文本内容会经过更严格的安全处理，降低跳转风险。',
        },
        {
          type: 'security',
          title: '验证码按场景校验',
          description: '登录、注册等不同操作使用独立验证码校验结果，避免一次验证被错误复用。',
        },
        {
          type: 'fix',
          title: '减少异常请求影响',
          description: '优化安全网关拦截后的页面处理方式，避免用户浏览内容时被异常状态打断。',
        },
      ],
    },
    {
      version: '1.11.0',
      releaseDate: '2026-04-21',
      title: '公告与品牌体验升级',
      summary: '优化公告弹窗、站点品牌展示和页面入口，让重要通知更清晰，同时减少重复打扰。',
      items: [
        {
          type: 'feature',
          title: '公告弹窗体验升级',
          description: '公告从弹窗到详情的流程更顺畅，关闭后不再频繁打扰用户。',
        },
        {
          type: 'fix',
          title: '修复公告重复显示',
          description: '修复关闭公告后切换浏览器标签页仍可能再次弹出的问题。',
        },
        {
          type: 'feature',
          title: '统一站点品牌展示',
          description: '统一页面中的站点名称、图标和展示风格，整体识别更一致。',
        },
      ],
    },
    {
      version: '1.10.0',
      releaseDate: '2026-04-18',
      title: '友链、广告与站点配置完善',
      summary: '完善友情链接、广告申请和站点配置相关能力，提升内容合作和站点运营体验。',
      items: [
        {
          type: 'feature',
          title: '友链申请流程增强',
          description: '友情链接申请、审核和状态展示更完整，申请后可以更清楚地了解处理进度。',
        },
        {
          type: 'feature',
          title: '广告申请体验优化',
          description: '广告投放申请流程更清晰，填写、提交和修改体验更稳定。',
        },
        {
          type: 'fix',
          title: '修复申请信息清空问题',
          description: '修复部分场景下清空广告信息后按钮状态不一致的问题。',
        },
      ],
    },
    {
      version: '1.9.0',
      releaseDate: '2026-04-15',
      title: '文章详情稳定性提升',
      summary: '集中修复文章详情页加载、刷新和异常恢复问题，让阅读过程更稳定。',
      items: [
        {
          type: 'fix',
          title: '修复文章详情卡住加载',
          description: '减少文章详情页长时间停留在加载状态的情况。',
        },
        {
          type: 'fix',
          title: '修复误判文章不存在',
          description: '网络波动或安全网关返回异常时，不再轻易把文章判断为不存在。',
        },
        {
          type: 'performance',
          title: '优化文章首屏恢复',
          description: '文章详情加载失败时会尝试在浏览器端恢复，减少用户手动刷新次数。',
        },
      ],
    },
    {
      version: '1.8.0',
      releaseDate: '2026-04-10',
      title: '图片与外链访问优化',
      summary: '优化图片资源、外链跳转和上传文件访问，让文章封面、分类列表和广告跳转更可靠。',
      items: [
        {
          type: 'fix',
          title: '修复历史图片显示问题',
          description: '兼容旧文章中的图片地址，减少封面或正文图片无法显示的情况。',
        },
        {
          type: 'security',
          title: '外部链接安全跳转',
          description: '广告和文章外链会经过安全处理，帮助用户识别即将离开本站的跳转。',
        },
        {
          type: 'performance',
          title: '优化本地媒体访问',
          description: '上传图片和本地媒体访问更稳定，减少图片加载失败。',
        },
      ],
    },
    {
      version: '1.7.0',
      releaseDate: '2026-04-04',
      title: '首屏性能优化',
      summary: '减少首页和全局组件的首屏加载压力，让页面打开更快、交互响应更轻。',
      items: [
        {
          type: 'performance',
          title: '首页非关键内容延后加载',
          description: '将不影响首屏阅读的弹窗、页脚和辅助组件延后加载，优先保障页面主体内容显示。',
        },
        {
          type: 'performance',
          title: '优化公告和弹窗挂载时机',
          description: '减少首次进入页面时的脚本负担，弹窗在需要时再加载。',
        },
        {
          type: 'fix',
          title: '优化分页暗色模式',
          description: '暗色模式下分页和筛选区域颜色更统一，阅读更舒适。',
        },
      ],
    },
    {
      version: '1.6.0',
      releaseDate: '2026-03-29',
      title: '用户端体验升级',
      summary: '整理用户端页面结构，优化内容浏览、审核状态和移动端关键交互。',
      items: [
        {
          type: 'feature',
          title: '页面结构更统一',
          description: '首页、列表页、详情页的布局和交互更一致，切换页面时更顺手。',
        },
        {
          type: 'feature',
          title: '个人资料审核链路完善',
          description: '用户资料修改后的审核状态更清楚，减少提交后不知道结果的情况。',
        },
        {
          type: 'security',
          title: '认证访问控制加固',
          description: '登录状态、受保护操作和异常跳转处理更严格，账号使用更安全。',
        },
      ],
    },
    {
      version: '1.5.0',
      releaseDate: '2026-03-25',
      title: '暗色模式与移动端细节完善',
      summary: '统一暗色模式样式，优化移动端菜单、轮播图、骨架屏和启动页体验。',
      items: [
        {
          type: 'feature',
          title: '暗色模式体验统一',
          description: '文章、专题、首页卡片和启动页的暗色样式更加协调，夜间浏览更舒适。',
        },
        {
          type: 'fix',
          title: '修复主题切换闪烁',
          description: '减少打开页面或切换主题时出现白屏、闪烁和颜色跳变。',
        },
        {
          type: 'fix',
          title: '优化移动端点击反馈',
          description: '移动端菜单、轮播和图片加载反馈更稳定，误触和空白感更少。',
        },
      ],
    },
    {
      version: '1.4.0',
      releaseDate: '2026-03-21',
      title: '搜索、排行与移动端浏览增强',
      summary: '增强站内搜索信息、排行兜底和移动端筛选分页，让用户更容易找到内容。',
      items: [
        {
          type: 'feature',
          title: '搜索信息更丰富',
          description: '搜索结果和搜索提示展示更多上下文，帮助用户更快判断内容是否相关。',
        },
        {
          type: 'feature',
          title: '排行空榜自动兜底',
          description: '当日榜数据不足时自动展示最新内容，避免页面空白。',
        },
        {
          type: 'fix',
          title: '优化移动端筛选分页',
          description: '移动端分类、专题和标签页面切换更流畅，分页位置更自然。',
        },
      ],
    },
    {
      version: '1.3.0',
      releaseDate: '2026-03-18',
      title: '分类路由与安全校验补齐',
      summary: '完善分类筛选、导航搜索和用户操作校验，减少重复提交和异常访问。',
      items: [
        {
          type: 'feature',
          title: '分类筛选链路完善',
          description: '分类页面的筛选、跳转和回退更稳定，浏览不同内容分类更方便。',
        },
        {
          type: 'feature',
          title: '导航搜索升级',
          description: '顶部搜索入口更易用，搜索提示与页面跳转衔接更顺畅。',
        },
        {
          type: 'security',
          title: '关键操作防重复提交',
          description: '点赞、评论、访问解锁等操作增加防抖和幂等保护，减少误操作。',
        },
      ],
    },
    {
      version: '1.2.0',
      releaseDate: '2026-03-16',
      title: '公告、广告与站点基础能力完善',
      summary: '补齐公告通知、广告申请和站点配置能力，让用户端具备完整运营入口。',
      items: [
        {
          type: 'feature',
          title: '通知中心上线',
          description: '用户可以集中查看站点通知，不再只依赖弹窗提示。',
        },
        {
          type: 'feature',
          title: '广告申请流程完善',
          description: '支持广告坑位展示、申请提交和投放信息填写，合作流程更完整。',
        },
        {
          type: 'feature',
          title: '站点配置能力补齐',
          description: '站点名称、页脚文案、统计信息等展示内容可统一配置，页面信息更准确。',
        },
      ],
    },
    {
      version: '1.1.0',
      releaseDate: '2026-03-11',
      title: '首页与个人入口体验升级',
      summary: '优化首页首屏、导航、榜单和个人入口，提升首次访问和刷新后的稳定性。',
      items: [
        {
          type: 'performance',
          title: '首页打开更顺滑',
          description: '优化首页导航、轮播和加载更多效果，减少首屏抖动和下沉感。',
        },
        {
          type: 'fix',
          title: '修复登录态刷新抖动',
          description: '刷新页面后头像、登录按钮和用户菜单状态更稳定。',
        },
        {
          type: 'feature',
          title: '个人入口更易用',
          description: '头像菜单改为更直观的悬浮入口，个人中心、收藏和评论入口更容易找到。',
        },
      ],
    },
    {
      version: '1.0.0',
      releaseDate: '2026-03-09',
      title: '用户端基础版本',
      summary: '用户端基础能力成型，支持首页浏览、文章阅读、登录互动和基础内容导航。',
      items: [
        {
          type: 'feature',
          title: '基础内容浏览',
          description: '支持首页、文章详情、分类、标签、专题、排行等基础内容入口。',
        },
        {
          type: 'feature',
          title: '账号登录与个人入口',
          description: '支持用户登录、退出、头像菜单和基础个人中心入口。',
        },
        {
          type: 'security',
          title: '基础安全防护',
          description: '完成登录、请求校验和设备识别相关基础防护，保障主要访问链路可用。',
        },
      ],
    },
  ],
}

export const releaseNoteTypeLabels: Record<ReleaseNoteType, string> = {
  feature: '新增',
  fix: '修复',
  security: '安全',
  performance: '优化',
}
