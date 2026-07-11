<template>
  <div class="ai-meta-generator">
    <el-popover
      ref="popoverRef"
      placement="left-start"
      :width="300"
      :visible="popoverVisible"
      popper-class="ai-meta-popover"
    >
      <template #reference>
        <div class="ai-meta-trigger">
          <button
            class="ai-generate-btn"
            :class="{ 'ai-generating': generating || singleLoading }"
            :disabled="contentTooShort || (generating && !abortCtrl)"
            @click="handleButtonClick"
          >
            <el-icon v-if="!generating && !singleLoading"><MagicStick /></el-icon>
            <span class="ai-btn-text">{{ buttonText }}</span>
            <span v-if="generating || singleLoading" class="ai-btn-shimmer" />
          </button>
          <button
            v-if="generating || singleLoading"
            class="ai-stop-btn"
            @click="handleStop"
          >
            <el-icon><CloseBold /></el-icon>
          </button>
          <el-dropdown v-else trigger="hover" @command="handleCommand">
            <el-button class="ai-meta-split-btn" :disabled="contentTooShort">
              <el-icon><ArrowDown /></el-icon>
            </el-button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="all">全部重新生成</el-dropdown-item>
                <el-dropdown-item command="remaining">生成剩余内容</el-dropdown-item>
                <el-dropdown-item command="summary">生成摘要</el-dropdown-item>
                <el-dropdown-item command="seo">生成 SEO</el-dropdown-item>
                <el-dropdown-item command="tags">生成标签</el-dropdown-item>
                <el-dropdown-item command="categories">生成分类</el-dropdown-item>
                <el-dropdown-item command="slug">生成 Slug</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </template>

      <div class="ai-meta-panel">
        <!-- 全量生成中：整体加载动画 -->
        <div v-if="generating" class="ai-meta-loading">
          <span class="ai-meta-loading-text">正在分析文章内容...</span>
          <span class="ai-meta-loading-bar" />
          <el-button size="small" text type="danger" class="ai-stop-inline" @click="handleStop">停止生成</el-button>
        </div>

        <!-- 全量生成失败 -->
        <div v-else-if="errorMsg && !result" class="ai-meta-error-inline">
          <span>{{ errorMsg }}</span>
          <el-button size="small" text type="primary" @click="handleGenerateAll">重试</el-button>
        </div>

        <!-- 结果区域：只显示已生成或正在生成的字段 -->
        <div v-else-if="result || singleLoading" class="ai-meta-results">
          <!-- 摘要 -->
          <AiMetaCard v-if="generated.has('summary') || regenerating.summary" title="摘要" :loading="regenerating.summary" loading-text="正在生成摘要..." @adopt="$emit('adopt-summary', result?.summary || '')" @regenerate="handleRegenerate('summary')">
            <div v-if="result?.summary" class="ai-meta-text">{{ result.summary }}</div>
          </AiMetaCard>

          <!-- SEO 标题 -->
          <AiMetaCard v-if="generated.has('seo') || regenerating.seo" title="SEO 标题" :loading="regenerating.seo" loading-text="正在生成 SEO..." @adopt="$emit('adopt-seo', { seoTitle: result?.seoTitle || '' })" @regenerate="handleRegenerate('seo')">
            <div v-if="result?.seoTitle" class="ai-meta-text">{{ result.seoTitle }}</div>
          </AiMetaCard>

          <!-- SEO 描述 -->
          <AiMetaCard v-if="generated.has('seoDesc') || regenerating.seoDesc" title="SEO 描述" :loading="regenerating.seoDesc" loading-text="正在生成 SEO 描述..." @adopt="$emit('adopt-seo', { seoDescription: result?.seoDescription || '' })" @regenerate="handleRegenerateSeoField('seoDesc')">
            <div v-if="result?.seoDescription" class="ai-meta-text">{{ result.seoDescription }}</div>
          </AiMetaCard>

          <!-- SEO 关键词 -->
          <AiMetaCard v-if="generated.has('seoKw') || regenerating.seoKw" title="SEO 关键词" :loading="regenerating.seoKw" loading-text="正在生成 SEO 关键词..." @adopt="$emit('adopt-seo', { seoKeywords: result?.seoKeywords?.join(', ') || '' })" @regenerate="handleRegenerateSeoField('seoKw')">
            <div v-if="result?.seoKeywords?.length" class="ai-kw-list">
              <el-tag v-for="kw in result.seoKeywords" :key="kw" size="small" class="ai-kw-tag">{{ kw }}</el-tag>
            </div>
          </AiMetaCard>

          <!-- 标签推荐 -->
          <AiMetaCard v-if="generated.has('tags') || regenerating.tags" title="标签推荐" :loading="regenerating.tags" loading-text="正在生成标签..." adopt-text="采用选中" @adopt="handleAdoptTags" @regenerate="handleRegenerate('tags')">
            <div v-if="result?.tags?.length" class="ai-tag-list">
              <el-check-tag
                v-for="tag in result.tags"
                :key="tag.name"
                :checked="selectedTags.has(tag.name)"
                :class="tag.isExisting ? 'ai-tag-existing' : 'ai-tag-new'"
                @change="toggleTag(tag.name)"
              >
                {{ tag.name }}
                <span v-if="tag.isExisting" class="ai-tag-badge existing">已有</span>
                <span v-else class="ai-tag-badge new">新</span>
              </el-check-tag>
            </div>
            <div v-if="selectedTags.size >= availableTagSlots" class="ai-tag-limit-tip">
              {{ props.currentTagCount ? `已有 ${props.currentTagCount} 个标签，最多还能选 ${availableTagSlots} 个` : '最多选择 5 个标签' }}
            </div>
          </AiMetaCard>

          <!-- 分类推荐 -->
          <AiMetaCard v-if="generated.has('categories') || regenerating.categories" title="分类推荐" :loading="regenerating.categories" loading-text="正在生成分类..." @adopt="handleAdoptCategory" @regenerate="handleRegenerate('categories')">
            <div v-if="result?.categories?.length" class="ai-cat-list">
              <el-radio-group v-model="selectedCategory">
                <el-radio
                  v-for="cat in result.categories"
                  :key="getCatKey(cat)"
                  :value="getCatKey(cat)"
                >
                  <span class="ai-cat-label">
                    <span v-if="cat.parentName" class="ai-cat-parent">{{ cat.parentName }} /</span>
                    {{ cat.name }}
                  </span>
                  <el-tag v-if="cat.isExisting" size="small" type="info" effect="plain" class="ai-cat-badge">已有</el-tag>
                  <el-tag v-else size="small" type="success" effect="plain" class="ai-cat-badge">新</el-tag>
                </el-radio>
              </el-radio-group>
            </div>
          </AiMetaCard>

          <!-- Slug -->
          <AiMetaCard v-if="generated.has('slug') || regenerating.slug" title="Slug" :loading="regenerating.slug" loading-text="正在生成 Slug..." @adopt="$emit('adopt-slug', result?.slug || '')" @regenerate="handleRegenerate('slug')">
            <code v-if="result?.slug" class="ai-slug-text">{{ result.slug }}</code>
          </AiMetaCard>

          <!-- 底部操作 -->
          <div v-if="hasAnyResult" class="ai-meta-adopt-all">
            <el-button type="primary" size="small" @click="handleAdoptAll">全部采用</el-button>
            <el-button size="small" @click="handleGenerateAll">全部重新生成</el-button>
          </div>
        </div>

        <!-- 空状态 -->
        <div v-else class="ai-meta-empty">
          <span>点击按钮生成文章元信息</span>
        </div>
      </div>
    </el-popover>
    <span v-if="contentTooShort" class="ai-meta-tip">文章内容不足 100 字</span>
  </div>
</template>

<script setup lang="ts">
import { MagicStick, ArrowDown, CloseBold } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'
import { aiApi, type AiMetaResult, type TagSuggestion, type CategorySuggestion } from '~/api/ai/ai'
import { handleAiError } from '~/utils/ai/aiError'

function isCancelled(e: unknown): boolean {
  return axios.isCancel(e)
}
import AiMetaCard from './AiMetaCard.vue'

const MAX_TAGS = 5

const LOADING_TEXT: Record<string, string> = {
  summary: '正在生成摘要...',
  seo: '正在生成 SEO...',
  seoDesc: '正在生成 SEO 描述...',
  seoKw: '正在生成 SEO 关键词...',
  tags: '正在生成标签...',
  categories: '正在生成分类...',
  slug: '正在生成 Slug...',
}

const props = defineProps<{
  title: string
  content: string
  currentTagCount?: number
  currentSummary?: string
  currentSeoTitle?: string
  currentSeoDescription?: string
  currentSeoKeywords?: string
  currentSlug?: string
  hasCategory?: boolean
}>()

const emit = defineEmits<{
  'adopt-summary': [summary: string]
  'adopt-seo': [seo: Record<string, string | undefined>]
  'adopt-tags': [tags: TagSuggestion[]]
  'adopt-category': [category: CategorySuggestion]
  'adopt-slug': [slug: string]
}>()

const popoverRef = ref()
const popoverVisible = ref(false)
const generating = ref(false)
const result = ref<AiMetaResult | null>(null)
const errorMsg = ref('')
const selectedTags = ref(new Set<string>())
const selectedCategory = ref('')
const generated = reactive(new Set<string>())
const regenerating = reactive({
  summary: false,
  seo: false,
  seoDesc: false,
  seoKw: false,
  tags: false,
  categories: false,
  slug: false,
})
const abortCtrl = ref<AbortController | null>(null)

const contentTooShort = computed(() => props.content.length < 100)
const singleLoading = computed(() => Object.values(regenerating).some(Boolean))
const hasAnyResult = computed(() => generated.size > 0)

const buttonText = computed(() => {
  if (generating.value) return 'AI 生成中'
  if (singleLoading.value) {
    const activeType = (Object.entries(regenerating) as [string, boolean][]).find(([, v]) => v)?.[0]
    return LOADING_TEXT[activeType] || '生成中...'
  }
  return result.value || generated.size > 0 ? '查看生成结果' : 'AI 一键生成'
})

function handleButtonClick() {
  if (generating.value || singleLoading.value) return
  if (result.value || generated.size > 0) {
    popoverVisible.value = !popoverVisible.value
    return
  }
  popoverVisible.value = true
  handleGenerateAll()
}

function handleStop() {
  if (abortCtrl.value) {
    abortCtrl.value.abort()
    abortCtrl.value = null
  }
  generating.value = false
  for (const key of Object.keys(regenerating) as (keyof typeof regenerating)[]) {
    regenerating[key] = false
  }
}

function markAllGenerated() {
  generated.add('summary')
  generated.add('seo')
  generated.add('seoDesc')
  generated.add('seoKw')
  generated.add('tags')
  generated.add('categories')
  generated.add('slug')
}

function createAbortController() {
  if (abortCtrl.value) {
    abortCtrl.value.abort()
  }
  abortCtrl.value = new AbortController()
  return abortCtrl.value.signal
}

async function handleGenerateAll() {
  if (contentTooShort.value || generating.value) return
  generating.value = true
  errorMsg.value = ''
  result.value = null
  const signal = createAbortController()
  try {
    const res = await aiApi.generateAll({ title: props.title, content: props.content }, signal)
    result.value = res.data
    markAllGenerated()
    const slots = MAX_TAGS - (props.currentTagCount ?? 0)
    const tagNames = (res.data.tags || []).slice(0, Math.max(0, slots)).map(t => t.name)
    selectedTags.value = new Set(tagNames)
    const firstCategory = res.data.categories?.[0]
    if (firstCategory) {
      selectedCategory.value = getCatKey(firstCategory)
    }
    popoverVisible.value = true
  } catch (e: unknown) {
    if (isCancelled(e)) return
    const err = e as { message?: string }
    errorMsg.value = err?.message || 'AI 生成失败，请稍后重试'
    handleAiError(e)
  } finally {
    generating.value = false
    abortCtrl.value = null
  }
}

function getCatKey(cat: CategorySuggestion): string {
  return cat.parentName ? `${cat.parentName}/${cat.name}` : cat.name
}

async function handleCommand(command: string) {
  if (generating.value) return
  popoverVisible.value = true
  if (command === 'all') {
    await handleGenerateAll()
    return
  }
  if (command === 'remaining') {
    await handleGenerateRemaining()
    return
  }
  handleStopSingle()
  await handleRegenerate(command as keyof typeof regenerating)
}

function handleStopSingle() {
  if (abortCtrl.value) {
    abortCtrl.value.abort()
    abortCtrl.value = null
  }
  for (const key of Object.keys(regenerating) as (keyof typeof regenerating)[]) {
    regenerating[key] = false
  }
  generated.clear()
  result.value = null
}

async function handleGenerateRemaining() {
  if (!result.value) {
    await handleGenerateAll()
    return
  }
  if (!result.value.summary && !props.currentSummary?.trim()) {
    await handleRegenerate('summary')
  }
  if ((!result.value.seoTitle || !result.value.seoDescription) && (!props.currentSeoTitle?.trim() || !props.currentSeoDescription?.trim())) {
    await handleRegenerate('seo')
  }
  if ((result.value.tags?.length ?? 0) === 0 && (props.currentTagCount ?? 0) < MAX_TAGS) {
    await handleRegenerate('tags')
  }
  if ((result.value.categories?.length ?? 0) === 0 && !props.hasCategory) {
    await handleRegenerate('categories')
  }
  if (!result.value.slug && !props.currentSlug?.trim()) {
    await handleRegenerate('slug')
  }
}

function ensureResult() {
  if (!result.value) {
    result.value = {
      summary: '',
      seoTitle: '',
      seoDescription: '',
      seoKeywords: [],
      tags: [],
      categories: [],
      slug: '',
    }
  }
}

async function handleRegenerate(type: keyof typeof regenerating) {
  ensureResult()
  regenerating[type] = true
  generated.add(type)
  if (type === 'seo') {
    generated.add('seoDesc')
    generated.add('seoKw')
  }
  popoverVisible.value = true
  const signal = createAbortController()
  const data = { title: props.title, content: props.content }
  try {
    switch (type) {
      case 'summary': {
        const sumRes = await aiApi.regenerateSummary(data, signal)
        result.value!.summary = sumRes.data
        break
      }
      case 'seo': {
        const seoRes = await aiApi.regenerateSeo(data, signal)
        result.value!.seoTitle = seoRes.data.seoTitle
        result.value!.seoDescription = seoRes.data.seoDescription
        result.value!.seoKeywords = seoRes.data.seoKeywords
        break
      }
      case 'tags': {
        const tagRes = await aiApi.regenerateTags(data, signal)
        result.value!.tags = tagRes.data
        const slots = MAX_TAGS - (props.currentTagCount ?? 0)
        const tagNames = tagRes.data.slice(0, Math.max(0, slots)).map(t => t.name)
        selectedTags.value = new Set(tagNames)
        break
      }
      case 'categories': {
        const catRes = await aiApi.regenerateCategories(data, signal)
        result.value!.categories = catRes.data
        const firstCategory = catRes.data[0]
        if (firstCategory) selectedCategory.value = getCatKey(firstCategory)
        break
      }
      case 'slug': {
        const slugRes = await aiApi.regenerateSlug({ title: props.title }, signal)
        result.value!.slug = slugRes.data
        break
      }
    }
  } catch (e: unknown) {
    if (isCancelled(e)) return
    handleAiError(e)
  } finally {
    regenerating[type] = false
    abortCtrl.value = null
  }
}

async function handleRegenerateSeoField(field: 'seoDesc' | 'seoKw') {
  ensureResult()
  generated.add(field)
  popoverVisible.value = true
  const signal = createAbortController()
  const data = { title: props.title, content: props.content }
  regenerating[field] = true
  try {
    const seoRes = await aiApi.regenerateSeo(data, signal)
    if (field === 'seoDesc') {
      result.value!.seoDescription = seoRes.data.seoDescription
    } else {
      result.value!.seoKeywords = seoRes.data.seoKeywords
    }
  } catch (e: unknown) {
    if (isCancelled(e)) return
    handleAiError(e)
  } finally {
    regenerating[field] = false
    abortCtrl.value = null
  }
}

const availableTagSlots = computed(() => MAX_TAGS - (props.currentTagCount ?? 0))

function toggleTag(name: string) {
  if (selectedTags.value.has(name)) {
    selectedTags.value.delete(name)
  } else {
    if (selectedTags.value.size >= availableTagSlots.value) {
      ElMessage.warning(`当前已有 ${props.currentTagCount ?? 0} 个标签，最多还能选 ${availableTagSlots.value} 个`)
      return
    }
    selectedTags.value.add(name)
  }
}

function handleAdoptTags() {
  if (!result.value?.tags) return
  const adopted = result.value.tags.filter(t => selectedTags.value.has(t.name))
  emit('adopt-tags', adopted)
}

function handleAdoptCategory() {
  if (!result.value?.categories || !selectedCategory.value) return
  const cat = result.value.categories.find(c => getCatKey(c) === selectedCategory.value)
  if (cat) emit('adopt-category', cat)
}

function handleAdoptAll() {
  if (!result.value) return
  emit('adopt-summary', result.value.summary)
  emit('adopt-seo', {
    seoTitle: result.value.seoTitle,
    seoDescription: result.value.seoDescription,
    seoKeywords: result.value.seoKeywords?.join(', '),
  })
  emit('adopt-slug', result.value.slug)
  handleAdoptTags()
  handleAdoptCategory()
  ElMessage.success('已全部采用')
  popoverVisible.value = false
}

function handleClickOutside(e: MouseEvent) {
  if (!popoverVisible.value) return
  const popoverEl = popoverRef.value?.popperRef?.contentRef
  const referenceEl = popoverRef.value?.triggerRef
  if (popoverEl?.contains(e.target as Node) || referenceEl?.contains(e.target as Node)) return
  popoverVisible.value = false
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside, true)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside, true)
  if (abortCtrl.value) {
    abortCtrl.value.abort()
  }
})

function getSnapshot(): { result: AiMetaResult | null; selectedTags: string[]; selectedCategory: string; generatedFields: string[] } {
  return {
    result: result.value,
    selectedTags: Array.from(selectedTags.value),
    selectedCategory: selectedCategory.value,
    generatedFields: Array.from(generated),
  }
}

function restoreSnapshot(snapshot: { result: AiMetaResult | null; selectedTags: string[]; selectedCategory: string; generatedFields: string[] }) {
  if (!snapshot) return
  if (snapshot.result) {
    result.value = snapshot.result
    selectedTags.value = new Set(snapshot.selectedTags || [])
    selectedCategory.value = snapshot.selectedCategory || ''
    generated.clear()
    for (const f of (snapshot.generatedFields || [])) {
      generated.add(f)
    }
  }
}

defineExpose({ getSnapshot, restoreSnapshot })
</script>

<style scoped>
.ai-meta-generator {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-meta-trigger {
  display: inline-flex;
  align-items: stretch;
}

.ai-generate-btn {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  height: 32px;
  padding: 0 15px;
  border: 1px solid var(--el-color-primary);
  border-radius: 4px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  overflow: hidden;
  transition: all 0.2s;
  white-space: nowrap;
}

.ai-generate-btn:hover:not(:disabled) {
  background: var(--el-color-primary-light-3);
  border-color: var(--el-color-primary-light-3);
}

.ai-generate-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.ai-generate-btn:not(:disabled):not(.ai-generating) {
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.ai-generating .ai-btn-text {
  position: relative;
  z-index: 1;
}

.ai-btn-shimmer {
  position: absolute;
  top: 0;
  left: -100%;
  width: 100%;
  height: 100%;
  background: linear-gradient(
    90deg,
    transparent 0%,
    rgba(255, 255, 255, 0.3) 50%,
    transparent 100%
  );
  animation: ai-shimmer 1.5s ease-in-out infinite;
}

@keyframes ai-shimmer {
  0% { left: -100%; }
  100% { left: 100%; }
}

.ai-generating {
  background: var(--el-color-primary-light-5) !important;
  border-color: var(--el-color-primary-light-3) !important;
}

.ai-stop-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: 1px solid var(--el-color-danger-light-5);
  border-radius: 4px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
  cursor: pointer;
  transition: all 0.2s;
  flex-shrink: 0;
}

.ai-stop-btn:hover {
  background: var(--el-color-danger-light-7);
  border-color: var(--el-color-danger-light-3);
}

.ai-meta-split-btn {
  padding: 0 10px;
  border-top-left-radius: 0 !important;
  border-bottom-left-radius: 0 !important;
  margin-left: -1px;
}

.ai-meta-tip {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.ai-meta-panel {
  max-height: 480px;
  overflow-y: auto;
}

.ai-meta-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 16px 0 8px;
}

.ai-meta-loading-text {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  position: relative;
  overflow: hidden;
  background: linear-gradient(90deg, var(--el-text-color-secondary) 0%, var(--el-color-primary) 50%, var(--el-text-color-secondary) 100%);
  background-size: 200% 100%;
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  animation: ai-text-sweep 1.5s ease-in-out infinite;
}

@keyframes ai-text-sweep {
  0% { background-position: 100% 0; }
  100% { background-position: -100% 0; }
}

.ai-meta-loading-bar {
  width: 120px;
  height: 3px;
  border-radius: 2px;
  background: var(--el-border-color-lighter);
  position: relative;
  overflow: hidden;
}

.ai-meta-loading-bar::after {
  content: '';
  position: absolute;
  top: 0;
  left: -40%;
  width: 40%;
  height: 100%;
  background: var(--el-color-primary);
  border-radius: 2px;
  animation: ai-bar-slide 1.2s ease-in-out infinite;
}

@keyframes ai-bar-slide {
  0% { left: -40%; }
  100% { left: 100%; }
}

.ai-stop-inline {
  margin-top: 0;
}

.ai-meta-error-inline {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  color: var(--el-color-danger);
}

.ai-meta-empty {
  padding: 16px 0;
  text-align: center;
  font-size: 13px;
  color: var(--el-text-color-placeholder);
}

.ai-meta-results {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.ai-meta-text {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
}

.ai-kw-list {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}

.ai-kw-tag {
  margin: 0;
}

.ai-tag-list {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.ai-tag-existing {
  border-style: solid;
}

.ai-tag-new {
  border-style: dashed;
}

.ai-tag-badge {
  font-size: 10px;
  margin-left: 2px;
  font-weight: 500;
}
.ai-tag-badge.existing {
  color: var(--el-text-color-secondary);
}
.ai-tag-badge.new {
  color: var(--el-color-success);
}

.ai-tag-limit-tip {
  font-size: 11px;
  color: var(--el-color-warning);
  margin-top: 4px;
}

.ai-cat-list :deep(.el-radio-group) {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ai-cat-list :deep(.el-radio) {
  display: flex;
  align-items: center;
  height: auto;
  margin-right: 0;
}

.ai-cat-label {
  font-size: 13px;
}

.ai-cat-parent {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin-right: 2px;
}

.ai-cat-badge {
  margin-left: 4px;
  vertical-align: middle;
}

.ai-slug-text {
  font-size: 12px;
  color: var(--el-text-color-regular);
  background: var(--el-fill-color-lighter);
  padding: 2px 6px;
  border-radius: 4px;
  word-break: break-all;
}

.ai-meta-adopt-all {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-extra-light);
}
</style>