<script setup lang="ts">
import { watch, onMounted, onUnmounted } from 'vue'
import { lockScroll, unlockScroll } from '~/composables/useScrollLock'

const props = withDefaults(defineProps<{
  visible: boolean
  title?: string
  width?: string | number
  showClose?: boolean
  maskClosable?: boolean
  zIndex?: number
}>(), {
  title: '',
  width: '480px',
  showClose: true,
  maskClosable: true,
  zIndex: 60000,
})

const emit = defineEmits<{
  'update:visible': [val: boolean]
  close: []
}>()

function close() {
  emit('update:visible', false)
  emit('close')
}

function onMaskClick() {
  if (props.maskClosable) close()
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Escape' && props.visible) close()
}

let locked = false

watch(() => props.visible, (v) => {
  if (v && !locked) { lockScroll(); locked = true }
  else if (!v && locked) { unlockScroll(); locked = false }
}, { immediate: true })

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  document.removeEventListener('keydown', onKeydown)
  if (locked) { unlockScroll(); locked = false }
})
</script>

<template>
  <Teleport to="body">
    <Transition name="modal">
      <div v-if="visible" class="modal-overlay" :style="{ zIndex }" @click="onMaskClick">
        <div class="modal-content" :style="{ width: typeof width === 'number' ? `${width}px` : width }" @click.stop>
          <div v-if="title || showClose" class="modal-header">
            <h3 v-if="title" class="modal-title">{{ title }}</h3>
            <slot name="header" />
            <button v-if="showClose" type="button" class="modal-close" @click="close" aria-label="关闭">&times;</button>
          </div>
          <div class="modal-body"><slot /></div>
          <div v-if="$slots.footer" class="modal-footer"><slot name="footer" /></div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped lang="scss">
.modal-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(0,0,0,0.5); backdrop-filter: blur(4px);
  display: flex; align-items: center; justify-content: center; padding: 16px;
}
.modal-content {
  background: #fff; border-radius: 12px; max-width: 90vw; max-height: calc(100vh - 40px);
  display: flex; flex-direction: column; overflow: hidden;
  box-shadow: 0 16px 48px rgba(0,0,0,0.18);
  .dark & { background: #1e293b; box-shadow: 0 16px 48px rgba(0,0,0,0.5); }
}
.modal-header {
  display: flex; align-items: center; padding: 16px 20px; border-bottom: 1px solid #f1f5f9;
  .dark & { border-bottom-color: #334155; }
}
.modal-title {
  margin: 0; flex: 1; font-size: 1.1rem; font-weight: 600; color: #1e293b;
  .dark & { color: #e2e8f0; }
}
.modal-close {
  background: none; border: none; font-size: 1.5rem; color: #94a3b8; cursor: pointer;
  width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;
  border-radius: 6px; transition: all 0.2s; flex-shrink: 0;
  &:hover { color: #64748b; background: rgba(0,0,0,0.05); }
  .dark & { color: #64748b; &:hover { color: #94a3b8; background: rgba(255,255,255,0.08); } }
}
.modal-body { padding: 20px; overflow-y: auto; flex: 1; }
.modal-footer {
  padding: 12px 20px; border-top: 1px solid #f1f5f9; display: flex; justify-content: flex-end; gap: 8px;
  .dark & { border-top-color: #334155; }
}

/* Transition 动画 */
.modal-enter-active {
  transition: opacity 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}
.modal-enter-active .modal-content {
  animation: modal-pop-in 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.modal-leave-active {
  transition: opacity 0.2s ease;
}
.modal-leave-active .modal-content {
  animation: modal-pop-out 0.2s ease forwards;
}
.modal-enter-from,
.modal-leave-to {
  opacity: 0;
}

@keyframes modal-pop-in {
  0% { transform: scale(0.8) translateY(20px); opacity: 0; }
  60% { transform: scale(1.01) translateY(-2px); }
  100% { transform: scale(1) translateY(0); opacity: 1; }
}
@keyframes modal-pop-out {
  0% { transform: scale(1); opacity: 1; }
  100% { transform: scale(0.9) translateY(10px); opacity: 0; }
}
</style>
