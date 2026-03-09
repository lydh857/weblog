<template>
  <div v-if="show" class="read-limit-overlay">
    <div class="read-limit-card">
      <h3>今日免费阅读已达上限</h3>
      <p class="desc">
        您今日已阅读 <strong>{{ readCount }}</strong> 篇文章，
        免费额度为 <strong>{{ limit }}</strong> 篇。
      </p>
      <div class="actions">
        <button class="btn-primary" @click="handleLogin">登录解锁无限阅读</button>
        <button v-if="!unlocked" class="btn-secondary" @click="handleVerify">
          验证后继续阅读
        </button>
      </div>
      <p class="hint">登录用户不受阅读限制</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { accessApi } from '~/api/access'
import { useLoginModal } from '~/composables/useLoginModal'

const props = defineProps<{
  show: boolean
  readCount: number
  limit: number
  unlocked: boolean
}>()

const emit = defineEmits<{
  unlocked: []
  close: []
}>()

function handleLogin() {
  useLoginModal().open()
}

async function handleVerify() {
  // 简化版验证：实际项目可集成滑块验证组件
  // 这里直接调用解锁API（生产环境需要真正的验证）
  try {
    await accessApi.unlock()
    emit('unlocked')
  } catch {
    // ignore
  }
}
</script>

<style scoped lang="scss">
.read-limit-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(0, 0, 0, 0.7);
  display: flex;
  align-items: center;
  justify-content: center;
  backdrop-filter: blur(4px);
}
.read-limit-card {
  background: var(--bg-card, #fff);
  border-radius: 12px;
  padding: 40px 32px;
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
    margin-bottom: 24px;
    line-height: 1.6;
    strong { color: var(--color-primary, #409eff); }
  }
  .actions {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .btn-primary {
    padding: 12px 24px;
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
  .hint {
    margin-top: 16px;
    font-size: 0.85rem;
    color: var(--text-muted, #999);
  }
}
</style>
