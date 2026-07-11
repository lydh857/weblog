<template>
  <div class="page">
    <n-card title="候选详情预览">
      <n-alert v-if="detailQuery.isError.value" type="error" :show-icon="false" style="margin-bottom: 12px">
        候选详情加载失败，请返回候选池重试。
      </n-alert>
      <div v-if="detailQuery.isLoading.value" style="margin-bottom: 12px">
        <n-spin size="small" />
      </div>
      <n-space style="margin-bottom: 12px">
        <n-button @click="goBackToList">返回候选池</n-button>
      </n-space>
      <n-form label-placement="top">
        <n-form-item label="标题">
          <n-input v-model:value="title" placeholder="请输入标题" />
        </n-form-item>
        <n-form-item label="摘要">
          <n-input v-model:value="summary" type="textarea" :autosize="{ minRows: 2, maxRows: 4 }" placeholder="请输入摘要" />
        </n-form-item>
        <n-form-item label="正文（Markdown）">
          <n-input v-model:value="body" type="textarea" :autosize="{ minRows: 10, maxRows: 20 }" placeholder="请输入正文 Markdown 内容" />
        </n-form-item>
        <n-form-item label="标签">
          <n-dynamic-tags v-model:value="tags" :max="5" />
        </n-form-item>
        <n-form-item label="采集标签候选（最多选择5个）">
          <n-space>
            <n-button
              v-for="tag in suggestedTags"
              :key="tag"
              size="small"
              :type="tags.includes(tag) ? 'primary' : 'default'"
              @click="toggleSuggestedTag(tag)"
            >
              {{ tag }}
            </n-button>
          </n-space>
        </n-form-item>
        <n-form-item label="一级分类">
          <n-input v-model:value="categoryLevel1" placeholder="例如：技术" />
        </n-form-item>
        <n-form-item label="二级分类">
          <n-input v-model:value="categoryLevel2" placeholder="例如：前端" />
        </n-form-item>
      </n-form>
      <n-alert v-if="lastReason" type="warning" :show-icon="false" style="margin-bottom: 12px">
        失败原因：{{ lastReason }}
      </n-alert>
      <n-card title="采集图片" size="small" style="margin-bottom: 12px">
        <div v-if="assetsQuery.isLoading.value" style="margin-bottom: 8px">
          <n-spin size="small" />
        </div>
        <n-empty v-else-if="assetRows.length === 0" description="当前没有采集到图片" />
        <div v-else class="asset-grid">
          <div v-for="asset in assetRows" :key="asset.id" class="asset-card">
            <img :src="asset.preview_url" class="asset-image" alt="采集图片" />
            <div class="asset-meta">
              <span>{{ asset.asset_role === 'cover' ? '封面图' : '内容图' }}</span>
              <n-space>
                <n-button size="tiny" :type="asset.asset_role === 'cover' ? 'primary' : 'default'" @click="setCoverAsset(asset.id)">
                  设为封面
                </n-button>
                <n-button size="tiny" type="error" @click="removeAsset(asset.id)">移除</n-button>
              </n-space>
            </div>
          </div>
        </div>
      </n-card>
      <n-space>
        <n-button type="primary" :loading="saveMutation.isPending.value" @click="saveDraftVersion">保存修改</n-button>
        <n-button type="success" :loading="reviewMutation.isPending.value" @click="approveItem">通过</n-button>
        <n-button type="error" :loading="reviewMutation.isPending.value" @click="rejectItem">拒绝</n-button>
        <n-button type="warning" :loading="reviewMutation.isPending.value" @click="recrawlItem">重新采集</n-button>
        <n-button type="error" :loading="deleteRemoteCandidateLoading" @click="deleteRemoteCandidate(false)">删后端候选</n-button>
        <n-button type="error" secondary :loading="deleteRemoteCandidateLoading" @click="deleteRemoteCandidate(true)">删候选并删草稿</n-button>
      </n-space>
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useMessage } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { workerApi } from '../api/http'

interface CandidateDetail {
  id: number
  state: string
  title: string | null
  summary: string | null
  content_markdown: string | null
  tags: string[]
  suggested_tags: string[]
  category_level1: string | null
  category_level2: string | null
  cover_asset_id: number | null
  fail_reason: string | null
  last_push_message: string | null
  backend_candidate_id?: number | null
}

interface AssetRow {
  id: number
  source_url: string
  preview_url: string
  mime_type: string | null
  file_size: number
  asset_role: 'cover' | 'content'
}

const route = useRoute()
const router = useRouter()
const message = useMessage()
const queryClient = useQueryClient()

const title = ref('')
const summary = ref('')
const body = ref('')
const tags = ref<string[]>([])
const suggestedTags = ref<string[]>([])
const categoryLevel1 = ref('')
const categoryLevel2 = ref('')
const lastReason = ref('')
const currentState = ref('')
const backendCandidateId = ref<number | null>(null)
const deleteRemoteCandidateLoading = ref(false)

const itemId = Number(route.params.id)

const detailQuery = useQuery({
  queryKey: ['candidateDetail', itemId],
  queryFn: async () => (await workerApi.get<CandidateDetail>(`/tasks/items/${itemId}`)).data,
  enabled: Number.isFinite(itemId)
})

const assetsQuery = useQuery({
  queryKey: ['candidateAssets', itemId],
  queryFn: async () => {
    const rows = (await workerApi.get<AssetRow[]>(`/tasks/items/${itemId}/assets`)).data
    const base = workerApi.defaults.baseURL || ''
    return rows.map((row) => ({
      ...row,
      preview_url: row.preview_url.startsWith('http') ? row.preview_url : `${base}${row.preview_url}`
    }))
  },
  enabled: Number.isFinite(itemId)
})

const assetRows = ref<AssetRow[]>([])

watch(
  () => detailQuery.data.value,
  (detail) => {
    if (!detail) {
      return
    }
    title.value = detail.title ?? ''
    currentState.value = detail.state ?? ''
    summary.value = detail.summary ?? ''
    body.value = detail.content_markdown ?? ''
    suggestedTags.value = detail.suggested_tags ?? []
    const defaultTags = (detail.tags ?? []).slice(0, 5)
    tags.value = defaultTags.length > 0 ? defaultTags : suggestedTags.value.slice(0, 5)
    categoryLevel1.value = detail.category_level1 ?? ''
    categoryLevel2.value = detail.category_level2 ?? ''
    lastReason.value = detail.fail_reason || detail.last_push_message || ''
    backendCandidateId.value = detail.backend_candidate_id ?? null
    if (Array.isArray(assetsQuery.data.value)) {
      assetRows.value = assetsQuery.data.value
    }
  },
  { immediate: true }
)

watch(
  () => assetsQuery.data.value,
  (rows) => {
    assetRows.value = rows ?? []
  },
  { immediate: true }
)

const saveMutation = useMutation({
  mutationFn: async () =>
    workerApi.put(`/tasks/items/${itemId}`, {
      title: title.value,
      summary: summary.value,
      tags: tags.value.slice(0, 5),
      category_level1: categoryLevel1.value,
      category_level2: categoryLevel2.value,
      content_markdown: body.value
    }),
  onSuccess: async () => {
    message.success('保存成功')
    await queryClient.invalidateQueries({ queryKey: ['candidateDetail', itemId] })
    await queryClient.invalidateQueries({ queryKey: ['candidatePool'] })
  }
})

const reviewMutation = useMutation({
  mutationFn: async ({ action }: { action: 'approve' | 'recrawl' | 'reject' }) =>
    workerApi.post('/tasks/review-action', { action, item_ids: [itemId] }),
  onSuccess: async (_, payload) => {
    if (payload.action === 'approve') {
      message.success('已通过该候选项')
    } else if (payload.action === 'reject') {
      message.success('已拒绝该候选项')
    } else {
      message.success('已加入重新采集队列')
    }
    await queryClient.invalidateQueries({ queryKey: ['candidateDetail', itemId] })
    await queryClient.invalidateQueries({ queryKey: ['candidatePool'] })
  }
})

const setCoverMutation = useMutation({
  mutationFn: async (assetId: number) => workerApi.put(`/tasks/items/${itemId}/assets/${assetId}/role`, { role: 'cover' }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['candidateAssets', itemId] })
    message.success('已设置为封面图')
  }
})

const deleteAssetMutation = useMutation({
  mutationFn: async (assetId: number) => workerApi.delete(`/tasks/items/${itemId}/assets/${assetId}`),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['candidateAssets', itemId] })
    await queryClient.invalidateQueries({ queryKey: ['candidateDetail', itemId] })
    message.success('图片已移除，本地缓存已同步删除')
  }
})

const saveDraftVersion = async () => {
  if (!itemId) {
    return
  }
  await saveMutation.mutateAsync()
  await goBackToList()
}

const toggleSuggestedTag = (tag: string) => {
  const idx = tags.value.indexOf(tag)
  if (idx >= 0) {
    tags.value.splice(idx, 1)
    return
  }
  if (tags.value.length >= 5) {
    message.warning('最多只能选择 5 个标签')
    return
  }
  tags.value.push(tag)
}

const approveItem = async () => {
  if (!itemId) {
    return
  }
  if (currentState.value === 'queued' || currentState.value === 'crawling') {
    message.warning('当前仍在采集中，请等待采集完成后再执行通过')
    return
  }
  if (currentState.value === 'approved') {
    message.info('该候选项已是通过状态')
    return
  }
  if (currentState.value === 'pushed') {
    message.info('该候选项已推送，无需重复通过')
    return
  }
  try {
    await saveMutation.mutateAsync()
    await reviewMutation.mutateAsync({ action: 'approve' })
    await goBackToList()
  } catch {
    message.error('通过失败，请稍后重试')
  }
}

const setCoverAsset = async (assetId: number) => {
  await setCoverMutation.mutateAsync(assetId)
}

const removeAsset = async (assetId: number) => {
  await deleteAssetMutation.mutateAsync(assetId)
}

const recrawlItem = async () => {
  if (!itemId) {
    return
  }
  try {
    await reviewMutation.mutateAsync({ action: 'recrawl' })
    await goBackToList()
  } catch {
    message.error('重新采集失败，请稍后重试')
  }
}

const rejectItem = async () => {
  if (!itemId) {
    return
  }
  try {
    await reviewMutation.mutateAsync({ action: 'reject' })
    await goBackToList()
  } catch {
    message.error('拒绝失败，请稍后重试')
  }
}

const goBackToList = async () => {
  await router.push('/candidates')
}

const deleteRemoteCandidate = async (deleteDraft: boolean) => {
  if (!backendCandidateId.value) {
    message.warning('当前候选没有关联后端 candidateId')
    return
  }
  if (!window.confirm(deleteDraft ? '确认删除后端候选并删除关联草稿？' : '确认删除后端候选？')) {
    return
  }
  deleteRemoteCandidateLoading.value = true
  try {
    await workerApi.delete(`/backend/candidates/${backendCandidateId.value}`, { params: { delete_draft: deleteDraft } })
    message.success(deleteDraft ? '已删除后端候选和关联草稿' : '已删除后端候选')
  } finally {
    deleteRemoteCandidateLoading.value = false
  }
}
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.asset-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
}

.asset-card {
  border: 1px solid #3a3a3a;
  border-radius: 8px;
  overflow: hidden;
}

.asset-image {
  width: 100%;
  height: 150px;
  object-fit: cover;
  display: block;
}

.asset-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px;
}
</style>
