<template>
  <div class="ai-meta-card">
    <div class="ai-meta-card-header">
      <span class="ai-meta-card-title">{{ title }}</span>
      <div class="ai-meta-card-actions">
        <button class="ai-meta-action-btn adopt" @click="$emit('adopt')" :disabled="loading">
          {{ adoptText }}
        </button>
        <button class="ai-meta-action-btn regen" @click="$emit('regenerate')" :disabled="loading">
          <el-icon v-if="loading" class="is-loading"><Loading /></el-icon>
          <svg v-else viewBox="0 0 24 24" width="12" height="12" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
        </button>
      </div>
    </div>
    <div class="ai-meta-card-body">
      <div v-if="loadingText" class="ai-card-shimmer">
        <span class="ai-card-shimmer-text">{{ loadingText }}</span>
      </div>
      <slot v-else />
    </div>
  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'

withDefaults(defineProps<{
  title: string
  loading?: boolean
  adoptText?: string
  loadingText?: string
}>(), {
  loading: false,
  adoptText: '采用',
  loadingText: '',
})

defineEmits<{
  adopt: []
  regenerate: []
}>()
</script>

<style scoped>
.ai-meta-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
}

.ai-meta-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  background: var(--el-fill-color-lighter);
}

.ai-meta-card-title {
  font-size: 12px;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.ai-meta-card-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-meta-action-btn {
  display: inline-flex;
  align-items: center;
  gap: 3px;
  padding: 2px 8px;
  border: none;
  border-radius: 4px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
  background: transparent;
}

.ai-meta-action-btn.adopt {
  color: var(--el-color-primary);
  font-weight: 500;
}
.ai-meta-action-btn.adopt:hover:not(:disabled) {
  background: var(--el-color-primary-light-9);
}

.ai-meta-action-btn.regen {
  color: var(--el-text-color-secondary);
  padding: 3px 5px;
}
.ai-meta-action-btn.regen:hover:not(:disabled) {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
}

.ai-meta-action-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ai-meta-card-body {
  padding: 8px 10px;
}

.ai-card-shimmer {
  display: flex;
  align-items: center;
  min-height: 20px;
}

.ai-card-shimmer-text {
  font-size: 13px;
  overflow: hidden;
  background: linear-gradient(90deg, var(--el-text-color-placeholder) 0%, var(--el-color-primary) 50%, var(--el-text-color-placeholder) 100%);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: ai-card-sweep 1.5s ease-in-out infinite;
}

@keyframes ai-card-sweep {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}
</style>