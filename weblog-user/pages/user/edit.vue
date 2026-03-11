<template>
  <div class="edit-page">
    <div class="edit-header">
      <NuxtLink to="/user" class="back-link">
        <Icon name="heroicons:arrow-left-20-solid" size="18" />
        返回个人中心
      </NuxtLink>
      <h1 class="edit-title">
        <Icon name="heroicons:user-circle-20-solid" size="22" />
        编辑资料
      </h1>
      <p class="edit-subtitle">完善你的公开资料和账号安全设置</p>
    </div>

    <div v-if="loading" class="loading-state">
      <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
    </div>

    <section
      v-if="!loading && profileData?.profileReviewStatus"
      class="review-status-banner"
      :class="`is-${profileData.profileReviewStatus}`"
    >
      <p class="review-title">{{ reviewStatusTitle }}</p>
      <p class="review-desc">{{ reviewStatusDesc }}</p>
      <p v-if="profileData.profileReviewStatus === 'rejected' && profileData.profileReviewRejectReason" class="review-reason">
        拒绝原因：{{ profileData.profileReviewRejectReason }}
      </p>
    </section>

    <form v-if="!loading" class="edit-form" @submit.prevent="handleSave">
      <div class="form-section">
        <label class="form-label">头像</label>
        <div class="avatar-edit">
          <div class="avatar-preview">
            <img v-if="showAvatarImage" :src="currentAvatarSrc!" alt="头像" class="avatar-img" @error="handleAvatarImageError" />
            <span v-else class="avatar-placeholder">{{ (form.nickname || 'U').charAt(0) }}</span>
          </div>
          <div class="avatar-actions">
            <label class="upload-btn" for="avatar-input">
              <Icon name="heroicons:camera-16-solid" size="16" />
              选择并裁剪
            </label>
            <input id="avatar-input" type="file" accept="image/jpeg,image/png,image/webp" class="hidden-input" @change="handleFileSelect" />
            <p class="avatar-hint">支持 JPG、PNG、WebP，最大 5MB</p>
            <p class="avatar-review-hint">头像将在提交后进入审核，审核通过后生效</p>
            <p v-if="pendingAvatarFile" class="avatar-pending-tip">已选择新头像，点击“提交审核”后生效</p>
          </div>
        </div>
      </div>

      <div class="form-section">
        <label for="nickname" class="form-label">昵称</label>
        <input id="nickname" v-model="form.nickname" type="text" maxlength="20" placeholder="请输入昵称" class="form-input" />
        <span class="char-count">{{ form.nickname?.length || 0 }}/20</span>
      </div>

      <div class="form-section">
        <label for="bio" class="form-label">个人简介</label>
        <textarea id="bio" v-model="form.bio" maxlength="200" rows="3" placeholder="介绍一下自己吧..." class="form-textarea" />
        <span class="char-count">{{ form.bio?.length || 0 }}/200</span>
      </div>

      <div class="form-actions">
        <button type="submit" class="btn btn-primary" :disabled="saving">
          <Icon v-if="saving" name="heroicons:arrow-path-16-solid" size="16" class="spin" />
          {{ saving ? '提交中...' : '提交审核' }}
        </button>
      </div>
    </form>

    <section v-if="!loading" class="security-section">
      <h2 class="section-title">账号安全</h2>
      <div class="security-card">
        <div class="security-header">
          <Icon name="heroicons:envelope-16-solid" size="18" class="security-icon" />
          <div>
            <p class="security-label">邮箱</p>
            <p class="security-value">{{ profileData?.email || '未绑定' }}</p>
          </div>
          <button type="button" class="btn btn-secondary btn-sm" @click="openEmailDialog">{{ profileData?.needBindEmail ? '绑定邮箱' : '换绑邮箱' }}</button>
        </div>
      </div>

      <div class="security-card">
        <div class="security-header">
          <Icon name="heroicons:lock-closed-16-solid" size="18" class="security-icon" />
          <div>
            <p class="security-label">密码</p>
            <p class="security-value">{{ profileData?.hasPassword ? '已设置' : '未设置' }}</p>
          </div>
          <button type="button" class="btn btn-secondary btn-sm" @click="openPasswordDialog">{{ profileData?.hasPassword ? '重置密码' : '设置密码' }}</button>
        </div>
      </div>
    </section>

    <div v-if="emailDialogVisible" class="dialog-overlay">
      <div class="dialog-card">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ profileData?.needBindEmail ? '绑定邮箱' : '换绑邮箱' }}</h3>
          <button type="button" class="dialog-close" @click="emailDialogVisible = false"><Icon name="heroicons:x-mark-16-solid" size="18" /></button>
        </div>
        <div class="dialog-form">
          <div class="dialog-field email-field-wrap">
            <label>新邮箱</label>
            <div class="input-wrapper" @mouseenter="dlgEmailHover = true" @mouseleave="dlgEmailHover = false">
              <input v-model="emailForm.email" type="email" placeholder="请输入邮箱" class="form-input" autocomplete="off" @input="onDlgEmailInput" @focus="onDlgEmailInput" @blur="onDlgEmailBlur" />
              <button v-show="dlgEmailHover && emailForm.email" type="button" class="field-clear" tabindex="-1" @mousedown.prevent="emailForm.email = ''; dlgEmailErrors.email = ''"><Icon name="heroicons:x-circle-16-solid" size="16" /></button>
            </div>
            <ul v-show="showDlgEmailSuggestions && dlgEmailSuggestions.length" class="dlg-email-suggestions">
              <li v-for="s in dlgEmailSuggestions" :key="s" @mousedown.prevent="selectDlgEmailSuggestion(s)">{{ s }}</li>
            </ul>
            <span class="field-error" :class="{ visible: dlgEmailErrors.email }">{{ dlgEmailErrors.email || '&nbsp;' }}</span>
          </div>

          <div class="dialog-field">
            <label>验证码</label>
            <div class="code-row">
              <div class="input-wrapper code-input-flex" @mouseenter="dlgEmailCodeHover = true" @mouseleave="dlgEmailCodeHover = false">
                <input v-model="emailForm.code" type="text" placeholder="请输入验证码" maxlength="6" inputmode="numeric" class="form-input" autocomplete="off" @blur="onDlgEmailCodeBlur" />
                <button v-show="dlgEmailCodeHover && emailForm.code" type="button" class="field-clear" tabindex="-1" @mousedown.prevent="emailForm.code = ''; dlgEmailErrors.code = ''"><Icon name="heroicons:x-circle-16-solid" size="16" /></button>
              </div>
              <button type="button" class="btn btn-secondary btn-sm code-btn" :disabled="emailCooldown > 0 || emailSending" @click="sendEmailCode">{{ emailCooldown > 0 ? `${emailCooldown}s` : '获取验证码' }}</button>
            </div>
            <span class="field-error" :class="{ visible: dlgEmailErrors.code }">{{ dlgEmailErrors.code || '&nbsp;' }}</span>
          </div>

          <div class="dialog-actions">
            <button type="button" class="btn btn-secondary" @click="emailDialogVisible = false">取消</button>
            <button type="button" class="btn btn-primary" :disabled="emailSubmitting" @click="submitEmail">{{ emailSubmitting ? '提交中...' : '确认' }}</button>
          </div>
        </div>
      </div>
    </div>
    <div v-if="passwordDialogVisible" class="dialog-overlay">
      <div class="dialog-card">
        <div class="dialog-header">
          <h3 class="dialog-title">{{ profileData?.hasPassword ? '重置密码' : '设置密码' }}</h3>
          <button type="button" class="dialog-close" @click="passwordDialogVisible = false"><Icon name="heroicons:x-mark-16-solid" size="18" /></button>
        </div>
        <div class="dialog-form">
          <template v-if="profileData?.hasPassword">
            <div class="dialog-field">
              <label>验证码（发送到 {{ profileData?.email }}）</label>
              <div class="code-row">
                <div class="input-wrapper code-input-flex" @mouseenter="dlgPwdCodeHover = true" @mouseleave="dlgPwdCodeHover = false">
                  <input v-model="pwdForm.code" type="text" placeholder="请输入验证码" maxlength="6" inputmode="numeric" class="form-input" autocomplete="off" @blur="onDlgPwdCodeBlur" />
                  <button v-show="dlgPwdCodeHover && pwdForm.code" type="button" class="field-clear" tabindex="-1" @mousedown.prevent="pwdForm.code = ''; dlgPwdErrors.code = ''"><Icon name="heroicons:x-circle-16-solid" size="16" /></button>
                </div>
                <button type="button" class="btn btn-secondary btn-sm code-btn" :disabled="pwdCooldown > 0 || pwdSending" @click="sendPwdCode">{{ pwdCooldown > 0 ? `${pwdCooldown}s` : '获取验证码' }}</button>
              </div>
              <span class="field-error" :class="{ visible: dlgPwdErrors.code }">{{ dlgPwdErrors.code || '&nbsp;' }}</span>
            </div>
          </template>

          <div class="dialog-field">
            <label>新密码</label>
            <div class="input-wrapper" @mouseenter="dlgPwd1Hover = true" @mouseleave="dlgPwd1Hover = false">
              <input v-model="pwdForm.password" :type="showDlgPwd ? 'text' : 'password'" placeholder="至少8位，含大小写字母和数字" class="form-input input-with-actions" autocomplete="new-password" @blur="onDlgPwdBlur" />
              <button v-show="dlgPwd1Hover && pwdForm.password" type="button" class="field-clear with-toggle" tabindex="-1" @mousedown.prevent="pwdForm.password = ''; dlgPwdErrors.password = ''"><Icon name="heroicons:x-circle-16-solid" size="16" /></button>
              <button v-show="dlgPwd1Hover && pwdForm.password" type="button" class="field-toggle" tabindex="-1" @mousedown.prevent="showDlgPwd = !showDlgPwd"><Icon :name="showDlgPwd ? 'heroicons:eye-slash-16-solid' : 'heroicons:eye-16-solid'" size="16" /></button>
            </div>
            <div v-show="pwdForm.password" class="password-strength">
              <div class="strength-bars"><span v-for="i in 4" :key="i" class="bar" :class="{ active: i <= dlgPwdLevel }" :style="{ backgroundColor: i <= dlgPwdLevel ? dlgPwdColor : undefined }" /></div>
              <span class="strength-text" :style="{ color: dlgPwdColor }">{{ dlgPwdLabel }}</span>
            </div>
            <span class="field-error" :class="{ visible: dlgPwdErrors.password }">{{ dlgPwdErrors.password || '&nbsp;' }}</span>
          </div>

          <div class="dialog-field">
            <label>确认密码</label>
            <div class="input-wrapper" @mouseenter="dlgPwd2Hover = true" @mouseleave="dlgPwd2Hover = false">
              <input v-model="pwdForm.confirmPassword" :type="showDlgConfirmPwd ? 'text' : 'password'" placeholder="请再次输入密码" class="form-input input-with-actions" autocomplete="new-password" @blur="onDlgConfirmPwdBlur" />
              <button v-show="dlgPwd2Hover && pwdForm.confirmPassword" type="button" class="field-clear with-toggle" tabindex="-1" @mousedown.prevent="pwdForm.confirmPassword = ''; dlgPwdErrors.confirmPassword = ''"><Icon name="heroicons:x-circle-16-solid" size="16" /></button>
              <button v-show="dlgPwd2Hover && pwdForm.confirmPassword" type="button" class="field-toggle" tabindex="-1" @mousedown.prevent="showDlgConfirmPwd = !showDlgConfirmPwd"><Icon :name="showDlgConfirmPwd ? 'heroicons:eye-slash-16-solid' : 'heroicons:eye-16-solid'" size="16" /></button>
            </div>
            <span class="field-error" :class="{ visible: dlgPwdErrors.confirmPassword }">{{ dlgPwdErrors.confirmPassword || '&nbsp;' }}</span>
          </div>

          <div class="dialog-actions">
            <button type="button" class="btn btn-secondary" @click="passwordDialogVisible = false">取消</button>
            <button type="button" class="btn btn-primary" :disabled="pwdSubmitting" @click="submitPassword">{{ pwdSubmitting ? '提交中...' : '确认' }}</button>
          </div>
        </div>
      </div>
    </div>

    <SliderCaptcha v-model:visible="captchaVisible" @success="onCaptchaSuccess" />

    <AvatarCropper
      v-model="cropperVisible"
      :image-src="cropperImageSrc"
      :output-size="320"
      output-type="image/webp"
      @crop="handleAvatarCropped"
    />
  </div>
</template>

<script setup lang="ts">
import { userApi, type UserProfileVO } from '~/api/user'
import { authApi } from '~/api/auth'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'

useHead({ title: '编辑资料' })

const userStore = useUserStore()
const message = useMessage()
const loading = ref(true)
const saving = ref(false)
const avatarPreview = ref<string | null>(null)
const avatarImageLoadFailed = ref(false)
const pendingAvatarFile = ref<File | null>(null)
const pendingAvatarUrl = ref<string | null>(null)
const cropperVisible = ref(false)
const cropperImageSrc = ref('')
const form = reactive({ nickname: '', bio: '', avatar: null as string | null })

const currentAvatarSrc = computed(() => avatarPreview.value || form.avatar)
const showAvatarImage = computed(() => !!currentAvatarSrc.value && !avatarImageLoadFailed.value)

const { visible: captchaVisible, open: openCaptcha, handleSuccess: onCaptchaSuccess } = useSliderCaptcha()

const profileData = ref<UserProfileVO | null>(null)
const emailDialogVisible = ref(false)
const passwordDialogVisible = ref(false)

const emailForm = reactive({ email: '', code: '' })
const emailCooldown = ref(0)
const emailSending = ref(false)
const emailSubmitting = ref(false)
let emailTimer: ReturnType<typeof setInterval> | null = null

const pwdForm = reactive({ code: '', password: '', confirmPassword: '' })
const pwdCooldown = ref(0)
const pwdSending = ref(false)
const pwdSubmitting = ref(false)
let pwdTimer: ReturnType<typeof setInterval> | null = null

const dlgEmailHover = ref(false)
const dlgEmailCodeHover = ref(false)
const dlgPwdCodeHover = ref(false)
const dlgPwd1Hover = ref(false)
const dlgPwd2Hover = ref(false)
const showDlgPwd = ref(false)
const showDlgConfirmPwd = ref(false)

const dlgEmailErrors = reactive({ email: '', code: '' })
const showDlgEmailSuggestions = ref(false)
const emailSuffixes = ['@qq.com', '@163.com', '@gmail.com', '@outlook.com', '@126.com', '@foxmail.com']
const dlgEmailSuggestions = computed(() => {
  const val = emailForm.email
  if (!val || val.includes('@')) return []
  return emailSuffixes.map(s => val + s)
})

function onDlgEmailInput() { showDlgEmailSuggestions.value = dlgEmailSuggestions.value.length > 0 }
function selectDlgEmailSuggestion(s: string) { emailForm.email = s; showDlgEmailSuggestions.value = false; dlgEmailErrors.email = '' }
function onDlgEmailBlur() {
  setTimeout(() => { showDlgEmailSuggestions.value = false }, 150)
  if (!emailForm.email) dlgEmailErrors.email = '请输入邮箱'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailForm.email)) dlgEmailErrors.email = '请输入有效的邮箱地址'
  else dlgEmailErrors.email = ''
}
function onDlgEmailCodeBlur() {
  if (!emailForm.code) dlgEmailErrors.code = '请输入验证码'
  else if (emailForm.code.length !== 6) dlgEmailErrors.code = '请输入 6 位验证码'
  else dlgEmailErrors.code = ''
}

const dlgPwdErrors = reactive({ code: '', password: '', confirmPassword: '' })
function onDlgPwdCodeBlur() {
  if (!pwdForm.code) dlgPwdErrors.code = '请输入验证码'
  else if (pwdForm.code.length !== 6) dlgPwdErrors.code = '请输入 6 位验证码'
  else dlgPwdErrors.code = ''
}
function onDlgPwdBlur() {
  if (!pwdForm.password) dlgPwdErrors.password = '请输入密码'
  else if (pwdForm.password.length < 8) dlgPwdErrors.password = '密码至少 8 位'
  else if (!/[a-z]/.test(pwdForm.password) || !/[A-Z]/.test(pwdForm.password)) dlgPwdErrors.password = '密码需包含大小写字母'
  else if (!/\d/.test(pwdForm.password)) dlgPwdErrors.password = '密码需包含数字'
  else dlgPwdErrors.password = ''
}
function onDlgConfirmPwdBlur() {
  if (!pwdForm.confirmPassword) dlgPwdErrors.confirmPassword = '请再次输入密码'
  else if (pwdForm.password !== pwdForm.confirmPassword) dlgPwdErrors.confirmPassword = '两次密码不一致'
  else dlgPwdErrors.confirmPassword = ''
}

const dlgPwdLevel = computed(() => {
  const p = pwdForm.password
  if (!p) return 0
  let s = 0
  if (p.length >= 8) s++
  if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s++
  if (/\d/.test(p)) s++
  if (/[^a-zA-Z0-9]/.test(p)) s++
  return s
})
const dlgPwdColor = computed(() => ['', '#ef4444', '#f59e0b', '#3b82f6', '#22c55e'][dlgPwdLevel.value])
const dlgPwdLabel = computed(() => ['', '弱', '一般', '较强', '强'][dlgPwdLevel.value])

const reviewStatusTitle = computed(() => {
  const status = profileData.value?.profileReviewStatus
  if (status === 'pending') return '当前有一条个人信息审核中'
  if (status === 'rejected') return '上一条个人信息审核未通过'
  if (status === 'approved') return '最近一次个人信息审核已通过'
  return ''
})

const reviewStatusDesc = computed(() => {
  const status = profileData.value?.profileReviewStatus
  if (status === 'pending') return '再次提交会覆盖当前待审核内容'
  if (status === 'rejected') return '请根据拒绝原因修改后重新提交审核'
  if (status === 'approved') return '你可以继续编辑并再次提交审核'
  return ''
})

function getErrorMessage(error: unknown, fallback: string) {
  if (error && typeof error === 'object' && 'message' in error) {
    const message = (error as { message?: unknown }).message
    if (typeof message === 'string' && message.trim()) {
      return message
    }
  }
  return fallback
}

function getAvatarFileExtension(type: string) {
  if (type === 'image/png') return 'png'
  if (type === 'image/jpeg') return 'jpg'
  return 'webp'
}

function handleAvatarImageError() {
  avatarImageLoadFailed.value = true
}

function openEmailDialog() {
  emailForm.email = ''
  emailForm.code = ''
  dlgEmailErrors.email = ''
  dlgEmailErrors.code = ''
  showDlgEmailSuggestions.value = false
  emailDialogVisible.value = true
}

function openPasswordDialog() {
  pwdForm.code = ''
  pwdForm.password = ''
  pwdForm.confirmPassword = ''
  showDlgPwd.value = false
  showDlgConfirmPwd.value = false
  dlgPwdErrors.code = ''
  dlgPwdErrors.password = ''
  dlgPwdErrors.confirmPassword = ''
  passwordDialogVisible.value = true
}

function startEmailCooldown(seconds: number) {
  emailCooldown.value = seconds
  if (emailTimer) clearInterval(emailTimer)
  emailTimer = setInterval(() => {
    if (emailCooldown.value > 0) emailCooldown.value--
    else if (emailTimer) { clearInterval(emailTimer); emailTimer = null }
  }, 1000)
}

function startPwdCooldown(seconds: number) {
  pwdCooldown.value = seconds
  if (pwdTimer) clearInterval(pwdTimer)
  pwdTimer = setInterval(() => {
    if (pwdCooldown.value > 0) pwdCooldown.value--
    else if (pwdTimer) { clearInterval(pwdTimer); pwdTimer = null }
  }, 1000)
}

async function sendEmailCode() {
  if (!emailForm.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(emailForm.email)) {
    dlgEmailErrors.email = '请输入有效的邮箱地址'
    return
  }
  if (!profileData.value?.needBindEmail && emailForm.email === profileData.value?.email) {
    dlgEmailErrors.email = '新邮箱不能与当前邮箱相同'
    return
  }

  dlgEmailErrors.email = ''
  emailSending.value = true
  try {
    await authApi.checkEmail(emailForm.email)
  } catch (e: unknown) {
    dlgEmailErrors.email = getErrorMessage(e, '邮箱地址不可用')
    emailSending.value = false
    return
  }
  emailSending.value = false

  openCaptcha(async (verifyToken: string) => {
    emailSending.value = true
    try {
      const scene = profileData.value?.needBindEmail ? 'bind' : 'change-email'
      await authApi.sendCode({ email: emailForm.email, scene }, verifyToken)
      startEmailCooldown(60)
      message.success('验证码已发送')
    } catch (e: unknown) {
      message.error(getErrorMessage(e, '发送失败'))
    } finally { emailSending.value = false }
  })
}

async function submitEmail() {
  if (!emailForm.email || !emailForm.code) {
    if (!emailForm.email) dlgEmailErrors.email = '请输入邮箱'
    if (!emailForm.code) dlgEmailErrors.code = '请输入验证码'
    return
  }

  emailSubmitting.value = true
  try {
    if (profileData.value?.needBindEmail) await userApi.bindEmail({ email: emailForm.email, code: emailForm.code })
    else await userApi.changeEmail({ email: emailForm.email, code: emailForm.code })

    emailDialogVisible.value = false
    const res = await userApi.getProfile()
    profileData.value = res.data
    message.success('邮箱更新成功')
  } catch (e: unknown) {
    message.error(getErrorMessage(e, '操作失败'))
  } finally { emailSubmitting.value = false }
}

async function sendPwdCode() {
  if (!profileData.value?.email) { message.error('请先绑定邮箱'); return }

  openCaptcha(async (verifyToken: string) => {
    pwdSending.value = true
    try {
      await authApi.sendCode({ email: profileData.value!.email!, scene: 'reset-pwd' }, verifyToken)
      startPwdCooldown(60)
      message.success('验证码已发送')
    } catch (e: unknown) {
      message.error(getErrorMessage(e, '发送失败'))
    } finally { pwdSending.value = false }
  })
}

async function submitPassword() {
  let valid = true
  dlgPwdErrors.code = ''
  dlgPwdErrors.password = ''
  dlgPwdErrors.confirmPassword = ''

  if (profileData.value?.hasPassword && (!pwdForm.code || pwdForm.code.length !== 6)) { dlgPwdErrors.code = '请输入 6 位验证码'; valid = false }
  if (!pwdForm.password) { dlgPwdErrors.password = '请输入密码'; valid = false }
  else if (pwdForm.password.length < 8) { dlgPwdErrors.password = '密码至少 8 位'; valid = false }
  else if (!/[a-z]/.test(pwdForm.password) || !/[A-Z]/.test(pwdForm.password)) { dlgPwdErrors.password = '密码需包含大小写字母'; valid = false }
  else if (!/\d/.test(pwdForm.password)) { dlgPwdErrors.password = '密码需包含数字'; valid = false }

  if (pwdForm.password !== pwdForm.confirmPassword) { dlgPwdErrors.confirmPassword = '两次密码不一致'; valid = false }
  if (!valid) return

  pwdSubmitting.value = true
  try {
    if (profileData.value?.hasPassword) await userApi.resetPassword({ code: pwdForm.code, password: pwdForm.password })
    else await userApi.setPassword({ password: pwdForm.password })

    passwordDialogVisible.value = false
    const res = await userApi.getProfile()
    profileData.value = res.data
    message.success('密码更新成功')
  } catch (e: unknown) {
    message.error(getErrorMessage(e, '操作失败'))
  } finally { pwdSubmitting.value = false }
}

function revokeAvatarPreview() {
  if (avatarPreview.value && avatarPreview.value.startsWith('blob:')) {
    URL.revokeObjectURL(avatarPreview.value)
  }
  avatarPreview.value = null
}

watch(currentAvatarSrc, () => {
  avatarImageLoadFailed.value = false
})

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) {
    message.error('图片大小不能超过 5MB')
    input.value = ''
    return
  }
  if (!/^image\/(jpeg|png|webp)$/.test(file.type)) {
    message.error('仅支持 JPG、PNG、WebP 格式')
    input.value = ''
    return
  }

  const reader = new FileReader()
  reader.onload = () => {
    cropperImageSrc.value = reader.result as string
    cropperVisible.value = true
  }
  reader.onerror = () => {
    message.error('读取图片失败，请重试')
  }
  reader.readAsDataURL(file)
  input.value = ''
}

function handleAvatarCropped(payload: { blob: Blob; url: string }) {
  if (!payload.blob) return
  revokeAvatarPreview()
  avatarPreview.value = payload.url
  pendingAvatarUrl.value = null

  const ext = getAvatarFileExtension(payload.blob.type)
  pendingAvatarFile.value = new File([payload.blob], `avatar-${Date.now()}.${ext}`, { type: payload.blob.type || 'image/webp' })
  cropperImageSrc.value = ''
}

async function handleSave() {
  saving.value = true
  try {
    let avatarUrlForSubmit = pendingAvatarUrl.value || undefined

    if (pendingAvatarFile.value) {
      try {
        const uploadRes = await userApi.uploadAvatar(pendingAvatarFile.value)
        avatarUrlForSubmit = uploadRes.data
        pendingAvatarUrl.value = uploadRes.data
        pendingAvatarFile.value = null
        cropperImageSrc.value = ''
        revokeAvatarPreview()
      } catch (e: unknown) {
        message.warning(getErrorMessage(e, '头像上传失败，未提交审核，请重试'))
        return
      }
    }

    await userApi.updateProfile({
      nickname: form.nickname || undefined,
      bio: form.bio || undefined,
      avatar: avatarUrlForSubmit,
    })

    message.success('已提交审核，请等待管理员处理')

    const profileRes = await userApi.getProfile()
    profileData.value = profileRes.data
    syncFormByProfile(profileRes.data)
  } catch (e: unknown) {
    message.error(getErrorMessage(e, '提交失败'))
  } finally { saving.value = false }
}

function syncFormByProfile(profile: UserProfileVO) {
  const usePendingDraft = profile.profileReviewStatus === 'pending' || profile.profileReviewStatus === 'rejected'
  const draftNickname = usePendingDraft ? (profile.pendingNickname ?? profile.nickname) : profile.nickname
  const draftBio = usePendingDraft ? (profile.pendingBio ?? profile.bio) : profile.bio
  const draftAvatar = usePendingDraft ? (profile.pendingAvatar ?? profile.avatar) : profile.avatar

  form.nickname = draftNickname || ''
  form.bio = draftBio || ''
  form.avatar = draftAvatar || profile.avatar || null
  pendingAvatarUrl.value = usePendingDraft ? (profile.pendingAvatar || null) : null
}

onMounted(async () => {
  if (!userStore.isLoggedIn) { useLoginModal().open(); navigateTo('/'); return }
  try {
    const res = await userApi.getProfile()
    profileData.value = res.data
    syncFormByProfile(res.data)
  } catch {
    useLoginModal().open(); navigateTo('/')
  } finally { loading.value = false }
})

onUnmounted(() => {
  pendingAvatarFile.value = null
  pendingAvatarUrl.value = null
  revokeAvatarPreview()
})
</script>

<style scoped lang="scss">
.edit-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
}

.edit-header {
  margin-bottom: 1rem;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  color: $color-text-muted;
  text-decoration: none;
  margin-bottom: 0.5rem;
}

.edit-title {
  margin: 0;
  font-size: var(--layout-page-title-size);
  line-height: 1.2;
  display: flex;
  align-items: center;
  gap: 0.45rem;
}

.edit-subtitle {
  margin-top: 0.4rem;
  font-size: 0.9rem;
  color: $color-text-muted;
}

.edit-form {
  border: 1px solid $color-border;
  border-radius: 14px;
  background: $color-bg;
  padding: 1rem;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.review-status-banner {
  border-radius: 12px;
  padding: 0.8rem 0.95rem;
  margin-bottom: 0.9rem;
  border: 1px solid transparent;
  background: #f8fafc;

  &.is-pending {
    border-color: #facc15;
    background: #fefce8;
  }

  &.is-rejected {
    border-color: #fca5a5;
    background: #fef2f2;
  }

  &.is-approved {
    border-color: #86efac;
    background: #f0fdf4;
  }
}

.review-title {
  margin: 0;
  font-size: 0.88rem;
  font-weight: 600;
}

.review-desc {
  margin-top: 0.25rem;
  margin-bottom: 0;
  font-size: 0.8rem;
  color: $color-text-muted;
}

.review-reason {
  margin-top: 0.35rem;
  margin-bottom: 0;
  font-size: 0.8rem;
  color: #b91c1c;
}

.form-section {
  margin-bottom: 1.3rem;
  position: relative;
}

.form-label {
  display: block;
  margin-bottom: 0.45rem;
  font-weight: 600;
}

.form-input,
.form-textarea {
  width: 100%;
  padding: 0.62rem 0.85rem;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: $color-bg-secondary;
}

.form-textarea {
  min-height: 90px;
  resize: vertical;
}

.char-count {
  position: absolute;
  right: 0;
  bottom: -1.1rem;
  font-size: 0.75rem;
  color: $color-text-muted;
}

.avatar-edit {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 0.85rem;
  border: 1px dashed rgba(148, 163, 184, 0.45);
  border-radius: 12px;
  background: rgba(248, 250, 252, 0.78);
}

.avatar-preview {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;
}

.avatar-img {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 80px;
  height: 80px;
  border-radius: 50%;
  background: $color-primary;
  color: #fff;
  font-size: 1.6rem;
}

.avatar-actions {
  flex: 1;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.45rem 0.8rem;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  cursor: pointer;
}

.hidden-input {
  display: none;
}

.avatar-hint {
  margin-top: 0.32rem;
  font-size: 0.76rem;
  color: $color-text-muted;
}

.avatar-review-hint {
  margin-top: 0.25rem;
  font-size: 0.76rem;
  color: #64748b;
}

.avatar-pending-tip {
  margin-top: 0.32rem;
  font-size: 0.78rem;
  color: #0f766e;
}

.dialog-actions,
.form-actions {
  display: flex;
  gap: 0.6rem;
  margin-top: 0.6rem;
  justify-content: flex-end;
}

.btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.3rem;
  padding: 0.5rem 1rem;
  min-height: 40px;
  border-radius: $radius-md;
  border: none;
  cursor: pointer;
}

.btn-primary {
  background: $color-primary;
  color: #fff;
}

.btn-secondary {
  background: transparent;
  border: 1px solid $color-border;
  color: $color-text;
}

.btn-sm {
  min-height: 36px;
  padding: 0.35rem 0.7rem;
}

.loading-state {
  display: flex;
  justify-content: center;
  padding: 3rem;
}

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

.security-section {
  margin-top: 1.2rem;
}

.section-title {
  margin: 0 0 0.75rem;
  font-size: 1.05rem;
}

.security-card {
  border: 1px solid $color-border;
  border-radius: 12px;
  padding: 0.9rem 1rem;
  margin-bottom: 0.7rem;
  background: $color-bg;
}

.security-header {
  display: flex;
  align-items: center;
  gap: 0.7rem;
}

.security-header > div {
  flex: 1;
}

.security-label {
  margin: 0;
  font-weight: 600;
}

.security-value {
  margin: 0.15rem 0 0;
  font-size: 0.8rem;
  color: $color-text-muted;
}

.dialog-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
}

.dialog-card {
  width: 100%;
  max-width: 420px;
  background: $color-bg;
  border-radius: $radius-lg;
  padding: 1.1rem;
}

.dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.7rem;
}

.dialog-title {
  margin: 0;
}

.dialog-close {
  border: none;
  background: none;
  cursor: pointer;
}

.dialog-field {
  margin-bottom: 0.4rem;
}

.dialog-field label {
  display: block;
  margin-bottom: 0.25rem;
  font-size: 0.85rem;
}

.code-row {
  display: flex;
  gap: 0.5rem;
}

.code-input-flex {
  flex: 1;
}

.input-wrapper {
  position: relative;
  display: flex;
  align-items: center;
}

.input-wrapper .form-input {
  padding-right: 2.25rem;
}

.input-wrapper .form-input.input-with-actions {
  padding-right: 3.8rem;
}

.field-clear,
.field-toggle {
  position: absolute;
  right: 0.45rem;
  border: none;
  background: none;
  cursor: pointer;
  display: flex;
}

.field-clear.with-toggle {
  right: 2rem;
}

.email-field-wrap {
  position: relative;
}

.dlg-email-suggestions {
  position: absolute;
  z-index: 10;
  left: 0;
  right: 0;
  top: calc(100% - 0.9rem);
  list-style: none;
  margin: 0;
  padding: 0.2rem 0;
  border: 1px solid $color-border;
  border-radius: $radius-md;
  background: $color-bg;
  max-height: 180px;
  overflow-y: auto;
}

.dlg-email-suggestions li {
  padding: 0.45rem 0.7rem;
  cursor: pointer;
}

.field-error {
  display: block;
  min-height: 1rem;
  font-size: 0.75rem;
  color: #ef4444;
  visibility: hidden;
}

.field-error.visible {
  visibility: visible;
}

.password-strength {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.25rem;
}

.strength-bars {
  display: flex;
  gap: 4px;
}

.bar {
  width: 2rem;
  height: 4px;
  border-radius: 2px;
  background: #e2e8f0;
}

.strength-text {
  font-size: 0.75rem;
}

@media (max-width: $breakpoint-md) {
  .avatar-edit {
    flex-direction: column;
    align-items: flex-start;
  }

  .security-header {
    flex-wrap: wrap;
  }
}
</style>
