<template>
  <div class="friend-link-page">
    <div class="page-header">
      <h2>友链管理</h2>
      <div class="filter-bar">
        <el-input
          v-model="filterKeyword"
          placeholder="搜索网站名称/链接"
          clearable
          style="width: 220px"
          @input="debouncedSearch"
          @clear="handleFilterChange"
        />
        <el-select v-model="statusFilter" placeholder="友链状态" clearable style="width: 140px" @change="handleFilterChange">
          <el-option label="待审核" value="pending" />
          <el-option label="正常" value="active" />
          <el-option label="停用" value="inactive" />
          <el-option label="失效" value="broken" />
          <el-option label="已拒绝" value="rejected" />
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
                <el-dropdown-item command="active">启用</el-dropdown-item>
                <el-dropdown-item command="inactive">停用</el-dropdown-item>
                <el-dropdown-item command="delete" divided :disabled="hasPendingSelected">
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-tooltip content="控制用户端友链申请入口" placement="top">
          <div class="apply-switch">
            <span class="switch-label">申请入口</span>
            <el-switch v-model="applyEnabled" :loading="switchLoading" @change="handleApplySwitchChange" />
          </div>
        </el-tooltip>
        <el-button :loading="checking" @click="handleCheckLinks">检测链接</el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建友链
        </el-button>
      </div>
    </div>

    <el-table :data="filteredLinks" v-loading="loading" stripe :height="tableHeight" :row-class-name="friendLinkRowClassName"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="40" align="center" :selectable="isRowSelectable" />
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column label="网站" min-width="200">
        <template #default="{ row }">
          <div class="site-cell">
            <img
              v-if="row.logo && !logoErrors[row.id]"
              :src="row.logo"
              class="site-logo"
              :alt="row.name"
              @error="onLogoError(row.id)"
            />
            <div v-else class="site-logo-fallback">
              {{ row.name?.charAt(0) || '?' }}
            </div>
            <div>
              <a :href="row.url" target="_blank" rel="noopener noreferrer" class="site-name">{{ row.name }}</a>
              <div class="site-desc">{{ row.description }}</div>
            </div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="链接" prop="url" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="108" align="center" column-class-name="friend-link-source-col">
        <template #default="{ row }">
          <span
            class="source-pill"
            :class="row.applicantUserId ? 'source-pill--user' : 'source-pill--admin'"
          >
            {{ row.applicantUserId ? '用户申请' : '管理员' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="80" align="center" />
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button v-if="isPendingStatus(row.status)" text type="warning" size="small" @click="openAuditDialog(row)">审核</el-button>
          <el-button v-if="!isPendingStatus(row.status)" text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="!isPendingStatus(row.status)" text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑友链' : '新建友链'" width="520px" destroy-on-close>
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialogForm.name" maxlength="50" placeholder="网站名称" />
        </el-form-item>
        <el-form-item label="链接" prop="url">
          <el-input v-model="dialogForm.url" maxlength="200" placeholder="https://example.com" />
        </el-form-item>
        <el-form-item label="Logo">
          <el-input v-model="dialogForm.logo" maxlength="500" placeholder="Logo URL（可选）" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialogForm.description" type="textarea" :rows="2" maxlength="200" placeholder="网站描述（可选）" />
        </el-form-item>
        <el-form-item v-if="editingId" label="状态">
          <el-select v-model="dialogForm.status" class="full-width">
            <el-option label="正常" value="active" />
            <el-option label="停用" value="inactive" />
            <el-option label="失效" value="broken" />
            <el-option label="待审核" value="pending" />
            <el-option label="已拒绝" value="rejected" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialogForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 审核弹窗 -->
    <el-dialog v-model="auditDialogVisible" title="友链审核" width="560px" destroy-on-close>
      <template v-if="auditingLink">
        <!-- 友链信息卡片 -->
        <div class="audit-card">
          <div class="audit-card__header">
            <img
              v-if="auditingLink.logo && !logoErrors[auditingLink.id]"
              :src="auditingLink.logo"
              class="audit-card__logo"
              :alt="auditingLink.name"
              @error="onLogoError(auditingLink.id)"
            />
            <div v-else class="audit-card__logo-fallback">
              {{ auditingLink.name?.charAt(0) || '?' }}
            </div>
            <div class="audit-card__info">
              <div class="audit-card__name">{{ auditingLink.name }}</div>
              <a
                :href="auditingLink.url"
                target="_blank"
                rel="noopener noreferrer"
                class="audit-card__url"
              >
                {{ auditingLink.url }}
                <el-icon size="12"><TopRight /></el-icon>
              </a>
            </div>
          </div>
          <div v-if="auditingLink.description" class="audit-card__desc">
            {{ auditingLink.description }}
          </div>
          <div class="audit-card__meta">
            <span>申请时间：{{ formatTime(auditingLink.createTime) }}</span>
          </div>
          <div v-if="auditingLink.applicantUserId" class="audit-applicant">
            <div class="audit-applicant__header">申请用户信息</div>
            <div class="audit-applicant__body">
              <el-avatar v-if="auditingLink.applicantAvatar" :size="28" :src="auditingLink.applicantAvatar" />
              <el-avatar v-else :size="28">{{ auditingLink.applicantNickname?.charAt(0) || 'U' }}</el-avatar>
              <div class="audit-applicant__meta">
                <p><span>ID：</span>{{ auditingLink.applicantUserId }}</p>
                <p><span>昵称：</span>{{ auditingLink.applicantNickname || '-' }}</p>
                <p><span>邮箱：</span>{{ auditingLink.applicantEmail || '-' }}</p>
              </div>
            </div>
          </div>
        </div>

        <!-- 拒绝原因 -->
        <div class="audit-reason">
          <label class="audit-reason__label">拒绝原因（拒绝时填写，申请人可见）</label>
          <el-select v-model="rejectReason" placeholder="选择预设原因" clearable style="width: 100%; margin-bottom: 8px;">
            <el-option v-for="reason in flinkRejectReasonOptions" :key="reason" :label="reason" :value="reason" />
          </el-select>
          <el-input
            v-model="rejectReason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="拒绝时必填，可选择预设后再编辑"
          />
        </div>
      </template>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="auditing" @click="handleAuditAction('block')">拒绝并封禁</el-button>
        <el-button type="danger" :loading="auditing" @click="handleAuditAction('reject')">拒绝</el-button>
        <el-button type="success" :loading="auditing" @click="handleAuditAction('approve')">通过</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus, ArrowDown, TopRight } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { friendLinkApi, type FriendLinkVO } from '~/api/content/friendLink'
import { logsApi } from '~/api/system/logs'

const route = useRoute()
const loading = ref(false)
const links = ref<FriendLinkVO[]>([])
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const dialogFormRef = ref<FormInstance>()
const selectedIds = ref<number[]>([])
const checking = ref(false)
const statusFilter = ref('')
const filterKeyword = ref('')
const supportedStatusFilter = new Set(['', 'pending', 'active', 'inactive', 'broken', 'rejected'])
const applyEnabled = ref(false)
const switchLoading = ref(false)
const pendingFriendLinkCount = useState<number>('pendingFriendLinkCount', () => 0)
const tableHeight = useAdminTableHeight()

// 审核弹窗
const auditDialogVisible = ref(false)
const auditingLink = ref<FriendLinkVO | null>(null)
const rejectReason = ref('')
const flinkRejectReasonOptions = [
  '网站内容与友链定位不符',
  '网站无法访问或访问异常',
  '网站包含违规或敏感内容',
  '网站涉嫌虚假或诈骗信息',
  '友链信息不完整或格式不合规',
]
const auditing = ref(false)

// Logo 加载失败记录
const logoErrors = reactive<Record<number, boolean>>({})

const dialogForm = reactive({
  name: '',
  url: '',
  logo: '',
  description: '',
  status: 'active',
  sortOrder: 0,
})

const dialogRules: FormRules = {
  name: [{ required: true, message: '请输入网站名称', trigger: 'blur' }],
  url: [
    { required: true, message: '请输入网站链接', trigger: 'blur' },
    { pattern: /^https?:\/\//, message: '链接必须以 http:// 或 https:// 开头', trigger: 'blur' },
  ],
}

let searchTimer: ReturnType<typeof setTimeout> | null = null

const filteredLinks = computed(() => {
  const keyword = filterKeyword.value.trim().toLowerCase()
  return links.value.filter((link) => {
    const matchesStatus = !statusFilter.value
      || (statusFilter.value === 'pending' ? isPendingStatus(link.status) : link.status === statusFilter.value)
    const matchesKeyword = !keyword
      || link.name.toLowerCase().includes(keyword)
      || link.url.toLowerCase().includes(keyword)
    return matchesStatus && matchesKeyword
  })
})

const selectedRows = computed(() => links.value.filter(link => selectedIds.value.includes(link.id)))
const hasPendingSelected = computed(() => selectedRows.value.some(row => isPendingStatus(row.status)))

function debouncedSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    handleFilterChange()
  }, 300)
}

function handleFilterChange() {
  selectedIds.value = []
}

function isRowSelectable(row: FriendLinkVO) {
  return !isPendingStatus(row.status)
}

function friendLinkRowClassName({ row }: { row: FriendLinkVO }) {
  return isPendingStatus(row.status) ? 'pending-highlight-row' : ''
}

function isPendingStatus(status: string) {
  return status === 'pending' || status === 'pending_domain_review'
}

async function loadData() {
  loading.value = true
  Object.keys(logoErrors).forEach(k => delete logoErrors[+k])
  try {
    const res = await friendLinkApi.listAll()
    links.value = res.data
    pendingFriendLinkCount.value = res.data.filter(item => isPendingStatus(item.status)).length
    openFocusedAuditIfNeeded()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
    pendingFriendLinkCount.value = 0
  } finally {
    loading.value = false
  }
}

async function loadApplySwitch() {
  switchLoading.value = true
  try {
    const res = await friendLinkApi.getApplySwitch()
    applyEnabled.value = Boolean(res.data.enabled)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载申请开关失败')
  } finally {
    switchLoading.value = false
  }
}

async function handleApplySwitchChange(val: boolean | string | number) {
  switchLoading.value = true
  try {
    await friendLinkApi.setApplySwitch(Boolean(val))
    ElMessage.success(Boolean(val) ? '已开放友链申请入口' : '已关闭友链申请入口')
  } catch (e: unknown) {
    applyEnabled.value = !Boolean(val)
    ElMessage.error((e as Error).message || '设置申请开关失败')
  } finally {
    switchLoading.value = false
  }
}

function openDialog(row?: FriendLinkVO) {
  editingId.value = row?.id || null
  dialogForm.name = row?.name || ''
  dialogForm.url = row?.url || ''
  dialogForm.logo = row?.logo || ''
  dialogForm.description = row?.description || ''
  dialogForm.status = row?.status || 'active'
  if (row) {
    dialogForm.sortOrder = row.sortOrder ?? 0
  } else {
    const maxSort = links.value.reduce((max, item) => Math.max(max, item.sortOrder ?? 0), 0)
    dialogForm.sortOrder = maxSort + 1
  }
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!dialogFormRef.value) return
  const valid = await dialogFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const data = {
      name: dialogForm.name,
      url: dialogForm.url,
      logo: dialogForm.logo || undefined,
      description: dialogForm.description || undefined,
      status: dialogForm.status,
      sortOrder: dialogForm.sortOrder,
    }
    if (editingId.value) {
      await friendLinkApi.update(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await friendLinkApi.create(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: FriendLinkVO) {
  if (isPendingStatus(row.status)) {
    ElMessage.warning('待审核友链不允许删除，请先审核')
    return
  }

  await ElMessageBox.confirm(`确定删除友链「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await friendLinkApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  }
}

function statusLabel(s: string) {
  return { active: '正常', inactive: '停用', broken: '失效', pending: '待审核', pending_domain_review: '域名待审', rejected: '已拒绝' }[s] || s
}
function statusType(s: string) {
  return ({ active: 'success', inactive: 'info', broken: 'danger', pending: 'warning', pending_domain_review: 'warning', rejected: 'danger' }[s] || '') as '' | 'success' | 'info' | 'danger' | 'warning'
}
function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}
function onLogoError(id: number) {
  logoErrors[id] = true
}
function handleSelectionChange(rows: FriendLinkVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchCommand(command: string) {
  if (command === 'delete') handleBatchDelete()
  else handleBatchStatus(command)
}

async function handleBatchStatus(status: string) {
  const label = status === 'active' ? '启用' : '停用'
  await ElMessageBox.confirm(`确定批量${label}选中的 ${selectedIds.value.length} 条友链？`, '批量操作', { type: 'warning' })
  try {
    await friendLinkApi.batchUpdateStatus(selectedIds.value, status)
    ElMessage.success(`批量${label}成功`)
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleCheckLinks() {
  checking.value = true
  try {
    const res = await friendLinkApi.checkLinks()
    ElMessage.success(`检测完成，${res.data} 条状态变更`)
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '检测失败')
  } finally {
    checking.value = false
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.some(row => isPendingStatus(row.status))) {
    ElMessage.warning('已选中待审核友链，无法执行删除')
    return
  }

  await ElMessageBox.confirm(`确定批量删除选中的 ${selectedIds.value.length} 条友链？`, '批量删除', { type: 'warning' })
  try {
    await friendLinkApi.batchDelete(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

// 审核弹窗
function openAuditDialog(row: FriendLinkVO) {
  auditingLink.value = row
  rejectReason.value = ''
  auditDialogVisible.value = true
}

async function handleAuditAction(action: 'approve' | 'reject' | 'block') {
  if (!auditingLink.value) return

  const reason = rejectReason.value.trim()
  if (action === 'reject' || action === 'block') {
    if (!reason) {
      ElMessage.warning('拒绝时必须填写拒绝原因')
      return
    }
    if (reason.length > 500) {
      ElMessage.warning('拒绝原因不能超过500字')
      return
    }
  }

  auditing.value = true
  try {
    if (action === 'approve') {
      await friendLinkApi.approve(auditingLink.value.id)
      ElMessage.success('已通过')
    } else if (action === 'block') {
      await friendLinkApi.reject(auditingLink.value.id, reason)
      if (auditingLink.value.applicantUserId) {
        await logsApi.blockUser({ userId: auditingLink.value.applicantUserId, durationMinutes: 1440, reason: `友链审核封禁：${reason}` })
      }
      ElMessage.success('已拒绝并封禁24小时')
    } else {
      await friendLinkApi.reject(auditingLink.value.id, reason)
      ElMessage.success('已拒绝')
    }
    auditDialogVisible.value = false
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    auditing.value = false
  }
}

onMounted(() => {
  const queryStatus = Array.isArray(route.query.status) ? route.query.status[0] : route.query.status
  if (typeof queryStatus === 'string' && supportedStatusFilter.has(queryStatus)) {
    statusFilter.value = queryStatus
  }
  loadData()
  loadApplySwitch()
})

onUnmounted(() => {
  if (searchTimer) clearTimeout(searchTimer)
})

function getFocusId() {
  const raw = Array.isArray(route.query.focusId) ? route.query.focusId[0] : route.query.focusId
  const id = Number(raw)
  return Number.isFinite(id) && id > 0 ? id : null
}

function openFocusedAuditIfNeeded() {
  const focusId = getFocusId()
  if (!focusId || auditDialogVisible.value) return
  const target = links.value.find(row => row.id === focusId)
  if (target && isPendingStatus(target.status)) openAuditDialog(target)
}
</script>

<style scoped lang="scss">
.friend-link-page {
  .full-width { width: 100%; }
}

.apply-switch {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-blank);
}

.switch-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

:deep(.friend-link-source-col .cell) {
  display: flex;
  align-items: center;
  justify-content: center;
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

.source-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 56px;
  height: 22px;
  border-radius: 999px;
  padding: 0 8px;
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
  border: 1px solid transparent;
}

.source-pill--user {
  color: #b45309;
  border-color: #fcd34d;
  background: #fffbeb;
}

.source-pill--admin {
  color: var(--el-text-color-secondary);
  border-color: var(--el-border-color);
  background: var(--el-fill-color-lighter);
}

// 网站单元格
.site-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}
.site-logo {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  object-fit: cover;
  flex-shrink: 0;
}
.site-logo-fallback {
  width: 32px;
  height: 32px;
  border-radius: 4px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font-size: 14px;
  font-weight: 700;
}
.site-name {
  color: var(--el-color-primary);
  text-decoration: none;
  &:hover { text-decoration: underline; }
}
.site-desc {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

// 审核弹窗卡片
.audit-card {
  background: var(--el-fill-color-lighter);
  border-radius: 12px;
  padding: 20px;
  margin-bottom: 20px;

  &__header {
    display: flex;
    align-items: center;
    gap: 14px;
    margin-bottom: 12px;
  }
  &__logo {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    object-fit: cover;
    flex-shrink: 0;
  }
  &__logo-fallback {
    width: 48px;
    height: 48px;
    border-radius: 8px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--el-color-primary-light-8);
    color: var(--el-color-primary);
    font-size: 18px;
    font-weight: 700;
  }
  &__info {
    flex: 1;
    min-width: 0;
  }
  &__name {
    font-size: 16px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    margin-bottom: 4px;
  }
  &__url {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 13px;
    color: var(--el-color-primary);
    text-decoration: none;
    word-break: break-all;
    &:hover { text-decoration: underline; }
  }
  &__desc {
    font-size: 13px;
    color: var(--el-text-color-regular);
    line-height: 1.5;
    margin-bottom: 12px;
  }
  &__meta {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }
}

.audit-reason {
  &__label {
    display: block;
    font-size: 13px;
    font-weight: 500;
    color: var(--el-text-color-regular);
    margin-bottom: 8px;
  }
}

.audit-applicant {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--el-border-color);

  &__header {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    margin-bottom: 8px;
  }

  &__body {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  &__meta {
    p {
      margin: 0;
      font-size: 12px;
      color: var(--el-text-color-regular);
      line-height: 1.6;
    }

    span {
      color: var(--el-text-color-secondary);
    }
  }
}

// 弹窗圆角
:deep(.el-dialog) {
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  .el-dialog__header {
    padding: 20px 24px 12px;
    .el-dialog__title { font-weight: 700; font-size: 16px; }
  }
  .el-dialog__body { padding: 12px 24px 20px; }
}
</style>
