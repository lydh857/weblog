import { defineStore } from 'pinia'
import { userApi } from '~/api/user'

interface UserInfo {
  userId: number | null
  email: string
  nickname: string
  avatar: string
  role: string
}

export const useUserStore = defineStore('user', () => {
  const userInfo = ref<UserInfo>({
    userId: null,
    email: '',
    nickname: '',
    avatar: '',
    role: '',
  })

  const isLoggedIn = computed(() => !!userInfo.value.userId)

  /**
   * 设置用户信息（登录成功后调用）
   */
  function setUser(info: UserInfo & { success?: boolean }) {
    userInfo.value = {
      userId: info.userId,
      email: info.email,
      nickname: info.nickname,
      avatar: info.avatar,
      role: info.role,
    }
  }

  async function fetchUser() {
    try {
      const res = await userApi.me()
      const d = res.data
      userInfo.value = {
        userId: d.userId,
        email: d.email,
        nickname: d.nickname,
        avatar: d.avatar,
        role: d.role,
      }
    } catch {
      clearUser()
    }
  }

  function clearUser() {
    userInfo.value = { userId: null, email: '', nickname: '', avatar: '', role: '' }
  }

  return { userInfo, isLoggedIn, setUser, fetchUser, clearUser }
})
