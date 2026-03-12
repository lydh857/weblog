<template>
  <div class="login-page">
    <button class="theme-toggle" aria-label="切换主题" @click="toggleDark()">
      <el-icon :size="18">
        <Sunny v-if="isDark" />
        <Moon v-else />
      </el-icon>
    </button>

    <div class="login-container">
      <!-- 左侧品牌区 -->
      <div class="brand-panel">
        <div class="brand-inner">
          <div class="brand-logo">W</div>
          <h2 class="brand-name">Weblog</h2>
          <p class="brand-slogan">简洁高效的博客管理系统</p>
          <div class="brand-divider" />
          <ul class="brand-features">
            <li>
              <el-icon :size="16"><Document /></el-icon>
              <span>文章与内容管理</span>
            </li>
            <li>
              <el-icon :size="16"><DataAnalysis /></el-icon>
              <span>数据统计与分析</span>
            </li>
            <li>
              <el-icon :size="16"><Setting /></el-icon>
              <span>灵活的系统配置</span>
            </li>
          </ul>
        </div>
      </div>

      <!-- 右侧表单区 -->
      <div class="form-panel">
        <div class="form-inner">
          <h1 class="form-title">欢迎回来</h1>
          <p class="form-subtitle">登录管理后台</p>

          <el-form
            ref="formRef"
            :model="form"
            :rules="rules"
            label-position="top"
            hide-required-asterisk
            @submit.prevent="handleSubmit"
          >
            <el-form-item label="邮箱" prop="email">
              <el-autocomplete
                v-model="form.email"
                :fetch-suggestions="suggestEmail"
                placeholder="请输入管理员邮箱"
                :prefix-icon="Message"
                size="large"
                clearable
                class="full-width"
                @select="onEmailSelect"
              />
            </el-form-item>

            <el-form-item label="密码" prop="password" class="pwd-form-item">
              <div
                class="pwd-wrapper"
                @mouseenter="pwdHover = true"
                @mouseleave="pwdHover = false"
              >
                <el-input
                  ref="pwdInputRef"
                  v-model="form.password"
                  :type="showPwd ? 'text' : 'password'"
                  placeholder="请输入密码"
                  :prefix-icon="Lock"
                  size="large"
                  clearable
                  @keyup.enter="handleSubmit"
                >
                  <template v-if="pwdHover && form.password" #suffix>
                    <el-icon class="pwd-toggle" @mousedown.prevent="togglePwd">
                      <View v-if="!showPwd" /><Hide v-else />
                    </el-icon>
                  </template>
                </el-input>
              </div>
            </el-form-item>

            <div class="remember-row">
              <el-checkbox v-model="rememberMe" :disabled="!isRememberEnabled" label="记住我" />
            </div>

            <el-button
              type="primary"
              native-type="submit"
              size="large"
              :loading="submitting"
              :disabled="accountLocked"
              class="submit-btn"
            >
              {{ accountLocked ? '账号已锁定' : '登 录' }}
            </el-button>
          </el-form>

          <p class="copyright">&copy; {{ new Date().getFullYear() }} Weblog. All rights reserved.</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import {
  Message, Lock, View, Hide, Sunny, Moon,
  Document, DataAnalysis, Setting,
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
// CryptoJS 已移除，记住密码功能现在使用明文存储
import { authApi } from '~/api/auth'
import { useUserStore } from '~/stores/user'
import { useDarkMode } from '~/composables/useDarkMode'

definePageMeta({ layout: false })

const REMEMBER_KEY = 'weblog_admin_remember'
const ACCOUNT_LOCKED_CODE = 40103

const config = useRuntimeConfig()
// 记住我功能默认启用（仅存储 remember token 与邮箱）
const isRememberEnabled = computed(() => true)

const { isDark, toggleDark } = useDarkMode()
const userStore = useUserStore()
const router = useRouter()

const formRef = ref<FormInstance>()
const pwdInputRef = ref<InstanceType<typeof import('element-plus')['ElInput']>>()
const showPwd = ref(false)
const pwdHover = ref(false)
const submitting = ref(false)
const rememberMe = ref(false)
const accountLocked = ref(false)
const form = reactive({ email: '', password: '' })
const skipEmailValidation = ref(false)

const emailFormatValidator = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (skipEmailValidation.value) {
    skipEmailValidation.value = false
    callback()
    return
  }
  const emailReg = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (value && !emailReg.test(value)) {
    callback(new Error('请输入有效的邮箱地址'))
  } else {
    callback()
  }
}

// 密码强度验证器
const passwordValidator = (_rule: unknown, value: string, callback: (err?: Error) => void) => {
  if (!value) {
    callback(new Error('请输入密码'))
    return
  }
  if (value.length < 8) {
    callback(new Error('密码至少8位'))
    return
  }
  // 密码强度验证：必须包含大小写字母和数字
  const hasUpper = /[A-Z]/.test(value)
  const hasLower = /[a-z]/.test(value)
  const hasDigit = /\d/.test(value)
  if (!hasUpper || !hasLower || !hasDigit) {
    callback(new Error('密码需包含大小写字母和数字'))
    return
  }
  callback()
}

const rules: FormRules = {
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { validator: emailFormatValidator, trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { validator: passwordValidator, trigger: 'blur' },
  ],
}

function togglePwd() {
  showPwd.value = !showPwd.value
  nextTick(() => {
    const input = pwdInputRef.value?.ref as HTMLInputElement | undefined
    if (input) {
      input.focus()
      const len = form.password.length
      input.setSelectionRange(len, len)
    }
  })
}

const emailSuffixes = ['@qq.com', '@163.com', '@gmail.com', '@outlook.com', '@126.com', '@foxmail.com']

function suggestEmail(query: string, cb: (results: { value: string }[]) => void) {
  if (!query || query.includes('@')) {
    cb([])
    return
  }
  cb(emailSuffixes.map(s => ({ value: query + s })))
}

function onEmailSelect() {
  skipEmailValidation.value = true
  nextTick(() => {
    formRef.value?.clearValidate('email')
  })
}

// ===== 记住我（使用 Remember Token）=====
function saveCredentials() {
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

function loadCredentials() {
  try {
    const raw = localStorage.getItem(REMEMBER_KEY)
    if (!raw) return
    const saved = JSON.parse(raw)
    if (!saved.remember) return
    if (saved.expireAt && Date.now() > saved.expireAt) {
      localStorage.removeItem(REMEMBER_KEY)
      return
    }
    // 仅加载邮箱
    form.email = saved.email
    rememberMe.value = true
  } catch {
    localStorage.removeItem(REMEMBER_KEY)
  }
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
      ElMessage.success('自动登录成功')
      return true
    }
    return false
  } catch (error) {
    console.error('自动登录失败:', error)
    localStorage.removeItem(REMEMBER_KEY)
    return false
  }
}

// （账号锁定由后端控制，前端仅展示后端返回的锁定提示）

// ===== 登录提交（防抖） =====
let lastSubmitTime = 0

async function handleSubmit() {
  // 防抖：1 秒内不能重复提交
  const now = Date.now()
  if (now - lastSubmitTime < 1000) return
  lastSubmitTime = now

  if (accountLocked.value) {
    ElMessage.warning('账号已锁定，请 30 分钟后再试')
    return
  }

  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    const res = await authApi.login({ 
      email: form.email, 
      password: form.password,
      rememberMe: rememberMe.value
    })
    userStore.setUser(res.data)
    
    // 持久化记住我开关（Remember Token 由 HttpOnly Cookie 管理）
    saveCredentials()
    
    accountLocked.value = false
    
    // 登录成功后主动获取 CSRF Token（通过 GET 请求触发后端生成 Cookie）
    await userStore.fetchUser()
    
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e: unknown) {
    const err = e as { code?: number; message?: string }
    if (err.code === ACCOUNT_LOCKED_CODE) {
      accountLocked.value = true
      ElMessage.error(err.message || '账号已锁定，请稍后再试')
    } else {
      ElMessage.error(err.message || '登录失败')
    }
  } finally {
    submitting.value = false
  }
}

onMounted(async () => {
  loadCredentials()
  
  // 尝试自动登录
  if (rememberMe.value) {
    const success = await autoLogin()
    if (success) {
      router.push('/')
    }
  }
})
</script>

<style scoped lang="scss">
/* ===== 页面容器 ===== */
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  position: relative;
  background-color: #f0f4f8;
  background-image:
    radial-gradient(circle at 15% 25%, rgba(91, 141, 239, 0.06) 0%, transparent 50%),
    radial-gradient(circle at 85% 75%, rgba(74, 125, 224, 0.05) 0%, transparent 50%),
    radial-gradient(circle at 50% 50%, rgba(123, 164, 242, 0.03) 0%, transparent 70%),
    linear-gradient(180deg, rgba(91, 141, 239, 0.02) 0%, transparent 40%);
  transition: background-color 0.3s ease;
  .dark & {
    background-color: #0d1117;
    background-image:
      radial-gradient(circle at 15% 25%, rgba(91, 141, 239, 0.08) 0%, transparent 50%),
      radial-gradient(circle at 85% 75%, rgba(74, 125, 224, 0.06) 0%, transparent 50%),
      radial-gradient(circle at 50% 50%, rgba(123, 164, 242, 0.03) 0%, transparent 70%);
  }
}

/* ===== 主题切换 ===== */
.theme-toggle {
  position: absolute;
  top: 1.25rem;
  right: 1.25rem;
  z-index: 10;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  border: 1px solid #dcdfe6;
  background: #fff;
  color: #606266;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.25s;
  &:hover {
    color: #5b8def;
    border-color: #5b8def;
    transform: rotate(20deg);
  }
  .dark & {
    background: #1c2128;
    border-color: #30363d;
    color: #8b949e;
    &:hover { color: #7ba4f2; border-color: #7ba4f2; }
  }
}

/* ===== 登录卡片容器 ===== */
.login-container {
  display: flex;
  width: 100%;
  max-width: 880px;
  min-height: 500px;
  border-radius: 16px;
  overflow: hidden;
  background: #fff;
  border: 1px solid #e8ecf1;
  box-shadow: 0 12px 48px rgba(0, 0, 0, 0.06);
  transition: all 0.3s;
  .dark & {
    background: #161b22;
    border-color: #30363d;
    box-shadow: 0 12px 48px rgba(0, 0, 0, 0.3);
  }
}

/* ===== 左侧品牌区 ===== */
.brand-panel {
  flex: 0 0 340px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem 2rem;
  background: linear-gradient(160deg, #5b8def 0%, #4a7de0 100%);
  color: #fff;
  position: relative;
  overflow: hidden;
  &::before {
    content: '';
    position: absolute;
    top: -60px;
    right: -60px;
    width: 200px;
    height: 200px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.06);
  }
  &::after {
    content: '';
    position: absolute;
    bottom: -40px;
    left: -40px;
    width: 160px;
    height: 160px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.04);
  }
}
.brand-inner {
  position: relative;
  z-index: 1;
  text-align: center;
}
.brand-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 1rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.15);
  backdrop-filter: blur(8px);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.75rem;
  font-weight: 800;
  letter-spacing: -1px;
}
.brand-name {
  font-size: 1.5rem;
  font-weight: 700;
  margin-bottom: 0.375rem;
  letter-spacing: 0.5px;
}
.brand-slogan {
  font-size: 0.85rem;
  opacity: 0.8;
  margin-bottom: 1.5rem;
}
.brand-divider {
  width: 32px;
  height: 2px;
  background: rgba(255, 255, 255, 0.3);
  margin: 0 auto 1.5rem;
  border-radius: 1px;
}
.brand-features {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.625rem;
  li {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 0.5rem;
    font-size: 0.8rem;
    opacity: 0.85;
    padding: 0.35rem 0.75rem;
    border-radius: 20px;
    background: rgba(255, 255, 255, 0.08);
    transition: background 0.2s;
    &:hover { background: rgba(255, 255, 255, 0.14); }
  }
}

/* ===== 右侧表单区 ===== */
.form-panel {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 3rem 2.5rem;
}
.form-inner {
  width: 100%;
  max-width: 320px;
}
.form-title {
  font-size: 1.4rem;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 0.25rem;
  .dark & { color: #d3dce6; }
}
.form-subtitle {
  font-size: 0.85rem;
  color: #909399;
  margin-bottom: 2rem;
  .dark & { color: #666; }
}
.submit-btn {
  width: 100%;
  margin-top: 0.5rem;
  background: #5b8def;
  border-color: #5b8def;
  &:hover { background: #4a7de0; border-color: #4a7de0; }
}
.remember-row {
  .remember-tip {
    font-size: 12px;
    color: #909399;
    margin-left: 8px;
  }
  display: flex;
  align-items: center;
  margin-bottom: 1rem;
  :deep(.el-checkbox__label) {
    font-size: 0.8rem;
    color: #909399;
  }
  :deep(.el-checkbox__input.is-checked .el-checkbox__inner) {
    background-color: #5b8def;
    border-color: #5b8def;
  }
  :deep(.el-checkbox__input.is-checked + .el-checkbox__label) {
    color: #5b8def;
  }
  .dark & :deep(.el-checkbox__label) { color: #666; }
  .dark & :deep(.el-checkbox__input.is-checked + .el-checkbox__label) { color: #7ba4f2; }
}
.pwd-toggle {
  cursor: pointer;
  color: #909399;
  transition: color 0.2s;
  &:hover { color: #303133; }
  .dark & { color: #666; &:hover { color: #ccc; } }
}
.pwd-wrapper { width: 100%; }
.full-width { width: 100%; }
.copyright {
  text-align: center;
  font-size: 0.7rem;
  color: #c0c4cc;
  margin-top: 2.5rem;
  .dark & { color: #444; }
}

/* ===== 响应式 ===== */
@media (max-width: 768px) {
  .login-container {
    flex-direction: column;
    max-width: 420px;
    min-height: auto;
  }
  .brand-panel {
    flex: none;
    padding: 2rem 1.5rem;
  }
  .brand-features { display: none; }
  .brand-divider { display: none; }
  .form-panel { padding: 2rem 1.5rem; }
}

/* ===== 减弱动画偏好 ===== */
@media (prefers-reduced-motion: reduce) {
  .theme-toggle { transition: none; }
  .theme-toggle:hover { transform: none; }
  .login-container { transition: none; }
}
</style>
