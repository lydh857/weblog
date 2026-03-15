<template>
  <Teleport to="body">
    <Transition name="ad-apply-fade">
      <div v-if="visible" class="ad-apply-overlay" @click.self="closeModal">
        <section class="ad-apply-modal" role="dialog" aria-modal="true" aria-label="广告投放申请">
          <header class="modal-header">
            <div>
              <h3>广告投放申请</h3>
              <p>按规则选择时效，提交后进入审核；若审核晚于申请开始，将自动顺延保障投放天数。</p>
            </div>
            <button class="close-btn" type="button" aria-label="关闭" @click="closeModal">
              <Icon name="heroicons:x-mark-20-solid" size="18" />
            </button>
          </header>

          <div class="stepper">
            <div class="step" :class="{ active: currentStep === 1, completed: currentStep > 1 }">
              <div class="step-dot">1</div>
              <span class="step-label">填写申请</span>
            </div>
            <div class="step-line" />
            <div class="step" :class="{ active: currentStep === 2 }">
              <div class="step-dot">2</div>
              <span class="step-label">申请状态</span>
            </div>
          </div>

          <div class="modal-body">
            <section v-if="currentStep === 1" class="step-content">
              <div v-if="configLoading" class="loading-text">正在加载申请配置...</div>
              <template v-else>
                <div class="notice notice-info">
                  <p>流程：选择位置和坑位 → 选择时效 → 上传素材 → 提交审核。</p>
                  <p>审核若晚于申请开始时间，系统会自动顺延，保障完整投放天数。</p>
                </div>

                <div v-if="!isLoggedIn" class="notice notice-warning">
                  登录后可提交广告申请。
                  <button class="inline-btn" type="button" @click="openLogin">去登录</button>
                </div>
                <div v-else-if="!applyEnabled" class="notice notice-error">广告申请入口当前未开放，请稍后再试。</div>
                <div v-else-if="!hasAvailablePit" class="notice notice-warning">当前暂无可申请坑位，请稍后再试。</div>

                <form v-else class="apply-form" @submit.prevent="handleSubmit">
                  <div class="form-grid">
                    <div class="form-item">
                      <label>广告标题 <span class="required">*</span></label>
                      <input v-model="form.title" type="text" maxlength="100" placeholder="请输入广告标题" required>
                    </div>

                    <div class="form-item">
                      <label>广告类型 <span class="required">*</span></label>
                      <div class="select-wrap">
                        <select v-model="form.type">
                          <option value="image">图片广告</option>
                          <option value="code">代码广告（HTML）</option>
                        </select>
                        <Icon name="heroicons:chevron-down-16-solid" size="16" class="select-arrow" />
                      </div>
                    </div>
                  </div>

                  <div class="form-grid">
                    <div class="form-item">
                      <label>投放位置 <span class="required">*</span></label>
                      <div class="select-wrap">
                        <select v-model="form.position">
                          <option :disabled="!hasPitForPosition('home_left')" value="home_left">首页左侧</option>
                          <option :disabled="!hasPitForPosition('post_top')" value="post_top">文章顶部</option>
                          <option :disabled="!hasPitForPosition('post_bottom')" value="post_bottom">文章底部</option>
                          <option :disabled="form.type === 'code' || !hasPitForPosition('post_list_card')" value="post_list_card">文章列表拟态卡</option>
                        </select>
                        <Icon name="heroicons:chevron-down-16-solid" size="16" class="select-arrow" />
                      </div>
                    </div>

                    <div class="form-item">
                      <label>申请坑位 <span class="required">*</span></label>
                      <div v-if="currentPositionPitOptions.length > 0" class="pit-options">
                        <button
                          v-for="pit in currentPositionPitOptions"
                          :key="pit.pitAdId"
                          class="pit-chip"
                          :class="{ active: form.pitAdId === pit.pitAdId }"
                          type="button"
                          @click="form.pitAdId = pit.pitAdId"
                        >
                          {{ pitOptionLabel(pit) }}
                        </button>
                      </div>
                      <div v-else class="notice notice-warning compact">当前广告位暂无可申请坑位，请切换位置。</div>
                      <p v-if="selectedPitOption" class="pit-tip">审核通过后将替换该坑位广告。</p>
                    </div>
                  </div>

                  <div class="form-item">
                    <label>投放时效 <span class="required">*</span></label>
                    <div v-if="currentPositionRules.length > 0" class="duration-options">
                      <button
                        v-for="rule in currentPositionRules"
                        :key="`${rule.position}-${rule.durationDays}`"
                        class="duration-chip"
                        :class="{ active: form.durationDays === rule.durationDays }"
                        type="button"
                        @click="applyDurationRule(rule.durationDays)"
                      >
                        {{ rule.durationDays }} 天 / ¥{{ formatPrice(rule.price) }}
                      </button>
                    </div>
                    <div v-else class="notice notice-warning compact">该位置尚未配置时效价格规则，请联系管理员。</div>
                    <p v-if="selectedRule" class="price-tip">预估价格：¥{{ formatPrice(selectedRule.price) }}</p>
                  </div>

                  <div class="form-grid">
                    <div class="form-item">
                      <label>开始日期 <span class="required">*</span></label>
                      <div class="date-preset-row">
                        <button
                          v-for="preset in startDatePresets"
                          :key="preset.key"
                          type="button"
                          class="date-preset-btn"
                          :class="{ active: isStartDatePresetActive(preset.value) }"
                          @click="applyStartDatePreset(preset.value)"
                        >
                          {{ preset.label }}
                        </button>
                      </div>
                      <input v-model="form.startDate" type="date" @change="handleStartDateChange">
                    </div>

                    <div class="form-item">
                      <label>结束日期（自动计算）</label>
                      <input :value="form.endDate || '请选择时效后自动生成'" type="text" readonly>
                      <p class="date-hint">结束日期会随开始日期和时效自动更新。</p>
                    </div>
                  </div>

                  <div v-if="form.type === 'image'" class="form-item">
                    <label>广告图片 <span class="required">*</span></label>
                    <div class="image-upload-box">
                      <div class="image-preview-shell" :class="`slot-${form.position}`" @click="openImageCropper">
                        <img v-if="imagePreviewUrl" :src="imagePreviewUrl" alt="广告预览" class="image-preview-img">
                        <div v-else class="image-preview-empty">
                          <Icon name="heroicons:photo-20-solid" size="22" />
                          <strong>点击上传广告图片</strong>
                          <span>当前位置推荐比例 {{ currentRatioText }}</span>
                        </div>
                        <span class="image-preview-badge">广告</span>
                        <span v-if="form.adInfo" class="image-preview-info">{{ form.adInfo }}</span>
                        <div class="image-preview-mask">点击裁剪 / 更换</div>
                      </div>

                      <div class="image-action-row">
                        <button type="button" class="action-btn" @click="triggerImageUpload">
                          {{ imagePreviewUrl ? '重新上传' : '上传图片' }}
                        </button>
                        <button v-if="imagePreviewUrl" type="button" class="action-btn" @click="openImageCropper">重新裁剪</button>
                      </div>

                      <input
                        ref="imageInputRef"
                        type="file"
                        accept="image/jpeg,image/png,image/webp"
                        class="hidden-input"
                        @change="handleImageFileChange"
                      >

                      <p class="upload-hint">支持 JPG/PNG/WebP；切换投放位置后会自动切换占位比例。</p>
                    </div>
                  </div>

                  <div v-else class="form-item">
                    <label>HTML 代码 <span class="required">*</span></label>
                    <textarea
                      v-model="form.content"
                      rows="8"
                      placeholder="请输入 HTML 广告代码"
                      required
                    />
                  </div>

                  <div class="form-grid">
                    <div class="form-item">
                      <label>跳转链接</label>
                      <input v-model="form.linkUrl" type="text" placeholder="https://example.com（可选）">
                    </div>
                    <div class="form-item">
                      <label>图片文案</label>
                      <input v-model="form.adInfo" type="text" maxlength="40" placeholder="展示在广告图上的简短说明（可选）">
                    </div>
                  </div>

                  <div v-if="form.type === 'image' && form.position === 'post_list_card'" class="form-item">
                    <label>拟态文案</label>
                    <textarea v-model="form.mimicContent" rows="2" maxlength="120" placeholder="用于文章列表拟态卡，不填则默认“品牌推广”" />
                  </div>

                  <div class="step-actions step-actions--form">
                    <button class="action-btn" type="button" @click="closeModal">取消</button>
                    <button class="action-btn primary" type="submit" :disabled="submitting || !selectedRule || !selectedPitOption">
                      {{ submitting ? '提交中...' : '提交投放申请' }}
                    </button>
                    <button
                      v-if="myApplication"
                      class="action-btn"
                      type="button"
                      @click="currentStep = 2"
                    >
                      查看当前申请
                    </button>
                  </div>
                </form>
              </template>
            </section>

            <section v-if="currentStep === 2" class="step-content">
              <div v-if="statusLoading" class="loading-text">正在获取申请状态...</div>
              <template v-else>
                <div v-if="!isLoggedIn" class="notice notice-warning">请先登录查看申请状态。</div>
                <div v-else-if="!myApplication" class="notice notice-info">当前广告位暂无申请记录。</div>
                <template v-else>
                  <div class="notice" :class="statusNoticeClass(myApplication.status)">
                    <p>{{ statusLabel(myApplication.status) }}</p>
                    <p v-if="myApplication.status === 'pending'">管理员审核中，请耐心等待。</p>
                    <p v-else-if="myApplication.status === 'active'">审核通过，广告已处于投放状态；若审核晚于申请开始，系统会自动顺延保障完整时长。</p>
                    <p v-else-if="myApplication.status === 'rejected'">审核未通过，可根据原因修改后重新提交。</p>
                    <p v-else-if="myApplication.status === 'expired'">当前申请已过期，可重新提交。</p>
                  </div>

                  <div class="preview-card">
                    <h4>我的申请信息</h4>
                    <div class="preview-row"><span>标题</span><strong>{{ myApplication.title }}</strong></div>
                    <div class="preview-row"><span>类型</span><strong>{{ myApplication.type === 'image' ? '图片广告' : '代码广告' }}</strong></div>
                    <div class="preview-row"><span>位置</span><strong>{{ posLabel(myApplication.position) }}</strong></div>
                    <div v-if="myApplication.pitAdId" class="preview-row"><span>申请坑位</span><strong>{{ applicationPitLabel(myApplication) }}</strong></div>
                    <div class="preview-row"><span>时效</span><strong>{{ applicationDurationText(myApplication) }}</strong></div>
                    <div class="preview-row"><span>预估价格</span><strong>{{ applicationPriceText(myApplication) }}</strong></div>
                    <div class="preview-row"><span>起始时间</span><strong>{{ formatTime(myApplication.startTime) }}</strong></div>
                    <div class="preview-row"><span>结束时间</span><strong>{{ formatTime(myApplication.endTime) }}</strong></div>
                    <div class="preview-row"><span>剩余时间</span><strong>{{ applicationRemainingText(myApplication) }}</strong></div>
                    <div class="preview-row"><span>提交时间</span><strong>{{ formatTime(myApplication.createTime) }}</strong></div>
                    <div v-if="myApplication.reviewReason" class="preview-reason">
                      <span>审核备注</span>
                      <p>{{ myApplication.reviewReason }}</p>
                    </div>
                  </div>

                  <div class="step-actions">
                    <button class="action-btn" type="button" @click="refreshMyApplication">刷新状态</button>
                    <button class="action-btn" type="button" @click="currentStep = 1">返回填写</button>
                    <button
                      v-if="['rejected', 'expired'].includes(myApplication.status)"
                      class="action-btn primary"
                      type="button"
                      @click="editMyApplication"
                    >
                      修改申请
                    </button>
                  </div>
                </template>
              </template>
            </section>
          </div>
        </section>
      </div>
    </Transition>
  </Teleport>

  <AdImageCropper
    v-model="cropperVisible"
    :image-src="cropperImageSrc"
    :aspect-ratio="currentCropRatio"
    output-type="image/webp"
    :max-output-width="1600"
    @crop="handleImageCropped"
  />
</template>

<script setup lang="ts">
import { advertisementApi, type AdApplyPitOption, type AdPriceRuleVO, type AdvertisementVO } from '~/api/advertisement'
import { uploadApi } from '~/api/upload'
import { useLoginModal } from '~/composables/useLoginModal'
import { lockScroll, unlockScroll } from '~/composables/useScrollLock'
import { useUserStore } from '~/stores/user'

interface AdApplyFormState {
  title: string
  type: 'image' | 'code'
  position: string
  pitAdId: number | null
  durationDays: number
  startDate: string
  endDate: string
  content: string
  linkUrl: string
  adInfo: string
  mimicContent: string
}

const adApplyModal = useAdApplyModal()
const loginModal = useLoginModal()
const userStore = useUserStore()
const message = useMessage()
const {
  applyEnabled,
  priceRules,
  pitOptions,
  loading: configLoading,
  loadAdApplyConfig,
} = useAdApplyConfig()

const visible = adApplyModal.visible
const currentStep = ref(1)
const submitting = ref(false)
const statusLoading = ref(false)
const myApplication = ref<AdvertisementVO | null>(null)
const isLoggedIn = computed(() => userStore.isLoggedIn)
const lockedByModal = ref(false)
const imageInputRef = ref<HTMLInputElement | null>(null)
const cropperVisible = ref(false)
const cropperImageSrc = ref('')
const pendingImageFile = ref<File | null>(null)

const form = reactive<AdApplyFormState>({
  title: '',
  type: 'image',
  position: 'home_left',
  pitAdId: null,
  durationDays: 0,
  startDate: '',
  endDate: '',
  content: '',
  linkUrl: '',
  adInfo: '',
  mimicContent: '',
})

const positionOrder = ['home_left', 'post_top', 'post_bottom', 'post_list_card']
const positionOptions = [
  { value: 'home_left', label: '首页左侧' },
  { value: 'post_top', label: '文章顶部' },
  { value: 'post_bottom', label: '文章底部' },
  { value: 'post_list_card', label: '文章列表拟态卡' },
]

const cropRatioMap: Record<string, [number, number]> = {
  home_left: [5, 8],
  post_top: [16, 5],
  post_bottom: [16, 5],
  post_list_card: [16, 9],
}

const currentPositionRules = computed(() => {
  const currentPit = resolvePitIndex(selectedPitOption.value)
  return priceRules.value
    .filter(rule => rule.position === form.position && resolvePitIndex(rule) === currentPit)
    .slice()
    .sort((a, b) => a.durationDays - b.durationDays)
})

const pitOptionsByPosition = computed(() => {
  const grouped = new Map<string, AdApplyPitOption[]>()
  for (const option of pitOptions.value) {
    const position = normalizePosition(option.position)
    if (!grouped.has(position)) {
      grouped.set(position, [])
    }
    grouped.get(position)?.push({
      ...option,
      position,
    })
  }

  grouped.forEach((items, key) => {
    grouped.set(key, items.slice().sort((a, b) => {
      const pitDiff = resolvePitIndex(a) - resolvePitIndex(b)
      if (pitDiff !== 0) return pitDiff
      return a.pitAdId - b.pitAdId
    }))
  })
  return grouped
})

const hasAvailablePit = computed(() => pitOptions.value.length > 0)

const currentPositionPitOptions = computed(() => {
  return pitOptionsByPosition.value.get(form.position) || []
})

const selectedPitOption = computed(() => {
  return currentPositionPitOptions.value.find(item => item.pitAdId === form.pitAdId) || null
})

const selectedRule = computed(() => {
  return currentPositionRules.value.find(rule => rule.durationDays === form.durationDays) || null
})

const startDatePresets = computed(() => {
  const today = getTodayDate()
  return [
    { key: 'today', label: '今天开始', value: today },
    { key: 'tomorrow', label: '明天开始', value: addDays(today, 1) },
    { key: 'next-monday', label: '下周一开始', value: getNextWeekdayDate(1) },
    { key: 'next-month', label: '下月1号开始', value: getNextMonthFirstDate() },
  ]
})

const currentCropRatio = computed<[number, number]>(() => {
  return cropRatioMap[form.position] || [16, 9]
})

const currentRatioText = computed(() => {
  const [width, height] = currentCropRatio.value
  return `${width}:${height}`
})

const imagePreviewUrl = computed(() => {
  if (form.type !== 'image') return ''
  const raw = form.content.trim()
  if (!raw) return ''
  if (raw.startsWith('blob:') || raw.startsWith('data:image/')) return raw
  try {
    const parsed = new URL(raw)
    return ['http:', 'https:'].includes(parsed.protocol) ? raw : ''
  } catch {
    return ''
  }
})

function normalizePosition(raw: unknown): string {
  const value = typeof raw === 'string' ? raw.trim() : ''
  return positionOrder.includes(value) ? value : 'home_left'
}

function getTodayDate() {
  const now = new Date()
  return formatDateInput(now)
}

function getNextWeekdayDate(targetWeekday: number) {
  const now = new Date()
  const currentWeekday = now.getDay()
  let diff = (targetWeekday - currentWeekday + 7) % 7
  if (diff === 0) {
    diff = 7
  }
  const next = new Date(now.getTime() + diff * 86400000)
  return formatDateInput(next)
}

function getNextMonthFirstDate() {
  const now = new Date()
  const next = new Date(now.getFullYear(), now.getMonth() + 1, 1)
  return formatDateInput(next)
}

function formatDateInput(date: Date) {
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

function parseDateInput(value: string): Date | null {
  if (!value) return null
  const normalized = `${value}T00:00:00`
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return null
  return date
}

function addDays(startDate: string, days: number) {
  const date = parseDateInput(startDate)
  if (!date) return ''
  const next = new Date(date.getTime() + days * 86400000)
  return formatDateInput(next)
}

function calcDurationDaysByDate(startDate: string, endDate: string) {
  const start = parseDateInput(startDate)
  const end = parseDateInput(endDate)
  if (!start || !end) return 0
  const diff = end.getTime() - start.getTime()
  if (diff <= 0) return 0
  return Math.round(diff / 86400000)
}

function toDateTimeStringFromDateInput(dateValue: string) {
  return `${dateValue} 00:00:00`
}

function isBlobOrDataUrl(url: string) {
  return url.startsWith('blob:') || url.startsWith('data:image/')
}

function posLabel(position: string) {
  return {
    home_left: '首页左侧',
    post_top: '文章顶部',
    post_bottom: '文章底部',
    post_list_card: '文章列表拟态卡',
  }[position] || position
}

function formatPrice(price: number) {
  return Number(price || 0).toFixed(0)
}

function hasPitForPosition(position: string) {
  return pitOptionsByPosition.value.has(normalizePosition(position))
}

function pitOptionLabel(option: AdApplyPitOption) {
  return `#${resolvePitIndex(option)}`
}

function resolvePitIndex(option: { pitIndex?: number | null } | null | undefined) {
  const value = Number(option?.pitIndex)
  if (Number.isInteger(value) && value > 0) {
    return value
  }
  return 1
}

function ensurePitSelectedForPosition() {
  const options = currentPositionPitOptions.value
  if (!options.length) {
    form.pitAdId = null
    return
  }

  const exists = options.some(item => item.pitAdId === form.pitAdId)
  if (!exists) {
    form.pitAdId = options[0].pitAdId
  }
}

function pickPreferredPositionAndPit() {
  const preferredPitId = adApplyModal.preferredPitAdId.value
  if (preferredPitId) {
    const matchedPit = pitOptions.value.find(item => item.pitAdId === preferredPitId)
    if (matchedPit) {
      form.position = normalizePosition(matchedPit.position)
      form.pitAdId = matchedPit.pitAdId
      return
    }
  }

  const preferredPosition = normalizePosition(adApplyModal.preferredPosition.value)
  if (hasPitForPosition(preferredPosition)) {
    form.position = preferredPosition
    ensurePitSelectedForPosition()
    return
  }

  const fallback = positionOptions.find(option => hasPitForPosition(option.value))
  if (fallback) {
    form.position = fallback.value
    ensurePitSelectedForPosition()
    return
  }

  form.position = preferredPosition
  form.pitAdId = null
}

function statusLabel(status: string) {
  return {
    pending: '待审核',
    active: '已通过',
    rejected: '已拒绝',
    expired: '已过期',
  }[status] || status
}

function statusNoticeClass(status: string) {
  return {
    'notice-info': status === 'pending',
    'notice-success': status === 'active',
    'notice-error': status === 'rejected',
    'notice-warning': status === 'expired',
  }
}

function formatTime(value: string | null | undefined) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 16)
}

function calcDurationDays(startTime: string | null, endTime: string | null) {
  if (!startTime || !endTime) return 0
  const start = new Date(startTime.replace(' ', 'T')).getTime()
  const end = new Date(endTime.replace(' ', 'T')).getTime()
  if (!Number.isFinite(start) || !Number.isFinite(end) || end <= start) return 0
  return Math.max(1, Math.round((end - start) / 86400000))
}

function remainingText(endTime: string | null) {
  if (!endTime) return '未设置'
  const end = new Date(endTime.replace(' ', 'T')).getTime()
  if (!Number.isFinite(end)) return '未设置'

  const diff = end - Date.now()
  if (diff <= 0) return '已结束'

  const days = Math.floor(diff / 86400000)
  const hours = Math.floor((diff % 86400000) / 3600000)
  if (days > 0) return `${days} 天 ${hours} 小时`

  const minutes = Math.max(1, Math.floor((diff % 3600000) / 60000))
  return `${minutes} 分钟`
}

function applicationDurationText(ad: AdvertisementVO) {
  const days = calcDurationDays(ad.startTime, ad.endTime)
  return days > 0 ? `${days} 天` : '未设置'
}

function applicationRemainingText(ad: AdvertisementVO) {
  if (ad.status === 'pending') {
    return '待审核后计算'
  }
  return remainingText(ad.endTime)
}

function applicationPriceText(ad: AdvertisementVO) {
  const days = calcDurationDays(ad.startTime, ad.endTime)
  if (days < 1) return '未匹配'
  const resolvedPitIndex = ad.pitIndex
    || pitOptions.value.find(item => item.pitAdId === ad.pitAdId)?.pitIndex
    || 1
  let matched = priceRules.value.find(rule => {
    return rule.position === ad.position
      && rule.pitIndex === resolvedPitIndex
      && rule.durationDays === days
  })
  if (!matched && resolvedPitIndex !== 1) {
    matched = priceRules.value.find(rule => {
      return rule.position === ad.position
        && rule.pitIndex === 1
        && rule.durationDays === days
    })
  }
  return matched ? `¥${formatPrice(matched.price)}` : '未匹配'
}

function applicationPitLabel(ad: AdvertisementVO) {
  if (!ad.pitAdId) return '-'
  const matched = pitOptions.value.find(item => item.pitAdId === ad.pitAdId)
  if (matched) return pitOptionLabel(matched)
  const fallbackPitIndex = resolvePitIndex({ pitIndex: ad.pitIndex })
  return `#${fallbackPitIndex}`
}

function ensureDurationSelected() {
  if (currentPositionRules.value.length === 0) {
    form.durationDays = 0
    return
  }

  const hasSelected = currentPositionRules.value.some(rule => rule.durationDays === form.durationDays)
  if (!hasSelected) {
    form.durationDays = currentPositionRules.value[0].durationDays
  }

  if (!form.startDate) {
    form.startDate = getTodayDate()
  }
  form.endDate = addDays(form.startDate, form.durationDays)
}

function isStartDatePresetActive(value: string) {
  if (!value) return false
  return form.startDate === value
}

function applyStartDatePreset(startDate: string) {
  if (!startDate) return
  form.startDate = startDate
  if (form.durationDays > 0) {
    form.endDate = addDays(form.startDate, form.durationDays)
    return
  }
  if (currentPositionRules.value.length > 0) {
    form.durationDays = currentPositionRules.value[0].durationDays
    form.endDate = addDays(form.startDate, form.durationDays)
  }
}

function applyDurationRule(days: number) {
  form.durationDays = days
  if (!form.startDate) {
    form.startDate = getTodayDate()
  }
  form.endDate = addDays(form.startDate, days)
}

function handleStartDateChange() {
  if (!form.startDate) return
  if (form.durationDays > 0) {
    form.endDate = addDays(form.startDate, form.durationDays)
    return
  }
  if (currentPositionRules.value.length > 0) {
    form.durationDays = currentPositionRules.value[0].durationDays
    form.endDate = addDays(form.startDate, form.durationDays)
  }
}

function resetImageState() {
  if (isBlobOrDataUrl(form.content)) {
    try {
      URL.revokeObjectURL(form.content)
    } catch {}
  }
  pendingImageFile.value = null
}

function triggerImageUpload() {
  imageInputRef.value?.click()
}

function handleImageFileChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = loadEvent => {
    const source = typeof loadEvent.target?.result === 'string' ? loadEvent.target.result : ''
    if (!source) return
    cropperImageSrc.value = source
    cropperVisible.value = true
  }
  reader.readAsDataURL(file)
  if (imageInputRef.value) {
    imageInputRef.value.value = ''
  }
}

function openImageCropper() {
  if (imagePreviewUrl.value) {
    cropperImageSrc.value = imagePreviewUrl.value
    cropperVisible.value = true
    return
  }
  triggerImageUpload()
}

function handleImageCropped(data: { blob: Blob; url: string }) {
  resetImageState()
  form.content = data.url
  const ext = data.blob.type === 'image/png' ? 'png' : data.blob.type === 'image/jpeg' ? 'jpg' : 'webp'
  pendingImageFile.value = new File([data.blob], `ad-apply.${ext}`, { type: data.blob.type })
}

function openLogin() {
  loginModal.open('code', async () => {
    if (!visible.value) return
    await initWhenOpen()
  })
}

function closeModal() {
  adApplyModal.close()
}

function syncFormFromApplication(ad: AdvertisementVO) {
  form.title = ad.title || ''
  form.type = ad.type === 'code' ? 'code' : 'image'
  form.position = normalizePosition(ad.position)
  form.pitAdId = typeof ad.pitAdId === 'number' ? ad.pitAdId : null
  form.content = ad.content || ''
  form.linkUrl = ad.linkUrl || ''
  form.adInfo = ad.adInfo || ''
  form.mimicContent = ad.mimicContent || ''
  form.startDate = ad.startTime ? formatDateInput(new Date(ad.startTime.replace(' ', 'T'))) : getTodayDate()
  form.endDate = ad.endTime ? formatDateInput(new Date(ad.endTime.replace(' ', 'T'))) : ''
  const days = calcDurationDaysByDate(form.startDate, form.endDate)
  form.durationDays = days > 0 ? days : form.durationDays
  ensurePitSelectedForPosition()
  ensureDurationSelected()
}

async function refreshMyApplication() {
  if (!isLoggedIn.value) return
  statusLoading.value = true
  try {
    const res = await advertisementApi.getMyApplication(form.position)
    myApplication.value = res.data
  } catch {
    myApplication.value = null
  } finally {
    statusLoading.value = false
  }
}

function validateForm(): string | null {
  if (!form.title.trim()) return '广告标题不能为空'
  if (!positionOrder.includes(form.position)) return '广告位置不合法'
  if (!hasPitForPosition(form.position)) return '当前广告位暂无可申请坑位'
  if (!selectedPitOption.value) return '请选择可申请坑位'
  if (!form.startDate || !form.endDate) return '请选择起止日期'
  const selectedDays = calcDurationDaysByDate(form.startDate, form.endDate)
  if (selectedDays < 1) return '结束日期必须晚于开始日期'
  if (!selectedRule.value || selectedRule.value.durationDays !== selectedDays) {
    return '起止日期与价格规则不匹配，请按规则时效选择'
  }

  if (form.type === 'image') {
    if (!form.content.trim()) return '请上传广告图片'
    if (!pendingImageFile.value) {
      try {
        const parsed = new URL(form.content.trim())
        if (!['http:', 'https:'].includes(parsed.protocol)) {
          return '图片地址仅支持 http/https'
        }
      } catch {
        return '图片地址格式不正确'
      }
    }
  } else if (!form.content.trim()) {
    return '代码内容不能为空'
  }

  if (form.linkUrl.trim()) {
    try {
      const parsed = new URL(form.linkUrl.trim())
      if (!['http:', 'https:'].includes(parsed.protocol)) {
        return '跳转链接仅支持 http/https'
      }
    } catch {
      return '跳转链接格式不正确'
    }
  }

  return null
}

async function handleSubmit() {
  if (!isLoggedIn.value) {
    openLogin()
    return
  }
  if (!applyEnabled.value) {
    message.warning('申请入口未开放')
    return
  }

  const error = validateForm()
  if (error) {
    message.warning(error)
    return
  }

  const pit = selectedPitOption.value
  if (!pit) {
    message.warning('请选择可申请坑位')
    return
  }

  submitting.value = true
  try {
    let content = form.content.trim()
    if (form.type === 'image' && pendingImageFile.value) {
      const uploadRes = await uploadApi.image(pendingImageFile.value, 'ad_apply')
      content = uploadRes.data
      if (isBlobOrDataUrl(form.content)) {
        try {
          URL.revokeObjectURL(form.content)
        } catch {}
      }
      form.content = content
      pendingImageFile.value = null
    }

    await advertisementApi.apply({
      title: form.title.trim(),
      type: form.type,
      position: form.position,
      content,
      linkUrl: form.linkUrl.trim() || undefined,
      adInfo: form.adInfo.trim() || undefined,
      mimicContent: form.position === 'post_list_card' ? (form.mimicContent.trim() || undefined) : undefined,
      insertAfter: form.position === 'post_list_card' ? (pit.insertAfter || 4) : undefined,
      pitAdId: pit.pitAdId,
      startTime: toDateTimeStringFromDateInput(form.startDate),
      endTime: toDateTimeStringFromDateInput(form.endDate),
    })

    message.success('广告投放申请已提交')
    await refreshMyApplication()
    currentStep.value = 2
    adApplyModal.notifyApplicationChanged()
  } catch {
  } finally {
    submitting.value = false
  }
}

function editMyApplication() {
  if (!myApplication.value) return
  syncFormFromApplication(myApplication.value)
  currentStep.value = 1
}

async function initWhenOpen() {
  form.startDate = getTodayDate()

  await loadAdApplyConfig(true)
  pickPreferredPositionAndPit()
  ensurePitSelectedForPosition()
  ensureDurationSelected()

  if (!applyEnabled.value) {
    currentStep.value = 1
    return
  }

  if (!hasAvailablePit.value) {
    currentStep.value = 1
    return
  }

  if (!isLoggedIn.value) {
    currentStep.value = 1
    return
  }

  await refreshMyApplication()
  if (myApplication.value) {
    syncFormFromApplication(myApplication.value)
  } else {
    ensurePitSelectedForPosition()
  }

  const preferredStep = adApplyModal.preferredStep.value
  if (preferredStep === 3 && myApplication.value) {
    currentStep.value = 2
    return
  }

  if (myApplication.value && ['pending', 'active'].includes(myApplication.value.status)) {
    currentStep.value = preferredStep === 3 ? 2 : 1
    return
  }

  currentStep.value = 1
}

function handleEscClose(event: KeyboardEvent) {
  if (!visible.value) return
  if (event.key !== 'Escape') return
  closeModal()
}

watch(() => form.type, type => {
  if (type === 'code' && form.position === 'post_list_card') {
    form.position = 'post_top'
    message.warning('代码广告不支持文章列表拟态卡位，已自动切换到文章顶部')
  }
  if (type === 'code') {
    resetImageState()
    if (!form.content.trim() || isBlobOrDataUrl(form.content) || /^https?:\/\//.test(form.content.trim())) {
      form.content = ''
    }
  }
  if (type === 'image' && !form.startDate) {
    form.startDate = getTodayDate()
  }
})

watch(() => form.position, async () => {
  ensurePitSelectedForPosition()
  ensureDurationSelected()
  if (!visible.value || !isLoggedIn.value) return
  await refreshMyApplication()
})

watch(() => pitOptions.value, () => {
  if (!hasPitForPosition(form.position)) {
    pickPreferredPositionAndPit()
  }
  ensurePitSelectedForPosition()
}, { deep: true })

watch(isLoggedIn, async loggedIn => {
  if (!visible.value) return
  if (!loggedIn) {
    myApplication.value = null
    currentStep.value = 1
    return
  }
  await refreshMyApplication()
})

watch([
  () => adApplyModal.preferredPosition.value,
  () => adApplyModal.preferredPitAdId.value,
], async () => {
  if (!visible.value) return
  pickPreferredPositionAndPit()
  ensurePitSelectedForPosition()
  ensureDurationSelected()
  if (isLoggedIn.value) {
    await refreshMyApplication()
  }
})

watch(() => visible.value, async opened => {
  if (opened) {
    if (!lockedByModal.value) {
      lockScroll()
      lockedByModal.value = true
    }
    await initWhenOpen()
    return
  }

  if (lockedByModal.value) {
    unlockScroll()
    lockedByModal.value = false
  }
})

onMounted(() => {
  document.addEventListener('keydown', handleEscClose)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscClose)
  if (lockedByModal.value) {
    unlockScroll()
    lockedByModal.value = false
  }
  resetImageState()
})
</script>

<style scoped lang="scss">
.ad-apply-fade-enter-active,
.ad-apply-fade-leave-active {
  transition: opacity 0.22s ease;
}

.ad-apply-fade-enter-from,
.ad-apply-fade-leave-to {
  opacity: 0;
}

.ad-apply-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(2, 6, 23, 0.52);
  backdrop-filter: blur(8px);
}

.ad-apply-modal {
  width: min(1120px, 100%);
  max-height: calc(100vh - 2rem);
  border-radius: 16px;
  border: 1px solid rgba(148, 163, 184, 0.32);
  background: $color-bg;
  box-shadow: 0 30px 80px rgba(15, 23, 42, 0.36);
  overflow: hidden;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: rgba(71, 85, 105, 0.55);
    box-shadow: 0 30px 80px rgba(2, 6, 23, 0.65);
  }
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.1rem 0.82rem;
  border-bottom: 1px solid $color-border;
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.96), rgba(248, 250, 252, 0.74));

  .dark & {
    border-bottom-color: $color-dark-border;
    background: linear-gradient(180deg, rgba(15, 23, 42, 0.95), rgba(15, 23, 42, 0.78));
  }

  h3 {
    margin: 0;
    font-size: 1.08rem;
  }

  p {
    margin: 0.3rem 0 0;
    color: $color-text-muted;
    font-size: 0.82rem;
  }
}

.close-btn {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  border: 1px solid $color-border;
  background: rgba(255, 255, 255, 0.95);
  color: $color-text-muted;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s;

  &:hover {
    border-color: $color-primary;
    color: $color-primary;
  }

  .dark & {
    border-color: $color-dark-border;
    background: rgba(15, 23, 42, 0.9);
    color: #94a3b8;
  }
}

.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.85rem 1.1rem;
  border-bottom: 1px solid $color-border;

  .dark & {
    border-bottom-color: $color-dark-border;
  }
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }

  &.active {
    color: $color-primary;
  }

  &.active .step-dot {
    border-color: $color-primary;
    background: $color-primary;
    color: #fff;
  }

  &.completed .step-dot {
    border-color: #10b981;
    background: #10b981;
    color: #fff;
  }
}

.step-dot {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2px solid $color-border;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
  font-weight: 700;

  .dark & {
    border-color: $color-dark-border;
  }
}

.step-label {
  font-size: 0.76rem;
  font-weight: 600;
}

.step-line {
  flex: 1;
  height: 2px;
  background: $color-border;
  margin: 0 0.7rem 1.2rem;

  .dark & {
    background: $color-dark-border;
  }
}

.modal-body {
  max-height: calc(100vh - 11rem);
  overflow: auto;
  padding: 1rem;
}

.step-content {
  min-height: 220px;
}

.loading-text {
  text-align: center;
  padding: 1.2rem 0;
  color: $color-text-muted;
}

.notice {
  border-left: 4px solid;
  border-radius: 8px;
  padding: 0.72rem 0.82rem;
  margin-bottom: 0.85rem;
  font-size: 0.86rem;
  line-height: 1.55;

  p {
    margin: 0.08rem 0;
  }

  &.compact {
    margin-top: 0.4rem;
    margin-bottom: 0;
  }
}

.notice-info {
  background: #e6f7ff;
  border-color: #1890ff;
  color: #096dd9;

  .dark & {
    background: rgba(24, 144, 255, 0.14);
  }
}

.notice-success {
  background: #f6ffed;
  border-color: #52c41a;
  color: #389e0d;

  .dark & {
    background: rgba(82, 196, 26, 0.14);
  }
}

.notice-warning {
  background: #fffbe6;
  border-color: #faad14;
  color: #d48806;

  .dark & {
    background: rgba(250, 173, 20, 0.14);
  }
}

.notice-error {
  background: #fff1f0;
  border-color: #f5222d;
  color: #cf1322;

  .dark & {
    background: rgba(245, 34, 45, 0.14);
  }
}

.inline-btn {
  margin-left: 0.46rem;
  border: none;
  background: transparent;
  color: inherit;
  font-weight: 700;
  text-decoration: underline;
  cursor: pointer;
}

.apply-form {
  display: flex;
  flex-direction: column;
  gap: 0.88rem;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.72rem;
}

.form-item {
  display: flex;
  flex-direction: column;

  label {
    margin-bottom: 0.34rem;
    font-size: 0.84rem;
    font-weight: 600;
  }

  .required {
    color: #ef4444;
  }

  input,
  textarea,
  select {
    width: 100%;
    border: 1px solid $color-border;
    border-radius: 10px;
    padding: 0.6rem 0.76rem;
    font-size: 0.88rem;
    background: $color-bg;
    color: $color-text;
    transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;

    &:focus {
      outline: none;
      border-color: $color-primary;
      box-shadow: 0 0 0 3px rgba(59, 130, 246, 0.12);
    }
  }

  textarea {
    resize: vertical;
  }
}

.select-wrap {
  position: relative;
  border-radius: 11px;

  &::before {
    content: '';
    position: absolute;
    inset: 1px;
    border-radius: 10px;
    pointer-events: none;
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.42), rgba(255, 255, 255, 0));

    .dark & {
      background: linear-gradient(180deg, rgba(148, 163, 184, 0.14), rgba(148, 163, 184, 0));
    }
  }

  select {
    appearance: none;
    position: relative;
    z-index: 1;
    padding-right: 2.05rem;
    border-color: rgba(148, 163, 184, 0.52);
    background:
      linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.9));
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.65);

    .dark & {
      border-color: rgba(100, 116, 139, 0.64);
      background: linear-gradient(180deg, rgba(15, 23, 42, 0.94), rgba(30, 41, 59, 0.84));
      color: $color-dark-text;
      box-shadow: inset 0 1px 0 rgba(148, 163, 184, 0.14);
    }

    &:hover {
      border-color: rgba(59, 130, 246, 0.52);
    }

    &:focus + .select-arrow {
      color: $color-primary;
      transform: translateY(-50%) scale(1.05);
    }

    &:disabled {
      cursor: not-allowed;
      color: rgba(148, 163, 184, 0.96);
      background: rgba(248, 250, 252, 0.75);

      .dark & {
        color: rgba(148, 163, 184, 0.78);
        background: rgba(30, 41, 59, 0.62);
      }
    }
  }
}

.select-arrow {
  position: absolute;
  right: 0.66rem;
  top: 50%;
  transform: translateY(-50%);
  color: $color-text-muted;
  pointer-events: none;
  transition: transform 0.2s ease, color 0.2s ease;
}

.duration-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.pit-options {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
}

.pit-chip {
  border: 1px solid rgba(148, 163, 184, 0.55);
  border-radius: 999px;
  background: rgba(248, 250, 252, 0.95);
  color: $color-text-muted;
  font-size: 0.78rem;
  padding: 0.28rem 0.62rem;
  cursor: pointer;

  &:hover {
    border-color: rgba(59, 130, 246, 0.45);
    color: $color-primary;
  }

  &.active {
    border-color: rgba(16, 185, 129, 0.72);
    background: rgba(16, 185, 129, 0.14);
    color: #047857;
    font-weight: 600;
  }
}

.pit-tip {
  margin: 0.42rem 0 0;
  font-size: 0.78rem;
  color: #059669;
  font-weight: 600;
}

.duration-chip {
  border: 1px solid $color-border;
  border-radius: 999px;
  padding: 0.33rem 0.66rem;
  background: $color-bg;
  color: $color-text-muted;
  font-size: 0.8rem;
  cursor: pointer;

  &.active {
    border-color: $color-primary;
    color: $color-primary;
    background: rgba(59, 130, 246, 0.11);
    font-weight: 600;
  }
}

.price-tip {
  margin: 0.42rem 0 0;
  font-size: 0.8rem;
  color: $color-primary;
  font-weight: 700;
}

.date-preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-bottom: 0.5rem;
}

.date-preset-btn {
  border: 1px solid $color-border;
  border-radius: 999px;
  background: $color-bg;
  color: $color-text-muted;
  font-size: 0.76rem;
  padding: 0.24rem 0.58rem;
  cursor: pointer;

  &:hover {
    border-color: rgba(59, 130, 246, 0.45);
    color: $color-primary;
  }

  &.active {
    border-color: $color-primary;
    color: $color-primary;
    background: rgba(59, 130, 246, 0.12);
    font-weight: 600;
  }
}


.date-hint {
  margin: 0.45rem 0 0;
  font-size: 0.76rem;
  color: $color-text-muted;
}

.image-upload-box {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.image-preview-shell {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  cursor: pointer;
  border: 1px solid rgba(148, 163, 184, 0.35);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.92), rgba(241, 245, 249, 0.88));
  display: flex;
  align-items: center;
  justify-content: center;

  &.slot-home_left {
    aspect-ratio: 5 / 8;
    max-width: 220px;
  }

  &.slot-post_top,
  &.slot-post_bottom {
    aspect-ratio: 16 / 5;
  }
}

.image-preview-empty {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  color: $color-text-muted;
  text-align: center;

  strong {
    font-size: 0.86rem;
    color: $color-text;
  }

  span {
    font-size: 0.78rem;
  }
}

.image-preview-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.image-preview-badge {
  position: absolute;
  left: 0.42rem;
  top: 0.42rem;
  z-index: 2;
  font-size: 0.58rem;
  color: #fff;
  background: rgba(15, 23, 42, 0.44);
  border-radius: 999px;
  padding: 0.08rem 0.42rem;
}

.image-preview-info {
  position: absolute;
  left: 0.56rem;
  right: 0.56rem;
  bottom: 0.58rem;
  z-index: 2;
  color: #fff;
  font-size: 0.74rem;
  font-weight: 600;
  text-shadow: 0 2px 8px rgba(0, 0, 0, 0.45);
}

.image-preview-mask {
  position: absolute;
  inset: 0;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.82rem;
  font-weight: 600;
  color: #fff;
  background: rgba(2, 6, 23, 0.4);
  opacity: 0;
  transition: opacity 0.2s;
}

.image-preview-shell:hover .image-preview-mask {
  opacity: 1;
}

.image-action-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.hidden-input {
  display: none;
}

.upload-hint {
  margin: 0;
  font-size: 0.76rem;
  color: $color-text-muted;
}

.preview-card {
  border: 1px solid $color-border;
  border-radius: 10px;
  padding: 0.78rem;
}

.preview-card h4 {
  margin: 0 0 0.62rem;
  font-size: 0.9rem;
}

.preview-row {
  display: flex;
  justify-content: space-between;
  gap: 0.75rem;
  padding: 0.25rem 0;
  font-size: 0.82rem;

  span {
    color: $color-text-muted;
  }

  strong {
    font-weight: 600;
    text-align: right;
  }
}

.preview-reason {
  margin-top: 0.5rem;
  padding: 0.55rem 0.62rem;
  border-radius: 8px;
  border: 1px dashed rgba(239, 68, 68, 0.35);
  background: rgba(254, 242, 242, 0.75);

  span {
    font-size: 0.78rem;
    color: #dc2626;
    font-weight: 700;
  }

  p {
    margin: 0.32rem 0 0;
    font-size: 0.8rem;
    line-height: 1.55;
    color: #991b1b;
  }
}

.step-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.62rem;
  margin-top: 0.95rem;
}

.step-actions--form {
  justify-content: flex-start;
}

.action-btn {
  border: 1px solid $color-border;
  border-radius: 9px;
  padding: 0.52rem 1.2rem;
  background: $color-bg;
  color: $color-text-muted;
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;

  &:disabled {
    opacity: 0.58;
    cursor: not-allowed;
  }

  &.primary {
    border-color: $color-primary;
    background: $color-primary;
    color: #fff;
  }
}

@media (max-width: $breakpoint-md) {
  .ad-apply-modal {
    width: 100%;
    max-height: calc(100vh - 1rem);
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .image-preview-shell.slot-home_left {
    max-width: 100%;
  }

  .step-actions {
    flex-wrap: wrap;
  }

  .action-btn {
    width: 100%;
  }
}
</style>
