<template>
  <Teleport to="body">
    <Transition name="confirm-fade">
      <div v-if="visible" class="confirm-overlay" @click.self="handleCancel">
        <div class="confirm-dialog">
          <div class="confirm-icon">
            <Icon name="heroicons:exclamation-triangle-20-solid" size="28" />
          </div>
          <p class="confirm-message">{{ message }}</p>
          <div class="confirm-actions">
            <button class="confirm-btn cancel" @click="handleCancel">取消</button>
            <button class="confirm-btn ok" @click="handleConfirm">确定</button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
const props = defineProps<{
  visible: boolean
  message: string
}>()

const emit = defineEmits<{
  'update:visible': [val: boolean]
  confirm: []
  cancel: []
}>()

function handleConfirm() {
  emit('confirm')
  emit('update:visible', false)
}

function handleCancel() {
  emit('cancel')
  emit('update:visible', false)
}
</script>

<style scoped lang="scss">
.confirm-overlay {
  position: fixed; inset: 0; z-index: 6000;
  background: rgba(0, 0, 0, 0.45);
  display: flex; align-items: center; justify-content: center;
}
.confirm-dialog {
  background: #fff; border-radius: 12px; padding: 1.5rem;
  width: 320px; max-width: 90vw;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
  text-align: center;
  .dark & { background: $color-dark-bg-secondary; box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4); }
}
.confirm-icon {
  color: #f59e0b; margin-bottom: 0.75rem;
  .dark & { color: #fbbf24; }
}
.confirm-message {
  font-size: 0.95rem; color: $color-text; line-height: 1.5; margin-bottom: 1.25rem;
  .dark & { color: $color-dark-text; }
}
.confirm-actions { display: flex; gap: 0.75rem; justify-content: center; }
.confirm-btn {
  padding: 0.5rem 1.5rem; border-radius: 8px; font-size: 0.85rem; font-weight: 500;
  cursor: pointer; border: none; min-height: 40px; transition: all 0.2s;
  &.cancel {
    background: #f1f5f9; color: $color-text;
    &:hover { background: #e2e8f0; }
    .dark & { background: #334155; color: $color-dark-text; &:hover { background: #475569; } }
  }
  &.ok {
    background: #ef4444; color: #fff;
    &:hover { background: #dc2626; }
  }
}
.confirm-fade-enter-active { transition: opacity 0.2s; }
.confirm-fade-leave-active { transition: opacity 0.15s; }
.confirm-fade-enter-from, .confirm-fade-leave-to { opacity: 0; }
</style>
