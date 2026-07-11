<template>
  <div class="page">
    <n-card title="后端残留清理">
      <n-alert type="warning" :show-icon="false" style="margin-bottom: 12px">
        该操作会清理当前推送目标后端中的历史采集候选与推送记录。failed/rejected 候选会按保留天数清理，推送记录会按保留天数统一清理。
      </n-alert>
      <n-form label-placement="top">
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="候选保留天数">
            <n-input-number v-model:value="candidateRetentionDays" :min="1" :max="3650" />
          </n-form-item-gi>
          <n-form-item-gi label="推送记录保留天数">
            <n-input-number v-model:value="pushRecordRetentionDays" :min="1" :max="3650" />
          </n-form-item-gi>
        </n-grid>
        <n-space>
          <n-button type="error" :loading="cleaning" @click="runCleanup">执行清理</n-button>
        </n-space>
      </n-form>
      <n-alert v-if="cleanupResult" type="success" :show-icon="false" style="margin-top: 16px">
        已清理候选 {{ cleanupResult.deletedCandidateCount }} 条，推送记录 {{ cleanupResult.deletedPushRecordCount }} 条。
      </n-alert>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useMessage } from 'naive-ui'
import { workerApi } from '../api/http'

interface CleanupResponse {
  deletedCandidateCount: number
  deletedPushRecordCount: number
}

const message = useMessage()
const cleaning = ref(false)
const candidateRetentionDays = ref(30)
const pushRecordRetentionDays = ref(90)
const cleanupResult = ref<CleanupResponse | null>(null)

const runCleanup = async () => {
  if (!window.confirm('确认清理当前推送目标后端中的历史候选和推送记录？')) {
    return
  }
  cleaning.value = true
  try {
    const { data } = await workerApi.post<CleanupResponse>('/backend/cleanup', null, {
      params: {
        candidate_retention_days: candidateRetentionDays.value,
        push_record_retention_days: pushRecordRetentionDays.value,
      }
    })
    cleanupResult.value = data
    message.success('清理完成')
  } finally {
    cleaning.value = false
  }
}
</script>

<style scoped>
.page {
  max-width: 960px;
  margin: 0 auto;
}
</style>
