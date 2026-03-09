<template>
  <div class="user-page">
    <div class="page-header">
      <h2>用户管理</h2>
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
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Lock, ArrowDown } from '@element-plus/icons-vue'
import { userApi, type UserVO } from '~/api/user'

const loading = ref(false)
const records = ref<UserVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const filterRole = ref('')
const filterStatus = ref('')
const selectedIds = ref<number[]>([])

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

function formatTime(t: string | null) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

onMounted(loadData)

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped lang="scss">
.user-page {
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
}

// ========== 锁定行高亮 ==========
:deep(.el-table) .locked-row td.el-table__cell {
  background-color: rgba(245, 158, 11, 0.06) !important;
}
</style>
