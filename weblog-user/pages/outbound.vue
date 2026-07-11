<template>
  <main class="outbound-page">
    <section class="outbound-main">
      <div class="outbound-panel">
        <p class="outbound-tag">安全跳转</p>
        <h1 class="outbound-title">即将离开本站</h1>
        <p class="outbound-desc">请确认目标地址可信后再继续访问，避免点击来源不明的外部链接。</p>

        <div v-if="safeTarget" class="outbound-target-wrap">
          <p class="outbound-target-label">目标地址</p>
          <p class="outbound-target-host">{{ targetHost }}</p>
          <p class="outbound-target-url">{{ safeTarget }}</p>
        </div>
        <div v-else class="outbound-invalid">链接无效或已被系统拦截。</div>

        <p class="outbound-note">提示：仅在你信任目标站点的情况下继续访问。</p>

        <button class="outbound-btn outbound-btn--primary" :disabled="!safeTarget" @click="handleContinue">
          继续访问
        </button>

        <p class="outbound-exit">不继续访问可直接关闭当前页面，或返回上一页。</p>
      </div>
    </section>
  </main>
</template>

<script setup lang="ts">
import { normalizeSafeHref } from '~/utils/security/urlSafety'

definePageMeta({
  layout: false,
})

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

<style lang="scss">
.outbound-page {
  --outbound-bg: var(--startup-mask-bg);
  --outbound-panel-bg: rgba(255, 255, 255, 0.78);
  --outbound-target-bg: rgba(248, 250, 252, 0.72);
  --outbound-border: rgba(148, 163, 184, 0.42);
  --outbound-title: #0f172a;
  --outbound-text: #334155;
  --outbound-muted: #475569;
  --outbound-primary: #2563eb;
  --outbound-primary-hover: #1d4ed8;
  --outbound-invalid: #dc2626;
  --outbound-shadow: 0 16px 42px rgba(15, 23, 42, 0.14);
  min-height: 100dvh;
  width: 100%;
  display: flex;
  flex-direction: column;
  background: var(--outbound-bg);
}

html.dark .outbound-page,
body.dark .outbound-page,
.dark .outbound-page,
html[data-theme='dark'] .outbound-page,
html[data-startup-theme='dark'] .outbound-page {
  --outbound-panel-bg: rgba(12, 20, 34, 0.74);
  --outbound-target-bg: rgba(15, 23, 42, 0.56);
  --outbound-border: rgba(100, 116, 139, 0.44);
  --outbound-title: #e6edf8;
  --outbound-text: #d3deef;
  --outbound-muted: #9eb0c9;
  --outbound-primary: #3b82f6;
  --outbound-primary-hover: #2563eb;
  --outbound-invalid: #fb7185;
  --outbound-shadow: 0 20px 54px rgba(2, 6, 23, 0.48);
}

.outbound-tag {
  margin: 0 0 0.65rem;
  font-size: 0.78rem;
  font-weight: 700;
  color: var(--outbound-primary);
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.outbound-main {
  flex: 1;
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: clamp(1rem, 4vw, 2.6rem) clamp(1rem, 3vw, 2.2rem);
}

.outbound-panel {
  width: min(100%, 760px);
  border: 1px solid var(--outbound-border);
  border-radius: 16px;
  background: var(--outbound-panel-bg);
  backdrop-filter: blur(12px);
  box-shadow: var(--outbound-shadow);
  padding: clamp(1rem, 2.8vw, 2rem);
}

.outbound-title {
  margin: 0;
  font-size: clamp(1.55rem, 3vw, 2.3rem);
  line-height: 1.15;
  color: var(--outbound-title);
}

.outbound-desc {
  margin: 0.72rem 0 0;
  color: var(--outbound-text);
  font-size: 1rem;
}

.outbound-target-wrap,
.outbound-invalid {
  margin-top: 1.15rem;
  background: var(--outbound-target-bg);
  border: 1px solid var(--outbound-border);
  border-radius: 0.85rem;
  padding: 1rem;
}

.outbound-target-label {
  margin: 0;
  font-size: 0.78rem;
  color: var(--outbound-muted);
}

.outbound-target-host {
  margin: 0.45rem 0 0;
  font-weight: 700;
  font-size: 1.05rem;
  color: var(--outbound-title);
}

.outbound-target-url {
  margin: 0.55rem 0 0;
  font-size: 0.92rem;
  line-height: 1.5;
  color: var(--outbound-muted);
  word-break: break-all;
}

.outbound-invalid {
  color: var(--outbound-invalid);
}

.outbound-note {
  margin: 0.9rem 0 0;
  font-size: 0.9rem;
  color: var(--outbound-muted);
}

.outbound-btn {
  margin-top: 1.15rem;
  width: 100%;
  border-radius: 0.84rem;
  padding: 0.84rem 1rem;
  font-size: 0.97rem;
  font-weight: 600;
  transition: transform 0.2s, box-shadow 0.2s, background 0.2s;
}

.outbound-btn--primary {
  border: 1px solid var(--outbound-primary);
  background: var(--outbound-primary);
  color: #fff;
  cursor: pointer;

  &:hover:not(:disabled) {
    border-color: var(--outbound-primary-hover);
    background: var(--outbound-primary-hover);
    transform: translateY(-1px);
    box-shadow: 0 10px 20px rgba(37, 99, 235, 0.3);
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
    transform: none;
    box-shadow: none;
  }
}

.outbound-exit {
  margin-top: 0.76rem;
  font-size: 0.82rem;
  color: var(--outbound-muted);
  text-align: center;
}

@media (max-width: 900px) {
  .outbound-main {
    padding-left: 1rem;
    padding-right: 1rem;
    align-items: flex-start;
    padding-top: 1rem;
  }
}
</style>
