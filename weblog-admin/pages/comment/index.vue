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
                <el-dropdown-item command="delete" divided>
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe :height="tableHeight" :row-class-name="rowClassName"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="40" align="center" :selectable="(row: CommentVO) => row.status !== 'pending'" />
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
          <el-button v-if="row.status === 'pending'" text type="primary" size="small" @click="openAuditDetail(row)">审核</el-button>
          <template v-else-if="row.status === 'approved'">
            <el-button text :type="row.isTop ? 'info' : 'primary'" size="small" @click="handleToggleTop(row)">
              {{ row.isTop ? '取消置顶' : '置顶' }}
            </el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
          <template v-else-if="row.status === 'rejected'">
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
          <span v-else class="muted-text">—</span>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="auditDialogVisible" title="评论审核详情" width="640px" destroy-on-close>
      <template v-if="auditRecord">
        <div class="audit-detail">
          <p><span>用户：</span>{{ auditRecord.nickname || '未知用户' }}</p>
          <p><span>文章：</span>{{ auditRecord.postTitle || '未知文章' }}</p>
          <p><span>时间：</span>{{ formatTime(auditRecord.createTime) }}</p>
          <div class="audit-content">
            <span>评论内容：</span>
            <div>{{ auditRecord.content }}</div>
          </div>
          <el-form label-width="82px" class="reject-form">
            <el-form-item label="拒绝原因">
              <el-select v-model="rejectReason" placeholder="选择预设原因" clearable style="width: 100%">
                <el-option v-for="reason in rejectReasonOptions" :key="reason" :label="reason" :value="reason" />
              </el-select>
            </el-form-item>
            <el-form-item label="自定义">
              <el-input v-model="rejectReason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="拒绝时必须填写原因，可选择预设后再编辑" />
            </el-form-item>
          </el-form>
        </div>
      </template>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="warning" :loading="auditSubmitting" @click="submitAuditWithPenalty('mute')">拒绝并禁言</el-button>
        <el-button type="danger" :loading="auditSubmitting" @click="submitAuditWithPenalty('block')">拒绝并封禁</el-button>
        <el-button type="danger" :loading="auditSubmitting" @click="submitAudit('rejected')">拒绝</el-button>
        <el-button type="success" :loading="auditSubmitting" @click="submitAudit('approved')">通过</el-button>
      </template>
    </el-dialog>

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
import { userApi } from '~/api/auth/user'
import { logsApi } from '~/api/system/logs'

const route = useRoute()
const loading = ref(false)
const records = ref<CommentVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const filterPostTitle = ref('')
const filterTop = ref('')
const selectedIds = ref<number[]>([])
const tableHeight = useAdminTableHeight()
const auditDialogVisible = ref(false)
const auditSubmitting = ref(false)
const auditRecord = ref<CommentVO | null>(null)
const rejectReason = ref('')
const rejectReasonOptions = [
  '内容与文章主题无关',
  '包含广告或推广信息',
  '存在不友善或攻击性表达',
  '疑似重复评论',
  '包含违规或敏感内容',
]

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

function rowClassName({ row }: { row: CommentVO }) {
  return row.status === 'pending' ? 'pending-highlight-row' : ''
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
    })
    records.value = res.data.records
    total.value = res.data.total
    openFocusedReviewIfNeeded()
    refreshPendingBadge()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

const pendingCommentCount = useState<number>('pendingCommentCount', () => 0)
async function refreshPendingBadge() {
  try {
    const res = await commentApi.pendingCount()
    pendingCommentCount.value = res.data
  } catch { /* ignore */ }
}

function getFocusId() {
  const raw = Array.isArray(route.query.focusId) ? route.query.focusId[0] : route.query.focusId
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : null
}

function openFocusedReviewIfNeeded() {
  const focusId = getFocusId()
  if (!focusId || auditDialogVisible.value) return
  const target = records.value.find(row => row.id === focusId)
  if (!target) return
  openAuditDetail(target)
}

function openAuditDetail(row: CommentVO) {
  auditRecord.value = row
  rejectReason.value = row.rejectReason || ''
  auditDialogVisible.value = true
}

async function submitAudit(status: 'approved' | 'rejected') {
  if (!auditRecord.value) return
  const reason = rejectReason.value.trim()
  if (status === 'rejected' && !reason) {
    ElMessage.warning('拒绝原因不能为空')
    return
  }
  auditSubmitting.value = true
  try {
    await commentApi.updateStatus(auditRecord.value.id, status, status === 'rejected' ? reason : undefined)
    ElMessage.success(status === 'approved' ? '已通过' : '已拒绝')
    auditDialogVisible.value = false
    auditRecord.value = null
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    auditSubmitting.value = false
  }
}

async function submitAuditWithPenalty(type: 'mute' | 'block') {
  if (!auditRecord.value) return
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('拒绝原因不能为空')
    return
  }
  const actionLabel = type === 'mute' ? '禁言24小时' : '封禁24小时'
  await ElMessageBox.confirm(`确认拒绝该评论并${actionLabel}用户「${auditRecord.value.nickname || auditRecord.value.userId}」？`, '高风险操作确认', { type: 'warning' })
  auditSubmitting.value = true
  try {
    await commentApi.updateStatus(auditRecord.value.id, 'rejected', reason)
    if (type === 'mute') {
      await userApi.mute(auditRecord.value.userId, { permanent: false, minutes: 1440, reason: `评论审核处置：${reason}` })
    } else {
      await logsApi.blockUser({ userId: auditRecord.value.userId, durationMinutes: 1440, reason: `评论审核封禁：${reason}` })
    }
    ElMessage.success(`已拒绝并${actionLabel}`)
    auditDialogVisible.value = false
    auditRecord.value = null
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    auditSubmitting.value = false
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
  else handleBatchAudit(command)
}

async function handleBatchAudit(status: string) {
  const label = status === 'approved' ? '通过' : '拒绝'
  let reason = ''
  if (status === 'rejected') {
    try {
      const result = await ElMessageBox.prompt('请输入批量拒绝原因', '批量拒绝', {
        inputPlaceholder: '例如：包含广告或推广信息',
        inputValidator: (v: string) => {
          if (!v || !v.trim()) return '拒绝原因不能为空'
          if (v.trim().length > 200) return '拒绝原因不能超过200字'
          return true
        },
      })
      reason = ((result as { value?: string }).value || '').trim()
    } catch {
      return
    }
  }
  await ElMessageBox.confirm(`确定批量${label}选中的 ${selectedIds.value.length} 条评论？`, '批量操作', { type: 'warning' })
  try {
    await commentApi.batchUpdateStatus(selectedIds.value, status, reason || undefined)
    ElMessage.success(`批量${label}成功`)
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleBatchDelete() {
  const hasPending = records.value.some(r => selectedIds.value.includes(r.id) && r.status === 'pending')
  if (hasPending) {
    ElMessage.warning('待审核评论不能删除，请先审核')
    return
  }
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

function statusLabel(s: string) {
  return { pending: '待审核', approved: '已通过', rejected: '已拒绝' }[s] || s
}
function statusType(s: string) {
  return ({ pending: 'warning', approved: 'success', rejected: 'danger' }[s] || '') as '' | 'success' | 'warning' | 'danger'
}
function formatTime(t: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

onMounted(() => {
  if (getFocusId()) {
    filterStatus.value = 'pending'
    pageSize.value = 50
  }
  loadData()
})

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

.muted-text {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.audit-detail {
  p {
    margin: 0 0 10px;
    line-height: 1.6;
    span { color: var(--el-text-color-secondary); }
  }
}

.audit-content {
  margin: 12px 0;
  span { color: var(--el-text-color-secondary); }
  div {
    margin-top: 6px;
    padding: 12px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    line-height: 1.7;
    white-space: pre-wrap;
  }
}

.reject-form {
  margin-top: 12px;
}

:deep(.el-table__body tr.pending-highlight-row > td.el-table__cell),
:deep(.el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped > td.el-table__cell) {
  background: rgba(245, 158, 11, 0.12) !important;
}

:deep(.el-table__body tr.pending-highlight-row:hover > td.el-table__cell),
:deep(.el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped:hover > td.el-table__cell) {
  background: rgba(245, 158, 11, 0.2) !important;
}

:deep(html.dark .el-table__body tr.pending-highlight-row > td.el-table__cell),
:deep(body.dark .el-table__body tr.pending-highlight-row > td.el-table__cell),
:deep(.dark .el-table__body tr.pending-highlight-row > td.el-table__cell),
:deep(html.dark .el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped > td.el-table__cell),
:deep(body.dark .el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped > td.el-table__cell),
:deep(.dark .el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped > td.el-table__cell) {
  background: rgba(245, 158, 11, 0.16) !important;
}

:deep(html.dark .el-table__body tr.pending-highlight-row:hover > td.el-table__cell),
:deep(body.dark .el-table__body tr.pending-highlight-row:hover > td.el-table__cell),
:deep(.dark .el-table__body tr.pending-highlight-row:hover > td.el-table__cell),
:deep(html.dark .el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped:hover > td.el-table__cell),
:deep(body.dark .el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped:hover > td.el-table__cell),
:deep(.dark .el-table--striped .el-table__body tr.pending-highlight-row.el-table__row--striped:hover > td.el-table__cell) {
  background: rgba(245, 158, 11, 0.24) !important;
}
</style>
