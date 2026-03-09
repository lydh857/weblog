import { useUserStore } from '~/stores/user'

export default defineNuxtRouteMiddleware(async (to) => {
  if (to.path === '/login') return

  const userStore = useUserStore()
  if (!userStore.userInfo.userId) {
    await userStore.fetchUser()
  }

  if (!userStore.userInfo.userId) {
    return navigateTo('/login')
  }
})
