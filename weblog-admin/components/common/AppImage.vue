<template>
  <div class="app-image" :style="wrapperStyle">
    <el-image
      :src="src"
      :fit="fit"
      :lazy="lazy"
      :preview-src-list="resolvedPreviewList"
      :preview-teleported="previewTeleported"
      class="app-image__el"
    >
      <template #placeholder>
        <div class="app-image__placeholder" />
      </template>
      <template #error>
        <div class="app-image__error">
          <span>图片加载失败</span>
        </div>
      </template>
    </el-image>
  </div>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  src?: string
  fit?: 'fill' | 'contain' | 'cover' | 'none' | 'scale-down'
  lazy?: boolean
  ratio?: string
  rounded?: number
  previewSrcList?: string[]
  previewTeleported?: boolean
}>(), {
  src: '',
  fit: 'cover',
  lazy: false,
  ratio: '',
  rounded: 8,
  previewSrcList: () => [],
  previewTeleported: true,
})

const resolvedPreviewList = computed(() => {
  if (props.previewSrcList.length > 0) {
    return props.previewSrcList
  }
  return props.src ? [props.src] : []
})

const wrapperStyle = computed<Record<string, string>>(() => {
  const style: Record<string, string> = {
    '--app-image-radius': `${props.rounded}px`,
  }
  if (props.ratio) {
    style.aspectRatio = props.ratio
  }
  return style
})
</script>

<style scoped lang="scss">
.app-image {
  width: 100%;
  height: 100%;
  border-radius: var(--app-image-radius);
  overflow: hidden;
  background: linear-gradient(145deg, var(--el-fill-color-light), var(--el-fill-color-extra-light));
  border: 1px solid var(--el-border-color-lighter);
}

.app-image__el {
  width: 100%;
  height: 100%;
  display: block;
}

:deep(.el-image__inner) {
  width: 100%;
  height: 100%;
}

.app-image__placeholder {
  width: 100%;
  height: 100%;
  background: linear-gradient(90deg, var(--el-fill-color-lighter) 25%, var(--el-fill-color-light) 50%, var(--el-fill-color-lighter) 75%);
  background-size: 200% 100%;
  animation: app-image-shimmer 1.4s infinite;
}

.app-image__error {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
  font-size: 12px;
  background: repeating-linear-gradient(
    -45deg,
    color-mix(in srgb, var(--el-color-danger) 6%, transparent),
    color-mix(in srgb, var(--el-color-danger) 6%, transparent) 8px,
    transparent 8px,
    transparent 16px
  );
}

@keyframes app-image-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
