<template>
  <div class="media-page">
    <div class="page-header">
      <h2>媒体管理</h2>
    </div>

    <el-tabs v-model="activeTab" class="media-tabs compact-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="媒体列表" name="list">
        <div class="toolbar">
          <div class="filter-bar">
            <el-select v-model="filters.usageType" placeholder="用途" clearable style="width: 130px" @change="handleFilterChange">
              <el-option label="文章图片" value="content" />
              <el-option label="封面图" value="cover" />
              <el-option label="头像" value="avatar" />
              <el-option label="广告" value="ad" />
              <el-option label="广告申请" value="ad_apply" />
              <el-option label="轮播" value="carousel" />
              <el-option label="未分类" value="other" />
            </el-select>
            <el-select v-model="filters.referenceStatus" placeholder="引用状态" clearable style="width: 130px" @change="handleFilterChange">
              <el-option label="已引用" value="referenced" />
              <el-option label="未引用" value="unreferenced" />
            </el-select>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="triggerUpload">上传图片</el-button>
            <el-button :loading="refreshingReferences" @click="handleRefreshReferences">立即检测引用</el-button>
            <el-button :disabled="!mediaList.length" @click="toggleSelectAll">
              {{ isAllSelected ? '取消全选' : '全选' }}
            </el-button>
            <el-button type="warning" :disabled="unreferencedCount === 0" @click="handleCleanup">
              清理未引用 ({{ unreferencedCount }})
            </el-button>
            <el-button type="danger" :disabled="!selectedIds.length" @click="handleBatchDelete">
              批量删除 ({{ selectedIds.length }})
            </el-button>
          </div>
        </div>

        <input
          ref="uploadInputRef"
          type="file"
          accept="image/*"
          multiple
          class="upload-input"
          @change="onFileInputChange"
        >

        <!-- 拖拽上传区域 -->
        <div
          class="drop-zone"
          :class="{ 'drop-active': isDragging }"
          @dragenter.prevent="onDragEnter"
          @dragover.prevent
          @dragleave.prevent="onDragLeave"
          @drop.prevent="onDrop"
        >
          <div v-if="isDragging" class="drop-overlay">
            <svg viewBox="0 0 48 48" width="40" height="40" fill="none">
              <path d="M24 4v28M14 22l10 10 10-10" stroke="currentColor" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
              <path d="M8 36v4a4 4 0 004 4h24a4 4 0 004-4v-4" stroke="currentColor" stroke-width="3" stroke-linecap="round" />
            </svg>
            <span>松开上传图片</span>
          </div>

          <el-table
            ref="tableRef"
            :data="mediaList"
            v-loading="loading"
            stripe
            :height="tableHeight"
            row-key="id"
            @selection-change="handleSelectionChange"
          >
            <template #empty>
              <el-empty description="暂无媒体资源" :image-size="80" />
            </template>
            <el-table-column type="selection" width="40" />
            <el-table-column label="#" width="55" align="center">
              <template #default="{ $index }">{{ (pagination.pageNum - 1) * pagination.pageSize + $index + 1 }}</template>
            </el-table-column>
            <el-table-column label="缩略图" width="90" align="center">
              <template #default="{ row }">
                <div class="media-thumb-cell" @click.stop="openPreview(row)">
                  <img
                    v-if="!imgErrors[row.id]"
                    :src="row.thumbnailUrl || row.url"
                    :alt="row.fileName"
                    class="thumb-img"
                    @error="onImgError(row.id)"
                  >
                  <div v-else class="thumb-broken">
                    <svg viewBox="0 0 48 48" width="18" height="18" fill="none">
                      <rect x="6" y="10" width="36" height="28" rx="3" stroke="currentColor" stroke-width="2.5" />
                      <line x1="4" y1="4" x2="44" y2="44" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
                    </svg>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="文件名" min-width="160">
              <template #default="{ row }">
                <el-tooltip :content="row.fileName" placement="top" :show-after="400" :disabled="row.fileName.length <= 22">
                  <span class="cell-text">{{ row.fileName }}</span>
                </el-tooltip>
              </template>
            </el-table-column>
            <el-table-column label="类型" width="85" align="center">
              <template #default="{ row }">
                <span class="cell-text cell-text--secondary">{{ extLabel(row.fileName) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="90" align="center">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column label="存储" width="95" align="center">
              <template #default="{ row }">
                <span class="storage-tag" :class="`storage-tag--${resolveStorageSource(row)}`">
                  {{ storageLabel(resolveStorageSource(row)) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column label="引用" width="85" align="center">
              <template #default="{ row }">
                <el-tag :type="row.referenced ? 'success' : 'warning'" size="small" effect="plain">
                  {{ row.referenced ? '已引用' : '未引用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="引用来源" min-width="140">
              <template #default="{ row }">
                <template v-if="row.referenceDetails?.length">
                  <el-tooltip placement="top" :show-after="300">
                    <template #content>
                      <div v-for="(d, i) in row.referenceDetails" :key="i" class="ref-tooltip-item">
                        <span class="ref-type-tag" :class="getRefTypeClass(d.refType)">{{ getRefTypeLabel(d.refType) }}</span>
                        {{ d.postTitle }}
                      </div>
                    </template>
                    <span class="cell-text cell-text--link">{{ row.referenceDetails[0].postTitle }}</span>
                    <span v-if="row.referenceDetails.length > 1" class="ref-more"> +{{ row.referenceDetails.length - 1 }}</span>
                  </el-tooltip>
                </template>
                <span v-else class="cell-text cell-text--secondary">—</span>
              </template>
            </el-table-column>
            <el-table-column label="用途" width="85" align="center">
              <template #default="{ row }">{{ usageLabel(row.usageType) }}</template>
            </el-table-column>
            <el-table-column label="上传时间" width="165" align="center">
              <template #default="{ row }">{{ row.createTime }}</template>
            </el-table-column>
            <el-table-column label="操作" width="120" fixed="right" align="center">
              <template #default="{ row }">
                <el-button text type="primary" size="small" @click="copyUrl(row)">复制</el-button>
                <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 上传进度 -->
        <div v-if="uploadingCount > 0" class="upload-progress">
          正在上传 {{ uploadingCount }} 个文件...
        </div>

        <!-- 大图预览 -->
        <el-image-viewer
          v-if="previewVisible"
          :url-list="previewUrls"
          :initial-index="previewIndex"
          @close="previewVisible = false"
        />

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="pagination.pageNum"
            v-model:page-size="pagination.pageSize"
            :total="pagination.total"
            :page-sizes="[24, 48, 72]"
            layout="total, sizes, prev, pager, next"
            background
            size="small"
            @size-change="loadData"
            @current-change="loadData"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="存储统计" name="stats">
        <div class="stats-panel" v-loading="statsLoading">
          <div class="stats-overview">
            <div class="stats-card">
              <div class="stats-card__label">总文件数</div>
              <div class="stats-card__value">{{ stats.totalCount }}</div>
            </div>
            <div class="stats-card stats-card--size">
              <div class="stats-card__label">总文件大小</div>
              <div class="stats-card__value">{{ formatSize(stats.totalSize) }}</div>
            </div>
          </div>

          <div class="stats-list-wrap">
            <div v-if="!stats.usageTypeStats.length" class="stats-empty">暂无统计数据</div>
            <div v-for="item in stats.usageTypeStats" :key="item.usageType" class="stats-row">
              <div class="stats-row__head">
                <div class="stats-row__name">{{ usageLabel(item.usageType) }}</div>
                <div class="stats-row__meta">{{ item.fileCount }} 个 · {{ formatSize(item.totalSize) }}</div>
              </div>
              <div class="stats-bar">
                <div
                  class="stats-bar__fill"
                  :style="{ width: `${stats.totalSize ? Math.max((item.totalSize / stats.totalSize) * 100, 1) : 0}%` }"
                />
              </div>
            </div>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTable } from 'element-plus'
import { mediaApi, type MediaStatsVO, type MediaVO } from '~/api/content/media'
import { uploadApi } from '~/api/system/upload'
import { useAdminTableHeight } from '~/composables/layout/useAdminTableHeight'

const loading = ref(false)
const mediaList = ref<MediaVO[]>([])
const activeTab = ref<'list' | 'stats'>('list')

const tableRef = ref<InstanceType<typeof ElTable> | null>(null)
const tableHeight = useAdminTableHeight()

// 表格选择
const selectedRows = ref<MediaVO[]>([])
const selectedIds = computed(() => selectedRows.value.map(r => r.id))
const isAllSelected = computed(() => mediaList.value.length > 0 && selectedRows.value.length === mediaList.value.length)

function handleSelectionChange(rows: MediaVO[]) {
  selectedRows.value = rows
}

function toggleSelectAll() {
  if (!tableRef.value) return
  if (isAllSelected.value) {
    tableRef.value.clearSelection()
  } else {
    mediaList.value.forEach(row => tableRef.value!.toggleRowSelection(row, true))
  }
}

// 图片加载失败记录
const imgErrors = reactive<Record<number, boolean>>({})

const filters = reactive({ usageType: '', referenceStatus: '' })
const pagination = reactive({ pageNum: 1, pageSize: 24, total: 0 })
const unreferencedCount = ref(0)
const refreshingReferences = ref(false)
const statsLoading = ref(false)
const stats = reactive<MediaStatsVO>({
  totalCount: 0,
  totalSize: 0,
  usageTypeStats: [],
})

// 大图预览
const previewVisible = ref(false)
const previewUrls = ref<string[]>([])
const previewIndex = ref(0)

// 拖拽上传
const isDragging = ref(false)
const uploadingCount = ref(0)
const uploadInputRef = ref<HTMLInputElement | null>(null)
let dragCounter = 0

const usageLabelMap: Record<string, string> = {
  content: '文章图片',
  cover: '封面图',
  avatar: '头像',
  ad: '广告',
  carousel: '轮播',
  ad_apply: '广告申请',
  other: '未分类',
}
function usageLabel(type: string) {
  return usageLabelMap[type] || '未分类'
}

function extLabel(fileName: string) {
  const dot = fileName.lastIndexOf('.')
  return dot >= 0 ? fileName.slice(dot + 1).toUpperCase() : '—'
}

type StorageSource = 'local' | 'oss' | 'r2' | 'unknown'

const storageLabelMap: Record<StorageSource, string> = {
  local: '本地',
  oss: 'OSS',
  r2: 'R2',
  unknown: '未知',
}

function storageLabel(source: StorageSource) {
  return storageLabelMap[source]
}

function resolveStorageSource(item: Pick<MediaVO, 'url' | 'filePath'>): StorageSource {
  const sourceText = `${item.url || ''} ${item.filePath || ''}`.toLowerCase()
  const normalizedFilePath = (item.filePath || '').toLowerCase()

  if (sourceText.includes('/uploads/') || sourceText.includes('localhost') || sourceText.includes('127.0.0.1')) {
    return 'local'
  }
  if (sourceText.includes('aliyuncs.com') || sourceText.includes('oss-') || sourceText.includes('.oss-')) {
    return 'oss'
  }
  if (sourceText.includes('r2.cloudflarestorage.com') || sourceText.includes('.r2.dev') || sourceText.includes('cloudflare')) {
    return 'r2'
  }

  try {
    const host = new URL(item.url).host.toLowerCase()
    if (host.includes('aliyuncs.com') || host.includes('oss-')) return 'oss'
    if (host.includes('r2.cloudflarestorage.com') || host.includes('.r2.dev') || host.includes('cloudflare')) return 'r2'

    if ((normalizedFilePath.startsWith('images/') || normalizedFilePath.startsWith('temp/'))
      && !host.includes('localhost')
      && !host.includes('127.0.0.1')) {
      return 'r2'
    }
  } catch {
    // ignore malformed url
  }

  if (normalizedFilePath.startsWith('images/') || normalizedFilePath.startsWith('temp/')) {
    return 'r2'
  }

  return 'unknown'
}

const refTypeLabelMap: Record<string, string> = {
  cover: '封面',
  content: '内容',
  topic_cover: '专题封面',
  carousel_image: '轮播',
  friend_link_logo: '友链',
  ad_image: '广告',
  user_avatar: '头像',
  user_avatar_pending: '待审头像',
}

function getRefTypeLabel(refType: string) {
  return refTypeLabelMap[refType] || '引用'
}

function getRefTypeClass(refType: string) {
  return refType === 'content' ? 'ref-type--content' : 'ref-type--cover'
}

function handleTabChange(name: string | number) {
  if (name === 'stats' && !stats.usageTypeStats.length && !statsLoading.value) {
    loadStats()
  }
}

function handleFilterChange() {
  pagination.pageNum = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await mediaApi.page({
      pageNum: pagination.pageNum,
      pageSize: pagination.pageSize,
      usageType: filters.usageType || undefined,
      referenceStatus: filters.referenceStatus || undefined,
    })

    const records = res.data.records

    // 清理已不在当前页的错误记录
    const idSet = new Set(records.map(item => item.id))
    Object.keys(imgErrors).forEach((k) => {
      if (!idSet.has(+k)) delete imgErrors[+k]
    })

    mediaList.value = records
    pagination.total = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadStats() {
  statsLoading.value = true
  try {
    const res = await mediaApi.stats()
    stats.totalCount = res.data.totalCount || 0
    stats.totalSize = res.data.totalSize || 0
    stats.usageTypeStats = res.data.usageTypeStats || []
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载统计失败')
  } finally {
    statsLoading.value = false
  }
}

async function loadUnreferencedCount() {
  try {
    const res = await mediaApi.getUnreferencedCount()
    unreferencedCount.value = res.data
  } catch (e: unknown) {
    if (import.meta.dev) {
      console.warn('获取未引用数量失败', e)
    }
  }
}

async function handleCleanup() {
  await ElMessageBox.confirm(
    `确定清理所有未引用的 ${unreferencedCount.value} 个资源？此操作不可撤销。`,
    '清理确认',
    { type: 'warning' },
  )
  try {
    const res = await mediaApi.cleanupUnreferenced()
    ElMessage.success(`成功清理 ${res.data} 个未引用资源`)
    await Promise.all([loadData(), loadUnreferencedCount(), loadStats()])
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '清理失败')
  }
}

async function handleRefreshReferences() {
  refreshingReferences.value = true
  try {
    const res = await mediaApi.refreshReferences()
    unreferencedCount.value = res.data
    await Promise.all([loadData(), loadStats()])
    ElMessage.success(`引用检测已刷新，当前未引用 ${res.data} 个`)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '引用检测失败')
  } finally {
    refreshingReferences.value = false
  }
}

/** 大图预览 */
function openPreview(item: MediaVO) {
  previewUrls.value = mediaList.value
    .filter(m => !imgErrors[m.id])
    .map(m => m.url)
  previewIndex.value = previewUrls.value.indexOf(item.url)
  if (previewIndex.value < 0) previewIndex.value = 0
  previewVisible.value = true
}

/** 拖拽上传 */
function onDragEnter() {
  dragCounter++
  isDragging.value = true
}
function onDragLeave() {
  dragCounter--
  if (dragCounter <= 0) {
    dragCounter = 0
    isDragging.value = false
  }
}

function triggerUpload() {
  uploadInputRef.value?.click()
}

async function onFileInputChange(event: Event) {
  const input = event.target as HTMLInputElement | null
  const files = input?.files ? Array.from(input.files) : []
  await uploadImageFiles(files)
  if (input) {
    input.value = ''
  }
}

async function uploadImageFiles(files: File[]) {
  if (!files.length) return
  const imageFiles = files.filter(f => f.type.startsWith('image/'))
  if (!imageFiles.length) {
    ElMessage.warning('仅支持上传图片文件')
    return
  }

  uploadingCount.value = imageFiles.length
  let successCount = 0
  for (const file of imageFiles) {
    try {
      await uploadApi.image(file, 'other')
      successCount++
    } catch (e: unknown) {
      ElMessage.error(`上传失败: ${file.name} - ${(e as Error).message}`)
    }
  }
  uploadingCount.value = 0

  if (successCount > 0) {
    ElMessage.success(`成功上传 ${successCount} 个文件`)
    await Promise.all([loadData(), loadUnreferencedCount(), loadStats()])
  }
}

async function onDrop(event: DragEvent) {
  dragCounter = 0
  isDragging.value = false
  const files = event.dataTransfer?.files ? Array.from(event.dataTransfer.files) : []
  await uploadImageFiles(files)
}

async function copyUrl(item: MediaVO) {
  if (!item.url) {
    ElMessage.warning('资源链接为空，无法复制')
    return
  }

  try {
    if (navigator.clipboard?.writeText) {
      await navigator.clipboard.writeText(item.url)
    } else {
      const input = document.createElement('input')
      input.value = item.url
      document.body.appendChild(input)
      input.select()
      document.execCommand('copy')
      document.body.removeChild(input)
    }
    ElMessage.success('链接已复制')
  } catch {
    ElMessage.error('复制失败，请手动复制')
  }
}

async function handleDelete(item: MediaVO) {
  const extraWarning = item.referenced === true
    ? '该资源已被文章引用，删除后相关内容图片将失效。'
    : ''
  const message = extraWarning
    ? `确定删除「${item.fileName}」？${extraWarning}`
    : `确定删除「${item.fileName}」？此操作将同步删除OSS文件。`

  await ElMessageBox.confirm(message, '提示', { type: 'warning' })
  try {
    await mediaApi.delete(item.id)
    ElMessage.success('删除成功')
    await Promise.all([loadData(), loadUnreferencedCount(), loadStats()])
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  }
}

async function handleBatchDelete() {
  const ids = selectedIds.value
  if (!ids.length) return

  const idSet = new Set(ids)
  const referencedCount = mediaList.value.reduce((count, item) => {
    if (idSet.has(item.id) && item.referenced === true) return count + 1
    return count
  }, 0)

  const message = referencedCount > 0
    ? `确定删除选中的 ${ids.length} 个文件？其中 ${referencedCount} 个已被文章引用，删除后相关内容图片将失效。`
    : `确定删除选中的 ${ids.length} 个文件？`

  await ElMessageBox.confirm(message, '提示', { type: 'warning' })
  try {
    await mediaApi.batchDelete(ids)
    ElMessage.success('批量删除成功')
    await Promise.all([loadData(), loadUnreferencedCount(), loadStats()])
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  }
}

function onImgError(id: number) {
  imgErrors[id] = true
}

function formatSize(bytes: number) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return `${size.toFixed(1)} ${units[i]}`
}

onMounted(() => {
  loadData()
  loadUnreferencedCount()
  loadStats()
})
</script>

<style scoped lang="scss">
.media-page {
  .page-header {
    margin-bottom: 8px;
  }
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
  flex: 0 0 auto;
}

.filter-bar,
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.upload-input {
  display: none;
}

// ========== 通用表格单元格 ==========
.cell-text {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  &--secondary {
    color: var(--el-text-color-secondary);
  }
  &--link {
    color: var(--el-color-primary);
    cursor: default;
  }
}

// ========== 缩略图列 ==========
.media-thumb-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 62px;
  height: 40px;
  border-radius: 4px;
  overflow: hidden;
  cursor: pointer;
  background: var(--el-fill-color-light);
  border: 1px solid var(--el-border-color-extra-light);
}
.thumb-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.thumb-broken {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-placeholder);
}

// ========== 存储标签 ==========
.storage-tag {
  display: inline-flex;
  align-items: center;
  height: 20px;
  padding: 0 6px;
  border-radius: 999px;
  font-size: 11px;
  border: 1px solid transparent;
  white-space: nowrap;
}
.storage-tag--local {
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-7);
}
.storage-tag--oss {
  color: var(--el-color-warning-dark-2);
  background: var(--el-color-warning-light-9);
  border-color: var(--el-color-warning-light-7);
}
.storage-tag--r2 {
  color: var(--el-color-success);
  background: var(--el-color-success-light-9);
  border-color: var(--el-color-success-light-7);
}
.storage-tag--unknown {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
  border-color: var(--el-border-color-light);
}

// ========== 引用相关 ==========
.ref-tooltip-item {
  display: flex;
  align-items: center;
  gap: 4px;
  line-height: 1.8;
}
.ref-type-tag {
  flex-shrink: 0;
  padding: 0 4px;
  border-radius: 2px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.6;
}
.ref-type--cover {
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  .dark & { background: rgba(91, 141, 239, 0.15); }
}
.ref-type--content {
  background: var(--el-color-success-light-9);
  color: var(--el-color-success);
  .dark & { background: rgba(16, 185, 129, 0.15); }
}
.ref-more {
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}

.pagination-wrap {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  flex: 0 0 auto;
}

// ========== 拖拽上传 ==========
.drop-zone {
  position: relative;
  flex: 1 1 auto;
  min-height: 0;
  display: flex;
  flex-direction: column;

  :deep(.el-table) {
    flex: 1 1 auto;
    min-height: 0;
  }
}
.drop-active {
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    border: 2px dashed var(--el-color-primary);
    border-radius: 8px;
    background: rgba(91, 141, 239, 0.05);
    z-index: 10;
    pointer-events: none;
  }
}
.drop-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  z-index: 11;
  color: var(--el-color-primary);
  font-size: 15px;
  font-weight: 600;
  pointer-events: none;
}

// ========== 上传进度 ==========
.upload-progress {
  position: fixed;
  bottom: 24px;
  right: 24px;
  padding: 10px 20px;
  background: var(--el-color-primary);
  color: #fff;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  box-shadow: 0 2px 12px rgba(91, 141, 239, 0.3);
  z-index: 2000;
  animation: slide-up 0.3s ease-out;
}
@keyframes slide-up {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

// ========== 统计页 ==========
.stats-panel {
  min-height: 460px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 12px;
  padding: 16px;
  background: var(--el-bg-color);
}

.stats-overview {
  display: grid;
  grid-template-columns: repeat(2, minmax(220px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.stats-card {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 14px;
  background: linear-gradient(165deg, var(--el-fill-color-extra-light), var(--el-bg-color));
}

.stats-card--size {
  background:
    radial-gradient(circle at 80% 15%, color-mix(in srgb, var(--el-color-primary) 18%, transparent), transparent 40%),
    linear-gradient(165deg, var(--el-fill-color-extra-light), var(--el-bg-color));
}

.stats-card__label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.stats-card__value {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 700;
  color: var(--el-text-color-primary);
}

.stats-list-wrap {
  display: grid;
  gap: 10px;
}

.stats-row {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 10px 12px;
}

.stats-row__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 8px;
}

.stats-row__name {
  font-size: 13px;
  font-weight: 600;
}

.stats-row__meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.stats-bar {
  height: 8px;
  border-radius: 999px;
  background: var(--el-fill-color-light);
  overflow: hidden;
}

.stats-bar__fill {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #22a1f3, #4f46e5);
}

.stats-empty {
  min-height: 160px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--el-text-color-secondary);
}
</style>
