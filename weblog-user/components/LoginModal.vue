<template>
  <BaseModal :visible="modalVisible" :show-close="true" :mask-closable="true" width="420px"
    @update:visible="v => { if (!v) loginModal.close() }" @close="loginModal.close()">
    <template #header>
      <div class="modal-logo">
        <img
          src="/brand/logo.png"
          :alt="`${siteName} logo`"
          class="modal-logo-img"
          width="28"
          height="28"
          style="width:28px;height:28px;max-width:28px;display:block;object-fit:cover;"
        >
        <span>{{ siteName }}</span>
      </div>
    </template>

    <div ref="contentRef" class="login-modal-body">
      <Transition name="fade-slide" mode="out-in">
        <p class="subtitle" :key="modeTitle">{{ modeTitle }}</p>
      </Transition>

      <!-- 登录模式切换 Tab -->
      <div v-show="mode !== 'register' && mode !== 'reset'" class="login-tabs">
        <button :class="{ active: mode === 'code' }" @click="switchMode('code')">验证码登录</button>
        <button :class="{ active: mode === 'password' }" @click="switchMode('password')">密码登录</button>
        <span class="tab-indicator" :class="{ 'at-right': mode === 'password' }" />
      </div>

      <form v-show="mode !== 'reset'" @submit.prevent="handleSubmit" class="auth-form">
        <!-- 隐藏的 dummy input，吸收 Chrome 对注册表单的自动填充 -->
        <template v-if="mode === 'register'">
          <input type="email" autocomplete="username" aria-hidden="true" tabindex="-1" style="position:absolute;width:0;height:0;overflow:hidden;opacity:0;pointer-events:none" />
          <input type="password" autocomplete="current-password" aria-hidden="true" tabindex="-1" style="position:absolute;width:0;height:0;overflow:hidden;opacity:0;pointer-events:none" />
        </template>

        <!-- 昵称（注册时） -->
        <div v-show="mode === 'register'" class="form-group">
          <label for="lm-nickname">昵称</label>
          <div class="input-wrapper" @mouseenter="nicknameHover = true" @mouseleave="nicknameHover = false">
            <Icon name="heroicons:user-16-solid" size="18" class="input-icon" />
            <input id="lm-nickname" v-model="form.nickname" type="text" placeholder="请输入昵称" maxlength="20" autocomplete="off" />
            <button v-show="nicknameHover && form.nickname" type="button" class="clear-btn" @mousedown.prevent="clearField('nickname')" tabindex="-1">
              <Icon name="heroicons:x-circle-16-solid" size="16" />
            </button>
          </div>
        </div>

        <!-- 邮箱 -->
        <div class="form-group email-group">
          <label for="lm-email">邮箱</label>
          <div class="input-wrapper" @mouseenter="emailHover = true" @mouseleave="emailHover = false">
            <Icon name="heroicons:envelope-16-solid" size="18" class="input-icon" />
            <input id="lm-email" v-model="form.email" type="email" placeholder="请输入邮箱" required :autocomplete="mode === 'register' ? 'off' : 'email'" @input="onEmailInput" @blur="onEmailBlur" @focus="onEmailFocus" />
            <button v-show="emailHover && form.email" type="button" class="clear-btn" @mousedown.prevent="clearField('email')" tabindex="-1">
              <Icon name="heroicons:x-circle-16-solid" size="16" />
            </button>
          </div>
          <ClientOnly>
            <ul v-show="showEmailSuggestions && emailSuggestions.length" class="email-suggestions">
              <li v-for="s in emailSuggestions" :key="s" @mousedown.prevent="selectEmailSuggestion(s)">{{ s }}</li>
            </ul>
            <ul v-show="showRecentEmails && filteredRecentEmails.length" class="email-suggestions recent-emails">
              <li class="recent-label">最近使用</li>
              <li v-for="e in filteredRecentEmails" :key="e" class="recent-item" @mousedown.prevent="selectRecentEmail(e)">
                <span class="recent-email-text">{{ e }}</span>
                <button type="button" class="recent-remove" @mousedown.prevent.stop="removeRecentEmail(e)" tabindex="-1">
                  <Icon name="heroicons:x-mark-16-solid" size="14" />
                </button>
              </li>
            </ul>
          </ClientOnly>
          <span class="error-text" :class="{ visible: errors.email }">{{ errors.email || '&nbsp;' }}</span>
        </div>

        <!-- 验证码/密码切换区域 -->
        <div v-show="mode !== 'register'" class="toggle-fields">
          <div class="toggle-field" :class="{ 'is-hidden': mode === 'password' }">
            <div class="form-group">
              <label>验证码</label>
              <div class="code-row">
                <div class="input-wrapper code-input-wrapper" @mouseenter="codeHover = true" @mouseleave="codeHover = false">
                  <Icon name="heroicons:shield-check-16-solid" size="18" class="input-icon" />
                  <input v-model="form.code" type="text" placeholder="请输入验证码" maxlength="6" autocomplete="off" inputmode="numeric" :tabindex="mode === 'password' ? -1 : undefined" />
                  <button v-show="codeHover && form.code" type="button" class="clear-btn" @mousedown.prevent="clearField('code')" tabindex="-1">
                    <Icon name="heroicons:x-circle-16-solid" size="16" />
                  </button>
                </div>
                <button type="button" class="send-code-btn" :disabled="codeCooldown > 0 || sendingCode" @click="handleSendCode" :tabindex="mode === 'password' ? -1 : undefined">
                  <Icon v-if="sendingCode" name="heroicons:arrow-path-16-solid" size="14" class="spin" />
                  {{ codeCooldown > 0 ? `${codeCooldown}s` : '获取验证码' }}
                </button>
              </div>
              <span class="error-text" :class="{ visible: errors.code }">{{ errors.code || '&nbsp;' }}</span>
            </div>
          </div>
          <div class="toggle-field" :class="{ 'is-hidden': mode === 'code' }">
            <div class="form-group">
              <label for="lm-password">密码</label>
              <div class="input-wrapper" @mouseenter="pwdHover = true" @mouseleave="pwdHover = false">
                <Icon name="heroicons:lock-closed-16-solid" size="18" class="input-icon" />
                <input id="lm-password" ref="pwdInputRef" v-model="form.password" :type="showPassword ? 'text' : 'password'" placeholder="请输入密码" autocomplete="current-password" class="no-browser-eye" :tabindex="mode === 'code' ? -1 : undefined" @keyup.enter="handleSubmit" @blur="validateField('password')" />
                <button v-show="pwdHover && form.password" type="button" class="clear-btn with-toggle" @mousedown.prevent="clearField('password')" tabindex="-1">
                  <Icon name="heroicons:x-circle-16-solid" size="16" />
                </button>
                <button v-show="pwdHover && form.password" type="button" class="toggle-pwd" @mousedown.prevent="togglePwd" tabindex="-1">
                  <Icon :name="showPassword ? 'heroicons:eye-slash-16-solid' : 'heroicons:eye-16-solid'" size="18" />
                </button>
              </div>
              <span class="error-text" :class="{ visible: errors.password }">{{ errors.password || '&nbsp;' }}</span>
            </div>
          </div>
        </div>

        <!-- 注册模式下的验证码和密码 -->
        <template v-if="mode === 'register'">
          <div class="form-group">
            <label>验证码</label>
            <div class="code-row">
              <div class="input-wrapper code-input-wrapper" @mouseenter="codeHover = true" @mouseleave="codeHover = false">
                <Icon name="heroicons:shield-check-16-solid" size="18" class="input-icon" />
                <input v-model="form.code" type="text" placeholder="请输入验证码" maxlength="6" autocomplete="off" inputmode="numeric" />
                <button v-show="codeHover && form.code" type="button" class="clear-btn" @mousedown.prevent="clearField('code')" tabindex="-1">
                  <Icon name="heroicons:x-circle-16-solid" size="16" />
                </button>
              </div>
              <button type="button" class="send-code-btn" :disabled="codeCooldown > 0 || sendingCode" @click="handleSendCode">
                <Icon v-if="sendingCode" name="heroicons:arrow-path-16-solid" size="14" class="spin" />
                {{ codeCooldown > 0 ? `${codeCooldown}s` : '获取验证码' }}
              </button>
            </div>
            <span class="error-text" :class="{ visible: errors.code }">{{ errors.code || '&nbsp;' }}</span>
          </div>

          <div class="form-group">
            <label for="lm-reg-password">密码</label>
            <div class="input-wrapper" @mouseenter="pwdHover = true" @mouseleave="pwdHover = false">
              <Icon name="heroicons:lock-closed-16-solid" size="18" class="input-icon" />
              <input id="lm-reg-password" ref="pwdInputRef" v-model="form.password" :type="showPassword ? 'text' : 'password'" placeholder="至少8位，含大小写字母和数字" autocomplete="new-password" class="no-browser-eye" @keyup.enter="handleSubmit" @blur="validateField('password')" />
              <button v-show="pwdHover && form.password" type="button" class="clear-btn with-toggle" @mousedown.prevent="clearField('password')" tabindex="-1">
                <Icon name="heroicons:x-circle-16-solid" size="16" />
              </button>
              <button v-show="pwdHover && form.password" type="button" class="toggle-pwd" @mousedown.prevent="togglePwd" tabindex="-1">
                <Icon :name="showPassword ? 'heroicons:eye-slash-16-solid' : 'heroicons:eye-16-solid'" size="18" />
              </button>
            </div>
            <div v-show="form.password" class="password-strength">
              <div class="strength-bars">
                <span v-for="i in 4" :key="i" class="bar" :class="{ active: i <= pwdLevel }" :style="{ backgroundColor: i <= pwdLevel ? pwdColor : undefined }" />
              </div>
              <span class="strength-text" :style="{ color: pwdColor }">{{ pwdLabel }}</span>
            </div>
            <span class="error-text" :class="{ visible: errors.password }">{{ errors.password || '&nbsp;' }}</span>
          </div>
        </template>

        <!-- 确认密码（注册时） -->
        <div v-show="mode === 'register'" class="form-group">
          <label for="lm-confirmPassword">确认密码</label>
          <div class="input-wrapper" @mouseenter="confirmPwdHover = true" @mouseleave="confirmPwdHover = false">
            <Icon name="heroicons:lock-closed-16-solid" size="18" class="input-icon" />
            <input id="lm-confirmPassword" ref="confirmPwdInputRef" v-model="form.confirmPassword" :type="showConfirmPassword ? 'text' : 'password'" placeholder="请再次输入密码" autocomplete="new-password" class="no-browser-eye" @blur="validateField('confirmPassword')" />
            <button v-show="confirmPwdHover && form.confirmPassword" type="button" class="clear-btn with-toggle" @mousedown.prevent="clearField('confirmPassword')" tabindex="-1">
              <Icon name="heroicons:x-circle-16-solid" size="16" />
            </button>
            <button v-show="confirmPwdHover && form.confirmPassword" type="button" class="toggle-pwd" @mousedown.prevent="toggleConfirmPwd" tabindex="-1">
              <Icon :name="showConfirmPassword ? 'heroicons:eye-slash-16-solid' : 'heroicons:eye-16-solid'" size="18" />
            </button>
          </div>
          <span class="error-text" :class="{ visible: errors.confirmPassword }">{{ errors.confirmPassword || '&nbsp;' }}</span>
        </div>

        <!-- 记住我 / 验证码提示 -->
        <div v-show="mode !== 'register'" class="toggle-hints">
          <p class="code-hint" :class="{ 'is-hidden': mode !== 'code' }">首次通过验证码登录将自动创建账号</p>
          <div class="remember-row" :class="{ 'is-hidden': mode !== 'password' }">
            <label class="remember-me">
              <input type="checkbox" v-model="rememberMe" :tabindex="mode !== 'password' ? -1 : undefined" />
              <span>记住我</span>
            </label>
            <button type="button" class="forgot-pwd-btn" @click="enterResetMode" :tabindex="mode !== 'password' ? -1 : undefined">忘记密码？</button>
          </div>
        </div>

        <button type="submit" class="submit-btn" :class="{ 'is-success': submitSuccess }" :disabled="submitting || accountLocked">
          <Icon v-if="submitting" name="heroicons:arrow-path-16-solid" size="18" class="spin" />
          <Icon v-else-if="submitSuccess" name="heroicons:check-16-solid" size="18" />
          {{ submitBtnText }}
        </button>
      </form>

      <!-- 重置密码表单 -->
      <form v-show="mode === 'reset'" @submit.prevent="handleResetSubmit" class="auth-form">
        <div class="form-group email-group">
          <label>邮箱</label>
          <div class="input-wrapper" @mouseenter="resetEmailHover = true" @mouseleave="resetEmailHover = false">
            <Icon name="heroicons:envelope-16-solid" size="18" class="input-icon" />
            <input v-model="resetForm.email" type="email" placeholder="请输入注册邮箱" autocomplete="off" @input="onResetEmailInput" @blur="onResetEmailBlur" @focus="onResetEmailInput" />
            <button v-show="resetEmailHover && resetForm.email" type="button" class="clear-btn" @mousedown.prevent="clearResetField('email')" tabindex="-1">
              <Icon name="heroicons:x-circle-16-solid" size="16" />
            </button>
          </div>
          <ClientOnly>
            <ul v-show="showResetEmailSuggestions && resetEmailSuggestions.length" class="email-suggestions">
              <li v-for="s in resetEmailSuggestions" :key="s" @mousedown.prevent="selectResetEmailSuggestion(s)">{{ s }}</li>
            </ul>
          </ClientOnly>
          <span class="error-text" :class="{ visible: resetErrors.email }">{{ resetErrors.email || '&nbsp;' }}</span>
        </div>

        <div class="form-group">
          <label>验证码</label>
          <div class="code-row">
            <div class="input-wrapper code-input-wrapper" @mouseenter="resetCodeHover = true" @mouseleave="resetCodeHover = false">
              <Icon name="heroicons:shield-check-16-solid" size="18" class="input-icon" />
              <input v-model="resetForm.code" type="text" placeholder="请输入验证码" maxlength="6" autocomplete="off" inputmode="numeric" />
              <button v-show="resetCodeHover && resetForm.code" type="button" class="clear-btn" @mousedown.prevent="clearResetField('code')" tabindex="-1">
                <Icon name="heroicons:x-circle-16-solid" size="16" />
              </button>
            </div>
            <button type="button" class="send-code-btn" :disabled="resetCooldown > 0 || resetSendingCode" @click="handleResetSendCode">
              <Icon v-if="resetSendingCode" name="heroicons:arrow-path-16-solid" size="14" class="spin" />
              {{ resetCooldown > 0 ? `${resetCooldown}s` : '获取验证码' }}
            </button>
          </div>
          <span class="error-text" :class="{ visible: resetErrors.code }">{{ resetErrors.code || '&nbsp;' }}</span>
        </div>

        <div class="form-group">
          <label>新密码</label>
          <div class="input-wrapper" @mouseenter="resetPwdHover = true" @mouseleave="resetPwdHover = false">
            <Icon name="heroicons:lock-closed-16-solid" size="18" class="input-icon" />
            <input ref="resetPwdInputRef" v-model="resetForm.password" :type="showResetPassword ? 'text' : 'password'" placeholder="至少8位，含大小写字母和数字" autocomplete="new-password" class="no-browser-eye" />
            <button v-show="resetPwdHover && resetForm.password" type="button" class="clear-btn with-toggle" @mousedown.prevent="clearResetField('password')" tabindex="-1">
              <Icon name="heroicons:x-circle-16-solid" size="16" />
            </button>
            <button v-show="resetPwdHover && resetForm.password" type="button" class="toggle-pwd" @mousedown.prevent="toggleResetPwd" tabindex="-1">
              <Icon :name="showResetPassword ? 'heroicons:eye-slash-16-solid' : 'heroicons:eye-16-solid'" size="18" />
            </button>
          </div>
          <div v-show="resetForm.password" class="password-strength">
            <div class="strength-bars">
              <span v-for="i in 4" :key="i" class="bar" :class="{ active: i <= resetPwdLevel }" :style="{ backgroundColor: i <= resetPwdLevel ? resetPwdColor : undefined }" />
            </div>
            <span class="strength-text" :style="{ color: resetPwdColor }">{{ resetPwdLabel }}</span>
          </div>
          <span class="error-text" :class="{ visible: resetErrors.password }">{{ resetErrors.password || '&nbsp;' }}</span>
        </div>

        <div class="form-group">
          <label>确认新密码</label>
          <div class="input-wrapper" @mouseenter="resetConfirmPwdHover = true" @mouseleave="resetConfirmPwdHover = false">
            <Icon name="heroicons:lock-closed-16-solid" size="18" class="input-icon" />
            <input ref="resetConfirmPwdInputRef" v-model="resetForm.confirmPassword" :type="showResetConfirmPassword ? 'text' : 'password'" placeholder="请再次输入新密码" autocomplete="new-password" class="no-browser-eye" />
            <button v-show="resetConfirmPwdHover && resetForm.confirmPassword" type="button" class="clear-btn with-toggle" @mousedown.prevent="clearResetField('confirmPassword')" tabindex="-1">
              <Icon name="heroicons:x-circle-16-solid" size="16" />
            </button>
            <button v-show="resetConfirmPwdHover && resetForm.confirmPassword" type="button" class="toggle-pwd" @mousedown.prevent="toggleResetConfirmPwd" tabindex="-1">
              <Icon :name="showResetConfirmPassword ? 'heroicons:eye-slash-16-solid' : 'heroicons:eye-16-solid'" size="18" />
            </button>
          </div>
          <span class="error-text" :class="{ visible: resetErrors.confirmPassword }">{{ resetErrors.confirmPassword || '&nbsp;' }}</span>
        </div>

        <button type="submit" class="submit-btn" :disabled="resetSubmitting">
          <Icon v-if="resetSubmitting" name="heroicons:arrow-path-16-solid" size="18" class="spin" />
          重置密码
        </button>
      </form>

      <div v-show="mode !== 'register' && mode !== 'reset'" class="divider"><span>或</span></div>

      <button v-show="mode !== 'register' && mode !== 'reset'" type="button" class="github-btn" :disabled="githubLoading" @click="handleGithubLogin">
        <Icon v-if="githubLoading" name="heroicons:arrow-path-16-solid" size="18" class="spin" />
        <Icon v-else name="simple-icons:github" size="20" />
        {{ githubLoading ? '跳转中...' : '使用 GitHub 登录' }}
      </button>

      <p class="switch-mode">
        <template v-if="mode === 'reset'">
          <button type="button" class="link-btn" @click="exitResetMode">返回登录</button>
        </template>
        <template v-else>
          {{ mode === 'register' ? '已有账号？' : '还没有账号？' }}
          <button type="button" class="link-btn" @click="toggleRegister">
            {{ mode === 'register' ? '去登录' : '立即注册' }}
          </button>
        </template>
      </p>
    </div>

    <!-- 滑块验证码 -->
    <SliderCaptcha v-model:visible="captchaVisible" @success="onCaptchaSuccess" />
  </BaseModal>
</template>

<script setup lang="ts">
import { authApi } from '~/api/auth'

// 滑块验证码
const { visible: captchaVisible, open: openCaptcha, handleSuccess: onCaptchaSuccess } = useSliderCaptcha()

// 动态导入 crypto-js，避免打进首屏 bundle
let _AES: any = null
let _Utf8: any = null
async function getCrypto() {
  if (!_AES || !_Utf8) {
    const [aes, utf8] = await Promise.all([import('crypto-js/aes'), import('crypto-js/enc-utf8')])
    _AES = aes.default || aes; _Utf8 = utf8.default || utf8
  }
  return { AES: _AES, Utf8: _Utf8 }
}
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'
import type { LoginModalMode } from '~/composables/useLoginModal'
import { lockScroll, unlockScroll } from '~/composables/useScrollLock'
import BaseModal from '~/components/ui/modal/BaseModal.vue'
import { saveNavContext } from '~/utils/navContext'
import { normalizeSafeHref } from '~/utils/urlSafety'

const loginModal = useLoginModal()
const message = useMessage()
const siteConfig = useSiteConfigState()
const siteName = computed(() => siteConfig.value.siteName || DEFAULT_SITE_NAME)

// 解构 ref 以便模板中正确响应
const modalVisible = computed(() => loginModal.visible.value)

const REMEMBER_KEY = 'weblog_user_remember'
const COOLDOWN_KEY_PREFIX = 'weblog_code_cooldown_'
const EMAIL_KEY = 'weblog_login_email'
const RECENT_EMAILS_KEY = 'weblog_recent_emails'
const ACCOUNT_LOCKED_CODE = 40103
const GITHUB_OAUTH_HOSTS = new Set(['github.com', 'www.github.com'])

const userStore = useUserStore()

const mode = ref<LoginModalMode>('code')
const showPassword = ref(false)
const showConfirmPassword = ref(false)
const submitting = ref(false)
const accountLocked = ref(false)
const rememberMe = ref(false)
const sendingCode = ref(false)
const codeCooldown = ref(0)

const pwdHover = ref(false)
const confirmPwdHover = ref(false)
const nicknameHover = ref(false)
const emailHover = ref(false)
const codeHover = ref(false)
const showEmailSuggestions = ref(false)
const showRecentEmails = ref(false)
const recentEmails = ref<string[]>([])

// 重置密码独立状态
const resetForm = reactive({ email: '', code: '', password: '', confirmPassword: '' })
const resetErrors = reactive({ email: '', code: '', password: '', confirmPassword: '' })
const resetCooldown = ref(0)
const resetSendingCode = ref(false)
const resetSubmitting = ref(false)
const showResetPassword = ref(false)
const showResetConfirmPassword = ref(false)
const resetPwdHover = ref(false)
const resetConfirmPwdHover = ref(false)
const resetEmailHover = ref(false)
const resetCodeHover = ref(false)
const showResetEmailSuggestions = ref(false)
const resetPwdInputRef = ref<HTMLInputElement>()
const resetConfirmPwdInputRef = ref<HTMLInputElement>()
let resetCooldownTimer: ReturnType<typeof setInterval> | null = null

const pwdInputRef = ref<HTMLInputElement>()
const confirmPwdInputRef = ref<HTMLInputElement>()
const contentRef = ref<HTMLElement>()

const form = reactive({
  email: '',
  password: '',
  confirmPassword: '',
  nickname: '',
  code: '',
})

const errors = reactive({ email: '', password: '', confirmPassword: '', code: '' })

const modeTitle = computed(() => {
  if (mode.value === 'register') return '创建新账号'
  if (mode.value === 'reset') return '重置密码'
  return '登录你的账号'
})

const submitBtnText = computed(() => {
  if (accountLocked.value) return '账号已锁定'
  if (mode.value === 'register') return '注 册'
  return '登 录'
})

// ===== 监听弹窗打开，初始化状态 =====
watch(() => loginModal.visible.value, async (v) => {
  if (v) {
    mode.value = loginModal.initialMode.value
    await loadCredentials()
    if (!form.email) loadEmail()
    loadRecentEmails()
    restoreCooldown()
    
    // 尝试自动登录
    if (rememberMe.value) {
      const success = await autoLogin()
      if (success) {
        loginModal.onLoginSuccess()
      }
    }
  }
})

watch(() => form.email, (val) => {
  const email = val.trim()
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    showRecentEmails.value = false
    showEmailSuggestions.value = false
  }
})

watch(() => resetForm.email, (val) => {
  const email = val.trim()
  if (/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    showResetEmailSuggestions.value = false
  }
})

// ===== 倒计时管理 =====

// ===== 模式切换高度过渡 =====
watch(mode, () => {
  const el = contentRef.value
  if (!el) return
  const from = el.offsetHeight
  // 临时去掉高度限制，让内容自然撑开
  el.style.transition = 'none'
  el.style.height = 'auto'
  nextTick(() => {
    const to = el.offsetHeight
    if (from === to) { el.style.height = ''; return }
    // 锁定起始高度
    el.style.height = `${from}px`
    // 双帧 rAF 确保浏览器完成布局
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        el.style.transition = 'height 0.35s cubic-bezier(0.4, 0, 0.2, 1)'
        el.style.height = `${to}px`
        const onEnd = (e: TransitionEvent) => {
          if (e.propertyName !== 'height') return
          el.style.height = ''
          el.style.transition = ''
          el.removeEventListener('transitionend', onEnd)
        }
        el.addEventListener('transitionend', onEnd)
      })
    })
  })
})

let cooldownTimer: ReturnType<typeof setInterval> | null = null

function getCooldownScene(): string {
  if (mode.value === 'register') return 'register'
  return 'login'
}

function getCooldownKey(): string {
  return COOLDOWN_KEY_PREFIX + getCooldownScene() + '_' + form.email
}

function startCooldown(seconds: number) {
  codeCooldown.value = seconds
  const expireAt = Date.now() + seconds * 1000
  try { localStorage.setItem(getCooldownKey(), String(expireAt)) } catch {}
  runCooldownTimer()
}

function runCooldownTimer() {
  if (cooldownTimer) clearInterval(cooldownTimer)
  cooldownTimer = setInterval(() => {
    if (codeCooldown.value > 0) codeCooldown.value--
    else { if (cooldownTimer) clearInterval(cooldownTimer) }
  }, 1000)
}

function restoreCooldown() {
  if (!form.email) { codeCooldown.value = 0; if (cooldownTimer) { clearInterval(cooldownTimer); cooldownTimer = null }; return }
  try {
    const stored = localStorage.getItem(getCooldownKey())
    if (!stored) { codeCooldown.value = 0; return }
    const remaining = Math.ceil((Number(stored) - Date.now()) / 1000)
    if (remaining > 0) { codeCooldown.value = remaining; runCooldownTimer() }
    else { codeCooldown.value = 0; localStorage.removeItem(getCooldownKey()) }
  } catch { codeCooldown.value = 0 }
}

// ===== 邮箱后缀自动补全 =====
const emailSuffixes = ['@qq.com', '@163.com', '@gmail.com', '@outlook.com', '@126.com', '@foxmail.com']
const emailSuggestions = computed(() => {
  const val = form.email
  if (!val || val.includes('@')) return []
  return emailSuffixes.map(s => val + s)
})

function onEmailInput() {
  if (emailSuggestions.value.length > 0) {
    showEmailSuggestions.value = true; showRecentEmails.value = false
  } else if (mode.value !== 'register' && filteredRecentEmails.value.length > 0) {
    showEmailSuggestions.value = false; showRecentEmails.value = true
  } else {
    showEmailSuggestions.value = false; showRecentEmails.value = false
  }
}

function onEmailFocus() {
  // 注册模式下不显示最近使用的邮箱
  if (mode.value === 'register') { showRecentEmails.value = false; showEmailSuggestions.value = false; return }
  const val = form.email.trim()
  if (!val || val.includes('@')) {
    if (filteredRecentEmails.value.length > 0) { showRecentEmails.value = true; showEmailSuggestions.value = false }
    else { showRecentEmails.value = false; showEmailSuggestions.value = false }
  } else { onEmailInput() }
}

function selectEmailSuggestion(suggestion: string) {
  form.email = suggestion; showEmailSuggestions.value = false; showRecentEmails.value = false
  errors.email = ''; saveEmail(); restoreCooldown()
}

function selectRecentEmail(email: string) {
  form.email = email; showRecentEmails.value = false; showEmailSuggestions.value = false
  errors.email = ''; saveEmail(); restoreCooldown()
}

function onEmailBlur() {
  setTimeout(() => { showEmailSuggestions.value = false; showRecentEmails.value = false }, 150)
  validateField('email'); saveEmail(); restoreCooldown()
}

function saveEmail() {
  try { if (form.email) localStorage.setItem(EMAIL_KEY, form.email); else localStorage.removeItem(EMAIL_KEY) } catch {}
}

function loadEmail() {
  try { const saved = localStorage.getItem(EMAIL_KEY); if (saved) form.email = saved } catch {}
}

// ===== 最近使用邮箱 =====
function loadRecentEmails() {
  try { const raw = localStorage.getItem(RECENT_EMAILS_KEY); recentEmails.value = raw ? JSON.parse(raw) : [] } catch { recentEmails.value = [] }
}

function addRecentEmail(email: string) {
  if (!email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return
  try {
    const list = recentEmails.value.filter(e => e !== email)
    list.unshift(email); if (list.length > 5) list.length = 5
    recentEmails.value = list; localStorage.setItem(RECENT_EMAILS_KEY, JSON.stringify(list))
  } catch {}
}

function removeRecentEmail(email: string) {
  recentEmails.value = recentEmails.value.filter(e => e !== email)
  try { localStorage.setItem(RECENT_EMAILS_KEY, JSON.stringify(recentEmails.value)) } catch {}
}

const filteredRecentEmails = computed(() => {
  const val = form.email.trim()
  if (!val) return recentEmails.value
  return recentEmails.value.filter(e => e.includes(val))
})

function clearField(field: 'email' | 'password' | 'confirmPassword' | 'nickname' | 'code') {
  form[field] = ''
  if (field === 'email') {
    showEmailSuggestions.value = false; showRecentEmails.value = false
    try { localStorage.removeItem(EMAIL_KEY) } catch {}
  }
}

function validateField(field: 'email' | 'password' | 'confirmPassword') {
  if (field === 'email') {
    if (!form.email) errors.email = '请输入邮箱'
    else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) errors.email = '请输入有效的邮箱地址'
    else errors.email = ''
  }
  if (field === 'password') {
    if (mode.value === 'code') { errors.password = ''; return }
    if (!form.password) errors.password = '请输入密码'
    else if (form.password.length < 8) errors.password = '密码至少8位'
    else if (mode.value === 'register') {
      if (!/[a-z]/.test(form.password) || !/[A-Z]/.test(form.password)) errors.password = '密码需包含大小写字母'
      else if (!/\d/.test(form.password)) errors.password = '密码需包含数字'
      else errors.password = ''
    } else errors.password = ''
  }
  if (field === 'confirmPassword') {
    if (mode.value === 'register') {
      if (!form.confirmPassword) errors.confirmPassword = '请再次输入密码'
      else if (form.password !== form.confirmPassword) errors.confirmPassword = '两次密码不一致'
      else errors.confirmPassword = ''
    }
  }
}

function togglePwd() {
  showPassword.value = !showPassword.value
  nextTick(() => { const input = pwdInputRef.value; if (input) { input.focus(); input.setSelectionRange(form.password.length, form.password.length) } })
}
function toggleConfirmPwd() {
  showConfirmPassword.value = !showConfirmPassword.value
  nextTick(() => { const input = confirmPwdInputRef.value; if (input) { input.focus(); input.setSelectionRange(form.confirmPassword.length, form.confirmPassword.length) } })
}

const pwdLevel = computed(() => {
  const p = form.password; if (!p) return 0; let s = 0
  if (p.length >= 8) s++; if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s++
  if (/\d/.test(p)) s++; if (/[^a-zA-Z0-9]/.test(p)) s++; return s
})
const pwdColor = computed(() => ['', 'var(--status-danger)', 'var(--status-warning)', 'var(--status-info)', 'var(--status-success)'][pwdLevel.value])
const pwdLabel = computed(() => ['', '弱', '一般', '较强', '强'][pwdLevel.value])

// ===== 重置密码相关 =====
const resetEmailSuggestions = computed(() => {
  const val = resetForm.email; if (!val || val.includes('@')) return []
  return emailSuffixes.map(s => val + s)
})

function onResetEmailInput() { showResetEmailSuggestions.value = resetEmailSuggestions.value.length > 0 }

function selectResetEmailSuggestion(s: string) {
  resetForm.email = s; showResetEmailSuggestions.value = false; resetErrors.email = ''; restoreResetCooldown()
}

function onResetEmailBlur() {
  setTimeout(() => { showResetEmailSuggestions.value = false }, 150)
  if (!resetForm.email) resetErrors.email = '请输入邮箱'
  else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resetForm.email)) resetErrors.email = '请输入有效的邮箱地址'
  else resetErrors.email = ''
  restoreResetCooldown()
}

function clearResetField(field: 'email' | 'code' | 'password' | 'confirmPassword') {
  resetForm[field] = ''; if (field === 'email') showResetEmailSuggestions.value = false
}

function getResetCooldownKey() { return COOLDOWN_KEY_PREFIX + 'forgot-password_' + resetForm.email }

function startResetCooldown(seconds: number) {
  resetCooldown.value = seconds
  const expireAt = Date.now() + seconds * 1000
  try { localStorage.setItem(getResetCooldownKey(), String(expireAt)) } catch {}
  runResetCooldownTimer()
}

function runResetCooldownTimer() {
  if (resetCooldownTimer) clearInterval(resetCooldownTimer)
  resetCooldownTimer = setInterval(() => {
    if (resetCooldown.value > 0) resetCooldown.value--
    else if (resetCooldownTimer) { clearInterval(resetCooldownTimer); resetCooldownTimer = null }
  }, 1000)
}

function restoreResetCooldown() {
  if (!resetForm.email) { resetCooldown.value = 0; if (resetCooldownTimer) { clearInterval(resetCooldownTimer); resetCooldownTimer = null }; return }
  try {
    const stored = localStorage.getItem(getResetCooldownKey())
    if (!stored) { resetCooldown.value = 0; return }
    const remaining = Math.ceil((Number(stored) - Date.now()) / 1000)
    if (remaining > 0) { resetCooldown.value = remaining; runResetCooldownTimer() }
    else { resetCooldown.value = 0; localStorage.removeItem(getResetCooldownKey()) }
  } catch { resetCooldown.value = 0 }
}

async function handleResetSendCode() {
  if (!resetForm.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resetForm.email)) {
    resetErrors.email = '请输入有效的邮箱地址'; return
  }
  resetErrors.email = ''; resetErrors.code = ''
  // 先弹出滑块验证
  openCaptcha(async (verifyToken: string) => {
    resetSendingCode.value = true
    try {
      await authApi.sendCode({ email: resetForm.email, scene: 'forgot-password' }, verifyToken)
      startResetCooldown(60); message.success('验证码已发送')
    } catch (e: any) { resetErrors.code = e.message || '发送失败' }
    finally { resetSendingCode.value = false }
  })
}

function toggleResetPwd() {
  showResetPassword.value = !showResetPassword.value
  nextTick(() => { const input = resetPwdInputRef.value; if (input) { input.focus(); input.setSelectionRange(resetForm.password.length, resetForm.password.length) } })
}

function toggleResetConfirmPwd() {
  showResetConfirmPassword.value = !showResetConfirmPassword.value
  nextTick(() => { const input = resetConfirmPwdInputRef.value; if (input) { input.focus(); input.setSelectionRange(resetForm.confirmPassword.length, resetForm.confirmPassword.length) } })
}

const resetPwdLevel = computed(() => {
  const p = resetForm.password; if (!p) return 0; let s = 0
  if (p.length >= 8) s++; if (/[a-z]/.test(p) && /[A-Z]/.test(p)) s++
  if (/\d/.test(p)) s++; if (/[^a-zA-Z0-9]/.test(p)) s++; return s
})
const resetPwdColor = computed(() => ['', 'var(--status-danger)', 'var(--status-warning)', 'var(--status-info)', 'var(--status-success)'][resetPwdLevel.value])
const resetPwdLabel = computed(() => ['', '弱', '一般', '较强', '强'][resetPwdLevel.value])

function validateResetForm(): boolean {
  let valid = true
  resetErrors.email = ''; resetErrors.code = ''; resetErrors.password = ''; resetErrors.confirmPassword = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(resetForm.email)) { resetErrors.email = '请输入有效的邮箱地址'; valid = false }
  if (!resetForm.code || resetForm.code.length !== 6) { resetErrors.code = '请输入6位验证码'; valid = false }
  if (resetForm.password.length < 8) { resetErrors.password = '密码至少8位'; valid = false }
  else {
    if (!/[a-z]/.test(resetForm.password) || !/[A-Z]/.test(resetForm.password)) { resetErrors.password = '密码需包含大小写字母'; valid = false }
    else if (!/\d/.test(resetForm.password)) { resetErrors.password = '密码需包含数字'; valid = false }
  }
  if (resetForm.password !== resetForm.confirmPassword) { resetErrors.confirmPassword = '两次密码不一致'; valid = false }
  return valid
}

async function handleResetSubmit() {
  if (!validateResetForm()) return
  openCaptcha(async (verifyToken: string) => {
    resetSubmitting.value = true
    try {
      await authApi.forgotPassword({ email: resetForm.email, code: resetForm.code, password: resetForm.password }, verifyToken)
      message.success('密码重置成功，请登录')
      setTimeout(() => {
        mode.value = 'password'; form.email = resetForm.email
        resetForm.email = ''; resetForm.code = ''; resetForm.password = ''; resetForm.confirmPassword = ''
      }, 1500)
    } catch (e: any) { message.error(e.message || '重置失败') }
    finally { resetSubmitting.value = false }
  })
}

function enterResetMode() {
  mode.value = 'reset'; resetForm.email = form.email
  resetErrors.email = ''; resetErrors.code = ''; resetErrors.password = ''; resetErrors.confirmPassword = ''
  restoreResetCooldown()
}

function exitResetMode() {
  mode.value = 'password'; form.email = resetForm.email || form.email
}

// ===== 表单快照 =====
interface FormSnapshot { email: string; password: string; code: string; nickname: string; confirmPassword: string }
let loginSnapshot: FormSnapshot | null = null
let registerSnapshot: FormSnapshot | null = null

function saveSnapshot() {
  const snap: FormSnapshot = { email: form.email, password: form.password, code: form.code, nickname: form.nickname, confirmPassword: form.confirmPassword }
  if (mode.value === 'register') registerSnapshot = snap; else loginSnapshot = snap
}

function restoreSnapshot(target: 'login' | 'register') {
  const snap = target === 'register' ? registerSnapshot : loginSnapshot
  if (snap) { form.email = snap.email; form.password = snap.password; form.code = snap.code; form.nickname = snap.nickname; form.confirmPassword = snap.confirmPassword }
  else { form.email = ''; form.password = ''; form.code = ''; form.nickname = ''; form.confirmPassword = '' }
  errors.email = ''; errors.password = ''; errors.confirmPassword = ''; errors.code = ''
  accountLocked.value = false; showEmailSuggestions.value = false; showPassword.value = false; showConfirmPassword.value = false
}

function switchMode(m: LoginModalMode) {
  if (mode.value === m) return; mode.value = m
  errors.email = ''; errors.password = ''; errors.confirmPassword = ''; errors.code = ''
  accountLocked.value = false; restoreCooldown()
}

function toggleRegister() {
  saveSnapshot()
  if (mode.value === 'register') { mode.value = 'code'; restoreSnapshot('login') }
  else { mode.value = 'register'; restoreSnapshot('register') }
  restoreCooldown()
}

// ===== 发送验证码 =====
async function handleSendCode() {
  if (!form.email || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { errors.email = '请输入有效的邮箱地址'; return }
  errors.email = ''; errors.code = ''
  // 先弹出滑块验证
  openCaptcha(async (verifyToken: string) => {
    sendingCode.value = true
    try {
      await authApi.sendCode({ email: form.email, scene: getCooldownScene() as 'login' | 'register' }, verifyToken)
      saveEmail(); startCooldown(60); message.success('验证码已发送')
    } catch (e: any) { errors.code = e.message || '发送失败' }
    finally { sendingCode.value = false }
  })
}

// ===== 记住我（使用 Remember Token）=====
async function saveCredentials() {
  if (rememberMe.value) {
    localStorage.setItem(REMEMBER_KEY, JSON.stringify({
      email: form.email,
      remember: true,
      expireAt: Date.now() + 30 * 24 * 60 * 60 * 1000, // 30 天
    }))
  } else {
    localStorage.removeItem(REMEMBER_KEY)
  }
}

async function loadCredentials() {
  try {
    const raw = localStorage.getItem(REMEMBER_KEY); if (!raw) return
    const saved = JSON.parse(raw); if (!saved.remember) return
    if (saved.expireAt && Date.now() > saved.expireAt) { localStorage.removeItem(REMEMBER_KEY); return }
    // 仅加载邮箱
    form.email = saved.email; rememberMe.value = true
  } catch { localStorage.removeItem(REMEMBER_KEY) }
}

// 自动登录（使用 Remember Token）
async function autoLogin() {
  try {
    const raw = localStorage.getItem(REMEMBER_KEY)
    if (!raw) return false
    
    const saved = JSON.parse(raw)
    if (!saved.remember) return false
    if (saved.expireAt && Date.now() > saved.expireAt) {
      localStorage.removeItem(REMEMBER_KEY)
      return false
    }
    
    // 调用 Cookie 版 Remember Token 自动登录接口
    const res = await authApi.rememberLogin()
    if (res.data) {
      userStore.setUser(res.data)
      message.success('自动登录成功')
      return true
    }
    return false
  } catch (error) {
    console.error('自动登录失败:', error)
    localStorage.removeItem(REMEMBER_KEY)
    return false
  }
}

// ===== 表单验证 =====
function validate(): boolean {
  let valid = true
  errors.email = ''; errors.password = ''; errors.confirmPassword = ''; errors.code = ''
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email)) { errors.email = '请输入有效的邮箱地址'; valid = false }
  if (mode.value === 'code') {
    if (!form.code || form.code.length !== 6) { errors.code = '请输入6位验证码'; valid = false }
  } else {
    if (form.password.length < 8) { errors.password = '密码至少8位'; valid = false }
    else if (mode.value === 'register') {
      if (!/[a-z]/.test(form.password) || !/[A-Z]/.test(form.password)) { errors.password = '密码需包含大小写字母'; valid = false }
      else if (!/\d/.test(form.password)) { errors.password = '密码需包含数字'; valid = false }
    }
    if (mode.value === 'register') {
      if (!form.code || form.code.length !== 6) { errors.code = '请输入6位验证码'; valid = false }
      if (form.password !== form.confirmPassword) { errors.confirmPassword = '两次密码不一致'; valid = false }
    }
  }
  return valid
}

let lastSubmitTime = 0

async function handleSubmit() {
  const now = Date.now()
  if (now - lastSubmitTime < 1000) return
  lastSubmitTime = now
  if (accountLocked.value) { message.error('账号已锁定，请稍后再试'); return }
  if (!validate()) return
  submitting.value = true
  try {
    if (mode.value === 'password') {
      // 密码登录需要先通过滑块验证
      submitting.value = false
      openCaptcha(async (verifyToken: string) => {
        submitting.value = true
        try {
          const res = await authApi.login({ email: form.email, password: form.password, rememberMe: rememberMe.value }, verifyToken)
          addRecentEmail(form.email); submitSuccess.value = true; message.success('登录成功')
          userStore.setUser(res.data); await saveCredentials(); try { localStorage.removeItem(EMAIL_KEY) } catch {}
          await new Promise(r => setTimeout(r, 400))
          loginModal.onLoginSuccess()
        } catch (e: any) {
          if (e.code === ACCOUNT_LOCKED_CODE) { accountLocked.value = true; message.error(e.message || '账号已锁定，请稍后再试') }
          else message.error(e.message || '操作失败，请稍后重试')
        } finally { submitting.value = false; submitSuccess.value = false }
      })
      return
    } else if (mode.value === 'code') {
      submitting.value = false
      openCaptcha(async (verifyToken: string) => {
        submitting.value = true
        try {
          const res = await authApi.loginByCode({ email: form.email, code: form.code }, verifyToken)
          addRecentEmail(form.email); submitSuccess.value = true; message.success('登录成功')
          userStore.setUser(res.data); try { localStorage.removeItem(EMAIL_KEY) } catch {}
          await new Promise(r => setTimeout(r, 400))
          loginModal.onLoginSuccess()
        } catch (e: any) {
          if (e.code === ACCOUNT_LOCKED_CODE) { accountLocked.value = true; message.error(e.message || '账号已锁定，请稍后再试') }
          else message.error(e.message || '操作失败，请稍后重试')
        } finally { submitting.value = false; submitSuccess.value = false }
      })
      return
    } else {
      // 注册需要先通过滑块验证
      submitting.value = false
      openCaptcha(async (verifyToken: string) => {
        submitting.value = true
        try {
          await authApi.register({ email: form.email, password: form.password, nickname: form.nickname || undefined }, form.code, verifyToken)
          addRecentEmail(form.email); submitSuccess.value = true; message.success('注册成功，请登录')
          await new Promise(r => setTimeout(r, 400))
          submitSuccess.value = false
          mode.value = 'code'; form.password = ''; form.confirmPassword = ''; form.code = ''
        } catch (e: any) {
          message.error(e.message || '操作失败，请稍后重试')
        } finally { submitting.value = false; submitSuccess.value = false }
      })
      return
    }
  } catch (e: any) {
    if (e.code === ACCOUNT_LOCKED_CODE) { accountLocked.value = true; message.error(e.message || '账号已锁定，请稍后再试') }
    else message.error(e.message || '操作失败，请稍后重试')
  } finally { submitting.value = false; submitSuccess.value = false }
}

const githubLoading = ref(false)
const submitSuccess = ref(false)

async function handleGithubLogin() {
  githubLoading.value = true
  try {
    // 记录返回页面与滚动位置，OAuth 回调后恢复
    const r = useRoute()
    saveNavContext({
      path: r.path,
      fullPath: r.fullPath,
      scrollY: typeof window !== 'undefined' ? window.scrollY : 0,
    })
    const redirectUri = window.location.origin + '/oauth/github/callback'
    const res = await authApi.getGithubAuthUrl(redirectUri)
    const authUrl = normalizeSafeHref(res.data)
    if (!authUrl) {
      throw new Error('无效的 OAuth 地址')
    }

    const authHost = new URL(authUrl).host.toLowerCase()
    if (!GITHUB_OAUTH_HOSTS.has(authHost)) {
      throw new Error('无效的 OAuth 域名')
    }

    window.location.href = authUrl
  } catch {
    message.error('GitHub 登录暂不可用'); githubLoading.value = false
  }
}

onUnmounted(() => { if (cooldownTimer) clearInterval(cooldownTimer); if (resetCooldownTimer) clearInterval(resetCooldownTimer) })

// 弹窗关闭时重置临时状态
watch(() => loginModal.visible.value, (v) => {
  if (!v) {
    showEmailSuggestions.value = false
    showRecentEmails.value = false
    showResetEmailSuggestions.value = false
    submitSuccess.value = false
    githubLoading.value = false
    accountLocked.value = false
  }
})
</script>

<style scoped lang="scss">
.modal-logo {
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 1.2rem;
  font-weight: 700;
  color: #3b82f6;
  flex: 1;

  .dark & {
    color: #f8fafc;
  }
}

.modal-logo-img {
  width: 28px;
  height: 28px;
  border-radius: 8px;
  object-fit: cover;
  display: block;
}
.login-modal-body {
  --lm-text-muted: #64748b;
  --lm-text-primary: #1e293b;
  --lm-label: #334155;
  --lm-tab-border: #e2e8f0;
  --lm-tab-active: #3b82f6;
  --lm-tab-inactive: #64748b;
  --lm-tab-hover: #334155;
  --lm-input-bg: #f8fafc;
  --lm-input-border: #e2e8f0;
  --lm-input-icon: #94a3b8;
  --lm-input-focus: #3b82f6;
  --lm-input-focus-ring: rgba(59, 130, 246, 0.12);
  --lm-suggest-bg: #ffffff;
  --lm-suggest-border: #e2e8f0;
  --lm-suggest-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  --lm-suggest-hover: #f1f5f9;
  --lm-send-bg: #ffffff;
  --lm-send-color: #3b82f6;
  --lm-send-border: #3b82f6;
  --lm-send-hover-bg: #3b82f6;
  --lm-send-hover-color: #ffffff;
  --lm-submit-bg: #2563eb;
  --lm-submit-hover: #1d4ed8;
  --lm-submit-border: transparent;
  --lm-github-bg: #ffffff;
  --lm-github-border: #e2e8f0;
  --lm-github-hover-bg: #f8fafc;
  --lm-github-hover-border: #cbd5e1;

  padding: 0;
  overflow: hidden;

  .dark & {
    --lm-text-muted: #94a3b8;
    --lm-text-primary: #f1f5f9;
    --lm-label: #e2e8f0;
    --lm-tab-border: rgba(71, 85, 105, 0.65);
    --lm-tab-active: #f8fafc;
    --lm-tab-inactive: #94a3b8;
    --lm-tab-hover: #f8fafc;
    --lm-input-bg: rgba(8, 10, 14, 0.9);
    --lm-input-border: rgba(71, 85, 105, 0.65);
    --lm-input-icon: #94a3b8;
    --lm-input-focus: #cbd5e1;
    --lm-input-focus-ring: rgba(148, 163, 184, 0.2);
    --lm-suggest-bg: rgba(8, 10, 14, 0.98);
    --lm-suggest-border: rgba(71, 85, 105, 0.62);
    --lm-suggest-shadow: 0 8px 20px rgba(2, 6, 23, 0.42);
    --lm-suggest-hover: rgba(20, 24, 31, 0.92);
    --lm-send-bg: rgba(8, 10, 14, 0.92);
    --lm-send-color: #e2e8f0;
    --lm-send-border: rgba(148, 163, 184, 0.52);
    --lm-send-hover-bg: rgba(20, 24, 31, 0.95);
    --lm-send-hover-color: #f8fafc;
    --lm-submit-bg: rgba(8, 10, 14, 0.95);
    --lm-submit-hover: rgba(20, 24, 31, 0.95);
    --lm-submit-border: rgba(148, 163, 184, 0.44);
    --lm-github-bg: rgba(8, 10, 14, 0.92);
    --lm-github-border: rgba(71, 85, 105, 0.65);
    --lm-github-hover-bg: rgba(20, 24, 31, 0.95);
    --lm-github-hover-border: rgba(148, 163, 184, 0.48);
  }
}
.subtitle {
  text-align: center; color: var(--lm-text-muted); font-size: 0.9rem; margin-bottom: 1rem;
}
.login-tabs {
  display: flex; gap: 0; margin-bottom: 1rem; border-bottom: 2px solid var(--lm-tab-border); position: relative;
  button {
    flex: 1; padding: 0.5rem 0; background: none; border: none; border-bottom: 2px solid transparent;
    margin-bottom: -2px; font-size: 0.9rem; color: var(--lm-tab-inactive); cursor: pointer; transition: color 0.25s;
    &.active { color: var(--lm-tab-active); font-weight: 600; }
    &:hover:not(.active) { color: var(--lm-tab-hover); }
  }
  .tab-indicator {
    position: absolute; bottom: -2px; left: 0; width: 50%; height: 2px;
    background: var(--lm-tab-active); border-radius: 1px;
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    &.at-right { transform: translateX(100%); }
  }
}
.form-group {
  margin-bottom: 0.125rem;
  label { display: block; margin-bottom: 0.25rem; font-size: 0.875rem; font-weight: 500; color: var(--lm-label); }
}
.auth-form { transition: min-height 0.2s ease; }
.toggle-fields {
  display: grid;
  .toggle-field {
    grid-area: 1 / 1; transition: opacity 0.25s ease, transform 0.25s ease;
    &.is-hidden { visibility: hidden; opacity: 0; pointer-events: none; transform: translateY(4px); }
  }
}
.toggle-hints {
  display: grid;
  > * { grid-area: 1 / 1; transition: opacity 0.25s ease; &.is-hidden { visibility: hidden; opacity: 0; pointer-events: none; } }
}
.input-wrapper {
  position: relative; display: flex; align-items: center;
  .input-icon {
    position: absolute;
    left: 0.75rem;
    z-index: 3;
    color: var(--lm-input-icon);
    pointer-events: none;
  }
  input {
    position: relative;
    z-index: 1;
    width: 100%; padding: 0.625rem 2.5rem; border: 1px solid var(--lm-input-border); border-radius: 0.5rem;
    font-size: 0.9rem; background: var(--lm-input-bg); color: var(--lm-text-primary); outline: none; transition: border-color 0.2s, box-shadow 0.2s;
    &:focus { border-color: var(--lm-input-focus); box-shadow: 0 0 0 3px var(--lm-input-focus-ring); }

    &:-webkit-autofill,
    &:-webkit-autofill:hover,
    &:-webkit-autofill:focus {
      -webkit-text-fill-color: var(--lm-text-primary);
      caret-color: var(--lm-text-primary);
      -webkit-box-shadow: 0 0 0 1000px var(--lm-input-bg) inset;
      box-shadow: 0 0 0 1000px var(--lm-input-bg) inset;
      transition: background-color 99999s ease-out 0s;
    }
  }
  .toggle-pwd { position: absolute; right: 0.5rem; background: none; border: none; color: var(--lm-input-icon); cursor: pointer; padding: 0.25rem; display: flex; &:hover { color: var(--lm-tab-hover); } }
}
.clear-btn {
  position: absolute; right: 0.5rem; background: none; border: none; color: var(--lm-input-icon); cursor: pointer; padding: 0.25rem; display: flex; transition: color 0.2s;
  &:hover { color: var(--lm-tab-hover); } &.with-toggle { right: 2rem; }
}
.email-group { position: relative; }
.email-suggestions {
  position: absolute; top: 100%; left: 0; right: 0; z-index: 10; margin: 0; padding: 0.25rem 0; list-style: none;
  background: var(--lm-suggest-bg); border: 1px solid var(--lm-suggest-border); border-radius: 0.5rem; box-shadow: var(--lm-suggest-shadow); max-height: 200px; overflow-y: auto;
  li { padding: 0.5rem 0.75rem; font-size: 0.85rem; color: var(--lm-text-primary); cursor: pointer; transition: background-color 0.15s; &:hover { background: var(--lm-suggest-hover); } }
}
.recent-emails {
  .recent-label { padding: 0.375rem 0.75rem; font-size: 0.75rem; color: var(--lm-text-muted); cursor: default; &:hover { background: transparent; } }
  .recent-item { display: flex; align-items: center; justify-content: space-between; padding: 0.5rem 0.75rem; }
  .recent-email-text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
  .recent-remove {
    flex-shrink: 0; background: none; border: none; color: #c0c4cc; cursor: pointer; padding: 0.125rem;
    display: flex; align-items: center; transition: color 0.2s; margin-left: 0.5rem;
    &:hover { color: #ef4444; }
    .dark & { color: $color-dark-text-muted; &:hover { color: #ef4444; } }
  }
}
.code-row { display: flex; gap: 0.5rem; .code-input-wrapper { flex: 1; } }
.send-code-btn {
  flex-shrink: 0; padding: 0 1rem; height: 2.375rem; border: 1px solid #3b82f6; border-radius: 0.5rem;
  background: var(--lm-send-bg); color: var(--lm-send-color); border-color: var(--lm-send-border); font-size: 0.8rem; cursor: pointer; white-space: nowrap;
  display: flex; align-items: center; gap: 4px; transition: all 0.2s;
  &:hover:not(:disabled) { background: var(--lm-send-hover-bg); color: var(--lm-send-hover-color); }
  &:disabled { opacity: 0.5; cursor: not-allowed; border-color: #94a3b8; color: #94a3b8; }
}
.error-text {
  display: block; margin-top: 0.125rem; font-size: 0.75rem; color: #ef4444;
  min-height: 1rem; visibility: hidden; opacity: 0; transition: opacity 0.2s;
  &.visible { visibility: visible; opacity: 1; }
}
.no-browser-eye {
  &::-ms-reveal, &::-ms-clear { display: none; }
  &::-webkit-credentials-auto-fill-button, &::-webkit-textfield-decoration-container { display: none !important; }
}
.password-strength {
  display: flex; align-items: center; gap: 0.5rem; margin-top: 0.25rem;
  .strength-bars { display: flex; gap: 4px; }
  .bar { width: 2rem; height: 4px; border-radius: 2px; background-color: #e2e8f0; transition: background-color 0.2s; .dark & { background-color: $color-dark-border; } }
  .strength-text { font-size: 0.75rem; font-weight: 500; }
}
.submit-btn {
  width: 100%; padding: 0.75rem; border: 1px solid var(--lm-submit-border); border-radius: 0.5rem; background: var(--lm-submit-bg); color: #fff;
  font-size: 1rem; font-weight: 600; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem; transition: background-color 0.2s;
  &:hover:not(:disabled) { background: var(--lm-submit-hover); } &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.remember-me {
  display: flex; align-items: center; gap: 0.5rem; cursor: pointer; font-size: 0.85rem; color: #64748b;
  input[type="checkbox"] { accent-color: #3b82f6; cursor: pointer; }
  .dark & { color: $color-dark-text-muted; }
}
.remember-row { display: flex; align-items: center; justify-content: space-between; margin-bottom: 0.75rem; }
.forgot-pwd-btn {
  background: none; border: none; color: var(--lm-tab-active); font-size: 0.85rem; cursor: pointer; padding: 0;
  &:hover { text-decoration: underline; }
}
.code-hint { margin-bottom: 0.75rem; font-size: 0.8rem; color: var(--lm-text-muted); line-height: 1.25; }
.divider {
  display: flex; align-items: center; margin: 0.5rem 0; color: var(--lm-text-muted); font-size: 0.8rem;
  &::before, &::after { content: ''; flex: 1; height: 1px; background: var(--lm-tab-border); }
  span { padding: 0 0.75rem; }
}
.github-btn {
  width: 100%; padding: 0.625rem; border: 1px solid var(--lm-github-border); border-radius: 0.5rem; background: var(--lm-github-bg); color: var(--lm-text-primary);
  font-size: 0.9rem; font-weight: 500; cursor: pointer; display: flex; align-items: center; justify-content: center; gap: 0.5rem; transition: background-color 0.2s, border-color 0.2s;
  &:hover:not(:disabled) { background: var(--lm-github-hover-bg); border-color: var(--lm-github-hover-border); }
  &:disabled { opacity: 0.6; cursor: not-allowed; }
}
.switch-mode {
  margin-top: 0.875rem; text-align: center; font-size: 0.85rem; color: var(--lm-text-muted);
  .link-btn { background: none; border: none; color: var(--lm-tab-active); cursor: pointer; font-size: inherit; font-weight: 500; &:hover { text-decoration: underline; } }
}
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* 模式切换过渡 */
.fade-slide-enter-active, .fade-slide-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.fade-slide-enter-from { opacity: 0; transform: translateY(-6px); }
.fade-slide-leave-to { opacity: 0; transform: translateY(6px); }

/* 提交按钮成功状态 */
.submit-btn.is-success {
  background: #22c55e;
  &:hover:not(:disabled) { background: #22c55e; }
}
</style>
