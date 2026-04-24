<template>
  <div class="page">
    <n-card title="推送目标切换">
      <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
        当前切换的是 crawler-worker 的真实推送目标。选择本地时，草稿会进本地后端数据库；选择服务器时，草稿会直接进入服务器后端数据库。
      </n-alert>

      <n-form label-placement="top">
        <n-form-item label="当前激活目标">
          <n-select v-model:value="activeTarget" :options="targetOptions" style="width: 240px" />
        </n-form-item>

        <n-grid :cols="2" :x-gap="16">
          <n-form-item-gi label="本地后端地址">
            <n-input v-model:value="profiles.local.base_url" placeholder="http://127.0.0.1:9091" />
          </n-form-item-gi>
          <n-form-item-gi label="服务器后端地址">
            <n-input v-model:value="profiles.server.base_url" placeholder="https://your-server-domain" />
          </n-form-item-gi>

          <n-form-item-gi label="本地集成令牌">
            <n-input v-model:value="profiles.local.crawler_token" type="password" show-password />
          </n-form-item-gi>
          <n-form-item-gi label="服务器集成令牌">
            <n-input v-model:value="profiles.server.crawler_token" type="password" show-password />
          </n-form-item-gi>

          <n-form-item-gi label="本地设备标识">
            <n-input v-model:value="profiles.local.device_id" placeholder="local-worker" />
          </n-form-item-gi>
          <n-form-item-gi label="服务器设备标识">
            <n-input v-model:value="profiles.server.device_id" placeholder="server-worker" />
          </n-form-item-gi>

          <n-form-item-gi label="本地请求来源">
            <n-input v-model:value="profiles.local.request_origin" placeholder="http://localhost:3001" />
          </n-form-item-gi>
          <n-form-item-gi label="服务器请求来源">
            <n-input v-model:value="profiles.server.request_origin" placeholder="https://your-admin-domain" />
          </n-form-item-gi>
        </n-grid>

        <n-space>
          <n-button @click="loadConfig">刷新配置</n-button>
          <n-button type="primary" :loading="saving" @click="saveConfig">保存并切换</n-button>
        </n-space>
      </n-form>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { workerApi } from '../api/http'

interface PushTargetProfile {
  base_url: string
  crawler_token: string
  device_id: string
  request_origin: string
}

interface PushTargetConfigResponse {
  active_target: 'local' | 'server'
  profiles: {
    local: PushTargetProfile
    server: PushTargetProfile
  }
}

const message = useMessage()
const saving = ref(false)
const activeTarget = ref<'local' | 'server'>('local')
const profiles = reactive<{ local: PushTargetProfile; server: PushTargetProfile }>({
  local: { base_url: '', crawler_token: '', device_id: '', request_origin: '' },
  server: { base_url: '', crawler_token: '', device_id: '', request_origin: '' },
})

const targetOptions = [
  { label: '本地草稿箱', value: 'local' },
  { label: '服务器草稿箱', value: 'server' },
]

const applyConfig = (data: PushTargetConfigResponse) => {
  activeTarget.value = data.active_target
  Object.assign(profiles.local, data.profiles.local)
  Object.assign(profiles.server, data.profiles.server)
}

const loadConfig = async () => {
  const { data } = await workerApi.get<PushTargetConfigResponse>('/settings/push-target')
  applyConfig(data)
}

const saveConfig = async () => {
  saving.value = true
  try {
    const { data } = await workerApi.put<PushTargetConfigResponse>('/settings/push-target', {
      active_target: activeTarget.value,
      profiles,
    })
    applyConfig(data)
    message.success(activeTarget.value === 'local' ? '已切换到本地草稿箱' : '已切换到服务器草稿箱')
  } finally {
    saving.value = false
  }
}

loadConfig()
</script>

<style scoped>
.page {
  max-width: 1100px;
  margin: 0 auto;
}
</style>
