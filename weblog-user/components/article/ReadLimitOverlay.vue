<template>
  <ClientOnly>
    <Teleport to="body">
      <div v-show="show" class="read-limit-overlay">
        <div class="read-limit-card">
          <h3>今日免费阅读已达上限</h3>
          <p class="desc">
            您今日已阅读 <strong>{{ readCount }}</strong> 篇文章，
            免费额度为 <strong>{{ limit }}</strong> 篇。
          </p>
          <div class="actions">
            <button class="btn-primary" @click="emit('login')">登录解锁无限阅读</button>
          </div>
        </div>
      </div>
    </Teleport>
  </ClientOnly>
</template>

<script setup lang="ts">
defineProps<{
  show: boolean
  readCount: number
  limit: number
}>()

const emit = defineEmits<{
  login: []
  close: []
}>()
</script>

<style scoped lang="scss">
.read-limit-overlay {
  --read-limit-overlay-bg: rgba(0, 0, 0, 0.5);
  --read-limit-card-bg: rgba(255, 255, 255, 0.96);
  --read-limit-card-border: rgba(148, 163, 184, 0.24);
  --read-limit-title: #0f172a;
  --read-limit-desc: #475569;
  --read-limit-strong: #2563eb;
  --read-limit-btn-bg: rgba(15, 23, 42, 0.88);
  --read-limit-btn-border: rgba(15, 23, 42, 0.24);
  --read-limit-btn-color: #f8fafc;

  position: fixed;
  inset: 0;
  z-index: var(--z-read-limit);
  background: var(--read-limit-overlay-bg);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);

  .dark & {
    --read-limit-overlay-bg: rgba(0, 0, 0, 0.5);
    --read-limit-card-bg:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
    --read-limit-card-border: rgba(148, 163, 184, 0.35);
    --read-limit-title: #e2e8f0;
    --read-limit-desc: #94a3b8;
    --read-limit-strong: #93c5fd;
    --read-limit-btn-bg: rgba(2, 6, 23, 0.8);
    --read-limit-btn-border: rgba(148, 163, 184, 0.42);
    --read-limit-btn-color: #f8fafc;
  }
}

.read-limit-card {
  background: var(--read-limit-card-bg);
  border: 1px solid var(--read-limit-card-border);
  border-radius: 12px;
  padding: 34px 28px;
  max-width: 420px;
  width: 90%;
  text-align: center;
  box-shadow: 0 16px 40px rgba(2, 6, 23, 0.28);

  h3 {
    font-size: 1.25rem;
    margin-bottom: 12px;
    color: var(--read-limit-title);
  }

  .desc {
    color: var(--read-limit-desc);
    margin-bottom: 20px;
    line-height: 1.6;

    strong {
      color: var(--read-limit-strong);
    }
  }

  .actions {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }

  .btn-primary {
    width: 100%;
    padding: 12px 18px;
    background: var(--read-limit-btn-bg);
    color: var(--read-limit-btn-color);
    border: 1px solid var(--read-limit-btn-border);
    border-radius: 8px;
    font-size: 1rem;
    font-weight: 600;
    cursor: pointer;

    transition: background 180ms ease, border-color 180ms ease, transform 180ms ease, box-shadow 180ms ease;

    &:hover {
      background: rgba(30, 41, 59, 0.95);
      border-color: rgba(226, 232, 240, 0.42);
      transform: translate3d(0, -1px, 0);
      box-shadow: 0 10px 24px rgba(2, 6, 23, 0.28);
    }

    &:focus-visible {
      outline: 2px solid rgba(148, 163, 184, 0.8);
      outline-offset: 1px;
    }
  }
}
</style>
