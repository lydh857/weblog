<template>
  <div
    class="unified-page-loader"
    :class="{ 'unified-page-loader--compact': compact }"
    role="status"
    aria-live="polite"
    aria-busy="true"
    :aria-label="text || '页面加载中'"
  >
    <span v-if="!compact" class="unified-page-loader__spinner" aria-hidden="true" />
    <p v-if="text" class="unified-page-loader__text">{{ text }}</p>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  text?: string
  compact?: boolean
}>(), {
  text: '加载中...',
  compact: false,
})
</script>

<style scoped lang="scss">
.unified-page-loader {
  min-height: 240px;
  width: 100%;
  border-radius: 14px;
  border: 1px dashed rgba(148, 163, 184, 0.45);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  background:
    radial-gradient(circle at 50% 0%, rgba(91, 141, 239, 0.16), transparent 58%),
    linear-gradient(180deg, rgba(244, 248, 255, 0.86), rgba(237, 242, 251, 0.9));
}

:global(html.dark) .unified-page-loader {
  border-color: rgba(71, 85, 105, 0.55);
  background:
    radial-gradient(circle at 50% 0%, rgba(91, 141, 239, 0.22), transparent 58%),
    linear-gradient(180deg, rgba(22, 32, 49, 0.92), rgba(16, 24, 38, 0.94));
}

.unified-page-loader__spinner {
  width: 28px;
  height: 28px;
  border-radius: 999px;
  border: 2px solid rgba(148, 163, 184, 0.35);
  border-top-color: #4f7cd8;
  animation: unified-page-loader-spin 0.8s linear infinite;
}

:global(html.dark) .unified-page-loader__spinner {
  border-color: rgba(148, 163, 184, 0.26);
  border-top-color: #93b4f4;
}

.unified-page-loader__text {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #45649d;
  letter-spacing: 0.4px;
}

:global(html.dark) .unified-page-loader__text {
  color: #aac6fa;
}

.unified-page-loader--compact {
  min-height: 88px;
  border-radius: 12px;
  gap: 0;
}

.unified-page-loader--compact .unified-page-loader__text {
  font-size: 13px;
}

@media (max-width: 768px) {
  .unified-page-loader {
    min-height: 208px;
  }
}

@keyframes unified-page-loader-spin {
  to {
    transform: rotate(360deg);
  }
}
</style>
