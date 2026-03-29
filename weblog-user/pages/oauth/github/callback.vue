<template>
  <div class="oauth-overlay">
    <div class="oauth-card">
      <template v-if="error">
        <div class="oauth-icon error"><Icon name="heroicons:x-circle-16-solid" size="48" /></div>
        <p class="oauth-title">登录失败</p>
        <p class="oauth-desc">{{ error }}</p>
        <button type="button" class="oauth-btn" @click="goLogin">返回登录</button>
      </template>
      <template v-else>
        <div class="oauth-spinner">
          <svg viewBox="0 0 50 50" class="circular"><circle cx="25" cy="25" r="20" fill="none" stroke-width="4" class="path" /></svg>
        </div>
        <p class="oauth-title">正在处理 GitHub 登录</p>
        <p class="oauth-desc">请稍候，正在验证您的身份...</p>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { authApi } from '~/api/auth/auth'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/modal/useLoginModal'
import { consumeNavContext } from '~/utils/navigation/navContext'
import { getErrorMessage } from '~/utils/security/error'

useHead({ title: 'GitHub 登录中...' })

const userStore = useUserStore()
const router = useRouter()
const route = useRoute()
const error = ref('')
const message = useMessage()

function goLogin() {
  useLoginModal().open()
  const ctx = consumeNavContext()
  router.replace(ctx?.fullPath || '/')
}

onMounted(async () => {
  const code = route.query.code as string
  const state = route.query.state as string

  if (!code || !state) {
    error.value = '无效的回调参数'
    return
  }

  try {
    const res = await authApi.githubCallback(code, state)
    userStore.setUser(res.data)
    message.success('GitHub 登录成功')
    const ctx = consumeNavContext()
    await router.replace(ctx?.fullPath || '/')
    if (import.meta.client && typeof ctx?.scrollY === 'number') {
      requestAnimationFrame(() => {
        window.scrollTo({ top: ctx.scrollY })
        requestAnimationFrame(() => window.scrollTo({ top: ctx.scrollY }))
      })
    }
  } catch (e: unknown) {
    error.value = getErrorMessage(e, 'GitHub 登录失败')
  }
})
</script>

<style scoped lang="scss">
.oauth-overlay { position: fixed; inset: 0; z-index: var(--z-confirm); background: rgba(255,255,255,.92); backdrop-filter: blur(12px); display: flex; align-items: center; justify-content: center; }
:global(html.dark) .oauth-overlay { background: rgba(16,18,21,.92); }
.oauth-card { text-align: center; padding: 2.5rem 2rem; max-width: 360px; width: 100%; }
.oauth-spinner { display: flex; justify-content: center; margin-bottom: 1.5rem; }
.circular { width: 48px; height: 48px; animation: rotate 1.4s linear infinite; }
.path { stroke: #3b82f6; stroke-linecap: round; animation: dash 1.4s ease-in-out infinite; }
.oauth-icon { margin-bottom: 1rem; }
.oauth-icon.error { color: #ef4444; }
.oauth-title { font-size: 1.1rem; font-weight: 600; color: #1e293b; margin-bottom: .5rem; }
:global(html.dark) .oauth-title { color: #d6dbe4; }
.oauth-desc { font-size: .875rem; color: #64748b; line-height: 1.5; }
:global(html.dark) .oauth-desc { color: #9aa5b5; }
.oauth-btn { margin-top: 1.5rem; padding: .625rem 1.5rem; border: none; border-radius: .5rem; background: #3b82f6; color: #fff; font-size: .9rem; font-weight: 500; cursor: pointer; }
.oauth-btn:hover { background: #2563eb; }
@keyframes rotate { to { transform: rotate(360deg); } }
@keyframes dash {
  0% { stroke-dasharray: 1,150; stroke-dashoffset: 0; }
  50% { stroke-dasharray: 90,150; stroke-dashoffset: -35; }
  100% { stroke-dasharray: 90,150; stroke-dashoffset: -124; }
}
</style>
