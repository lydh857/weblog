<script setup lang="ts">
import { useMessageStore } from '~/composables/modal/useMessage'

const { messages, remove } = useMessageStore()

const isDarkMode = ref(false)
let themeObserver: MutationObserver | null = null

function syncDarkMode() {
  if (!import.meta.client) return
  const root = document.documentElement
  const body = document.body
  isDarkMode.value = root.classList.contains('dark') || body.classList.contains('dark')
}

onMounted(() => {
  if (!import.meta.client) return
  syncDarkMode()
  themeObserver = new MutationObserver(syncDarkMode)
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
  themeObserver.observe(document.body, {
    attributes: true,
    attributeFilter: ['class', 'data-theme'],
  })
})

onUnmounted(() => {
  themeObserver?.disconnect()
  themeObserver = null
})
</script>

<template>
  <ClientOnly>
    <Teleport to="body">
      <div class="msg-container" :class="{ 'msg-container--dark': isDarkMode }">
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

<style scoped lang="scss">
.msg-container {
  --msg-bg: #ffffff;
  --msg-border: rgba(148, 163, 184, 0.28);
  --msg-text: #1e293b;
  --msg-shadow: 0 8px 18px rgba(15, 23, 42, 0.12);
  --msg-close: #94a3b8;
  --msg-close-hover: #64748b;
  --msg-close-hover-bg: rgba(0, 0, 0, 0.06);

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
  border: 1px solid var(--msg-border); width: 100%;
  box-shadow: var(--msg-shadow);
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
.msg-text { flex: 1; font-size: 14px; line-height: 1.5; color: var(--msg-text); word-break: break-word; }
.msg-close {
  position: absolute;
  top: 50%;
  right: 2px;
  transform: translateY(-50%);
  background: none; border: none; font-size: 16px; color: var(--msg-close);
  cursor: pointer; width: 32px; height: 32px; display: flex; align-items: center;
  justify-content: center; border-radius: 8px; padding: 0; transition: color 0.2s, background 0.2s;
  line-height: 1;
}
.msg-close:hover { color: var(--msg-close-hover); background: var(--msg-close-hover-bg); }

.msg-container--dark {
  --msg-bg: linear-gradient(180deg, #171b20, #101215);
  --msg-border: transparent;
  --msg-text: #e2e8f0;
  --msg-shadow: 0 10px 24px rgba(2, 6, 23, 0.4);
  --msg-close: #9aa5b5;
  --msg-close-hover: #d6dbe4;
  --msg-close-hover-bg: rgba(148, 163, 184, 0.12);
}

.msg-container--dark .msg-item.msg-info,
.msg-container--dark .msg-item.msg-success,
.msg-container--dark .msg-item.msg-warning,
.msg-container--dark .msg-item.msg-error {
  background: linear-gradient(180deg, rgba(27, 32, 39, 0.96), rgba(14, 17, 22, 0.98)) !important;
  border-color: transparent !important;
  backdrop-filter: blur(6px);
}


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
