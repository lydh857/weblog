<template>
  <div class="rate-limit-page">
    <div class="page-header">
      <div class="header-title">
        <h2>限流与风控</h2>
        <div class="quick-stats">
          <el-tag size="small" effect="plain">用户端 {{ userLimitCount }} 项</el-tag>
          <el-tag size="small" effect="plain">管理端 {{ adminLimitCount }} 项</el-tag>
          <el-tag size="small" effect="plain">自动封禁 5 项</el-tag>
        </div>
      </div>
      <div class="header-actions">
        <el-button size="small" plain :disabled="activeTab === 'block'" @click="handleExpandCurrent">展开当前分组</el-button>
        <el-button size="small" plain :disabled="activeTab === 'block'" @click="handleCollapseCurrent">收起当前分组</el-button>
        <el-button type="warning" plain size="small" :loading="resetting" @click="handleResetDefaults">恢复默认值</el-button>
        <el-button type="primary" size="small" :loading="saving" @click="handleSave">保存配置</el-button>
      </div>
    </div>

    <el-alert
      title="说明：阈值表示“每个统计窗口内最多允许次数”。例如 5 且窗口 60 秒 = 每 60 秒最多 5 次。"
      type="info"
      :closable="false"
      class="intro-alert"
    />

    <div class="filter-bar">
      <el-input
        v-model="searchText"
        size="small"
        clearable
        placeholder="搜索：接口名 / 标签 / key"
        class="filter-search"
      />
      <div class="filter-switch">
        <span>仅显示非默认值</span>
        <el-switch v-model="onlyChanged" />
      </div>
    </div>

    <el-tabs v-model="activeTab" class="panel-tabs">
      <el-tab-pane label="用户端限流" name="user">
        <div v-loading="loading" class="config-stack">
          <el-collapse v-model="userActiveGroups" class="group-collapse">
            <el-collapse-item
              v-for="group in filteredUserGroups"
              :key="group.title"
              :name="group.title"
            >
              <template #title>
                <span class="collapse-title">{{ group.title }}</span>
                <el-tag size="small" effect="plain" class="collapse-count">{{ group.items.length }} 项</el-tag>
              </template>
              <div class="limit-list">
                <div v-for="item in group.items" :key="item.key" class="limit-item" :class="{ 'limit-item--changed': isChangedFromDefault(item.key) }">
                  <div class="limit-main">
                    <span class="item-label">{{ item.label }}</span>
                    <div class="limit-actions">
                      <el-input-number
                        v-model.number="form[item.key]"
                        size="small"
                        :min="item.min"
                        :max="item.max"
                        controls-position="right"
                      />
                      <el-button v-if="isChangedFromDefault(item.key)" size="small" link type="primary" @click="handleResetOne(item.key)">恢复默认</el-button>
                    </div>
                  </div>
                  <p class="form-tip">{{ item.tip }}</p>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-if="filteredUserGroups.length === 0" description="没有匹配的限流项" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="管理端限流" name="admin">
        <div v-loading="loading" class="config-stack">
          <el-collapse v-model="adminActiveGroups" class="group-collapse">
            <el-collapse-item
              v-for="group in filteredAdminGroups"
              :key="group.title"
              :name="group.title"
            >
              <template #title>
                <span class="collapse-title">{{ group.title }}</span>
                <el-tag size="small" effect="plain" class="collapse-count">{{ group.items.length }} 项</el-tag>
              </template>
              <div class="limit-list">
                <div v-for="item in group.items" :key="item.key" class="limit-item" :class="{ 'limit-item--changed': isChangedFromDefault(item.key) }">
                  <div class="limit-main">
                    <span class="item-label">{{ item.label }}</span>
                    <div class="limit-actions">
                      <el-input-number
                        v-model.number="form[item.key]"
                        size="small"
                        :min="item.min"
                        :max="item.max"
                        controls-position="right"
                      />
                      <el-button v-if="isChangedFromDefault(item.key)" size="small" link type="primary" @click="handleResetOne(item.key)">恢复默认</el-button>
                    </div>
                  </div>
                  <p class="form-tip">{{ item.tip }}</p>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
          <el-empty v-if="filteredAdminGroups.length === 0" description="没有匹配的限流项" />
        </div>
      </el-tab-pane>

      <el-tab-pane label="自动封禁策略" name="block">
        <el-card v-loading="loading" shadow="never" class="config-card config-card--wide">
          <template #header><span class="card-title">自动封禁策略</span></template>
          <div class="limit-list">
            <div class="limit-item" :class="{ 'limit-item--changed': isChangedFromDefault('rate_limit_auto_block_enabled') }">
              <div class="limit-main">
                <span class="item-label">启用自动封禁</span>
                <div class="limit-actions">
                  <el-switch v-model="form.rate_limit_auto_block_enabled" active-value="true" inactive-value="false" />
                  <el-button v-if="isChangedFromDefault('rate_limit_auto_block_enabled')" size="small" link type="primary" @click="handleResetOne('rate_limit_auto_block_enabled')">恢复默认</el-button>
                </div>
              </div>
              <p class="form-tip">高风险接口短时间多次触发限流后自动封禁 IP</p>
            </div>
            <div class="limit-item" :class="{ 'limit-item--changed': isChangedFromDefault('rate_limit_auto_block_threshold') }">
              <div class="limit-main">
                <span class="item-label">触发阈值（次）</span>
                <div class="limit-actions">
                  <el-input-number v-model.number="form.rate_limit_auto_block_threshold" size="small" :min="1" :max="500" controls-position="right" />
                  <el-button v-if="isChangedFromDefault('rate_limit_auto_block_threshold')" size="small" link type="primary" @click="handleResetOne('rate_limit_auto_block_threshold')">恢复默认</el-button>
                </div>
              </div>
              <p class="form-tip">默认 20，建议 10~100</p>
            </div>
            <div class="limit-item" :class="{ 'limit-item--changed': isChangedFromDefault('rate_limit_auto_block_window_minutes') }">
              <div class="limit-main">
                <span class="item-label">统计窗口（分钟）</span>
                <div class="limit-actions">
                  <el-input-number v-model.number="form.rate_limit_auto_block_window_minutes" size="small" :min="1" :max="180" controls-position="right" />
                  <el-button v-if="isChangedFromDefault('rate_limit_auto_block_window_minutes')" size="small" link type="primary" @click="handleResetOne('rate_limit_auto_block_window_minutes')">恢复默认</el-button>
                </div>
              </div>
              <p class="form-tip">默认 10，建议 5~30</p>
            </div>
            <div class="limit-item" :class="{ 'limit-item--changed': isChangedFromDefault('rate_limit_auto_block_minutes') }">
              <div class="limit-main">
                <span class="item-label">封禁时长（分钟）</span>
                <div class="limit-actions">
                  <el-input-number v-model.number="form.rate_limit_auto_block_minutes" size="small" :min="1" :max="43200" controls-position="right" />
                  <el-button v-if="isChangedFromDefault('rate_limit_auto_block_minutes')" size="small" link type="primary" @click="handleResetOne('rate_limit_auto_block_minutes')">恢复默认</el-button>
                </div>
              </div>
              <p class="form-tip">默认 60，建议 30~1440</p>
            </div>
            <div class="limit-item" :class="{ 'limit-item--changed': isChangedFromDefault('rate_limit_auto_block_key_prefixes') }">
              <div class="limit-main limit-main--input">
                <span class="item-label">适用 key 前缀</span>
                <div class="limit-actions limit-actions--input">
                  <el-input v-model="form.rate_limit_auto_block_key_prefixes" size="small" placeholder="sendCode,forgotPassword,comment-create" />
                  <el-button v-if="isChangedFromDefault('rate_limit_auto_block_key_prefixes')" size="small" link type="primary" @click="handleResetOne('rate_limit_auto_block_key_prefixes')">恢复默认</el-button>
                </div>
              </div>
              <p class="form-tip">仅对这些限流 key 前缀启用自动封禁</p>
            </div>
          </div>
        </el-card>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ElMessage, ElMessageBox } from 'element-plus'
import { systemConfigApi, type SystemConfigVO } from '~/api/system/system-config'

interface LimitItem {
  key: string
  label: string
  min: number
  max: number
  tip: string
}

const loading = ref(false)
const saving = ref(false)
const resetting = ref(false)
const activeTab = ref<'user' | 'admin' | 'block'>('user')
const searchText = ref('')
const onlyChanged = ref(false)

const rateLimitDefaults: Record<string, string | number> = {
  login_rate_limit: 5,
  register_rate_limit: 5,
  admin_login_rate_limit: 5,
  send_code_rate_limit: 3,
  check_email_rate_limit: 10,
  forgot_password_rate_limit: 5,
  captcha_generate_rate_limit: 10,
  captcha_verify_rate_limit: 20,
  access_read_rate_limit: 120,
  access_unlock_rate_limit: 5,
  upload_rate_limit: 20,
  ad_apply_rate_limit: 5,
  friend_link_apply_rate_limit: 5,
  friend_link_update_rate_limit: 10,
  interaction_like_toggle_rate_limit: 60,
  interaction_like_state_rate_limit: 90,
  interaction_favorite_toggle_rate_limit: 60,
  interaction_favorite_state_rate_limit: 90,
  interaction_favorite_batch_rate_limit: 20,
  comment_rate_limit: 5,
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

const numberKeys = new Set(Object.keys(rateLimitDefaults).filter((key) =>
  key !== 'rate_limit_auto_block_enabled' && key !== 'rate_limit_auto_block_key_prefixes',
))

const form = reactive<Record<string, string | number>>({ ...rateLimitDefaults })

const authLimitItems: LimitItem[] = [
  { key: 'login_rate_limit', label: '登录限流', min: 1, max: 60, tip: '作用接口：portal-login / portal-login-by-code / admin-login；每 60 秒最多请求次数；默认 5，建议 3~20' },
  { key: 'register_rate_limit', label: '注册限流', min: 1, max: 60, tip: '作用接口：register；每 60 秒最多请求次数；默认 5，建议 3~20' },
  { key: 'captcha_generate_rate_limit', label: '验证码生成', min: 1, max: 120, tip: '作用接口：captchaGenerate；每 60 秒最多请求次数；默认 10，建议 5~40' },
  { key: 'captcha_verify_rate_limit', label: '验证码验证', min: 1, max: 180, tip: '作用接口：captchaVerify；每 60 秒最多请求次数；默认 20，建议 10~80' },
  { key: 'send_code_rate_limit', label: '发送验证码', min: 1, max: 60, tip: '作用接口：sendCode；每 60 秒最多请求次数；默认 3，建议 2~10' },
  { key: 'check_email_rate_limit', label: '邮箱可用性校验', min: 1, max: 60, tip: '作用接口：checkEmail；每 60 秒最多请求次数；默认 10，建议 5~30' },
  { key: 'forgot_password_rate_limit', label: '忘记密码', min: 1, max: 60, tip: '作用接口：forgotPassword；每 60 秒最多请求次数；默认 5，建议 3~15' },
  { key: 'user_bind_email_rate_limit', label: '绑定邮箱', min: 1, max: 60, tip: '作用接口：user-bind-email；每 300 秒最多请求次数；默认 8，建议 3~20' },
  { key: 'user_change_email_rate_limit', label: '换绑邮箱', min: 1, max: 60, tip: '作用接口：user-change-email；每 300 秒最多请求次数；默认 8，建议 3~20' },
  { key: 'user_set_password_rate_limit', label: '设置密码', min: 1, max: 60, tip: '作用接口：user-set-password；每 300 秒最多请求次数；默认 8，建议 3~20' },
  { key: 'user_reset_password_rate_limit', label: '重置密码', min: 1, max: 60, tip: '作用接口：user-reset-password；每 300 秒最多请求次数；默认 8，建议 3~20' },
]

const behaviorLimitItems: LimitItem[] = [
  { key: 'access_read_rate_limit', label: '阅读记录', min: 1, max: 240, tip: '作用接口：access-read；每 60 秒最多请求次数；默认 120，建议 60~180' },
  { key: 'access_unlock_rate_limit', label: '滑块解锁', min: 1, max: 60, tip: '作用接口：access-unlock；每 60 秒最多请求次数；默认 5，建议 3~20' },
  { key: 'comment_rate_limit', label: '评论创建', min: 1, max: 60, tip: '作用接口：comment-create；每 60 秒最多请求次数；默认 5，建议 3~20' },
  { key: 'comment_delete_rate_limit', label: '评论删除', min: 1, max: 120, tip: '作用接口：comment-delete；每 60 秒最多请求次数；默认 30，建议 10~80' },
  { key: 'comment_batch_delete_rate_limit', label: '评论批量删除', min: 1, max: 120, tip: '作用接口：comment-batch-delete；每 60 秒最多请求次数；默认 10，建议 3~40' },
  { key: 'comment_like_toggle_rate_limit', label: '评论点赞切换', min: 1, max: 120, tip: '作用接口：comment-like-toggle；每 60 秒最多请求次数；默认 60，建议 20~100' },
  { key: 'comment_like_state_rate_limit', label: '评论点赞状态', min: 1, max: 180, tip: '作用接口：comment-like-state；每 60 秒最多请求次数；默认 90，建议 30~150' },
  { key: 'interaction_like_toggle_rate_limit', label: '文章点赞切换', min: 1, max: 120, tip: '作用接口：interaction-like-toggle；每 60 秒最多请求次数；默认 60，建议 20~100' },
  { key: 'interaction_like_state_rate_limit', label: '文章点赞状态', min: 1, max: 180, tip: '作用接口：interaction-like-state；每 60 秒最多请求次数；默认 90，建议 30~150' },
  { key: 'interaction_favorite_toggle_rate_limit', label: '文章收藏切换', min: 1, max: 120, tip: '作用接口：interaction-favorite-toggle；每 60 秒最多请求次数；默认 60，建议 20~100' },
  { key: 'interaction_favorite_state_rate_limit', label: '文章收藏状态', min: 1, max: 180, tip: '作用接口：interaction-favorite-state；每 60 秒最多请求次数；默认 90，建议 30~150' },
  { key: 'interaction_favorite_batch_rate_limit', label: '批量取消收藏', min: 1, max: 120, tip: '作用接口：interaction-favorite-batch；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'upload_rate_limit', label: '上传接口', min: 1, max: 300, tip: '作用接口：portal-upload-image / user-avatar-upload / admin-upload-image；每 300 秒最多请求次数；默认 20，建议 10~80' },
  { key: 'ad_apply_rate_limit', label: '广告申请', min: 1, max: 60, tip: '作用接口：ad-apply；每 300 秒最多请求次数；默认 5，建议 3~20' },
  { key: 'friend_link_apply_rate_limit', label: '友链申请', min: 1, max: 60, tip: '作用接口：friend-link-apply；每 300 秒最多请求次数；默认 5，建议 3~20' },
  { key: 'friend_link_update_rate_limit', label: '友链更新', min: 1, max: 60, tip: '作用接口：friend-link-update；每 300 秒最多请求次数；默认 10，建议 3~30' },
]

const aiLimitItems: LimitItem[] = [
  { key: 'ai_writing_rate_limit', label: 'AI 写作', min: 1, max: 300, tip: '作用接口：ai-writing；每 60 秒最多请求次数；默认 20，建议 10~60' },
  { key: 'ai_chat_rate_limit', label: 'AI 对话', min: 1, max: 300, tip: '作用接口：ai-chat；每 60 秒最多请求次数；默认 30，建议 10~80' },
  { key: 'ai_meta_rate_limit', label: 'AI 元信息', min: 1, max: 300, tip: '作用接口：ai-meta；每 60 秒最多请求次数；默认 20，建议 10~60' },
]

const adminLimitItems: LimitItem[] = [
  { key: 'admin_revoke_token_rate_limit', label: '撤销单令牌', min: 1, max: 120, tip: '作用接口：admin-revoke-token；每 60 秒最多请求次数；默认 20，建议 10~60' },
  { key: 'admin_revoke_all_tokens_rate_limit', label: '撤销全部令牌', min: 1, max: 60, tip: '作用接口：admin-revoke-all-tokens；每 60 秒最多请求次数；默认 5，建议 2~20' },
  { key: 'system_config_batch_update_rate_limit', label: '系统配置保存', min: 1, max: 120, tip: '作用接口：admin-system-config-batch-update；每 60 秒最多请求次数；默认 20，建议 5~40' },
  { key: 'ranking_refresh_rate_limit', label: '手动刷新榜单', min: 1, max: 60, tip: '作用接口：admin-refresh-ranking；每 60 秒最多请求次数；默认 5，建议 1~20' },
  { key: 'admin_post_delete_rate_limit', label: '文章删除', min: 1, max: 120, tip: '作用接口：admin-post-delete；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'admin_post_permanent_delete_rate_limit', label: '文章永久删除', min: 1, max: 120, tip: '作用接口：admin-post-permanent-delete；每 60 秒最多请求次数；默认 10，建议 2~30' },
  { key: 'admin_topic_delete_rate_limit', label: '专题删除', min: 1, max: 120, tip: '作用接口：admin-topic-delete；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'admin_topic_permanent_delete_rate_limit', label: '专题永久删除', min: 1, max: 120, tip: '作用接口：admin-topic-permanent-delete；每 60 秒最多请求次数；默认 10，建议 2~30' },
  { key: 'admin_media_delete_rate_limit', label: '媒体删除', min: 1, max: 120, tip: '作用接口：admin-media-delete；每 60 秒最多请求次数；默认 30，建议 10~80' },
  { key: 'admin_media_cleanup_rate_limit', label: '媒体清理', min: 1, max: 120, tip: '作用接口：admin-media-cleanup；每 60 秒最多请求次数；默认 5，建议 1~20' },
  { key: 'admin_user_status_update_rate_limit', label: '用户状态变更', min: 1, max: 120, tip: '作用接口：admin-user-status-update；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'admin_user_reset_password_rate_limit', label: '重置用户密码', min: 1, max: 120, tip: '作用接口：admin-user-reset-password；每 60 秒最多请求次数；默认 10，建议 2~30' },
  { key: 'admin_ad_status_update_rate_limit', label: '广告状态变更', min: 1, max: 120, tip: '作用接口：admin-ad-status-update；每 60 秒最多请求次数；默认 30，建议 10~80' },
  { key: 'admin_ad_delete_rate_limit', label: '广告删除', min: 1, max: 120, tip: '作用接口：admin-ad-delete；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'admin_ad_permanent_delete_rate_limit', label: '广告永久删除', min: 1, max: 120, tip: '作用接口：admin-ad-permanent-delete；每 60 秒最多请求次数；默认 10，建议 2~30' },
  { key: 'admin_ad_apply_switch_rate_limit', label: '广告申请开关', min: 1, max: 120, tip: '作用接口：admin-ad-apply-switch；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'admin_ad_price_rules_rate_limit', label: '广告价格规则', min: 1, max: 120, tip: '作用接口：admin-ad-price-rules；每 60 秒最多请求次数；默认 10，建议 2~30' },
  { key: 'admin_ad_pit_update_rate_limit', label: '广告坑位调整', min: 1, max: 120, tip: '作用接口：admin-ad-pit-update；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'admin_friend_link_status_update_rate_limit', label: '友链状态变更', min: 1, max: 120, tip: '作用接口：admin-friend-link-status-update；每 60 秒最多请求次数；默认 30，建议 10~80' },
  { key: 'admin_friend_link_delete_rate_limit', label: '友链删除', min: 1, max: 120, tip: '作用接口：admin-friend-link-delete；每 60 秒最多请求次数；默认 20，建议 5~60' },
  { key: 'admin_announcement_status_update_rate_limit', label: '公告状态变更', min: 1, max: 120, tip: '作用接口：admin-announcement-status-update；每 60 秒最多请求次数；默认 30，建议 10~80' },
  { key: 'admin_announcement_delete_rate_limit', label: '公告删除', min: 1, max: 120, tip: '作用接口：admin-announcement-delete；每 60 秒最多请求次数；默认 20，建议 5~60' },
]

const userGroups = [
  { title: '认证与账号安全', items: authLimitItems },
  { title: '用户行为限流', items: behaviorLimitItems },
  { title: 'AI 接口限流', items: aiLimitItems },
]

const USER_GROUPS_STORAGE_KEY = 'rate-limit:user-groups'
const ADMIN_GROUPS_STORAGE_KEY = 'rate-limit:admin-groups'

function loadStoredGroups(storageKey: string, defaultGroups: string[]): string[] {
  if (!import.meta.client) {
    return [...defaultGroups]
  }
  try {
    const raw = localStorage.getItem(storageKey)
    if (!raw) {
      return [...defaultGroups]
    }
    const parsed = JSON.parse(raw)
    if (!Array.isArray(parsed)) {
      return [...defaultGroups]
    }
    const filtered = parsed.filter((name): name is string => typeof name === 'string' && defaultGroups.includes(name))
    return filtered
  } catch {
    return [...defaultGroups]
  }
}

function persistGroups(storageKey: string, groups: string[]) {
  if (!import.meta.client) {
    return
  }
  localStorage.setItem(storageKey, JSON.stringify(groups))
}

const userGroupTitles = userGroups.map((group) => group.title)
const userActiveGroups = ref(loadStoredGroups(USER_GROUPS_STORAGE_KEY, userGroupTitles))

const adminCoreKeys = new Set([
  'admin_revoke_token_rate_limit',
  'admin_revoke_all_tokens_rate_limit',
  'system_config_batch_update_rate_limit',
  'ranking_refresh_rate_limit',
  'admin_user_status_update_rate_limit',
  'admin_user_reset_password_rate_limit',
])

const adminGroups = [
  {
    title: '账号与系统操作',
    items: adminLimitItems.filter((item) => adminCoreKeys.has(item.key)),
  },
  {
    title: '内容与广告操作',
    items: adminLimitItems.filter((item) => !adminCoreKeys.has(item.key)),
  },
]
const adminGroupTitles = adminGroups.map((group) => group.title)
const adminActiveGroups = ref(loadStoredGroups(ADMIN_GROUPS_STORAGE_KEY, adminGroupTitles))

const userLimitCount = computed(() => authLimitItems.length + behaviorLimitItems.length + aiLimitItems.length)
const adminLimitCount = computed(() => adminLimitItems.length)

function isChangedFromDefault(key: string): boolean {
  return String(form[key] ?? '') !== String(rateLimitDefaults[key] ?? '')
}

function filterGroups(groups: { title: string, items: LimitItem[] }[]) {
  const keyword = searchText.value.trim().toLowerCase()
  return groups
    .map((group) => {
      const items = group.items.filter((item) => {
        if (onlyChanged.value && !isChangedFromDefault(item.key)) {
          return false
        }
        if (!keyword) {
          return true
        }
        return (
          item.label.toLowerCase().includes(keyword)
          || item.key.toLowerCase().includes(keyword)
          || item.tip.toLowerCase().includes(keyword)
        )
      })
      return { ...group, items }
    })
    .filter((group) => group.items.length > 0)
}

const filteredUserGroups = computed(() => filterGroups(userGroups))
const filteredAdminGroups = computed(() => filterGroups(adminGroups))

function handleExpandCurrent() {
  if (activeTab.value === 'user') {
    userActiveGroups.value = filteredUserGroups.value.map((group) => group.title)
    return
  }
  if (activeTab.value === 'admin') {
    adminActiveGroups.value = filteredAdminGroups.value.map((group) => group.title)
  }
}

function handleCollapseCurrent() {
  if (activeTab.value === 'user') {
    userActiveGroups.value = []
    return
  }
  if (activeTab.value === 'admin') {
    adminActiveGroups.value = []
  }
}

function handleResetOne(key: string) {
  if (!(key in rateLimitDefaults)) {
    return
  }
  form[key] = rateLimitDefaults[key]
}

async function loadData() {
  loading.value = true
  try {
    const res = await systemConfigApi.list()
    const list: SystemConfigVO[] = res.data
    for (const item of list) {
      if (!(item.configKey in rateLimitDefaults)) {
        continue
      }
      form[item.configKey] = numberKeys.has(item.configKey)
        ? Number(item.configValue) || 0
        : (item.configValue || '')
    }
    for (const [key, value] of Object.entries(rateLimitDefaults)) {
      if (form[key] === undefined || form[key] === '') {
        form[key] = value
      }
    }
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '加载限流配置失败')
  } finally {
    loading.value = false
  }
}

function buildPayload(): Record<string, string> {
  const payload: Record<string, string> = {}
  for (const key of Object.keys(rateLimitDefaults)) {
    payload[key] = String(form[key] ?? rateLimitDefaults[key])
  }
  return payload
}

async function handleSave() {
  saving.value = true
  try {
    await systemConfigApi.batchUpdate(buildPayload())
    ElMessage.success('限流与风控配置保存成功')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function handleResetDefaults() {
  await ElMessageBox.confirm('将恢复全部限流与自动封禁配置为默认值并立即保存，是否继续？', '恢复默认值', {
    type: 'warning',
    confirmButtonText: '确认恢复',
    cancelButtonText: '取消',
  })

  resetting.value = true
  try {
    for (const [key, value] of Object.entries(rateLimitDefaults)) {
      form[key] = value
    }
    await systemConfigApi.batchUpdate(buildPayload())
    ElMessage.success('已恢复默认值并保存')
  } catch (e: unknown) {
    ElMessage.error((e as Error).message || '恢复默认值失败')
  } finally {
    resetting.value = false
  }
}

onMounted(loadData)

watch(userActiveGroups, (groups) => {
  persistGroups(USER_GROUPS_STORAGE_KEY, groups)
}, { deep: true })

watch(adminActiveGroups, (groups) => {
  persistGroups(ADMIN_GROUPS_STORAGE_KEY, groups)
}, { deep: true })
</script>

<style scoped lang="scss">
.rate-limit-page {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  .page-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;
    padding: 0 20px;
    height: 62px;
    background: var(--el-bg-color);
    border-radius: 10px;
    border: 1px solid var(--el-border-color-lighter);
    h2 {
      margin: 0;
      font-size: 1.05rem;
      font-weight: 600;
    }
  }
}

.header-title {
  display: flex;
  align-items: center;
  gap: 12px;
}

.quick-stats {
  display: flex;
  gap: 8px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.intro-alert {
  margin-bottom: 10px;
}

.filter-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.filter-search {
  max-width: 360px;
}

.filter-switch {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.config-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12px;
}

.config-stack {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.group-collapse {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  overflow: hidden;
  :deep(.el-collapse-item__header) {
    padding: 0 10px;
    font-size: 13px;
    border-radius: 0;
  }
  :deep(.el-collapse-item__content) {
    padding: 10px 4px 0;
  }
  :deep(.el-collapse-item:last-child .el-collapse-item__wrap) {
    border-bottom: none;
  }
}

.collapse-title {
  font-weight: 600;
}

.collapse-count {
  margin-left: 8px;
}

.config-card--wide {
  grid-column: 1 / -1;
}

.panel-tabs {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  background: var(--el-bg-color);
  overflow: hidden;
  :deep(.el-tabs__header) {
    margin: 0;
    padding: 0 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    background: var(--el-fill-color-blank);
  }
  :deep(.el-tabs__content) {
    height: calc(100% - 46px);
    overflow-y: auto;
    overflow-x: hidden;
    padding: 10px;
  }
  :deep(.el-tab-pane) {
    min-height: 100%;
  }
}

@media (max-width: 1200px) {
  .config-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 900px) {
  .filter-bar {
    flex-direction: column;
    align-items: stretch;
  }
  .filter-search {
    max-width: unset;
  }
}

.card-title {
  font-size: 14px;
  font-weight: 600;
}

.limit-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.limit-item {
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  padding: 8px 10px;
  background: var(--el-fill-color-blank);
}

.limit-item--changed {
  border-color: var(--el-color-primary-light-5);
  box-shadow: inset 0 0 0 1px var(--el-color-primary-light-8);
}

.limit-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.limit-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
}

.limit-actions--input {
  width: 100%;
  :deep(.el-input) {
    flex: 1;
  }
}

.limit-main--input {
  align-items: flex-start;
}

.item-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.form-tip {
  margin: 6px 0 0;
  font-size: 11px;
  line-height: 1.45;
  color: var(--el-text-color-secondary);
}

:deep(.el-card__body) {
  padding: 12px;
}

:deep(.el-card) {
  border-radius: 10px;
  overflow: hidden;
}
</style>
