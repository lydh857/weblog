<template>
  <n-config-provider :theme="theme">
    <n-message-provider>
      <n-layout has-sider style="height: 100vh">
        <n-layout-sider width="220" bordered>
          <div class="logo">本地采集工作台</div>
          <n-menu :options="menuOptions" :render-label="renderMenuLabel" />
        </n-layout-sider>
        <n-layout-content content-style="padding: 16px; overflow: auto">
          <div class="topbar">
            <div class="target-indicator" :class="`is-${activeTarget}`">
              当前推送目标：{{ activeTargetLabel }}
            </div>
          </div>
          <router-view />
        </n-layout-content>
      </n-layout>
    </n-message-provider>
  </n-config-provider>
</template>

<script setup lang="ts">
import { computed, inject, onMounted, onUnmounted, ref } from 'vue'
import { menuOptions, renderMenuLabel } from './layout/menu'
import { workerApi } from './api/http'

interface PushTargetConfigResponse {
  active_target: 'local' | 'server'
}

const theme = inject('theme')
const activeTarget = ref<'local' | 'server'>('local')

const activeTargetLabel = computed(() => activeTarget.value === 'local' ? '本地草稿箱' : '服务器草稿箱')

let refreshTimer: ReturnType<typeof setInterval> | null = null

const loadPushTarget = async () => {
  try {
    const { data } = await workerApi.get<PushTargetConfigResponse>('/settings/push-target')
    activeTarget.value = data.active_target
  } catch {
    // ignore
  }
}

onMounted(() => {
  loadPushTarget()
  refreshTimer = setInterval(loadPushTarget, 5000)
})

onUnmounted(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = null
  }
})
</script>

<style scoped>
.logo {
  font-weight: 700;
  padding: 16px;
  font-size: 16px;
}

.topbar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

.target-indicator {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  font-size: 13px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.08);
}

.target-indicator.is-local {
  color: #18a058;
}

.target-indicator.is-server {
  color: #2080f0;
}
</style>
