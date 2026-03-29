<template>
  <div class="config-page">
    <div class="page-header">
      <h2>系统配置</h2>
    </div>

    <div v-loading="loading" class="config-grid">
      <!-- 站点信息 -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M2 12h20M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10z"/></svg>
            <span class="card-title">站点信息</span>
          </div>
        </template>
        <el-form label-width="140px" label-position="right">
          <el-form-item label="站点名称">
            <el-input v-model="form.site_name" placeholder="请输入站点名称" />
          </el-form-item>
          <el-form-item label="站点描述">
            <el-input v-model="form.site_description" type="textarea" :rows="2" placeholder="请输入站点描述" />
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 安全设置 -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
            <span class="card-title">安全设置</span>
          </div>
        </template>
        <el-form label-width="140px" label-position="right">
          <el-form-item label="最大失败次数">
            <el-input-number v-model.number="form.login_max_attempts" :min="1" :max="20" />
            <span class="form-tip">超过此次数账号将被锁定</span>
          </el-form-item>
          <el-form-item label="锁定时间（分钟）">
            <el-input-number v-model.number="form.login_lock_minutes" :min="1" :max="1440" />
          </el-form-item>
          <el-form-item label="限流（次/分钟）">
            <el-input-number v-model.number="form.login_rate_limit" :min="1" :max="60" />
          </el-form-item>
          <el-form-item label="安全日志">
            <el-button type="warning" plain :loading="cleaningSecurityLogs" @click="handleCleanupSecurityLogs">
              立即清理安全日志
            </el-button>
            <span class="form-tip">手动清理超出保留期的登录日志和审计日志</span>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 阅读设置 -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z"/><path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z"/></svg>
            <span class="card-title">阅读设置</span>
          </div>
        </template>
        <el-form label-width="140px" label-position="right">
          <el-form-item label="每日阅读限制">
            <el-input-number v-model.number="form.daily_read_limit" :min="0" :max="100" />
            <span class="form-tip">未登录用户，0 表示不限制</span>
          </el-form-item>
          <el-form-item label="排行榜间隔（分钟）">
            <el-input-number v-model.number="form.ranking_update_interval" :min="5" :max="1440" />
          </el-form-item>
          <el-form-item label="排行榜">
            <el-button type="primary" plain :loading="refreshingRanking" @click="handleRefreshRanking">
              立即刷新排行榜
            </el-button>
            <span class="form-tip">手动触发排行榜重新计算</span>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 上传设置 -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
            <span class="card-title">上传设置</span>
          </div>
        </template>
        <el-form label-width="140px" label-position="right">
          <el-form-item label="最大文件大小（MB）">
            <el-input-number v-model.number="form.upload_max_size_mb" :min="1" :max="50" />
          </el-form-item>
          <el-form-item label="允许的文件类型">
            <el-input v-model="form.upload_allowed_types" placeholder="jpg,jpeg,png,webp,gif" />
            <span class="form-tip">多个类型用英文逗号分隔</span>
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 评论设置 -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>
            <span class="card-title">评论设置</span>
          </div>
        </template>
        <el-form label-width="140px" label-position="right">
          <el-form-item label="评论审核">
            <div class="switch-row">
              <el-switch v-model="form.comment_audit_enabled" active-value="true" inactive-value="false" />
              <span class="form-tip">开启后新评论需管理员审核通过才会显示</span>
            </div>
          </el-form-item>
          <el-form-item label="评论最大长度">
            <el-input-number v-model.number="form.comment_max_length" :min="50" :max="5000" />
          </el-form-item>
          <el-form-item label="限流（次/分钟）">
            <el-input-number v-model.number="form.comment_rate_limit" :min="1" :max="60" />
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 导入设置 -->
      <el-card shadow="never" class="config-card">
        <template #header>
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="12" y1="18" x2="12" y2="12"/><line x1="9" y1="15" x2="12" y2="12"/><line x1="15" y1="15" x2="12" y2="12"/></svg>
            <span class="card-title">导入设置</span>
          </div>
        </template>
        <el-form label-width="140px" label-position="right">
          <el-form-item label="限流（次/分钟）">
            <el-input-number v-model.number="form.import_rate_limit" :min="1" :max="60" />
          </el-form-item>
          <el-form-item label="最大并发数">
            <el-input-number v-model.number="form.import_max_concurrent" :min="1" :max="10" />
          </el-form-item>
        </el-form>
      </el-card>

      <!-- 邮件设置（跨两列） -->
      <el-card shadow="never" class="config-card config-card--wide">
        <template #header>
          <div class="card-header">
            <svg class="card-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 4h16c1.1 0 2 .9 2 2v12c0 1.1-.9 2-2 2H4c-1.1 0-2-.9-2-2V6c0-1.1.9-2 2-2z"/><polyline points="22,6 12,13 2,6"/></svg>
            <span class="card-title">邮件设置</span>
          </div>
        </template>
        <div class="mail-grid">
          <el-form label-width="160px" label-position="right">
            <el-form-item label="SMTP服务器">
              <el-input v-model="form.mail_smtp_host" placeholder="如 smtp.qq.com" />
            </el-form-item>
            <el-form-item label="SMTP端口">
              <el-input-number v-model.number="form.mail_smtp_port" :min="1" :max="65535" />
            </el-form-item>
            <el-form-item label="发件邮箱">
              <el-input v-model="form.mail_username" placeholder="如 noreply@example.com" />
            </el-form-item>
            <el-form-item label="邮箱授权码">
              <el-input v-model="form.mail_password" type="password" show-password placeholder="SMTP授权码" />
            </el-form-item>
          </el-form>
          <el-form label-width="160px" label-position="right">
            <el-form-item label="发件人名称">
              <el-input v-model="form.mail_from_name" placeholder="如 我的博客" />
            </el-form-item>
            <el-form-item label="启用SSL">
              <el-switch v-model="form.mail_ssl_enabled" active-value="true" inactive-value="false" />
            </el-form-item>
            <el-form-item label="验证码有效期（分钟）">
              <el-input-number v-model.number="form.mail_code_expire_minutes" :min="1" :max="30" />
            </el-form-item>
            <el-form-item label="发送冷却时间（秒）">
              <el-input-number v-model.number="form.mail_code_cooldown_seconds" :min="30" :max="300" />
            </el-form-item>
          </el-form>
        </div>
      </el-card>
    </div>

    <!-- 悬浮保存按钮 -->
    <el-button class="fab-save" type="primary" :loading="saving" @click="handleSave">保存</el-button>
  </div>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { systemConfigApi, type SystemConfigVO } from '~/api/system/system-config'

const loading = ref(false)
const saving = ref(false)
const refreshingRanking = ref(false)
const cleaningSecurityLogs = ref(false)

// 数值类型的配置 key
const numberKeys = new Set([
  'daily_read_limit', 'login_max_attempts', 'login_lock_minutes',
  'login_rate_limit', 'upload_max_size_mb', 'comment_max_length',
  'comment_rate_limit', 'ranking_update_interval',
  'import_rate_limit', 'import_max_concurrent',
  'mail_smtp_port', 'mail_code_expire_minutes', 'mail_code_cooldown_seconds',
])

// 开关类型的配置 key（值为 "true"/"false"）
const switchKeys = new Set([
  'mail_ssl_enabled', 'comment_audit_enabled',
])

const form = reactive<Record<string, string | number>>({})

async function loadData() {
  loading.value = true
  try {
    const res = await systemConfigApi.list()
    const list: SystemConfigVO[] = res.data
    for (const item of list) {
      if (numberKeys.has(item.configKey)) {
        form[item.configKey] = Number(item.configValue) || 0
      } else {
        form[item.configKey] = item.configValue || ''
      }
    }
    // 评论审核开关默认开启（如果后端还没有这个配置项）
    if (form.comment_audit_enabled === undefined || form.comment_audit_enabled === '') {
      form.comment_audit_enabled = 'true'
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载配置失败')
  } finally {
    loading.value = false
  }
}

async function handleSave() {
  saving.value = true
  try {
    const configs: Record<string, string> = {}
    for (const [key, val] of Object.entries(form)) {
      configs[key] = String(val)
    }
    await systemConfigApi.batchUpdate(configs)
    ElMessage.success('配置保存成功')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleRefreshRanking() {
  refreshingRanking.value = true
  try {
    await systemConfigApi.refreshRanking()
    ElMessage.success('排行榜刷新成功')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '刷新排行榜失败')
  } finally {
    refreshingRanking.value = false
  }
}

async function handleCleanupSecurityLogs() {
  cleaningSecurityLogs.value = true
  try {
    const res = await systemConfigApi.cleanupSecurityLogs()
    const { loginDeleted, auditDeleted } = res.data
    ElMessage.success(`安全日志清理完成：登录日志 ${loginDeleted} 条，审计日志 ${auditDeleted} 条`)
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '清理安全日志失败')
  } finally {
    cleaningSecurityLogs.value = false
  }
}

onMounted(loadData)
</script>

<style scoped lang="scss">
.config-page {
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 16px;
    padding: 0 20px;
    height: 62px;
    background: var(--el-bg-color);
    border-radius: 10px;
    border: 1px solid var(--el-border-color-lighter);
    flex-wrap: wrap;
    gap: 12px;
    h2 {
      font-size: 1.05rem;
      font-weight: 600;
      color: var(--el-text-color-primary);
      margin: 0;
      white-space: nowrap;
      letter-spacing: 0.3px;
    }
  }
}

// ========== 两列网格 ==========
.config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
.config-card--wide {
  grid-column: 1 / -1;
}
@media (max-width: 1200px) {
  .config-grid {
    grid-template-columns: 1fr;
  }
}

// ========== 卡片头部 ==========
.card-header {
  display: flex;
  align-items: center;
  gap: 8px;
}
.card-icon {
  width: 18px;
  height: 18px;
  color: var(--el-color-primary);
  flex-shrink: 0;
}
.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

// ========== 表单 ==========
.config-card {
  .el-form-item {
    margin-bottom: 18px;
    &:last-child { margin-bottom: 0; }
  }
}
.form-tip {
  margin-left: 12px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.switch-row {
  display: flex;
  align-items: center;
}

// ========== 邮件双列 ==========
.mail-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0 24px;
  :deep(.el-form-item__label) {
    white-space: nowrap;
  }
}
@media (max-width: 900px) {
  .mail-grid {
    grid-template-columns: 1fr;
  }
}

// ========== Card ==========
:deep(.el-card) {
  border-radius: 10px;
  border: 1px solid var(--el-border-color-lighter);
  .el-card__header {
    padding: 14px 20px;
    border-bottom: 1px solid var(--el-border-color-extra-light);
  }
  .el-card__body {
    padding: 20px;
  }
}

// ========== Switch ==========
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

// ========== 悬浮保存按钮 ==========
.fab-save {
  position: fixed;
  right: 32px;
  bottom: 32px;
  z-index: 100;
  box-shadow: 0 4px 12px rgba(91, 141, 239, 0.4);
  &:hover {
    box-shadow: 0 6px 16px rgba(91, 141, 239, 0.5);
    transform: translateY(-1px);
  }
  transition: box-shadow 0.2s, transform 0.2s;
}
</style>
