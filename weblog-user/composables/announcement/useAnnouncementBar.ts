/** 公告栏可见状态（全局共享） */
const bannerVisible = ref(false)

export function useAnnouncementBar() {
  return { bannerVisible }
}
