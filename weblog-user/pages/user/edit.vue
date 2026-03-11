<template>
  <div class="edit-page">
    <div class="edit-header">
      <NuxtLink to="/user" class="back-link">
        <Icon name="heroicons:arrow-left-20-solid" size="18" />
        返回个人中心
      </NuxtLink>
      <h1 class="edit-title">编辑资料</h1>
    </div>

    <div v-if="loading" class="loading-state">
      <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
    </div>

    <form v-else class="edit-form" @submit.prevent="handleSave">
      <div class="form-section">
        <label class="form-label">头像</label>
        <div class="avatar-edit">
          <div class="avatar-preview">
            <img v-if="avatarPreview || form.avatar" :src="avatarPreview || form.avatar!" alt="头像" class="avatar-img" />
            <span v-else class="avatar-placeholder">{{ (form.nickname || 'U').charAt(0) }}</span>
          </div>
          <div class="avatar-actions">
            <label class="upload-btn" for="avatar-input">
              <Icon name="heroicons:camera-16-solid" size="16" />
              选择图片
            </label>
            <input id="avatar-input" type="file" accept="image/jpeg,image/png,image/webp" class="hidden-input" @change="handleFileSelect" />
            <p class="avatar-hint">支持 JPG、PNG、WebP，最大 5MB</p>
          </div>
        </div>

        <div v-if="cropperVisible" class="cropper-area">
          <div class="cropper-container">
            <canvas ref="cropCanvas" class="crop-canvas" @mousedown="startCrop" @mousemove="doCrop" @mouseup="endCrop" @touchstart.prevent="startCropTouch" @touchmove.prevent="doCropTouch" @touchend="endCrop" />
          </div>
          <div class="cropper-actions">
            <button type="button" class="btn btn-secondary" @click="cancelCrop">取消</button>
            <button type="button" class="btn btn-primary" @click="confirmCrop">确认裁剪</button>
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
          {{ saving ? '保存中...' : '保存修改' }}
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
const cropperVisible = ref(false)
const cropCanvas = ref<HTMLCanvasElement | null>(null)
const form = reactive({ nickname: '', bio: '', avatar: null as string | null })

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
  } catch (e: any) {
    dlgEmailErrors.email = e.message || '邮箱地址不可用'
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
    } catch (e: any) {
      message.error(e.message || '发送失败')
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
  } catch (e: any) {
    message.error(e.message || '操作失败')
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
    } catch (e: any) {
      message.error(e.message || '发送失败')
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
  } catch (e: any) {
    message.error(e.message || '操作失败')
  } finally { pwdSubmitting.value = false }
}

let originalImage: HTMLImageElement | null = null
let cropFile: File | null = null
let cropping = false
let cropStartX = 0
let cropStartY = 0
let cropRect = { x: 0, y: 0, size: 0 }
let canvasScale = 1

function handleFileSelect(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  if (file.size > 5 * 1024 * 1024) { message.error('图片大小不能超过 5MB'); return }

  const reader = new FileReader()
  reader.onload = () => {
    const img = new Image()
    img.onload = () => { originalImage = img; cropperVisible.value = true; nextTick(() => drawCropper()) }
    img.src = reader.result as string
  }
  reader.readAsDataURL(file)
  input.value = ''
}
function drawCropper() {
  if (!cropCanvas.value || !originalImage) return
  const canvas = cropCanvas.value
  const maxW = Math.min(400, window.innerWidth - 48)
  canvasScale = maxW / originalImage.width
  canvas.width = maxW
  canvas.height = originalImage.height * canvasScale
  const minDim = Math.min(canvas.width, canvas.height)
  cropRect = { x: (canvas.width - minDim * 0.8) / 2, y: (canvas.height - minDim * 0.8) / 2, size: minDim * 0.8 }
  renderCropper()
}

function renderCropper() {
  const canvas = cropCanvas.value!
  const ctx = canvas.getContext('2d')!
  ctx.clearRect(0, 0, canvas.width, canvas.height)
  ctx.drawImage(originalImage!, 0, 0, canvas.width, canvas.height)
  ctx.fillStyle = 'rgba(0,0,0,0.5)'
  ctx.fillRect(0, 0, canvas.width, canvas.height)
  ctx.save(); ctx.beginPath(); ctx.arc(cropRect.x + cropRect.size / 2, cropRect.y + cropRect.size / 2, cropRect.size / 2, 0, Math.PI * 2); ctx.clip()
  ctx.drawImage(originalImage!, 0, 0, canvas.width, canvas.height)
  ctx.restore(); ctx.strokeStyle = '#3b82f6'; ctx.lineWidth = 2
  ctx.beginPath(); ctx.arc(cropRect.x + cropRect.size / 2, cropRect.y + cropRect.size / 2, cropRect.size / 2, 0, Math.PI * 2); ctx.stroke()
}

function getPos(e: MouseEvent | Touch) {
  const r = cropCanvas.value!.getBoundingClientRect()
  return { x: e.clientX - r.left, y: e.clientY - r.top }
}
function startCrop(e: MouseEvent) { const p = getPos(e); cropStartX = p.x - cropRect.x; cropStartY = p.y - cropRect.y; cropping = true }
function startCropTouch(e: TouchEvent) { const p = getPos(e.touches[0]!); cropStartX = p.x - cropRect.x; cropStartY = p.y - cropRect.y; cropping = true }
function doCrop(e: MouseEvent) { if (!cropping) return; moveCrop(getPos(e)) }
function doCropTouch(e: TouchEvent) { if (!cropping) return; moveCrop(getPos(e.touches[0]!)) }
function moveCrop(pos: { x: number; y: number }) {
  const c = cropCanvas.value!
  cropRect.x = Math.max(0, Math.min(c.width - cropRect.size, pos.x - cropStartX))
  cropRect.y = Math.max(0, Math.min(c.height - cropRect.size, pos.y - cropStartY))
  renderCropper()
}
function endCrop() { cropping = false }
function cancelCrop() { cropperVisible.value = false; originalImage = null }

async function confirmCrop() {
  if (!originalImage) return
  const out = document.createElement('canvas')
  out.width = 200; out.height = 200
  const ctx = out.getContext('2d')!
  const sx = cropRect.x / canvasScale; const sy = cropRect.y / canvasScale; const sSize = cropRect.size / canvasScale
  ctx.beginPath(); ctx.arc(100, 100, 100, 0, Math.PI * 2); ctx.clip()
  ctx.drawImage(originalImage, sx, sy, sSize, sSize, 0, 0, 200, 200)
  out.toBlob(async (blob) => {
    if (!blob) return
    cropFile = new File([blob], 'avatar.webp', { type: 'image/webp' })
    avatarPreview.value = URL.createObjectURL(blob)
    cropperVisible.value = false
    try {
      saving.value = true
      const res = await userApi.uploadAvatar(cropFile)
      form.avatar = res.data
      userStore.updateUserInfo({ avatar: res.data })
      avatarPreview.value = null
      message.success('头像上传成功')
    } catch (e: any) {
      message.error(e.message || '头像上传失败')
    } finally { saving.value = false }
  }, 'image/webp')
}

async function handleSave() {
  saving.value = true
  try {
    await userApi.updateProfile({ nickname: form.nickname || undefined, bio: form.bio || undefined })
    userStore.updateUserInfo({ nickname: form.nickname || '' })
    message.success('保存成功')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  } finally { saving.value = false }
}

onMounted(async () => {
  if (!userStore.isLoggedIn) { useLoginModal().open(); navigateTo('/'); return }
  try {
    const res = await userApi.getProfile()
    profileData.value = res.data
    form.nickname = res.data.nickname || ''
    form.bio = res.data.bio || ''
    form.avatar = res.data.avatar
  } catch {
    useLoginModal().open(); navigateTo('/')
  } finally { loading.value = false }
})
</script>

<style scoped lang="scss">
.edit-page { max-width: var(--layout-max-width); margin: 0 auto; padding: var(--layout-page-padding-y) var(--layout-page-padding-x); }
.edit-header { margin-bottom: 1.2rem; }
.back-link { display: inline-flex; align-items: center; gap: .35rem; color: $color-text-muted; text-decoration: none; margin-bottom: .5rem; }
.edit-title { margin: 0; font-size: 1.35rem; }
.form-section { margin-bottom: 1.2rem; position: relative; }
.form-label { display: block; margin-bottom: .45rem; font-weight: 600; }
.form-input, .form-textarea { width: 100%; padding: .6rem .85rem; border: 1px solid $color-border; border-radius: $radius-md; background: $color-bg-secondary; }
.form-textarea { min-height: 80px; resize: vertical; }
.char-count { position: absolute; right: 0; bottom: -1.1rem; font-size: .75rem; color: $color-text-muted; }
.avatar-edit { display: flex; align-items: center; gap: 1rem; }
.avatar-img { width: 72px; height: 72px; border-radius: 50%; object-fit: cover; }
.avatar-placeholder { display: flex; align-items: center; justify-content: center; width: 72px; height: 72px; border-radius: 50%; background: $color-primary; color: #fff; font-size: 1.6rem; }
.upload-btn { display: inline-flex; align-items: center; gap: .35rem; padding: .45rem .8rem; border: 1px solid $color-border; border-radius: $radius-md; cursor: pointer; }
.hidden-input { display: none; }
.avatar-hint { margin-top: .3rem; font-size: .75rem; color: $color-text-muted; }
.cropper-container { display: flex; justify-content: center; }
.crop-canvas { max-width: 100%; border-radius: $radius-md; cursor: move; }
.cropper-actions, .dialog-actions { display: flex; gap: .6rem; margin-top: .6rem; justify-content: flex-end; }
.btn { display: inline-flex; align-items: center; justify-content: center; gap: .3rem; padding: .5rem 1rem; min-height: 40px; border-radius: $radius-md; border: none; cursor: pointer; }
.btn-primary { background: $color-primary; color: #fff; }
.btn-secondary { background: transparent; border: 1px solid $color-border; color: $color-text; }
.btn-sm { min-height: 36px; padding: .35rem .7rem; }
.loading-state { display: flex; justify-content: center; padding: 3rem; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.security-section { margin-top: 1.8rem; }
.section-title { margin: 0 0 .8rem; }
.security-card { border: 1px solid $color-border; border-radius: $radius-md; padding: .9rem 1rem; margin-bottom: .7rem; }
.security-header { display: flex; align-items: center; gap: .7rem; }
.security-header > div { flex: 1; }
.security-label { margin: 0; font-weight: 600; }
.security-value { margin: .15rem 0 0; font-size: .8rem; color: $color-text-muted; }
.dialog-overlay { position: fixed; inset: 0; z-index: 100; background: rgba(0,0,0,.4); display: flex; align-items: center; justify-content: center; padding: 1rem; }
.dialog-card { width: 100%; max-width: 420px; background: $color-bg; border-radius: $radius-lg; padding: 1.1rem; }
.dialog-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: .7rem; }
.dialog-title { margin: 0; }
.dialog-close { border: none; background: none; cursor: pointer; }
.dialog-field { margin-bottom: .4rem; }
.dialog-field label { display: block; margin-bottom: .25rem; font-size: .85rem; }
.code-row { display: flex; gap: .5rem; }
.code-input-flex { flex: 1; }
.input-wrapper { position: relative; display: flex; align-items: center; }
.input-wrapper .form-input { padding-right: 2.25rem; }
.input-wrapper .form-input.input-with-actions { padding-right: 3.8rem; }
.field-clear, .field-toggle { position: absolute; right: .45rem; border: none; background: none; cursor: pointer; display: flex; }
.field-clear.with-toggle { right: 2rem; }
.email-field-wrap { position: relative; }
.dlg-email-suggestions { position: absolute; z-index: 10; left: 0; right: 0; top: calc(100% - .9rem); list-style: none; margin: 0; padding: .2rem 0; border: 1px solid $color-border; border-radius: $radius-md; background: $color-bg; max-height: 180px; overflow-y: auto; }
.dlg-email-suggestions li { padding: .45rem .7rem; cursor: pointer; }
.field-error { display: block; min-height: 1rem; font-size: .75rem; color: #ef4444; visibility: hidden; }
.field-error.visible { visibility: visible; }
.password-strength { display: flex; align-items: center; gap: .4rem; margin-top: .25rem; }
.strength-bars { display: flex; gap: 4px; }
.bar { width: 2rem; height: 4px; border-radius: 2px; background: #e2e8f0; }
.strength-text { font-size: .75rem; }
@media (max-width: $breakpoint-md) { .edit-page { padding: var(--layout-page-padding-y) var(--layout-page-padding-x); } }
</style>
