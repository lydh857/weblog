import { defineStore } from 'pinia'
import { setToken, removeToken, syncUserCookie } from '~/utils/http'
import { userApi } from '~/api/user'

interface UserInfo {
  userId: number | null
  email: string
  nickname: string
  avatar: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const LOGGED_IN_COOKIE = 'weblog_logged_in'
  const loggedInCookie = useCookie<string | number | null>(LOGGED_IN_COOKIE)

  const userInfo = ref<UserInfo>({
    userId: null,
    email: '',
    nickname: '',
    avatar: '',
    role: '',
  })

  // 用响应式 ref 追踪登录状态
  // 注意：不再存储 token，因为 token 现在通过 HttpOnly Cookie 管理
  const token = ref<string | null>(String(loggedInCookie.value ?? '') === '1' ? 'logged_in' : null)

  const isLoggedIn = computed(() => !!token.value)

  /**
   * 设置用户信息（登录成功后调用）
   * 注意：不再接收 token 参数，因为 token 通过 HttpOnly Cookie 管理
   */
  function setUser(info: UserInfo & { success?: boolean }) {
    // 设置登录状态标识（不再存储 token）
    setToken('logged_in')
    token.value = 'logged_in'
    userInfo.value = {
      userId: info.userId,
      email: info.email,
      nickname: info.nickname,
      avatar: info.avatar,
      role: info.role,
    }
    syncUserCookie(info.avatar, info.nickname)
  }

  function clearUser() {
    removeToken()
    token.value = null
    userInfo.value = { userId: null, email: '', nickname: '', avatar: '', role: '' }
  }

  function updateUserInfo(partial: Partial<UserInfo>) {
    userInfo.value = {
      ...userInfo.value,
      ...partial,
    }
    syncUserCookie(userInfo.value.avatar || '', userInfo.value.nickname || '')
  }

  /** 页面刷新后根据 token 恢复用户信息 */
  async function fetchUser() {
    if (!token.value) return
    try {
      const res = await userApi.getProfile()
      const p = res.data
      userInfo.value = {
        userId: p.userId,
        email: p.email || '',
        nickname: p.nickname,
        avatar: p.avatar || '',
        role: p.role,
      }
      syncUserCookie(p.avatar || '', p.nickname)
    } catch {
      // token 过期或无效，清除登录状态
      clearUser()
    }
  }

  return { userInfo, isLoggedIn, setUser, clearUser, fetchUser, updateUserInfo }
})
