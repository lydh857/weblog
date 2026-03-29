<template>
  <div class="comment-page">
    <div class="page-header">
      <h2>评论管理</h2>
      <div class="filter-bar">
        <el-input
          v-model="filterPostTitle"
          placeholder="搜索文章标题"
          clearable
          style="width: 200px"
          @input="debouncedSearch"
          @clear="handleFilterChange"
        />
        <el-select v-model="filterStatus" placeholder="评论状态" clearable style="width: 130px" @change="handleFilterChange">
          <el-option label="待审核" value="pending" />
          <el-option label="已通过" value="approved" />
          <el-option label="已拒绝" value="rejected" />
        </el-select>
        <el-select v-model="filterAiReview" placeholder="AI审核" clearable style="width: 130px" @change="handleFilterChange">
          <el-option label="AI通过" value="pass" />
          <el-option label="AI疑似" value="suspect" />
          <el-option label="AI违规" value="reject" />
          <el-option label="待AI审核" value="pending" />
        </el-select>
        <el-select v-model="filterTop" placeholder="置顶" clearable style="width: 100px" @change="handleFilterChange">
          <el-option label="置顶" value="true" />
          <el-option label="非置顶" value="false" />
        </el-select>
      </div>
      <div class="header-actions">
        <template v-if="selectedIds.length > 0">
          <span class="selection-count">已选 {{ selectedIds.length }} 条</span>
          <el-dropdown trigger="hover" @command="handleBatchCommand">
            <el-button type="primary" size="small">
              批量操作 <el-icon class="el-icon--right"><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="approved">通过</el-dropdown-item>
                <el-dropdown-item command="rejected">拒绝</el-dropdown-item>
                <el-dropdown-item command="aiReview" divided>AI 重新审核</el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe height="560"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="40" align="center" />
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column label="用户" width="150">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="28" :src="row.avatar || undefined">{{ row.nickname?.[0] }}</el-avatar>
            <span class="nickname">{{ row.nickname }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="评论内容" min-width="300">
        <template #default="{ row }">
          <div class="comment-content-cell">
            <span v-if="row.replyToNickname" class="reply-tag">
              回复 <span class="reply-target">@{{ row.replyToNickname }}</span>
            </span>
            <span class="comment-text">{{ row.content }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="文章" min-width="180">
        <template #default="{ row }">
          <span class="post-title-link" :title="row.postTitle">{{ row.postTitle }}</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="AI审核" width="100" align="center">
        <template #default="{ row }">
          <el-tooltip v-if="row.aiReviewReason" :content="row.aiReviewReason" placement="top">
            <el-tag :type="aiReviewType(row.aiReviewStatus)" size="small">{{ aiReviewLabel(row.aiReviewStatus) }}</el-tag>
          </el-tooltip>
          <el-tag v-else :type="aiReviewType(row.aiReviewStatus)" size="small">{{ aiReviewLabel(row.aiReviewStatus) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="点赞" prop="likeCount" width="65" align="center" />
      <el-table-column label="置顶" width="65" align="center">
        <template #default="{ row }">
          <el-tag v-if="row.isTop" type="warning" size="small">置顶</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="155">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" text type="success" size="small" @click="handleAudit(row, 'approved')">通过</el-button>
          <el-button v-if="row.status === 'pending'" text type="warning" size="small" @click="handleAudit(row, 'rejected')">拒绝</el-button>
          <el-button v-if="row.status === 'rejected'" text type="success" size="small" @click="handleAudit(row, 'approved')">通过</el-button>
          <el-button text :type="row.isTop ? 'info' : 'primary'" size="small" @click="handleToggleTop(row)">
            {{ row.isTop ? '取消置顶' : '置顶' }}
          </el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        size="small"
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { commentApi, type CommentVO } from '~/api/content/comment'
import { aiApi } from '~/api/ai/ai'
import { handleAiError } from '~/utils/ai/aiError'

const loading = ref(false)
const records = ref<CommentVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const filterPostTitle = ref('')
const filterTop = ref('')
const filterAiReview = ref('')
const selectedIds = ref<number[]>([])

let searchTimer: ReturnType<typeof setTimeout> | null = null
function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    handleFilterChange()
  }, 300)
}

function handleFilterChange() {
  pageNum.value = 1
  loadData()
}

function handleSizeChange() {
  pageNum.value = 1
  loadData()
}

function handleSelectionChange(rows: CommentVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

async function loadData() {
  loading.value = true
  try {
    const res = await commentApi.list({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: filterStatus.value || undefined,
      postTitle: filterPostTitle.value || undefined,
      isTop: filterTop.value ? filterTop.value === 'true' : undefined,
      aiReviewStatus: filterAiReview.value || undefined,
    })
    records.value = res.data.records
    total.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleAudit(row: CommentVO, status: string) {
  try {
    await commentApi.updateStatus(row.id, status)
    ElMessage.success(status === 'approved' ? '已通过' : '已拒绝')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleToggleTop(row: CommentVO) {
  try {
    await commentApi.toggleTop(row.id)
    ElMessage.success(row.isTop ? '已取消置顶' : '已置顶')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleDelete(row: CommentVO) {
  await ElMessageBox.confirm('确定删除该评论？', '提示', { type: 'warning' })
  try {
    await commentApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

async function handleBatchCommand(command: string) {
  if (command === 'delete') handleBatchDelete()
  else if (command === 'aiReview') handleBatchAiReview()
  else handleBatchAudit(command)
}

async function handleBatchAudit(status: string) {
  const label = status === 'approved' ? '通过' : '拒绝'
  await ElMessageBox.confirm(`确定批量${label}选中的 ${selectedIds.value.length} 条评论？`, '批量操作', { type: 'warning' })
  try {
    await commentApi.batchUpdateStatus(selectedIds.value, status)
    ElMessage.success(`批量${label}成功`)
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定批量删除选中的 ${selectedIds.value.length} 条评论？此操作不可恢复。`, '批量删除', { type: 'warning' })
  try {
    await commentApi.batchDelete(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

async function handleBatchAiReview() {
  await ElMessageBox.confirm(`确定对选中的 ${selectedIds.value.length} 条评论进行 AI 重新审核？`, 'AI 审核', { type: 'info' })
  try {
    await aiApi.batchReview(selectedIds.value)
    ElMessage.success('已提交 AI 审核，结果将稍后更新')
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    handleAiError(e)
  }
}

function aiReviewLabel(status: string | null): string {
  if (!status) return '—'
  const map: Record<string, string> = { pass: 'AI通过', suspect: 'AI疑似', reject: 'AI违规', pending: '待AI审核' }
  return map[status] || '—'
}

function aiReviewType(status: string | null): '' | 'success' | 'warning' | 'danger' | 'info' {
  if (!status) return 'info'
  const map: Record<string, '' | 'success' | 'warning' | 'danger' | 'info'> = { pass: 'success', suspect: 'warning', reject: 'danger', pending: 'info' }
  return map[status] || 'info'
}

function statusLabel(s: string) {
  return { pending: '待审核', approved: '已通过', rejected: '已拒绝' }[s] || s
}
function statusType(s: string) {
  return ({ pending: 'warning', approved: 'success', rejected: 'danger' }[s] || '') as '' | 'success' | 'warning' | 'danger'
}
function formatTime(t: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

onMounted(loadData)

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped lang="scss">
// ========== 页面级配色覆盖 ==========
.comment-page {
  .page-header {
    h2 {
      font-size: 1.05rem;
      font-weight: 600;
      color: var(--el-text-color-primary);
      margin: 0;
      white-space: nowrap;
      letter-spacing: 0.3px;
    }
  }
}

// ========== 用户单元格 ==========
.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
  .nickname { font-size: 13px; font-weight: 500; }
}

// ========== 评论内容 ==========
.comment-content-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
  line-height: 1.5;
}
.reply-tag {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.reply-target {
  color: var(--el-color-primary);
  font-weight: 500;
}
.comment-text {
  font-size: 13px;
  color: var(--el-text-color-primary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

// ========== 文章标题 ==========
.post-title-link {
  font-size: 13px;
  color: var(--el-text-color-regular);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  display: block;
}
</style>
