<template>
  <div class="risk-control-page">
    <div class="page-header">
      <h2>风控管理</h2>
      <div class="filter-bar">
        <el-input
          v-model="filters.keyword"
          clearable
          placeholder="搜索 IP / 邮箱 / 用户ID"
          style="width: 240px"
          @input="debouncedSearch"
          @clear="handleFilterChange"
        />
        <el-select v-model="filters.type" clearable placeholder="处置类型" style="width: 140px" @change="handleFilterChange">
          <el-option label="IP 封禁" value="IP" />
          <el-option label="用户封禁" value="USER" />
          <el-option label="禁言" value="MUTE" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="状态" style="width: 130px" @change="handleFilterChange">
          <el-option label="生效中" value="active" />
          <el-option label="已过期" value="expired" />
        </el-select>
      </div>
      <el-button type="danger" @click="openBlockDialog()">新增封禁</el-button>
    </div>

    <el-table v-loading="loading" :data="riskRows" stripe :height="tableHeight" class="risk-table">
      <el-table-column prop="createTime" label="创建时间" min-width="150">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small" :type="getTypeTag(row.type)">{{ getTypeLabel(row.type) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="subject" label="处置对象" min-width="210" show-overflow-tooltip />
      <el-table-column prop="reason" label="原因" min-width="240" show-overflow-tooltip />
      <el-table-column label="状态" min-width="150">
        <template #default="{ row }">
          <el-space :size="6">
            <el-tag size="small" :type="row.active ? 'danger' : 'info'">{{ row.active ? '生效中' : '已过期' }}</el-tag>
            <el-tag v-if="row.permanent" size="small" type="danger">永久</el-tag>
          </el-space>
        </template>
      </el-table-column>
      <el-table-column label="到期时间" min-width="170">
        <template #default="{ row }">{{ row.permanent ? '永久' : formatTime(row.expireTime) }}</template>
      </el-table-column>
      <el-table-column label="剩余" min-width="120">
        <template #default="{ row }">{{ formatRemainingSeconds(row.remainingSeconds, row.permanent) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-space>
            <el-button v-if="!row.permanent" text type="warning" size="small" @click="openAdjustDialog(row)">调整时效</el-button>
            <el-button text type="success" size="small" @click="handleRelease(row)">{{ row.type === 'MUTE' ? '解除禁言' : '解除封禁' }}</el-button>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pager.pageNum"
        v-model:page-size="pager.pageSize"
        :total="pager.total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        size="small"
        @size-change="reload"
        @current-change="loadData"
      />
    </div>

    <el-dialog v-model="blockDialogVisible" :title="editingRow ? '调整时效' : '新增封禁'" width="560px" destroy-on-close>
      <el-form label-width="92px">
        <el-form-item label="类型">
          <el-radio-group v-model="blockForm.type" :disabled="Boolean(editingRow)">
            <el-radio-button value="IP">IP 封禁</el-radio-button>
            <el-radio-button value="USER">用户封禁</el-radio-button>
            <el-radio-button value="MUTE">禁言</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="blockForm.type === 'IP'" label="IP 地址">
          <el-input v-model="blockForm.ip" :disabled="Boolean(editingRow)" placeholder="请输入 IP 地址" />
        </el-form-item>
        <el-form-item v-else label="邮箱账号">
          <el-input v-model="blockForm.email" :disabled="Boolean(editingRow)" placeholder="请输入用户邮箱" />
        </el-form-item>
        <el-form-item label="方式">
          <el-radio-group v-model="blockForm.permanent">
            <el-radio :value="false">限时</el-radio>
            <el-radio :value="true">永久</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="!blockForm.permanent" label="有效期">
          <el-select v-model="blockForm.durationMinutes" style="width: 100%">
            <el-option v-for="option in durationOptions" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因">
          <el-select v-model="blockForm.reason" placeholder="选择预设原因" clearable style="width: 100%; margin-bottom: 8px">
            <el-option v-for="reason in reasonOptions" :key="reason" :label="reason" :value="reason" />
          </el-select>
          <el-input v-model="blockForm.reason" type="textarea" :rows="3" maxlength="200" show-word-limit placeholder="处置原因必填，可选择预设后编辑" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="blockDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="submitting" @click="submitBlock">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { logsApi, type RiskControlVO } from '~/api/system/logs'
import { userApi } from '~/api/auth/user'

type RiskType = 'IP' | 'USER' | 'MUTE'
type RiskStatus = '' | 'active' | 'expired'

interface RiskRow {
  id: number | string
  type: RiskType
  targetValue: string
  userId: number | null
  subject: string
  reason: string | null
  expireTime: string | null
  remainingSeconds: number
  permanent: boolean
  active: boolean
  createTime: string | null
}

const tableHeight = useAdminTableHeight()
const loading = ref(false)
const submitting = ref(false)
const riskRows = ref<RiskRow[]>([])
const pager = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const filters = reactive<{ type: '' | RiskType; keyword: string; status: RiskStatus }>({ type: '', keyword: '', status: '' })
const blockDialogVisible = ref(false)
const editingRow = ref<RiskRow | null>(null)
const blockForm = reactive({ type: 'IP' as RiskType, ip: '', email: '', permanent: false, durationMinutes: 1440, reason: '' })

const durationOptions = [
  { label: '1 小时', value: 60 },
  { label: '24 小时', value: 1440 },
  { label: '7 天', value: 10080 },
  { label: '30 天', value: 43200 },
]

const reasonOptions = [
  '恶意请求或攻击行为',
  '发布违规内容',
  '垃圾内容或刷屏',
  '骚扰用户或破坏社区秩序',
  '严重违反站点规则',
]

function normalizeText(value: unknown): string {
  return typeof value === 'string' ? value.trim() : ''
}

function isCancelAction(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}

function toRiskRow(row: RiskControlVO): RiskRow {
  return {
    id: row.id,
    type: row.type,
    targetValue: row.targetValue,
    userId: row.userId,
    subject: row.subject || row.targetValue,
    reason: row.reason,
    expireTime: row.expireTime,
    remainingSeconds: row.remainingSeconds,
    permanent: row.permanent,
    active: row.active,
    createTime: row.createTime,
  }
}

async function loadData() {
  loading.value = true
  try {
    const keyword = normalizeText(filters.keyword) || undefined
    const res = await logsApi.getRiskControls({
      pageNum: pager.pageNum,
      pageSize: pager.pageSize,
      type: filters.type || undefined,
      keyword,
      status: filters.status || undefined,
    })
    riskRows.value = (res.data.records || []).map(toRiskRow)
    pager.total = res.data.total || 0
  } catch (error: unknown) {
    ElMessage.error((error as Error).message || '加载风控列表失败')
  } finally {
    loading.value = false
  }
}

function reload() {
  pager.pageNum = 1
  loadData()
}

function handleFilterChange() {
  if (keywordTimer) clearTimeout(keywordTimer)
  reload()
}

let keywordTimer: ReturnType<typeof setTimeout> | null = null
function debouncedSearch() {
  if (keywordTimer) clearTimeout(keywordTimer)
  keywordTimer = setTimeout(reload, 300)
}

function openBlockDialog(row?: RiskRow) {
  editingRow.value = row || null
  blockForm.type = row?.type || 'IP'
  blockForm.ip = row?.type === 'IP' ? row.targetValue : ''
  blockForm.email = row && row.type !== 'IP' ? row.subject : ''
  blockForm.permanent = row?.permanent || false
  blockForm.durationMinutes = 1440
  blockForm.reason = row?.reason || ''
  blockDialogVisible.value = true
}

function openAdjustDialog(row: RiskRow) {
  openBlockDialog(row)
}

async function submitBlock() {
  const reason = normalizeText(blockForm.reason)
  if (!reason) {
    ElMessage.warning('处置原因不能为空')
    return
  }
  if (blockForm.type === 'IP' && !normalizeText(blockForm.ip)) {
    ElMessage.warning('IP 地址不能为空')
    return
  }
  if (blockForm.type !== 'IP' && !normalizeText(blockForm.email)) {
    ElMessage.warning('邮箱账号不能为空')
    return
  }

  const subject = blockForm.type === 'IP' ? normalizeText(blockForm.ip) : normalizeText(blockForm.email)
  const action = editingRow.value ? '调整时效' : '新增封禁'
  try {
    await ElMessageBox.confirm(`确认对 ${subject} 执行${action}？`, '高风险操作确认', {
      type: 'warning',
      confirmButtonText: '确认',
      cancelButtonText: '取消',
    })
  } catch (error: unknown) {
    if (isCancelAction(error)) return
    ElMessage.error('确认失败')
    return
  }

  submitting.value = true
  try {
    const payload = {
      permanent: blockForm.permanent,
      durationMinutes: blockForm.permanent ? undefined : blockForm.durationMinutes,
      reason,
    }
    if (blockForm.type === 'IP') {
      await logsApi.blockIp({ ip: subject, ...payload })
    } else if (blockForm.type === 'USER') {
      if (editingRow.value?.userId) {
        await logsApi.blockUser({ userId: editingRow.value.userId, ...payload })
      } else {
        await logsApi.blockUser({ email: subject, ...payload })
      }
    } else if (editingRow.value?.userId) {
      await userApi.mute(editingRow.value.userId, { permanent: blockForm.permanent, minutes: blockForm.permanent ? undefined : blockForm.durationMinutes, reason })
    } else {
      await userApi.muteByEmail({ email: subject, permanent: blockForm.permanent, minutes: blockForm.permanent ? undefined : blockForm.durationMinutes, reason })
    }
    ElMessage.success(editingRow.value ? '时效已调整' : '处置成功')
    blockDialogVisible.value = false
    reload()
  } catch (error: unknown) {
    ElMessage.error((error as Error).message || '处置失败')
  } finally {
    submitting.value = false
  }
}

async function handleRelease(row: RiskRow) {
  try {
    await ElMessageBox.confirm(`确认解除 ${row.subject} 的${getTypeLabel(row.type)}？`, '解除确认', { type: 'warning' })
  } catch (error: unknown) {
    if (isCancelAction(error)) return
    ElMessage.error('解除确认失败')
    return
  }

  try {
    await logsApi.unblockBlacklist(Number(row.id))
    ElMessage.success('已解除')
    reload()
  } catch (error: unknown) {
    ElMessage.error((error as Error).message || '解除失败')
  }
}

function getTypeLabel(type: RiskType): string {
  if (type === 'IP') return 'IP 封禁'
  if (type === 'USER') return '用户封禁'
  return '禁言'
}

function getTypeTag(type: RiskType): 'danger' | 'warning' | 'info' {
  if (type === 'IP') return 'danger'
  if (type === 'USER') return 'warning'
  return 'info'
}

function formatTime(value?: string | null): string {
  return value ? value.replace('T', ' ').slice(0, 16) : '-'
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

onMounted(loadData)
onUnmounted(() => {
  if (keywordTimer) clearTimeout(keywordTimer)
})
</script>

<style scoped lang="scss">
.risk-control-page {
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

.risk-table {
  width: 100%;
}

.pagination-wrap {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}
</style>
