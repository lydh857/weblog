<template>
  <div class="page">
    <n-card title="候选池">
      <n-space>
        <n-select v-model:value="stateFilter" :options="stateFilterOptions" style="width: 180px" />
        <n-input
          v-model:value="failReasonKeyword"
          clearable
          placeholder="按失败原因关键词筛选"
          style="width: 240px"
        />
        <n-button @click="refresh">刷新</n-button>
        <n-button-group>
          <n-button size="small" @click="applyQuickFilter('all')">全部</n-button>
          <n-button size="small" @click="applyQuickFilter('pushable')">可推送</n-button>
          <n-button size="small" @click="applyQuickFilter('duplicate')">重复拒绝</n-button>
          <n-button size="small" @click="applyQuickFilter('policy')">策略拦截</n-button>
          <n-button size="small" @click="applyQuickFilter('running')">执行中</n-button>
          <n-button size="small" @click="applyQuickFilter('failed')">失败/拒绝</n-button>
        </n-button-group>
        <n-button type="primary" @click="batchApprove">批量通过</n-button>
        <n-button type="warning" @click="batchReject">批量拒绝</n-button>
        <n-button type="info" @click="batchRecrawl">批量重采</n-button>
        <n-button type="error" @click="batchDelete">批量删除</n-button>
        <n-button type="success" @click="pushApproved">推送已通过</n-button>
        <n-button @click="clearPushResults">清空推送结果</n-button>
      </n-space>
      <n-alert v-if="listQuery.status.value === 'error' && !listQuery.isFetching.value" style="margin-top: 16px" type="error" :show-icon="false">
        候选列表加载失败，请确认 crawler-worker 正在运行（127.0.0.1:17891）。
      </n-alert>
      <div v-if="listQuery.isLoading.value" style="margin-top: 16px">
        <n-spin size="small" />
      </div>
      <n-empty v-else-if="rows.length === 0" style="margin-top: 16px" description="当前筛选条件下暂无候选内容" />
      <template v-else>
        <n-data-table
          style="margin-top: 16px"
          :columns="columns"
          :data="rows"
          :scroll-x="1820"
          :row-key="(row: CandidateRow) => row.id"
          @update:checked-row-keys="onChecked"
        />
        <n-pagination
          style="margin-top: 12px; justify-content: flex-end"
          :page="pageNum"
          :page-size="pageSize"
          :item-count="total"
          show-size-picker
          :page-sizes="[20, 50, 100]"
          @update:page="pageNum = $event"
          @update:page-size="handlePageSizeChange"
        />
      </template>
      <n-data-table
        v-if="pushRows.length > 0"
        style="margin-top: 16px"
        :columns="pushColumns"
        :data="pushRows"
        :pagination="false"
      />

      <n-modal v-model:show="duplicateModalVisible" preset="card" title="发现重复内容" style="width: 560px">
        <n-space vertical>
          <n-alert type="warning" :show-icon="false">
            本次推送有 {{ duplicateConflictCount }} 条候选命中重复内容。请选择如何处理这些重复项。
          </n-alert>
          <n-radio-group v-model:value="duplicateActionMode">
            <n-space vertical>
              <n-radio value="update_existing_draft">更新已有草稿</n-radio>
              <n-radio value="create_new_draft">新建副本草稿</n-radio>
            </n-space>
          </n-radio-group>
          <n-space justify="end">
            <n-button @click="cancelDuplicateResolution">取消</n-button>
            <n-button type="primary" @click="confirmDuplicateResolution">确定</n-button>
          </n-space>
        </n-space>
      </n-modal>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { h } from 'vue'
import { NAlert, NButton, NButtonGroup, NModal, NRadio, NRadioGroup, useMessage } from 'naive-ui'
import { useRouter } from 'vue-router'
import { workerApi } from '../api/http'
import { useCrawlerStore, type CandidateRow, type PushResultRow } from '../stores/crawler'
import { formatDateTime, normalizeCellText } from '../utils/format'

interface CandidateApiRow {
  id: number
  task_id: number
  title: string | null
  summary: string | null
  external_url: string
  source_site: string | null
  state: string
  draft_push_status: string | null
  backend_candidate_id: number | null
  last_push_message: string | null
  fail_reason: string | null
  updated_at: string
  created_at: string
}

interface CandidatePageResponse {
  records: CandidateApiRow[]
  total: number
  page_num: number
  page_size: number
}

const store = useCrawlerStore()
const queryClient = useQueryClient()
const message = useMessage()
const router = useRouter()
const stateFilter = ref('all')
const failReasonKeyword = ref('')
const pageNum = ref(1)
const pageSize = ref(20)
const duplicateModalVisible = ref(false)
const duplicateActionMode = ref<'update_existing_draft' | 'create_new_draft'>('update_existing_draft')
const duplicateConflictCount = ref(0)
let duplicateModalResolver: ((mode: 'update_existing_draft' | 'create_new_draft' | null) => void) | null = null

const stateLabelMap: Record<string, string> = {
  review_pending: '待审核',
  approved: '已通过',
  rejected: '已拒绝',
  queued: '待执行',
  crawling: '执行中',
  fetching: '抓取页面中',
  extracting: '正文抽取中',
  staging_assets: '图片处理中',
  pushing: '推送草稿中',
  pushed: '已推送',
  failed: '失败',
  policy_blocked: 'URL策略拦截'
}

const stateFilterOptions = [
  { label: '全部状态', value: 'all' },
  { label: '待审核', value: 'review_pending' },
  { label: '已通过', value: 'approved' },
  { label: '已拒绝', value: 'rejected' },
  { label: '待执行', value: 'queued' },
  { label: '执行中', value: 'running' },
  { label: '已推送', value: 'pushed' },
  { label: '失败', value: 'failed' }
]

type QuickFilterKey = 'all' | 'pushable' | 'duplicate' | 'policy' | 'running' | 'failed'

const listQuery = useQuery({
  queryKey: ['candidatePool', stateFilter, failReasonKeyword, pageNum, pageSize],
  queryFn: async () => {
    const response = (await workerApi.get<CandidatePageResponse>('/tasks/items', {
      params: {
        state: stateFilter.value === 'all' ? undefined : stateFilter.value,
        keyword: failReasonKeyword.value.trim() || undefined,
        page_num: pageNum.value,
        page_size: pageSize.value
      }
    })).data
    return {
      ...response,
      records: response.records.map((row) => ({
        id: row.id,
        task_id: row.task_id,
        title: row.title,
        summary: row.summary,
        state: row.state,
        sourceUrl: row.external_url,
        source_site: row.source_site,
        draft_push_status: row.draft_push_status,
        backend_candidate_id: row.backend_candidate_id,
        last_push_message: row.last_push_message,
        fail_reason: row.fail_reason,
        updated_at: row.updated_at,
        created_at: row.created_at
      }))
    }
  },
  refetchInterval: 5000
})

const reviewMutation = useMutation({
  mutationFn: async ({ action, itemIds }: { action: 'approve' | 'reject' | 'recrawl'; itemIds: number[] }) =>
    workerApi.post('/tasks/review-action', { action, item_ids: itemIds }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['candidatePool'] })
  }
})

const pushMutation = useMutation({
  mutationFn: async ({ itemIds, pushMode }: { itemIds: number[]; pushMode?: 'skip' | 'update_existing_draft' | 'create_new_draft' }) =>
    workerApi.post('/tasks/push-approved', { item_ids: itemIds, push_mode: pushMode || 'skip' })
})

const rows = computed(() => listQuery.data.value?.records ?? [])
const total = computed(() => listQuery.data.value?.total ?? 0)
const pushRows = computed<PushResultRow[]>(() => {
  const resultRows = store.pushResults
  const rowMap = new Map(rows.value.map((row) => [row.id, row]))
  return resultRows
    .map((result) => {
      const candidate = rowMap.get(result.item_id)
      const message = candidate?.last_push_message?.trim() || result.message
      return {
        ...result,
        title: candidate?.title || `候选内容 #${result.item_id}`,
        message,
        pushed_at: result.pushed_at || candidate?.updated_at || ''
      }
    })
    .sort((a, b) => new Date(b.pushed_at || 0).getTime() - new Date(a.pushed_at || 0).getTime())
})
const approvedRows = computed(() => rows.value.filter((row) => row.state === 'approved'))

watch([stateFilter, failReasonKeyword], () => {
  store.setSelectedCandidateIds([])
  pageNum.value = 1
})

const columns = [
  { type: 'selection' },
  { title: 'ID', key: 'id', width: 80 },
  {
    title: '标题',
    key: 'title',
    width: 240,
    render: (row: CandidateRow) => {
      const text = row.title || `候选内容 #${row.id}`
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '状态',
    key: 'state',
    width: 100,
    render: (row: CandidateRow) => stateLabelMap[row.state] ?? row.state
  },
  {
    title: '来源站点',
    key: 'source_site',
    width: 180,
    render: (row: CandidateRow) => {
      const text = normalizeCellText(row.source_site)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '来源链接',
    key: 'sourceUrl',
    width: 300,
    render: (row: CandidateRow) => {
      const text = normalizeCellText(row.sourceUrl)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '拒绝/失败原因',
    key: 'fail_reason',
    width: 340,
    render: (row: CandidateRow) => {
      const text = row.state === 'rejected' || row.state === 'failed'
        ? normalizeCellText(row.fail_reason || row.last_push_message)
        : '-'
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '更新时间',
    key: 'updated_at',
    width: 180,
    render: (row: CandidateRow) => formatDateTime(row.updated_at)
  },
  {
    title: '操作',
    key: 'action',
    width: 100,
    fixed: 'right',
    render: (row: CandidateRow) =>
      h(
        NButton,
        {
          type: 'primary',
          text: true,
          onClick: () => router.push(`/candidates/${row.id}`)
        },
        { default: () => '查看详情' }
      )
  }
]

const pushColumns = [
  { title: '候选ID', key: 'item_id' },
  {
    title: '标题',
    key: 'title',
    width: 260,
    render: (row: PushResultRow) => {
      const text = normalizeCellText(row.title)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  {
    title: '结果',
    key: 'status',
    render: (row: PushResultRow) => {
      const statusLabelMap: Record<string, string> = {
        succeeded: '成功',
        failed: '失败'
      }
      return statusLabelMap[row.status] ?? row.status
    }
  },
  {
    title: '推送时间',
    key: 'pushed_at',
    width: 180,
    render: (row: PushResultRow) => (row.pushed_at ? formatDateTime(row.pushed_at) : '-')
  },
  {
    title: '信息',
    key: 'message',
    width: 360,
    render: (row: PushResultRow) => {
      const text = normalizeCellText(row.message)
      return h('span', { class: 'ellipsis-cell', title: text }, text)
    }
  },
  { title: '后端候选ID', key: 'backend_candidate_id' },
  { title: '草稿ID', key: 'draft_id' }
]

const deleteMutation = useMutation({
  mutationFn: async (itemIds: number[]) =>
    workerApi.delete('/tasks/items', {
      data: { item_ids: itemIds }
    }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['candidatePool'] })
  }
})

const onChecked = (keys: Array<string | number>) => {
  store.setSelectedCandidateIds(keys.map((k) => Number(k)))
}

const batchApprove = async () => {
  if (store.selectedCandidateIds.length === 0) {
    message.warning('请先勾选候选项')
    return
  }
  await reviewMutation.mutateAsync({ action: 'approve', itemIds: store.selectedCandidateIds })
  message.success('已批量通过')
}

const batchReject = async () => {
  if (store.selectedCandidateIds.length === 0) {
    message.warning('请先勾选候选项')
    return
  }
  await reviewMutation.mutateAsync({ action: 'reject', itemIds: store.selectedCandidateIds })
  message.success('已批量拒绝')
}

const refresh = async () => {
  await queryClient.invalidateQueries({ queryKey: ['candidatePool'] })
}

const applyQuickFilter = (key: QuickFilterKey) => {
  if (key === 'all') {
    stateFilter.value = 'all'
    failReasonKeyword.value = ''
    return
  }
  if (key === 'pushable') {
    stateFilter.value = 'approved'
    failReasonKeyword.value = ''
    return
  }
  if (key === 'duplicate') {
    stateFilter.value = 'failed'
    failReasonKeyword.value = '重复内容'
    return
  }
  if (key === 'policy') {
    stateFilter.value = 'all'
    failReasonKeyword.value = 'private/loopback/link-local'
    return
  }
  if (key === 'running') {
    stateFilter.value = 'running'
    failReasonKeyword.value = ''
    return
  }
  stateFilter.value = 'all'
  failReasonKeyword.value = '失败'
}

const batchRecrawl = async () => {
  if (store.selectedCandidateIds.length === 0) {
    message.warning('请先勾选候选项')
    return
  }
  await reviewMutation.mutateAsync({ action: 'recrawl', itemIds: store.selectedCandidateIds })
  message.success('已加入重新采集队列')
}

const handlePageSizeChange = (size: number) => {
  pageSize.value = size
  pageNum.value = 1
}

const openDuplicateResolutionModal = async (count: number) => {
  duplicateConflictCount.value = count
  duplicateActionMode.value = 'update_existing_draft'
  duplicateModalVisible.value = true
  return await new Promise<'update_existing_draft' | 'create_new_draft' | null>((resolve) => {
    duplicateModalResolver = resolve
  })
}

const confirmDuplicateResolution = () => {
  duplicateModalVisible.value = false
  duplicateModalResolver?.(duplicateActionMode.value)
  duplicateModalResolver = null
}

const cancelDuplicateResolution = () => {
  duplicateModalVisible.value = false
  duplicateModalResolver?.(null)
  duplicateModalResolver = null
}

const pushApproved = async () => {
  const selectedApproved = rows.value
    .filter((row) => store.selectedCandidateIds.includes(row.id) && row.state === 'approved')
    .map((row) => row.id)
  const itemIds = store.selectedCandidateIds.length > 0 ? selectedApproved : approvedRows.value.map((row) => row.id)
  if (itemIds.length === 0) {
    message.warning('没有可推送的已通过候选项')
    return
  }

  const scopeLabel =
    store.selectedCandidateIds.length > 0
      ? `当前勾选中可推送 ${itemIds.length} 条（仅统计已通过）`
      : `当前全部可推送 ${itemIds.length} 条已通过候选项`
  if (!window.confirm(`确认推送？\n${scopeLabel}`)) {
    return
  }

  const firstResponse = await pushMutation.mutateAsync({ itemIds, pushMode: 'skip' })
  let finalResults = [...(firstResponse.data.results ?? [])]
  const duplicateFailures = finalResults.filter(result => result.duplicate_candidate_id)
  if (duplicateFailures.length > 0) {
    const mode = await openDuplicateResolutionModal(duplicateFailures.length)
    if (mode) {
      const retryResponse = await pushMutation.mutateAsync({
        itemIds: duplicateFailures.map(item => item.item_id),
        pushMode: mode
      })
      const retryMap = new Map((retryResponse.data.results ?? []).map(item => [item.item_id, item]))
      finalResults = finalResults.map(item => retryMap.get(item.item_id) ?? item)
    }
  }
  await queryClient.invalidateQueries({ queryKey: ['candidatePool'] })
  store.setPushResults(
    finalResults.map((result) => {
      const candidate = rows.value.find((row) => row.id === result.item_id)
      return {
        item_id: result.item_id,
        status: result.status,
        message: candidate?.last_push_message?.trim() || candidate?.fail_reason?.trim() || result.message,
        backend_candidate_id: result.backend_candidate_id ?? null,
        draft_id: result.draft_id ?? null,
        title: candidate?.title || `候选内容 #${result.item_id}`,
        pushed_at: result.pushed_at || candidate?.updated_at || ''
      }
    })
  )
  message.success(`已提交推送：${itemIds.length} 条`)
}

const batchDelete = async () => {
  if (store.selectedCandidateIds.length === 0) {
    message.warning('请先勾选候选项')
    return
  }
  await deleteMutation.mutateAsync(store.selectedCandidateIds)
  store.setSelectedCandidateIds([])
  message.success('删除成功')
}

const clearPushResults = () => {
  store.clearPushResults()
}
</script>

<style scoped>
.page {
  max-width: 1320px;
  margin: 0 auto;
}

:deep(.ellipsis-cell) {
  display: inline-block;
  width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
