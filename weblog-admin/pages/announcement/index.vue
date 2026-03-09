<template>
  <div class="ann-page">
    <div class="page-header">
      <h2>公告管理</h2>
      <div class="filter-bar">
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px" @change="handleFilterChange">
          <el-option label="草稿" value="draft" />
          <el-option label="已发布" value="published" />
          <el-option label="已归档" value="archived" />
        </el-select>
        <el-select v-model="filterType" placeholder="类型" clearable style="width: 120px" @change="handleFilterChange">
          <el-option label="弹窗" value="popup" />
          <el-option label="横幅" value="banner" />
          <el-option label="侧边栏" value="sidebar" />
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
                <el-dropdown-item command="published">发布</el-dropdown-item>
                <el-dropdown-item command="archived">归档</el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建公告
        </el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe height="560" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="40" align="center" />
      <el-table-column label="#" type="index" width="50" align="center" />
      <el-table-column label="标题" prop="title" min-width="180" show-overflow-tooltip />
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">{{ typeLabel(row.type) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === 'published' ? 'success' : row.status === 'draft' ? 'warning' : 'info'" size="small">
            {{ row.status === 'published' ? '已发布' : row.status === 'draft' ? '草稿' : '已归档' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="优先级" prop="priority" width="80" align="center" />
      <el-table-column label="可关闭" width="80" align="center">
        <template #default="{ row }">{{ row.isClosable ? '是' : '否' }}</template>
      </el-table-column>
      <el-table-column label="有效期" min-width="240">
        <template #default="{ row }">
          <div v-if="row.startTime || row.endTime" class="time-cell">
            <span class="time-range-text">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</span>
            <span v-if="row.endTime" :class="['remaining-tag', remainingLevel(row.endTime)]">
              {{ remainingText(row.endTime) }}
            </span>
          </div>
          <span v-else class="muted">永久</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'published'" text type="success" size="small" @click="handleStatus(row, 'published')">发布</el-button>
          <el-button v-if="row.status === 'published'" text type="info" size="small" @click="handleStatus(row, 'archived')">归档</el-button>
          <el-button text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" background size="small" @current-change="loadData" @size-change="handleSizeChange" />
    </div>

    <!-- 公告弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑公告' : '新建公告'" width="560px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="100" clearable /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.type" style="width:100%" clearable>
            <el-option label="弹窗" value="popup" /><el-option label="横幅" value="banner" /><el-option label="侧边栏" value="sidebar" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="4" clearable /></el-form-item>
        <el-form-item label="优先级"><el-input-number v-model="form.priority" :min="0" :max="100" /></el-form-item>
        <el-form-item label="可关闭"><el-switch v-model="form.isClosable" /></el-form-item>
        <el-form-item label="有效期">
          <div class="time-section">
            <el-checkbox v-model="isPermanent">永久有效</el-checkbox>
            <template v-if="!isPermanent">
              <div class="preset-btns">
                <el-button v-for="p in presets" :key="p.label" size="small" @click="applyPreset(p.days)">{{ p.label }}</el-button>
              </div>
              <div class="time-range">
                <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始时间" style="width:100%" clearable format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
                <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束时间" style="width:100%" clearable format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
              </div>
            </template>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { announcementApi, type AnnouncementVO } from '~/api/announcement'

const loading = ref(false)
const records = ref<AnnouncementVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const filterType = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
const isPermanent = ref(true)

const form = reactive({
  title: '', type: 'banner', content: '', priority: 0, isClosable: true,
  startTime: null as string | null, endTime: null as string | null,
})

const presets = [
  { label: '7天', days: 7 },
  { label: '30天', days: 30 },
  { label: '90天', days: 90 },
  { label: '半年', days: 180 },
  { label: '1年', days: 365 },
]

function typeLabel(t: string) { return { popup: '弹窗', banner: '横幅', sidebar: '侧边栏' }[t] || t }
function fmt(t: string | null) { return t ? t.replace('T', ' ').slice(0, 16) : '' }

/** 计算剩余时间文本 */
function remainingText(endTime: string): string {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return '已过期'
  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  if (days > 30) return `剩${Math.floor(days / 30)}个月`
  if (days > 0) return `剩${days}天`
  if (hours > 0) return `剩${hours}小时`
  return `剩${Math.max(1, Math.floor((diff % 3600000) / 60000))}分钟`
}

/** 根据剩余时长返回样式级别 */
function remainingLevel(endTime: string): string {
  const diff = new Date(endTime).getTime() - Date.now()
  if (diff <= 0) return 'expired'
  if (diff < 86400000) return 'urgent'       // < 1天
  if (diff < 3 * 86400000) return 'warning'  // < 3天
  return 'normal'
}

function handleFilterChange() { pageNum.value = 1; loadData() }
function handleSizeChange(size: number) { pageSize.value = size; pageNum.value = 1; loadData() }
function onSelectionChange(rows: AnnouncementVO[]) { selectedIds.value = rows.map(r => r.id) }

function applyPreset(days: number) {
  const now = new Date()
  form.startTime = formatLocal(now)
  const end = new Date(now.getTime() + days * 86400000)
  form.endTime = formatLocal(end)
}

function formatLocal(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

async function loadData() {
  loading.value = true
  try {
    const res = await announcementApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, status: filterStatus.value || undefined, type: filterType.value || undefined })
    records.value = res.data.records; total.value = res.data.total
  } catch (e: unknown) { ElMessage.error((e as Error).message || '加载失败') } finally { loading.value = false }
}

function openDialog(row?: AnnouncementVO) {
  editingId.value = row?.id || null
  form.title = row?.title || ''; form.type = row?.type || 'banner'; form.content = row?.content || ''
  form.priority = row?.priority ?? 0; form.isClosable = row?.isClosable ?? true
  form.startTime = row?.startTime || null; form.endTime = row?.endTime || null
  isPermanent.value = !row?.startTime && !row?.endTime
  dialogVisible.value = true
}

async function handleSubmit() {
  submitting.value = true
  try {
    const data = { ...form }
    if (isPermanent.value) {
      data.startTime = null; data.endTime = null
    } else {
      // el-date-picker 返回 Date 对象，预设按钮返回字符串，统一格式化为 ISO
      if (data.startTime) data.startTime = toISO(data.startTime)
      if (data.endTime) data.endTime = toISO(data.endTime)
    }
    if (editingId.value) { await announcementApi.update(editingId.value, data); ElMessage.success('更新成功') }
    else { await announcementApi.create(data); ElMessage.success('创建成功') }
    dialogVisible.value = false; loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') } finally { submitting.value = false }
}

/** 将 Date 或字符串统一转为 yyyy-MM-ddTHH:mm:ss 格式 */
function toISO(v: string | Date | null): string | null {
  if (!v) return null
  const d = v instanceof Date ? v : new Date(v)
  if (isNaN(d.getTime())) return null
  return formatLocal(d)
}

async function handleStatus(row: AnnouncementVO, status: string) {
  // popup 类型发布时提示互斥
  if (status === 'published' && row.type === 'popup') {
    const hasOther = records.value.some(r => r.id !== row.id && r.type === 'popup' && r.status === 'published')
    if (hasOther) {
      await ElMessageBox.confirm('发布此弹窗公告后，其他已发布的弹窗公告将自动归档（同时只允许一条弹窗公告生效）', '提示', { type: 'warning' })
    }
  }
  try { await announcementApi.updateStatus(row.id, status); ElMessage.success('操作成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message) }
}

async function handleDelete(row: AnnouncementVO) {
  await ElMessageBox.confirm(`确定删除公告「${row.title}」？`, '提示', { type: 'warning' })
  try { await announcementApi.delete(row.id); ElMessage.success('删除成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message) }
}

async function handleBatchCommand(command: string) {
  if (command === 'delete') handleBatchDelete()
  else handleBatchStatus(command)
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条公告？`, '提示', { type: 'warning' })
  try { await announcementApi.batchDelete(selectedIds.value); ElMessage.success('删除成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
}

async function handleBatchStatus(status: string) {
  const label = status === 'published' ? '发布' : '归档'
  await ElMessageBox.confirm(`确定将选中的 ${selectedIds.value.length} 条公告${label}？`, '提示', { type: 'info' })
  try { await announcementApi.batchUpdateStatus(selectedIds.value, status); ElMessage.success('操作成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.ann-page {
  .muted {
    color: var(--el-text-color-regular);
    font-size: 13px;
  }
  .time-cell {
    display: flex;
    align-items: center;
    gap: 8px;
    .time-range-text { font-size: 12px; color: var(--el-text-color-regular); }
  }
  .remaining-tag {
    display: inline-block;
    font-size: 11px;
    font-weight: 600;
    padding: 0 6px;
    height: 18px;
    line-height: 18px;
    border-radius: 4px;
    width: fit-content;
    &.normal { background: var(--el-color-success-light-9); color: var(--el-color-success); }
    &.warning { background: var(--el-color-warning-light-9); color: var(--el-color-warning); }
    &.urgent { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
    &.expired { background: var(--el-fill-color-light); color: var(--el-text-color-disabled); text-decoration: line-through; }
  }
}

// ========== 弹窗时间区域 ==========
.time-section {
  width: 100%;
  .preset-btns {
    display: flex;
    gap: 6px;
    margin: 8px 0;
    flex-wrap: wrap;
  }
  .time-range {
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
}

// ========== Switch ==========
:deep(.el-switch) {
  --el-switch-on-color: var(--el-color-primary);
  --el-switch-off-color: var(--el-fill-color-darker);
  height: 20px;
}
:deep(.el-switch .el-switch__core) {
  height: 20px;
  min-width: 36px;
  border-radius: 10px;
  border: none;
}
:deep(.el-switch .el-switch__core .el-switch__action) {
  width: 16px;
  height: 16px;
}

// ========== 弹窗 ==========
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
