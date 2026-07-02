<template>
  <div class="post-editor-fullscreen">
    <header class="editor-toolbar">
        <div class="toolbar-left">
          <el-button text @click="goBack"><el-icon><ArrowLeft /></el-icon> 返回</el-button>
          <el-divider direction="vertical" />
          <span class="editor-title">{{ editorTitle }}</span>
          <el-alert
            v-if="draftModeText"
            :title="draftModeText"
            type="info"
            :closable="false"
            show-icon
            class="draft-mode-alert"
          />
          <div v-if="isEdit && isDraft" class="draft-nav-wrap">
            <el-button text size="small" :disabled="!canGoDraftPrev || draftNavLoading" @click="goDraftNeighbor(-1)">
              <el-icon><ArrowLeft /></el-icon> 上一篇
            </el-button>
          <span class="draft-nav-stat">{{ draftNavText }}</span>
          <el-button text size="small" :disabled="!canGoDraftNext || draftNavLoading" @click="goDraftNeighbor(1)">
            下一篇 <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
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
            <LazyMarkdownEditor v-model="form.content" v-model:preview-theme="form.previewTheme" v-model:code-theme="form.codeTheme" :height="editorHeight" :image-watermark="imageWatermark" @save="handleEditorSave" />
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
              :current-summary="form.summary"
              :current-seo-title="form.seoTitle"
              :current-seo-description="form.seoDescription"
              :current-seo-keywords="form.seoKeywords"
              :current-slug="form.slug"
              :has-category="categoryValue.length > 0"
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
              <div class="category-actions">
                <el-button v-if="selectedTemporaryCategory" text type="primary" size="small" @click="openEditTemporaryCategoryDialog">修改临时分类</el-button>
                <el-button text type="primary" size="small" @click="openAddCategoryDialog">+ 新建</el-button>
              </div>
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
              <div v-if="form.coverImage" class="cover-preview" @click="triggerCoverUpload">
                <AppImage :src="form.coverImage" fit="cover" class="cover-img" />
                <div class="cover-overlay">重新选择 / 裁剪</div>
              </div>
              <div v-else class="cover-placeholder" @click="triggerCoverUpload">
                <el-icon :size="28"><Plus /></el-icon>
                <span>上传封面</span>
              </div>
              <input ref="coverInputRef" type="file" accept="image/*" style="display:none" @change="handleCoverFileChange" />
            </div>
          </div>

          <!-- 图片水印 -->
          <div class="setting-group">
            <div class="setting-label-row">
              <label class="setting-label">图片水印</label>
              <el-button text type="primary" size="small" @click="resetImageWatermark">恢复默认</el-button>
            </div>
            <el-switch v-model="imageWatermark.enabled" active-text="启用" inactive-text="关闭" />
            <div class="watermark-panel" :class="{ disabled: !imageWatermark.enabled }">
              <el-checkbox-group v-model="imageWatermark.targets">
                <el-checkbox label="cover">封面图</el-checkbox>
                <el-checkbox label="content">正文图片</el-checkbox>
              </el-checkbox-group>
              <el-input v-model="imageWatermark.text" maxlength="40" placeholder="输入水印文字，例如站点名 / 作者名" clearable />
              <el-radio-group v-model="imageWatermark.mode">
                <el-radio-button label="single">单点</el-radio-button>
                <el-radio-button label="tile">平铺</el-radio-button>
              </el-radio-group>
              <el-select v-model="imageWatermark.position" class="full-width">
                <el-option label="左上角" value="top-left" />
                <el-option label="右上角" value="top-right" />
                <el-option label="左下角" value="bottom-left" />
                <el-option label="右下角" value="bottom-right" />
                <el-option label="居中" value="center" />
              </el-select>
              <div class="watermark-slider-row">
                <span>字号</span>
                <el-slider v-model="imageWatermark.fontSize" :min="14" :max="48" />
              </div>
              <div class="watermark-slider-row">
                <span>倾斜</span>
                <el-slider v-model="imageWatermark.angle" :min="-60" :max="60" />
              </div>
              <div class="watermark-slider-row">
                <span>透明度</span>
                <el-slider v-model="watermarkOpacityPercent" :min="10" :max="100" />
              </div>
              <div class="watermark-slider-row">
                <span>粗细</span>
                <el-slider v-model="watermarkFontWeight" :min="400" :max="800" :step="100" />
              </div>
              <template v-if="imageWatermark.mode === 'tile'">
                <div class="watermark-slider-row">
                  <span>横距</span>
                  <el-slider v-model="imageWatermark.spacingX" :min="40" :max="300" />
                </div>
                <div class="watermark-slider-row">
                  <span>纵距</span>
                  <el-slider v-model="imageWatermark.spacingY" :min="30" :max="240" />
                </div>
              </template>
              <el-input v-model="imageWatermark.color" maxlength="16" placeholder="颜色，例如 #ffffff" />
              <div class="watermark-preview">
                <div class="watermark-preview__label">预览示意</div>
                <div class="watermark-preview__canvas" :class="{ 'is-tile': imageWatermark.mode === 'tile' }">
                  <div
                    v-if="imageWatermark.text.trim()"
                    class="watermark-preview__text"
                    :class="`is-${imageWatermark.position}`"
                    :style="watermarkPreviewStyle"
                  >
                    {{ imageWatermark.text }}
                  </div>
                  <template v-if="imageWatermark.mode === 'tile' && imageWatermark.text.trim()">
                    <div
                      v-for="index in 16"
                      :key="index"
                      class="watermark-preview__tile"
                      :style="getTilePreviewStyle(index)"
                    >
                      {{ imageWatermark.text }}
                    </div>
                  </template>
                </div>
              </div>
              <p class="watermark-tip">仅对本次新上传的封面和正文图片生效，不会回写历史图片。</p>
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

          <!-- 展示范围 -->
          <div class="setting-group">
            <label class="setting-label setting-divider">展示范围</label>
            <el-checkbox v-model="form.topicOnly">仅在专题内展示</el-checkbox>
            <p class="setting-tip">勾选后不出现在首页、分类、标签和搜索列表中，仍可在专题目录中阅读。</p>
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

    <!-- 临时分类弹窗 -->
    <el-dialog v-model="showAddCategory" :title="editingTempCategoryId === null ? '新建临时分类' : '修改临时分类'" width="420px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="归属一级">
          <el-select v-model="newCategoryParentId" placeholder="作为一级分类" clearable class="full-width" :disabled="editingTempCategoryId !== null">
            <el-option v-for="c in topCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="editingTempParentCategoryId !== null || !newCategoryParentId ? '一级分类' : '二级分类'">
          <el-input v-model="newCategoryName" maxlength="30" :placeholder="editingTempParentCategoryId !== null || !newCategoryParentId ? '输入一级分类名称' : '输入二级分类名称'" />
        </el-form-item>
        <el-form-item v-if="!newCategoryParentId || editingTempParentCategoryId !== null" label="二级分类">
          <el-input v-model="newChildCategoryName" maxlength="30" placeholder="可选：同时创建二级分类" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddCategory = false">取消</el-button>
        <el-button type="primary" :loading="addingCategory" @click="handleAddCategory">{{ editingTempCategoryId === null ? '创建临时分类' : '保存修改' }}</el-button>
      </template>
    </el-dialog>

    <!-- 封面裁剪弹窗 -->
    <LazyImageCropper v-if="showCoverCropper" v-model="showCoverCropper" :image-src="coverCropperSrc" :aspect-ratio="[14, 9]"
      output-type="image/webp" :max-output-width="1200" @crop="handleCoverCropped" />
  </div>
</template>

<script setup lang="ts">
import { ArrowLeft, ArrowRight, Clock, Timer, Plus, CircleClose } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router'
import { postApi, type PostCreateParams, type PostVO } from '~/api/content/post'
import { categoryApi, type CategoryVO } from '~/api/content/category'
import { tagApi, type TagVO } from '~/api/content/tag'
import { uploadApi } from '~/api/system/upload'
import { applyWatermarkToBlob, shouldApplyWatermark, snapshotImageWatermarkConfig, type ImageWatermarkConfig } from '~/utils/image/watermark'

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
const draftNavLoading = ref(false)
const draftNavPosts = ref<PostVO[]>([])

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
  topicOnly: false,
  scheduledTime: '',
  previewTheme: 'default',
  codeTheme: 'atom',
})

const imageWatermark = reactive<ImageWatermarkConfig>({
  enabled: true,
  targets: ['cover', 'content'],
    text: 'www.example.com',
  mode: 'tile',
  position: 'center',
  opacity: 0.1,
  fontSize: 15,
  angle: -20,
  spacingX: 210,
  spacingY: 180,
  fontWeight: 400,
  color: '#ffffff',
})

const defaultImageWatermark = {
  enabled: true,
  targets: ['cover', 'content'] as Array<'cover' | 'content'>,
  text: 'www.example.com',
  mode: 'tile' as const,
  position: 'center' as const,
  opacity: 0.1,
  fontSize: 15,
  angle: -20,
  spacingX: 210,
  spacingY: 180,
  fontWeight: 400,
  color: '#ffffff',
}

const watermarkOpacityPercent = computed({
  get: () => Math.round(imageWatermark.opacity * 100),
  set: (value: number) => {
    imageWatermark.opacity = Math.min(Math.max(value / 100, 0.1), 1)
  }
})

const watermarkFontWeight = computed({
  get: () => imageWatermark.fontWeight,
  set: (value: number) => {
    imageWatermark.fontWeight = value
  }
})

const watermarkPreviewStyle = computed(() => ({
  color: imageWatermark.color,
  opacity: String(imageWatermark.opacity),
  fontSize: `${Math.max(12, Math.round(imageWatermark.fontSize * 0.7))}px`,
  fontWeight: String(imageWatermark.fontWeight),
  transform: imageWatermark.mode === 'single'
    ? `${imageWatermark.position === 'center' ? 'translate(-50%, -50%) ' : ''}rotate(${imageWatermark.angle}deg)`
    : `rotate(${imageWatermark.angle}deg)`,
}))

function getTilePreviewStyle(index: number) {
  const col = (index - 1) % 4
  const row = Math.floor((index - 1) / 4)
  const stepX = Math.max(72, imageWatermark.spacingX * 0.78)
  const stepY = Math.max(40, imageWatermark.spacingY * 0.42)
  return {
    left: `${-12 + col * stepX}px`,
    top: `${-8 + row * stepY}px`,
    color: imageWatermark.color,
    opacity: String(imageWatermark.opacity),
    fontSize: `${Math.max(11, Math.round(imageWatermark.fontSize * 0.55))}px`,
    fontWeight: String(imageWatermark.fontWeight),
    transform: `rotate(${imageWatermark.angle}deg)`,
  }
}

function resetImageWatermark() {
  Object.assign(imageWatermark, defaultImageWatermark)
}

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
const draftModeText = computed(() => {
  if (!isEdit.value) {
    return '当前为新建草稿，保存后会创建新的草稿记录。'
  }
  if (isDraft.value) {
    return '当前编辑的是历史草稿，保存将直接更新这篇草稿。'
  }
  return ''
})
const currentDraftIndex = computed(() => draftNavPosts.value.findIndex(post => post.id === postId.value))
const canGoDraftPrev = computed(() => currentDraftIndex.value > 0)
const canGoDraftNext = computed(() => currentDraftIndex.value >= 0 && currentDraftIndex.value < draftNavPosts.value.length - 1)
const draftNavText = computed(() => {
  if (!isEdit.value || !isDraft.value || draftNavPosts.value.length === 0 || currentDraftIndex.value < 0) {
    return '0 / 0'
  }
  return `${currentDraftIndex.value + 1} / ${draftNavPosts.value.length}`
})

function formatScheduleDisplay(t: string): string {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

// ========== 分类 ==========
type CategoryOption = CategoryVO & { isTemporary?: boolean }

const categories = ref<CategoryOption[]>([])
const categoryValue = ref<number[]>([])
let temporaryCategoryId = -1

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
const selectedTemporaryCategory = computed(() => {
  const selectedId = categoryValue.value.at(-1)
  return selectedId ? categories.value.find(c => c.id === selectedId && c.isTemporary) : undefined
})

// 新建分类：先创建本地临时分类，发布/定时时再交由后端落库
const showAddCategory = ref(false)
const addingCategory = ref(false)
const newCategoryName = ref('')
const newChildCategoryName = ref('')
const newCategoryParentId = ref<number | undefined>(undefined)
const editingTempCategoryId = ref<number | null>(null)
const editingTempParentCategoryId = ref<number | null>(null)

function openAddCategoryDialog() {
  editingTempCategoryId.value = null
  editingTempParentCategoryId.value = null
  newCategoryName.value = ''
  newChildCategoryName.value = ''
  const selectedTopId = categoryValue.value[0]
  newCategoryParentId.value = selectedTopId && selectedTopId > 0 ? selectedTopId : undefined
  showAddCategory.value = false
  nextTick(() => { showAddCategory.value = true })
}

function openEditTemporaryCategoryDialog() {
  const temp = selectedTemporaryCategory.value
  if (!temp) return
  const tempParent = temp.parentId ? categories.value.find(c => c.id === temp.parentId && c.isTemporary) : undefined
  editingTempCategoryId.value = temp.id
  editingTempParentCategoryId.value = tempParent?.id ?? null
  newCategoryName.value = tempParent ? tempParent.name : temp.name
  newChildCategoryName.value = tempParent ? temp.name : ''
  newCategoryParentId.value = temp.parentId && temp.parentId !== 0 ? temp.parentId : undefined
  showAddCategory.value = false
  nextTick(() => { showAddCategory.value = true })
}

function appendCategory(category: CategoryOption) {
  const index = categories.value.findIndex(item => item.id === category.id)
  if (index >= 0) categories.value.splice(index, 1, category)
  else categories.value.push(category)
}

function createTemporaryCategory(name: string, parentId: number): CategoryOption {
  return {
    id: temporaryCategoryId--,
    name,
    slug: '',
    description: '',
    parentId,
    sortOrder: 0,
    articleCount: 0,
    createTime: '',
    updateTime: '',
    isTemporary: true,
  }
}

async function handleAddCategory() {
  if (!newCategoryName.value.trim()) { ElMessage.warning('请输入分类名称'); return }
  addingCategory.value = true
  try {
    const name = newCategoryName.value.trim()
    const parentId = newCategoryParentId.value || 0

    if (editingTempCategoryId.value !== null) {
      const target = categories.value.find(item => item.id === editingTempCategoryId.value && item.isTemporary)
      if (!target) throw new Error('临时分类不存在')
      if (editingTempParentCategoryId.value !== null) {
        const parent = categories.value.find(item => item.id === editingTempParentCategoryId.value && item.isTemporary)
        if (!parent) throw new Error('临时一级分类不存在')
        const childName = newChildCategoryName.value.trim()
        if (!childName) { ElMessage.warning('请输入二级分类名称'); return }
        appendCategory({ ...parent, name })
        appendCategory({ ...target, name: childName })
      } else {
        appendCategory({ ...target, name })
      }
      startAutoSave()
      ElMessage.success('临时分类已更新')
      showAddCategory.value = false
      return
    }

    const created = createTemporaryCategory(name, parentId)
    appendCategory(created)

    const childName = newChildCategoryName.value.trim()
    if (parentId === 0 && childName) {
      const child = createTemporaryCategory(childName, created.id)
      appendCategory(child)
      categoryValue.value = [created.id, child.id]
    } else if (parentId === 0) {
      categoryValue.value = [created.id]
    } else {
      categoryValue.value = [parentId, created.id]
    }

    ElMessage.success(childName && parentId === 0 ? '临时一级分类和二级分类已创建' : '临时分类已创建')
    showAddCategory.value = false
    newCategoryName.value = ''
    newChildCategoryName.value = ''
    newCategoryParentId.value = undefined
    editingTempCategoryId.value = null
    editingTempParentCategoryId.value = null
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '分类创建失败')
  } finally {
    addingCategory.value = false
  }
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

async function handleAdoptAiCategory(cat: { name: string; isExisting: boolean; categoryId: number | null; parentName: string | null }) {
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
      await handleAddCategory()
    } else {
      // 父分类也不存在，一次创建父子分类
      newCategoryName.value = cat.parentName
      newChildCategoryName.value = cat.name
      newCategoryParentId.value = undefined
      await handleAddCategory()
    }
  } else {
    // 新的一级分类
    newCategoryName.value = cat.name
    newChildCategoryName.value = ''
    newCategoryParentId.value = undefined
    await handleAddCategory()
  }
}

async function loadCategories() {
  try {
    const res = await categoryApi.listAll()
    categories.value = res.data
  } catch (e) {
    if (import.meta.dev) {
      console.warn('加载分类失败', e)
    }
  }
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
  } catch (e) {
    if (import.meta.dev) {
      console.warn('加载标签失败', e)
    }
  }
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

async function handleCoverFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  // 读取为 base64 后打开裁剪器
  const reader = new FileReader()
  reader.onload = (ev) => {
    const src = ev.target?.result as string
    showCoverCropper.value = false
    coverCropperSrc.value = ''
    nextTick(() => {
      coverCropperSrc.value = src
      showCoverCropper.value = true
    })
  }
  reader.readAsDataURL(file)
  if (coverInputRef.value) coverInputRef.value.value = ''
}

async function handleCoverCropped(data: { blob: Blob; url: string }) {
  const watermarkConfig = snapshotImageWatermarkConfig(imageWatermark)
  const finalBlob = watermarkConfig && shouldApplyWatermark(watermarkConfig, 'cover')
    ? await applyWatermarkToBlob(data.blob, watermarkConfig)
    : data.blob
  const finalUrl = URL.createObjectURL(finalBlob)
  URL.revokeObjectURL(data.url)
  // 释放旧的 blob URL
  if (form.coverImage.startsWith('blob:')) URL.revokeObjectURL(form.coverImage)
  // 用裁剪后的 blob 作为预览和待上传文件
  form.coverImage = finalUrl
  const ext = finalBlob.type === 'image/webp' ? 'webp' : finalBlob.type === 'image/png' ? 'png' : 'jpg'
  pendingCoverFile.value = new File([finalBlob], `cover.${ext}`, { type: finalBlob.type })
  // 同时保存到 IndexedDB 以便自动恢复
  saveCoverToIDB(finalBlob)
}

// ========== IndexedDB 封面图持久化（跨刷新恢复） ==========
const IDB_NAME = 'weblog_draft'
const IDB_STORE = 'cover'

interface RequestError extends Error {
  code?: number
}

function getImageFileExtension(type: string) {
  if (type === 'image/webp') return 'webp'
  if (type === 'image/png') return 'png'
  if (type === 'image/gif') return 'gif'
  return 'jpg'
}

function isAuthExpiredError(error: unknown) {
  return (error as RequestError).code === 401
}

function showSaveError(error: unknown, fallback: string) {
  if (isAuthExpiredError(error)) {
    ElMessage.error('登录已过期，请重新登录；当前编辑内容已保存在本机草稿')
    return
  }
  ElMessage.error((error as Error).message || fallback)
}

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
    categoryValue: categoryValue.value, temporaryCategories: categories.value.filter(item => item.isTemporary), selectedTags: selectedTags.value,
    previewTheme: form.previewTheme, codeTheme: form.codeTheme,
    topicOnly: form.topicOnly,
    imageWatermark,
  })
}

function buildLocalData() {
  return {
    title: form.title, content: form.content, summary: form.summary,
    coverImage: form.coverImage.startsWith('blob:') ? '__pending_idb__' : form.coverImage,
    slug: form.slug, seoTitle: form.seoTitle, seoDescription: form.seoDescription,
    seoKeywords: form.seoKeywords, categoryValue: categoryValue.value,
    temporaryCategories: categories.value.filter(item => item.isTemporary),
    selectedTags: selectedTags.value, previewTheme: form.previewTheme, codeTheme: form.codeTheme,
    topicOnly: form.topicOnly,
    imageWatermark,
    aiMeta: aiMetaRef.value?.getSnapshot() ?? null,
  }
}

function saveEditLocalDataSilently(id: number) {
  try {
    localStorage.setItem(EDIT_STORAGE_PREFIX + id, JSON.stringify(buildLocalData()))
  } catch { /* ignore */ }
}

async function doAutoSave(manual = false) {
  const snapshot = buildSnapshot()
  if (snapshot === lastSavedSnapshot) {
    if (manual) autoSaveText.value = `内容已是最新 ${new Date().toLocaleTimeString()}`
    return
  }
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
        saveEditLocalDataSilently(postId.value)
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
    if (Array.isArray(data.temporaryCategories)) restoreTemporaryCategories(data.temporaryCategories)
    if (data.categoryValue) categoryValue.value = data.categoryValue
    if (data.selectedTags) selectedTags.value = data.selectedTags
    if (data.previewTheme) form.previewTheme = data.previewTheme
    if (data.codeTheme) form.codeTheme = data.codeTheme
    if (typeof data.topicOnly === 'boolean') form.topicOnly = data.topicOnly
    if (data.imageWatermark) Object.assign(imageWatermark, data.imageWatermark)
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
          pendingCoverFile.value = new File([blob], `cover.${getImageFileExtension(blob.type)}`, { type: blob.type })
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
    () => form.topicOnly,
    categoryValue, selectedTags,
  ],
  () => { startAutoSave() },
)

watch(
  imageWatermark,
  () => { startAutoSave() },
  { deep: true },
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
      if (!isAuthExpiredError(e)) {
        ElMessage.error('封面上传失败：' + ((e as Error).message || '未知错误'))
      }
      throw e
    }
  }
}

function restoreTemporaryCategories(items: CategoryOption[]) {
  for (const item of items) {
    if (!item?.isTemporary || typeof item.id !== 'number' || !item.name) continue
    appendCategory(item)
    if (item.id <= temporaryCategoryId) {
      temporaryCategoryId = item.id - 1
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
  let newCategoryName: string | undefined
  let newCategoryParentId: number | undefined
  let newChildCategoryName: string | undefined

  const selectedIds = categoryValue.value
  const selectedId = selectedIds.at(-1)
  const selectedTemp = selectedId ? categories.value.find(c => c.id === selectedId && c.isTemporary) : undefined
  const shouldCreateTempCategory = status !== 'draft' && !!selectedTemp

  if (shouldCreateTempCategory && selectedTemp) {
    const parent = selectedTemp.parentId ? categories.value.find(c => c.id === selectedTemp.parentId) : undefined
    if (parent?.isTemporary) {
      newCategoryName = parent.name
      newChildCategoryName = selectedTemp.name
      newCategoryParentId = 0
    } else if (parent && parent.id > 0) {
      categoryId = parent.id
      newCategoryName = selectedTemp.name
      newCategoryParentId = parent.id
    } else {
      newCategoryName = selectedTemp.name
      newCategoryParentId = 0
    }
  } else if (selectedIds.length === 1) {
    const current = categories.value.find(c => c.id === selectedIds[0])
    if (!current?.isTemporary) categoryId = selectedIds[0]
  } else if (selectedIds.length >= 2) {
    const parent = categories.value.find(c => c.id === selectedIds[0])
    const child = categories.value.find(c => c.id === selectedIds[1])
    if (!parent?.isTemporary) categoryId = selectedIds[0]
    if (!child?.isTemporary) subCategoryId = selectedIds[1]
  }

  return {
    title: form.title, content: form.content,
    summary: form.summary || undefined, coverImage: form.coverImage || undefined,
    slug: form.slug || undefined, categoryId, subCategoryId,
    tagIds: existingTagIds.length ? existingTagIds : undefined,
    newTagNames: newTagNames.length ? newTagNames : undefined,
    newCategoryName,
    newCategoryParentId,
    newChildCategoryName,
    status, scheduledTime,
    seoTitle: form.seoTitle || undefined, seoDescription: form.seoDescription || undefined,
    seoKeywords: form.seoKeywords || undefined,
    isTop: form.isTop || undefined,
    topicOnly: form.topicOnly || undefined,
    previewTheme: form.previewTheme,
    codeTheme: form.codeTheme,
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
    clearLocalDraft()
    lastSavedSnapshot = buildSnapshot()
    ElMessage.success('定时文章已保存')
    router.push(getBackPath())
  } catch (e: unknown) { showSaveError(e, '保存失败') }
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
      lastSavedSnapshot = buildSnapshot()
      ElMessage.success('更新成功')
    } else {
      await postApi.create(params)
      clearLocalDraft()
      lastSavedSnapshot = buildSnapshot()
      ElMessage.success('发布成功')
    }
    router.push(getBackPath())
  } catch (e: unknown) { showSaveError(e, '发布失败') }
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
      saveEditLocalDataSilently(postId.value)
      ElMessage.success('草稿已保存')
    } else {
      const res = await postApi.create(params)
      if (res.data?.id) {
        saveEditLocalDataSilently(res.data.id)
      }
      clearLocalDraft()
      ElMessage.success('草稿已保存')
    }
    lastSavedSnapshot = buildSnapshot()
    router.push(getBackPath())
  } catch (e: unknown) { showSaveError(e, '保存失败') }
  finally { savingType.value = '' }
}

async function handleRevertToDraft() {
  if (!postId.value) return
  savingType.value = 'revert'
  try {
    await syncContentBeforeSave()
    const params = buildParams('draft')
    await postApi.update(postId.value, params)
    lastSavedSnapshot = buildSnapshot()
    ElMessage.success('已撤回到草稿')
    router.push(getBackPath())
  } catch (e: unknown) { showSaveError(e, '撤回失败') }
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
      clearLocalDraft()
    } else {
      await postApi.create(params)
      clearLocalDraft()
    }
    lastSavedSnapshot = buildSnapshot()
    ElMessage.success('定时发布设置成功')
    showScheduleDialog.value = false
    router.push(getBackPath())
  } catch (e: unknown) { showSaveError(e, '设置失败') }
  finally { savingType.value = '' }
}

// ========== 返回 ==========
function getBackPath(): string {
  const from = route.query.from as string | undefined
  return from ? `/post?from=${from}` : '/post'
}

function hasUnsavedChanges(): boolean {
  return buildSnapshot() !== lastSavedSnapshot
}

async function confirmLeaveIfDirty(): Promise<boolean> {
  if (!hasUnsavedChanges()) {
    return true
  }
  try {
    await ElMessageBox.confirm('当前内容尚未保存，确定要离开吗？', '提示', {
      confirmButtonText: '离开',
      cancelButtonText: '继续编辑',
      type: 'warning',
    })
    return true
  } catch {
    return false
  }
}

function goBack() {
  router.push(getBackPath())
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
    form.topicOnly = post.topicOnly || false
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

    // 恢复本地编辑草稿，用于保留尚未落库的临时分类等编辑态
    if (postId.value) {
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
          if (typeof data.topicOnly === 'boolean') form.topicOnly = data.topicOnly
          if (data.imageWatermark) Object.assign(imageWatermark, data.imageWatermark)
          if (Array.isArray(data.temporaryCategories)) restoreTemporaryCategories(data.temporaryCategories)
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

async function loadDraftNavigation() {
  if (!isEdit.value || !postId.value) {
    draftNavPosts.value = []
    return
  }
  if (!isDraft.value) {
    draftNavPosts.value = []
    return
  }

  draftNavLoading.value = true
  try {
    const res = await postApi.page({ pageNum: 1, pageSize: 1000, status: 'draft' })
    draftNavPosts.value = res.data.records
  } catch (e) {
    if (import.meta.dev) {
      console.warn('加载草稿导航失败', e)
    }
    draftNavPosts.value = []
  } finally {
    draftNavLoading.value = false
  }
}

async function goDraftNeighbor(step: -1 | 1) {
  if (!isEdit.value || !isDraft.value || currentDraftIndex.value < 0) {
    return
  }
  const target = draftNavPosts.value[currentDraftIndex.value + step]
  if (!target) {
    return
  }
  navigateTo(`/post/create?id=${target.id}&from=drafts`)
}

let tagInputElement: HTMLInputElement | null = null

function handleTagInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && tagQuery.value.trim() && visibleTags.value.length === 0) {
    e.preventDefault()
    e.stopPropagation()
    handleTagEnter()
  }
}

function handleEditorShortcut(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 's') {
    e.preventDefault()
    void doAutoSave(true)
  }
}

// ========== 初始化 ==========
onMounted(async () => {
  window.addEventListener('keydown', handleEditorShortcut)
  await Promise.all([loadCategories(), loadTags()])

  // 绑定原生 keydown 到标签 select 的 input，实现回车创建新标签
  nextTick(() => {
    const input = tagSelectRef.value?.$el?.querySelector('input') as HTMLInputElement | null
    if (input) {
      tagInputElement = input
      input.addEventListener('keydown', handleTagInputKeydown, true)
    }
  })
})

watch(
  () => route.query.id,
  async (id) => {
    const nextId = typeof id === 'string' ? Number(id) : Number(id?.[0])
    if (Number.isFinite(nextId) && nextId > 0) {
      postId.value = nextId
      isEdit.value = true
      await loadPost(nextId)
      await loadDraftNavigation()
      return
    }

    postId.value = null
    isEdit.value = false
    draftNavPosts.value = []
    // 新文章：尝试恢复 localStorage 中的草稿
    restoreLocalDraft()
    lastSavedSnapshot = buildSnapshot()
  },
  { immediate: true }
)

onBeforeRouteLeave(async () => {
  return await confirmLeaveIfDirty()
})

onBeforeRouteUpdate(async (to) => {
  const nextId = typeof to.query.id === 'string' ? Number(to.query.id) : Number(to.query.id?.[0])
  if (Number.isFinite(nextId) && nextId !== postId.value) {
    return await confirmLeaveIfDirty()
  }
  return true
})

onUnmounted(() => {
  if (autoSaveTimer) clearTimeout(autoSaveTimer)
  window.removeEventListener('keydown', handleEditorShortcut)
  if (tagInputElement) {
    tagInputElement.removeEventListener('keydown', handleTagInputKeydown, true)
    tagInputElement = null
  }
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
  .draft-nav-wrap {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    margin-left: 6px;
    padding: 0 8px;
    border-left: 1px solid var(--el-border-color-lighter);
    border-right: 1px solid var(--el-border-color-lighter);
  }
  .draft-nav-stat {
    min-width: 64px;
    text-align: center;
    font-size: 12px;
    color: var(--el-text-color-secondary);
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
  .draft-mode-alert {
    margin-left: 8px;
    width: auto;
    min-width: 220px;
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

@media (max-width: 1100px) {
  .editor-toolbar {
    padding: 0 12px;
    .draft-nav-wrap {
      display: none;
    }
  }
  .editor-main {
    padding: 12px 12px 0;
  }
  .editor-sidebar {
    width: 280px;
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
  margin-bottom: 12px;
}

.setting-group {
  margin-bottom: 18px;

  .setting-label-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 6px;
  }
  .category-actions {
    display: flex;
    align-items: center;
    gap: 6px;
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
  .setting-tip {
    margin: 4px 0 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-placeholder);
  }
}

.full-width { width: 100%; }
.mb-8 { margin-bottom: 8px; }

.cover-upload-area {
  .cover-preview {
    position: relative;
    width: 100%;
    aspect-ratio: 14 / 9;
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
    aspect-ratio: 14 / 9;
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

.watermark-panel {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 10px;

  &.disabled {
    opacity: 0.6;
  }
}

.watermark-slider-row {
  display: grid;
  grid-template-columns: 48px 1fr;
  gap: 10px;
  align-items: center;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.watermark-tip {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-placeholder);
}

.watermark-preview {
  display: flex;
  flex-direction: column;
  gap: 6px;

  &__label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  &__canvas {
    position: relative;
    width: 100%;
    aspect-ratio: 14 / 9;
    border-radius: 10px;
    overflow: hidden;
    border: 1px solid var(--el-border-color-light);
    background:
      linear-gradient(135deg, rgba(20, 24, 35, 0.9), rgba(50, 65, 95, 0.72)),
      radial-gradient(circle at top right, rgba(255, 255, 255, 0.16), transparent 40%);

    &.is-tile .watermark-preview__text {
      display: none;
    }
  }

  &__text {
    position: absolute;
    font-weight: 600;
    line-height: 1.3;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
    white-space: nowrap;
    overflow: hidden;
    text-overflow: clip;

    &.is-top-left {
      top: 12px;
      left: 12px;
    }

    &.is-top-right {
      top: 12px;
      right: 12px;
      text-align: right;
    }

    &.is-bottom-left {
      left: 12px;
      bottom: 12px;
    }

    &.is-bottom-right {
      right: 12px;
      bottom: 12px;
      text-align: right;
    }

    &.is-center {
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      text-align: center;
    }
  }

  &__tile {
    position: absolute;
    line-height: 1.2;
    text-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
    white-space: nowrap;
    overflow: hidden;
    pointer-events: none;
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
