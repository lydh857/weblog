<template>
  <div class="error-page">
    <div v-if="isChunkError" class="error-card warning">
      <div class="error-icon">⚠️</div>
      <h2>页面加载异常</h2>
      <p>检测到浏览器广告拦截插件可能阻止了页面资源加载。</p>
      <p class="hint">请尝试以下操作：</p>
      <ul>
        <li>关闭广告拦截插件（如 uBlock Origin、AdBlock 等）</li>
        <li>或将本站域名加入插件白名单</li>
      </ul>
      <button class="retry-btn" @click="reload">刷新页面</button>
    </div>
    <div v-else class="error-card">
      <div class="error-icon">😵</div>
      <h2>{{ error?.statusCode || 500 }} 错误</h2>
      <p>{{ error?.message || '页面出了点问题' }}</p>
      <button class="retry-btn" @click="handleError">返回首页</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { NuxtError } from '#app'

const props = defineProps<{ error: NuxtError }>()

const isChunkError = computed(() => {
  const msg = props.error?.message || ''
  return msg.includes('dynamically imported module') || msg.includes('Failed to fetch')
})

function reload() {
  window.location.reload()
}

function handleError() {
  clearError({ redirect: '/' })
}
</script>

<style scoped>
.error-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 2rem;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background: #f8fafc;
}
.error-card {
  max-width: 500px;
  width: 100%;
  text-align: center;
  padding: 2.5rem 2rem;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08);
}
.error-card.warning {
  border: 1px solid #e6a23c;
  background: #fffbeb;
}
.error-icon {
  font-size: 3rem;
  margin-bottom: 1rem;
}
.error-card h2 {
  font-size: 1.25rem;
  color: #1e293b;
  margin-bottom: 0.75rem;
}
.error-card p {
  color: #64748b;
  line-height: 1.7;
  margin-bottom: 0.5rem;
}
.error-card .hint {
  margin-top: 1rem;
  font-weight: 600;
  color: #92400e;
}
.error-card ul {
  text-align: left;
  color: #92400e;
  margin: 0.5rem auto 1.5rem;
  max-width: 320px;
  line-height: 1.8;
}
.retry-btn {
  margin-top: 1rem;
  padding: 0.6rem 2rem;
  background: #5b8def;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 0.9rem;
  cursor: pointer;
  transition: background 0.2s;
}
.retry-btn:hover {
  background: #4a7de0;
}
.warning .retry-btn {
  background: #e6a23c;
}
.warning .retry-btn:hover {
  background: #d97706;
}
</style>
