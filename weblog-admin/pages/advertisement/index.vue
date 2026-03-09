<template>
  <div class="ad-page">
    <div class="page-header">
      <h2>广告管理</h2>
      <div class="filter-bar">
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 130px" @change="handleFilterChange">
          <el-option label="待审核" value="pending" />
          <el-option label="投放中" value="active" />
          <el-option label="已拒绝" value="rejected" />
          <el-option label="已过期" value="expired" />
        </el-select>
        <el-select v-model="filterPosition" placeholder="位置" clearable style="width: 130px" @change="handleFilterChange">
          <el-option label="顶部横幅" value="top" />
          <el-option label="侧边栏" value="sidebar" />
          <el-option label="文章中部" value="middle" />
          <el-option label="页面底部" value="bottom" />
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
                <el-dropdown-item command="active">上架</el-dropdown-item>
                <el-dropdown-item command="expired">下架</el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-tooltip content="控制用户端广告申请入口" placement="top">
          <div class="apply-switch">
            <span class="switch-label">申请入口</span>
            <el-switch v-model="applyEnabled" :loading="switchLoading" @change="handleApplySwitchChange" />
          </div>
        </el-tooltip>
        <el-button @click="showTrashBox = true"><el-icon><Delete /></el-icon> 回收站</el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建广告
        </el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe height="560"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="40" align="center" />
      <el-table-column type="index" label="#" width="50" align="center" />
      <el-table-column label="标题" prop="title" min-width="140" show-overflow-tooltip />
      <el-table-column label="类型" width="70" align="center">
        <template #default="{ row }">{{ row.type === 'image' ? '图片' : '代码' }}</template>
      </el-table-column>
      <el-table-column label="位置" width="100" align="center">
        <template #default="{ row }">
          <el-tag size="small">{{ posLabel(row.position) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="投放时间" min-width="240">
        <template #default="{ row }">
          <div v-if="!row.startTime && !row.endTime" class="time-permanent">永久</div>
          <div v-else class="time-cell">
            <span class="time-range-text">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) || '永久' }}</span>
            <span v-if="row.endTime" :class="['remaining-tag', remainingLevel(row.endTime)]">
              {{ remainingText(row.endTime) }}
            </span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="权重" prop="weight" width="60" align="center" />
      <el-table-column label="点击" prop="clickCount" width="60" align="center" />
      <el-table-column label="操作" width="280" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'pending'" text type="success" size="small" @click="handleStatus(row, 'active')">通过</el-button>
          <el-button v-if="row.status === 'pending'" text type="warning" size="small" @click="handleStatus(row, 'rejected')">拒绝</el-button>
          <el-button v-if="row.status === 'active'" text type="info" size="small" @click="handleStatus(row, 'expired')">下架</el-button>
          <el-button v-if="row.status !== 'active' && row.status !== 'pending'" text type="success" size="small" @click="handleStatus(row, 'active')">上架</el-button>
          <el-button text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="pageNum" v-model:page-size="pageSize" :total="total"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next"
        background size="small"
        @current-change="loadData" @size-change="handleSizeChange" />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑广告' : '新建广告'" width="620px" destroy-on-close>
      <div class="dialog-scroll-body">
        <el-form :model="form" label-width="80px">
          <el-form-item label="标题"><el-input v-model="form.title" maxlength="100" clearable /></el-form-item>
          <el-form-item label="类型">
            <el-select v-model="form.type" style="width:100%">
              <el-option label="图片" value="image" /><el-option label="代码" value="code" />
            </el-select>
          </el-form-item>
          <el-form-item label="位置">
            <div class="position-selector">
              <el-select v-model="form.position" style="width: 160px">
                <el-option label="顶部横幅" value="top" />
                <el-option label="侧边栏" value="sidebar" />
                <el-option label="文章中部" value="middle" />
                <el-option label="页面底部" value="bottom" />
              </el-select>
              <div class="pos-mini-preview">
                <div class="pos-block" :class="{ active: form.position === 'top' }">顶部</div>
                <div class="pos-body">
                  <div class="pos-main">
                    <div class="pos-block small" :class="{ active: form.position === 'middle' }">中部</div>
                  </div>
                  <div class="pos-block sidebar-block" :class="{ active: form.position === 'sidebar' }">侧栏</div>
                </div>
                <div class="pos-block" :class="{ active: form.position === 'bottom' }">底部</div>
              </div>
            </div>
          </el-form-item>
          <el-form-item :label="form.type === 'image' ? '图片URL' : 'HTML'">
            <el-input v-model="form.content" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="跳转链接"><el-input v-model="form.linkUrl" clearable /></el-form-item>
          <el-form-item label="投放时间">
            <div class="time-config">
              <el-checkbox v-model="form.permanent">永久投放</el-checkbox>
              <template v-if="!form.permanent">
                <div class="time-presets">
                  <el-button v-for="p in timePresets" :key="p.label" size="small" @click="applyPreset(p)">{{ p.label }}</el-button>
                </div>
                <div class="time-pickers">
                  <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始时间"
                    format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width: 210px" />
                  <span class="time-sep">~</span>
                  <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束时间"
                    format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" style="width: 210px" />
                </div>
              </template>
            </div>
          </el-form-item>
          <el-form-item label="权重"><el-input-number v-model="form.weight" :min="0" :max="100" /></el-form-item>
        </el-form>
      </div>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 回收站弹窗 -->
    <el-dialog v-model="showTrashBox" title="回收站" width="900px" destroy-on-close>
      <div class="trash-toolbar">
        <el-input v-model="trashKeyword" placeholder="搜索标题" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="trash-actions">
          <el-button type="primary" size="small" :disabled="selectedTrashIds.length === 0" @click="handleBatchRestore">
            恢复{{ selectedTrashIds.length > 0 ? ` (${selectedTrashIds.length})` : '' }}
          </el-button>
          <el-button type="danger" size="small" :disabled="selectedTrashIds.length === 0" @click="handleBatchPermanentDelete">
            彻底删除{{ selectedTrashIds.length > 0 ? ` (${selectedTrashIds.length})` : '' }}
          </el-button>
          <el-button type="danger" size="small" plain @click="handleClearTrash" :disabled="trashRecords.length === 0">
            清空回收站
          </el-button>
        </div>
      </div>
      <div v-loading="trashLoading">
        <el-table :data="trashRecords" stripe max-height="400px" v-if="trashRecords.length" row-key="id"
          @selection-change="onTrashSelectionChange">
          <el-table-column type="selection" width="40" />
          <el-table-column label="#" width="55" align="center">
            <template #default="{ $index }">{{ (trashPagination.pageNum - 1) * trashPagination.pageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="标题" prop="title" min-width="160" show-overflow-tooltip />
          <el-table-column label="位置" width="100" align="center">
            <template #default="{ row }">
              <el-tag size="small">{{ posLabel(row.position) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="点击" prop="clickCount" width="60" align="center" />
          <el-table-column label="删除时间" width="160">
            <template #default="{ row }">{{ fmt(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="160">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="handleRestoreOne(row)">恢复</el-button>
              <el-button text type="danger" size="small" @click="handlePermanentDeleteOne(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="回收站为空" />
      </div>
      <div class="trash-pagination" v-if="trashPagination.total > trashPagination.pageSize">
        <el-pagination v-model:current-page="trashPagination.pageNum" v-model:page-size="trashPagination.pageSize"
          :total="trashPagination.total" :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next" background size="small"
          @size-change="loadTrash" @current-change="loadTrash" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus, Delete, Search, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { advertisementApi, type AdvertisementVO } from '~/api/advertisement'

const loading = ref(false)
const records = ref<AdvertisementVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const filterPosition = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const selectedIds = ref<number[]>([])
const applyEnabled = ref(false)
const switchLoading = ref(false)

const form = reactive({
  title: '', type: 'image', content: '', linkUrl: '', position: 'sidebar',
  weight: 1,
  permanent: true, startTime: '' as string, endTime: '' as string,
})

// 时间预设
const timePresets = [
  { label: '7天', days: 7 },
  { label: '30天', days: 30 },
  { label: '90天', days: 90 },
  { label: '半年', days: 180 },
  { label: '1年', days: 365 },
]

function applyPreset(p: { days: number }) {
  const now = new Date()
  form.startTime = formatISO(now)
  const end = new Date(now.getTime() + p.days * 86400000)
  form.endTime = formatISO(end)
}

function formatISO(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:00`
}

function posLabel(p: string) { return { top: '顶部横幅', sidebar: '侧边栏', middle: '文章中部', bottom: '页面底部' }[p] || p }
function statusLabel(s: string) { return { pending: '待审核', approved: '已通过', rejected: '已拒绝', active: '投放中', expired: '已过期' }[s] || s }
function statusType(s: string) { return ({ pending: 'warning', active: 'success', rejected: 'danger', expired: 'info' }[s] || 'info') as 'warning' | 'success' | 'danger' | 'info' }
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
  if (diff < 86400000) return 'urgent'
  if (diff < 3 * 86400000) return 'warning'
  return 'normal'
}

function handleFilterChange() { pageNum.value = 1; loadData() }
function handleSizeChange() { pageNum.value = 1; loadData() }
function handleSelectionChange(rows: AdvertisementVO[]) { selectedIds.value = rows.map(r => r.id) }

async function loadData() {
  loading.value = true
  try {
    const res = await advertisementApi.list({ pageNum: pageNum.value, pageSize: pageSize.value, status: filterStatus.value || undefined, position: filterPosition.value || undefined })
    records.value = res.data.records; total.value = res.data.total
  } catch (e: unknown) { ElMessage.error((e as Error).message || '加载失败') } finally { loading.value = false }
}

async function loadApplySwitch() {
  try {
    const res = await advertisementApi.getApplySwitch()
    applyEnabled.value = res.data.enabled
  } catch { /* 忽略 */ }
}

async function handleApplySwitchChange(val: boolean | string | number) {
  switchLoading.value = true
  try {
    await advertisementApi.setApplySwitch(Boolean(val))
    ElMessage.success(val ? '申请入口已开放' : '申请入口已关闭')
  } catch (e: unknown) {
    applyEnabled.value = !Boolean(val)
    ElMessage.error((e as Error).message || '操作失败')
  } finally { switchLoading.value = false }
}

function openDialog(row?: AdvertisementVO) {
  editingId.value = row?.id || null
  form.title = row?.title || ''; form.type = row?.type || 'image'; form.content = row?.content || ''
  form.linkUrl = row?.linkUrl || ''; form.position = row?.position || 'sidebar'
  form.weight = row?.weight ?? 1
  form.permanent = !row?.startTime && !row?.endTime
  form.startTime = row?.startTime || ''; form.endTime = row?.endTime || ''
  dialogVisible.value = true
}

async function handleSubmit() {
  submitting.value = true
  try {
    const data: Record<string, unknown> = {
      title: form.title, type: form.type, content: form.content,
      linkUrl: form.linkUrl, position: form.position,
      weight: form.weight,
      startTime: form.permanent ? null : (form.startTime || null),
      endTime: form.permanent ? null : (form.endTime || null),
    }
    if (editingId.value) { await advertisementApi.update(editingId.value, data); ElMessage.success('更新成功') }
    else { await advertisementApi.create(data); ElMessage.success('创建成功') }
    dialogVisible.value = false; loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') } finally { submitting.value = false }
}

async function handleStatus(row: AdvertisementVO, status: string) {
  try { await advertisementApi.updateStatus(row.id, status); ElMessage.success('操作成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message) }
}

async function handleDelete(row: AdvertisementVO) {
  await ElMessageBox.confirm(`确定删除广告「${row.title}」？`, '提示', { type: 'warning' })
  try { await advertisementApi.delete(row.id); ElMessage.success('删除成功'); loadData() }
  catch (e: unknown) { ElMessage.error((e as Error).message) }
}

async function handleBatchCommand(command: string) {
  if (command === 'delete') handleBatchDelete()
  else handleBatchStatus(command)
}

async function handleBatchStatus(status: string) {
  const label = status === 'active' ? '上架' : '下架'
  await ElMessageBox.confirm(`确定批量${label}选中的 ${selectedIds.value.length} 条广告？`, '批量操作', { type: 'warning' })
  try {
    await advertisementApi.batchUpdateStatus(selectedIds.value, status)
    ElMessage.success(`批量${label}成功`); selectedIds.value = []; loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定批量删除选中的 ${selectedIds.value.length} 条广告？`, '批量删除', { type: 'warning' })
  try {
    await advertisementApi.batchDelete(selectedIds.value)
    ElMessage.success('批量删除成功'); selectedIds.value = []; loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

// ========== 回收站 ==========
const showTrashBox = ref(false)
const trashRecords = ref<AdvertisementVO[]>([])
const trashLoading = ref(false)
const selectedTrashIds = ref<number[]>([])
const trashKeyword = ref('')
const trashPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

function onTrashSelectionChange(rows: AdvertisementVO[]) {
  selectedTrashIds.value = rows.map(r => r.id)
}

watch(showTrashBox, (val) => { if (val) { trashKeyword.value = ''; trashPagination.pageNum = 1; loadTrash() } })

let trashDebounce: ReturnType<typeof setTimeout> | null = null
watch(() => trashKeyword.value, () => {
  if (trashDebounce) clearTimeout(trashDebounce)
  trashDebounce = setTimeout(() => { trashPagination.pageNum = 1; loadTrash() }, 300)
})

async function loadTrash() {
  trashLoading.value = true
  try {
    const res = await advertisementApi.trashPage({
      pageNum: trashPagination.pageNum,
      pageSize: trashPagination.pageSize,
      keyword: trashKeyword.value || undefined,
    })
    trashRecords.value = res.data.records
    trashPagination.total = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally { trashLoading.value = false }
}

async function handleBatchRestore() {
  await ElMessageBox.confirm(`确定恢复选中的 ${selectedTrashIds.value.length} 条广告？`, '恢复确认', { type: 'info' })
  try {
    await advertisementApi.batchRestore(selectedTrashIds.value)
    ElMessage.success('恢复成功'); selectedTrashIds.value = []; loadTrash(); loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '恢复失败') }
}

async function handleBatchPermanentDelete() {
  await ElMessageBox.confirm(`确定彻底删除选中的 ${selectedTrashIds.value.length} 条广告？此操作不可恢复`, '彻底删除', { type: 'warning' })
  try {
    await advertisementApi.batchPermanentDelete(selectedTrashIds.value)
    ElMessage.success('彻底删除成功'); selectedTrashIds.value = []; loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

async function handleRestoreOne(row: AdvertisementVO) {
  try {
    await advertisementApi.batchRestore([row.id])
    ElMessage.success('恢复成功'); loadTrash(); loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '恢复失败') }
}

async function handlePermanentDeleteOne(row: AdvertisementVO) {
  await ElMessageBox.confirm(`确定彻底删除广告「${row.title}」？此操作不可恢复`, '彻底删除', { type: 'warning' })
  try {
    await advertisementApi.batchPermanentDelete([row.id])
    ElMessage.success('彻底删除成功'); loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

async function handleClearTrash() {
  await ElMessageBox.confirm('确定清空回收站？所有已删除广告将被永久删除，此操作不可恢复', '清空回收站', { type: 'warning' })
  try {
    await advertisementApi.clearTrash()
    ElMessage.success('回收站已清空'); trashRecords.value = []; trashPagination.total = 0; loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
}

onMounted(() => { loadData(); loadApplySwitch() })
</script>

<style scoped lang="scss">
.ad-page {

  .apply-switch {
    display: flex; align-items: center; gap: 6px;
    padding: 4px 12px; border-radius: 8px;
    background: var(--el-fill-color-light);
    .switch-label { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
  }

  .time-permanent { font-size: 12px; color: var(--el-color-success); font-weight: 500; }

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

  .pagination-wrap { display: flex; justify-content: flex-end; margin-top: 12px; }
}

// ========== 位置预览（弹窗内） ==========
.pos-mini-preview {
  display: flex; flex-direction: column; gap: 3px; width: 120px; margin-left: 12px;
  .pos-block {
    background: var(--el-fill-color-light, #f1f5f9); border-radius: 3px;
    padding: 3px 0; text-align: center; font-size: 10px; color: var(--el-text-color-disabled, #94a3b8);
    transition: all 0.15s;
    &.active { background: var(--el-color-primary, #5b8def); color: #fff; font-weight: 600; }
    &.small { margin: 4px 0; }
  }
  .pos-body { display: flex; gap: 3px; }
  .pos-main { flex: 1; display: flex; flex-direction: column; }
  .sidebar-block { width: 40px; min-height: 30px; display: flex; align-items: center; justify-content: center; }
}

.position-selector { display: flex; align-items: flex-start; gap: 8px; }

// ========== 弹窗滚动体 ==========
.dialog-scroll-body {
  max-height: 60vh;
  overflow-y: auto;
}

// ========== 时间配置 ==========
.time-config {
  display: flex; flex-direction: column; gap: 8px;
  .time-presets { display: flex; gap: 6px; flex-wrap: wrap; }
  .time-pickers { display: flex; align-items: center; gap: 8px; }
  .time-sep { color: var(--el-text-color-secondary); }
}

// ========== 回收站工具栏 ==========
.trash-toolbar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px;
  .trash-actions { display: flex; gap: 8px; }
}
.trash-pagination { display: flex; justify-content: flex-end; margin-top: 12px; }

// ========== Switch ==========
:deep(.el-switch) { --el-switch-on-color: var(--el-color-primary); --el-switch-off-color: var(--el-fill-color-darker); height: 20px; }
:deep(.el-switch .el-switch__core) { height: 20px; min-width: 36px; border-radius: 10px; border: none; }
:deep(.el-switch .el-switch__core .el-switch__action) { width: 16px; height: 16px; }

// ========== 弹窗 ==========
:deep(.el-dialog) {
  border-radius: 16px; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  .el-dialog__header { padding: 20px 24px 12px; .el-dialog__title { font-weight: 700; font-size: 16px; } }
  .el-dialog__body { padding: 12px 24px 20px; }
}
</style>
