<template>
  <div class="oauth-screen" :class="{ 'has-error': Boolean(error) }">
    <div class="oauth-glow oauth-glow-a" aria-hidden="true" />
    <div class="oauth-glow oauth-glow-b" aria-hidden="true" />
    <div class="oauth-content">
      <template v-if="error">
        <div class="oauth-icon error"><Icon name="heroicons:x-circle-16-solid" size="56" /></div>
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
.oauth-screen {
  --oauth-bg:
    radial-gradient(1200px 600px at 12% 12%, rgba(59, 130, 246, 0.2), transparent 60%),
    radial-gradient(1000px 540px at 90% 88%, rgba(14, 165, 233, 0.18), transparent 60%),
    linear-gradient(160deg, #eef4ff 0%, #f8fbff 45%, #eef7ff 100%);
  --oauth-title-color: #172033;
  --oauth-desc-color: #4d5d76;
  --oauth-spinner-track: rgba(37, 99, 235, 0.2);
  --oauth-spinner-color: #2563eb;
  --oauth-btn-bg: #2563eb;
  --oauth-btn-bg-hover: #1d4ed8;
  position: fixed;
  inset: 0;
  z-index: var(--z-confirm);
  min-height: 100dvh;
  width: 100%;
  display: grid;
  place-items: center;
  overflow: hidden;
  background: var(--oauth-bg);
}

:global(html.dark) .oauth-screen {
  --oauth-bg:
    radial-gradient(1100px 580px at 10% 10%, rgba(56, 189, 248, 0.22), transparent 58%),
    radial-gradient(1050px 600px at 88% 85%, rgba(30, 64, 175, 0.28), transparent 62%),
    linear-gradient(165deg, #0b1220 0%, #101a2c 45%, #0d1728 100%);
  --oauth-title-color: #e6edf8;
  --oauth-desc-color: #9bb0cf;
  --oauth-spinner-track: rgba(147, 197, 253, 0.3);
  --oauth-spinner-color: #93c5fd;
  --oauth-btn-bg: #3b82f6;
  --oauth-btn-bg-hover: #2563eb;
}

.oauth-glow {
  position: absolute;
  border-radius: 999px;
  filter: blur(42px);
  opacity: .55;
}

.oauth-glow-a {
  width: clamp(220px, 26vw, 460px);
  height: clamp(220px, 26vw, 460px);
  left: -4%;
  top: 2%;
  background: rgba(37, 99, 235, 0.3);
}

.oauth-glow-b {
  width: clamp(240px, 30vw, 520px);
  height: clamp(240px, 30vw, 520px);
  right: -6%;
  bottom: -5%;
  background: rgba(14, 165, 233, 0.28);
}

.oauth-content {
  position: relative;
  text-align: center;
  width: min(92vw, 520px);
  padding: clamp(1.25rem, 3.6vw, 2.8rem);
}

.oauth-spinner { display: flex; justify-content: center; margin-bottom: 1.5rem; }
.circular { width: 62px; height: 62px; animation: rotate 1.4s linear infinite; }
.track { stroke: var(--oauth-spinner-track); }
.path {
  stroke: var(--oauth-spinner-color);
  stroke-linecap: round;
  animation: dash 1.4s ease-in-out infinite;
}
.oauth-icon { margin-bottom: 1rem; }
.oauth-icon.error { color: #ef4444; }
.oauth-title { font-size: clamp(1.5rem, 3.5vw, 2rem); font-weight: 700; color: var(--oauth-title-color); margin-bottom: .625rem; }
.oauth-desc { font-size: clamp(1rem, 2.2vw, 1.18rem); color: var(--oauth-desc-color); line-height: 1.65; }
.oauth-btn {
  margin-top: 1.75rem;
  min-width: 9.5rem;
  padding: .74rem 1.65rem;
  border: none;
  border-radius: 999px;
  background: var(--oauth-btn-bg);
  color: #fff;
  font-size: .94rem;
  font-weight: 600;
  cursor: pointer;
}
.oauth-btn:hover { background: var(--oauth-btn-bg-hover); }
.oauth-screen.has-error .oauth-spinner { display: none; }
@keyframes rotate { to { transform: rotate(360deg); } }
@keyframes dash {
  0% { stroke-dasharray: 1,150; stroke-dashoffset: 0; }
  50% { stroke-dasharray: 90,150; stroke-dashoffset: -35; }
  100% { stroke-dasharray: 90,150; stroke-dashoffset: -124; }
}
</style>
