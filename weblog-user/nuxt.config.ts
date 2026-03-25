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
      script: [
        {
          id: 'weblog-init-color-scheme',
          children: "(() => { try { const keys = ['vueuse-color-scheme', 'nuxt-color-mode', 'theme', 'color-scheme']; const normalizeMode = (value) => { if (value === null || value === undefined) return ''; const text = String(value).trim().toLowerCase().replace(/^\"|\"$/g, ''); if (text === 'dark' || text === 'true' || text === '1') return 'dark'; if (text === 'light' || text === 'false' || text === '0') return 'light'; if (text === 'auto' || text === 'system') return 'auto'; return ''; }; const parseMode = (raw) => { const direct = normalizeMode(raw); if (direct) return direct; try { const parsed = JSON.parse(String(raw)); if (typeof parsed === 'string' || typeof parsed === 'number' || typeof parsed === 'boolean') return normalizeMode(parsed); if (parsed && typeof parsed === 'object') return normalizeMode(parsed.preference ?? parsed.value ?? parsed.mode ?? parsed.theme ?? parsed.colorMode ?? ''); } catch (_) {} return ''; }; let mode = ''; for (const key of keys) { const raw = localStorage.getItem(key); if (raw === null) continue; mode = parseMode(raw); if (mode) break; } const media = window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)').matches : false; const isDark = mode === 'dark' || ((mode === 'auto' || mode === '') && media); const root = document.documentElement; const darkMask = 'radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%), radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%), linear-gradient(180deg, #171b20, #101215)'; const lightMask = 'radial-gradient(120% 120% at 0% 0%, rgba(37, 99, 235, 0.09), transparent 46%), radial-gradient(120% 120% at 100% 100%, rgba(14, 165, 233, 0.11), transparent 52%), linear-gradient(180deg, #ffffff, #f8fafc)'; root.classList.toggle('dark', isDark); root.style.colorScheme = isDark ? 'dark' : 'light'; root.style.backgroundColor = isDark ? '#101215' : '#f8fafc'; root.setAttribute('data-startup-theme', isDark ? 'dark' : 'light'); root.style.setProperty('--startup-mask-bg', isDark ? darkMask : lightMask); root.style.setProperty('--startup-loader-text-color', isDark ? '#d7e4fb' : '#4e6ea8'); root.style.setProperty('--startup-loader-shadow', isDark ? 'drop-shadow(0 10px 24px rgba(2, 6, 23, 0.6))' : 'drop-shadow(0 8px 20px rgba(32, 61, 120, 0.2))'); } catch (_) {} })();",
        },
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
