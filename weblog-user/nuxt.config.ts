const securityHeaders: Record<string, string> = {
  'X-Frame-Options': 'SAMEORIGIN',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  'Reporting-Endpoints': 'csp-endpoint="/api/security/csp/report"',
  'Report-To': '{"group":"csp-endpoint","max_age":10886400,"endpoints":[{"url":"/api/security/csp/report"}]}',
}

export default defineNuxtConfig({
  compatibilityDate: '2026-02-11',
  // SSR 模式
  ssr: true,

  // 站点全局 head
  app: {
    head: {
      meta: [
        {
          name: 'viewport',
          content: 'width=device-width, initial-scale=1, maximum-scale=1, minimum-scale=1, user-scalable=no, viewport-fit=cover',
        },
      ],
      link: [
        { rel: 'icon', type: 'image/png', href: '/brand/logo.png' },
      ],
    },
  },

  modules: [
    '@vueuse/nuxt',
    '@pinia/nuxt',
    '@nuxt/icon',
    '@nuxt/eslint',
  ],

  imports: {
    dirs: [
      'composables/**',
      'utils/**',
    ],
  },

  // 组件目录按领域拆分，保持组件名不变便于渐进迁移。
  components: [
    { path: '~/components/ad', pathPrefix: false },
    { path: '~/components/announcement', pathPrefix: false },
    { path: '~/components/article', pathPrefix: false },
    { path: '~/components/auth', pathPrefix: false },
    { path: '~/components/category', pathPrefix: false },
    { path: '~/components/common', pathPrefix: false },
    { path: '~/components/home', pathPrefix: false },
    { path: '~/components/link', pathPrefix: false },
    { path: '~/components/ranking', pathPrefix: false },
    { path: '~/components/search', pathPrefix: false },
    { path: '~/components/topic', pathPrefix: false },
    { path: '~/components/user', pathPrefix: false },
    { path: '~/components/ui', prefix: 'Ui' },
  ],

  // SCSS 全局变量
  css: ['~/assets/scss/main.scss'],

  vite: {
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: '@use "~/assets/scss/variables" as *;',
        },
      },
    },
  },

  // 运行时配置
  runtimeConfig: {
    public: {
      apiBase: import.meta.env.NUXT_PUBLIC_API_BASE || 'http://localhost:9091/api',
    },
  },

  devtools: { enabled: import.meta.dev },

  // 页面缓存配置（SWR - Stale-While-Revalidate）
  routeRules: {
    // 基础安全响应头（CSP 统一由网关层下发，避免重复策略冲突）
    '/**': {
      headers: securityHeaders,
    },
    // 首页缓存 10 分钟
    '/': { swr: 600 },
    // 文章页缓存 30 分钟
    '/post/**': { swr: 1800 },
    // 分类页缓存 15 分钟
    '/category/**': { swr: 900 },
    // 归档页缓存 30 分钟
    '/archive/**': { swr: 1800 },
    // 公告页缓存 10 分钟
    '/announcement/**': { swr: 600 },
    // API 请求不缓存
    '/api/**': { cache: false },
  },
})
