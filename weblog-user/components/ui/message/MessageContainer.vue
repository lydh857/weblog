<script setup lang="ts">
import { useMessageStore } from '~/composables/useMessage'

const { messages, remove } = useMessageStore()
</script>

<template>
  <ClientOnly>
    <Teleport to="body">
      <div class="msg-container">
        <TransitionGroup name="msg" tag="div" class="msg-list">
          <div
            v-for="msg in messages"
            :key="msg.id"
            class="msg-item"
            :class="`msg-${msg.type}`"
          >
            <Icon v-if="msg.type === 'info'" name="heroicons:information-circle-16-solid" size="18" class="msg-icon" />
            <Icon v-else-if="msg.type === 'success'" name="heroicons:check-circle-16-solid" size="18" class="msg-icon" />
            <Icon v-else-if="msg.type === 'warning'" name="heroicons:exclamation-triangle-16-solid" size="18" class="msg-icon" />
            <Icon v-else name="heroicons:x-circle-16-solid" size="18" class="msg-icon" />
            <span class="msg-text">{{ msg.content }}</span>
            <button type="button" class="msg-close" aria-label="关闭" @click="remove(msg.id)">&times;</button>
          </div>
        </TransitionGroup>
      </div>
    </Teleport>
  </ClientOnly>
</template>

<style scoped>
.msg-container {
  position: fixed; top: 24px; left: 0; width: 100vw;
  display: flex; justify-content: center;
  z-index: var(--z-toast); pointer-events: none;
  padding: 0 16px; box-sizing: border-box;
}
.msg-list {
  width: 100%; max-width: 420px;
}
.msg-list {
  display: flex; flex-direction: column; align-items: center;
  position: relative;
}
.msg-item {
  pointer-events: auto;
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; margin-bottom: 8px; border-radius: 8px;
  border: 1px solid; width: 100%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  backface-visibility: hidden;
}
.msg-info { background: #eff6ff; border-color: #bfdbfe; }
.msg-info .msg-icon { color: #3b82f6; }
.msg-success { background: #f0fdf4; border-color: #bbf7d0; }
.msg-success .msg-icon { color: #22c55e; }
.msg-warning { background: #fffbeb; border-color: #fde68a; }
.msg-warning .msg-icon { color: #f59e0b; }
.msg-error { background: #fef2f2; border-color: #fecaca; }
.msg-error .msg-icon { color: #ef4444; }
.msg-icon { flex-shrink: 0; }
.msg-text { flex: 1; font-size: 14px; line-height: 1.5; color: #1e293b; word-break: break-word; }
.msg-close {
  flex-shrink: 0; background: none; border: none; font-size: 16px; color: #94a3b8;
  cursor: pointer; width: 20px; height: 20px; display: flex; align-items: center;
  justify-content: center; border-radius: 4px; padding: 0; transition: color 0.2s, background 0.2s;
}
.msg-close:hover { color: #64748b; background: rgba(0,0,0,0.06); }

/* 暗色模式 */
:root.dark .msg-info { background: #1e293b; border-color: #1e3a5f; }
:root.dark .msg-success { background: #1a2e1a; border-color: #1a3d1a; }
:root.dark .msg-warning { background: #2e2a1a; border-color: #3d351a; }
:root.dark .msg-error { background: #2e1a1a; border-color: #3d1a1a; }
:root.dark .msg-text { color: #e2e8f0; }
:root.dark .msg-close { color: #64748b; }
:root.dark .msg-close:hover { color: #94a3b8; background: rgba(255,255,255,0.08); }

/* TransitionGroup 动画 */
.msg-move {
  transition: transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.msg-enter-active {
  transition: opacity 0.35s cubic-bezier(0.22, 1, 0.36, 1),
              transform 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}
.msg-leave-active {
  transition: opacity 0.25s ease,
              transform 0.25s ease;
  /* 不用 position:absolute，避免布局跳动 */
}
.msg-enter-from {
  opacity: 0;
  transform: translateY(-20px) scale(0.85);
}
.msg-leave-to {
  opacity: 0;
  transform: translateX(60px);
}
</style>
