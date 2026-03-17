<template>
  <div class="post-list-page">
    <div class="page-header">
      <h2>文章管理</h2>
      <div class="filter-bar">
        <el-input v-model="filters.keyword" placeholder="搜索标题" clearable style="width: 180px"
          @clear="handleSearch" @keyup.enter="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.categoryId" placeholder="分类" clearable filterable @change="handleSearch" style="width: 140px">
          <el-option-group v-for="top in categoryTree" :key="top.id" :label="top.name">
            <el-option :label="top.name" :value="top.id" />
            <el-option v-for="sub in top.children" :key="sub.id" :label="'  └ ' + sub.name" :value="sub.id" />
          </el-option-group>
        </el-select>
        <el-select v-model="filters.tagId" placeholder="标签" clearable filterable @change="handleSearch" style="width: 130px">
          <el-option v-for="t in allTags" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
        <el-select v-model="filters.isDisabled" placeholder="状态" clearable @change="handleSearch" style="width: 100px">
          <el-option label="已启用" :value="false" />
          <el-option label="已禁用" :value="true" />
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
                <el-dropdown-item command="unTop">取消置顶</el-dropdown-item>
                <el-dropdown-item command="disable" divided>禁用</el-dropdown-item>
                <el-dropdown-item command="enable">启用</el-dropdown-item>
                <el-dropdown-item command="delete" divided>
                  <span style="color: var(--el-color-danger)">删除</span>
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <el-button @click="showTrashBox = true"><el-icon><Delete /></el-icon> 回收站</el-button>
        <el-button @click="showScheduledBox = true"><el-icon><Timer /></el-icon> 定时任务</el-button>
        <el-button @click="showDraftBox = true"><el-icon><Box /></el-icon> 草稿箱</el-button>
        <el-button type="primary" @click="navigateTo('/post/create')"><el-icon><EditPen /></el-icon> 写文章</el-button>
      </div>
    </div>
    <!-- 文章表格 -->
    <el-table :data="posts" v-loading="loading" stripe height="560" row-key="id"
      :row-class-name="({ row }: { row: PostVO }) => row.isDisabled ? 'row-disabled' : ''"
      @selection-change="handleSelectionChange">
      <el-table-column type="selection" width="40" />
      <el-table-column label="#" width="55" align="center">
        <template #default="{ $index }">{{ (pagination.pageNum - 1) * pagination.pageSize + $index + 1 }}</template>
      </el-table-column>
      <el-table-column label="封面" width="76" align="center">
        <template #default="{ row }">
          <AppImage v-if="row.coverImage" :src="row.coverImage" fit="cover" lazy class="cover-thumb"
            :preview-src-list="[row.coverImage]" />
          <div v-else class="no-cover"><el-icon :size="20"><Picture /></el-icon></div>
        </template>
      </el-table-column>
      <el-table-column label="标题" min-width="200">
        <template #default="{ row }">
          <div class="post-title-cell">
            <el-tag v-if="row.isTop" type="danger" size="small" effect="plain" round>置顶</el-tag>
            <el-tooltip :content="row.title" placement="top" :show-after="300" :disabled="!row.title || row.title.length <= 20">
              <span class="post-title text-ellipsis" @click="navigateTo(`/post/create?id=${row.id}`)">{{ row.title }}</span>
            </el-tooltip>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="分类" min-width="120">
        <template #default="{ row }">
          <span class="category-text">{{ row.categoryName }}</span>
          <span v-if="row.subCategoryName" class="sub-category"> / {{ row.subCategoryName }}</span>
        </template>
      </el-table-column>
      <el-table-column label="标签" min-width="120">
        <template #default="{ row }">
          <el-tooltip :content="row.tags?.map((t: TagVO) => t.name).join('、')" placement="top" :show-after="300"
            :disabled="!row.tags || row.tags.length <= 1">
            <span class="tag-text">{{ row.tags?.map((t: TagVO) => t.name).join('、') }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="浏览" width="90" align="center" sortable :sort-method="(a: PostVO, b: PostVO) => (a.viewCount ?? 0) - (b.viewCount ?? 0)">
        <template #default="{ row }">
          <el-tooltip :content="(row.viewCount ?? 0).toLocaleString()" placement="top" :show-after="300"
            :disabled="(row.viewCount ?? 0) < 1000">
            <span class="view-count">{{ formatViewCount(row.viewCount ?? 0) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column label="创建时间" width="170" sortable :sort-method="(a: PostVO, b: PostVO) => new Date(a.createTime).getTime() - new Date(b.createTime).getTime()">
        <template #default="{ row }">
          <span class="time-text">{{ formatTime(row.createTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="置顶" width="65" align="center">
        <template #default="{ row }">
          <el-switch :model-value="!!row.isTop" :loading="row._topLoading" @change="handleToggleTop(row)" />
        </template>
      </el-table-column>
      <el-table-column label="禁用" width="65" align="center">
        <template #default="{ row }">
          <el-switch :model-value="!!row.isDisabled" :loading="row._disableLoading" @change="handleToggleDisabled(row)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="navigateTo(`/post/create?id=${row.id}`)">编辑</el-button>
          <el-popconfirm title="确定删除该文章？" @confirm="handleDelete(row)" width="200">
            <template #reference>
              <el-button text type="danger" size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>
    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination v-model:current-page="pagination.pageNum" v-model:page-size="pagination.pageSize"
        :total="pagination.total" :page-sizes="[10, 20, 50, 100]"
        layout="total, sizes, prev, pager, next" background size="small"
        @size-change="loadData" @current-change="loadData" />
    </div>


    <!-- 草稿箱弹窗 -->
    <el-dialog v-model="showDraftBox" title="草稿箱" width="900px" destroy-on-close>
      <div class="draft-toolbar">
        <el-input v-model="draftKeyword" placeholder="搜索草稿标题" clearable style="width: 200px"
          @clear="() => { draftPagination.pageNum = 1; loadDrafts() }" @keyup.enter="() => { draftPagination.pageNum = 1; loadDrafts() }">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="draft-actions">
          <el-button type="primary" size="small" :disabled="selectedDraftIds.length === 0" @click="openBatchSchedule">
            定时发布{{ selectedDraftIds.length > 0 ? ` (${selectedDraftIds.length})` : '' }}
          </el-button>
          <el-button type="success" size="small" :disabled="selectedDraftIds.length === 0" @click="handleBatchPublishDrafts">
            批量发布{{ selectedDraftIds.length > 0 ? ` (${selectedDraftIds.length})` : '' }}
          </el-button>
          <el-button type="danger" size="small" :disabled="selectedDraftIds.length === 0" @click="handleBatchDeleteDrafts">
            批量删除{{ selectedDraftIds.length > 0 ? ` (${selectedDraftIds.length})` : '' }}
          </el-button>
        </div>
      </div>
      <div v-loading="draftLoading">
        <el-table :data="drafts" stripe max-height="400px" v-if="drafts.length" row-key="id"
          @selection-change="onDraftSelectionChange">
          <el-table-column type="selection" width="40" />
          <el-table-column label="序号" width="55" align="center">
            <template #default="{ $index }">{{ $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="标题" min-width="180">
            <template #default="{ row }">
              <el-tooltip :content="row.title || '无标题'" placement="top" :show-after="300" :disabled="!row.title || row.title.length <= 18">
                <span class="draft-title text-ellipsis">{{ row.title || '无标题' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="更新时间" width="135">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="220" align="center">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="editDraft(row)">编辑</el-button>
              <el-button text size="small" @click="openSingleSchedule(row)">定时</el-button>
              <el-button text type="success" size="small" @click="handlePublishDraft(row)">发布</el-button>
              <el-button text type="danger" size="small" @click="deleteDraft(row)">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无草稿" />
      </div>
      <div class="draft-pagination" v-if="draftPagination.total > 10">
        <el-pagination v-model:current-page="draftPagination.pageNum" v-model:page-size="draftPagination.pageSize"
          :total="draftPagination.total" :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next" background size="small"
          @size-change="loadDrafts" @current-change="loadDrafts" />
      </div>
    </el-dialog>


    <!-- 定时任务弹窗 -->
    <el-dialog v-model="showScheduledBox" title="定时发布队列" width="900px" destroy-on-close>
      <div class="draft-toolbar">
        <el-input v-model="scheduledKeyword" placeholder="搜索标题" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="draft-actions">
          <el-button type="primary" size="small" :disabled="selectedScheduledIds.length === 0" @click="handleBatchPublishScheduled">
            立即发布{{ selectedScheduledIds.length > 0 ? ` (${selectedScheduledIds.length})` : '' }}
          </el-button>
          <el-button type="warning" size="small" :disabled="selectedScheduledIds.length === 0" @click="handleBatchCancelSchedule">
            撤销到草稿{{ selectedScheduledIds.length > 0 ? ` (${selectedScheduledIds.length})` : '' }}
          </el-button>
        </div>
      </div>
      <div v-loading="scheduledLoading">
        <el-table :data="scheduledPosts" stripe max-height="400px" v-if="scheduledPosts.length" row-key="id"
          @selection-change="onScheduledSelectionChange">
          <el-table-column type="selection" width="40" />
          <el-table-column label="序号" width="55" align="center">
            <template #default="{ $index }">{{ (scheduledPagination.pageNum - 1) * scheduledPagination.pageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="标题" min-width="180">
            <template #default="{ row }">
              <el-tooltip :content="row.title || '无标题'" placement="top" :show-after="300" :disabled="!row.title || row.title.length <= 18">
                <span class="draft-title text-ellipsis">{{ row.title || '无标题' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="定时发布" width="150">
            <template #default="{ row }">
              <span class="scheduled-time">{{ formatTime(row.scheduledTime) }}</span>
            </template>
          </el-table-column>
          <el-table-column label="创建时间" width="150">
            <template #default="{ row }">{{ formatTime(row.createTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="200" fixed="right">
            <template #default="{ row }">
              <el-tooltip v-if="isNearSchedule(row)" content="距发布不足5分钟，禁止编辑" placement="top">
                <el-button text type="info" size="small" disabled>编辑</el-button>
              </el-tooltip>
              <el-button v-else text type="primary" size="small" @click="editScheduledPost(row)">编辑</el-button>
              <el-button text type="success" size="small" @click="handlePublishOneScheduled(row)">发布</el-button>
              <el-button text type="warning" size="small" @click="handleCancelOneSchedule(row)">撤销</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="暂无定时发布任务" />
      </div>
      <div class="draft-pagination" v-if="scheduledPagination.total > scheduledPagination.pageSize">
        <el-pagination v-model:current-page="scheduledPagination.pageNum" v-model:page-size="scheduledPagination.pageSize"
          :total="scheduledPagination.total" :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next" background size="small"
          @size-change="loadScheduledPosts" @current-change="loadScheduledPosts" />
      </div>
    </el-dialog>


    <!-- 批量定时发布弹窗 -->
    <el-dialog v-model="showBatchScheduleDialog" title="批量定时发布" width="480px">
      <el-form label-width="100px">
        <el-form-item label="发布模式">
          <el-radio-group v-model="batchScheduleMode">
            <el-radio value="same">统一时间发布</el-radio>
            <el-radio value="interval" :disabled="selectedDraftIds.length <= 1">间隔依次发布</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="batchScheduleMode === 'interval' ? '开始时间' : '发布时间'">
          <el-date-picker v-model="batchScheduleTime" type="datetime" placeholder="选择发布时间"
            :disabled-date="(d: Date) => d.getTime() < Date.now() - 86400000" style="width: 100%"
            format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" />
          <div class="time-presets">
            <el-button size="small" @click="applyBatchPreset(1)">1小时后</el-button>
            <el-button size="small" @click="applyBatchPreset(2)">2小时后</el-button>
            <el-button size="small" @click="applyBatchPreset(6)">6小时后</el-button>
            <el-button size="small" @click="applyBatchPreset(24)">明天此时</el-button>
          </div>
        </el-form-item>
        <el-form-item v-if="batchScheduleMode === 'interval'" label="间隔时间">
          <div class="interval-row">
            <el-input-number v-model="batchScheduleInterval" :min="1" :max="1440" />
            <span class="interval-unit">分钟</span>
          </div>
          <div class="interval-presets">
            <el-button size="small" @click="batchScheduleInterval = 30">30分钟</el-button>
            <el-button size="small" @click="batchScheduleInterval = 60">1小时</el-button>
            <el-button size="small" @click="batchScheduleInterval = 120">2小时</el-button>
            <el-button size="small" @click="batchScheduleInterval = 360">6小时</el-button>
            <el-button size="small" @click="batchScheduleInterval = 1440">1天</el-button>
          </div>
        </el-form-item>
        <el-form-item label="发布预览">
          <div class="schedule-preview">
            <div v-for="(item, idx) in batchSchedulePreview" :key="idx" class="preview-item">
              <span class="preview-index">{{ idx + 1 }}.</span>
              <span class="preview-title">{{ item.title }}</span>
              <span class="preview-time">{{ item.time }}</span>
            </div>
            <div v-if="batchSchedulePreview.length === 0" class="preview-empty">请选择发布时间</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showBatchScheduleDialog = false">取消</el-button>
        <el-button type="primary" @click="handleConfirmBatchSchedule" :loading="saving">
          确定（{{ batchScheduleTargetIds.length }} 篇）
        </el-button>
      </template>
    </el-dialog>


    <!-- 回收站弹窗 -->
    <el-dialog v-model="showTrashBox" title="回收站" width="900px" destroy-on-close>
      <div class="draft-toolbar">
        <el-input v-model="trashKeyword" placeholder="搜索标题" clearable style="width: 200px">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <div class="draft-actions">
          <el-button type="primary" size="small" :disabled="selectedTrashIds.length === 0" @click="handleBatchRestore">
            恢复到草稿{{ selectedTrashIds.length > 0 ? ` (${selectedTrashIds.length})` : '' }}
          </el-button>
          <el-button type="danger" size="small" :disabled="selectedTrashIds.length === 0" @click="handleBatchPermanentDelete">
            彻底删除{{ selectedTrashIds.length > 0 ? ` (${selectedTrashIds.length})` : '' }}
          </el-button>
          <el-button type="danger" size="small" plain @click="handleClearTrash" :disabled="trashPosts.length === 0">
            清空回收站
          </el-button>
        </div>
      </div>
      <div v-loading="trashLoading">
        <el-table :data="trashPosts" stripe max-height="400px" v-if="trashPosts.length" row-key="id"
          @selection-change="onTrashSelectionChange">
          <el-table-column type="selection" width="40" />
          <el-table-column label="序号" width="55" align="center">
            <template #default="{ $index }">{{ (trashPagination.pageNum - 1) * trashPagination.pageSize + $index + 1 }}</template>
          </el-table-column>
          <el-table-column label="标题" min-width="200">
            <template #default="{ row }">
              <el-tooltip :content="row.title || '无标题'" placement="top" :show-after="300" :disabled="!row.title || row.title.length <= 20">
                <span class="text-ellipsis">{{ row.title || '无标题' }}</span>
              </el-tooltip>
            </template>
          </el-table-column>
          <el-table-column label="原状态" width="90" align="center">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 'published' ? 'success' : row.status === 'scheduled' ? 'warning' : 'info'">
                {{ row.status === 'published' ? '已发布' : row.status === 'scheduled' ? '定时' : '草稿' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="删除时间" width="150">
            <template #default="{ row }">{{ formatTime(row.updateTime) }}</template>
          </el-table-column>
          <el-table-column label="操作" width="150" fixed="right">
            <template #default="{ row }">
              <el-button text type="primary" size="small" @click="handleRestoreOne(row)">恢复</el-button>
              <el-button text type="danger" size="small" @click="handlePermanentDeleteOne(row)">彻底删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="回收站为空" />
      </div>
      <div class="draft-pagination" v-if="trashPagination.total > trashPagination.pageSize">
        <el-pagination v-model:current-page="trashPagination.pageNum" v-model:page-size="trashPagination.pageSize"
          :total="trashPagination.total" :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next" background size="small"
          @size-change="loadTrash" @current-change="loadTrash" />
      </div>
    </el-dialog>
  </div>
</template>


<script setup lang="ts">
import { EditPen, Box, Search, Timer, Delete, Picture, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { postApi, type PostVO } from '~/api/post'
import { categoryApi, type CategoryVO } from '~/api/category'
import { tagApi, type TagVO } from '~/api/tag'

// ========== 主列表 ==========
const loading = ref(false)
const posts = ref<PostVO[]>([])
const categories = ref<CategoryVO[]>([])
const allTags = ref<TagVO[]>([])
const filters = reactive({
  categoryId: undefined as number | undefined,
  tagId: undefined as number | undefined,
  keyword: '',
  isDisabled: undefined as boolean | undefined,
})
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

interface TreeNode extends CategoryVO { children?: TreeNode[] }
const categoryTree = computed<TreeNode[]>(() => {
  const topLevel = categories.value.filter(c => c.parentId === 0)
  return topLevel.map(top => ({
    ...top,
    children: categories.value.filter(c => c.parentId === top.id),
  }))
})

let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(() => filters.keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => { pagination.pageNum = 1; loadData() }, 300)
})

function handleSearch() { pagination.pageNum = 1; loadData() }

async function loadData() {
  loading.value = true
  try {
    const res = await postApi.page({
      pageNum: pagination.pageNum, pageSize: pagination.pageSize,
      categoryId: filters.categoryId, status: 'published',
      keyword: filters.keyword || undefined,
      isDisabled: filters.isDisabled, tagId: filters.tagId,
    })
    posts.value = res.data.records
    pagination.total = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function handleToggleTop(row: PostVO) {
  row._topLoading = true
  try {
    await postApi.toggleTop(row.id)
    row.isTop = !row.isTop
    ElMessage.success(row.isTop ? '已置顶' : '已取消置顶')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  } finally {
    row._topLoading = false
  }
}

async function handleToggleDisabled(row: PostVO) {
  row._disableLoading = true
  try {
    await postApi.toggleDisabled(row.id)
    row.isDisabled = !row.isDisabled
    ElMessage.success(row.isDisabled ? '已禁用' : '已启用')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  } finally {
    row._disableLoading = false
  }
}

async function handleDelete(row: PostVO) {
  try {
    await postApi.delete(row.id)
    ElMessage.success('删除成功')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message)
  }
}

// ========== 批量操作 ==========
const selectedIds = ref<number[]>([])

function handleSelectionChange(rows: PostVO[]) {
  selectedIds.value = rows.map(r => r.id)
}

async function handleBatchCommand(command: string) {
  const count = selectedIds.value.length
  switch (command) {
    case 'top':
    case 'unTop': {
      const isTop = command === 'top'
      const label = isTop ? '置顶' : '取消置顶'
      await ElMessageBox.confirm(`确定批量${label}选中的 ${count} 篇文章？`, '批量操作', { type: 'warning' })
      try {
        await postApi.batchSetTop(selectedIds.value, isTop)
        ElMessage.success(`批量${label}成功`)
        selectedIds.value = []
        loadData()
      } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
      break
    }
    case 'disable':
    case 'enable': {
      const isDisabled = command === 'disable'
      const label = isDisabled ? '禁用' : '启用'
      await ElMessageBox.confirm(`确定批量${label}选中的 ${count} 篇文章？`, '批量操作', { type: 'warning' })
      try {
        await postApi.batchSetDisabled(selectedIds.value, isDisabled)
        ElMessage.success(`批量${label}成功`)
        selectedIds.value = []
        loadData()
      } catch (e: unknown) { ElMessage.error((e as Error).message || '操作失败') }
      break
    }
    case 'delete': {
      await ElMessageBox.confirm(`确定批量删除选中的 ${count} 篇文章？`, '批量删除', { type: 'warning' })
      try {
        await postApi.batchDelete(selectedIds.value)
        ElMessage.success('批量删除成功')
        selectedIds.value = []
        loadData()
      } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
      break
    }
  }
}


// ========== 草稿箱 ==========
const showDraftBox = ref(false)
const drafts = ref<PostVO[]>([])
const draftLoading = ref(false)
const draftKeyword = ref('')
const selectedDraftIds = ref<number[]>([])
const draftPagination = reactive({ pageNum: 1, pageSize: 20, total: 0 })

let draftDebounce: ReturnType<typeof setTimeout> | null = null
watch(() => draftKeyword.value, () => {
  if (draftDebounce) clearTimeout(draftDebounce)
  draftDebounce = setTimeout(() => { draftPagination.pageNum = 1; loadDrafts() }, 300)
})

function onDraftSelectionChange(rows: PostVO[]) {
  selectedDraftIds.value = rows.map(r => r.id)
}

watch(showDraftBox, (val) => { if (val) { draftKeyword.value = ''; draftPagination.pageNum = 1; loadDrafts() } })

async function loadDrafts() {
  draftLoading.value = true
  try {
    const res = await postApi.page({ pageNum: draftPagination.pageNum, pageSize: draftPagination.pageSize, status: 'draft', keyword: draftKeyword.value || undefined })
    drafts.value = res.data.records
    draftPagination.total = res.data.total
    if (drafts.value.length === 0 && draftPagination.pageNum > 1) {
      draftPagination.pageNum--
      loadDrafts()
    }
  } catch (e) { console.warn('加载草稿失败', e) }
  finally { draftLoading.value = false }
}

function editDraft(row: PostVO) {
  showDraftBox.value = false
  navigateTo(`/post/create?id=${row.id}&from=drafts`)
}

async function handlePublishDraft(row: PostVO) {
  try {
    await postApi.batchPublish([row.id])
    ElMessage.success('发布成功')
    loadDrafts()
    loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '发布失败') }
}

async function handleBatchPublishDrafts() {
  await ElMessageBox.confirm(`确定发布选中的 ${selectedDraftIds.value.length} 篇草稿？`, '提示', { type: 'info' })
  try {
    await postApi.batchPublish(selectedDraftIds.value)
    ElMessage.success('批量发布成功')
    selectedDraftIds.value = []
    loadDrafts()
    loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '发布失败') }
}

async function handleBatchDeleteDrafts() {
  await ElMessageBox.confirm(`确定删除选中的 ${selectedDraftIds.value.length} 篇草稿？`, '提示', { type: 'warning' })
  try {
    await postApi.batchDelete(selectedDraftIds.value)
    ElMessage.success('批量删除成功')
    selectedDraftIds.value = []
    loadDrafts()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

async function deleteDraft(row: PostVO) {
  await ElMessageBox.confirm(`确定删除草稿「${row.title || '无标题'}」？`, '提示', { type: 'warning' })
  try {
    await postApi.delete(row.id)
    ElMessage.success('删除成功')
    loadDrafts()
  } catch (e: unknown) { ElMessage.error((e as Error).message) }
}


// ========== 定时任务队列 ==========
const showScheduledBox = ref(false)
const scheduledPosts = ref<PostVO[]>([])
const scheduledLoading = ref(false)
const selectedScheduledIds = ref<number[]>([])
const scheduledKeyword = ref('')
const scheduledPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

function onScheduledSelectionChange(rows: PostVO[]) {
  selectedScheduledIds.value = rows.map(r => r.id)
}

watch(showScheduledBox, (val) => { if (val) { scheduledKeyword.value = ''; scheduledPagination.pageNum = 1; loadScheduledPosts() } })

let scheduledDebounce: ReturnType<typeof setTimeout> | null = null
watch(() => scheduledKeyword.value, () => {
  if (scheduledDebounce) clearTimeout(scheduledDebounce)
  scheduledDebounce = setTimeout(() => { scheduledPagination.pageNum = 1; loadScheduledPosts() }, 300)
})

async function loadScheduledPosts() {
  scheduledLoading.value = true
  try {
    const res = await postApi.page({
      pageNum: scheduledPagination.pageNum,
      pageSize: scheduledPagination.pageSize,
      status: 'scheduled',
      keyword: scheduledKeyword.value || undefined,
    })
    scheduledPosts.value = res.data.records
    scheduledPagination.total = res.data.total
    if (scheduledPosts.value.length === 0 && scheduledPagination.pageNum > 1) {
      scheduledPagination.pageNum--
      loadScheduledPosts()
    }
  } catch (e) { console.warn('加载定时任务失败', e) }
  finally { scheduledLoading.value = false }
}

function isNearSchedule(row: PostVO): boolean {
  if (!row.scheduledTime) return false
  return new Date(row.scheduledTime).getTime() - Date.now() < 5 * 60 * 1000
}

function editScheduledPost(row: PostVO) {
  showScheduledBox.value = false
  navigateTo(`/post/create?id=${row.id}&from=scheduled`)
}

async function handleBatchPublishScheduled() {
  await ElMessageBox.confirm(`确定立即发布选中的 ${selectedScheduledIds.value.length} 篇文章？`, '提示', { type: 'info' })
  try {
    await postApi.batchPublish(selectedScheduledIds.value)
    ElMessage.success('发布成功')
    selectedScheduledIds.value = []
    loadScheduledPosts()
    loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '发布失败') }
}

async function handleBatchCancelSchedule() {
  await ElMessageBox.confirm(`确定撤销选中的 ${selectedScheduledIds.value.length} 篇定时发布？撤销后将回到草稿箱`, '提示', { type: 'warning' })
  try {
    await postApi.batchCancelSchedule(selectedScheduledIds.value)
    ElMessage.success('已撤销到草稿箱')
    selectedScheduledIds.value = []
    loadScheduledPosts()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '撤销失败') }
}

async function handlePublishOneScheduled(row: PostVO) {
  try {
    await postApi.batchPublish([row.id])
    ElMessage.success('发布成功')
    loadScheduledPosts()
    loadData()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '发布失败') }
}

async function handleCancelOneSchedule(row: PostVO) {
  try {
    await postApi.batchCancelSchedule([row.id])
    ElMessage.success('已撤销到草稿箱')
    loadScheduledPosts()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '撤销失败') }
}


// ========== 批量定时发布 ==========
const showBatchScheduleDialog = ref(false)
const batchScheduleMode = ref<'same' | 'interval'>('same')
const batchScheduleTime = ref<string | null>(null)
const batchScheduleInterval = ref(60)
const saving = ref(false)
const batchScheduleTargetIds = ref<number[]>([])

function formatLocalDateTime(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

function formatPreviewTime(d: Date): string {
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function openBatchSchedule() {
  if (selectedDraftIds.value.length === 0) return
  batchScheduleTargetIds.value = [...selectedDraftIds.value]
  initBatchScheduleDialog()
}

function openSingleSchedule(row: PostVO) {
  batchScheduleTargetIds.value = [row.id]
  initBatchScheduleDialog()
}

function initBatchScheduleDialog() {
  const now = new Date()
  const mins = now.getMinutes()
  const roundedMins = Math.ceil((mins + 1) / 5) * 5
  const startTime = new Date(now)
  startTime.setMinutes(roundedMins, 0, 0)
  batchScheduleTime.value = formatLocalDateTime(startTime)
  batchScheduleMode.value = 'same'
  batchScheduleInterval.value = 60
  showBatchScheduleDialog.value = true
}

function applyBatchPreset(hours: number) {
  batchScheduleTime.value = formatLocalDateTime(new Date(Date.now() + hours * 3600000))
}

const batchSchedulePreview = computed(() => {
  if (!batchScheduleTime.value || batchScheduleTargetIds.value.length === 0) return []
  const baseTime = new Date(batchScheduleTime.value)
  const targetDrafts = drafts.value.filter(d => batchScheduleTargetIds.value.includes(d.id))
  const interval = batchScheduleMode.value === 'interval' ? batchScheduleInterval.value : 0
  return targetDrafts.map((d, idx) => {
    const t = new Date(baseTime.getTime() + idx * interval * 60000)
    return { title: d.title || '无标题', time: formatPreviewTime(t) }
  })
})

async function handleConfirmBatchSchedule() {
  if (!batchScheduleTime.value) { ElMessage.warning('请选择发布时间'); return }
  if (new Date(batchScheduleTime.value).getTime() <= Date.now()) { ElMessage.warning('发布时间必须在未来'); return }
  saving.value = true
  try {
    const interval = batchScheduleMode.value === 'interval' ? batchScheduleInterval.value : undefined
    await postApi.batchSchedule(
      batchScheduleTargetIds.value,
      batchScheduleTime.value,
      interval
    )
    ElMessage.success('定时发布设置成功')
    showBatchScheduleDialog.value = false
    selectedDraftIds.value = []
    batchScheduleTargetIds.value = []
    loadDrafts()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '设置失败') }
  finally { saving.value = false }
}


// ========== 回收站 ==========
const showTrashBox = ref(false)
const trashPosts = ref<PostVO[]>([])
const trashLoading = ref(false)
const selectedTrashIds = ref<number[]>([])
const trashKeyword = ref('')
const trashPagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })

function onTrashSelectionChange(rows: PostVO[]) {
  selectedTrashIds.value = rows.map(r => r.id)
}

watch(showTrashBox, (val) => { if (val) { trashKeyword.value = ''; trashPagination.pageNum = 1; loadTrash() } })

let trashDebounce: ReturnType<typeof setTimeout> | null = null
watch(() => trashKeyword.value, () => {
  if (trashDebounce) clearTimeout(trashDebounce)
  trashDebounce = setTimeout(() => { trashPagination.pageNum = 1; loadTrash() }, 300)
})

async function loadTrash() {
  trashLoading.value = true
  try {
    const res = await postApi.trashPage({
      pageNum: trashPagination.pageNum,
      pageSize: trashPagination.pageSize,
      keyword: trashKeyword.value || undefined,
    })
    trashPosts.value = res.data.records
    trashPagination.total = res.data.total
    if (trashPosts.value.length === 0 && trashPagination.pageNum > 1) {
      trashPagination.pageNum--
      loadTrash()
    }
  } catch (e) { console.warn('加载回收站失败', e) }
  finally { trashLoading.value = false }
}

async function handleBatchRestore() {
  await ElMessageBox.confirm(`确定恢复选中的 ${selectedTrashIds.value.length} 篇文章到草稿箱？`, '提示', { type: 'info' })
  try {
    await postApi.batchRestore(selectedTrashIds.value)
    ElMessage.success('恢复成功')
    selectedTrashIds.value = []
    loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '恢复失败') }
}

async function handleBatchPermanentDelete() {
  await ElMessageBox.confirm(`确定彻底删除选中的 ${selectedTrashIds.value.length} 篇文章？此操作不可恢复！`, '警告', { type: 'error' })
  try {
    await postApi.batchPermanentDelete(selectedTrashIds.value)
    ElMessage.success('已彻底删除')
    selectedTrashIds.value = []
    loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

async function handleRestoreOne(row: PostVO) {
  try {
    await postApi.batchRestore([row.id])
    ElMessage.success('已恢复到草稿箱')
    loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '恢复失败') }
}

async function handlePermanentDeleteOne(row: PostVO) {
  await ElMessageBox.confirm(`确定彻底删除「${row.title}」？此操作不可恢复！`, '警告', { type: 'error' })
  try {
    await postApi.batchPermanentDelete([row.id])
    ElMessage.success('已彻底删除')
    loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '删除失败') }
}

async function handleClearTrash() {
  await ElMessageBox.confirm('确定清空回收站？所有文章将被永久删除，此操作不可恢复！', '警告', { type: 'error', confirmButtonText: '确定清空', cancelButtonText: '取消' })
  try {
    await postApi.clearTrash()
    ElMessage.success('回收站已清空')
    loadTrash()
  } catch (e: unknown) { ElMessage.error((e as Error).message || '清空失败') }
}

// ========== 工具函数 ==========
function formatTime(t: string) {
  if (!t) return ''
  return t.replace('T', ' ').slice(0, 16)
}

function formatViewCount(count: number): string {
  if (count >= 10000) return (count / 10000).toFixed(1).replace(/\.0$/, '') + 'w'
  if (count >= 1000) return (count / 1000).toFixed(1).replace(/\.0$/, '') + 'k'
  return String(count)
}

// ========== 生命周期 ==========
const route = useRoute()

onMounted(() => {
  // 并行加载文章列表和分类/标签数据
  loadData()
  Promise.all([categoryApi.listAll(), tagApi.listAll()]).then(([catRes, tagRes]) => {
    categories.value = catRes.data
    allTags.value = tagRes.data
  }).catch((e) => { console.warn('加载分类/标签失败', e) })

  // 从编辑器返回时，自动打开之前的弹窗
  const from = route.query.from as string | undefined
  if (from === 'drafts') showDraftBox.value = true
  else if (from === 'scheduled') showScheduledBox.value = true
})

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
  if (draftDebounce) clearTimeout(draftDebounce)
  if (scheduledDebounce) clearTimeout(scheduledDebounce)
  if (trashDebounce) clearTimeout(trashDebounce)
})
</script>


<style scoped lang="scss">
.post-list-page {

  .post-title-cell {
    display: flex;
    align-items: center;
    gap: 6px;
    overflow: hidden;
  }
  .post-title {
    cursor: pointer;
    color: var(--el-text-color-primary);
    font-weight: 500;
    transition: color 0.15s;
    &:hover { color: var(--el-color-primary); }
  }
  .text-ellipsis {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: block;
    max-width: 100%;
  }
  .tag-text {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    display: block;
  }
  .category-text { font-size: 13px; color: var(--el-text-color-regular); }
  .sub-category { color: var(--el-text-color-disabled); font-size: 12px; }
  .cover-thumb {
    display: block;
    width: 56px;
    height: 36px;
    border-radius: 8px;
    object-fit: cover;
    cursor: pointer;
    overflow: hidden;
    margin: 0 auto;
    border: 1px solid var(--el-border-color-extra-light);
    :deep(.el-image__error) {
      font-size: 10px;
      white-space: nowrap;
      padding: 0;
      background: var(--el-fill-color-light);
      color: var(--el-text-color-placeholder);
    }
  }
  .no-cover {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 56px;
    height: 36px;
    border-radius: 8px;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-placeholder);
    margin: 0 auto;
  }
  .view-count {
    font-size: 13px;
    color: var(--el-text-color-secondary);
    font-variant-numeric: tabular-nums;
  }
  .time-text {
    font-size: 12px;
    color: var(--el-text-color-disabled);
    font-variant-numeric: tabular-nums;
    letter-spacing: 0.2px;
  }
  .pagination-wrap {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
  }
  .draft-title {
    color: var(--el-text-color-primary);
    cursor: pointer;
    transition: color 0.15s;
    &:hover { color: var(--el-color-primary); }
  }
}

// ========== 扁平化表格（页面特有的排序列样式） ==========
:deep(.el-table) {
  .el-table__column-filter-trigger,
  .caret-wrapper { opacity: 0.4; transition: opacity 0.15s; }
  th:hover .caret-wrapper { opacity: 0.8; }
}

// ========== 弹窗内工具栏 ==========
.draft-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--el-border-color-extra-light);
  .draft-actions { display: flex; gap: 6px; }
}
.draft-pagination {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid var(--el-border-color-extra-light);
  display: flex;
  justify-content: flex-end;
}

// ========== 禁用行 ==========
:deep(.row-disabled) {
  background-color: var(--el-color-danger-light-9) !important;
  td { color: var(--el-text-color-disabled); }
}

// ========== Switch ==========
:deep(.el-switch) {
  --el-switch-on-color: var(--el-color-primary);
  --el-switch-off-color: var(--el-fill-color-darker);
}

.scheduled-time { color: var(--el-color-primary); font-weight: 500; font-size: 13px; }

.time-presets, .interval-presets {
  display: flex;
  gap: 6px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.interval-row {
  display: flex;
  align-items: center;
  gap: 8px;
  .interval-unit { font-size: 13px; color: var(--el-text-color-secondary); }
}

.schedule-preview {
  max-height: 200px;
  overflow-y: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 10px;
  background: var(--el-fill-color-blank);

  .preview-item {
    display: flex;
    align-items: center;
    gap: 8px;
    padding: 6px 0;
    font-size: 13px;
    &:not(:last-child) { border-bottom: 1px dashed var(--el-border-color-lighter); }
  }
  .preview-index { color: var(--el-text-color-secondary); min-width: 20px; }
  .preview-title {
    flex: 1;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    color: var(--el-text-color-primary);
  }
  .preview-time { color: var(--el-color-primary); white-space: nowrap; font-size: 12px; }
  .preview-empty { text-align: center; color: var(--el-text-color-placeholder); font-size: 13px; padding: 12px 0; }
}
</style>
