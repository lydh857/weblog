import { useUserStore } from '~/stores/user'
import { authApi } from '~/api/auth/auth'

const REMEMBER_KEY = 'weblog_admin_remember'

function hasRememberLoginEnabled() {
  if (!import.meta.client) {
    return false
  }

  try {
    const raw = localStorage.getItem(REMEMBER_KEY)
    if (!raw) {
      return false
    }
    const saved = JSON.parse(raw) as { remember?: boolean, expireAt?: number }
    if (!saved.remember) {
      return false
    }
    if (saved.expireAt && Date.now() > saved.expireAt) {
      localStorage.removeItem(REMEMBER_KEY)
      return false
    }
    return true
  } catch {
    localStorage.removeItem(REMEMBER_KEY)
    return false
  }
}

async function tryRememberLogin() {
  if (!hasRememberLoginEnabled()) {
    return false
  }

  try {
    const userStore = useUserStore()
    const res = await authApi.rememberLogin()
    if (!res.data) {
      return false
    }
    userStore.setUser(res.data)
    return true
  } catch {
    localStorage.removeItem(REMEMBER_KEY)
    return false
  }
}

export default defineNuxtRouteMiddleware(async (to) => {
  if (to.path === '/login') return

  const userStore = useUserStore()
  if (!userStore.userInfo.userId) {
    const remembered = await tryRememberLogin()
    if (remembered) {
      return
    }
  }

  if (!userStore.userInfo.userId) {
    await userStore.fetchUser()
  }

  if (!userStore.userInfo.userId) {
    return navigateTo('/login')
  }
})
