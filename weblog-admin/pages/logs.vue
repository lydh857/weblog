<template>
  <div class="logs-page">
    <div class="page-header">
      <h2>日志中心</h2>
      <div class="header-actions">
        <el-button type="warning" plain :loading="isCleaning('all')" :disabled="hasCleaningTask && !isCleaning('all')" @click="handleCleanupLogs('all')">
          清理全部
        </el-button>
        <el-button type="warning" plain :loading="isCleaning('login')" :disabled="hasCleaningTask && !isCleaning('login')" @click="handleCleanupLogs('login')">
          仅登录日志
        </el-button>
        <el-button type="warning" plain :loading="isCleaning('audit')" :disabled="hasCleaningTask && !isCleaning('audit')" @click="handleCleanupLogs('audit')">
          仅审计日志
        </el-button>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="logs-tabs compact-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="登录日志" name="login">
        <div class="toolbar">
          <el-input v-model="loginFilters.email" clearable placeholder="邮箱" style="width: 220px" @keyup.enter="reloadLogin" />
          <el-input v-model="loginFilters.ip" clearable placeholder="IP" style="width: 150px" @keyup.enter="reloadLogin" />
          <el-select v-model="loginFilters.loginType" clearable placeholder="登录类型" style="width: 140px">
            <el-option label="用户端" value="user" />
            <el-option label="管理端" value="admin" />
          </el-select>
          <el-select v-model="loginFilters.result" clearable placeholder="结果" style="width: 120px">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
          </el-select>
        </div>

        <el-table v-loading="loginLoading" :data="loginRows" stripe :height="tableHeight">
          <el-table-column prop="createTime" label="时间" min-width="160" />
          <el-table-column prop="email" label="邮箱" min-width="180" />
          <el-table-column label="类型" width="120">
            <template #default="{ row }">
              <el-tag size="small" type="info">{{ formatLoginType(row.loginType) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="结果" width="100">
            <template #default="{ row }">
              <el-tag :type="isLoginSuccess(row.result) ? 'success' : 'danger'" size="small">
                {{ isLoginSuccess(row.result) ? '成功' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="ip" label="IP" width="140" />
          <el-table-column prop="userAgent" label="UA" min-width="260" show-overflow-tooltip />
          <el-table-column prop="failReason" label="失败原因" min-width="220" show-overflow-tooltip />
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="loginPager.pageNum"
            v-model:page-size="loginPager.pageSize"
            :total="loginPager.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            size="small"
            @size-change="loadLoginLogs"
            @current-change="loadLoginLogs"
          />
        </div>
      </el-tab-pane>

      <el-tab-pane label="审计日志" name="audit">
        <div class="toolbar">
          <el-select v-model="auditFilters.operation" clearable placeholder="操作" style="width: 160px">
            <el-option label="登录" value="LOGIN" />
            <el-option label="登出" value="LOGOUT" />
            <el-option label="注册" value="REGISTER" />
            <el-option label="创建" value="CREATE" />
            <el-option label="更新" value="UPDATE" />
            <el-option label="删除" value="DELETE" />
            <el-option label="清理" value="CLEANUP" />
          </el-select>
          <el-select v-model="auditFilters.module" clearable filterable placeholder="模块" style="width: 160px">
            <el-option v-for="item in auditModuleOptions" :key="item" :label="item" :value="item" />
          </el-select>
          <el-input v-model="auditFilters.username" clearable placeholder="用户名" style="width: 140px" @keyup.enter="reloadAudit" />
          <el-input v-model="auditFilters.ipAddress" clearable placeholder="IP" style="width: 140px" @keyup.enter="reloadAudit" />
        </div>

        <el-table v-loading="auditLoading" :data="auditRows" stripe :height="tableHeight">
          <el-table-column prop="createTime" label="时间" min-width="160" />
          <el-table-column label="用户" width="160">
            <template #default="{ row }">
              <el-space size="4">
                <span>{{ formatAuditUsername(row) }}</span>
                <el-tag v-if="isAdminAudit(row)" size="small" type="warning">管理员</el-tag>
              </el-space>
            </template>
          </el-table-column>
          <el-table-column prop="module" label="模块" width="130" />
          <el-table-column label="操作" min-width="160" show-overflow-tooltip>
            <template #default="{ row }">
              {{ formatAuditOperation(row.operation) }}
            </template>
          </el-table-column>
          <el-table-column prop="requestMethod" label="方法" width="90" />
          <el-table-column prop="requestUrl" label="请求地址" min-width="220" show-overflow-tooltip />
          <el-table-column prop="responseCode" label="状态码" width="90" />
          <el-table-column prop="executionTime" label="耗时(ms)" width="100" />
          <el-table-column prop="ipAddress" label="IP" width="140" />
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            v-model:current-page="auditPager.pageNum"
            v-model:page-size="auditPager.pageSize"
            :total="auditPager.total"
            :page-sizes="[10, 20, 50]"
            layout="total, sizes, prev, pager, next"
            background
            size="small"
            @size-change="loadAuditLogs"
            @current-change="loadAuditLogs"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { logsApi, type AuditLogVO, type LoginLogVO } from '~/api/system/logs'
import { systemConfigApi } from '~/api/system/system-config'
import type { LocationQueryValue } from 'vue-router'

const route = useRoute()
const activeTab = ref<'login' | 'audit'>('login')
const tableHeight = useAdminTableHeight()

const loginLoading = ref(false)
const loginRows = ref<LoginLogVO[]>([])
const loginPager = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const loginFilters = reactive({ email: '', ip: '', loginType: '', result: '' })

const auditLoading = ref(false)
const auditRows = ref<AuditLogVO[]>([])
const auditPager = reactive({ pageNum: 1, pageSize: 20, total: 0 })
const auditFilters = reactive({ operation: '', module: '', username: '', ipAddress: '' })

type CleanupTarget = 'all' | 'login' | 'audit'

const cleaningTarget = ref<CleanupTarget | null>(null)
const hasCleaningTask = computed(() => cleaningTarget.value !== null)

const auditOperationLabelMap: Record<string, string> = {
  LOGIN: '登录',
  LOGOUT: '登出',
  REGISTER: '注册',
  CREATE: '创建',
  UPDATE: '更新',
  DELETE: '删除',
  CLEANUP: '清理',
  REVOKE_TOKEN: '撤销 Token',
  REVOKE_ALL_TOKENS: '撤销全部 Token',
  LOGIN_FAIL: '登录失败',
  LOGIN_BLOCKED: '登录拦截',
  IP_BLOCK_HIT: '黑名单命中',
  BLOCK_IP: '封禁IP',
  UNBLOCK_IP: '解除IP封禁',
  AUTO_BLOCK_IP: '自动封禁IP',
}

const auditModuleOptions = [
  '系统配置',
  '管理端认证',
  '认证安全',
  '安全防护',
  '安全处置',
  '用户管理',
  '文章管理',
  '分类管理',
  '标签管理',
  '评论管理',
  '媒体管理',
  '文件管理',
  '友链管理',
  '公告管理',
  '广告管理',
  '轮播管理',
  '专题管理',
  '认证',
  '个人中心',
  '个人信息审核',
  'AI配置',
  'AI提示词',
]

function normalizeText(value: unknown): string {
  if (typeof value !== 'string') return ''
  return value.trim()
}

function normalizeFilterValue(value: string): string | undefined {
  const trimmed = normalizeText(value)
  return trimmed ? trimmed : undefined
}

function isCancelAction(error: unknown): boolean {
  return error === 'cancel' || error === 'close'
}

function isCleaning(target: CleanupTarget): boolean {
  return cleaningTarget.value === target
}

function isLoginSuccess(result: string): boolean {
  return result === 'success'
}

function formatLoginType(loginType: string): string {
  if (loginType === 'admin') return '管理端'
  if (loginType === 'user') return '用户端'
  return loginType || '-'
}

function formatAuditOperation(operation: string): string {
  return auditOperationLabelMap[operation] || operation || '-'
}

function formatAuditUsername(row: AuditLogVO): string {
  const username = normalizeText(row.username)
  if (username) return username
  if (row.userId !== null && row.userId !== undefined) return `UID:${row.userId}`
  return '匿名'
}

function isAdminAudit(row: AuditLogVO): boolean {
  if (typeof row.adminActor === 'boolean') return row.adminActor
  return row.requestUrl?.startsWith('/api/admin/') || row.module?.includes('管理端')
}

function debounce<T extends (...args: never[]) => void>(fn: T, delay = 300) {
  let timer: ReturnType<typeof setTimeout> | null = null
  return (...args: Parameters<T>) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => fn(...args), delay)
  }
}

async function loadLoginLogs() {
  loginLoading.value = true
  try {
    const res = await logsApi.getLoginLogs({
      pageNum: loginPager.pageNum,
      pageSize: loginPager.pageSize,
      email: normalizeFilterValue(loginFilters.email),
      ip: normalizeFilterValue(loginFilters.ip),
      loginType: normalizeFilterValue(loginFilters.loginType),
      result: normalizeFilterValue(loginFilters.result),
    })
    loginRows.value = res.data.records || []
    loginPager.total = res.data.total || 0
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载登录日志失败')
  } finally {
    loginLoading.value = false
  }
}

async function loadAuditLogs() {
  auditLoading.value = true
  try {
    const res = await logsApi.getAuditLogs({
      pageNum: auditPager.pageNum,
      pageSize: auditPager.pageSize,
      operation: normalizeFilterValue(auditFilters.operation),
      module: normalizeFilterValue(auditFilters.module),
      username: normalizeFilterValue(auditFilters.username),
      ipAddress: normalizeFilterValue(auditFilters.ipAddress),
    })
    auditRows.value = res.data.records || []
    auditPager.total = res.data.total || 0
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载审计日志失败')
  } finally {
    auditLoading.value = false
  }
}

function reloadLogin() {
  loginPager.pageNum = 1
  loadLoginLogs()
}

function reloadAudit() {
  auditPager.pageNum = 1
  loadAuditLogs()
}

function handleTabChange(name: string | number) {
  if (name === 'audit' && !auditLoading.value) loadAuditLogs()
  if (name === 'login' && !loginLoading.value) loadLoginLogs()
}

function cleanupActionText(target: CleanupTarget): string {
  if (target === 'login') return '登录日志'
  if (target === 'audit') return '审计日志'
  return '安全日志'
}

function cleanupConfirmContent(target: CleanupTarget): string {
  if (target === 'login') {
    return '将按系统配置保留天数清理超期登录日志，可能会归档到归档表，是否继续？'
  }
  if (target === 'audit') {
    return '将按系统配置保留天数清理超期审计日志，可能会归档到归档表，是否继续？'
  }
  return '将按照系统配置保留天数清理超期登录日志和审计日志，可能会归档到归档表，是否继续？'
}

async function handleCleanupLogs(target: CleanupTarget) {
  try {
    await ElMessageBox.confirm(cleanupConfirmContent(target), `清理${cleanupActionText(target)}`, {
      type: 'warning',
      confirmButtonText: '立即清理',
      cancelButtonText: '取消',
    })
  } catch (error: unknown) {
    if (isCancelAction(error)) return
    ElMessage.error('清理确认失败')
    return
  }

  cleaningTarget.value = target
  try {
    if (target === 'all') {
      const res = await systemConfigApi.cleanupSecurityLogs()
      const { loginDeleted, auditDeleted } = res.data
      ElMessage.success(`日志清理完成：登录日志 ${loginDeleted} 条，审计日志 ${auditDeleted} 条`)
      loadLoginLogs()
      loadAuditLogs()
      return
    }

    if (target === 'login') {
      const res = await systemConfigApi.cleanupLoginLogs()
      ElMessage.success(`登录日志清理完成：${res.data.deleted} 条`)
      loadLoginLogs()
      return
    }

    const res = await systemConfigApi.cleanupAuditLogs()
    ElMessage.success(`审计日志清理完成：${res.data.deleted} 条`)
    loadAuditLogs()
  } catch (error: unknown) {
    ElMessage.error((error as Error).message || '清理日志失败')
  } finally {
    cleaningTarget.value = null
  }
}

function toQueryString(value: LocationQueryValue | LocationQueryValue[] | undefined): string {
  if (Array.isArray(value)) return value[0] ?? ''
  return value ?? ''
}

function applyRouteFilters() {
  const tab = toQueryString(route.query.tab)
  if (tab === 'audit') {
    activeTab.value = 'audit'
  } else if (tab === 'login') {
    activeTab.value = 'login'
  } else if (tab === 'blacklist') {
    navigateTo('/risk-control')
  }

  const ip = normalizeText(toQueryString(route.query.ip))
  if (ip) loginFilters.ip = ip

  const email = normalizeText(toQueryString(route.query.email))
  if (email) loginFilters.email = email
}

const triggerLoginAutoSearch = debounce(() => {
  loginPager.pageNum = 1
  loadLoginLogs()
}, 260)

const triggerAuditAutoSearch = debounce(() => {
  auditPager.pageNum = 1
  loadAuditLogs()
}, 260)

watch(() => [loginFilters.email, loginFilters.ip, loginFilters.loginType, loginFilters.result], () => {
  if (activeTab.value === 'login') triggerLoginAutoSearch()
})

watch(() => [auditFilters.operation, auditFilters.module, auditFilters.username, auditFilters.ipAddress], () => {
  if (activeTab.value === 'audit') triggerAuditAutoSearch()
})

onMounted(() => {
  applyRouteFilters()
  if (activeTab.value === 'audit') {
    loadAuditLogs()
  } else {
    loadLoginLogs()
  }
})
</script>

<style scoped lang="scss">
.logs-page {
  padding: 0;
}

.page-header h2 {
  font-size: 1rem;
  font-weight: 600;
  letter-spacing: 0.2px;
}

.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
  flex: 0 0 auto;
}

.logs-tabs {
  min-height: 0;

  :deep(.el-tabs__content) {
    min-height: 0;
  }

  :deep(.el-tab-pane > .el-table) {
    flex: 1 1 auto;
    min-height: 0;
  }
}

.pagination-wrap {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
  flex: 0 0 auto;
}
</style>
