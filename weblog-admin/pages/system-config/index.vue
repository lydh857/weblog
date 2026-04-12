<template>
  <div class="config-page">
    <div class="page-header">
      <h2>系统配置</h2>
      <div class="header-actions">
        <el-button type="primary" plain @click="navigateTo('/rate-limit')">
          前往限流与风控
        </el-button>
      </div>
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
          <el-form-item label="登录日志保留天数">
            <el-input-number v-model.number="form.login_log_retention_days" :min="7" :max="3650" />
            <span class="form-tip">建议 30~365 天，超期日志可在日志中心手动清理</span>
          </el-form-item>
          <el-form-item label="审计日志保留天数">
            <el-input-number v-model.number="form.audit_log_retention_days" :min="7" :max="3650" />
            <span class="form-tip">建议不低于登录日志，避免审计追溯信息缺失</span>
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemConfigApi, type SystemConfigVO } from '~/api/system/system-config'

const loading = ref(false)
const saving = ref(false)
const refreshingRanking = ref(false)
const resettingRateLimit = ref(false)

const rateLimitDefaults: Record<string, string | number> = {
  login_rate_limit: 5,
  register_rate_limit: 5,
  admin_login_rate_limit: 5,
  send_code_rate_limit: 3,
  check_email_rate_limit: 10,
  forgot_password_rate_limit: 5,
  captcha_generate_rate_limit: 10,
  captcha_verify_rate_limit: 20,
  comment_rate_limit: 5,
  upload_rate_limit: 20,
  ad_apply_rate_limit: 5,
  friend_link_apply_rate_limit: 5,
  friend_link_update_rate_limit: 10,
  access_read_rate_limit: 120,
  access_unlock_rate_limit: 5,
  interaction_like_toggle_rate_limit: 60,
  interaction_like_state_rate_limit: 90,
  interaction_favorite_toggle_rate_limit: 60,
  interaction_favorite_state_rate_limit: 90,
  interaction_favorite_batch_rate_limit: 20,
  comment_delete_rate_limit: 30,
  comment_batch_delete_rate_limit: 10,
  comment_like_toggle_rate_limit: 60,
  comment_like_state_rate_limit: 90,
  ai_writing_rate_limit: 20,
  ai_chat_rate_limit: 30,
  ai_meta_rate_limit: 20,
  admin_revoke_token_rate_limit: 20,
  admin_revoke_all_tokens_rate_limit: 5,
  system_config_batch_update_rate_limit: 20,
  ranking_refresh_rate_limit: 5,
  admin_post_delete_rate_limit: 20,
  admin_post_permanent_delete_rate_limit: 10,
  admin_topic_delete_rate_limit: 20,
  admin_topic_permanent_delete_rate_limit: 10,
  admin_media_delete_rate_limit: 30,
  admin_media_cleanup_rate_limit: 5,
  admin_user_status_update_rate_limit: 20,
  admin_user_reset_password_rate_limit: 10,
  admin_ad_status_update_rate_limit: 30,
  admin_ad_delete_rate_limit: 20,
  admin_ad_permanent_delete_rate_limit: 10,
  admin_ad_apply_switch_rate_limit: 20,
  admin_ad_price_rules_rate_limit: 10,
  admin_ad_pit_update_rate_limit: 20,
  admin_friend_link_status_update_rate_limit: 30,
  admin_friend_link_delete_rate_limit: 20,
  admin_announcement_status_update_rate_limit: 30,
  admin_announcement_delete_rate_limit: 20,
  user_bind_email_rate_limit: 8,
  user_change_email_rate_limit: 8,
  user_set_password_rate_limit: 8,
  user_reset_password_rate_limit: 8,
  rate_limit_auto_block_enabled: 'false',
  rate_limit_auto_block_threshold: 20,
  rate_limit_auto_block_window_minutes: 10,
  rate_limit_auto_block_minutes: 60,
  rate_limit_auto_block_key_prefixes: 'register,sendCode,checkEmail,forgotPassword,captchaGenerate,captchaVerify,comment-create,comment-delete,comment-batch-delete,comment-like-toggle,comment-like-state,portal-upload-image,ad-apply,friend-link-apply,friend-link-update,access-read,access-unlock,interaction-like-toggle,interaction-like-state,interaction-favorite-toggle,interaction-favorite-state,interaction-favorite-batch,user-bind-email,user-change-email,user-set-password,user-reset-password,ai-chat,ai-writing,ai-meta,admin-login,admin-post-delete,admin-post-permanent-delete,admin-topic-delete,admin-topic-permanent-delete,admin-media-delete,admin-media-cleanup,admin-user-status-update,admin-user-reset-password,admin-ad-status-update,admin-ad-delete,admin-ad-permanent-delete,admin-ad-apply-switch,admin-ad-price-rules,admin-ad-pit-update,admin-friend-link-status-update,admin-friend-link-delete,admin-announcement-status-update,admin-announcement-delete',
}

// 数值类型的配置 key
const numberKeys = new Set([
  'daily_read_limit', 'login_max_attempts', 'login_lock_minutes',
  'login_rate_limit', 'register_rate_limit', 'admin_login_rate_limit',
  'upload_max_size_mb', 'comment_max_length', 'comment_rate_limit', 'ranking_update_interval',
  'send_code_rate_limit', 'check_email_rate_limit', 'forgot_password_rate_limit', 'captcha_generate_rate_limit', 'captcha_verify_rate_limit',
  'upload_rate_limit', 'ad_apply_rate_limit', 'friend_link_apply_rate_limit',
  'friend_link_update_rate_limit', 'access_read_rate_limit', 'access_unlock_rate_limit',
  'interaction_like_toggle_rate_limit', 'interaction_like_state_rate_limit',
  'interaction_favorite_toggle_rate_limit', 'interaction_favorite_state_rate_limit', 'interaction_favorite_batch_rate_limit',
  'comment_delete_rate_limit', 'comment_batch_delete_rate_limit',
  'comment_like_toggle_rate_limit', 'comment_like_state_rate_limit',
  'ai_writing_rate_limit', 'ai_chat_rate_limit', 'ai_meta_rate_limit',
  'admin_revoke_token_rate_limit', 'admin_revoke_all_tokens_rate_limit',
  'system_config_batch_update_rate_limit', 'ranking_refresh_rate_limit',
  'admin_post_delete_rate_limit', 'admin_post_permanent_delete_rate_limit',
  'admin_topic_delete_rate_limit', 'admin_topic_permanent_delete_rate_limit',
  'admin_media_delete_rate_limit', 'admin_media_cleanup_rate_limit',
  'admin_user_status_update_rate_limit', 'admin_user_reset_password_rate_limit',
  'admin_ad_status_update_rate_limit', 'admin_ad_delete_rate_limit',
  'admin_ad_permanent_delete_rate_limit', 'admin_ad_apply_switch_rate_limit',
  'admin_ad_price_rules_rate_limit', 'admin_ad_pit_update_rate_limit',
  'admin_friend_link_status_update_rate_limit', 'admin_friend_link_delete_rate_limit',
  'admin_announcement_status_update_rate_limit', 'admin_announcement_delete_rate_limit',
  'user_bind_email_rate_limit', 'user_change_email_rate_limit',
  'user_set_password_rate_limit', 'user_reset_password_rate_limit',
  'rate_limit_auto_block_threshold', 'rate_limit_auto_block_window_minutes', 'rate_limit_auto_block_minutes',
  'mail_smtp_port', 'mail_code_expire_minutes', 'mail_code_cooldown_seconds',
  'login_log_retention_days', 'audit_log_retention_days',
])

// 开关类型的配置 key（值为 "true"/"false"）
const switchKeys = new Set([
  'mail_ssl_enabled', 'comment_audit_enabled', 'rate_limit_auto_block_enabled',
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
    if (form.login_log_retention_days === undefined) {
      form.login_log_retention_days = 180
    }
    if (form.audit_log_retention_days === undefined) {
      form.audit_log_retention_days = 180
    }
    if (form.send_code_rate_limit === undefined) {
      form.send_code_rate_limit = 3
    }
    if (form.check_email_rate_limit === undefined) {
      form.check_email_rate_limit = 10
    }
    if (form.register_rate_limit === undefined) {
      form.register_rate_limit = 5
    }
    if (form.admin_login_rate_limit === undefined) {
      form.admin_login_rate_limit = 5
    }
    if (form.forgot_password_rate_limit === undefined) {
      form.forgot_password_rate_limit = 5
    }
    if (form.captcha_generate_rate_limit === undefined) {
      form.captcha_generate_rate_limit = 10
    }
    if (form.captcha_verify_rate_limit === undefined) {
      form.captcha_verify_rate_limit = 20
    }
    if (form.upload_rate_limit === undefined) {
      form.upload_rate_limit = 20
    }
    if (form.ad_apply_rate_limit === undefined) {
      form.ad_apply_rate_limit = 5
    }
    if (form.friend_link_apply_rate_limit === undefined) {
      form.friend_link_apply_rate_limit = 5
    }
    if (form.friend_link_update_rate_limit === undefined) {
      form.friend_link_update_rate_limit = 10
    }
    if (form.access_read_rate_limit === undefined) {
      form.access_read_rate_limit = 120
    }
    if (form.access_unlock_rate_limit === undefined) {
      form.access_unlock_rate_limit = 5
    }
    if (form.interaction_like_toggle_rate_limit === undefined) {
      form.interaction_like_toggle_rate_limit = 60
    }
    if (form.interaction_like_state_rate_limit === undefined) {
      form.interaction_like_state_rate_limit = 90
    }
    if (form.interaction_favorite_toggle_rate_limit === undefined) {
      form.interaction_favorite_toggle_rate_limit = 60
    }
    if (form.interaction_favorite_state_rate_limit === undefined) {
      form.interaction_favorite_state_rate_limit = 90
    }
    if (form.interaction_favorite_batch_rate_limit === undefined) {
      form.interaction_favorite_batch_rate_limit = 20
    }
    if (form.comment_delete_rate_limit === undefined) {
      form.comment_delete_rate_limit = 30
    }
    if (form.comment_batch_delete_rate_limit === undefined) {
      form.comment_batch_delete_rate_limit = 10
    }
    if (form.comment_like_toggle_rate_limit === undefined) {
      form.comment_like_toggle_rate_limit = 60
    }
    if (form.comment_like_state_rate_limit === undefined) {
      form.comment_like_state_rate_limit = 90
    }
    if (form.ai_writing_rate_limit === undefined) {
      form.ai_writing_rate_limit = 20
    }
    if (form.ai_chat_rate_limit === undefined) {
      form.ai_chat_rate_limit = 30
    }
    if (form.ai_meta_rate_limit === undefined) {
      form.ai_meta_rate_limit = 20
    }
    if (form.admin_revoke_token_rate_limit === undefined) {
      form.admin_revoke_token_rate_limit = 20
    }
    if (form.admin_revoke_all_tokens_rate_limit === undefined) {
      form.admin_revoke_all_tokens_rate_limit = 5
    }
    if (form.system_config_batch_update_rate_limit === undefined) {
      form.system_config_batch_update_rate_limit = 20
    }
    if (form.ranking_refresh_rate_limit === undefined) {
      form.ranking_refresh_rate_limit = 5
    }
    if (form.admin_post_delete_rate_limit === undefined) {
      form.admin_post_delete_rate_limit = 20
    }
    if (form.admin_post_permanent_delete_rate_limit === undefined) {
      form.admin_post_permanent_delete_rate_limit = 10
    }
    if (form.admin_topic_delete_rate_limit === undefined) {
      form.admin_topic_delete_rate_limit = 20
    }
    if (form.admin_topic_permanent_delete_rate_limit === undefined) {
      form.admin_topic_permanent_delete_rate_limit = 10
    }
    if (form.admin_media_delete_rate_limit === undefined) {
      form.admin_media_delete_rate_limit = 30
    }
    if (form.admin_media_cleanup_rate_limit === undefined) {
      form.admin_media_cleanup_rate_limit = 5
    }
    if (form.admin_user_status_update_rate_limit === undefined) {
      form.admin_user_status_update_rate_limit = 20
    }
    if (form.admin_user_reset_password_rate_limit === undefined) {
      form.admin_user_reset_password_rate_limit = 10
    }
    if (form.admin_ad_status_update_rate_limit === undefined) {
      form.admin_ad_status_update_rate_limit = 30
    }
    if (form.admin_ad_delete_rate_limit === undefined) {
      form.admin_ad_delete_rate_limit = 20
    }
    if (form.admin_ad_permanent_delete_rate_limit === undefined) {
      form.admin_ad_permanent_delete_rate_limit = 10
    }
    if (form.admin_ad_apply_switch_rate_limit === undefined) {
      form.admin_ad_apply_switch_rate_limit = 20
    }
    if (form.admin_ad_price_rules_rate_limit === undefined) {
      form.admin_ad_price_rules_rate_limit = 10
    }
    if (form.admin_ad_pit_update_rate_limit === undefined) {
      form.admin_ad_pit_update_rate_limit = 20
    }
    if (form.admin_friend_link_status_update_rate_limit === undefined) {
      form.admin_friend_link_status_update_rate_limit = 30
    }
    if (form.admin_friend_link_delete_rate_limit === undefined) {
      form.admin_friend_link_delete_rate_limit = 20
    }
    if (form.admin_announcement_status_update_rate_limit === undefined) {
      form.admin_announcement_status_update_rate_limit = 30
    }
    if (form.admin_announcement_delete_rate_limit === undefined) {
      form.admin_announcement_delete_rate_limit = 20
    }
    if (form.user_bind_email_rate_limit === undefined) {
      form.user_bind_email_rate_limit = 8
    }
    if (form.user_change_email_rate_limit === undefined) {
      form.user_change_email_rate_limit = 8
    }
    if (form.user_set_password_rate_limit === undefined) {
      form.user_set_password_rate_limit = 8
    }
    if (form.user_reset_password_rate_limit === undefined) {
      form.user_reset_password_rate_limit = 8
    }
    if (form.rate_limit_auto_block_enabled === undefined || form.rate_limit_auto_block_enabled === '') {
      form.rate_limit_auto_block_enabled = 'false'
    }
    if (form.rate_limit_auto_block_threshold === undefined) {
      form.rate_limit_auto_block_threshold = 20
    }
    if (form.rate_limit_auto_block_window_minutes === undefined) {
      form.rate_limit_auto_block_window_minutes = 10
    }
    if (form.rate_limit_auto_block_minutes === undefined) {
      form.rate_limit_auto_block_minutes = 60
    }
    if (form.rate_limit_auto_block_key_prefixes === undefined || form.rate_limit_auto_block_key_prefixes === '') {
      form.rate_limit_auto_block_key_prefixes = 'register,sendCode,checkEmail,forgotPassword,captchaGenerate,captchaVerify,comment-create,comment-delete,comment-batch-delete,comment-like-toggle,comment-like-state,portal-upload-image,ad-apply,friend-link-apply,friend-link-update,access-read,access-unlock,interaction-like-toggle,interaction-like-state,interaction-favorite-toggle,interaction-favorite-state,interaction-favorite-batch,user-bind-email,user-change-email,user-set-password,user-reset-password,ai-chat,ai-writing,ai-meta,admin-login,admin-post-delete,admin-post-permanent-delete,admin-topic-delete,admin-topic-permanent-delete,admin-media-delete,admin-media-cleanup,admin-user-status-update,admin-user-reset-password,admin-ad-status-update,admin-ad-delete,admin-ad-permanent-delete,admin-ad-apply-switch,admin-ad-price-rules,admin-ad-pit-update,admin-friend-link-status-update,admin-friend-link-delete,admin-announcement-status-update,admin-announcement-delete'
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

async function handleResetRateLimitDefaults() {
  try {
    await ElMessageBox.confirm(
      '将仅恢复限流相关配置到默认值，并立即保存生效。是否继续？',
      '恢复限流默认值',
      {
        type: 'warning',
        confirmButtonText: '恢复并保存',
        cancelButtonText: '取消',
      },
    )
  } catch {
    return
  }

  resettingRateLimit.value = true
  try {
    const payload: Record<string, string> = {}
    for (const [key, value] of Object.entries(rateLimitDefaults)) {
      payload[key] = String(value)
      form[key] = value
    }
    await systemConfigApi.batchUpdate(payload)
    ElMessage.success('限流配置已恢复默认值并保存成功')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '恢复限流默认值失败')
  } finally {
    resettingRateLimit.value = false
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

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
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
  .rate-limit-alert {
    margin-bottom: 14px;
  }
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
