const cspPolicy = [
  "default-src 'self'",
  "script-src 'self'",
  "style-src 'self' https://fonts.googleapis.com",
  "img-src 'self' data: https:",
  "font-src 'self' data: https://fonts.gstatic.com",
  "connect-src 'self' https: http: ws: wss:",
  "frame-ancestors 'self'",
  "base-uri 'self'",
  "form-action 'self'",
  "report-uri /api/security/csp/report",
  "report-to csp-endpoint",
].join('; ')

const defaultCspStage = import.meta.env.PROD ? 'enforce' : 'report-only'
const cspStage = (import.meta.env.NUXT_CSP_STAGE || defaultCspStage).toLowerCase()
const configuredAdminBaseUrl = (import.meta.env.NUXT_APP_BASE_URL || '/admin/').trim()
const adminBaseUrl = configuredAdminBaseUrl.endsWith('/') ? configuredAdminBaseUrl : `${configuredAdminBaseUrl}/`

const securityHeaders: Record<string, string> = {
  'X-Frame-Options': 'SAMEORIGIN',
  'X-Content-Type-Options': 'nosniff',
  'Referrer-Policy': 'strict-origin-when-cross-origin',
  'Permissions-Policy': 'camera=(), microphone=(), geolocation=()',
  'Reporting-Endpoints': 'csp-endpoint="/api/security/csp/report"',
  'Report-To': '{"group":"csp-endpoint","max_age":10886400,"endpoints":[{"url":"/api/security/csp/report"}]}',
}

if (cspStage === 'enforce' || cspStage === 'dual') {
  securityHeaders['Content-Security-Policy'] = cspPolicy
}

if (cspStage === 'report-only' || cspStage === 'dual') {
  securityHeaders['Content-Security-Policy-Report-Only'] = cspPolicy
}

export default defineNuxtConfig({
  compatibilityDate: '2026-02-11',
  // SPA 模式（管理端不需要 SSR）
  ssr: false,

  modules: [
    '@element-plus/nuxt',
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
    optimizeDeps: {
      include: ['dayjs'],
    },
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
              return
            }

            if (id.includes('node_modules/@element-plus/icons-vue/')) {
              return 'vendor-element-icons'
            }

            if (id.includes('node_modules/echarts/')) {
              const echartsPath = id.split('node_modules/echarts/')[1]
              const echartsSegments = echartsPath?.split('/') ?? []
              const rawSegment = echartsSegments[0] === 'lib' ? echartsSegments[1] : echartsSegments[0]
              const echartsSegment = rawSegment?.replace(/\.[a-z]+$/i, '')

              const runtimeCoreSegments = new Set([
                'core',
                'model',
                'util',
                'scale',
                'data',
                'visual',
                'animation',
                'layout',
              ])

              const runtimeViewSegments = new Set([
                'view',
                'chart',
                'component',
                'coord',
                'extension',
                'index',
              ])

              if (!echartsSegment || runtimeCoreSegments.has(echartsSegment)) {
                return 'vendor-echarts-runtime-core'
              }

              if (runtimeViewSegments.has(echartsSegment)) {
                return 'vendor-echarts-runtime-view'
              }

              const optionalSegments = new Set(['export', 'charts', 'components', 'renderers'])
              if (optionalSegments.has(echartsSegment)) {
                return
              }

              return `vendor-echarts-${echartsSegment}`
            }

            if (id.includes('node_modules/zrender/')) {
              return 'vendor-zrender'
            }

            if (id.includes('node_modules/md-editor-v3/')) {
              return 'vendor-md-editor-core'
            }

            if (id.includes('node_modules/@codemirror/')) {
              const packagePath = id.split('node_modules/@codemirror/')[1]
              const packageName = packagePath?.split('/')[0]
              if (packageName === 'legacy-modes') {
                const modePath = packagePath.split('/mode/')[1]
                const modeName = modePath?.split('/')[0]
                const normalizedModeName = modeName?.replace(/[^a-z0-9-]/gi, '-').toLowerCase()
                return normalizedModeName ? `vendor-codemirror-legacy-${normalizedModeName}` : 'vendor-codemirror-legacy'
              }
              return packageName ? `vendor-codemirror-${packageName}` : 'vendor-codemirror'
            }

            if (id.includes('node_modules/@lezer/')) {
              const packagePath = id.split('node_modules/@lezer/')[1]
              const packageName = packagePath?.split('/')[0]
              return packageName ? `vendor-lezer-${packageName}` : 'vendor-lezer'
            }

            if (id.includes('node_modules/markdown-it/')) {
              return 'vendor-markdown-it'
            }

            if (id.includes('node_modules/markdown-it-')) {
              return 'vendor-markdown-it-plugins'
            }

            if (id.includes('node_modules/medium-zoom/')) {
              return 'vendor-medium-zoom'
            }

            if (id.includes('node_modules/xss/')) {
              return 'vendor-xss'
            }

            if (id.includes('node_modules/katex/')) {
              return 'vendor-katex'
            }

            if (id.includes('node_modules/mermaid/')) {
              return 'vendor-mermaid'
            }

            if (id.includes('node_modules/prettier/')) {
              return 'vendor-prettier'
            }

            return
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

  routeRules: {
    '/**': {
      headers: securityHeaders,
    },
  },

  // 全局标题配置
  app: {
    baseURL: adminBaseUrl,
    head: {
      title: 'zhhhkl-管理端',
      link: [
        { rel: 'icon', type: 'image/png', href: `${adminBaseUrl}brand/logo.png` },
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
