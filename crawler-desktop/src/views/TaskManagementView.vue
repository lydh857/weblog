<template>
  <div class="page">
    <n-card title="快速采集">
      <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
        单链接：输入一篇文章直采。站点批量：选择站点规则 + 列表页链接，系统会自动提取多篇文章链接入队。
      </n-alert>
      <n-space vertical>
        <n-space>
          <n-button :type="taskMode === 'single' ? 'primary' : 'default'" @click="taskMode = 'single'">单链接</n-button>
          <n-button :type="taskMode === 'batch' ? 'primary' : 'default'" @click="taskMode = 'batch'">站点批量</n-button>
        </n-space>

        <n-input
          v-if="taskMode === 'single'"
          v-model:value="quickUrl"
          placeholder="请输入文章链接，如：https://example.com/article"
        />

        <template v-else>
          <n-select
            v-model:value="selectedSiteProfileId"
            :options="siteProfileOptions"
            placeholder="请选择站点规则"
          />
          <n-input
            v-model:value="listUrl"
            placeholder="可留空：优先用规则默认列表页；若未配置则自动使用站点首页"
          />
          <n-input-number v-model:value="batchMaxItems" :min="1" :max="200" placeholder="最大抓取条数" />
        </template>

        <n-button type="primary" @click="createTask">创建任务</n-button>
      </n-space>
    </n-card>

    <n-card title="任务列表" style="margin-top: 16px">
      <n-space style="margin-bottom: 12px">
        <n-button @click="refreshTasks">刷新任务</n-button>
        <n-button type="warning" @click="clearFinishedTasks">清理完成/失败任务</n-button>
        <n-button type="error" @click="clearAllTasks">清空全部任务数据</n-button>
      </n-space>
      <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
        批量任务暂停后，会尽快在当前条目处理边界生效；若某条已进入网络请求或抽取流程，可能在完成当前步骤后停止继续处理。
      </n-alert>
      <n-alert v-if="listQuery.status.value === 'error' && !listQuery.isFetching.value" type="error" :show-icon="false" style="margin-bottom: 12px">
        任务列表加载失败，请确认 crawler-worker 正在运行。
      </n-alert>
      <div v-if="listQuery.isLoading.value" style="margin-bottom: 12px">
        <n-spin size="small" />
      </div>
      <n-empty v-else-if="tasks.length === 0" description="暂无任务，先创建一个采集任务" style="margin-bottom: 12px" />
      <template v-else>
        <n-data-table :columns="columns" :data="tasks" :pagination="false" />
        <n-pagination
          style="margin-top: 12px; justify-content: flex-end"
          :page="taskPageNum"
          :page-size="taskPageSize"
          :item-count="taskTotal"
          show-size-picker
          :page-sizes="[10, 20, 50]"
          @update:page="taskPageNum = $event"
          @update:page-size="handleTaskPageSizeChange"
        />
      </template>
    </n-card>

    <n-drawer v-model:show="taskDetailVisible" :width="720" placement="right">
      <n-drawer-content title="任务详情" closable>
        <template v-if="selectedTask">
          <n-grid :cols="2" :x-gap="12" style="margin-bottom: 16px">
            <n-gi>
              <n-card size="small" embedded>
                <div class="stat-label">任务状态</div>
                <div class="stat-value">{{ getDisplayStatus(selectedTask) }}</div>
                <div class="stat-sub">进度 {{ selectedTask.completed_items }}/{{ selectedTask.total_items || 0 }}</div>
              </n-card>
            </n-gi>
            <n-gi>
              <n-card size="small" embedded>
                <div class="stat-label">增量结果</div>
                <div class="stat-value">{{ selectedTask.discovered_new_count }}</div>
                <div class="stat-sub">跳过旧文 {{ selectedTask.skipped_known_count }} / 扫描 {{ selectedTask.scanned_link_count }}</div>
              </n-card>
            </n-gi>
          </n-grid>

          <n-alert v-if="selectedTask.last_error_summary" type="warning" :show-icon="false" style="margin-bottom: 16px">
            最近错误：{{ selectedTask.last_error_summary }}
          </n-alert>

          <n-descriptions bordered :column="1" size="small" style="margin-bottom: 16px">
            <n-descriptions-item label="任务 ID">{{ selectedTask.id }}</n-descriptions-item>
            <n-descriptions-item label="模式">{{ modeLabelMap[selectedTask.mode] ?? selectedTask.mode }}</n-descriptions-item>
            <n-descriptions-item label="最大条数">{{ selectedTask.max_items }}</n-descriptions-item>
            <n-descriptions-item label="创建时间">{{ formatDateTime(selectedTask.created_at) }}</n-descriptions-item>
            <n-descriptions-item label="最近活跃">{{ selectedTask.last_active_at ? formatDateTime(selectedTask.last_active_at) : '-' }}</n-descriptions-item>
          </n-descriptions>

          <n-card title="最近候选" size="small">
            <n-empty v-if="taskCandidateRows.length === 0" description="暂无候选数据" />
            <n-data-table
              v-else
              :columns="taskCandidateColumns"
              :data="taskCandidateRows"
              :pagination="false"
              :max-height="360"
            />
          </n-card>
        </template>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<script setup lang="ts">
import { computed, h, ref, watch } from 'vue'
import { useQuery, useMutation, useQueryClient } from '@tanstack/vue-query'
import { NButton, useMessage } from 'naive-ui'
import { workerApi } from '../api/http'
import { formatDateTime } from '../utils/format'

interface TaskRow {
  id: number
  mode: string
  status: string
  current_stage?: string | null
  last_error_summary?: string | null
  max_items: number
  discovered_new_count: number
  skipped_known_count: number
  scanned_link_count: number
  created_at: string
  last_active_at?: string | null
  total_items: number
  completed_items: number
}

interface TaskPageResponse {
  records: TaskRow[]
  total: number
  page_num: number
  page_size: number
}

interface SiteProfileOptionRow {
  id: number
  name: string
  domain: string
  defaultListUrl?: string | null
}

interface TaskCandidateRow {
  id: number
  title: string | null
  state: string
  updated_at: string
  fail_reason: string | null
  last_push_message: string | null
}

interface TaskCandidatePageResponse {
  records: TaskCandidateRow[]
  total: number
  page_num: number
  page_size: number
}

const quickUrl = ref('')
const listUrl = ref('')
const batchMaxItems = ref(20)
const taskMode = ref<'single' | 'batch'>('single')
const selectedSiteProfileId = ref<number | null>(null)
const queryClient = useQueryClient()
const message = useMessage()
const taskPageNum = ref(1)
const taskPageSize = ref(10)
const taskDetailVisible = ref(false)
const selectedTaskId = ref<number | null>(null)

const listQuery = useQuery({
  queryKey: ['tasks', taskPageNum, taskPageSize],
  queryFn: async () => (await workerApi.get<TaskPageResponse>('/tasks', {
    params: {
      page_num: taskPageNum.value,
      page_size: taskPageSize.value
    }
  })).data,
  refetchInterval: 2000
})

const createMutation = useMutation({
  mutationFn: async (payload: Record<string, unknown>) => workerApi.post('/tasks', payload),
  onSuccess: async () => {
    quickUrl.value = ''
    listUrl.value = ''
    taskPageNum.value = 1
    await queryClient.invalidateQueries({ queryKey: ['tasks'] })
  }
})

const purgeMutation = useMutation({
  mutationFn: async (mode: 'all' | 'finished') => workerApi.post('/tasks/purge', { mode }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['tasks'] })
    await queryClient.invalidateQueries({ queryKey: ['candidatePool'] })
  }
})

const controlMutation = useMutation({
  mutationFn: async ({ action, taskIds }: { action: 'pause' | 'resume'; taskIds: number[] }) =>
    workerApi.post('/tasks/control', { action, task_ids: taskIds }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['tasks'] })
  }
})

const siteProfileQuery = useQuery({
  queryKey: ['siteProfilesForTask'],
  queryFn: async () => (await workerApi.get<SiteProfileOptionRow[]>('/site-profiles')).data
})

const taskCandidateQuery = useQuery({
  queryKey: ['taskCandidatesPreview', selectedTaskId],
  queryFn: async () => {
    if (!selectedTaskId.value) {
      return { records: [], total: 0, page_num: 1, page_size: 10 } as TaskCandidatePageResponse
    }
    return (await workerApi.get<TaskCandidatePageResponse>('/tasks/items', {
      params: {
        task_id: selectedTaskId.value,
        page_num: 1,
        page_size: 10
      }
    })).data
  },
  enabled: computed(() => taskDetailVisible.value && selectedTaskId.value !== null)
})

const siteProfileOptions = computed(() =>
  (siteProfileQuery.data.value ?? []).map((item) => ({
    label: `${item.name} (${item.domain})`,
    value: item.id
  }))
)

const selectedSiteProfile = computed(() =>
  (siteProfileQuery.data.value ?? []).find((item) => item.id === selectedSiteProfileId.value) ?? null
)

watch(
  () => selectedSiteProfileId.value,
  () => {
    if (taskMode.value !== 'batch') {
      return
    }
    if (listUrl.value.trim()) {
      return
    }
    const defaultUrl = selectedSiteProfile.value?.defaultListUrl?.trim() || ''
    if (defaultUrl) {
      listUrl.value = defaultUrl
    }
  }
)

watch(
  () => taskMode.value,
  (mode) => {
    if (mode !== 'batch') {
      return
    }
    if (listUrl.value.trim()) {
      return
    }
    const defaultUrl = selectedSiteProfile.value?.defaultListUrl?.trim() || ''
    if (defaultUrl) {
      listUrl.value = defaultUrl
    }
  }
)

const tasks = computed(() => listQuery.data.value?.records ?? [])
const taskTotal = computed(() => listQuery.data.value?.total ?? 0)
const selectedTask = computed(() => tasks.value.find((row) => row.id === selectedTaskId.value) ?? null)
const taskCandidateRows = computed(() => taskCandidateQuery.data.value?.records ?? [])
const modeLabelMap: Record<string, string> = {
  single: '单链接',
  batch: '批量'
}

const statusLabelMap: Record<string, string> = {
  queued: '待执行',
  expanding: '提链中',
  running: '执行中',
  crawling: '执行中',
  fetching: '抓取页面中',
  extracting: '正文抽取中',
  staging_assets: '图片处理中',
  pushing: '推送草稿中',
  review_pending: '待审核',
  approved: '已通过待推送',
  policy_blocked: 'URL策略拦截',
  done: '已完成',
  completed: '已完成',
  paused: '已暂停',
  failed: '失败',
  pushed: '已推送',
  rejected: '已拒绝'
}

const getDisplayStatus = (task: TaskRow) => {
  return statusLabelMap[task.current_stage || task.status] ?? task.current_stage ?? task.status
}

const getProgressText = (taskId: number) => {
  const task = tasks.value.find((row) => row.id === taskId)
  if (!task || task.total_items === 0) {
    return '-'
  }
  return `${task.completed_items}/${task.total_items}`
}

const getIncrementalSummary = (task: TaskRow) => {
  if (task.mode !== 'batch') {
    return '-'
  }
  const scanned = task.scanned_link_count || 0
  const added = task.discovered_new_count || 0
  const skipped = task.skipped_known_count || 0
  return `新增 ${added} / 跳过旧文 ${skipped} / 扫描 ${scanned}`
}

const columns = [
  { title: '任务ID', key: 'id', width: 90 },
  {
    title: '模式',
    key: 'mode',
    width: 100,
    render: (row: TaskRow) => {
      const text = modeLabelMap[row.mode] ?? row.mode
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '状态',
    key: 'status',
    width: 120,
    render: (row: TaskRow) => {
      const text = getDisplayStatus(row)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '最近错误摘要',
    key: 'last_error_summary',
    width: 320,
    render: (row: TaskRow) => {
      const text = row.last_error_summary || '-'
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '进度',
    key: 'progress',
    width: 120,
    render: (row: TaskRow) => {
      const text = getProgressText(row.id)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '增量结果',
    key: 'incremental',
    width: 240,
    render: (row: TaskRow) => {
      const text = getIncrementalSummary(row)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  { title: '最大条数', key: 'max_items', width: 120 },
  {
    title: '创建时间',
    key: 'created_at',
    width: 180,
    render: (row: TaskRow) => {
      const text = formatDateTime(row.created_at)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '最近活跃',
    key: 'last_active_at',
    width: 180,
    render: (row: TaskRow) => {
      const text = row.last_active_at ? formatDateTime(row.last_active_at) : '-'
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '操作',
    key: 'action',
    width: 180,
    fixed: 'right',
    render: (row: TaskRow) => {
      const canPause = row.mode === 'batch' && !['paused', 'done', 'failed', 'completed'].includes(row.status)
      const canResume = row.mode === 'batch' && row.status === 'paused'
      return h('div', { class: 'task-actions' }, [
        h(
          NButton,
          {
            type: 'primary',
            text: true,
            onClick: () => openTaskDetail(row.id)
          },
          { default: () => '详情' }
        ),
        canPause || canResume
          ? h(
            NButton,
            {
              type: canPause ? 'warning' : 'primary',
              text: true,
              onClick: () => handleTaskControl(canPause ? 'pause' : 'resume', row.id)
            },
            { default: () => canPause ? '暂停' : '继续' }
          )
          : null,
      ])
    }
  }
]

const taskCandidateColumns = [
  { title: 'ID', key: 'id', width: 80 },
  {
    title: '标题',
    key: 'title',
    width: 220,
    render: (row: TaskCandidateRow) => {
      const text = row.title || `候选内容 #${row.id}`
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '状态',
    key: 'state',
    width: 120,
    render: (row: TaskCandidateRow) => statusLabelMap[row.state] ?? row.state
  },
  {
    title: '原因',
    key: 'reason',
    width: 220,
    render: (row: TaskCandidateRow) => {
      const text = row.fail_reason || row.last_push_message || '-'
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '更新时间',
    key: 'updated_at',
    width: 180,
    render: (row: TaskCandidateRow) => formatDateTime(row.updated_at)
  }
]

const createTask = async () => {
  if (taskMode.value === 'single') {
    if (!quickUrl.value) {
      message.warning('请先输入文章链接')
      return
    }
    await createMutation.mutateAsync({ mode: 'single', source_url: quickUrl.value, max_items: 1 })
    message.success('单链接任务创建成功')
    return
  }

  if (!selectedSiteProfileId.value) {
    message.warning('请先选择站点规则')
    return
  }
  const resolvedListUrl = listUrl.value.trim() || selectedSiteProfile.value?.defaultListUrl?.trim() || ''
  const fallbackHomepage = selectedSiteProfile.value?.domain?.trim() ? `https://${selectedSiteProfile.value.domain.trim()}/` : ''
  const finalListUrl = resolvedListUrl || fallbackHomepage
  if (!finalListUrl) {
    message.warning('请先输入列表页链接，或在站点规则中配置默认列表页链接')
    return
  }

  await createMutation.mutateAsync({
    mode: 'batch',
    source_url: finalListUrl,
    site_profile_id: selectedSiteProfileId.value,
    max_items: batchMaxItems.value || 20
  })
  message.success('批量任务创建成功')
}

const refreshTasks = async () => {
  await queryClient.invalidateQueries({ queryKey: ['tasks'] })
}

const handleTaskPageSizeChange = (size: number) => {
  taskPageSize.value = size
  taskPageNum.value = 1
}

const handleTaskControl = async (action: 'pause' | 'resume', taskId: number) => {
  await controlMutation.mutateAsync({ action, taskIds: [taskId] })
  message.success(action === 'pause' ? '任务已暂停' : '任务已继续')
}

const openTaskDetail = (taskId: number) => {
  selectedTaskId.value = taskId
  taskDetailVisible.value = true
}

const clearFinishedTasks = async () => {
  if (!window.confirm('仅清理已完成/失败任务及其候选数据，是否继续？')) {
    return
  }
  await purgeMutation.mutateAsync('finished')
  message.success('已清理完成/失败任务')
}

const clearAllTasks = async () => {
  if (!window.confirm('将删除全部任务、候选、图片缓存索引，是否继续？')) {
    return
  }
  await purgeMutation.mutateAsync('all')
  message.success('已清空任务数据')
}
</script>

<style scoped>
.page {
  max-width: 1280px;
  margin: 0 auto;
}

:deep(.ellipsis-cell) {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.task-actions {
  display: flex;
  gap: 8px;
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
