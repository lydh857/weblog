<template>
  <div class="profile-review-page">
    <div class="page-header">
      <h2>个人信息审核</h2>
      <div class="filter-bar">
        <el-input v-model="keyword" placeholder="搜索昵称/邮箱" clearable style="width: 240px" @clear="handleSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe height="560">
      <el-table-column type="index" label="#" width="50" align="center" />

      <el-table-column label="用户" min-width="230">
        <template #default="{ row }">
          <div class="user-cell">
            <el-avatar :size="36" :src="row.currentAvatar || undefined">{{ row.currentNickname?.[0] || 'U' }}</el-avatar>
            <div class="user-info">
              <span class="nickname">{{ row.currentNickname || '未命名用户' }}</span>
              <span class="email">{{ row.email || '-' }}</span>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="待审信息" min-width="320">
        <template #default="{ row }">
          <div class="pending-cell">
            <el-image
              :src="row.pendingAvatar || ''"
              fit="cover"
              class="pending-avatar"
              :preview-src-list="row.pendingAvatar ? [row.pendingAvatar] : []"
              preview-teleported
            >
              <template #error>
                <div class="image-fallback">无图</div>
              </template>
            </el-image>
            <div class="pending-meta">
              <p class="meta-line"><span>昵称：</span>{{ row.pendingNickname || '-' }}</p>
              <p class="meta-line bio"><span>简介：</span>{{ row.pendingBio || '-' }}</p>
            </div>
          </div>
        </template>
      </el-table-column>

      <el-table-column label="提交时间" width="170">
        <template #default="{ row }">{{ formatTime(row.submitTime) }}</template>
      </el-table-column>

      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button text type="primary" size="small" @click="openDetail(row)">详情</el-button>
          <el-button text type="danger" size="small" @click="handleReject(row)">拒绝</el-button>
          <el-button text type="success" size="small" @click="handleApprove(row)">通过</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        background
        size="small"
        @current-change="loadData"
        @size-change="handleSizeChange"
      />
    </div>

    <el-dialog v-model="detailVisible" title="个人信息审核详情" width="760px" destroy-on-close>
      <template v-if="detailRecord">
        <div class="detail-grid">
          <section class="detail-card">
            <h4>当前生效信息</h4>
            <div class="detail-avatar">
              <el-image v-if="detailRecord.currentAvatar" :src="detailRecord.currentAvatar" fit="cover" class="detail-image" />
              <div v-else class="detail-avatar-fallback">无头像</div>
            </div>
            <p><span>昵称：</span>{{ detailRecord.currentNickname || '-' }}</p>
            <p><span>简介：</span>{{ detailRecord.currentBio || '-' }}</p>
          </section>

          <section class="detail-card pending">
            <h4>待审核信息</h4>
            <div class="detail-avatar">
              <el-image v-if="detailRecord.pendingAvatar" :src="detailRecord.pendingAvatar" fit="cover" class="detail-image" :preview-src-list="[detailRecord.pendingAvatar]" preview-teleported />
              <div v-else class="detail-avatar-fallback">无头像</div>
            </div>
            <p><span>昵称：</span>{{ detailRecord.pendingNickname || '-' }}</p>
            <p><span>简介：</span>{{ detailRecord.pendingBio || '-' }}</p>
          </section>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { profileReviewApi, type ProfileReviewVO } from '~/api/profileReview'

const loading = ref(false)
const records = ref<ProfileReviewVO[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)
const keyword = ref('')

const detailVisible = ref(false)
const detailRecord = ref<ProfileReviewVO | null>(null)

async function loadData() {
  loading.value = true
  try {
    const res = await profileReviewApi.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      keyword: keyword.value || undefined,
    })
    records.value = res.data.records
    total.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  loadData()
}

function handleSizeChange() {
  pageNum.value = 1
  loadData()
}

function openDetail(row: ProfileReviewVO) {
  detailRecord.value = row
  detailVisible.value = true
}

async function handleApprove(row: ProfileReviewVO) {
  await ElMessageBox.confirm(`确认通过「${row.currentNickname}」的个人信息审核？`, '提示', { type: 'warning' })
  try {
    await profileReviewApi.approve(row.reviewId)
    ElMessage.success('审核通过')
    if (detailRecord.value?.reviewId === row.reviewId) {
      detailVisible.value = false
      detailRecord.value = null
    }
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleReject(row: ProfileReviewVO) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt(`请输入拒绝「${row.currentNickname}」的原因`, '拒绝审核', {
      confirmButtonText: '确认拒绝',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：头像不清晰、昵称不合规、简介含敏感词',
      inputValidator: (v: string) => {
        if (!v || !v.trim()) return '拒绝原因不能为空'
        if (v.trim().length > 200) return '拒绝原因不能超过200字'
        return true
      },
    })
    reason = value.trim()
  } catch {
    return
  }

  try {
    await profileReviewApi.reject(row.reviewId, reason)
    ElMessage.success('已拒绝')
    if (detailRecord.value?.reviewId === row.reviewId) {
      detailVisible.value = false
      detailRecord.value = null
    }
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

function formatTime(t: string) {
  return t ? t.replace('T', ' ').slice(0, 16) : '-'
}

let debounceTimer: ReturnType<typeof setTimeout> | null = null
watch(keyword, () => {
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    pageNum.value = 1
    loadData()
  }, 300)
})

onMounted(loadData)

onUnmounted(() => {
  if (debounceTimer) clearTimeout(debounceTimer)
})
</script>

<style scoped lang="scss">
.profile-review-page {
  .user-cell {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .user-info {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }

  .nickname {
    font-size: 13px;
    color: var(--el-text-color-primary);
  }

  .email {
    font-size: 12px;
    color: var(--el-text-color-secondary);
  }

  .pending-cell {
    display: flex;
    align-items: flex-start;
    gap: 10px;
  }

  .pending-avatar {
    width: 64px;
    height: 64px;
    border-radius: 8px;
    overflow: hidden;
    border: 1px solid var(--el-border-color-lighter);
    flex-shrink: 0;
  }

  .image-fallback {
    width: 64px;
    height: 64px;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--el-fill-color-light);
    color: var(--el-text-color-secondary);
    font-size: 11px;
  }

  .pending-meta {
    flex: 1;
    min-width: 0;
  }

  .meta-line {
    margin: 0;
    font-size: 12px;
    line-height: 1.5;
    color: var(--el-text-color-primary);

    span {
      color: var(--el-text-color-secondary);
    }
  }

  .meta-line.bio {
    margin-top: 4px;
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
  }
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;

  @media (max-width: 860px) {
    grid-template-columns: 1fr;
  }
}

.detail-card {
  border: 1px solid var(--el-border-color);
  border-radius: 10px;
  padding: 12px;
  background: var(--el-fill-color-blank);

  &.pending {
    border-color: rgba(245, 158, 11, 0.45);
    background: rgba(254, 252, 232, 0.55);
  }

  h4 {
    margin: 0 0 10px;
    font-size: 14px;
    font-weight: 600;
  }

  p {
    margin: 8px 0 0;
    font-size: 13px;
    color: var(--el-text-color-primary);
    line-height: 1.6;

    span {
      color: var(--el-text-color-secondary);
    }
  }
}

.detail-avatar {
  margin-bottom: 6px;
}

.detail-image,
.detail-avatar-fallback {
  width: 90px;
  height: 90px;
  border-radius: 10px;
}

.detail-avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
