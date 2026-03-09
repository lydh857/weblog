<template>
  <div class="carousel-page">
    <div class="page-header">
      <h2>轮播管理</h2>
      <div class="filter-bar"></div>
      <div class="header-actions">
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建轮播
        </el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe height="560">
      <el-table-column label="#" type="index" width="50" align="center" />
      <el-table-column label="背景图" width="120">
        <template #default="{ row }">
          <el-image :src="row.imageUrl" fit="cover" class="cover-thumb" :preview-src-list="[row.imageUrl]" preview-teleported />
        </template>
      </el-table-column>
      <el-table-column label="标题" prop="title" min-width="160" show-overflow-tooltip />
      <el-table-column label="类型" width="80" align="center">
        <template #default="{ row }">
          <el-tag :type="row.type === 'article' ? '' : 'warning'" size="small">
            {{ row.type === 'article' ? '文章' : '图片' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="启用" width="80" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.isEnabled" @change="handleToggle(row)" />
        </template>
      </el-table-column>
      <el-table-column label="排序" prop="sortOrder" width="80" align="center" sortable />
      <el-table-column label="有效期" min-width="220">
        <template #default="{ row }">
          <div v-if="row.startTime || row.endTime" class="time-cell">
            <span class="time-range-text">{{ fmt(row.startTime) }} ~ {{ fmt(row.endTime) }}</span>
          </div>
          <span v-else class="muted">永久</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :page-sizes="[10, 20, 50]"
        :total="total" layout="total, sizes, prev, pager, next" background size="small"
        @current-change="loadData" @size-change="handleSizeChange" />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑轮播' : '新建轮播'" width="580px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90px">
        <el-form-item label="类型" prop="type">
          <el-radio-group v-model="form.type">
            <el-radio value="article">文章推荐</el-radio>
            <el-radio value="image">图片广告</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="form.type === 'article'" label="关联文章" prop="articleId">
          <el-select
            v-model="form.articleId"
            filterable remote reserve-keyword
            placeholder="搜索文章标题"
            :remote-method="searchArticles"
            :loading="articleSearchLoading"
            style="width: 100%"
            clearable
            value-key="id"
            @change="handleArticleChange"
          >
            <el-option
              v-for="item in articleOptions"
              :key="item.id"
              :label="item.title"
              :value="item.id"
            >
              <span>{{ item.title }}</span>
              <span style="float:right;color:var(--el-text-color-placeholder);font-size:12px">ID: {{ item.id }}</span>
            </el-option>
          </el-select>
        </el-form-item>
        <el-form-item label="标题" :prop="form.type === 'image' ? 'title' : undefined">
          <el-input v-model="form.title" maxlength="200" show-word-limit
            :placeholder="form.type === 'article' && articleDefaults.title ? `默认：${articleDefaults.title}` : '轮播标题'" />
          <span v-if="form.type === 'article'" class="form-tip">留空则使用文章标题</span>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" show-word-limit
            :placeholder="form.type === 'article' && articleDefaults.summary ? `默认：${articleDefaults.summary}` : '副标题/描述（可选）'" />
          <span v-if="form.type === 'article'" class="form-tip">留空则使用文章摘要</span>
        </el-form-item>
        <el-form-item label="背景图" :prop="form.type === 'image' ? 'imageUrl' : undefined">
          <div class="cover-upload-area">
            <div v-if="form.imageUrl" class="cover-preview" @click="openImageCropper">
              <el-image :src="form.imageUrl" fit="cover" class="cover-img" />
              <div class="cover-overlay">裁剪 / 更换</div>
            </div>
            <div v-else-if="form.type === 'article' && articleDefaults.coverImage" class="cover-preview cover-preview--default" @click="triggerImageUpload">
              <el-image :src="articleDefaults.coverImage" fit="cover" class="cover-img" />
              <div class="cover-overlay cover-overlay--default">
                <span>使用文章封面</span>
                <span class="cover-overlay-sub">点击可自定义上传</span>
              </div>
            </div>
            <div v-else class="cover-placeholder" @click="triggerImageUpload">
              <el-icon :size="28"><Plus /></el-icon>
              <span>{{ form.type === 'article' ? '上传背景图（可选，默认使用文章封面）' : '上传背景图' }}</span>
            </div>
            <input ref="imageInputRef" type="file" accept="image/*" style="display:none" @change="handleImageFileChange" />
          </div>
        </el-form-item>
        <el-form-item v-if="form.type === 'image'" label="跳转链接" prop="linkUrl">
          <el-input v-model="form.linkUrl" placeholder="点击跳转的 URL" />
        </el-form-item>
        <el-form-item label="排序权重">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" controls-position="right" />
          <span class="form-tip">数值越大越靠前</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isEnabled" />
        </el-form-item>
        <el-form-item label="有效期">
          <div class="time-section">
            <el-checkbox v-model="isPermanent">永久有效</el-checkbox>
            <template v-if="!isPermanent">
              <div class="time-range">
                <el-date-picker v-model="form.startTime" type="datetime" placeholder="开始时间" style="width:100%"
                  clearable format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
                <el-date-picker v-model="form.endTime" type="datetime" placeholder="结束时间" style="width:100%"
                  clearable format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
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

    <!-- 图片裁剪弹窗 -->
    <ImageCropper v-model="showImageCropper" :image-src="cropperImageSrc" :aspect-ratio="[16, 9]"
      output-type="image/webp" :max-output-width="1920" @crop="handleImageCropped" />
  </div>
</template>

<script setup lang="ts">
import { Plus } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox, type FormInstance, type FormRules } from 'element-plus'
import { carouselApi, type CarouselItem, type CarouselForm } from '~/api/carousel'
import { uploadApi } from '~/api/upload'
import { postApi, type PostVO } from '~/api/post'
import ImageCropper from '~/components/ImageCropper.vue'

// ===== 列表状态 =====
const loading = ref(false)
const records = ref<CarouselItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// ===== 弹窗状态 =====
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<FormInstance>()
const isPermanent = ref(true)

const form = reactive<CarouselForm & { articleId: number | null }>({
  type: 'article',
  title: '',
  description: '',
  imageUrl: '',
  linkUrl: '',
  articleId: null,
  sortOrder: 0,
  isEnabled: true,
  startTime: null,
  endTime: null,
})

// 文章默认值（选择文章后从文章获取，用于 placeholder 展示）
const articleDefaults = reactive({
  title: '',
  summary: '',
  coverImage: '',
  slug: '',
})

function resetArticleDefaults() {
  articleDefaults.title = ''
  articleDefaults.summary = ''
  articleDefaults.coverImage = ''
  articleDefaults.slug = ''
}

/** 表单校验规则：article 类型 title/imageUrl 不强制 */
const formRules = computed<FormRules>(() => ({
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  title: form.type === 'image'
    ? [{ required: true, message: '请输入标题', trigger: 'blur' }]
    : [],
  imageUrl: form.type === 'image'
    ? [{ required: true, message: '请上传背景图', trigger: 'change' }]
    : [],
  linkUrl: form.type === 'image'
    ? [{ required: true, message: '图片类型必须填写跳转链接', trigger: 'blur' }]
    : [],
  articleId: form.type === 'article'
    ? [{ required: true, message: '请选择关联文章', trigger: 'change' }]
    : [],
}))

// 切换类型时清除校验状态，避免切换瞬间出现失焦提示
watch(() => form.type, () => {
  nextTick(() => {
    formRef.value?.clearValidate()
  })
})

// ===== 图片上传 + 裁剪 =====
const imageInputRef = ref<HTMLInputElement>()
const pendingImageFile = ref<File | null>(null)
const showImageCropper = ref(false)
const cropperImageSrc = ref('')

function triggerImageUpload() {
  imageInputRef.value?.click()
}

function handleImageFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    cropperImageSrc.value = ev.target?.result as string
    showImageCropper.value = true
  }
  reader.readAsDataURL(file)
  if (imageInputRef.value) imageInputRef.value.value = ''
}

function handleImageCropped(data: { blob: Blob; url: string }) {
  if (form.imageUrl.startsWith('blob:')) URL.revokeObjectURL(form.imageUrl)
  form.imageUrl = data.url
  const ext = data.blob.type === 'image/webp' ? 'webp' : data.blob.type === 'image/png' ? 'png' : 'jpg'
  pendingImageFile.value = new File([data.blob], `carousel.${ext}`, { type: data.blob.type })
}

function openImageCropper() {
  if (form.imageUrl && !form.imageUrl.startsWith('blob:')) {
    cropperImageSrc.value = form.imageUrl
    showImageCropper.value = true
  } else {
    triggerImageUpload()
  }
}

// ===== 文章远程搜索 =====
const articleSearchLoading = ref(false)
const articleOptions = ref<Pick<PostVO, 'id' | 'title'>[]>([])

async function searchArticles(query: string) {
  if (!query) { articleOptions.value = []; return }
  articleSearchLoading.value = true
  try {
    const res = await postApi.page({ keyword: query, pageNum: 1, pageSize: 20, status: 'published' })
    articleOptions.value = res.data.records.map(p => ({ id: p.id, title: p.title }))
  } catch {
    articleOptions.value = []
  } finally {
    articleSearchLoading.value = false
  }
}

/** 选择文章后加载文章详情，填充默认值 */
async function handleArticleChange(articleId: number | null) {
  if (!articleId) {
    resetArticleDefaults()
    return
  }
  try {
    const res = await postApi.getById(articleId)
    const post = res.data
    articleDefaults.title = post.title || ''
    articleDefaults.summary = post.summary || ''
    articleDefaults.coverImage = post.coverImage || ''
    articleDefaults.slug = post.slug || ''
  } catch {
    resetArticleDefaults()
  }
}

/** 编辑时回填文章选项并加载默认值 */
async function loadArticleOption(articleId: number) {
  try {
    const res = await postApi.getById(articleId)
    articleOptions.value = [{ id: res.data.id, title: res.data.title }]
    articleDefaults.title = res.data.title || ''
    articleDefaults.summary = res.data.summary || ''
    articleDefaults.coverImage = res.data.coverImage || ''
    articleDefaults.slug = res.data.slug || ''
  } catch {
    articleOptions.value = [{ id: articleId, title: `文章 #${articleId}` }]
    resetArticleDefaults()
  }
}

// ===== 工具函数 =====
function fmt(t: string | null) { return t ? t.replace('T', ' ').slice(0, 16) : '' }

function formatLocal(d: Date) {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function toISO(v: string | Date | null): string | null {
  if (!v) return null
  const d = v instanceof Date ? v : new Date(v)
  if (isNaN(d.getTime())) return null
  return formatLocal(d)
}

// ===== 数据加载 =====
async function loadData() {
  loading.value = true
  try {
    const res = await carouselApi.list({ pageNum: pageNum.value, pageSize: pageSize.value })
    records.value = res.data.records
    total.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleSizeChange(size: number) {
  pageSize.value = size
  pageNum.value = 1
  loadData()
}

// ===== 启用/禁用切换 =====
async function handleToggle(row: CarouselItem) {
  try {
    await carouselApi.update(row.id, { isEnabled: row.isEnabled })
    ElMessage.success(row.isEnabled ? '已启用' : '已禁用')
  } catch (e: unknown) {
    row.isEnabled = !row.isEnabled
    ElMessage.error((e as Error).message || '操作失败')
  }
}

// ===== 弹窗操作 =====
function openDialog(row?: CarouselItem) {
  editingId.value = row?.id || null
  form.type = row?.type || 'article'
  form.title = row?.title || ''
  form.description = row?.description || ''
  form.imageUrl = row?.imageUrl || ''
  form.linkUrl = row?.linkUrl || ''
  form.articleId = row?.articleId || null
  form.sortOrder = row?.sortOrder ?? 0
  form.isEnabled = row?.isEnabled ?? true
  form.startTime = row?.startTime || null
  form.endTime = row?.endTime || null
  isPermanent.value = !row?.startTime && !row?.endTime
  pendingImageFile.value = null
  articleOptions.value = []
  resetArticleDefaults()
  // 编辑时回填文章选项和默认值
  if (row?.articleId) loadArticleOption(row.articleId)
  dialogVisible.value = true
  // 弹窗打开后清除校验状态，避免残留的校验提示
  nextTick(() => {
    formRef.value?.clearValidate()
  })
}

async function handleSubmit() {
  if (!formRef.value) return
  await formRef.value.validate()

  submitting.value = true
  try {
    // 上传待上传的背景图
    if (pendingImageFile.value) {
      const uploadRes = await uploadApi.image(pendingImageFile.value, 'carousel')
      if (form.imageUrl.startsWith('blob:')) URL.revokeObjectURL(form.imageUrl)
      form.imageUrl = uploadRes.data
      pendingImageFile.value = null
    }

    const data: CarouselForm = { ...form }
    if (isPermanent.value) {
      data.startTime = null
      data.endTime = null
    } else {
      if (data.startTime) data.startTime = toISO(data.startTime)
      if (data.endTime) data.endTime = toISO(data.endTime)
    }

    if (editingId.value) {
      await carouselApi.update(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await carouselApi.create(data)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

// ===== 删除 =====
async function handleDelete(row: CarouselItem) {
  await ElMessageBox.confirm(`确定删除轮播「${row.title}」？`, '提示', { type: 'warning' })
  try {
    await carouselApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.carousel-page {
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
}

/* 封面缩略图 */
.cover-thumb {
  width: 90px;
  height: 50px;
  border-radius: 4px;
  object-fit: cover;
}

/* 图片上传区域 - 16:9 比例 */
.cover-upload-area {
  width: 100%;
}
.cover-preview {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 9;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  .cover-img {
    width: 100%;
    height: 100%;
    display: block;
  }
  .cover-overlay {
    position: absolute;
    inset: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background: rgba(0, 0, 0, 0.45);
    color: #fff;
    font-size: 14px;
    opacity: 0;
    transition: opacity 0.2s;
  }
  .cover-overlay-sub {
    font-size: 12px;
    opacity: 0.8;
    margin-top: 4px;
  }
  &:hover .cover-overlay { opacity: 1; }

  /* 文章封面默认预览样式 */
  &--default {
    .cover-overlay--default {
      opacity: 0.6;
      background: rgba(0, 0, 0, 0.3);
    }
    &:hover .cover-overlay--default {
      opacity: 1;
      background: rgba(0, 0, 0, 0.45);
    }
  }
}
.cover-placeholder {
  width: 100%;
  aspect-ratio: 16 / 9;
  border: 1px dashed var(--el-border-color);
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  cursor: pointer;
  color: var(--el-text-color-placeholder);
  font-size: 13px;
  transition: border-color 0.2s, color 0.2s;
  &:hover {
    border-color: var(--el-color-primary);
    color: var(--el-color-primary);
  }
}

/* 表单提示 */
.form-tip {
  margin-left: 8px;
  font-size: 12px;
  color: var(--el-text-color-placeholder);
}

/* 时间区域 */
.time-section {
  width: 100%;
  .time-range {
    display: flex;
    flex-direction: column;
    gap: 8px;
    margin-top: 8px;
  }
}

/* 弹窗样式 */
:deep(.el-dialog) {
  border-radius: 16px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  .el-dialog__header {
    padding: 20px 24px 12px;
    .el-dialog__title { font-weight: 700; font-size: 16px; }
  }
  .el-dialog__body { padding: 12px 24px 20px; }
}

/* Switch 样式 */
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
</style>
