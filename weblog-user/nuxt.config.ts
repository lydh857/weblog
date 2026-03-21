export default defineNuxtConfig({
  compatibilityDate: '2026-02-11',
  // SSR 模式
  ssr: true,

  // 站点全局 head
  app: {
    head: {
      link: [
        { rel: 'icon', type: 'image/png', href: '/brand/logo.png' },
      ],
    },
  },

  modules: [
    '@vueuse/nuxt',
    '@pinia/nuxt',
    '@nuxt/icon',
  ],

  // SCSS 全局变量
  css: ['~/assets/scss/main.scss'],

  vite: {
    css: {
      preprocessorOptions: {
        scss: {
          api: 'modern-compiler',
          additionalData: '@use "~/assets/scss/variables" as *;',
        },
      },
    },
  },

  // 运行时配置
  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || 'http://localhost:9091/api',
    },
  },

  devtools: { enabled: process.env.NODE_ENV === 'development' },

  // 页面缓存配置（SWR - Stale-While-Revalidate）
  routeRules: {
    // 基础安全响应头（先以 Report-Only 观察 CSP）
    '/**': {
      headers: {
        'X-Frame-Options': 'SAMEORIGIN',
        'X-Content-Type-Options': 'nosniff',
        'Referrer-Policy': 'strict-origin-when-cross-origin',
        'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
        'Content-Security-Policy-Report-Only': "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; img-src 'self' data: https:; font-src 'self' data: https://fonts.gstatic.com; connect-src 'self' https: http: ws: wss:; frame-ancestors 'self'; base-uri 'self'; form-action 'self'",
      },
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
