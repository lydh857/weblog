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
          manualChunks: {
            // UI 组件库 - 大约 300KB
            'vendor-element': ['element-plus'],
            // 图表库 - 大约 600KB（按需引入后）
            'vendor-echarts': ['echarts'],
          },
        },
      },
      // 代码分割阈值优化
      chunkSizeWarningLimit: 500,
    },
  },

  runtimeConfig: {
    public: {
      apiBase: 'http://localhost:9091/api',
    },
  },

  devtools: { enabled: true },

  // 全局标题配置
  app: {
    head: {
      title: 'zhhhkl-管理端',
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