<template>
  <!-- 图标 path 为本地静态常量字符串 -->
  <!-- eslint-disable vue/no-v-html -->
  <svg
    v-if="iconBody"
    class="form-field-icon"
    :width="size"
    :height="size"
    viewBox="0 0 16 16"
    fill="none"
    aria-hidden="true"
    v-html="iconBody"
  />
  <!-- eslint-enable vue/no-v-html -->
</template>

<script setup lang="ts">
type FormFieldIconName =
  | 'user-16-solid'
  | 'envelope-16-solid'
  | 'shield-check-16-solid'
  | 'lock-closed-16-solid'
  | 'x-circle-16-solid'
  | 'eye-16-solid'
  | 'eye-slash-16-solid'

interface Props {
  name: FormFieldIconName
  size?: number | string
}

const props = withDefaults(defineProps<Props>(), {
  size: 16,
})

const iconBodies: Record<FormFieldIconName, string> = {
  'user-16-solid': '<path fill="currentColor" d="M8 8a3 3 0 1 0 0-6a3 3 0 0 0 0 6m4.735 6c.618 0 1.093-.561.872-1.139a6.002 6.002 0 0 0-11.215 0c-.22.578.254 1.139.872 1.139z"/>',
  'envelope-16-solid': '<g fill="currentColor"><path d="M2.5 3A1.5 1.5 0 0 0 1 4.5v.793q.039.014.076.032L7.674 8.51c.206.1.446.1.652 0l6.598-3.185l.076-.032V4.5A1.5 1.5 0 0 0 13.5 3z"/><path d="M15 6.954L8.978 9.86a2.25 2.25 0 0 1-1.956 0L1 6.954V11.5A1.5 1.5 0 0 0 2.5 13h11a1.5 1.5 0 0 0 1.5-1.5z"/></g>',
  'shield-check-16-solid': '<path fill="currentColor" fill-rule="evenodd" d="M8.5 1.709a.75.75 0 0 0-1 0a8.96 8.96 0 0 1-4.84 2.217a.75.75 0 0 0-.654.72Q2 4.821 2 5c0 4.052 2.295 7.567 5.653 9.317a.75.75 0 0 0 .694 0a10.5 10.5 0 0 0 5.647-9.672a.75.75 0 0 0-.654-.719A8.96 8.96 0 0 1 8.5 1.71m2.34 5.504a.75.75 0 0 0-1.18-.926L7.394 9.17l-1.156-.99a.75.75 0 1 0-.976 1.138l1.75 1.5a.75.75 0 0 0 1.078-.106z" clip-rule="evenodd"/>',
  'lock-closed-16-solid': '<path fill="currentColor" fill-rule="evenodd" d="M8 1a3.5 3.5 0 0 0-3.5 3.5V7A1.5 1.5 0 0 0 3 8.5v5A1.5 1.5 0 0 0 4.5 15h7a1.5 1.5 0 0 0 1.5-1.5v-5A1.5 1.5 0 0 0 11.5 7V4.5A3.5 3.5 0 0 0 8 1m2 6V4.5a2 2 0 1 0-4 0V7z" clip-rule="evenodd"/>',
  'x-circle-16-solid': '<path fill="currentColor" fill-rule="evenodd" d="M8 15A7 7 0 1 0 8 1a7 7 0 0 0 0 14m2.78-4.22a.75.75 0 0 1-1.06 0L8 9.06l-1.72 1.72a.75.75 0 1 1-1.06-1.06L6.94 8L5.22 6.28a.75.75 0 0 1 1.06-1.06L8 6.94l1.72-1.72a.75.75 0 1 1 1.06 1.06L9.06 8l1.72 1.72a.75.75 0 0 1 0 1.06" clip-rule="evenodd"/>',
  'eye-16-solid': '<g fill="currentColor"><path d="M8 9.5a1.5 1.5 0 1 0 0-3a1.5 1.5 0 0 0 0 3"/><path fill-rule="evenodd" d="M1.38 8.28a.87.87 0 0 1 0-.566a7.003 7.003 0 0 1 13.238.006a.87.87 0 0 1 0 .566A7.003 7.003 0 0 1 1.379 8.28M11 8a3 3 0 1 1-6 0a3 3 0 0 1 6 0" clip-rule="evenodd"/></g>',
  'eye-slash-16-solid': '<path fill="currentColor" fill-rule="evenodd" d="M3.28 2.22a.75.75 0 0 0-1.06 1.06l10.5 10.5a.75.75 0 1 0 1.06-1.06l-1.322-1.323a7 7 0 0 0 2.16-3.11a.87.87 0 0 0 0-.567A7.003 7.003 0 0 0 4.82 3.76zm3.196 3.195l1.135 1.136A1.502 1.502 0 0 1 9.45 8.389l1.136 1.135a3 3 0 0 0-4.109-4.109" clip-rule="evenodd"/><path fill="currentColor" d="m7.812 10.994l1.816 1.816A7.003 7.003 0 0 1 1.38 8.28a.87.87 0 0 1 0-.566a7 7 0 0 1 1.113-2.039l2.513 2.513a3 3 0 0 0 2.806 2.806"/>',
}

const iconBody = computed(() => iconBodies[props.name])
</script>

<style scoped lang="scss">
.form-field-icon {
  display: block;
  flex-shrink: 0;
}
</style>
