<template>
  <main class="outbound-page">
    <section class="outbound-card">
      <p class="outbound-tag">安全跳转</p>
      <h1 class="outbound-title">即将离开本站</h1>
      <p class="outbound-desc">请确认目标地址可信后再继续访问。</p>

      <div v-if="safeTarget" class="outbound-target-wrap">
        <p class="outbound-target-host">{{ targetHost }}</p>
        <p class="outbound-target-url">{{ safeTarget }}</p>
      </div>
      <div v-else class="outbound-invalid">链接无效或已被系统拦截。</div>

      <div class="outbound-actions">
        <button class="outbound-btn outbound-btn--primary" :disabled="!safeTarget" @click="handleContinue">
          继续访问
        </button>
        <NuxtLink class="outbound-btn outbound-btn--ghost" to="/">
          返回首页
        </NuxtLink>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { normalizeSafeHref } from '~/utils/security/urlSafety'

const route = useRoute()

useHead({
  title: '安全跳转中',
  meta: [
    { name: 'robots', content: 'noindex,nofollow,noarchive' }
  ]
})

const safeTarget = computed(() => {
  const rawTarget = route.query.target
  if (typeof rawTarget !== 'string' || !rawTarget.trim()) {
    return null
  }

  let decoded = rawTarget
  try {
    decoded = decodeURIComponent(rawTarget)
  } catch {
    decoded = rawTarget
  }

  return normalizeSafeHref(decoded)
})

const targetHost = computed(() => {
  if (!safeTarget.value) return '未知目标'
  if (safeTarget.value.startsWith('/')) return '站内页面'

  try {
    return new URL(safeTarget.value).host
  } catch {
    return '未知目标'
  }
})

function handleContinue() {
  if (!safeTarget.value || !import.meta.client) return
  window.location.href = safeTarget.value
}
</script>

<style scoped lang="scss">
.outbound-page {
  --outbound-bg:
    radial-gradient(1200px 680px at 8% 10%, rgba(56, 189, 248, 0.16), transparent 58%),
    radial-gradient(980px 620px at 92% 88%, rgba(59, 130, 246, 0.18), transparent 60%),
    linear-gradient(165deg, #eef4ff 0%, #f8fbff 45%, #edf6ff 100%);
  --outbound-card-bg: rgba(255, 255, 255, 0.75);
  --outbound-card-border: rgba(148, 163, 184, 0.35);
  --outbound-title: #0f172a;
  --outbound-text: #334155;
  --outbound-muted: #475569;
  --outbound-shadow: 0 16px 44px rgba(15, 23, 42, 0.12);
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(1rem, 4vw, 2.4rem);
  background: var(--outbound-bg);
}

:global(html.dark) .outbound-page,
:global(body.dark) .outbound-page,
:global(html[data-theme='dark']) .outbound-page,
:global(html[data-startup-theme='dark']) .outbound-page {
  --outbound-bg:
    radial-gradient(1200px 700px at 8% 12%, rgba(56, 189, 248, 0.18), transparent 58%),
    radial-gradient(1000px 620px at 90% 86%, rgba(30, 64, 175, 0.3), transparent 60%),
    linear-gradient(165deg, #0a1220 0%, #0f172a 46%, #0b1628 100%);
  --outbound-card-bg: rgba(8, 18, 36, 0.66);
  --outbound-card-border: rgba(71, 85, 105, 0.5);
  --outbound-title: #e2e8f0;
  --outbound-text: #cbd5e1;
  --outbound-muted: #94a3b8;
  --outbound-shadow: 0 18px 48px rgba(2, 6, 23, 0.44);
}

.outbound-card {
  width: min(92vw, 640px);
  border: 1px solid var(--outbound-card-border);
  border-radius: 1.1rem;
  background: var(--outbound-card-bg);
  box-shadow: var(--outbound-shadow);
  backdrop-filter: blur(10px);
  padding: clamp(1.1rem, 3.5vw, 2rem);
}

.outbound-tag {
  font-size: 0.75rem;
  font-weight: 700;
  color: #2563eb;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  margin: 0;
}

.outbound-title {
  margin: 0.45rem 0 0;
  font-size: clamp(1.38rem, 2.6vw, 2rem);
  color: var(--outbound-title);
}

.outbound-desc {
  margin: 0.55rem 0 0;
  color: var(--outbound-text);
}

.outbound-target-wrap,
.outbound-invalid {
  margin-top: 1rem;
  border: 1px solid var(--outbound-card-border);
  border-radius: 0.85rem;
  padding: 0.75rem 0.85rem;
}

.outbound-target-host {
  margin: 0;
  font-weight: 700;
  color: var(--outbound-title);
}

.outbound-target-url {
  margin: 0.35rem 0 0;
  color: var(--outbound-muted);
  font-size: 0.88rem;
  word-break: break-all;
}

.outbound-invalid {
  color: #dc2626;
}

.outbound-actions {
  margin-top: 1rem;
  display: flex;
  gap: 0.7rem;
  flex-wrap: wrap;
}

.outbound-btn {
  border-radius: 999px;
  padding: 0.52rem 0.98rem;
  font-weight: 600;
  text-decoration: none;
}

.outbound-btn--primary {
  border: 1px solid #2563eb;
  background: #2563eb;
  color: #fff;
  cursor: pointer;

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

.outbound-btn--ghost {
  border: 1px solid var(--outbound-card-border);
  color: var(--outbound-title);
}
</style>
