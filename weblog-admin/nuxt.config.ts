export default defineNuxtConfig({
  compatibilityDate: '2026-02-11',
  // SPA 模式（管理端不需要 SSR）
  ssr: false,

  modules: [
    '@vueuse/nuxt',
    '@pinia/nuxt',
    '@nuxt/icon',
    '@nuxt/image',
  ],

  imports: {
    dirs: [
      'composables/**',
      'utils/**',
    ],
  },

  // 按领域拆分组件目录，保持现有组件命名习惯。
  components: [
    { path: '~/components/ai', pathPrefix: false },
    { path: '~/components/auth', pathPrefix: false },
    { path: '~/components/common', pathPrefix: false },
    { path: '~/components/editor', pathPrefix: false },
  ],

  css: ['~/assets/scss/main.scss'],

  vite: {
    css: {
      preprocessorOptions: {
        scss: {
          additionalData: '@use "~/assets/scss/variables" as *;',
        },
      },
    },
    resolve: {},
    server: {
      proxy: {
        '/api': {
          target: 'http://localhost:9091',
          changeOrigin: true,
        },
        // 代理本地上传文件，解决开发环境跨域（裁剪组件 canvas 需要同源图片）
        '/uploads': {
          target: 'http://localhost:9091',
          changeOrigin: true,
        },
      },
    },
    // 代码分割优化
    build: {
      rollupOptions: {
        output: {
          manualChunks(id) {
            if (!id.includes('node_modules')) return

            if (
              id.includes('node_modules/vue/') ||
              id.includes('node_modules/@vue/') ||
              id.includes('node_modules/vue-router/') ||
              id.includes('node_modules/pinia/')
            ) {
              return 'vendor-vue'
            }

            if (id.includes('node_modules/element-plus/') || id.includes('node_modules/@element-plus/')) {
              return 'vendor-element'
            }

            if (id.includes('node_modules/echarts/')) {
              return 'vendor-echarts'
            }

            if (id.includes('node_modules/zrender/')) {
              return 'vendor-zrender'
            }
          },
        },
      },
      // 代码分割阈值优化
      chunkSizeWarningLimit: 1000,
    },
  },

  runtimeConfig: {
    public: {
      apiBase: process.env.NUXT_PUBLIC_API_BASE || '/api',
      portalBaseUrl: process.env.NUXT_PUBLIC_PORTAL_BASE_URL || '',
    },
  },

  devtools: { enabled: process.env.NODE_ENV === 'development' },

  // 全局标题配置
  app: {
    head: {
      title: 'zhhhkl-管理端',
      link: [
        { rel: 'icon', type: 'image/png', href: '/brand/logo.png' },
      ],
    }
  },

  // 图片懒加载配置
  image: {
    provider: 'ipx',
    screens: {
      xs: 320,
      sm: 640,
      md: 768,
      lg: 1024,
      xl: 1280,
      xxl: 1536,
    },
  },
})
