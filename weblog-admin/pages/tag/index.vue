<template>
  <div class="tag-page">
    <div class="page-header">
      <h2>标签管理</h2>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索标签名称" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="header-actions">
        <template v-if="selectedIds.length > 0">
          <span class="selection-count">已选 {{ selectedIds.length }} 条</span>
          <el-button type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
        </template>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建标签
        </el-button>
      </div>
    </div>

    <el-table
      :data="pagedTags"
      v-loading="loading"
      stripe
      height="560"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column label="#" width="70" align="center">
        <template #default="{ $index }">{{ (currentPage - 1) * pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="名称" min-width="160">
        <template #default="{ row }">
          <el-tag>{{ row.name }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="Slug" min-width="140" show-overflow-tooltip>
        <template #default="{ row }">
          <span class="slug-text">/tag/{{ row.slug }}</span>
        </template>
      </el-table-column>
      <el-table-column label="文章数" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.articleCount ?? 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :total="filteredTags.length"
        :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next"
        background
        size="small"
      />
    </div>

    <!-- 新建/编辑弹窗（统一） -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" destroy-on-close @opened="onDialogOpened">
      <!-- 编辑模式：单个标签 -->
      <template v-if="editingId">
        <el-form ref="dialogFormRef" :model="singleForm" :rules="singleRules" label-width="60px">
          <el-form-item label="名称" prop="name">
            <el-input ref="nameInputRef" v-model="singleForm.name" maxlength="30" placeholder="标签名称" @keyup.enter="handleSubmitSingle" />
          </el-form-item>
          <el-form-item label="Slug">
            <el-input v-model="singleForm.slug" maxlength="80" placeholder="URL标识，留空自动生成拼音">
              <template #prepend>/tag/</template>
            </el-input>
          </el-form-item>
        </el-form>
      </template>

      <!-- 新建模式：支持批量 -->
      <template v-else>
        <div class="tag-input-area">
          <div class="tag-input-row">
            <el-input
              ref="nameInputRef"
              v-model="newTagName"
              maxlength="30"
              placeholder="输入标签名称后按回车添加"
              @keyup.enter="addTagToList"
              style="flex: 1"
            />
            <el-button @click="addTagToList" :disabled="!newTagName.trim()">添加</el-button>
          </div>
          <div class="tag-input-tip">提示：输入名称按回车添加到列表，可一次添加多个标签</div>
        </div>

        <!-- 待创建标签列表 -->
        <div v-if="pendingTags.length" class="pending-tags">
          <div class="pending-header">
            <span>待创建标签 ({{ pendingTags.length }})</span>
          </div>
          <div class="pending-list">
            <div v-for="(item, index) in pendingTags" :key="index" class="pending-item">
              <div class="pending-name">
                <el-tag closable @close="removePendingTag(index)">{{ item.name }}</el-tag>
              </div>
              <div class="pending-slug">
                <el-input v-model="item.slug" size="small" maxlength="80" placeholder="Slug（留空自动生成）">
                  <template #prepend>/tag/</template>
                </el-input>
              </div>
            </div>
          </div>
        </div>
        <div v-else class="empty-pending">
          <span>请在上方输入标签名称</span>
        </div>
      </template>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="editingId" type="primary" :loading="submitting" @click="handleSubmitSingle">确定</el-button>
        <el-button v-else type="primary" :loading="submitting" :disabled="pendingTags.length === 0" @click="handleSubmitBatch">
          创建 ({{ pendingTags.length }})
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { tagApi, type TagVO, type BatchTagItem } from '~/api/content/tag'

const loading = ref(false)
const allTags = ref<TagVO[]>([])
const keyword = ref('')
const selectedIds = ref<number[]>([])
const currentPage = ref(1)
const pageSize = ref(20)

// 搜索防抖
let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    currentPage.value = 1
    loadData()
  }, 300)
})

// 过滤 + 分页
const filteredTags = computed(() => allTags.value)
const pagedTags = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  return filteredTags.value.slice(start, start + pageSize.value)
})

// 弹窗状态
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const dialogFormRef = ref<FormInstance>()
const nameInputRef = ref()

const dialogTitle = computed(() => editingId.value ? '编辑标签' : '新建标签')

// 编辑模式表单
const singleForm = reactive({ name: '', slug: '' })
const singleRules: FormRules = {
  name: [{ required: true, message: '请输入标签名称', trigger: 'blur' }],
}

// 新建模式
const newTagName = ref('')
const pendingTags = ref<BatchTagItem[]>([])

function onSelectionChange(rows: TagVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

async function loadData() {
  loading.value = true
  try {
    const res = await tagApi.listAll(keyword.value || undefined)
    allTags.value = res.data
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openDialog(row?: TagVO) {
  editingId.value = row?.id || null
  if (row) {
    singleForm.name = row.name
    singleForm.slug = row.slug || ''
  } else {
    singleForm.name = ''
    singleForm.slug = ''
    newTagName.value = ''
    pendingTags.value = []
  }
  dialogVisible.value = true
}

function onDialogOpened() {
  nextTick(() => {
    dialogFormRef.value?.clearValidate()
  })
}

function addTagToList() {
  const name = newTagName.value.trim()
  if (!name) return
  if (pendingTags.value.some(t => t.name === name)) {
    ElMessage.warning('标签已在列表中')
    return
  }
  pendingTags.value.push({ name, slug: '' })
  newTagName.value = ''
  nextTick(() => nameInputRef.value?.focus())
}

function removePendingTag(index: number) {
  pendingTags.value.splice(index, 1)
}

async function handleSubmitSingle() {
  if (!dialogFormRef.value) return
  const valid = await dialogFormRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await tagApi.update(editingId.value!, { name: singleForm.name, slug: singleForm.slug || undefined })
    ElMessage.success('更新成功')
    dialogVisible.value = false
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    submitting.value = false
  }
}

async function handleSubmitBatch() {
  if (pendingTags.value.length === 0) return
  submitting.value = true
  try {
    const tags: BatchTagItem[] = pendingTags.value.map(t => ({
      name: t.name,
      slug: t.slug || undefined,
    }))
    if (tags.length === 1) {
      const firstTag = tags[0]
      if (!firstTag) return
      await tagApi.create({ name: firstTag.name, slug: firstTag.slug })
    } else {
      await tagApi.batchCreate(tags)
    }
    ElMessage.success(`成功创建 ${tags.length} 个标签`)
    dialogVisible.value = false
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '创建失败')
  } finally {
    submitting.value = false
  }
}

async function handleDelete(row: TagVO) {
  await ElMessageBox.confirm(`确定删除标签「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await tagApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  }
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个标签？`, '批量删除', { type: 'warning' })
  try {
    await tagApi.batchDelete(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.tag-page {
  .slug-text {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
  }
}

// ========== 弹窗内输入框组（带 prepend） ==========
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

// ========== 标签创建区域 ==========
.tag-input-area {
  margin-bottom: 16px;
  .tag-input-row {
    display: flex;
    gap: 8px;
  }
  .tag-input-tip {
    font-size: 12px;
    color: var(--el-text-color-secondary);
    margin-top: 8px;
  }
}

.pending-tags {
  .pending-header {
    font-size: 13px;
    font-weight: 600;
    color: var(--el-text-color-primary);
    margin-bottom: 10px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }
  .pending-list {
    max-height: 300px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .pending-item {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .pending-name {
    flex-shrink: 0;
    min-width: 100px;
  }
  .pending-slug { flex: 1; }
}

.empty-pending {
  text-align: center;
  padding: 32px 0;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
