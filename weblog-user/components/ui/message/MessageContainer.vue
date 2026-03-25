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
            <Icon v-if="msg.type === 'info'" name="heroicons:information-circle-20-solid" size="18" class="msg-icon" />
            <Icon v-else-if="msg.type === 'success'" name="heroicons:check-circle-20-solid" size="18" class="msg-icon" />
            <Icon v-else-if="msg.type === 'warning'" name="heroicons:exclamation-triangle-20-solid" size="18" class="msg-icon" />
            <Icon v-else name="heroicons:x-circle-20-solid" size="18" class="msg-icon" />
            <span class="msg-text">{{ msg.content }}</span>
            <button type="button" class="msg-close touch-target" aria-label="关闭" @click="remove(msg.id)">&times;</button>
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
  position: relative;
  pointer-events: auto;
  display: flex; align-items: center; gap: 8px;
  padding: 10px 44px 10px 14px; margin-bottom: 8px; border-radius: 8px;
  border: 1px solid; width: 100%;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  backface-visibility: hidden;
}
.msg-info { background: var(--status-info-soft-bg); border-color: var(--status-info-soft-border); }
.msg-info .msg-icon { color: var(--status-info); }
.msg-success { background: var(--status-success-soft-bg); border-color: var(--status-success-soft-border); }
.msg-success .msg-icon { color: var(--status-success); }
.msg-warning { background: var(--status-warning-soft-bg); border-color: var(--status-warning-soft-border); }
.msg-warning .msg-icon { color: var(--status-warning); }
.msg-error { background: var(--status-danger-soft-bg); border-color: var(--status-danger-soft-border); }
.msg-error .msg-icon { color: var(--status-danger); }
.msg-icon { flex-shrink: 0; }
.msg-text { flex: 1; font-size: 14px; line-height: 1.5; color: #1e293b; word-break: break-word; }
.msg-close {
  position: absolute;
  top: 50%;
  right: 2px;
  transform: translateY(-50%);
  background: none; border: none; font-size: 16px; color: #94a3b8;
  cursor: pointer; width: 32px; height: 32px; display: flex; align-items: center;
  justify-content: center; border-radius: 8px; padding: 0; transition: color 0.2s, background 0.2s;
  line-height: 1;
}
.msg-close:hover { color: #64748b; background: rgba(0,0,0,0.06); }

/* 暗色模式（柔和黑） */
:root.dark .msg-info { background: var(--status-info-soft-bg); border-color: var(--status-info-soft-border); }
:root.dark .msg-success { background: var(--status-success-soft-bg); border-color: var(--status-success-soft-border); }
:root.dark .msg-warning { background: var(--status-warning-soft-bg); border-color: var(--status-warning-soft-border); }
:root.dark .msg-error { background: var(--status-danger-soft-bg); border-color: var(--status-danger-soft-border); }
:root.dark .msg-text { color: #d6dbe4; }
:root.dark .msg-close { color: #9aa5b5; }
:root.dark .msg-close:hover { color: #d6dbe4; background: rgba(148,163,184,0.12); }

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
