import { siteConfigApi } from '~/api/system/site-config'

export default defineNuxtPlugin(async () => {
  const state = useSiteConfigState()

  // 服务端同一次渲染中避免重复请求；客户端每次启动都拉最新，覆盖可能的 SSR/SWR 旧快照
  if (import.meta.server && state.value.loaded) return

  try {
    const res = await siteConfigApi.getPublic()
    const siteName = res.data.siteName?.trim()
    const siteDescription = res.data.siteDescription?.trim()

    if (siteName) {
      state.value.siteName = siteName
    }
    if (siteDescription) {
      state.value.siteDescription = siteDescription
    }
  } catch {
    // 失败时保留默认值
  } finally {
    state.value.loaded = true
  }
})
