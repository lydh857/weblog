<template>
  <div class="post-editor-fullscreen">
    <header class="editor-toolbar">
      <div class="toolbar-left">
        <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
        <el-divider direction="vertical" />
        <span class="editor-title">{{ editorTitle }}</span>
        <span v-if="isScheduled && form.scheduledTime" class="schedule-badge">
          <el-icon><Timer /></el-icon> {{ formatScheduleDisplay(form.scheduledTime) }}
        </span>
        <span v-if="autoSaveText" class="auto-save-status"><el-icon><Clock /></el-icon> {{ autoSaveText }}</span>
      </div>
      <div class="toolbar-right">
        <!-- 定时文章：保存修改 -->
        <el-button v-if="isScheduled" @click="handleSaveScheduled" :loading="savingType === 'scheduled'" :disabled="!!savingType">保存修改</el-button>
        <!-- 已发布文章：撤回草稿 -->
        <el-button v-if="isPublished" @click="handleRevertToDraft" :loading="savingType === 'revert'" :disabled="!!savingType">撤回草稿</el-button>
        <!-- 草稿/新文章：保存草稿（编辑草稿时显示"保存修改"） -->
        <el-button v-if="!isScheduled && !isPublished" @click="handleSaveDraft" :loading="savingType === 'draft'" :disabled="!!savingType">
          {{ isDraft && isEdit ? '保存修改' : '保存草稿' }}
        </el-button>
        <!-- 非已发布文章才显示定时发布 -->
        <el-button v-if="!isPublished" @click="showScheduleDialog = true" :disabled="!!savingType"><el-icon><Timer /></el-icon> {{ isScheduled ? '修改定时' : '定时发布' }}</el-button>
        <el-button type="primary" @click="handlePublish" :loading="savingType === 'publish'" :disabled="!!savingType">
          {{ isEdit && form.status === 'published' ? '更新发布' : '发布文章' }}
        </el-button>
      </div>
    </header>

    <div class="editor-body">
      <div class="editor-main">
        <input v-model="form.title" class="title-input" placeholder="请输入文章标题..." maxlength="200" />
        <div class="editor-wrap">
          <ClientOnly>
            <LazyMarkdownEditor v-model="form.content" v-model:preview-theme="form.previewTheme" v-model:code-theme="form.codeTheme" :height="editorHeight" @save="handleEditorSave" />
          </ClientOnly>
        </div>
      </div>

      <aside class="editor-sidebar" :class="{ collapsed: sidebarCollapsed }">
        <div class="sidebar-toggle" @click="sidebarCollapsed = !sidebarCollapsed">
          <el-icon :size="14"><ArrowRight v-if="!sidebarCollapsed" /><ArrowLeft v-else /></el-icon>
        </div>
        <div v-show="!sidebarCollapsed" class="sidebar-scroll">
          <!-- AI 元信息生成（顶部） -->
          <div class="setting-group ai-meta-top">
            <LazyAiMetaGenerator
              ref="aiMetaRef"
              :title="form.title"
              :content="form.content"
              :current-tag-count="selectedTags.length"
              @adopt-summary="(s: string) => form.summary = s"
              @adopt-seo="handleAdoptSeo"
              @adopt-tags="handleAdoptAiTags"
              @adopt-category="handleAdoptAiCategory"
              @adopt-slug="(s: string) => form.slug = s"
            />
          </div>

          <!-- 分类 -->
          <div class="setting-group">
            <div class="setting-label-row">
              <label class="setting-label">分类</label>
              <el-button text type="primary" size="small" @click="showAddCategory = true">+ 新建</el-button>
            </div>
            <el-cascader v-model="categoryValue" :options="categoryTree"
              :props="{ value: 'id', label: 'name', children: 'children', emitPath: true, expandTrigger: 'hover' }"
              placeholder="选择分类" clearable filterable class="full-width"
              popper-class="category-cascader-popper" />
          </div>

          <!-- 标签 -->
          <div class="setting-group">
            <label class="setting-label">标签 <span class="label-hint">（最多5个，回车创建新标签）</span></label>
            <el-select ref="tagSelectRef" v-model="selectedTags" multiple filterable
              placeholder="搜索或输入新标签，回车创建" class="full-width" :multiple-limit="5" clearable
              :filter-method="filterTags"
              @change="handleTagChange">
              <el-option v-for="t in visibleTags" :key="t.id" :label="t.name" :value="t.id" />
              <template #empty>
                <p v-if="tagQuery" style="padding: 8px 16px; margin: 0; font-size: 13px; color: var(--el-text-color-secondary); cursor: pointer" @click="handleTagEnter">
                  点击或回车创建「{{ tagQuery }}」
                </p>
                <p v-else style="padding: 8px 16px; margin: 0; font-size: 13px; color: var(--el-text-color-placeholder)">
                  无匹配标签
                </p>
              </template>
            </el-select>
          </div>

          <!-- 摘要 -->
          <div class="setting-group">
            <label class="setting-label">摘要</label>
            <div class="textarea-clearable">
              <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="文章摘要（可选）" />
              <el-icon v-if="form.summary" class="textarea-clear-btn" @click="form.summary = ''"><CircleClose /></el-icon>
            </div>
          </div>

          <!-- 封面图 -->
          <div class="setting-group">
            <label class="setting-label">封面图</label>
            <div class="cover-upload-area">
              <div v-if="form.coverImage" class="cover-preview" @click="openCoverCropper">
                <AppImage :src="form.coverImage" fit="cover" class="cover-img" />
                <div class="cover-overlay">裁剪 / 更换</div>
              </div>
              <div v-else class="cover-placeholder" @click="triggerCoverUpload">
                <el-icon :size="28"><Plus /></el-icon>
                <span>上传封面</span>
              </div>
              <input ref="coverInputRef" type="file" accept="image/*" style="display:none" @change="handleCoverFileChange" />
            </div>
          </div>

          <!-- SEO -->
          <div class="setting-group">
            <label class="setting-label setting-divider">SEO 设置</label>
            <el-input v-model="form.seoTitle" maxlength="60" show-word-limit placeholder="SEO标题" clearable class="mb-8" />
            <div class="textarea-clearable">
              <el-input v-model="form.seoDescription" type="textarea" :rows="2" maxlength="160" show-word-limit placeholder="SEO描述" class="mb-8" />
              <el-icon v-if="form.seoDescription" class="textarea-clear-btn" @click="form.seoDescription = ''"><CircleClose /></el-icon>
            </div>
            <el-input v-model="form.seoKeywords" maxlength="200" show-word-limit placeholder="关键词，逗号分隔" clearable class="mb-8" />
            <label class="setting-label" style="margin-top: 8px">Slug</label>
            <el-input v-model="form.slug" maxlength="80" placeholder="留空自动生成" clearable>
              <template #prepend>/post/</template>
            </el-input>
          </div>
        </div>
      </aside>
    </div>

    <!-- 定时发布弹窗 -->
    <el-dialog v-model="showScheduleDialog" title="定时发布" width="420px" @open="onScheduleDialogOpen">
      <el-form label-width="80px">
        <el-form-item label="发布时间">
          <el-date-picker v-model="scheduleTime" type="datetime" placeholder="选择发布时间"
            :disabled-date="(d: Date) => d.getTime() < Date.now() - 86400000" style="width: 100%"
            format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
          <div class="time-presets">
            <el-button size="small" @click="applyPreset(0.5)">30分钟后</el-button>
            <el-button size="small" @click="applyPreset(1)">1小时后</el-button>
            <el-button size="small" @click="applyPreset(2)">2小时后</el-button>
            <el-button size="small" @click="applyPreset(6)">6小时后</el-button>
            <el-button size="small" @click="applyPreset(12)">12小时后</el-button>
            <el-button size="small" @click="applyPreset(24)">明天此时</el-button>
          </div>
          <div class="custom-time-row">
            <el-input-number v-model="customTimeValue" :min="1" :max="999" size="small" controls-position="right" style="width: 100px" />
            <el-select v-model="customTimeUnit" size="small" style="width: 90px">
              <el-option label="分钟" value="minutes" />
              <el-option label="小时" value="hours" />
              <el-option label="天" value="days" />
            </el-select>
            <el-button size="small" type="primary" plain @click="applyCustomPreset">应用</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showScheduleDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSchedulePublish" :loading="savingType === 'schedule'">确定</el-button>
      </template>
    </el-dialog>

    <!-- 新建分类弹窗 -->
    <el-dialog v-model="showAddCategory" title="新建分类" width="400px">
      <el-form label-width="80px">
        <el-form-item label="父分类">
          <el-select v-model="newCategoryParentId" placeholder="顶级分类" clearable class="full-width">
            <el-option v-for="c in topCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="分类名称">
          <el-input v-model="newCategoryName" maxlength="30" placeholder="输入分类名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddCategory = false">取消</el-button>
        <el-button type="primary" @click="handleAddCategory">创建</el-button>
      </template>
    </el-dialog>

    <!-- 封面裁剪弹窗 -->
    <LazyImageCropper v-if="showCoverCropper" v-model="showCoverCropper" :image-src="coverCropperSrc" :aspect-ratio="[16, 9]"
      output-type="image/webp" :max-output-width="1200" @crop="handleCoverCropped" />
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Clock, Timer, Plus, CircleClose } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postApi, type PostCreateParams } from '~/api/post'
import { categoryApi, type CategoryVO } from '~/api/category'
import { tagApi, type TagVO } from '~/api/tag'
import { uploadApi } from '~/api/upload'

const route = useRoute()
const router = useRouter()

// ========== 基础状态 ==========
const isEdit = ref(false)
const postId = ref<number | null>(null)
// 当前正在执行的保存操作类型，用于按钮独立 loading
const savingType = ref<'' | 'draft' | 'publish' | 'scheduled' | 'revert' | 'schedule'>('')
const sidebarCollapsed = ref(false)
const autoSaveText = ref('')
const editorHeight = ref('calc(100vh - 142px)')
const aiMetaRef = ref<{ getSnapshot: () => unknown; restoreSnapshot: (s: unknown) => void } | null>(null)

const form = reactive({
  title: '',
  content: '',
  summary: '',
  coverImage: '',
  slug: '',
  seoTitle: '',
  seoDescription: '',
  seoKeywords: '',
  status: 'draft',
  isTop: false,
  scheduledTime: '',
  previewTheme: 'default',
  codeTheme: 'atom',
})

// 编辑器标题
const editorTitle = computed(() => {
  if (!isEdit.value) return '写文章'
  if (form.status === 'scheduled') return '编辑定时文章'
  if (form.status === 'published') return '编辑已发布文章'
  if (form.status === 'draft') return '编辑草稿'
  return '编辑文章'
})

const isScheduled = computed(() => form.status === 'scheduled')
const isPublished = computed(() => isEdit.value && form.status === 'published')
const isDraft = computed(() => form.status === 'draft')

function formatScheduleDisplay(t: string): string {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

// ========== 分类 ==========
const categories = ref<CategoryVO[]>([])
const categoryValue = ref<number[]>([])

interface TreeNode { id: number; name: string; children?: TreeNode[] }
const categoryTree = computed<TreeNode[]>(() => {
  const topLevel = categories.value.filter(c => c.parentId === 0)
  return topLevel.map(top => ({
    id: top.id,
    name: top.name,
    children: categories.value.filter(c => c.parentId === top.id).map(sub => ({ id: sub.id, name: sub.name })),
  }))
})
const topCategories = computed(() => categories.value.filter(c => c.parentId === 0))

// 新建分类（暂存本地，发布时才真正创建）
const showAddCategory = ref(false)
const newCategoryName = ref('')
const newCategoryParentId = ref<number | undefined>(undefined)
// 临时分类用负数 ID 标识
let tempCategoryIdCounter = -1
const pendingCategories = ref<{ tempId: number; name: string; parentId: number }[]>([])

function handleAddCategory() {
  if (!newCategoryName.value.trim()) { ElMessage.warning('请输入分类名称'); return }
  const name = newCategoryName.value.trim()
  const parentId = newCategoryParentId.value || 0
  const tempId = tempCategoryIdCounter--

  // 添加到临时分类列表
  pendingCategories.value.push({ tempId, name, parentId })

  // 添加到 categories 列表中（用临时负数 ID），让 cascader 能选中
  categories.value.push({ id: tempId, name: `${name}（新）`, parentId } as CategoryVO)

  // 自动选中新分类
  if (parentId === 0) {
    categoryValue.value = [tempId]
  } else {
    categoryValue.value = [parentId, tempId]
  }

  ElMessage.success('分类已暂存，保存文章时自动创建')
  showAddCategory.value = false
  newCategoryName.value = ''
  newCategoryParentId.value = undefined
}

// ========== AI 元信息采用 ==========
function handleAdoptSeo(seo: Record<string, string | undefined>) {
  if (seo.seoTitle !== undefined) form.seoTitle = seo.seoTitle
  if (seo.seoDescription !== undefined) form.seoDescription = seo.seoDescription
  if (seo.seoKeywords !== undefined) form.seoKeywords = seo.seoKeywords
}

function handleAdoptAiTags(aiTags: Array<{ name: string; isExisting: boolean; tagId: number | null }>) {
  const maxTags = 5
  for (const t of aiTags) {
    if (selectedTags.value.length >= maxTags) {
      ElMessage.warning(`标签最多 ${maxTags} 个，已忽略多余的推荐标签`)
      break
    }
    if (t.isExisting && t.tagId) {
      if (!selectedTags.value.includes(t.tagId)) selectedTags.value.push(t.tagId)
    } else {
      if (!selectedTags.value.includes(t.name)) selectedTags.value.push(t.name)
    }
  }
}

function handleAdoptAiCategory(cat: { name: string; isExisting: boolean; categoryId: number | null; parentName: string | null }) {
  if (cat.isExisting && cat.categoryId) {
    // 已有分类：查找是否是二级分类
    const found = categories.value.find(c => c.id === cat.categoryId)
    if (found && found.parentId && found.parentId !== 0) {
      categoryValue.value = [found.parentId, cat.categoryId]
    } else {
      categoryValue.value = [cat.categoryId]
    }
  } else if (cat.parentName) {
    // 新的二级分类：先找到父分类
    const parent = categories.value.find(c => c.name === cat.parentName && c.parentId === 0)
    if (parent) {
      newCategoryName.value = cat.name
      newCategoryParentId.value = parent.id
      handleAddCategory()
    } else {
      // 父分类也不存在，先创建父分类再创建子分类
      newCategoryName.value = cat.parentName
      newCategoryParentId.value = undefined
      handleAddCategory()
      // 再创建子分类
      const parentTemp = categories.value.find(c => c.name === `${cat.parentName}（新）`)
      if (parentTemp) {
        newCategoryName.value = cat.name
        newCategoryParentId.value = parentTemp.id
        handleAddCategory()
      }
    }
  } else {
    // 新的一级分类
    newCategoryName.value = cat.name
    newCategoryParentId.value = undefined
    handleAddCategory()
  }
}

async function loadCategories() {
  try {
    const res = await categoryApi.listAll()
    categories.value = res.data
  } catch (e) { console.warn('加载分类失败', e) }
}

// ========== 标签 ==========
const tags = ref<TagVO[]>([])
const tagQuery = ref('')
const selectedTags = ref<(number | string)[]>([])
const tagSelectRef = ref()

const visibleTags = computed(() => {
  if (!tagQuery.value) return tags.value
  const kw = tagQuery.value.toLowerCase()
  return tags.value.filter(t => t.name.toLowerCase().startsWith(kw))
})

async function loadTags() {
  try {
    const res = await tagApi.listAll()
    tags.value = res.data
  } catch (e) { console.warn('加载标签失败', e) }
}

function filterTags(query: string) {
  tagQuery.value = query
}

function handleTagEnter() {
  const query = tagQuery.value.trim()
  if (!query) return
  if (selectedTags.value.length >= 5) return
  // 如果已有完全匹配的标签，选中它
  const exact = tags.value.find(t => t.name.toLowerCase() === query.toLowerCase())
  if (exact) {
    if (!selectedTags.value.includes(exact.id)) {
      selectedTags.value.push(exact.id)
    }
  } else {
    // 新标签，用字符串存储
    if (!selectedTags.value.includes(query)) {
      selectedTags.value.push(query)
    }
  }
  tagQuery.value = ''
  nextTick(() => {
    const input = tagSelectRef.value?.$el?.querySelector('.el-select__input') as HTMLInputElement | null
    if (input) { input.value = ''; input.dispatchEvent(new Event('input')) }
  })
}

function handleTagChange() {
  tagQuery.value = ''
  nextTick(() => {
    const input = tagSelectRef.value?.$el?.querySelector('.el-select__input') as HTMLInputElement | null
    if (input) { input.value = ''; input.dispatchEvent(new Event('input')) }
  })
}

// ========== 封面图 ==========
const coverInputRef = ref<HTMLInputElement>()
const pendingCoverFile = ref<File | null>(null)
const showCoverCropper = ref(false)
const coverCropperSrc = ref('')

function triggerCoverUpload() {
  coverInputRef.value?.click()
}

function openCoverCropper() {
  // 已有封面图时，打开裁剪器重新裁剪
  // 将绝对 URL 转为相对路径，通过 vite proxy 同源访问，避免 canvas 跨域污染
  const apiBase = useRuntimeConfig().public.apiBase as string
  const origin = apiBase.replace(/\/api\/?$/, '')
  const src = form.coverImage.startsWith(origin)
    ? form.coverImage.slice(origin.length)
    : form.coverImage
  coverCropperSrc.value = src
  showCoverCropper.value = true
}

async function handleCoverFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  // 读取为 base64 后打开裁剪器
  const reader = new FileReader()
  reader.onload = (ev) => {
    coverCropperSrc.value = ev.target?.result as string
    showCoverCropper.value = true
  }
  reader.readAsDataURL(file)
  if (coverInputRef.value) coverInputRef.value.value = ''
}

function handleCoverCropped(data: { blob: Blob; url: string }) {
  // 释放旧的 blob URL
  if (form.coverImage.startsWith('blob:')) URL.revokeObjectURL(form.coverImage)
  // 用裁剪后的 blob 作为预览和待上传文件
  form.coverImage = data.url
  const ext = data.blob.type === 'image/webp' ? 'webp' : data.blob.type === 'image/png' ? 'png' : 'jpg'
  pendingCoverFile.value = new File([data.blob], `cover.${ext}`, { type: data.blob.type })
  // 同时保存到 IndexedDB 以便自动恢复
  saveCoverToIDB(data.blob)
}

// ========== IndexedDB 封面图持久化（跨刷新恢复） ==========
const IDB_NAME = 'weblog_draft'
const IDB_STORE = 'cover'

function openIDB(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(IDB_NAME, 1)
    req.onupgradeneeded = () => { req.result.createObjectStore(IDB_STORE) }
    req.onsuccess = () => resolve(req.result)
    req.onerror = () => reject(req.error)
  })
}

async function saveCoverToIDB(blob: Blob) {
  try {
    const db = await openIDB()
    const tx = db.transaction(IDB_STORE, 'readwrite')
    tx.objectStore(IDB_STORE).put(blob, 'pending_cover')
    db.close()
  } catch { /* ignore */ }
}

async function loadCoverFromIDB(): Promise<Blob | null> {
  try {
    const db = await openIDB()
    return new Promise((resolve) => {
      const tx = db.transaction(IDB_STORE, 'readonly')
      const req = tx.objectStore(IDB_STORE).get('pending_cover')
      req.onsuccess = () => { db.close(); resolve(req.result || null) }
      req.onerror = () => { db.close(); resolve(null) }
    })
  } catch { return null }
}

async function clearCoverFromIDB() {
  try {
    const db = await openIDB()
    const tx = db.transaction(IDB_STORE, 'readwrite')
    tx.objectStore(IDB_STORE).delete('pending_cover')
    db.close()
  } catch { /* ignore */ }
}

// ========== 定时发布 ==========
const showScheduleDialog = ref(false)
const scheduleTime = ref<string | null>(null)
const customTimeValue = ref(30)
const customTimeUnit = ref<'minutes' | 'hours' | 'days'>('minutes')

function formatLocalDateTime(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function onScheduleDialogOpen() {
  if (!scheduleTime.value) {
    scheduleTime.value = formatLocalDateTime(new Date())
  }
}

function applyPreset(hours: number) {
  scheduleTime.value = formatLocalDateTime(new Date(Date.now() + hours * 3600000))
}

function applyCustomPreset() {
  const multiplier = customTimeUnit.value === 'minutes' ? 60000 : customTimeUnit.value === 'hours' ? 3600000 : 86400000
  scheduleTime.value = formatLocalDateTime(new Date(Date.now() + customTimeValue.value * multiplier))
}

// ========== 自动保存 ==========
const LOCAL_STORAGE_KEY = 'weblog_new_post_draft'
const EDIT_STORAGE_PREFIX = 'weblog_edit_draft_'
let autoSaveTimer: ReturnType<typeof setTimeout> | null = null
// 脏检测：记录上次保存时的快照
let lastSavedSnapshot = ''

function buildSnapshot(): string {
  return JSON.stringify({
    title: form.title, content: form.content, summary: form.summary,
    coverImage: form.coverImage, slug: form.slug,
    seoTitle: form.seoTitle, seoDescription: form.seoDescription, seoKeywords: form.seoKeywords,
    categoryValue: categoryValue.value, selectedTags: selectedTags.value,
    previewTheme: form.previewTheme, codeTheme: form.codeTheme,
  })
}

function buildLocalData() {
  return {
    title: form.title, content: form.content, summary: form.summary,
    coverImage: form.coverImage.startsWith('blob:') ? '__pending_idb__' : form.coverImage,
    slug: form.slug, seoTitle: form.seoTitle, seoDescription: form.seoDescription,
    seoKeywords: form.seoKeywords, categoryValue: categoryValue.value,
    selectedTags: selectedTags.value, previewTheme: form.previewTheme, codeTheme: form.codeTheme,
    aiMeta: aiMetaRef.value?.getSnapshot() ?? null,
  }
}

async function doAutoSave(manual = false) {
  const snapshot = buildSnapshot()
  if (snapshot === lastSavedSnapshot) return
  const label = manual ? '已手动保存' : '已自动保存'

  if (isEdit.value && postId.value) {
    if (!form.title) return
    if (form.status === 'published' || form.status === 'scheduled') {
      // 已发布/定时文章：只存浏览器，更新发布时才写入数据库
      try {
        localStorage.setItem(EDIT_STORAGE_PREFIX + postId.value, JSON.stringify(buildLocalData()))
        lastSavedSnapshot = snapshot
        autoSaveText.value = `${label} ${new Date().toLocaleTimeString()}`
      } catch { /* ignore */ }
    } else {
      // 草稿文章：服务端自动保存
      try {
        await postApi.autoSave(postId.value, { title: form.title, content: form.content })
        lastSavedSnapshot = snapshot
        autoSaveText.value = `${label} ${new Date().toLocaleTimeString()}`
      } catch { /* ignore */ }
    }
  } else {
    // 新文章：localStorage
    if (!form.title && !form.content) return
    try {
      localStorage.setItem(LOCAL_STORAGE_KEY, JSON.stringify(buildLocalData()))
      lastSavedSnapshot = snapshot
      autoSaveText.value = `${label} ${new Date().toLocaleTimeString()}`
    } catch { /* ignore */ }
  }
}

function startAutoSave() {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  autoSaveTimer = setTimeout(doAutoSave, 15000)
}

function restoreLocalDraft() {
  try {
    const raw = localStorage.getItem(LOCAL_STORAGE_KEY)
    if (!raw) return
    const data = JSON.parse(raw)
    if (data.title) form.title = data.title
    if (data.content) form.content = data.content
    if (data.summary) form.summary = data.summary
    if (data.coverImage && data.coverImage !== '__pending_idb__') {
      form.coverImage = data.coverImage
    }
    if (data.slug) form.slug = data.slug
    if (data.seoTitle) form.seoTitle = data.seoTitle
    if (data.seoDescription) form.seoDescription = data.seoDescription
    if (data.seoKeywords) form.seoKeywords = data.seoKeywords
    if (data.categoryValue) categoryValue.value = data.categoryValue
    if (data.selectedTags) selectedTags.value = data.selectedTags
    if (data.previewTheme) form.previewTheme = data.previewTheme
    if (data.codeTheme) form.codeTheme = data.codeTheme
    autoSaveText.value = '已恢复上次编辑的草稿'

    // 恢复 AI 生成结果
    if (data.aiMeta) {
      nextTick(() => aiMetaRef.value?.restoreSnapshot(data.aiMeta))
    }

    // 恢复 IndexedDB 中的封面图
    if (data.coverImage === '__pending_idb__') {
      loadCoverFromIDB().then((blob) => {
        if (blob) {
          form.coverImage = URL.createObjectURL(blob)
          pendingCoverFile.value = new File([blob], 'cover.jpg', { type: blob.type })
        }
      })
    }
  } catch { /* ignore */ }
}

function clearLocalDraft() {
  try { localStorage.removeItem(LOCAL_STORAGE_KEY) } catch { /* ignore */ }
  if (postId.value) {
    try { localStorage.removeItem(EDIT_STORAGE_PREFIX + postId.value) } catch { /* ignore */ }
  }
  clearCoverFromIDB()
}

watch(
  [
    () => form.title, () => form.content, () => form.summary,
    () => form.coverImage, () => form.slug,
    () => form.seoTitle, () => form.seoDescription, () => form.seoKeywords,
    categoryValue, selectedTags,
  ],
  () => { startAutoSave() },
)

// ========== 构建提交参数 ==========
async function syncContentBeforeSave() {
  // 上传待发布的封面图
  if (pendingCoverFile.value) {
    try {
      const res = await uploadApi.image(pendingCoverFile.value, 'cover')
      // 释放 blob URL
      if (form.coverImage.startsWith('blob:')) URL.revokeObjectURL(form.coverImage)
      form.coverImage = res.data
      pendingCoverFile.value = null
      clearCoverFromIDB()
    } catch (e: unknown) {
      ElMessage.error('封面上传失败: ' + ((e as Error).message || '未知错误'))
      throw e
    }
  }
}

function buildParams(status: string, scheduledTime?: string): PostCreateParams {
  const existingTagIds: number[] = []
  const newTagNames: string[] = []
  for (const t of selectedTags.value) {
    if (typeof t === 'number') existingTagIds.push(t)
    else newTagNames.push(t)
  }

  let categoryId: number | undefined
  let subCategoryId: number | undefined
  let newCatName: string | undefined
  let newCatParentId: number | undefined

  if (categoryValue.value.length === 1) {
    const id = categoryValue.value[0]
    const pending = pendingCategories.value.find(p => p.tempId === id)
    if (pending) {
      // 新建的一级分类
      newCatName = pending.name
      newCatParentId = 0
    } else {
      categoryId = id
    }
  } else if (categoryValue.value.length >= 2) {
    const parentCatId = categoryValue.value[0]
    const childCatId = categoryValue.value[1]
    const pendingChild = pendingCategories.value.find(p => p.tempId === childCatId)
    if (pendingChild) {
      // 新建的二级分类，父分类是已有的
      categoryId = parentCatId
      newCatName = pendingChild.name
      newCatParentId = parentCatId
    } else {
      categoryId = parentCatId
      subCategoryId = childCatId
    }
  }

  return {
    title: form.title, content: form.content,
    summary: form.summary || undefined, coverImage: form.coverImage || undefined,
    slug: form.slug || undefined, categoryId, subCategoryId,
    tagIds: existingTagIds.length ? existingTagIds : undefined,
    newTagNames: newTagNames.length ? newTagNames : undefined,
    newCategoryName: newCatName, newCategoryParentId: newCatParentId,
    status, scheduledTime,
    seoTitle: form.seoTitle || undefined, seoDescription: form.seoDescription || undefined,
    seoKeywords: form.seoKeywords || undefined,
    isTop: form.isTop || undefined,
    previewTheme: form.previewTheme !== 'default' ? form.previewTheme : undefined,
    codeTheme: form.codeTheme !== 'atom' ? form.codeTheme : undefined,
  }
}

// ========== 发布 / 草稿 / 定时 / 保存定时修改 ==========
async function handleSaveScheduled() {
  if (!form.title.trim()) { ElMessage.warning('请输入文章标题'); return }
  savingType.value = 'scheduled'
  try {
    await syncContentBeforeSave()
    const params = buildParams('scheduled', form.scheduledTime)
    await postApi.update(postId.value!, params)
    ElMessage.success('定时文章已保存')
    router.push(getBackPath())
  } catch (e: unknown) { ElMessage.error((e as Error).message || '保存失败') }
  finally { savingType.value = '' }
}

async function handlePublish() {
  if (!form.title.trim()) { ElMessage.warning('请输入文章标题'); return }
  savingType.value = 'publish'
  try {
    await syncContentBeforeSave()
    const params = buildParams('published')
    if (isEdit.value && postId.value) {
      await postApi.update(postId.value, params)
      clearLocalDraft()
      ElMessage.success('更新成功')
    } else {
      await postApi.create(params)
      clearLocalDraft()
      ElMessage.success('发布成功')
    }
    router.push(getBackPath())
  } catch (e: unknown) { ElMessage.error((e as Error).message || '发布失败') }
  finally { savingType.value = '' }
}

// 编辑器 Ctrl+S 保存回调
function handleEditorSave() {
  doAutoSave(true)
}

async function handleSaveDraft() {
  if (!form.title.trim()) { ElMessage.warning('请输入文章标题'); return }
  savingType.value = 'draft'
  try {
    await syncContentBeforeSave()
    const params = buildParams('draft')
    if (isEdit.value && postId.value) {
      await postApi.update(postId.value, params)
      ElMessage.success('草稿已保存')
    } else {
      await postApi.create(params)
      clearLocalDraft()
      ElMessage.success('草稿已保存')
    }
    router.push(getBackPath())
  } catch (e: unknown) { ElMessage.error((e as Error).message || '保存失败') }
  finally { savingType.value = '' }
}

async function handleRevertToDraft() {
  if (!postId.value) return
  savingType.value = 'revert'
  try {
    await syncContentBeforeSave()
    const params = buildParams('draft')
    await postApi.update(postId.value, params)
    ElMessage.success('已撤回到草稿')
    router.push(getBackPath())
  } catch (e: unknown) { ElMessage.error((e as Error).message || '撤回失败') }
  finally { savingType.value = '' }
}

async function handleSchedulePublish() {
  if (!form.title.trim()) { ElMessage.warning('请输入文章标题'); return }
  if (!scheduleTime.value) { ElMessage.warning('请选择发布时间'); return }
  if (new Date(scheduleTime.value).getTime() <= Date.now()) { ElMessage.warning('发布时间必须在未来'); return }
  savingType.value = 'schedule'
  try {
    await syncContentBeforeSave()
    const params = buildParams('scheduled', scheduleTime.value)
    if (isEdit.value && postId.value) {
      await postApi.update(postId.value, params)
    } else {
      await postApi.create(params)
      clearLocalDraft()
    }
    ElMessage.success('定时发布设置成功')
    showScheduleDialog.value = false
    router.push(getBackPath())
  } catch (e: unknown) { ElMessage.error((e as Error).message || '设置失败') }
  finally { savingType.value = '' }
}

// ========== 返回 ==========
function getBackPath(): string {
  const from = route.query.from as string | undefined
  return from ? `/post?from=${from}` : '/post'
}

function goBack() {
  const hasChanges = buildSnapshot() !== lastSavedSnapshot
  if (hasChanges) {
    ElMessageBox.confirm('当前内容尚未保存，确定要离开吗？', '提示', {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
      type: 'warning',
    }).then(() => {
      router.push(getBackPath())
    }).catch(() => { /* 取消，留在页面 */ })
  } else {
    router.push(getBackPath())
  }
}

// ========== 加载编辑数据 ==========
async function loadPost(id: number) {
  try {
    const res = await postApi.getById(id)
    const post = res.data
    form.title = post.title
    form.content = post.content || ''
    form.summary = post.summary || ''
    form.coverImage = post.coverImage || ''
    form.slug = post.slug || ''
    form.seoTitle = post.seoTitle || ''
    form.seoDescription = post.seoDescription || ''
    form.seoKeywords = post.seoKeywords || ''
    form.status = post.status
    form.isTop = post.isTop || false
    form.scheduledTime = post.scheduledTime || ''
    form.previewTheme = post.previewTheme || 'default'
    form.codeTheme = post.codeTheme || 'atom'

    // 分类
    if (post.subCategoryId) {
      categoryValue.value = [post.categoryId, post.subCategoryId]
    } else if (post.categoryId) {
      categoryValue.value = [post.categoryId]
    }

    // 标签
    selectedTags.value = (post.tags || []).map(t => t.id)

    // 恢复已发布/定时文章的本地编辑草稿
    if ((post.status === 'published' || post.status === 'scheduled') && postId.value) {
      try {
        const raw = localStorage.getItem(EDIT_STORAGE_PREFIX + postId.value)
        if (raw) {
          const data = JSON.parse(raw)
          if (data.title) form.title = data.title
          if (data.content) form.content = data.content
          if (data.summary !== undefined) form.summary = data.summary
          if (data.slug !== undefined) form.slug = data.slug
          if (data.seoTitle !== undefined) form.seoTitle = data.seoTitle
          if (data.seoDescription !== undefined) form.seoDescription = data.seoDescription
          if (data.seoKeywords !== undefined) form.seoKeywords = data.seoKeywords
          if (data.previewTheme) form.previewTheme = data.previewTheme
          if (data.codeTheme) form.codeTheme = data.codeTheme
          if (data.categoryValue) categoryValue.value = data.categoryValue
          if (data.selectedTags) selectedTags.value = data.selectedTags
          autoSaveText.value = '已恢复未发布的编辑内容'
          // 恢复 AI 生成结果
          if (data.aiMeta) {
            nextTick(() => aiMetaRef.value?.restoreSnapshot(data.aiMeta))
          }
        }
      } catch { /* ignore */ }
    }

    // 初始化快照（恢复草稿后的状态）
    lastSavedSnapshot = buildSnapshot()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '加载文章失败') }
}

// ========== 初始化 ==========
onMounted(async () => {
  await Promise.all([loadCategories(), loadTags()])

  const id = route.query.id
  if (id) {
    postId.value = Number(id)
    isEdit.value = true
    await loadPost(postId.value)
  } else {
    // 新文章：尝试恢复 localStorage 中的草稿
    restoreLocalDraft()
  }

  // 初始化快照（loadPost 或 restoreLocalDraft 之后）
  lastSavedSnapshot = buildSnapshot()

  // 绑定原生 keydown 到标签 select 的 input，实现回车创建新标签
  nextTick(() => {
    const input = tagSelectRef.value?.$el?.querySelector('input') as HTMLInputElement | null
    if (input) {
      input.addEventListener('keydown', (e: KeyboardEvent) => {
        if (e.key === 'Enter' && tagQuery.value.trim() && visibleTags.value.length === 0) {
          e.preventDefault()
          e.stopPropagation()
          handleTagEnter()
        }
      }, true)
    }
  })
})

onUnmounted(() => {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  // 释放 blob URL
  if (form.coverImage.startsWith('blob:')) URL.revokeObjectURL(form.coverImage)
})
</script>

<style scoped lang="scss">
.post-editor-fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  background: var(--el-bg-color);
  display: flex;
  flex-direction: column;
}

.editor-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 52px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  flex-shrink: 0;

  .toolbar-left {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .editor-title {
    font-size: 14px;
    font-weight: 700;
    color: var(--el-text-color-primary);
    letter-spacing: 0.3px;
  }
  .auto-save-status {
    display: flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--el-text-color-disabled);
  }
  .schedule-badge {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    font-size: 12px;
    color: var(--el-color-warning);
    background: var(--el-color-warning-light-9);
    padding: 2px 10px;
    border-radius: 10px;
    font-weight: 600;
  }
}

.editor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.editor-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 24px 0;

  .title-input {
    width: 100%;
    font-size: 22px;
    font-weight: 700;
    border: none;
    outline: none;
    padding: 8px 0;
    margin-bottom: 12px;
    background: transparent;
    color: var(--el-text-color-primary);
    border-bottom: 1px solid var(--el-border-color-extra-light);
    transition: border-color 0.15s;
    letter-spacing: 0.3px;
    &::placeholder { color: var(--el-text-color-placeholder); font-weight: 400; }
    &:focus { border-bottom-color: var(--el-color-primary); }
  }
  .editor-wrap {
    flex: 1;
    overflow: hidden;
  }
}

.editor-sidebar {
  width: 300px;
  border-left: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  flex-shrink: 0;
  position: relative;
  transition: width 0.25s ease;

  &.collapsed {
    width: 0;
    border-left: none;
    .sidebar-scroll { display: none; }
  }

  .sidebar-toggle {
    position: absolute;
    left: -14px;
    top: 50%;
    transform: translateY(-50%);
    width: 28px;
    height: 28px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-lighter);
    border-radius: 50%;
    cursor: pointer;
    z-index: 10;
    transition: all 0.15s;
    color: var(--el-text-color-disabled);
    &:hover {
      background: var(--el-color-primary-light-9);
      color: var(--el-color-primary);
      border-color: var(--el-color-primary-light-7);
    }
  }

  .sidebar-scroll {
    height: 100%;
    overflow-y: auto;
    padding: 16px;
  }
}

.ai-meta-top {
  margin-bottom: 14px;
  padding-bottom: 14px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
}

.setting-group {
  margin-bottom: 18px;

  .setting-label-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }
  .setting-label {
    font-size: 12px;
    font-weight: 600;
    color: var(--el-text-color-disabled);
    margin-bottom: 6px;
    display: block;
    text-transform: uppercase;
    letter-spacing: 0.5px;
  }
  .setting-divider {
    padding-top: 14px;
    border-top: 1px solid var(--el-border-color-extra-light);
  }
  .label-hint {
    font-weight: 400;
    font-size: 11px;
    color: var(--el-text-color-placeholder);
    text-transform: none;
    letter-spacing: 0;
  }
}

.full-width { width: 100%; }
.mb-8 { margin-bottom: 8px; }

.cover-upload-area {
  .cover-preview {
    position: relative;
    width: 100%;
    aspect-ratio: 16 / 9;
    border-radius: 10px;
    overflow: hidden;
    cursor: pointer;
    .cover-img { width: 100%; height: 100%; }
    .cover-overlay {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      background: rgba(0, 0, 0, 0.4);
      color: #fff;
      font-size: 13px;
      font-weight: 500;
      opacity: 0;
      transition: opacity 0.15s;
    }
    &:hover .cover-overlay { opacity: 1; }
  }
  .cover-placeholder {
    width: 100%;
    aspect-ratio: 16 / 9;
    border: 2px dashed var(--el-border-color-light);
    border-radius: 10px;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 6px;
    cursor: pointer;
    color: var(--el-text-color-placeholder);
    font-size: 13px;
    transition: all 0.15s;
    &:hover {
      border-color: var(--el-color-primary);
      color: var(--el-color-primary);
      background: var(--el-color-primary-light-9);
    }
  }
}

.time-presets {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}

.custom-time-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
}

// ========== 扁平化组件覆盖 ==========

// 带 prepend 的输入框（如 Slug）
:deep(.el-input-group) {
  .el-input-group__prepend {
    box-shadow: none;
    border: 1px solid var(--el-border-color-light);
    border-right: none;
    border-radius: 8px 0 0 8px;
    background: var(--el-fill-color-light);
    padding: 0 12px;
    font-size: 13px;
    color: var(--el-text-color-secondary);
  }
  .el-input__wrapper {
    border-radius: 0 8px 8px 0 !important;
  }
}

// 弹窗
:deep(.el-dialog) {
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  .el-dialog__header {
    padding: 20px 24px 12px;
    .el-dialog__title { font-weight: 700; font-size: 16px; }
  }
  .el-dialog__body { padding: 12px 24px 20px; }
}

// 分割线
:deep(.el-divider--vertical) {
  border-color: var(--el-border-color-extra-light);
}

.textarea-clearable {
  position: relative;
  .textarea-clear-btn {
    position: absolute;
    top: 6px;
    right: 6px;
    cursor: pointer;
    color: var(--el-text-color-placeholder);
    font-size: 14px;
    z-index: 1;
    opacity: 0;
    transition: opacity 0.15s, color 0.15s;
    &:hover { color: var(--el-text-color-regular); }
  }
  &:hover .textarea-clear-btn {
    opacity: 1;
  }
}
</style>

<style lang="scss">
.category-cascader-popper {
  // 固定面板总宽度，避免二级展开时一级菜单被挤动
  .el-cascader-panel {
    min-width: 360px;
  }
  .el-cascader-menu {
    min-width: 180px;
  }
}

.ai-meta-popover {
  padding: 12px !important;
  border-radius: 12px !important;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.12) !important;
}
</style>
