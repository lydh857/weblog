export default defineNuxtConfig({
  compatibilityDate: '2026-02-11',
  // SSR 模式
  ssr: true,

  // 预加载 Google Fonts，避免 FOUT
  app: {
    head: {
      link: [
        { rel: 'icon', type: 'image/png', href: '/brand/logo.png' },
        { rel: 'preconnect', href: 'https://fonts.googleapis.com' },
        { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: '' },
        { rel: 'stylesheet', href: 'https://fonts.googleapis.com/css2?family=Pacifico&display=block' },
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
