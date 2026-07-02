<template>
  <div class="user-page">
    <div class="page-header">
      <h2>用户管理</h2>
      <div class="header-actions">
        <el-button type="primary" plain @click="navigateTo('/risk-control')">前往风控管理</el-button>
      </div>
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

        <el-table :data="records" v-loading="loading" stripe :height="tableHeight"
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
          <el-table-column label="状态" width="190" align="center">
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
                <el-tooltip v-if="isMuted(row)" placement="top">
                  <template #content>
                    <div style="font-size: 12px; line-height: 1.6;">
                      <div>{{ row.mutedPermanent ? '永久禁言' : `禁言至：${formatTime(row.mutedUntil)}` }}</div>
                      <div>原因：{{ row.mutedReason || '-' }}</div>
                    </div>
                  </template>
                  <el-tag type="warning" size="small">禁言</el-tag>
                </el-tooltip>
                <el-tooltip v-if="row.userBlocked" placement="top">
                  <template #content>
                    <div style="font-size: 12px; line-height: 1.6;">
                      <div>{{ row.userBlockPermanent ? '永久封禁' : `封禁剩余：${formatRemainingSeconds(row.userBlockRemainingSeconds, false)}` }}</div>
                      <div>原因：{{ row.userBlockReason || '-' }}</div>
                    </div>
                  </template>
                  <el-tag type="danger" size="small">封禁</el-tag>
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
          <el-table-column label="操作" width="210" fixed="right">
            <template #default="{ row }">
              <template v-if="row.role !== 'admin'">
                <el-button v-if="row.status === 'locked'" text type="primary" size="small"
                  @click="handleUnlock(row)">解锁</el-button>
                <el-button v-if="row.status === 'enabled'" text type="warning" size="small"
                  @click="handleToggleStatus(row, 'disabled')">禁用</el-button>
                <el-button v-if="row.status === 'disabled'" text type="success" size="small"
                  @click="handleToggleStatus(row, 'enabled')">启用</el-button>
                <el-button text type="danger" size="small"
                  @click="openUserRiskDialog(row)">{{ row.userBlocked ? '调整封禁' : '封禁' }}</el-button>
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

          <el-table :data="reviewRecords" v-loading="reviewLoading" stripe :height="tableHeight" :row-class-name="reviewRowClassName">
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
<el-table-column label="操作" width="100" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="openReviewDetail(row)">审核</el-button>
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

        <template v-if="reviewDetailRecord">
          <el-divider />
          <el-form label-width="82px" class="reject-form">
            <el-form-item label="拒绝原因">
              <el-select v-model="reviewRejectReason" placeholder="选择预设原因" clearable style="width: 100%">
                <el-option v-for="reason in reviewRejectReasonOptions" :key="reason" :label="reason" :value="reason" />
              </el-select>
            </el-form-item>
            <el-form-item label="自定义">
              <el-input v-model="reviewRejectReason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="拒绝时必须填写原因，可选择预设后再编辑" />
            </el-form-item>
          </el-form>
        </template>
      </template>
      <template #footer>
        <el-button @click="reviewDetailVisible = false">取消</el-button>
        <el-button type="danger" :loading="reviewSubmitting" @click="submitReviewReject('block')">拒绝并封禁</el-button>
        <el-button type="danger" :loading="reviewSubmitting" @click="submitReviewReject('reject')">拒绝</el-button>
        <el-button type="success" :loading="reviewSubmitting" @click="submitReviewApprove">通过</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="userBlockDialogVisible" title="用户风控处置" width="520px" destroy-on-close>
      <template v-if="userBlockRecord">
        <el-form label-width="90px">
          <el-form-item label="用户">
            <span>{{ userBlockRecord.nickname || '-' }}（{{ userBlockRecord.email }}）</span>
          </el-form-item>
          <el-form-item label="处置类型">
            <el-radio-group v-model="userBlockForm.type">
              <el-radio-button value="USER">用户封禁</el-radio-button>
              <el-radio-button value="MUTE">禁言</el-radio-button>
              <el-radio-button value="IP">IP 封禁</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="userBlockForm.type === 'IP'" label="IP 地址">
            <el-input v-model="userBlockForm.ip" placeholder="默认使用用户最后登录 IP" />
          </el-form-item>
          <el-form-item label="处置方式">
            <el-radio-group v-model="userBlockForm.permanent">
              <el-radio :value="false">限时</el-radio>
              <el-radio :value="true">永久</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item v-if="!userBlockForm.permanent" label="有效期">
            <el-select v-model="userBlockForm.durationMinutes" style="width: 100%">
              <el-option label="1 小时" :value="60" />
              <el-option label="24 小时" :value="1440" />
              <el-option label="7 天" :value="10080" />
              <el-option label="30 天" :value="43200" />
            </el-select>
          </el-form-item>
          <el-form-item label="原因">
            <el-select v-model="userBlockForm.reason" placeholder="选择预设原因" clearable style="width: 100%; margin-bottom: 8px">
              <el-option v-for="reason in userBlockReasonOptions" :key="reason" :label="reason" :value="reason" />
            </el-select>
            <el-input v-model="userBlockForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="处置原因必填，可选择预设后编辑" />
          </el-form-item>
        </el-form>
      </template>
      <template #footer>
        <el-button @click="userBlockDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="userBlockSubmitting" @click="submitUserRiskAction">确认</el-button>
      </template>
    </el-dialog>

  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Lock, ArrowDown } from '@element-plus/icons-vue'
import { userApi, type UserVO } from '~/api/auth/user'
import { profileReviewApi, type ProfileReviewVO } from '~/api/system/profileReview'
import { logsApi } from '~/api/system/logs'

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
const tableHeight = useAdminTableHeight()
type UserRiskActionType = 'USER' | 'MUTE' | 'IP'

const reviewLoading = ref(false)
const reviewRecords = ref<ProfileReviewVO[]>([])
const reviewTotal = ref(0)
const reviewPageNum = ref(1)
const reviewPageSize = ref(20)
const reviewKeyword = ref('')
const reviewDetailVisible = ref(false)
const reviewDetailRecord = ref<ProfileReviewVO | null>(null)
const reviewRejectReason = ref('')
const reviewSubmitting = ref(false)
const reviewRejectReasonOptions = [
  '头像不清晰或不符合规范',
  '昵称包含违规或敏感内容',
  '简介包含广告或推广信息',
  '个人信息涉及不友善表达',
  '头像或简介疑似侵权',
]
const userBlockDialogVisible = ref(false)
const userBlockSubmitting = ref(false)
const userBlockRecord = ref<UserVO | null>(null)
const userBlockForm = reactive({ type: 'USER' as UserRiskActionType, ip: '', permanent: false, durationMinutes: 1440, reason: '' })
const userBlockReasonOptions = [
  '发布违规内容，封禁 24 小时',
  '多次提交垃圾内容，封禁 7 天',
  '恶意刷屏或骚扰用户，封禁 30 天',
  '严重违反站点规则，永久封禁',
]
function handleSelectionChange(rows: UserVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

function statusLabel(s: string) {
  return s === 'enabled' ? '正常' : s === 'disabled' ? '禁用' : s === 'locked' ? '锁定' : s
}

function statusTagType(s: string) {
  return s === 'enabled' ? 'success' : s === 'disabled' ? 'danger' : 'warning'
}

function isCancelAction(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}

function rowClassName({ row }: { row: UserVO }) {
  if (row.userBlocked) return 'blocked-row'
  if (isMuted(row)) return 'muted-row'
  return row.status === 'locked' ? 'locked-row' : ''
}

function isMuted(row: UserVO) {
  if (row.mutedPermanent) return true
  if (!row.mutedUntil) return false
  return new Date(row.mutedUntil).getTime() > Date.now()
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
    openFocusedReviewIfNeeded()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '审核数据加载失败')
  } finally {
    reviewLoading.value = false
  }
}

function formatRemainingSeconds(remainingSeconds: number, permanent: boolean): string {
  if (permanent) return '永久'
  if (!remainingSeconds || remainingSeconds <= 0) return '已过期'
  const days = Math.floor(remainingSeconds / 86400)
  const hours = Math.floor((remainingSeconds % 86400) / 3600)
  const minutes = Math.floor((remainingSeconds % 3600) / 60)
  if (days > 0) return `${days}天${hours}小时`
  if (hours > 0) return `${hours}小时${minutes}分钟`
  return `${minutes}分钟`
}

function getFocusReviewId() {
  const raw = Array.isArray(route.query.focusId) ? route.query.focusId[0] : route.query.focusId
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : null
}

function openFocusedReviewIfNeeded() {
  const focusId = getFocusReviewId()
  if (!focusId || reviewDetailVisible.value) return
  const target = reviewRecords.value.find(row => row.reviewId === focusId)
  if (target) openReviewDetail(target)
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
  reviewRejectReason.value = ''
  reviewDetailVisible.value = true
}

async function submitReviewApprove() {
  if (!reviewDetailRecord.value) return
  const row = reviewDetailRecord.value
  await ElMessageBox.confirm(`确认通过「${row.pendingNickname || row.currentNickname}」的个人信息审核？`, '提示', { type: 'warning' })
  reviewSubmitting.value = true
  try {
    await profileReviewApi.approve(row.reviewId)
    ElMessage.success('审核通过')
    reviewDetailVisible.value = false
    reviewDetailRecord.value = null
    await loadReviewData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    reviewSubmitting.value = false
  }
}

async function submitReviewReject(type: 'reject' | 'block') {
  if (!reviewDetailRecord.value) return
  const reason = reviewRejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('拒绝原因不能为空')
    return
  }
  const row = reviewDetailRecord.value
  const actionLabel = type === 'block' ? '拒绝并封禁24小时' : '拒绝'
  await ElMessageBox.confirm(`确认${actionLabel}「${row.pendingNickname || row.currentNickname}」的个人信息审核？`, '高风险操作确认', { type: 'warning' })
  reviewSubmitting.value = true
  try {
    await profileReviewApi.reject(row.reviewId, reason)
    if (type === 'block') {
      await logsApi.blockUser({ userId: row.userId, durationMinutes: 1440, reason: `个人信息审核封禁：${reason}` })
    }
    ElMessage.success(type === 'block' ? '已拒绝并封禁24小时' : '已拒绝')
    reviewDetailVisible.value = false
    reviewDetailRecord.value = null
    await loadReviewData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    reviewSubmitting.value = false
  }
}

function openUserRiskDialog(row: UserVO) {
  userBlockRecord.value = row
  userBlockForm.type = 'USER'
  userBlockForm.ip = row.lastLoginIp || ''
  userBlockForm.permanent = Boolean(row.userBlockPermanent)
  userBlockForm.durationMinutes = 1440
  userBlockForm.reason = row.userBlockReason || ''
  userBlockDialogVisible.value = true
}

watch(() => userBlockForm.type, (type) => {
  if (!userBlockDialogVisible.value || !userBlockRecord.value) return
  userBlockForm.permanent = type === 'USER' ? Boolean(userBlockRecord.value.userBlockPermanent) : false
  userBlockForm.durationMinutes = 1440
  userBlockForm.reason = type === 'USER' ? (userBlockRecord.value.userBlockReason || '') : ''
})

async function submitUserRiskAction() {
  if (!userBlockRecord.value) return
  const reason = userBlockForm.reason.trim()
  if (!reason) {
    ElMessage.warning('处置原因不能为空')
    return
  }
  if (userBlockForm.type === 'IP' && !userBlockForm.ip.trim()) {
    ElMessage.warning('IP 地址不能为空')
    return
  }
  const row = userBlockRecord.value
  const actionLabel = userBlockForm.type === 'MUTE' ? '禁言' : userBlockForm.type === 'IP' ? 'IP 封禁' : (row.userBlocked ? '调整封禁' : '用户封禁')
  try {
    await ElMessageBox.confirm(`确认对用户「${row.nickname || row.email}」执行${actionLabel}？`, '高风险操作确认', { type: 'warning' })
  } catch (error: unknown) {
    if (isCancelAction(error)) return
    ElMessage.error('确认失败')
    return
  }
  userBlockSubmitting.value = true
  try {
    const payload = {
      permanent: userBlockForm.permanent,
      durationMinutes: userBlockForm.permanent ? undefined : userBlockForm.durationMinutes,
      reason,
    }
    if (userBlockForm.type === 'USER') {
      await logsApi.blockUser({ email: row.email, ...payload })
    } else if (userBlockForm.type === 'MUTE') {
      await userApi.muteByEmail({ email: row.email, permanent: userBlockForm.permanent, minutes: userBlockForm.permanent ? undefined : userBlockForm.durationMinutes, reason })
    } else {
      await logsApi.blockIp({ ip: userBlockForm.ip.trim(), ...payload })
    }
    ElMessage.success('处置成功')
    userBlockDialogVisible.value = false
    userBlockRecord.value = null
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '处置失败')
  } finally {
    userBlockSubmitting.value = false
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
  if (getFocusReviewId()) {
    activeTab.value = 'reviews'
    reviewPageSize.value = 50
  }
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
.reject-form {
  margin-top: 12px;
}

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

:deep(.el-table) .muted-row td.el-table__cell {
  background-color: rgba(239, 68, 68, 0.06) !important;
}

:deep(.el-table) .blocked-row td.el-table__cell {
  background-color: rgba(220, 38, 38, 0.08) !important;
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
