<script setup lang="ts">
import { computed } from 'vue'
import { sanitizeHtml } from '~/utils/security/xss'

const props = defineProps<{
  /** 需要安全渲染的 HTML 字符串 */
  content: string
  /** 外层标签，默认 div */
  tag?: string
}>()

const safeContent = computed(() => sanitizeHtml(props.content))
</script>

<template>
  <!-- 统一安全渲染组件，内部内容已净化 -->
  <!-- eslint-disable-next-line vue/no-v-html -->
  <component :is="tag || 'div'" v-html="safeContent" />
</template>
