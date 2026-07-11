<template>
  <div class="topic-page">
    <div class="page-header">
      <h2>专题管理</h2>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索标题" clearable style="width: 180px" @input="handleSearch" @clear="handleSearch" />
        <el-select v-model="filterPublish" placeholder="发布状态" clearable style="width: 120px" @change="handleFilterChange">
          <el-option label="已发布" :value="true" />
          <el-option label="未发布" :value="false" />
        </el-select>
        <el-select v-model="filterTop" placeholder="置顶状态" clearable style="width: 120px" @change="handleFilterChange">
          <el-option label="已置顶" :value="true" />
          <el-option label="未置顶" :value="false" />
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
                <el-dropdown-item command="top">置顶</el-dropdown-item>
                <el-dropdown-item command="cancelTop">取消置顶</el-dropdown-item>
                <el-dropdown-item command="publish">发布</el-dropdown-item>
                <el-dropdown-item command="cancelPublish">取消发布</el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-button type="primary" @click="openDialog()">
          <el-icon><Plus /></el-icon> 新建专题
        </el-button>
        <el-button @click="showTrashBox = true">
          <el-icon><Delete /></el-icon> 回收站
        </el-button>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe :height="tableHeight" @selection-change="onSelectionChange">
      <el-table-column type="selection" width="40" align="center" />
      <el-table-column label="#" type="index" width="50" align="center" />
      <el-table-column label="封面" width="100" align="center">
        <template #default="{ row }">
          <AppImage v-if="row.cover" :src="row.cover" fit="cover" :preview-src-list="[row.cover]"
            style="width: 60px; height: 40px" />
          <span v-else class="muted">无</span>
        </template>
      </el-table-column>
      <el-table-column label="标题" prop="title" min-width="160" show-overflow-tooltip />
      <el-table-column label="摘要" prop="summary" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.summary || '-' }}</template>
      </el-table-column>
      <el-table-column label="文章数" prop="articleCount" width="80" align="center" />
      <el-table-column label="置顶" width="70" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.isTop" :loading="row._topLoading" @change="handleToggleTop(row)" />
        </template>
      </el-table-column>
      <el-table-column label="发布" width="70" align="center">
        <template #default="{ row }">
          <el-switch v-model="row.isPublish" :loading="row._publishLoading" @change="handleTogglePublish(row)" />
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" align="center">
        <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openCatalogDialog(row)">目录</el-button>
          <el-button text type="primary" size="small" @click="openDialog(row)">编辑</el-button>
          <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination v-model:current-page="pageNum" :page-size="pageSize" :page-sizes="[10, 20, 50]" :total="total" layout="total, sizes, prev, pager, next" background size="small" @current-change="loadData" @size-change="handleSizeChange" />
    </div>

    <!-- 新建/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑专题' : '新建专题'" width="520px" destroy-on-close>
      <el-form :model="form" label-width="80px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" clearable placeholder="请输入专题标题" />
        </el-form-item>
        <el-form-item label="摘要">
          <el-input v-model="form.summary" type="textarea" :rows="3" maxlength="500" clearable placeholder="请输入专题摘要" />
        </el-form-item>
        <el-form-item label="封面">
          <div class="cover-upload">
            <div v-if="form.cover" class="cover-preview" @click="openCoverCropper">
              <AppImage :src="form.cover" fit="cover" class="cover-img" />
              <div class="cover-overlay">裁剪 / 更换</div>
            </div>
            <div v-else class="cover-placeholder" @click="triggerCoverUpload">
              <el-icon :size="24"><Plus /></el-icon>
              <span>上传封面</span>
            </div>
            <input ref="coverInputRef" type="file" accept="image/*" style="display:none" @change="handleCoverFileChange" />
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 图片裁剪 -->
    <ImageCropper v-model="cropperVisible" :image-src="coverCropperSrc" :aspect-ratio="[16, 9]"
      output-type="image/webp" :max-output-width="1200" @crop="handleCoverCropped" />

    <!-- 目录管理弹窗 -->
    <el-dialog v-model="catalogDialogVisible" title="目录管理" width="700px" destroy-on-close>
      <div class="catalog-toolbar">
        <el-button size="small" @click="addRootNode">
          <el-icon><Plus /></el-icon> 添加目录
        </el-button>
        <el-button size="small" @click="openArticleSelectFromRoot">
          <el-icon><DocumentAdd /></el-icon> 添加文章
        </el-button>
        <el-button size="small" @click="reindexAllNodes" title="按层级重新编号所有节点标题">
          <el-icon><Sort /></el-icon> 整理序号
        </el-button>
      </div>
      <el-tree
        v-if="catalogTree.length"
        ref="treeRef"
        :data="catalogTree"
        node-key="__key"
        :props="{ children: 'children', label: 'title' }"
        default-expand-all
        :draggable="!editingNodeKey"
        :allow-drop="allowDrop"
        @node-drop="handleNodeDrop"
        class="catalog-tree"
      >
        <template #default="{ node, data }">
          <div class="tree-node">
            <div class="node-content">
              <el-icon v-if="data.articleId" class="node-icon article"><Document /></el-icon>
              <el-icon v-else class="node-icon folder"><Folder /></el-icon>
              <template v-if="editingNodeKey === data.__key">
                <el-input ref="editInputRef" v-model="editingTitle" size="small" style="width: 240px" clearable
                  @keyup.enter="confirmEditNode(data)" @clear="confirmEditNode(data)" />
                <el-button text type="primary" size="small" @click.stop="confirmEditNode(data)" style="margin-left: 4px">
                  <el-icon><Check /></el-icon>
                </el-button>
              </template>
              <template v-else>
                <span class="node-title" @dblclick="startEditNode(data)">{{ data.title }}</span>
              </template>
            </div>
            <div v-if="editingNodeKey !== data.__key" class="node-actions">
              <el-button text size="small" @click.stop="startEditNode(data)" title="重命名"><el-icon><Edit /></el-icon></el-button>
              <el-button v-if="!data.articleId" text size="small" @click.stop="addChildNode(data)" title="添加子目录"><el-icon><FolderAdd /></el-icon></el-button>
              <el-button v-if="!data.articleId" text size="small" @click.stop="openArticleSelect(data)" title="添加文章"><el-icon><DocumentAdd /></el-icon></el-button>
              <el-button text size="small" @click.stop="moveNodeUp(data, node)" title="上移"><el-icon><Top /></el-icon></el-button>
              <el-button text size="small" @click.stop="moveNodeDown(data, node)" title="下移"><el-icon><Bottom /></el-icon></el-button>
              <el-button text type="danger" size="small" @click.stop="deleteNode(data, node)" title="删除"><el-icon><Delete /></el-icon></el-button>
            </div>
          </div>
        </template>
      </el-tree>
      <el-empty v-else description="暂无目录，点击上方按钮添加" :image-size="80" />
      <template #footer>
        <el-button @click="catalogDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="catalogSaving" @click="handleSaveCatalogs">保存</el-button>
      </template>
    </el-dialog>

    <!-- 文章选择弹窗 -->
    <el-dialog v-model="articleSelectVisible" title="选择文章" width="700px" destroy-on-close append-to-body>
      <div class="article-select-header">
        <el-input v-model="articleKeyword" placeholder="搜索文章标题" clearable style="width: 240px" @input="handleArticleSearch" @clear="handleArticleSearch" />
      </div>
      <el-table :data="articleList" v-loading="articleLoading" stripe height="400" @selection-change="onArticleSelectionChange" ref="articleTableRef">
        <el-table-column type="selection" width="40" align="center" :selectable="isArticleSelectable" />
        <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
        <el-table-column label="分类" prop="categoryName" width="120" />
        <el-table-column label="状态" width="80" align="center">
          <template #default="{ row }">
            <el-tag v-if="referencedIds.has(row.id)" type="info" size="small">已引用</el-tag>
            <el-tag v-else type="success" size="small">可选</el-tag>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-wrap">
        <el-pagination v-model:current-page="articlePageNum" :page-size="articlePageSize" :total="articleTotal" layout="total, prev, pager, next" background size="small" @current-change="loadArticles" />
      </div>
      <template #footer>
        <el-button @click="articleSelectVisible = false">取消</el-button>
        <el-button type="primary" :disabled="selectedArticles.length === 0" @click="handleArticleConfirm">
          确认添加 ({{ selectedArticles.length }})
        </el-button>
      </template>
    </el-dialog>

    <!-- 回收站弹窗 -->
    <el-dialog v-model="showTrashBox" title="回收站" width="700px" destroy-on-close>
      <div class="trash-toolbar">
        <el-input v-model="trashKeyword" placeholder="搜索标题" clearable style="width: 200px" @input="handleTrashSearch" @clear="handleTrashSearch">
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
            <template #default="{ $index }">{{ (trashPageNum - 1) * trashPageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="标题" prop="title" min-width="200" show-overflow-tooltip />
          <el-table-column label="删除时间" width="170" align="center">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="回收站为空" />
      </div>
      <div class="pagination-wrap" v-if="trashTotal > trashPageSize">
        <el-pagination v-model:current-page="trashPageNum" v-model:page-size="trashPageSize"
          :total="trashTotal" :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next" background size="small"
          @size-change="loadTrash" @current-change="loadTrash" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Plus, ArrowDown, Document, Folder, Edit, Delete, Top, Bottom, DocumentAdd, Check, Search, Sort, FolderAdd } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ElTree } from 'element-plus'
import { topicApi, type TopicVO, type SaveTopicParams, type CatalogNode } from '~/api/content/topic'
import { postApi, type PostVO } from '~/api/content/post'
import { uploadApi } from '~/api/system/upload'
import { collectArticleIds, recalculateSort } from '~/utils/content/topicCatalog'

// ========== 表格高度自适应 ==========
const tableHeight = useAdminTableHeight()

// ========== 列表与分页 ==========
const loading = ref(false)
const records = ref<TopicVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

// ========== 搜索筛选 ==========
const keyword = ref('')
const filterPublish = ref<boolean | ''>('')
const filterTop = ref<boolean | ''>('')
let searchTimer: ReturnType<typeof setTimeout> | null = null

// ========== 多选 ==========
const selectedIds = ref<number[]>([])

// ========== 新建/编辑弹窗 ==========
const dialogVisible = ref(false)
const submitting = ref(false)
const editingId = ref<number | null>(null)
const form = reactive<SaveTopicParams>({ title: '', cover: '', summary: '' })
const cropperVisible = ref(false)
const coverCropperSrc = ref('')
const coverInputRef = ref<HTMLInputElement>()
const pendingCoverFile = ref<File | null>(null)

// ========== 目录管理 ==========
const catalogDialogVisible = ref(false)
const currentTopicId = ref<number | null>(null)
const catalogTree = ref<(CatalogNode & { __key: string })[]>([])
const catalogSaving = ref(false)
const editingNodeKey = ref<string | null>(null)
const editingTitle = ref('')
const editInputRef = ref()
const treeRef = ref<InstanceType<typeof ElTree>>()
let nodeKeyCounter = 0
const addArticleTargetNode = ref<(CatalogNode & { __key: string }) | null>(null)

// ========== 文章选择 ==========
const articleSelectVisible = ref(false)
const articleLoading = ref(false)
const articleList = ref<PostVO[]>([])
const articleTotal = ref(0)
const articlePageNum = ref(1)
const articlePageSize = ref(10)
const articleKeyword = ref('')
const referencedIds = ref<Set<number>>(new Set())
const selectedArticles = ref<PostVO[]>([])
const articleTableRef = ref()

// ========== 工具函数 ==========
function formatTime(t: string | null) {
  return t ? t.replace('T', ' ').slice(0, 16) : ''
}

function generateKey(): string {
  return `node_${++nodeKeyCounter}_${Date.now()}`
}

/** 为树节点递归添加 __key */
function assignKeys(nodes: CatalogNode[]): (CatalogNode & { __key: string })[] {
  return nodes.map(n => ({
    ...n,
    __key: generateKey(),
    children: n.children ? assignKeys(n.children) : [],
  }))
}

/** 从带 __key 的树中移除 __key，还原为纯 CatalogNode */
function stripKeys(nodes: (CatalogNode & { __key: string })[]): CatalogNode[] {
  return nodes.map(n => {
    const { __key, ...rest } = n
    return { ...rest, children: n.children ? stripKeys(n.children as (CatalogNode & { __key: string })[]) : [] }
  })
}

// ========== 列表加载 ==========
async function loadData() {
  loading.value = true
  try {
    const res = await topicApi.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
      isPublish: filterPublish.value === '' ? undefined : filterPublish.value,
      isTop: filterTop.value === '' ? undefined : filterTop.value,
    })
    records.value = res.data.records
    total.value = res.data.total
    selectedIds.value = []
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    pageNum.value = 1
    loadData()
  }, 300)
}

function handleFilterChange() {
  pageNum.value = 1
  loadData()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  pageNum.value = 1
  loadData()
}

function onSelectionChange(rows: TopicVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

// ========== 新建/编辑 ==========
function openDialog(row?: TopicVO) {
  editingId.value = row?.id || null
  form.title = row?.title || ''
  form.cover = row?.cover || ''
  form.summary = row?.summary || ''
  pendingCoverFile.value = null
  coverCropperSrc.value = ''
  dialogVisible.value = true
}

async function handleSubmit() {
  if (!form.title.trim()) {
    ElMessage.warning('专题标题不能为空')
    return
  }
  submitting.value = true
  try {
    // 如果有待上传的封面文件，先上传
    if (pendingCoverFile.value) {
      const res = await uploadApi.image(pendingCoverFile.value, 'cover')
      if (form.cover?.startsWith('blob:')) URL.revokeObjectURL(form.cover)
      form.cover = res.data
      pendingCoverFile.value = null
    }
    if (editingId.value) {
      await topicApi.update(editingId.value, form)
      ElMessage.success('更新成功')
    } else {
      await topicApi.create(form)
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

async function handleCoverCropped({ blob, url }: { blob: Blob; url: string }) {
  // 释放旧的 blob URL
  if (form.cover?.startsWith('blob:')) URL.revokeObjectURL(form.cover)
  // 暂存 blob URL 作为预览，提交时再上传
  form.cover = url
  const ext = blob.type === 'image/webp' ? 'webp' : blob.type === 'image/png' ? 'png' : 'jpg'
  pendingCoverFile.value = new File([blob], `cover.${ext}`, { type: blob.type })
}

function triggerCoverUpload() {
  coverInputRef.value?.click()
}

function canCropImageSource(src: string) {
  if (!src) return false
  if (src.startsWith('blob:') || src.startsWith('data:') || src.startsWith('/')) return true
  if (typeof window === 'undefined') return false
  try {
    return new URL(src, window.location.origin).origin === window.location.origin
  } catch {
    return false
  }
}

function openCoverCropper() {
  // 已有封面时，将绝对 URL 转为相对路径避免跨域
  const apiBase = useRuntimeConfig().public.apiBase as string
  const origin = apiBase.replace(/\/api\/?$/, '')
  const src = form.cover?.startsWith(origin)
    ? form.cover.slice(origin.length)
    : (form.cover || '')
  if (!canCropImageSource(src)) {
    ElMessage.info('远程图片无法直接裁剪，请选择新图片')
    triggerCoverUpload()
    return
  }
  coverCropperSrc.value = src
  cropperVisible.value = true
}

function handleCoverFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = (ev) => {
    coverCropperSrc.value = ev.target?.result as string
    cropperVisible.value = true
  }
  reader.readAsDataURL(file)
  if (coverInputRef.value) coverInputRef.value.value = ''
}

// ========== 状态切换（乐观更新） ==========
async function handleToggleTop(row: TopicVO) {
  row._topLoading = true
  try {
    await topicApi.toggleTop(row.id)
  } catch (e: unknown) {
    row.isTop = !row.isTop
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    row._topLoading = false
  }
}

async function handleTogglePublish(row: TopicVO) {
  row._publishLoading = true
  try {
    await topicApi.togglePublish(row.id)
  } catch (e: unknown) {
    row.isPublish = !row.isPublish
    ElMessage.error((e as Error).message || '操作失败')
  } finally {
    row._publishLoading = false
  }
}

// ========== 删除 ==========
async function handleDelete(row: TopicVO) {
  await ElMessageBox.confirm(`确定删除专题「${row.title}」？将同时删除所有目录。`, '提示', { type: 'warning' })
  try {
    await topicApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

// ========== 批量操作 ==========
async function handleBatchCommand(command: string) {
  const ids = selectedIds.value
  if (!ids.length) return
  try {
    switch (command) {
      case 'top':
        await topicApi.batchSetTop(ids, true)
        ElMessage.success('批量置顶成功')
        break
      case 'cancelTop':
        await topicApi.batchSetTop(ids, false)
        ElMessage.success('批量取消置顶成功')
        break
      case 'publish':
        await topicApi.batchSetPublish(ids, true)
        ElMessage.success('批量发布成功')
        break
      case 'cancelPublish':
        await topicApi.batchSetPublish(ids, false)
        ElMessage.success('批量取消发布成功')
        break
      case 'delete':
        await ElMessageBox.confirm(`确定删除选中的 ${ids.length} 个专题？`, '提示', { type: 'warning' })
        await topicApi.batchDelete(ids)
        ElMessage.success('批量删除成功')
        break
    }
    loadData()
  } catch (e: unknown) {
    if ((e as Error).message !== 'cancel') {
      ElMessage.error((e as Error).message || '操作失败')
    }
  }
}

// ========== 目录管理 ==========
async function openCatalogDialog(row: TopicVO) {
  currentTopicId.value = row.id
  catalogDialogVisible.value = true
  try {
    const res = await topicApi.getCatalogs(row.id)
    catalogTree.value = assignKeys(res.data)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载目录失败')
    catalogTree.value = []
  }
}

function addRootNode() {
  const idx = catalogTree.value.length + 1
  const key = generateKey()
  catalogTree.value.push({
    id: null,
    articleId: null,
    title: `第${idx}章`,
    level: 1,
    parentId: 0,
    sort: catalogTree.value.length,
    children: [],
    __key: key,
  })
  // 自动进入编辑模式
  nextTick(() => {
    editingNodeKey.value = key
    editingTitle.value = `第${idx}章`
    nextTick(() => {
      editInputRef.value?.focus()
      editInputRef.value?.select()
    })
  })
}

/** 在指定目录节点下添加子目录 */
function addChildNode(parentData: CatalogNode & { __key: string }) {
  if (!parentData.children) parentData.children = []
  const children = parentData.children as (CatalogNode & { __key: string })[]
  const idx = children.length + 1
  const key = generateKey()
  children.push({
    id: null,
    articleId: null,
    title: `${idx}`,
    level: parentData.level + 1,
    parentId: parentData.id ?? 0,
    sort: children.length,
    children: [],
    __key: key,
  })
  catalogTree.value = [...catalogTree.value]
  // 自动进入编辑模式
  nextTick(() => {
    editingNodeKey.value = key
    editingTitle.value = `${idx}`
    nextTick(() => {
      editInputRef.value?.focus()
      editInputRef.value?.select()
    })
  })
}

function startEditNode(data: CatalogNode & { __key: string }) {
  editingNodeKey.value = data.__key
  editingTitle.value = data.title
  nextTick(() => {
    editInputRef.value?.focus()
    editInputRef.value?.select()
  })
}

function confirmEditNode(data: CatalogNode & { __key: string }) {
  if (editingTitle.value.trim()) {
    data.title = editingTitle.value.trim()
  }
  editingNodeKey.value = null
}

async function deleteNode(data: CatalogNode & { __key: string }, node: { parent: { data: CatalogNode & { __key: string } } }) {
  // 有子节点时二次确认
  if (data.children?.length) {
    await ElMessageBox.confirm(`目录「${data.title}」下有 ${data.children.length} 个子节点，确定删除？`, '提示', { type: 'warning' })
  }
  const siblings = getSiblings(data, node)
  const idx = siblings.findIndex(n => n.__key === data.__key)
  if (idx !== -1) {
    siblings.splice(idx, 1)
    recalculateSort(siblings)
    catalogTree.value = [...catalogTree.value]
  }
}

/** 获取节点所在的兄弟数组 */
function getSiblings(data: CatalogNode & { __key: string }, node: { parent: { data: CatalogNode & { __key: string } } }): (CatalogNode & { __key: string })[] {
  const parentData = node.parent?.data
  if (parentData && parentData.children) {
    return parentData.children as (CatalogNode & { __key: string })[]
  }
  return catalogTree.value
}

function moveNodeUp(data: CatalogNode & { __key: string }, node: { parent: { data: CatalogNode & { __key: string } } }) {
  const siblings = getSiblings(data, node)
  const idx = siblings.findIndex(n => n.__key === data.__key)
  if (idx > 0) {
    siblings.splice(idx, 1)
    siblings.splice(idx - 1, 0, data)
    recalculateSort(siblings)
    // 触发 el-tree 重新渲染
    catalogTree.value = [...catalogTree.value]
  }
}

function moveNodeDown(data: CatalogNode & { __key: string }, node: { parent: { data: CatalogNode & { __key: string } } }) {
  const siblings = getSiblings(data, node)
  const idx = siblings.findIndex(n => n.__key === data.__key)
  if (idx >= 0 && idx < siblings.length - 1) {
    siblings.splice(idx, 1)
    siblings.splice(idx + 1, 0, data)
    recalculateSort(siblings)
    catalogTree.value = [...catalogTree.value]
  }
}

function allowDrop(_draggingNode: unknown, _dropNode: unknown, type: string) {
  // 不允许放在文章节点内部
  return type !== 'inner' || !((_dropNode as { data: CatalogNode }).data.articleId)
}

function handleNodeDrop() {
  // 拖拽后重新计算整棵树的 level、parentId、sort，并自动重新编号
  recalculateTree(catalogTree.value, 0, 1)
  reindexTreeTitles(catalogTree.value, '')
  catalogTree.value = [...catalogTree.value]
}

function recalculateTree(nodes: (CatalogNode & { __key: string })[], parentId: number, level: number) {
  for (const [index, node] of nodes.entries()) {
    node.parentId = parentId
    node.level = level
    node.sort = index
    if (node.children?.length) {
      // 使用 __key 的数字部分作为临时 parentId（因为新节点没有真实 id）
      recalculateTree(node.children as (CatalogNode & { __key: string })[], node.id ?? 0, level + 1)
    }
  }
}

/**
 * 递归重新编号所有节点标题
 * 一级目录：第1章、第2章...（纯目录节点）或 1 文章标题（文章节点）
 * 二级及以下：1.1、1.2、2.1.1...
 * 编号前缀会替换标题中已有的序号前缀
 */
function reindexTreeTitles(nodes: (CatalogNode & { __key: string })[], parentPrefix: string) {
  for (const [index, node] of nodes.entries()) {
    const idx = index + 1
    const rawTitle = stripTitlePrefix(node.title)

    if (!parentPrefix) {
      // 一级节点
      if (node.articleId) {
        node.title = rawTitle ? `${idx} ${rawTitle}` : `${idx}`
      } else {
        node.title = rawTitle ? `第${idx}章 ${rawTitle}` : `第${idx}章`
      }
      // 递归子节点
      if (node.children?.length) {
        reindexTreeTitles(node.children as (CatalogNode & { __key: string })[], `${idx}`)
      }
    } else {
      // 二级及以下
      const prefix = `${parentPrefix}.${idx}`
      node.title = rawTitle ? `${prefix} ${rawTitle}` : prefix
      if (node.children?.length) {
        reindexTreeTitles(node.children as (CatalogNode & { __key: string })[], prefix)
      }
    }
  }
}

/** 去除标题中已有的序号前缀，返回纯标题文本 */
function stripTitlePrefix(title: string): string {
  // 匹配 "第N章 "、"N.N.N " 或 "N " 开头的前缀
  return title
    .replace(/^第\d+章\s*/, '')
    .replace(/^[\d.]+\s+/, '')
    .trim()
}

/** 整理序号：对整棵目录树重新编号 */
function reindexAllNodes() {
  if (!catalogTree.value.length) return
  recalculateTree(catalogTree.value, 0, 1)
  reindexTreeTitles(catalogTree.value, '')
  catalogTree.value = [...catalogTree.value]
  ElMessage.success('序号整理完成')
}

async function handleSaveCatalogs() {
  if (!currentTopicId.value) return
  catalogSaving.value = true
  try {
    const cleanTree = stripKeys(catalogTree.value)
    await topicApi.saveCatalogs(currentTopicId.value, cleanTree)
    ElMessage.success('目录保存成功')
    catalogDialogVisible.value = false
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    catalogSaving.value = false
  }
}

// ========== 文章选择 ==========
function openArticleSelect(targetNode: CatalogNode & { __key: string }) {
  addArticleTargetNode.value = targetNode
  articleKeyword.value = ''
  articlePageNum.value = 1
  selectedArticles.value = []
  articleSelectVisible.value = true
  loadArticles()
  loadReferencedIds()
}

function openArticleSelectFromRoot() {
  // 添加到根级别
  addArticleTargetNode.value = null
  articleKeyword.value = ''
  articlePageNum.value = 1
  selectedArticles.value = []
  articleSelectVisible.value = true
  loadArticles()
  loadReferencedIds()
}

async function loadArticles() {
  articleLoading.value = true
  try {
    const res = await postApi.page({
      pageNum: articlePageNum.value,
      pageSize: articlePageSize.value,
      keyword: articleKeyword.value || undefined,
      status: 'published',
    })
    articleList.value = res.data.records
    articleTotal.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载文章失败')
  } finally {
    articleLoading.value = false
  }
}

let articleSearchTimer: ReturnType<typeof setTimeout> | null = null
function handleArticleSearch() {
  if (articleSearchTimer) clearTimeout(articleSearchTimer)
  articleSearchTimer = setTimeout(() => {
    articlePageNum.value = 1
    loadArticles()
  }, 300)
}

async function loadReferencedIds() {
  if (!currentTopicId.value) return
  try {
    const res = await topicApi.getArticleIds(currentTopicId.value)
    // 同时收集本地目录树中已有的 articleId
    const localIds = collectArticleIds(catalogTree.value)
    referencedIds.value = new Set([...res.data, ...localIds])
  } catch {
    referencedIds.value = new Set()
  }
}

function isArticleSelectable(row: PostVO) {
  return !referencedIds.value.has(row.id)
}

function onArticleSelectionChange(rows: PostVO[]) {
  selectedArticles.value = rows.filter(r => !referencedIds.value.has(r.id))
}

function handleArticleConfirm() {
  const targetChildren = addArticleTargetNode.value
    ? (addArticleTargetNode.value.children as (CatalogNode & { __key: string })[])
    : catalogTree.value
  const baseLevel = addArticleTargetNode.value ? addArticleTargetNode.value.level + 1 : 1
  const parentId = addArticleTargetNode.value?.id ?? 0

  // 计算父节点在兄弟中的序号（用于生成如 "1.1" 的前缀）
  const parentIndex = addArticleTargetNode.value
    ? getNodeIndex(addArticleTargetNode.value)
    : ''

  for (const article of selectedArticles.value) {
    const childIdx = targetChildren.length + 1
    const prefix = parentIndex ? `${parentIndex}.${childIdx}` : `${childIdx}`
    targetChildren.push({
      id: null,
      articleId: article.id,
      title: `${prefix} ${article.title}`,
      level: baseLevel,
      parentId: parentId,
      sort: targetChildren.length,
      children: [],
      __key: generateKey(),
    })
  }
  recalculateSort(targetChildren)
  catalogTree.value = [...catalogTree.value]
  articleSelectVisible.value = false
  selectedArticles.value = []
}

/** 获取节点在其兄弟中的序号字符串（如根节点第2个返回 "2"） */
function getNodeIndex(node: CatalogNode & { __key: string }): string {
  // 简单实现：在 catalogTree 中递归查找
  function findIndex(nodes: (CatalogNode & { __key: string })[], target: string, prefix: string): string {
    for (const [index, currentNode] of nodes.entries()) {
      const current = prefix ? `${prefix}.${index + 1}` : `${index + 1}`
      if (currentNode.__key === target) return current
      if (currentNode.children?.length) {
        const found = findIndex(currentNode.children as (CatalogNode & { __key: string })[], target, current)
        if (found) return found
      }
    }
    return ''
  }
  return findIndex(catalogTree.value, node.__key, '')
}

onMounted(loadData)

// ========== 回收站 ==========
const showTrashBox = ref(false)
const trashRecords = ref<TopicVO[]>([])
const trashLoading = ref(false)
const trashTotal = ref(0)
const trashPageNum = ref(1)
const trashPageSize = ref(10)
const trashKeyword = ref('')
const selectedTrashIds = ref<number[]>([])
let trashSearchTimer: ReturnType<typeof setTimeout> | null = null

function onTrashSelectionChange(rows: TopicVO[]) {
  selectedTrashIds.value = rows.map(r => r.id)
}

watch(showTrashBox, (val) => {
  if (val) {
    trashKeyword.value = ''
    trashPageNum.value = 1
    selectedTrashIds.value = []
    loadTrash()
  }
})

function handleTrashSearch() {
  if (trashSearchTimer) clearTimeout(trashSearchTimer)
  trashSearchTimer = setTimeout(() => {
    trashPageNum.value = 1
    loadTrash()
  }, 300)
}

async function loadTrash() {
  trashLoading.value = true
  try {
    const res = await topicApi.trashPage({
      pageNum: trashPageNum.value,
      pageSize: trashPageSize.value,
      keyword: trashKeyword.value || undefined,
    })
    trashRecords.value = res.data.records
    trashTotal.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载回收站失败')
  } finally {
    trashLoading.value = false
  }
}

async function handleBatchRestore() {
  try {
    await topicApi.batchRestore(selectedTrashIds.value)
    ElMessage.success('恢复成功')
    selectedTrashIds.value = []
    loadTrash()
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '恢复失败')
  }
}

async function handleBatchPermanentDelete() {
  await ElMessageBox.confirm(`确定彻底删除选中的 ${selectedTrashIds.value.length} 个专题？此操作不可恢复。`, '提示', { type: 'warning' })
  try {
    await topicApi.batchPermanentDelete(selectedTrashIds.value)
    ElMessage.success('彻底删除成功')
    selectedTrashIds.value = []
    loadTrash()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '删除失败')
  }
}

async function handleClearTrash() {
  await ElMessageBox.confirm('确定清空回收站？所有已删除专题将被永久删除，此操作不可恢复。', '提示', { type: 'warning' })
  try {
    await topicApi.clearTrash()
    ElMessage.success('回收站已清空')
    selectedTrashIds.value = []
    loadTrash()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '清空失败')
  }
}
</script>

<style scoped lang="scss">
.topic-page {
  .muted { color: var(--el-text-color-regular); font-size: 13px; }
}

// 封面上传区域
.cover-upload {
  display: flex; align-items: flex-end; gap: 8px;
  .cover-preview {
    width: 160px; height: 90px; border-radius: 8px; cursor: pointer;
    border: 1px solid var(--el-border-color-lighter); position: relative; overflow: hidden;
    .cover-img { width: 100%; height: 100%; display: block; }
    .cover-overlay {
      position: absolute; inset: 0; background: rgba(0, 0, 0, 0.45); color: #fff;
      display: flex; align-items: center; justify-content: center; font-size: 13px; font-weight: 500;
      opacity: 0; transition: opacity 0.2s;
    }
    &:hover .cover-overlay { opacity: 1; }
  }
  .cover-placeholder {
    width: 160px; height: 90px; border-radius: 8px; border: 1px dashed var(--el-border-color);
    display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
    cursor: pointer; color: var(--el-text-color-placeholder); font-size: 13px;
    transition: border-color 0.2s, color 0.2s;
    &:hover { border-color: var(--el-color-primary); color: var(--el-color-primary); }
  }
}

// 目录管理
.catalog-toolbar { display: flex; gap: 8px; margin-bottom: 12px; }
.catalog-tree { min-height: 200px; max-height: 400px; overflow-y: auto; }
.tree-node {
  display: flex; align-items: center; justify-content: space-between; width: 100%; padding-right: 4px;
  .node-content { display: flex; align-items: center; gap: 6px; flex: 1; min-width: 0; }
  .node-icon { font-size: 16px; flex-shrink: 0; &.article { color: var(--el-color-primary); } &.folder { color: var(--el-color-warning); } }
  .node-title { font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; cursor: text; }
  .node-actions {
    display: flex; gap: 0; flex-shrink: 0; opacity: 0; transition: opacity 0.15s;
    .el-button { padding: 2px 4px; }
  }
  &:hover .node-actions { opacity: 1; }
}

// 文章选择
.article-select-header { margin-bottom: 12px; }

// 回收站
.trash-toolbar {
  display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; gap: 8px; flex-wrap: wrap;
  .trash-actions { display: flex; gap: 8px; }
}

// ========== 页面特有样式 ==========
:deep(.el-switch) {
  --el-switch-on-color: var(--el-color-primary); --el-switch-off-color: var(--el-fill-color-darker); height: 20px;
}
:deep(.el-switch .el-switch__core) { height: 20px; min-width: 36px; border-radius: 10px; border: none; }
:deep(.el-switch .el-switch__core .el-switch__action) { width: 16px; height: 16px; }

:deep(.el-dialog) {
  border-radius: 16px; box-shadow: 0 8px 32px rgba(0, 0, 0, 0.08);
  .el-dialog__header { padding: 20px 24px 12px; .el-dialog__title { font-weight: 700; font-size: 16px; } }
  .el-dialog__body { padding: 12px 24px 20px; }
}
</style>
