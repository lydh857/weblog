<template>
  <div class="category-page">
    <div class="page-header">
      <h2>分类管理</h2>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索分类名称" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
      <div class="header-actions">
        <template v-if="selectedIds.length > 0">
          <span class="selection-count">已选 {{ selectedIds.length }} 条</span>
          <el-button type="danger" size="small" @click="handleBatchDelete">批量删除</el-button>
        </template>
        <el-button @click="expandAll" text>展开全部</el-button>
        <el-button @click="collapseAll" text>折叠全部</el-button>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建分类
        </el-button>
      </div>
    </div>

    <el-table
      ref="tableRef"
      :data="categoryTree"
      v-loading="loading"
      row-key="id"
      :default-expand-all="defaultExpand"
      height="560"
      stripe
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="45" />
      <el-table-column label="#" width="80" align="center">
        <template #default="{ row }">{{ getRowIndex(row) }}</template>
      </el-table-column>
      <el-table-column label="名称" min-width="180">
        <template #default="{ row }">
          {{ row.name }}<span v-if="row.parentId === 0 && row.children?.length" class="child-count">（{{ row.children.length }}）</span>
        </template>
      </el-table-column>
      <el-table-column label="文章数" width="90" align="center">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ row.articleCount ?? 0 }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="描述" prop="description" min-width="180" show-overflow-tooltip />
      <el-table-column label="排序" prop="sortOrder" width="70" align="center" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button v-if="row.parentId === 0" text size="small" @click="openDialog(undefined, row.id, row.name)">添加子分类</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="480px" destroy-on-close @opened="onDialogOpened">
      <div v-if="!editingId && parentName" class="parent-info">
        <span class="label">父分类：</span>
        <el-tag type="primary" size="small">{{ parentName }}</el-tag>
      </div>
      <el-form ref="dialogFormRef" :model="dialogForm" :rules="dialogRules" label-width="80px">
        <el-form-item v-if="!parentName" label="父分类">
          <el-select v-model="dialogForm.parentId" placeholder="顶级分类" clearable class="full-width">
            <el-option label="顶级分类" :value="0" />
            <el-option v-for="c in topCategories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称" prop="name">
          <el-input v-model="dialogForm.name" maxlength="50" placeholder="分类名称" @keyup.enter="handleSubmit" />
        </el-form-item>
        <el-form-item label="Slug">
          <el-input v-model="dialogForm.slug" maxlength="80" placeholder="URL标识，留空自动生成拼音">
            <template #prepend>/category/</template>
          </el-input>
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="dialogForm.description" type="textarea" :rows="2" maxlength="200" placeholder="分类描述（可选）" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="dialogForm.sortOrder" :min="0" :max="999" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button v-if="!editingId && parentName" type="info" :loading="submitting" @click="handleSubmitAndContinue">添加并继续</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { categoryApi, type CategoryVO } from '~/api/content/category'

const loading = ref(false)
const categories = ref<CategoryVO[]>([])
const keyword = ref('')
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const parentName = ref('')
const dialogFormRef = ref<FormInstance>()
const tableRef = ref()
const selectedIds = ref<number[]>([])
const defaultExpand = ref(true)

const dialogForm = reactive({
  name: '',
  slug: '',
  description: '',
  parentId: 0 as number,
  sortOrder: 0,
})

const dialogRules: FormRules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
}

const dialogTitle = computed(() => {
  if (editingId.value) return '编辑分类'
  if (parentName.value) return '添加子分类'
  return '新建分类'
})

interface TreeNode extends CategoryVO { children?: TreeNode[] }

const topCategories = computed(() => categories.value.filter(c => c.parentId === 0))

const categoryTree = computed<TreeNode[]>(() => {
  const tops = categories.value.filter(c => c.parentId === 0)
  return tops.map(top => ({
    ...top,
    children: categories.value.filter(c => c.parentId === top.id),
  }))
})

// 序号映射：父分类 1,2,3... 子分类 1.1, 1.2...
const indexMap = computed(() => {
  const map = new Map<number, string>()
  const tree = categoryTree.value
  tree.forEach((top, i) => {
    map.set(top.id, String(i + 1))
    top.children?.forEach((child, j) => {
      map.set(child.id, `${i + 1}.${j + 1}`)
    })
  })
  return map
})

function getRowIndex(row: CategoryVO) {
  return indexMap.value.get(row.id) || ''
}

// 搜索防抖
let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, (val) => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    loadData()
  }, 300)
})

function onSelectionChange(rows: CategoryVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

function expandAll() {
  defaultExpand.value = true
  const data = categories.value
  categories.value = []
  nextTick(() => { categories.value = data })
}

function collapseAll() {
  defaultExpand.value = false
  const data = categories.value
  categories.value = []
  nextTick(() => { categories.value = data })
}

async function loadData() {
  loading.value = true
  try {
    const res = await categoryApi.listAll(keyword.value || undefined)
    categories.value = res.data
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

// 计算下一个排序值
function getNextSortOrder(parentId: number): number {
  const siblings = categories.value.filter(c => c.parentId === parentId)
  if (siblings.length === 0) return 0
  return Math.max(...siblings.map(c => c.sortOrder)) + 1
}

function openDialog(row?: CategoryVO, parentId?: number, pName?: string) {
  editingId.value = row?.id || null
  parentName.value = pName || ''
  dialogForm.name = row?.name || ''
  dialogForm.slug = row?.slug || ''
  dialogForm.description = row?.description || ''
  dialogForm.parentId = parentId ?? row?.parentId ?? 0
  if (row) {
    // 编辑模式：保留原排序
    dialogForm.sortOrder = row.sortOrder ?? 0
  } else {
    // 新建模式：自动递增
    dialogForm.sortOrder = getNextSortOrder(parentId ?? 0)
  }
  dialogVisible.value = true
}

// 弹窗打开后清除校验
function onDialogOpened() {
  nextTick(() => {
    dialogFormRef.value?.clearValidate()
  })
}

async function doSubmit() {
  if (!dialogFormRef.value) return false
  const valid = await dialogFormRef.value.validate().catch(() => false)
  if (!valid) return false

  submitting.value = true
  try {
    const data = {
      name: dialogForm.name,
      slug: dialogForm.slug || undefined,
      description: dialogForm.description || undefined,
      parentId: dialogForm.parentId,
      sortOrder: dialogForm.sortOrder,
    }
    if (editingId.value) {
      await categoryApi.update(editingId.value, data)
      ElMessage.success('更新成功')
    } else {
      await categoryApi.create(data)
      ElMessage.success('创建成功')
    }
    loadData()
    return true
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
    return false
  } finally {
    submitting.value = false
  }
}

async function handleSubmit() {
  if (await doSubmit()) dialogVisible.value = false
}

async function handleSubmitAndContinue() {
  if (await doSubmit()) {
    dialogForm.name = ''
    dialogForm.slug = ''
    dialogForm.description = ''
    dialogForm.sortOrder = getNextSortOrder(dialogForm.parentId)
    nextTick(() => {
      dialogFormRef.value?.clearValidate()
    })
  }
}

async function handleDelete(row: CategoryVO) {
  await ElMessageBox.confirm(`确定删除分类「${row.name}」？`, '提示', { type: 'warning' })
  try {
    await categoryApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  }
}

async function handleBatchDelete() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 个分类？`, '批量删除', { type: 'warning' })
  try {
    await categoryApi.batchDelete(selectedIds.value)
    ElMessage.success('批量删除成功')
    selectedIds.value = []
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
// ========== 页面级配色覆盖 ==========
.category-page {
  .page-header {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
    gap: 12px;
    padding: 0 20px;
    height: 62px;
    background: var(--el-bg-color);
    border-radius: 10px;
    border: 1px solid var(--el-border-color-lighter);
    flex-wrap: wrap;
    h2 {
      font-size: 1.05rem;
      font-weight: 600;
      color: var(--el-text-color-primary);
      margin: 0;
      white-space: nowrap;
      letter-spacing: 0.3px;
    }
    .filter-bar {
      display: flex;
      gap: 8px;
      flex: 1;
      flex-wrap: wrap;
    }
  }
  .header-actions {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-shrink: 0;
    flex-wrap: wrap;
    .selection-count { font-size: 13px; color: var(--el-color-primary); font-weight: 500; }
  }
  .full-width { width: 100%; }
  .child-count {
    color: var(--el-text-color-secondary);
    font-size: 12px;
    margin-left: 2px;
  }
  .parent-info {
    margin-bottom: 16px;
    padding: 10px 12px;
    background: var(--el-fill-color-light);
    border-radius: 8px;
    display: flex;
    align-items: center;
    gap: 8px;
    .label { color: var(--el-text-color-secondary); font-size: 13px; }
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
</style>
