<template>
  <div class="user-page">
    <div class="page-header">
      <h2>用户管理</h2>
    </div>

    <el-tabs v-model="activeTab" class="user-tabs compact-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="用户列表" name="users">
        <div class="toolbar-row">
          <div class="filter-bar">
            <el-input v-model="keyword" placeholder="搜索邮箱/昵称" clearable style="width: 220px"
              @clear="handleSearch">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
            <el-select v-model="filterRole" placeholder="角色" clearable style="width: 120px" @change="handleSearch">
              <el-option label="管理员" value="admin" />
              <el-option label="普通用户" value="user" />
            </el-select>
            <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="handleSearch">
              <el-option label="正常" value="enabled" />
              <el-option label="禁用" value="disabled" />
              <el-option label="锁定" value="locked" />
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
                    <el-dropdown-item command="enabled">启用</el-dropdown-item>
                    <el-dropdown-item command="disabled">禁用</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </div>
        </div>

        <el-table :data="records" v-loading="loading" stripe height="560"
          :row-class-name="rowClassName" @selection-change="handleSelectionChange">
          <el-table-column type="selection" width="40" align="center"
            :selectable="(row: UserVO) => row.role !== 'admin'" />
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="用户" min-width="200">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="32" :src="row.avatar || undefined">{{ row.nickname?.[0] }}</el-avatar>
                <div class="user-info">
                  <span class="nickname">{{ row.nickname }}</span>
                  <span class="email">{{ row.email }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="90" align="center">
            <template #default="{ row }">
              <el-tag :type="row.role === 'admin' ? 'danger' : 'info'" size="small">
                {{ row.role === 'admin' ? '管理员' : '用户' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="状态" width="140" align="center">
            <template #default="{ row }">
              <div class="status-cell">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
                <el-tooltip v-if="row.status === 'locked' && row.lockUntil" placement="top">
                  <template #content>
                    <div style="font-size: 12px; line-height: 1.6;">
                      <div>解锁时间：{{ formatTime(row.lockUntil) }}</div>
                      <div>失败次数：{{ row.failedLoginAttempts ?? 0 }}</div>
                    </div>
                  </template>
                  <el-icon class="lock-icon"><Lock /></el-icon>
                </el-tooltip>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="最后登录IP" width="140" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="ip-text">{{ row.lastLoginIp || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="个人介绍" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="bio-text">{{ row.bio || '-' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="最后登录" width="150">
            <template #default="{ row }">{{ formatTime(row.lastLoginTime) }}</template>
          </el-table-column>
          <el-table-column label="注册时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <template v-if="row.role !== 'admin'">
                <el-button v-if="row.status === 'locked'" text type="primary" size="small"
                  @click="handleUnlock(row)">解锁</el-button>
                <el-button v-if="row.status === 'enabled'" text type="warning" size="small"
                  @click="handleToggleStatus(row, 'disabled')">禁用</el-button>
                <el-button v-if="row.status === 'disabled'" text type="success" size="small"
                  @click="handleToggleStatus(row, 'enabled')">启用</el-button>
                <el-button text type="info" size="small"
                  @click="handleResetPassword(row)">重置密码</el-button>
              </template>
              <span v-else class="muted-text">—</span>
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
      </el-tab-pane>

      <el-tab-pane name="reviews">
        <template #label>
          <span>个人信息审核</span>
          <span v-if="pendingProfileReviewCount > 0" class="compact-tab-count compact-tab-count--warning">
            {{ pendingProfileReviewCount > 99 ? '99+' : pendingProfileReviewCount }}
          </span>
        </template>
        <div class="toolbar-row">
          <div class="filter-bar">
            <el-input v-model="reviewKeyword" placeholder="搜索昵称/邮箱" clearable style="width: 240px" @clear="handleReviewSearch">
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>
          </div>
        </div>

        <el-table :data="reviewRecords" v-loading="reviewLoading" stripe height="560" :row-class-name="reviewRowClassName">
          <el-table-column type="index" label="#" width="50" align="center" />
          <el-table-column label="用户" min-width="230">
            <template #default="{ row }">
              <div class="user-cell">
                <el-avatar :size="36" :src="row.currentAvatar || undefined">{{ row.currentNickname?.[0] || 'U' }}</el-avatar>
                <div class="user-info">
                  <span class="nickname">{{ row.currentNickname || '未命名用户' }}</span>
                  <span class="email">{{ row.email || '-' }}</span>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="待审信息" min-width="320">
            <template #default="{ row }">
              <div class="pending-cell">
                <AppImage
                  :src="row.pendingAvatar || ''"
                  fit="cover"
                  class="pending-avatar"
                  :preview-src-list="row.pendingAvatar ? [row.pendingAvatar] : []"
                />
                <div class="pending-meta">
                  <p class="meta-line"><span>昵称：</span>{{ row.pendingNickname || '-' }}</p>
                  <p class="meta-line bio"><span>简介：</span>{{ row.pendingBio || '-' }}</p>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="提交时间" width="170">
            <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openReviewDetail(row)">详情</el-button>
              <el-button text type="danger" size="small" @click="handleRejectReview(row)">拒绝</el-button>
              <el-button text type="success" size="small" @click="handleApproveReview(row)">通过</el-button>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="reviewPageNum"
            v-model:page-size="reviewPageSize"
            :total="reviewTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            size="small"
            @current-change="loadReviewData"
            @size-change="handleReviewSizeChange"
          />
        </div>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="reviewDetailVisible" title="个人信息审核详情" width="760px" destroy-on-close>
      <template v-if="reviewDetailRecord">
        <div class="detail-grid">
          <section class="detail-card">
            <h4>当前生效信息</h4>
            <div class="detail-avatar">
              <AppImage v-if="reviewDetailRecord.currentAvatar" :src="reviewDetailRecord.currentAvatar" fit="cover" class="detail-image" />
              <div v-else class="detail-avatar-fallback">无头像</div>
            </div>
            <p><span>昵称：</span>{{ reviewDetailRecord.currentNickname || '-' }}</p>
            <p><span>简介：</span>{{ reviewDetailRecord.currentBio || '-' }}</p>
          </section>

          <section class="detail-card pending">
            <h4>待审核信息</h4>
            <div class="detail-avatar">
              <AppImage v-if="reviewDetailRecord.pendingAvatar" :src="reviewDetailRecord.pendingAvatar" fit="cover" class="detail-image" :preview-src-list="[reviewDetailRecord.pendingAvatar]" />
              <div v-else class="detail-avatar-fallback">无头像</div>
            </div>
            <p><span>昵称：</span>{{ reviewDetailRecord.pendingNickname || '-' }}</p>
            <p><span>简介：</span>{{ reviewDetailRecord.pendingBio || '-' }}</p>
          </section>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Lock, ArrowDown } from '@element-plus/icons-vue'
import { userApi, type UserVO } from '~/api/auth/user'
import { profileReviewApi, type ProfileReviewVO } from '~/api/system/profileReview'

const activeTab = ref<'users' | 'reviews'>('users')
const route = useRoute()
const router = useRouter()
const loading = ref(false)
const records = ref<UserVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const filterRole = ref('')
const filterStatus = ref('')
const selectedIds = ref<number[]>([])
const pendingProfileReviewCount = useState<number>('pendingProfileReviewCount', () => 0)

const reviewLoading = ref(false)
const reviewRecords = ref<ProfileReviewVO[]>([])
const reviewTotal = ref(0)
const reviewPageNum = ref(1)
const reviewPageSize = ref(20)
const reviewKeyword = ref('')
const reviewDetailVisible = ref(false)
const reviewDetailRecord = ref<ProfileReviewVO | null>(null)

function handleSelectionChange(rows: UserVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

function statusLabel(s: string) {
  return s === 'enabled' ? '正常' : s === 'disabled' ? '禁用' : s === 'locked' ? '锁定' : s
}

function statusTagType(s: string) {
  return s === 'enabled' ? 'success' : s === 'disabled' ? 'danger' : 'warning'
}

function rowClassName({ row }: { row: UserVO }) {
  return row.status === 'locked' ? 'locked-row' : ''
}

function reviewRowClassName() {
  return 'pending-highlight-row'
}

async function loadData() {
  loading.value = true
  try {
    const res = await userApi.list({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      role: filterRole.value || undefined,
      status: filterStatus.value || undefined,
    })
    records.value = res.data.records
    total.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadReviewData() {
  reviewLoading.value = true
  try {
    const res = await profileReviewApi.page({
      pageNum: reviewPageNum.value,
      pageSize: reviewPageSize.value,
      keyword: reviewKeyword.value || undefined,
    })
    reviewRecords.value = res.data.records
    reviewTotal.value = res.data.total
    pendingProfileReviewCount.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '审核数据加载失败')
  } finally {
    reviewLoading.value = false
  }
}

async function loadPendingReviewCount() {
  try {
    const res = await profileReviewApi.page({ pageNum: 1, pageSize: 1 })
    pendingProfileReviewCount.value = res.data.total
  } catch {
    pendingProfileReviewCount.value = 0
  }
}

function handleTabChange(name: string | number) {
  const nextTab = name === 'reviews' ? 'reviews' : 'users'
  if (activeTab.value !== nextTab) {
    activeTab.value = nextTab
  }

  if (nextTab === 'reviews' && !reviewRecords.value.length && !reviewLoading.value) {
    loadReviewData()
  }

  const queryTab = Array.isArray(route.query.tab) ? route.query.tab[0] : route.query.tab
  if (queryTab !== nextTab) {
    router.replace({
      path: route.path,
      query: {
        ...route.query,
        tab: nextTab,
      },
    })
  }
}

function getTabFromQuery(): 'users' | 'reviews' {
  const queryTab = Array.isArray(route.query.tab) ? route.query.tab[0] : route.query.tab
  return queryTab === 'reviews' ? 'reviews' : 'users'
}

// 关键词防抖自动搜索
let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { pageNum.value = 1; loadData() }, 300)
})

function handleSearch() {
  pageNum.value = 1
  loadData()
}

function handleSizeChange() {
  pageNum.value = 1
  loadData()
}

function handleReviewSearch() {
  reviewPageNum.value = 1
  loadReviewData()
}

function handleReviewSizeChange() {
  reviewPageNum.value = 1
  loadReviewData()
}

async function handleBatchCommand(command: string) {
  handleBatchStatus(command)
}

async function handleBatchStatus(status: string) {
  const label = status === 'enabled' ? '启用' : '禁用'
  await ElMessageBox.confirm(`确定批量${label}选中的 ${selectedIds.value.length} 个用户？`, '批量操作', { type: 'warning' })
  try {
    await userApi.batchUpdateStatus(selectedIds.value, status)
    ElMessage.success(`批量${label}成功`)
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleToggleStatus(row: UserVO, newStatus: string) {
  const action = newStatus === 'disabled' ? '禁用' : '启用'
  await ElMessageBox.confirm(`确定${action}用户「${row.nickname}」？`, '提示', { type: 'warning' })
  try {
    await userApi.updateStatus(row.id, newStatus)
    ElMessage.success(`${action}成功`)
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleUnlock(row: UserVO) {
  await ElMessageBox.confirm(`确定解锁用户「${row.nickname}」？解锁后用户可正常登录。`, '解锁确认', { type: 'warning' })
  try {
    await userApi.unlock(row.id)
    ElMessage.success('解锁成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleResetPassword(row: UserVO) {
  await ElMessageBox.confirm(
    `确定重置用户「${row.nickname}」的密码？新密码将通过邮件发送至 ${row.email}`,
    '重置密码确认',
    { type: 'warning' }
  )
  try {
    await userApi.resetPassword(row.id)
    ElMessage.success('密码已重置，新密码已发送至用户邮箱')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '重置失败')
  }
}

function openReviewDetail(row: ProfileReviewVO) {
  reviewDetailRecord.value = row
  reviewDetailVisible.value = true
}

async function handleApproveReview(row: ProfileReviewVO) {
  await ElMessageBox.confirm(`确认通过「${row.currentNickname}」的个人信息审核？`, '提示', { type: 'warning' })
  try {
    await profileReviewApi.approve(row.reviewId)
    ElMessage.success('审核通过')
    if (reviewDetailRecord.value?.reviewId === row.reviewId) {
      reviewDetailVisible.value = false
      reviewDetailRecord.value = null
    }
    await loadReviewData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleRejectReview(row: ProfileReviewVO) {
  let reason = ''
  try {
    const promptResult = await ElMessageBox.prompt(`请输入拒绝「${row.currentNickname}」的原因`, '拒绝审核', {
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：头像不清晰、昵称不合规、简介含敏感词',
      inputValidator: (v: string) => {
        if (!v || !v.trim()) return '拒绝原因不能为空'
        if (v.trim().length > 200) return '拒绝原因不能超过200字'
        return true
      },
    })
    const promptValue = (promptResult as { value?: string; inputValue?: string }).value
      ?? (promptResult as { inputValue?: string }).inputValue
      ?? ''
    reason = promptValue.trim()
  } catch {
    return
  }

  try {
    await profileReviewApi.reject(row.reviewId, reason)
    ElMessage.success('已拒绝')
    if (reviewDetailRecord.value?.reviewId === row.reviewId) {
      reviewDetailVisible.value = false
      reviewDetailRecord.value = null
    }
    await loadReviewData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

function formatTime(t: string | null) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

let reviewDebounceTimer: ReturnType<typeof setTimeout> | null = null
watch(reviewKeyword, () => {
  if (reviewDebounceTimer) clearTimeout(reviewDebounceTimer)
  reviewDebounceTimer = setTimeout(() => {
    reviewPageNum.value = 1
    loadReviewData()
  }, 300)
})

onMounted(() => {
  activeTab.value = getTabFromQuery()
  loadData()
  if (activeTab.value === 'reviews') {
    loadReviewData()
  }
  loadPendingReviewCount()
})

watch(() => route.query.tab, () => {
  const nextTab = getTabFromQuery()
  if (activeTab.value !== nextTab) {
    activeTab.value = nextTab
  }
  if (nextTab === 'reviews' && !reviewRecords.value.length && !reviewLoading.value) {
    loadReviewData()
  }
})

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (reviewDebounceTimer) clearTimeout(reviewDebounceTimer)
})
</script>

<style scoped lang="scss">
.user-page {
  .toolbar-row {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 10px;
    margin-bottom: 10px;
    flex-wrap: wrap;
  }

  .user-cell {
    display: flex;
    align-items: center;
    gap: 10px;
    .user-info {
      display: flex;
      flex-direction: column;
      .nickname { font-size: 13px; }
      .email { font-size: 12px; color: var(--el-text-color-secondary); }
    }
  }

  .status-cell {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    .lock-icon {
      font-size: 14px;
      color: var(--el-color-warning);
      cursor: help;
    }
  }

  .ip-text {
    font-size: 12px;
    font-family: 'SF Mono', 'Consolas', monospace;
    color: var(--el-text-color-regular);
  }

  .bio-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }

  .muted-text {
    color: var(--el-text-color-regular);
    font-size: 13px;
  }

  .pending-cell {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .pending-avatar {
    width: 64px;
    height: 64px;
    flex-shrink: 0;
  }

  .pending-meta {
    flex: 1;
    min-width: 0;
  }

  .meta-line {
    margin: 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-primary);

    span {
      color: var(--el-text-color-secondary);
    }
  }

  .meta-line.bio {
    margin-top: 4px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 860px) {
    grid-template-columns: 1fr;
  }
}

.detail-card {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 12px;
  background: var(--el-fill-color-blank);

  &.pending {
    border-color: rgba(245, 158, 11, 0.45);
    background: rgba(254, 252, 232, 0.55);
  }

  h4 {
    margin: 0 0 10px;
    font-size: 14px;
    font-weight: 600;
  }

  p {
    margin: 8px 0 0;
    font-size: 13px;
    color: var(--el-text-color-primary);
    line-height: 1.6;

    span {
      color: var(--el-text-color-secondary);
    }
  }
}

.detail-avatar {
  margin-bottom: 6px;
}

.detail-image,
.detail-avatar-fallback {
  width: 90px;
  height: 90px;
  border-radius: 10px;
}

.detail-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

// ========== 锁定行高亮 ==========
:deep(.el-table) .locked-row td.el-table__cell {
  background-color: rgba(245, 158, 11, 0.06) !important;
}

:deep(.el-table__row.pending-highlight-row > td) {
  background: rgba(245, 158, 11, 0.12) !important;
}

:deep(.el-table__row.pending-highlight-row:hover > td) {
  background: rgba(245, 158, 11, 0.2) !important;
}

:deep(html.dark .el-table__row.pending-highlight-row > td),
:deep(body.dark .el-table__row.pending-highlight-row > td),
:deep(.dark .el-table__row.pending-highlight-row > td) {
  background: rgba(245, 158, 11, 0.16) !important;
}

:deep(html.dark .el-table__row.pending-highlight-row:hover > td),
:deep(body.dark .el-table__row.pending-highlight-row:hover > td),
:deep(.dark .el-table__row.pending-highlight-row:hover > td) {
  background: rgba(245, 158, 11, 0.24) !important;
}
</style>
