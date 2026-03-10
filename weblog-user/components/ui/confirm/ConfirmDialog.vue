<script setup lang="ts">
import { watch, onUnmounted } from 'vue'
import { useConfirmStore } from '~/composables/useConfirm'
import { lockScroll, unlockScroll } from '~/composables/useScrollLock'

const { visible, options, confirm, cancel } = useConfirmStore()

let locked = false

watch(visible, (v) => {
  if (v && !locked) { lockScroll(); locked = true }
  else if (!v && locked) { unlockScroll(); locked = false }
})

onUnmounted(() => {
  if (locked) { unlockScroll(); locked = false }
})
</script>

<template>
  <ClientOnly>
    <Teleport to="body">
      <Transition name="confirm">
        <div v-if="visible" class="confirm-overlay" @click="cancel">
          <div class="confirm-box" @click.stop>
            <div class="confirm-header">
              <Icon
                :name="options.type === 'danger' ? 'heroicons:exclamation-triangle-16-solid' : options.type === 'warning' ? 'heroicons:exclamation-circle-16-solid' : 'heroicons:question-mark-circle-16-solid'"
                size="22"
                :class="['confirm-icon', `icon-${options.type || 'info'}`]"
              />
              <h3 class="confirm-title">{{ options.title || '确认' }}</h3>
            </div>
            <p class="confirm-message">{{ options.message }}</p>
            <div class="confirm-actions">
              <button type="button" class="btn-cancel" @click="cancel">{{ options.cancelText || '取消' }}</button>
              <button type="button" class="btn-confirm" :class="`btn-${options.type || 'info'}`" @click="confirm">
                {{ options.confirmText || '确定' }}
              </button>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </ClientOnly>
</template>

<style scoped lang="scss">
.confirm-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; z-index: var(--z-confirm);
  background: rgba(0,0,0,0.45); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; padding: 16px;
}
.confirm-box {
  background: #fff; border-radius: 12px; padding: 24px; width: 100%; max-width: 360px;
  box-shadow: 0 16px 48px rgba(0,0,0,0.18);
  .dark & { background: #1e293b; box-shadow: 0 16px 48px rgba(0,0,0,0.5); }
}
.confirm-header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.confirm-icon { flex-shrink: 0; }
.icon-info { color: #3b82f6; }
.icon-warning { color: #f59e0b; }
.icon-danger { color: #ef4444; }
.confirm-title {
  margin: 0; font-size: 1rem; font-weight: 600; color: #1e293b;
  .dark & { color: #e2e8f0; }
}
.confirm-message {
  margin: 0 0 20px; font-size: 0.9rem; color: #64748b; line-height: 1.5;
  .dark & { color: #94a3b8; }
}
.confirm-actions { display: flex; justify-content: flex-end; gap: 8px; }
.btn-cancel, .btn-confirm {
  padding: 8px 20px; border-radius: 8px; font-size: 0.875rem; font-weight: 500;
  cursor: pointer; transition: all 0.2s; border: 1px solid;
}
.btn-cancel {
  background: #fff; border-color: #e2e8f0; color: #64748b;
  &:hover { border-color: #cbd5e1; color: #334155; }
  .dark & { background: #0f172a; border-color: #334155; color: #94a3b8; &:hover { border-color: #475569; color: #e2e8f0; } }
}
.btn-confirm {
  color: #fff; border-color: transparent;
  &.btn-info { background: #3b82f6; &:hover { background: #2563eb; } }
  &.btn-warning { background: #f59e0b; &:hover { background: #d97706; } }
  &.btn-danger { background: #ef4444; &:hover { background: #dc2626; } }
}

/* Transition 动画 — overlay 淡入淡出 + box 弹性缩放 */
.confirm-enter-active {
  transition: opacity 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
.confirm-enter-active .confirm-box {
  animation: confirm-pop-in 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.confirm-leave-active {
  transition: opacity 0.2s ease;
}
.confirm-leave-active .confirm-box {
  animation: confirm-pop-out 0.2s ease forwards;
}
.confirm-enter-from,
.confirm-leave-to {
  opacity: 0;
}

@keyframes confirm-pop-in {
  0% { transform: scale(0.75); opacity: 0; }
  60% { transform: scale(1.02); }
  100% { transform: scale(1); opacity: 1; }
}
@keyframes confirm-pop-out {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(0.85); opacity: 0; }
}
</style>
