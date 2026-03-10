<template>
  <ClientOnly>
    <Teleport to="body">
      <div v-if="show" class="read-limit-overlay">
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
const props = defineProps<{
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
  position: fixed;
  inset: 0;
  z-index: var(--z-read-limit);
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
}
.read-limit-card {
  background: var(--bg-card, #fff);
  border-radius: 12px;
  padding: 34px 28px;
  max-width: 420px;
  width: 90%;
  text-align: center;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);

  h3 {
    font-size: 1.25rem;
    margin-bottom: 12px;
    color: var(--text-primary, #1a1a1a);
  }
  .desc {
    color: var(--text-secondary, #666);
    margin-bottom: 20px;
    line-height: 1.6;
    strong { color: var(--color-primary, #409eff); }
  }
  .actions {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .btn-primary {
    width: 100%;
    padding: 12px 18px;
    background: var(--color-primary, #409eff);
    color: #fff;
    border: none;
    border-radius: 8px;
    font-size: 1rem;
    cursor: pointer;
    &:hover { opacity: 0.9; }
  }
  .btn-secondary {
    padding: 12px 24px;
    background: transparent;
    color: var(--color-primary, #409eff);
    border: 1px solid var(--color-primary, #409eff);
    border-radius: 8px;
    font-size: 1rem;
    cursor: pointer;
    &:hover { background: rgba(64, 158, 255, 0.05); }
  }
}
</style>
