import { useSiteConfigState } from '~/composables/useSiteConfig'

/**
 * 应用启动时从后端拉取站点配置（site_name 等），
 * 用数据库中的值覆盖代码默认值。
 *
 * 使用公开接口 /portal/site-config，无需登录即可访问，
 * 保证登录页也能显示正确的品牌名。
 */
export default defineNuxtPlugin(async () => {
  const state = useSiteConfigState()

  if (import.meta.server && state.value.loaded) return

  try {
    // 使用原生 fetch 而非 $fetch，确保在 SPA 开发模式下走 Vite proxy
    const res = await fetch('/api/portal/site-config')
    const json = await res.json()

    if (json?.code === 200 && json.data) {
      const siteName = json.data.siteName?.trim()
      const siteCopyright = json.data.siteFooterCopyright?.trim()
      if (siteName) state.value.siteName = siteName
      if (siteCopyright) state.value.siteCopyright = siteCopyright
    }
  } catch {
    // 拉取失败时保留默认值
  } finally {
    state.value.loaded = true
  }
})
