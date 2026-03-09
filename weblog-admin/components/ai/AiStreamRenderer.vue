<template>
  <div class="ai-stream-renderer">
    <!-- 加载状态 -->
    <div v-if="loading && !displayText" class="ai-loading">
      <el-icon class="ai-loading-icon"><Loading /></el-icon>
      <span>AI 正在思考...</span>
    </div>

    <!-- 流式内容：使用 md-editor-v3 的 MdPreview 渲染 Markdown -->
    <div v-if="displayText" class="ai-content">
      <MdPreview
        :model-value="displayText"
        :theme="editorTheme"
        preview-theme="default"
        class="ai-md-preview"
      />
      <span v-if="loading" class="ai-cursor">▍</span>
    </div>


  </div>
</template>

<script setup lang="ts">
import { Loading } from '@element-plus/icons-vue'
import { MdPreview } from 'md-editor-v3'

const props = defineProps<{
  text: string
  loading: boolean
}>()

const colorMode = useColorMode()
const editorTheme = computed(() => colorMode.value === 'dark' ? 'dark' : 'light')

const displayText = computed(() => props.text)
</script>

<style scoped>
.ai-stream-renderer {
  padding: 4px 0;
}

.ai-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  padding: 4px 0;
}

.ai-loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.ai-content {
  font-size: 14px;
  line-height: 1.7;
  color: var(--el-text-color-primary);
}

/* 重置 MdPreview 在小面板中的样式 */
.ai-md-preview {
  background: transparent !important;
  padding: 0 !important;
}
.ai-md-preview :deep(.md-editor-preview-wrapper) {
  padding: 0 !important;
}
.ai-md-preview :deep(.md-editor-preview) {
  font-size: 14px;
  line-height: 1.7;
}
.ai-md-preview :deep(.md-editor-preview p) {
  margin: 0 0 6px;
}
.ai-md-preview :deep(.md-editor-preview h1),
.ai-md-preview :deep(.md-editor-preview h2),
.ai-md-preview :deep(.md-editor-preview h3),
.ai-md-preview :deep(.md-editor-preview h4),
.ai-md-preview :deep(.md-editor-preview h5),
.ai-md-preview :deep(.md-editor-preview h6) {
  margin: 8px 0 4px;
  line-height: 1.4;
}
.ai-md-preview :deep(.md-editor-preview h1:first-child),
.ai-md-preview :deep(.md-editor-preview h2:first-child),
.ai-md-preview :deep(.md-editor-preview h3:first-child),
.ai-md-preview :deep(.md-editor-preview h4:first-child),
.ai-md-preview :deep(.md-editor-preview h5:first-child),
.ai-md-preview :deep(.md-editor-preview h6:first-child) {
  margin-top: 0;
}
.ai-md-preview :deep(.md-editor-preview ul),
.ai-md-preview :deep(.md-editor-preview ol) {
  margin: 4px 0;
  padding-left: 20px;
}
.ai-md-preview :deep(.md-editor-preview li) {
  margin: 2px 0;
}
.ai-md-preview :deep(.md-editor-preview code) {
  background: var(--el-fill-color-light);
  padding: 2px 4px;
  border-radius: 3px;
  font-size: 13px;
}
.ai-md-preview :deep(.md-editor-preview pre) {
  border-radius: 6px;
  margin: 8px 0;
}

.ai-cursor {
  animation: blink 1s step-end infinite;
  color: var(--el-color-primary);
}

@keyframes blink {
  50% { opacity: 0; }
}

</style>
