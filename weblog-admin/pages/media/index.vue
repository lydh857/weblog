<template>
  <div class="media-page">
    <div class="page-header">
      <h2>媒体管理</h2>
    </div>

    <el-tabs v-model="activeTab" class="media-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="媒体列表" name="list">
        <div class="toolbar">
          <div class="filter-bar">
            <el-select v-model="filters.usageType" placeholder="用途" clearable style="width: 130px" @change="handleFilterChange">
              <el-option label="文章图片" value="content" />
              <el-option label="封面图" value="cover" />
              <el-option label="头像" value="avatar" />
              <el-option label="广告" value="ad" />
              <el-option label="轮播" value="carousel" />
              <el-option label="其他" value="other" />
            </el-select>
            <el-select v-model="filters.referenceStatus" placeholder="引用状态" clearable style="width: 130px" @change="handleFilterChange">
              <el-option label="已引用" value="referenced" />
              <el-option label="未引用" value="unreferenced" />
            </el-select>
          </div>
          <div class="header-actions">
            <el-button type="primary" @click="triggerUpload">
              上传图片
            </el-button>
            <el-button :disabled="!mediaList.length" @click="toggleSelectAll">
              {{ isAllSelected ? '取消全选' : '全选' }}
            </el-button>
            <el-button type="warning" :disabled="unreferencedCount === 0" @click="handleCleanup">
              清理未引用 ({{ unreferencedCount }})
            </el-button>
            <el-button type="danger" :disabled="!selectedCount" @click="handleBatchDelete">
              批量删除 ({{ selectedCount }})
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

        <!-- 拖拽上传遮罩 -->
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

          <!-- 媒体网格 -->
          <div v-loading="loading" class="media-grid-wrap" :class="{ 'is-loading': loading }">
            <div class="media-grid">
              <div
                v-for="(item, index) in mediaList"
                :key="item.id"
                class="media-card"
                :class="[{ selected: selectedSet[item.id] }, `media-card--${item.usageType || 'other'}`]"
                @click="handleCardClick($event, item.id, index)"
              >
                <div class="media-thumb">
                  <div v-if="!imgErrors[item.id] && !visibleSet[item.id]" class="img-skeleton" />
                  <img
                    v-if="!imgErrors[item.id] && visibleSet[item.id]"
                    :src="item.thumbnailUrl || item.url"
                    :alt="item.fileName"
                    class="img-fade"
                    @error="onImgError(item.id)"
                    @click.stop="openPreview(item)"
                  />
                  <div
                    v-if="!imgErrors[item.id] && !visibleSet[item.id]"
                    :ref="(el: unknown) => setObserveRef(el as HTMLElement | null, item.id)"
                    class="img-observe-trigger"
                  />
                  <div v-if="imgErrors[item.id]" class="img-broken">
                    <svg viewBox="0 0 48 48" width="28" height="28" fill="none">
                      <rect x="6" y="10" width="36" height="28" rx="3" stroke="currentColor" stroke-width="2.5" />
                      <circle cx="17" cy="21" r="3" stroke="currentColor" stroke-width="2" />
                      <path d="M6 32l10-8 6 5 8-6 12 9" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" />
                      <line x1="4" y1="4" x2="44" y2="44" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" />
                    </svg>
                    <span>加载失败</span>
                  </div>
                  <div class="media-overlay" @click.stop="toggleSelect(item.id)">
                    <el-checkbox :model-value="!!selectedSet[item.id]" />
                  </div>
                  <span v-if="item.referenced === true" class="ref-badge ref-badge--yes">已引用</span>
                  <span v-else class="ref-badge ref-badge--no">未引用</span>
                  <span class="usage-badge">{{ usageLabel(item.usageType) }}</span>
                </div>
                <div class="media-info">
                  <span class="media-name" :title="item.fileName">{{ item.fileName }}</span>
                  <div class="info-row-2">
                    <span class="media-size">{{ formatSize(item.fileSize) }}</span>
                    <div class="info-actions">
                      <el-button text type="primary" size="small" class="btn-copy" @click.stop="copyUrl(item)">复制链接</el-button>
                      <el-button text type="danger" size="small" class="btn-delete" @click.stop="handleDelete(item)">删除</el-button>
                    </div>
                  </div>
                  <div v-if="item.referenceDetails?.length" class="ref-details">
                    <div v-for="(d, i) in item.referenceDetails" :key="i" class="ref-detail-item">
                      <span class="ref-type-tag" :class="getRefTypeClass(d.refType)">
                        {{ getRefTypeLabel(d.refType) }}
                      </span>
                      <span class="ref-post-title" :title="d.postTitle">{{ d.postTitle }}</span>
                    </div>
                  </div>
                </div>
              </div>
            </div>
            <el-empty v-if="!loading && !mediaList.length" description="暂无媒体资源" class="grid-empty" />
          </div>
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
            :page-sizes="[21, 42, 63]"
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
import { mediaApi, type MediaStatsVO, type MediaVO } from '~/api/media'
import { uploadApi } from '~/api/upload'

const loading = ref(false)
const mediaList = ref<MediaVO[]>([])
const activeTab = ref<'list' | 'stats'>('list')
// 用 reactive 对象代替 Set，O(1) 查找且精准响应式
const selectedSet = reactive<Record<number, boolean>>({})
const selectedCount = computed(() => Object.keys(selectedSet).filter(k => selectedSet[+k]).length)
const selectedIds = computed(() => Object.keys(selectedSet).filter(k => selectedSet[+k]).map(Number))
const isAllSelected = computed(() => mediaList.value.length > 0 && mediaList.value.every(item => selectedSet[item.id]))
// 图片加载失败记录
const imgErrors = reactive<Record<number, boolean>>({})
// IntersectionObserver 懒加载：记录哪些卡片进入可视区
const visibleSet = reactive<Record<number, boolean>>({})
const observeRefs = new Map<number, HTMLElement>()
let observer: IntersectionObserver | null = null
const filters = reactive({ usageType: '', referenceStatus: '' })
const pagination = reactive({ pageNum: 1, pageSize: 21, total: 0 })
const unreferencedCount = ref(0)
const statsLoading = ref(false)
const stats = reactive<MediaStatsVO>({
  totalCount: 0,
  totalSize: 0,
  usageTypeStats: [],
})
// Shift+点击范围选择：记录上次点击的索引
let lastClickIndex = -1
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
  other: '其他',
}
function usageLabel(type: string) {
  return usageLabelMap[type] || '其他'
}

const refTypeLabelMap: Record<string, string> = {
  cover: '文章封面',
  content: '文章内容',
  topic_cover: '专题封面',
  carousel_image: '轮播图',
  friend_link_logo: '友链Logo',
  ad_image: '广告图片',
  user_avatar: '用户头像',
  user_avatar_pending: '待审头像',
}

function getRefTypeLabel(refType: string) {
  return refTypeLabelMap[refType] || '其他引用'
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
    const idSet = new Set(records.map(item => item.id))

    Object.keys(selectedSet).forEach((k) => {
      if (!idSet.has(+k)) {
        delete selectedSet[+k]
      }
    })
    Object.keys(imgErrors).forEach((k) => {
      if (!idSet.has(+k)) {
        delete imgErrors[+k]
      }
    })

    const nextVisibleSet: Record<number, boolean> = {}
    records.forEach((item) => {
      nextVisibleSet[item.id] = visibleSet[item.id] ?? false
    })

    observeRefs.clear()
    Object.keys(visibleSet).forEach(k => delete visibleSet[+k])
    Object.entries(nextVisibleSet).forEach(([k, v]) => {
      visibleSet[+k] = v
    })

    mediaList.value = records
    pagination.total = res.data.total
    // 数据加载后重新初始化 observer
    nextTick(() => initObserver())
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
    console.warn('获取未引用数量失败', e)
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

function toggleSelect(id: number) {
  selectedSet[id] = !selectedSet[id]
}

/** 全选/取消全选 */
function toggleSelectAll() {
  if (isAllSelected.value) {
    // 取消全选
    mediaList.value.forEach(item => { selectedSet[item.id] = false })
  } else {
    // 全选
    mediaList.value.forEach(item => { selectedSet[item.id] = true })
  }
}

/** 卡片点击：支持 Shift+点击范围选择 */
function handleCardClick(event: MouseEvent, id: number, index: number) {
  if (event.shiftKey && lastClickIndex >= 0 && lastClickIndex !== index) {
    // Shift+点击：范围选择
    const start = Math.min(lastClickIndex, index)
    const end = Math.max(lastClickIndex, index)
    for (let i = start; i <= end; i++) {
      const card = mediaList.value[i]
      if (card) selectedSet[card.id] = true
    }
  } else {
    toggleSelect(id)
  }
  lastClickIndex = index
}

/** 大图预览 */
function openPreview(item: MediaVO) {
  // 构建预览 URL 列表（使用原图 URL）
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
    if (idSet.has(item.id) && item.referenced === true) {
      return count + 1
    }
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

/** IntersectionObserver 懒加载：卡片进入滚动容器可视区时才加载图片 */
function setObserveRef(el: HTMLElement | null, id: number) {
  if (!el) {
    observeRefs.delete(id)
    return
  }
  observeRefs.set(id, el)
  if (observer) observer.observe(el)
}

function initObserver() {
  observer?.disconnect()
  observer = null

  const root = document.querySelector('.media-grid-wrap')
  if (!root) return

  observer = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          // 找到对应 id
          for (const [id, el] of observeRefs) {
            if (el === entry.target) {
              visibleSet[id] = true
              observer?.unobserve(el)
              observeRefs.delete(id)
              break
            }
          }
        }
      }
    },
    { root, rootMargin: '100px' },
  )
  // 观察已注册的元素
  for (const el of observeRefs.values()) {
    observer.observe(el)
  }
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

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<style scoped lang="scss">
.media-page {
  .page-header {
    margin-bottom: 8px;
    h2 {
      margin: 0;
      font-size: 22px;
      font-weight: 700;
      letter-spacing: 0.2px;
    }
  }
}

.media-tabs {
  :deep(.el-tabs__header) {
    margin-bottom: 14px;
  }
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  flex-wrap: wrap;
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

// ========== 网格容器 ==========
.media-grid-wrap {
  height: 68vh;
  min-height: 560px;
  overflow-y: auto;
  border-radius: 12px;
  border: 1px solid var(--el-border-color-lighter);
  background: linear-gradient(180deg, var(--el-bg-color-page), var(--el-fill-color-extra-light));
  padding: 12px;
  transition: opacity 0.2s ease;
}
.media-grid-wrap.is-loading {
  opacity: 0.96;
}
.media-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 12px;
  align-content: start;
}
.grid-empty {
  min-height: 420px;
  display: flex;
  align-items: center;
  justify-content: center;
}

// ========== 卡片 ==========
.media-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
  display: flex;
  flex-direction: column;
  min-width: 0;
  // 选中状态优先级高于 hover
  &.selected {
    border-color: var(--el-color-primary);
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--el-color-primary) 28%, transparent);
  }
  &:hover:not(.selected) {
    border-color: var(--el-border-color);
    transform: translateY(-1px);
    box-shadow: 0 8px 24px rgba(15, 23, 42, 0.08);
  }
}

// ========== 缩略图 ==========
.media-thumb {
  position: relative;
  overflow: hidden;
  background:
    radial-gradient(circle at 15% 20%, color-mix(in srgb, var(--el-color-primary) 18%, transparent), transparent 35%),
    linear-gradient(135deg, var(--el-fill-color-light), var(--el-fill-color-extra-light));
  flex-shrink: 0;
  aspect-ratio: 4 / 3;
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

.media-card--avatar .media-thumb img {
  object-fit: contain;
  padding: 8px;
}

// ========== 破图占位 ==========
.img-broken {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: var(--el-text-color-placeholder);
  svg { opacity: 0.4; }
  span { font-size: 11px; }
}

// ========== 图片懒加载骨架 ==========
.img-skeleton {
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, var(--el-fill-color-lighter) 25%, var(--el-fill-color-light) 50%, var(--el-fill-color-lighter) 75%);
  background-size: 200% 100%;
  animation: skeleton-shimmer 1.5s infinite;
}
@keyframes skeleton-shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
.img-observe-trigger {
  position: absolute;
  inset: 0;
  pointer-events: none;
}
.img-fade {
  animation: img-fadein 0.3s ease-out;
}
@keyframes img-fadein {
  from { opacity: 0; }
  to { opacity: 1; }
}

// ========== 角标 ==========
.ref-badge {
  position: absolute;
  top: 4px;
  right: 4px;
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 600;
  line-height: 1.5;
  pointer-events: none;
  z-index: 1;
  color: #fff;
}
.ref-badge--yes { background: var(--el-color-success); }
.ref-badge--no { background: var(--el-color-warning); }
.ref-badge--protected { background: var(--el-color-info); }

.usage-badge {
  position: absolute;
  bottom: 4px;
  left: 4px;
  padding: 1px 5px;
  border-radius: 3px;
  font-size: 10px;
  font-weight: 500;
  line-height: 1.4;
  pointer-events: none;
  z-index: 1;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  backdrop-filter: blur(4px);
}

// ========== 复选框覆盖层 ==========
.media-overlay {
  position: absolute;
  top: 4px;
  left: 4px;
  z-index: 2;
  cursor: pointer;
}

// ========== 信息区 ==========
.media-info {
  padding: 8px 10px 10px;
  flex: 1;
  min-width: 0;
}
.media-name {
  display: block;
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 2px;
}
.info-row-2 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.media-size {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.info-actions {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.btn-copy,
.btn-delete {
  padding: 0 2px;
  height: auto;
  font-size: 11px;
}

// ========== 引用来源 ==========
.ref-details {
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed var(--el-border-color-lighter);
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.ref-detail-item {
  display: flex;
  align-items: center;
  gap: 3px;
  min-width: 0;
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
.ref-post-title {
  flex: 1;
  font-size: 10px;
  color: var(--el-text-color-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pagination-wrap {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
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

// ========== 拖拽上传 ==========
.drop-zone {
  position: relative;
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

@media (max-width: 992px) {
  .media-grid-wrap {
    height: 62vh;
    min-height: 480px;
  }

  .stats-overview {
    grid-template-columns: 1fr;
  }
}
</style>
