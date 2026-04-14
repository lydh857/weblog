<script setup lang="ts">
import { watch, onMounted, onUnmounted } from 'vue'
import { lockScroll, unlockScroll } from '~/composables/layout/useScrollLock'

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
    <Transition name="modal-fade" appear>
      <div v-if="visible" class="modal-overlay" :style="{ zIndex }" @click="onMaskClick">
        <div class="modal-content" :style="{ width: typeof width === 'number' ? `${width}px` : width }" @click.stop>
          <div v-if="title || showClose" class="modal-header">
            <h3 v-if="title" class="modal-title">{{ title }}</h3>
            <slot name="header" />
            <button v-if="showClose" type="button" class="modal-close touch-target" aria-label="关闭" @click="close">&times;</button>
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
  background: rgba(0,0,0,0.4); backdrop-filter: blur(8px);
  display: flex; align-items: center; justify-content: center; padding: 16px;
  will-change: opacity;

  .dark & {
    background: rgba(0,0,0,0.6);
  }
}

@media (hover: none) and (pointer: coarse) {
  .modal-overlay {
    backdrop-filter: none;
    background: rgba(0, 0, 0, 0.52);
  }
}
.modal-content {
  background: #fff; border-radius: 12px; max-width: 90vw; max-height: calc(100vh - 40px);
  display: flex; flex-direction: column; overflow: hidden;
  box-shadow: 0 16px 48px rgba(0,0,0,0.18);
  transform: translate3d(0, 0, 0);
  will-change: transform;
  .dark & {
    background: $color-dark-bg-elevated;
    box-shadow: 0 16px 48px rgba(0,0,0,0.5);
    border: 1px solid rgba(148, 163, 184, 0.14);
  }
}
.modal-header {
  display: flex; align-items: center; padding: 16px 20px; border-bottom: 1px solid #f1f5f9;
  .dark & { border-bottom-color: $color-dark-border; }
}
.modal-title {
  margin: 0; flex: 1; font-size: 1.1rem; font-weight: 600; color: #1e293b;
  .dark & { color: $color-dark-text; }
}
.modal-close {
  background: none; border: none; font-size: 1.5rem; color: #94a3b8; cursor: pointer;
  width: 44px; height: 44px; min-width: 44px; min-height: 44px;
  display: flex; align-items: center; justify-content: center;
  border-radius: 10px; transition: all 0.2s; flex-shrink: 0;
  &:hover { color: #64748b; background: rgba(0,0,0,0.05); }
  .dark & {
    color: $color-dark-text-muted;
    &:hover { color: $color-dark-text; background: rgba(148,163,184,0.12); }
  }
}
.modal-body { padding: 20px; overflow-y: auto; flex: 1; }
.modal-footer {
  padding: 12px 20px; border-top: 1px solid #f1f5f9; display: flex; justify-content: flex-end; gap: 8px;
  .dark & { border-top-color: $color-dark-border; }
}

/* Transition 动画 */
.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;

  .modal-content {
    transition: transform 0.25s;
  }
}

.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;

  .modal-content {
    transform: translateY(20px) scale(0.96);
  }
}
</style>
