<template>
  <div class="ai-meta-generator">
    <!-- AI 一键生成按钮 -->
    <el-popover
      ref="popoverRef"
      placement="bottom-end"
      :width="268"
      :visible="popoverVisible"
      popper-class="ai-meta-popover"
    >
      <template #reference>
        <el-button
          :type="result ? 'default' : 'primary'"
          :loading="generating"
          :disabled="contentTooShort"
          @click="handleButtonClick"
        >
          <el-icon><MagicStick /></el-icon>
          {{ buttonText }}
          <el-icon class="ai-arrow-icon"><ArrowDown /></el-icon>
        </el-button>
      </template>

      <!-- 弹窗内容 -->
      <div class="ai-meta-panel">
        <!-- 生成中 -->
        <div v-if="generating" class="ai-meta-loading">
          <el-icon class="is-loading" :size="20"><Loading /></el-icon>
          <span>正在分析文章内容...</span>
        </div>

        <!-- 生成失败 -->
        <div v-else-if="errorMsg" class="ai-meta-error-inline">
          <span>{{ errorMsg }}</span>
          <el-button size="small" text type="primary" @click="handleGenerateAll">重试</el-button>
        </div>

        <!-- 无结果提示 -->
        <div v-else-if="!result" class="ai-meta-empty">
          <span>点击按钮生成文章元信息</span>
        </div>

        <!-- 生成结果 -->
        <div v-else class="ai-meta-results">
          <!-- 全部重新生成 -->
          <div class="ai-meta-regen-all">
            <el-button size="small" text type="primary" :loading="generating" @click="handleGenerateAll">
              <el-icon><RefreshRight /></el-icon>
              全部重新生成
            </el-button>
          </div>

          <!-- 摘要 -->
          <AiMetaCard title="摘要" :loading="regenerating.summary" @adopt="$emit('adopt-summary', result.summary)" @regenerate="handleRegenerate('summary')">
            <div class="ai-meta-text">{{ result.summary }}</div>
          </AiMetaCard>

          <!-- SEO 标题 -->
          <AiMetaCard title="SEO 标题" :loading="regenerating.seo" @adopt="$emit('adopt-seo', { seoTitle: result.seoTitle })" @regenerate="handleRegenerate('seo')">
            <div class="ai-meta-text">{{ result.seoTitle }}</div>
          </AiMetaCard>

          <!-- SEO 描述 -->
          <AiMetaCard title="SEO 描述" :loading="regenerating.seoDesc" @adopt="$emit('adopt-seo', { seoDescription: result.seoDescription })" @regenerate="handleRegenerateSeoField('seoDesc')">
            <div class="ai-meta-text">{{ result.seoDescription }}</div>
          </AiMetaCard>

          <!-- SEO 关键词 -->
          <AiMetaCard title="SEO 关键词" :loading="regenerating.seoKw" @adopt="$emit('adopt-seo', { seoKeywords: result.seoKeywords?.join(', ') })" @regenerate="handleRegenerateSeoField('seoKw')">
            <div class="ai-kw-list">
              <el-tag v-for="kw in result.seoKeywords" :key="kw" size="small" class="ai-kw-tag">{{ kw }}</el-tag>
            </div>
          </AiMetaCard>

          <!-- 标签推荐 -->
          <AiMetaCard title="标签推荐" :loading="regenerating.tags" adopt-text="采用选中" @adopt="handleAdoptTags" @regenerate="handleRegenerate('tags')">
            <div class="ai-tag-list">
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
          <AiMetaCard title="分类推荐" :loading="regenerating.categories" @adopt="handleAdoptCategory" @regenerate="handleRegenerate('categories')">
            <div class="ai-cat-list">
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
          <AiMetaCard title="Slug" :loading="regenerating.slug" @adopt="$emit('adopt-slug', result.slug)" @regenerate="handleRegenerate('slug')">
            <code class="ai-slug-text">{{ result.slug }}</code>
          </AiMetaCard>

          <!-- 全部采用 -->
          <div class="ai-meta-adopt-all">
            <el-button type="primary" size="small" @click="handleAdoptAll">全部采用</el-button>
          </div>
        </div>
      </div>
    </el-popover>
    <span v-if="contentTooShort" class="ai-meta-tip">文章内容不足 100 字</span>
  </div>
</template>

<script setup lang="ts">
import { MagicStick, ArrowDown, Loading, RefreshRight } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { aiApi, type AiMetaResult, type TagSuggestion, type CategorySuggestion } from '~/api/ai'
import { handleAiError } from '~/utils/aiError'
import AiMetaCard from './AiMetaCard.vue'

const MAX_TAGS = 5

const props = defineProps<{
  title: string
  content: string
  currentTagCount?: number
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
const regenerating = reactive({
  summary: false,
  seo: false,
  seoDesc: false,
  seoKw: false,
  tags: false,
  categories: false,
  slug: false,
})

const contentTooShort = computed(() => props.content.length < 100)

// 按钮文字：根据是否已有结果动态显示
const buttonText = computed(() => {
  if (generating.value) return '生成中...'
  return result.value ? 'AI 已生成' : 'AI 一键生成'
})

// 按钮点击：切换弹窗，首次自动生成
function handleButtonClick() {
  popoverVisible.value = !popoverVisible.value
  if (popoverVisible.value && !result.value && !generating.value) {
    handleGenerateAll()
  }
}

// 点击外部关闭弹窗
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
})

async function handleGenerateAll() {
  generating.value = true
  errorMsg.value = ''
  result.value = null
  try {
    const res = await aiApi.generateAll({ title: props.title, content: props.content })
    result.value = res.data
    // 默认选中标签（限制不超过可用名额）
    const slots = MAX_TAGS - (props.currentTagCount ?? 0)
    const tagNames = (res.data.tags || []).slice(0, Math.max(0, slots)).map(t => t.name)
    selectedTags.value = new Set(tagNames)
    // 默认选第一个分类
    if (res.data.categories?.length) {
      selectedCategory.value = getCatKey(res.data.categories[0])
    }
  } catch (e) {
    const err = e as { message?: string }
    errorMsg.value = err?.message || 'AI 生成失败，请稍后重试'
    handleAiError(e)
  } finally {
    generating.value = false
    // 生成完成后自动弹出弹窗
    popoverVisible.value = true
  }
}

// 分类唯一键：parentName/name
function getCatKey(cat: CategorySuggestion): string {
  return cat.parentName ? `${cat.parentName}/${cat.name}` : cat.name
}

async function handleRegenerate(type: string) {
  const data = { title: props.title, content: props.content }
  try {
    switch (type) {
      case 'summary': {
        regenerating.summary = true
        const sumRes = await aiApi.regenerateSummary(data)
        if (result.value) result.value.summary = sumRes.data
        break
      }
      case 'seo': {
        regenerating.seo = true
        const seoRes = await aiApi.regenerateSeo(data)
        if (result.value) {
          result.value.seoTitle = seoRes.data.seoTitle
          result.value.seoDescription = seoRes.data.seoDescription
          result.value.seoKeywords = seoRes.data.seoKeywords
        }
        break
      }
      case 'tags': {
        regenerating.tags = true
        const tagRes = await aiApi.regenerateTags(data)
        if (result.value) {
          result.value.tags = tagRes.data
          const slots = MAX_TAGS - (props.currentTagCount ?? 0)
          const tagNames = tagRes.data.slice(0, Math.max(0, slots)).map(t => t.name)
          selectedTags.value = new Set(tagNames)
        }
        break
      }
      case 'categories': {
        regenerating.categories = true
        const catRes = await aiApi.regenerateCategories(data)
        if (result.value) {
          result.value.categories = catRes.data
          if (catRes.data.length) selectedCategory.value = getCatKey(catRes.data[0])
        }
        break
      }
      case 'slug': {
        regenerating.slug = true
        const slugRes = await aiApi.regenerateSlug({ title: props.title })
        if (result.value) result.value.slug = slugRes.data
        break
      }
    }
  } catch (e) {
    handleAiError(e)
  } finally {
    regenerating[type as keyof typeof regenerating] = false
  }
}

// 单独重新生成 SEO 描述或关键词
async function handleRegenerateSeoField(field: 'seoDesc' | 'seoKw') {
  if (!result.value) return
  const data = { title: props.title, content: props.content }
  regenerating[field] = true
  try {
    const seoRes = await aiApi.regenerateSeo(data)
    if (field === 'seoDesc') {
      result.value.seoDescription = seoRes.data.seoDescription
    } else {
      result.value.seoKeywords = seoRes.data.seoKeywords
    }
  } catch (e) {
    handleAiError(e)
  } finally {
    regenerating[field] = false
  }
}

// 可用的AI标签选中名额 = 总限制 - 编辑器已选标签数
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

// ========== 持久化支持 ==========

/** 获取当前 AI 生成结果快照（供 create.vue 保存到 localStorage） */
function getSnapshot(): { result: AiMetaResult | null; selectedTags: string[]; selectedCategory: string } {
  return {
    result: result.value,
    selectedTags: Array.from(selectedTags.value),
    selectedCategory: selectedCategory.value,
  }
}

/** 从快照恢复 AI 生成结果（页面刷新后恢复） */
function restoreSnapshot(snapshot: { result: AiMetaResult | null; selectedTags: string[]; selectedCategory: string }) {
  if (!snapshot) return
  if (snapshot.result) {
    result.value = snapshot.result
    selectedTags.value = new Set(snapshot.selectedTags || [])
    selectedCategory.value = snapshot.selectedCategory || ''
  }
}

defineExpose({ getSnapshot, restoreSnapshot })
</script>

<style scoped>
.ai-meta-generator {
  display: flex;
  align-items: center;
  gap: 8px;
}

.ai-arrow-icon {
  margin-left: 4px;
  transition: transform 0.2s;
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
  align-items: center;
  gap: 8px;
  padding: 16px 0;
  justify-content: center;
  color: var(--el-text-color-secondary);
  font-size: 13px;
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

.ai-meta-regen-all {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 4px;
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
  padding-top: 4px;
  border-top: 1px solid var(--el-border-color-extra-light);
}
</style>
