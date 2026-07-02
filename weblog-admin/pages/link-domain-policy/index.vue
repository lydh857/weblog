<template>
  <div class="link-domain-policy-page">
    <div class="page-header">
      <h2>域名治理</h2>
      <div class="filters">
        <el-select v-model="statusFilter" placeholder="状态" clearable style="width: 140px" @change="handleFilterChange">
          <el-option label="待审核" value="pending" />
          <el-option label="已信任" value="trusted" />
          <el-option label="已封禁" value="blocked" />
        </el-select>
        <el-input
          v-model="keyword"
          placeholder="搜索域名"
          clearable
          style="width: 220px"
          @keyup.enter="loadData"
          @clear="handleFilterChange"
        >
          <template #append>
            <el-button @click="loadData">搜索</el-button>
          </template>
        </el-input>
      </div>
    </div>

    <el-table :data="records" v-loading="loading" stripe :height="tableHeight">
      <el-table-column type="index" label="#" width="60" align="center" />
      <el-table-column label="域名" prop="domain" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="来源" width="100" align="center">
        <template #default="{ row }">{{ sourceLabel(row.source) }}</template>
      </el-table-column>
      <el-table-column label="审核人" prop="reviewer" width="130" show-overflow-tooltip />
      <el-table-column label="策略备注" prop="reviewReason" min-width="220" show-overflow-tooltip />
      <el-table-column label="过期时间" width="170">
        <template #default="{ row }">{{ fmt(row.expireAt) || '-' }}</template>
      </el-table-column>
      <el-table-column label="更新时间" width="170">
        <template #default="{ row }">{{ fmt(row.updateTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status !== 'trusted'" text type="success" size="small" @click="handleTrust(row.id)">信任</el-button>
          <el-button v-if="row.status !== 'blocked'" text type="danger" size="small" @click="handleBlock(row.id)">封禁</el-button>
          <el-button v-if="row.status === 'blocked'" text type="warning" size="small" @click="handleUnblock(row.id)">解封</el-button>
          <el-button text type="primary" size="small" @click="handleUpdateNote(row)">备注</el-button>
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
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { linkDomainPolicyApi, type LinkDomainPolicyStatus, type LinkDomainPolicyVO } from '~/api/content/linkDomainPolicy'

const loading = ref(false)
const records = ref<LinkDomainPolicyVO[]>([])
const pageNum = ref(1)
const pageSize = ref(20)
const total = ref(0)
const statusFilter = ref<string>('')
const keyword = ref('')
const tableHeight = useAdminTableHeight()

function statusLabel(status: LinkDomainPolicyStatus) {
  return {
    pending: '待审核',
    trusted: '已信任',
    blocked: '已封禁',
  }[status]
}

function statusType(status: LinkDomainPolicyStatus) {
  return {
    pending: 'warning',
    trusted: 'success',
    blocked: 'danger',
  }[status] as 'warning' | 'success' | 'danger'
}

function sourceLabel(source: string) {
  return {
    auto: '自动',
    seed: '种子',
    manual: '人工',
  }[source] || source
}

function fmt(value?: string | null) {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 16)
}

function handleFilterChange() {
  pageNum.value = 1
  loadData()
}

function handleSizeChange() {
  pageNum.value = 1
  loadData()
}

async function loadData() {
  loading.value = true
  try {
    const res = await linkDomainPolicyApi.page({
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      status: statusFilter.value || undefined,
      keyword: keyword.value.trim() || undefined,
    })
    records.value = res.data.records
    total.value = res.data.total
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function askReviewReason(title: string) {
  try {
    const result = await ElMessageBox.prompt('可填写审核备注（选填）', title, {
      inputType: 'textarea',
      inputPlaceholder: '例如：业务合作域名、误报申诉通过',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPattern: /^.{0,500}$/,
      inputErrorMessage: '备注不能超过 500 字',
    })
    return result.value?.trim() || undefined
  } catch {
    return null
  }
}

async function handleTrust(id: number) {
  const reviewReason = await askReviewReason('通过域名')
  if (reviewReason === null) return
  try {
    await linkDomainPolicyApi.trust(id, { reviewReason })
    ElMessage.success('域名已设为信任')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleBlock(id: number) {
  const reviewReason = await askReviewReason('封禁域名')
  if (reviewReason === null) return
  try {
    await linkDomainPolicyApi.block(id, { reviewReason })
    ElMessage.success('域名已封禁')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleUnblock(id: number) {
  const reviewReason = await askReviewReason('解除封禁')
  if (reviewReason === null) return
  try {
    await linkDomainPolicyApi.unblock(id, { reviewReason })
    ElMessage.success('域名已回到待审核')
    loadData()
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '操作失败')
  }
}

async function handleUpdateNote(row: LinkDomainPolicyVO) {
  try {
    const result = await ElMessageBox.prompt('更新策略备注（可为空）', '更新备注', {
      inputType: 'textarea',
      inputValue: row.reviewReason || '',
      confirmButtonText: '保存',
      cancelButtonText: '取消',
      inputPattern: /^.{0,500}$/,
      inputErrorMessage: '备注不能超过 500 字',
    })
    await linkDomainPolicyApi.updateNote(row.id, {
      status: row.status,
      reviewReason: result.value.trim() || undefined,
    })
    ElMessage.success('备注已更新')
    loadData()
  } catch {
    // 用户取消输入时不提示
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped lang="scss">
.link-domain-policy-page {
  .page-header {
    margin-bottom: 14px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;

    h2 {
      margin: 0;
      font-size: 20px;
      font-weight: 600;
    }
  }

  .filters {
    display: flex;
    align-items: center;
    gap: 10px;
  }

  .pagination-wrap {
    margin-top: 12px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>
