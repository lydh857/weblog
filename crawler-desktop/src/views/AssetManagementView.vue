<template>
  <div class="page">
    <n-card title="媒体资源管理">
      <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
        待提交：已通过候选仍会用到；已提交可清理：推送完成后可按需清理本地缓存；无引用可清理：失败/拒绝或正文不再引用的资源。
      </n-alert>
      <n-space style="margin-bottom: 12px">
        <n-select v-model:value="statusFilter" :options="statusOptions" style="width: 220px" />
        <n-button @click="refresh">刷新</n-button>
        <n-button type="warning" @click="cleanSubmitted">清理已提交可清理</n-button>
        <n-button type="error" @click="cleanUnused">清理无引用可清理</n-button>
        <n-button type="error" @click="deleteSelected">删除勾选</n-button>
      </n-space>

      <n-space style="margin-bottom: 12px">
        <n-tag type="success">待提交 {{ summary.pendingSubmit }}</n-tag>
        <n-tag type="warning">已提交可清理 {{ summary.submittedCleanable }}</n-tag>
        <n-tag type="error">无引用可清理 {{ summary.unused }}</n-tag>
        <n-tag>总数 {{ summary.total }}</n-tag>
      </n-space>

      <n-grid :cols="4" :x-gap="12" style="margin-bottom: 12px">
        <n-gi>
          <n-card size="small" embedded>
            <div class="stat-label">文件总数</div>
            <div class="stat-value">{{ assetSummary.total_count }}</div>
            <div class="stat-sub">唯一文件 {{ assetSummary.unique_file_count }}</div>
          </n-card>
        </n-gi>
        <n-gi>
          <n-card size="small" embedded>
            <div class="stat-label">缓存总占用</div>
            <div class="stat-value">{{ formatSize(assetSummary.total_file_size) }}</div>
            <div class="stat-sub">实际落盘 {{ formatSize(assetSummary.unique_file_size) }}</div>
          </n-card>
        </n-gi>
        <n-gi>
          <n-card size="small" embedded>
            <div class="stat-label">去重节省</div>
            <div class="stat-value">{{ formatSize(assetSummary.shared_saved_bytes) }}</div>
            <div class="stat-sub">共享文件复用收益</div>
          </n-card>
        </n-gi>
        <n-gi>
          <n-card size="small" embedded>
            <div class="stat-label">可清理资源</div>
            <div class="stat-value">{{ assetSummary.submitted_cleanable_count + assetSummary.unused_count }}</div>
            <div class="stat-sub">已提交 {{ assetSummary.submitted_cleanable_count }} / 无引用 {{ assetSummary.unused_count }}</div>
          </n-card>
        </n-gi>
      </n-grid>

      <n-alert v-if="assetsQuery.status.value === 'error' && !assetsQuery.isFetching.value" type="error" :show-icon="false" style="margin-bottom: 12px">
        媒体资源列表加载失败，请确认 crawler-worker 正在运行。
      </n-alert>
      <div v-if="assetsQuery.isLoading.value" style="margin-bottom: 12px">
        <n-spin size="small" />
      </div>
      <n-empty v-else-if="filteredRows.length === 0" description="当前筛选条件下暂无媒体资源" style="margin-bottom: 12px" />
      <n-data-table
        v-else
        :columns="columns"
        :data="filteredRows"
        :row-key="(row: AssetRow) => row.id"
        :scroll-x="1880"
        @update:checked-row-keys="onChecked"
      />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { NButton, useMessage } from 'naive-ui'
import { workerApi } from '../api/http'
import { formatDateTime, normalizeCellText } from '../utils/format'

interface AssetApiRow {
  id: number
  task_item_id: number
  task_id: number | null
  task_item_state: string | null
  title: string | null
  source_url: string
  preview_url: string
  mime_type: string | null
  file_size: number
  asset_role: string
  usage_status: 'pending_submit' | 'submitted_cleanable' | 'unused' | 'active'
  usage_reason: string
  created_at: string
}

interface AssetSummary {
  total_count: number
  total_file_size: number
  unique_file_count: number
  unique_file_size: number
  shared_saved_bytes: number
  pending_submit_count: number
  submitted_cleanable_count: number
  unused_count: number
  active_count: number
}

interface AssetManageResponse {
  records: AssetApiRow[]
  summary: AssetSummary
}

interface AssetRow extends AssetApiRow {
  preview_url: string
}

const message = useMessage()
const queryClient = useQueryClient()
const selectedAssetIds = ref<number[]>([])
const statusFilter = ref<'all' | 'pending_submit' | 'submitted_cleanable' | 'unused' | 'active'>('all')

const statusOptions = [
  { label: '全部状态', value: 'all' },
  { label: '待提交', value: 'pending_submit' },
  { label: '已提交可清理', value: 'submitted_cleanable' },
  { label: '无引用可清理', value: 'unused' },
  { label: '使用中', value: 'active' }
]

const usageLabelMap: Record<string, string> = {
  pending_submit: '待提交',
  submitted_cleanable: '已提交可清理',
  unused: '无引用可清理',
  active: '使用中'
}

const assetsQuery = useQuery({
  queryKey: ['assetManagement'],
  queryFn: async () => {
    const response = (await workerApi.get<AssetManageResponse>('/tasks/assets?limit=2000')).data
    const base = workerApi.defaults.baseURL || ''
    return {
      records: response.records.map((row) => ({
        ...row,
        preview_url: row.preview_url.startsWith('http') ? row.preview_url : `${base}${row.preview_url}`
      })),
      summary: response.summary
    }
  },
  refetchInterval: 5000
})

const deleteMutation = useMutation({
  mutationFn: async (assetIds: number[]) =>
    workerApi.delete('/tasks/assets', {
      data: { asset_ids: assetIds }
    }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['assetManagement'] })
    await queryClient.invalidateQueries({ queryKey: ['candidateAssets'] })
    await queryClient.invalidateQueries({ queryKey: ['candidateDetail'] })
  }
})

const rows = computed(() => assetsQuery.data.value?.records ?? [])
const assetSummary = computed<AssetSummary>(() => assetsQuery.data.value?.summary ?? {
  total_count: 0,
  total_file_size: 0,
  unique_file_count: 0,
  unique_file_size: 0,
  shared_saved_bytes: 0,
  pending_submit_count: 0,
  submitted_cleanable_count: 0,
  unused_count: 0,
  active_count: 0
})
const filteredRows = computed(() => {
  if (statusFilter.value === 'all') {
    return rows.value
  }
  return rows.value.filter((row) => row.usage_status === statusFilter.value)
})

const summary = computed(() => {
  const total = rows.value.length
  const pendingSubmit = rows.value.filter((row) => row.usage_status === 'pending_submit').length
  const submittedCleanable = rows.value.filter((row) => row.usage_status === 'submitted_cleanable').length
  const unused = rows.value.filter((row) => row.usage_status === 'unused').length
  return { total, pendingSubmit, submittedCleanable, unused }
})

watch(statusFilter, () => {
  selectedAssetIds.value = []
})

const columns = [
  { type: 'selection' },
  { title: '资源ID', key: 'id', width: 90 },
  {
    title: '预览',
    key: 'preview',
    width: 120,
    render: (row: AssetRow) => h('img', { src: row.preview_url, alt: '预览图', class: 'asset-thumb' })
  },
  {
    title: '状态',
    key: 'usage_status',
    width: 140,
    render: (row: AssetRow) => usageLabelMap[row.usage_status] ?? row.usage_status
  },
  {
    title: '判定依据',
    key: 'usage_reason',
    width: 220,
    render: (row: AssetRow) => {
      const text = normalizeCellText(row.usage_reason)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '候选状态',
    key: 'task_item_state',
    width: 120,
    render: (row: AssetRow) => normalizeCellText(row.task_item_state)
  },
  {
    title: '标题',
    key: 'title',
    width: 260,
    render: (row: AssetRow) => {
      const text = normalizeCellText(row.title)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '来源链接',
    key: 'source_url',
    width: 360,
    render: (row: AssetRow) => {
      const text = normalizeCellText(row.source_url)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '角色',
    key: 'asset_role',
    width: 90,
    render: (row: AssetRow) => (row.asset_role === 'cover' ? '封面' : '内容')
  },
  {
    title: '大小(KB)',
    key: 'file_size',
    width: 110,
    render: (row: AssetRow) => (row.file_size / 1024).toFixed(1)
  },
  {
    title: '创建时间',
    key: 'created_at',
    width: 180,
    render: (row: AssetRow) => formatDateTime(row.created_at)
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    fixed: 'right',
    render: (row: AssetRow) =>
      h(
        NButton,
        {
          type: 'error',
          text: true,
          onClick: () => deleteByIds([row.id])
        },
        { default: () => '删除' }
      )
  }
]

const onChecked = (keys: Array<string | number>) => {
  selectedAssetIds.value = keys.map((k) => Number(k))
}

const refresh = async () => {
  await queryClient.invalidateQueries({ queryKey: ['assetManagement'] })
}

const deleteByIds = async (assetIds: number[]) => {
  if (assetIds.length === 0) {
    message.warning('没有可删除的资源')
    return
  }
  if (!window.confirm(`确认删除 ${assetIds.length} 条媒体资源？删除后不可恢复。`)) {
    return
  }
  await deleteMutation.mutateAsync(assetIds)
  selectedAssetIds.value = []
  message.success(`已删除 ${assetIds.length} 条媒体资源`)
}

const cleanSubmitted = async () => {
  const ids = rows.value.filter((row) => row.usage_status === 'submitted_cleanable').map((row) => row.id)
  await deleteByIds(ids)
}

const cleanUnused = async () => {
  const ids = rows.value.filter((row) => row.usage_status === 'unused').map((row) => row.id)
  await deleteByIds(ids)
}

const deleteSelected = async () => {
  await deleteByIds(selectedAssetIds.value)
}

const formatSize = (bytes: number) => {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / 1024 / 1024).toFixed(1)} MB`
  return `${(bytes / 1024 / 1024 / 1024).toFixed(2)} GB`
}
</script>

<style scoped>
.page {
  max-width: 1480px;
  margin: 0 auto;
}

:deep(.ellipsis-cell) {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

:deep(.asset-thumb) {
  width: 72px;
  height: 48px;
  object-fit: cover;
  border-radius: 4px;
  border: 1px solid #444;
}

.stat-label {
  font-size: 12px;
  color: #888;
  margin-bottom: 6px;
}

.stat-value {
  font-size: 22px;
  font-weight: 600;
  line-height: 1.2;
}

.stat-sub {
  margin-top: 6px;
  font-size: 12px;
  color: #999;
}
</style>
