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
          <svg viewBox="0 0 50 50" class="circular" aria-hidden="true">
            <circle cx="25" cy="25" r="20" fill="none" stroke-width="4" class="track" />
            <circle cx="25" cy="25" r="20" fill="none" stroke-width="4" class="path" />
          </svg>
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
.oauth-overlay {
  --oauth-overlay-bg: rgba(255, 255, 255, 0.9);
  --oauth-card-bg: rgba(248, 251, 255, 0.85);
  --oauth-card-border: rgba(59, 130, 246, 0.2);
  --oauth-title-color: #1e293b;
  --oauth-desc-color: #64748b;
  --oauth-spinner-track: rgba(59, 130, 246, 0.18);
  --oauth-spinner-color: #3b82f6;
  --oauth-card-shadow: 0 16px 40px rgba(15, 23, 42, 0.08);
  position: fixed;
  inset: 0;
  z-index: var(--z-confirm);
  background: var(--oauth-overlay-bg);
  backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
}

:global(html.dark) .oauth-overlay {
  --oauth-overlay-bg: rgba(13, 16, 20, 0.9);
  --oauth-card-bg: rgba(22, 28, 36, 0.86);
  --oauth-card-border: rgba(125, 211, 252, 0.22);
  --oauth-title-color: #d6dbe4;
  --oauth-desc-color: #9aa5b5;
  --oauth-spinner-track: rgba(125, 211, 252, 0.25);
  --oauth-spinner-color: #7dd3fc;
  --oauth-card-shadow: 0 20px 50px rgba(0, 0, 0, 0.45);
}

.oauth-card {
  text-align: center;
  padding: 2.5rem 2rem;
  max-width: 360px;
  width: 100%;
  border-radius: 1rem;
  border: 1px solid var(--oauth-card-border);
  background: var(--oauth-card-bg);
  box-shadow: var(--oauth-card-shadow);
}

.oauth-spinner { display: flex; justify-content: center; margin-bottom: 1.5rem; }
.circular { width: 48px; height: 48px; animation: rotate 1.4s linear infinite; }
.track { stroke: var(--oauth-spinner-track); }
.path {
  stroke: var(--oauth-spinner-color);
  stroke-linecap: round;
  animation: dash 1.4s ease-in-out infinite;
}
.oauth-icon { margin-bottom: 1rem; }
.oauth-icon.error { color: #ef4444; }
.oauth-title { font-size: 1.1rem; font-weight: 600; color: var(--oauth-title-color); margin-bottom: .5rem; }
.oauth-desc { font-size: .875rem; color: var(--oauth-desc-color); line-height: 1.5; }
.oauth-btn { margin-top: 1.5rem; padding: .625rem 1.5rem; border: none; border-radius: .5rem; background: #3b82f6; color: #fff; font-size: .9rem; font-weight: 500; cursor: pointer; }
.oauth-btn:hover { background: #2563eb; }
@keyframes rotate { to { transform: rotate(360deg); } }
@keyframes dash {
  0% { stroke-dasharray: 1,150; stroke-dashoffset: 0; }
  50% { stroke-dasharray: 90,150; stroke-dashoffset: -35; }
  100% { stroke-dasharray: 90,150; stroke-dashoffset: -124; }
}
</style>
