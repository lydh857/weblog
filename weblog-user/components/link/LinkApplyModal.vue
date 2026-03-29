<template>
  <Teleport to="body">
    <Transition name="modal-fade" appear>
      <div v-if="visible" class="modal-overlay" @click.self="closeModal">
        <div class="modal-container">
          <!-- 标题栏 -->
          <div class="modal-header">
            <h3>申请友链</h3>
            <button class="close-btn" aria-label="关闭" @click="closeModal">
              <Icon name="heroicons:x-mark-20-solid" size="20" />
            </button>
          </div>

          <!-- 步骤条 -->
          <div class="stepper">
            <div class="step" :class="{ active: currentStep >= 1, completed: currentStep > 1 }">
              <div class="step-dot">
                <Icon v-if="currentStep > 1" name="heroicons:check-16-solid" size="14" />
                <span v-else>1</span>
              </div>
              <span class="step-label">申请须知</span>
            </div>
            <div class="step-line" />
            <div class="step" :class="{ active: currentStep >= 2, completed: currentStep > 2 }">
              <div class="step-dot">
                <Icon v-if="currentStep > 2" name="heroicons:check-16-solid" size="14" />
                <span v-else>2</span>
              </div>
              <span class="step-label">填写信息</span>
            </div>
            <div class="step-line" />
            <div class="step" :class="{ active: currentStep >= 3 }">
              <div class="step-dot"><span>3</span></div>
              <span class="step-label">审批状态</span>
            </div>
          </div>

          <div class="modal-body">
            <!-- 步骤1：申请须知 -->
            <div v-if="currentStep === 1" class="step-content">
              <div v-if="fetchLoading" class="loading-text">正在检查申请状态...</div>
              <template v-else>
                <div v-if="!isLoggedIn" class="notice notice-warning">
                  请先登录再进行申请。
                </div>
                <template v-else>
                  <div v-if="myLink && myLink.status === 'rejected'" class="notice notice-error">
                    <p>您的申请被拒绝。</p>
                    <p v-if="myLink.reason">拒绝原因：{{ myLink.reason }}</p>
                  </div>
                  <h4 class="guidelines-title">申请须知</h4>
                  <ul class="guidelines">
                    <li>网站内容健康，无政治、色情、暴力等不良信息。</li>
                    <li>网站能正常访问，且内容有价值，定期更新。</li>
                    <li>本站会定期检查友链，无法访问的链接将被移除。</li>
                  </ul>
                  <div class="step-actions">
                    <button
                      class="action-btn primary"
                      @click="currentStep = 2"
                    >
                      {{ myLink?.status === 'rejected' ? '修改信息并继续' : '我已了解，下一步' }}
                    </button>
                  </div>
                </template>
              </template>
            </div>

            <!-- 步骤2：填写信息 -->
            <div v-if="currentStep === 2" class="step-content">
              <form class="apply-form" @submit.prevent="handleSubmit">
                <div class="form-item">
                  <label for="link-name">网站名称 <span class="required">*</span></label>
                  <input id="link-name" v-model="form.name" type="text" maxlength="50" placeholder="例如：我的博客" required >
                </div>
                <div class="form-item">
                  <label for="link-url">网站链接 <span class="required">*</span></label>
                  <input id="link-url" v-model="form.url" type="url" maxlength="200" placeholder="https://example.com" required >
                </div>
                <div class="form-item">
                  <label for="link-logo">网站 Logo <span class="required">*</span></label>
                  <input id="link-logo" v-model="form.logo" type="url" maxlength="500" placeholder="https://example.com/logo.png" required >
                </div>
                <div class="form-item">
                  <label for="link-desc">网站介绍 <span class="required">*</span></label>
                  <textarea id="link-desc" v-model="form.description" maxlength="200" placeholder="一句话介绍您的网站" required rows="3" />
                </div>
              </form>
              <div class="step-actions">
                <button class="action-btn" @click="currentStep = 1">上一步</button>
                <button class="action-btn primary" :disabled="submitting" @click="handleSubmit">
                  {{ submitting ? '提交中...' : '提交申请' }}
                </button>
              </div>
            </div>

            <!-- 步骤3：审批状态 -->
            <div v-if="currentStep === 3" class="step-content">
              <div v-if="fetchLoading" class="loading-text">正在更新状态...</div>
              <template v-else-if="myLink">
                <div
                  class="notice"
                  :class="{
                    'notice-info': myLink.status === 'pending',
                    'notice-success': myLink.status === 'active',
                    'notice-error': myLink.status === 'rejected',
                  }"
                >
                  <p v-if="myLink.status === 'pending'">您的申请正在等待管理员审批...</p>
                  <p v-else-if="myLink.status === 'active'">您的申请已通过！</p>
                  <p v-else-if="myLink.status === 'rejected'">
                    您的申请被拒绝。
                    <span v-if="myLink.reason">原因：{{ myLink.reason }}</span>
                  </p>
                </div>

                <div class="link-preview">
                  <h4>您的友链信息</h4>
                  <div class="preview-row">
                    <span class="preview-label">网站名称：</span>
                    <span>{{ myLink.name }}</span>
                  </div>
                  <div class="preview-row">
                    <span class="preview-label">网站链接：</span>
                    <a
                      :href="getSafeHref(myLink.url) || '#'"
                      target="_blank"
                      rel="noopener noreferrer"
                      @click="handlePreviewLinkClick($event, myLink.url)"
                    >{{ myLink.url }}</a>
                  </div>
                  <div class="preview-row">
                    <span class="preview-label">Logo：</span>
                    <img
                      v-if="myLink.logo && !logoError"
                      :src="myLink.logo"
                      alt="Logo"
                      class="preview-logo"
                      @error="logoError = true"
                    >
                    <span v-else class="preview-logo-placeholder">{{ myLink.name?.charAt(0) || '?' }}</span>
                  </div>
                  <div class="preview-row">
                    <span class="preview-label">介绍：</span>
                    <span>{{ myLink.description }}</span>
                  </div>
                </div>

                <div v-if="myLink.status === 'active' || myLink.status === 'rejected'" class="step-actions">
                  <button class="action-btn" @click="currentStep = 2">编辑信息</button>
                </div>
              </template>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { friendLinkApi, type FriendLinkVO } from '~/api/content/friendLink'
import { useUserStore } from '~/stores/user'
import { normalizeSafeHref } from '~/utils/security/urlSafety'

const props = defineProps<{ visible: boolean }>()
const emit = defineEmits<{
  'update:visible': [val: boolean]
  'success': []
}>()

const userStore = useUserStore()
const message = useMessage()

const isLoggedIn = computed(() => userStore.isLoggedIn)
const currentStep = ref(1)
const fetchLoading = ref(false)
const submitting = ref(false)
const myLink = ref<FriendLinkVO | null>(null)
const logoError = ref(false)

const form = reactive({
  name: '',
  url: '',
  logo: '',
  description: '',
})

function getSafeHref(rawUrl: string | null | undefined) {
  return normalizeSafeHref(rawUrl)
}

function handlePreviewLinkClick(event: MouseEvent, rawUrl: string | null | undefined) {
  if (!getSafeHref(rawUrl)) {
    event.preventDefault()
  }
}

// 弹窗打开时获取我的申请状态
watch(() => props.visible, async (val) => {
  if (!val) return
  logoError.value = false
  if (!isLoggedIn.value) {
    currentStep.value = 1
    return
  }
  fetchLoading.value = true
  try {
    const res = await friendLinkApi.getMyLink()
    myLink.value = res.data
    if (myLink.value) {
      form.name = myLink.value.name
      form.url = myLink.value.url
      form.logo = myLink.value.logo
      form.description = myLink.value.description || ''
      // 待审批或已通过 → 直接跳到状态页
      if (myLink.value.status === 'pending' || myLink.value.status === 'active') {
        currentStep.value = 3
      } else {
        currentStep.value = 1
      }
    } else {
      Object.assign(form, { name: '', url: '', logo: '', description: '' })
      currentStep.value = 1
    }
  } catch {
    currentStep.value = 1
  } finally {
    fetchLoading.value = false
  }
})

function closeModal() {
  emit('update:visible', false)
}

function validateHttpUrl(rawUrl: string, fieldLabel: string): string | null {
  let parsed: URL
  try {
    parsed = new URL(rawUrl)
  } catch {
    return `${fieldLabel}格式不正确`
  }

  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return `${fieldLabel}仅支持 http/https`
  }

  return null
}

function validateForm(): string | null {
  const name = form.name.trim()
  const url = form.url.trim()
  const logo = form.logo.trim()
  const description = form.description.trim()

  if (!name) return '网站名称不能为空'
  if (!url) return '网站链接不能为空'
  if (!logo) return '网站 Logo 不能为空'
  if (!description) return '网站介绍不能为空'

  if (name.length > 50) return '网站名称不能超过50个字符'
  if (url.length > 200) return '网站链接不能超过200个字符'
  if (logo.length > 500) return 'Logo 链接不能超过500个字符'
  if (description.length > 200) return '网站介绍不能超过200个字符'

  const urlError = validateHttpUrl(url, '网站链接')
  if (urlError) return urlError

  const logoErrorMessage = validateHttpUrl(logo, 'Logo 链接')
  if (logoErrorMessage) return logoErrorMessage

  return null
}

async function handleSubmit() {
  const err = validateForm()
  if (err) {
    message.warning(err)
    return
  }
  submitting.value = true
  try {
    const payload = {
      name: form.name.trim(),
      url: form.url.trim(),
      logo: form.logo.trim(),
      description: form.description.trim(),
    }

    if (myLink.value) {
      await friendLinkApi.updateMyLink(payload)
    } else {
      await friendLinkApi.applyLink(payload)
    }
    message.success('提交成功')
    emit('success')
    // 重新获取状态
    const res = await friendLinkApi.getMyLink()
    myLink.value = res.data
    currentStep.value = 3
  } catch (error) {
    const messageText = error && typeof error === 'object' && 'message' in error
      ? String((error as { message?: unknown }).message || '').trim()
      : ''
    message.error(messageText || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
/* 弹窗动画 */
.modal-fade-enter-active,
.modal-fade-leave-active,
.modal-fade-appear-active {
  transition: opacity 0.25s;
  .modal-container { transition: transform 0.25s; }
}
.modal-fade-enter-from,
.modal-fade-leave-to,
.modal-fade-appear-from {
  opacity: 0;
  .modal-container { transform: translateY(20px) scale(0.96); }
}

.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;

  .dark & {
    background: rgba(0, 0, 0, 0.6);
  }
}

.modal-container {
  background: $color-bg;
  border-radius: 12px;
  border: 1px solid transparent;
  width: 100%;
  max-width: 560px;
  max-height: 90vh;
  overflow-y: auto;
  box-shadow: 0 16px 48px rgba(0, 0, 0, 0.18);
  .dark & {
    background: $color-dark-bg-secondary;
    box-shadow: 0 16px 48px rgba(0, 0, 0, 0.5);
    border-color: rgba(148, 163, 184, 0.14);
  }
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 1.25rem 1.5rem;
  border-bottom: 1px solid $color-border;
  .dark & { border-bottom-color: $color-dark-border; }
  h3 {
    font-size: 1.1rem;
    font-weight: 600;
    color: $color-text;
    .dark & { color: $color-dark-text; }
  }
}

.close-btn {
  border: none;
  background: none;
  color: $color-text-muted;
  cursor: pointer;
  padding: 4px;
  border-radius: 6px;
  transition: background 0.15s;
  &:hover { background: rgba(0, 0, 0, 0.06); }
  .dark & {
    color: $color-dark-text-muted;
    &:hover { background: rgba(255, 255, 255, 0.08); }
  }
}

/* 步骤条 */
.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.25rem 2rem;
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.4rem;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
  &.active {
    color: $color-primary;
    .step-dot {
      background: $color-primary;
      color: #fff;
      border-color: $color-primary;
    }
  }
  &.completed .step-dot {
    background: #10b981;
    border-color: #10b981;
    color: #fff;
  }
}

.step-dot {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  border: 2px solid $color-border;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.8rem;
  font-weight: 600;
  transition: all 0.2s;
  .dark & { border-color: $color-dark-border; }
}

.step-label {
  font-size: 0.8rem;
  font-weight: 500;
}

.step-line {
  flex: 1;
  height: 2px;
  background: $color-border;
  margin: 0 1rem;
  margin-bottom: 1.4rem;
  .dark & { background: $color-dark-border; }
}

.modal-body {
  padding: 1.5rem;
}

.step-content {
  animation: fadeIn 0.3s ease;
  min-height: 180px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

.loading-text {
  text-align: center;
  padding: 2rem;
  color: $color-text-muted;
}

/* 提示框 */
.notice {
  padding: 0.875rem 1rem;
  border-radius: 8px;
  margin-bottom: 1.25rem;
  border-left: 4px solid;
  font-size: 0.9rem;
  p { margin: 0.2rem 0; }
  &-warning {
    background: #fffbe6;
    border-color: #faad14;
    color: #d48806;
    .dark & { background: rgba(250, 173, 20, 0.1); }
  }
  &-info {
    background: #e6f7ff;
    border-color: #1890ff;
    color: #096dd9;
    .dark & { background: rgba(24, 144, 255, 0.1); }
  }
  &-success {
    background: #f6ffed;
    border-color: #52c41a;
    color: #389e0d;
    .dark & { background: rgba(82, 196, 26, 0.1); }
  }
  &-error {
    background: #fff1f0;
    border-color: #f5222d;
    color: #cf1322;
    .dark & { background: rgba(245, 34, 45, 0.1); }
  }
}

.guidelines-title {
  font-size: 1.05rem;
  font-weight: 600;
  text-align: center;
  margin-bottom: 0.75rem;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}

.guidelines {
  list-style: none;
  padding: 0;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
  li {
    padding: 0.3rem 0;
    text-align: center;
    &::before { content: '• '; }
  }
}

.step-actions {
  display: flex;
  justify-content: center;
  gap: 0.75rem;
  margin-top: 1.5rem;
}

.action-btn {
  padding: 0.6rem 1.5rem;
  border: 1px solid $color-border;
  background: $color-bg;
  color: $color-text-muted;
  border-radius: 8px;
  cursor: pointer;
  font-size: 0.9rem;
  font-weight: 500;
  transition: all 0.2s;
  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
    color: $color-dark-text-muted;
  }
  &:hover {
    border-color: $color-text-muted;
  }
  &.primary {
    background: $color-primary;
    border-color: $color-primary;
    color: #fff;
    &:hover {
      background: $color-primary-dark;
      border-color: $color-primary-dark;
    }
  }
  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* 表单 */
.apply-form {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.form-item {
  display: flex;
  flex-direction: column;
  label {
    margin-bottom: 0.4rem;
    font-size: 0.9rem;
    font-weight: 600;
    color: $color-text;
    .dark & { color: $color-dark-text; }
    .required { color: #f5222d; margin-left: 2px; }
  }
  input, textarea {
    padding: 0.65rem 0.875rem;
    border: 1px solid $color-border;
    border-radius: 8px;
    font-size: 0.9rem;
    background: $color-bg;
    color: $color-text;
    transition: border-color 0.2s, box-shadow 0.2s;
    .dark & {
      background: $color-dark-bg;
      border-color: $color-dark-border;
      color: $color-dark-text;
    }
    &:focus {
      outline: none;
      border-color: $color-primary;
      box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.15);
    }
    &::placeholder { color: #9ca3af; }
  }
}

/* 友链预览 */
.link-preview {
  margin-top: 1.5rem;
  h4 {
    text-align: center;
    font-size: 1rem;
    margin-bottom: 1rem;
    color: $color-text;
    .dark & { color: $color-dark-text; }
  }
}

.preview-row {
  display: flex;
  align-items: center;
  margin-bottom: 0.75rem;
  font-size: 0.9rem;
  color: $color-text;
  .dark & { color: $color-dark-text; }
  a {
    color: $color-primary;
    text-decoration: none;
    &:hover { text-decoration: underline; }
  }
}

.preview-label {
  width: 80px;
  flex-shrink: 0;
  font-weight: 500;
  color: $color-text-muted;
  .dark & { color: $color-dark-text-muted; }
}

.preview-logo {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  object-fit: cover;
}

.preview-logo-placeholder {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  color: #fff;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  font-weight: 700;
  flex-shrink: 0;
}
</style>
