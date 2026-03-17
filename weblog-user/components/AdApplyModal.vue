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
                <div v-else-if="myApplication && !canResubmitApplication" class="notice notice-info">
                  你已提交过广告申请，当前不支持修改申请内容。
                  <button class="inline-btn" type="button" @click="currentStep = 2">查看申请状态</button>
                </div>
                <div v-else-if="!hasAvailablePit && !canResubmitApplication" class="notice notice-warning">当前暂无可申请坑位，请稍后再试。</div>

                <form v-else class="apply-form" @submit.prevent="handleSubmit">
                  <div v-if="canResubmitApplication" class="notice notice-warning">
                    {{ resubmitNoticeText }}
                    <button
                      v-if="myApplication?.status === 'rejected'"
                      class="inline-btn"
                      type="button"
                      @click="currentStep = 2"
                    >
                      查看拒绝原因
                    </button>
                  </div>
                  <div class="apply-layout">
                    <section class="preview-panel">
                      <h4 class="preview-panel-title">预览区</h4>
                      <div class="image-upload-box">
                        <div class="image-upload-layout image-upload-layout--single">
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
                        </div>
                        <input
                          ref="imageInputRef"
                          type="file"
                          accept="image/jpeg,image/png,image/webp"
                          class="hidden-input"
                          @change="handleImageFileChange"
                        >
                        <p class="upload-hint">支持 JPG/PNG/WebP；点击预览区上传或重新裁剪。</p>
                      </div>
                    </section>

                    <section class="form-panel">
                      <div class="form-grid">
                        <div class="form-item">
                          <label>投放位置 <span class="required">*</span></label>
                          <div ref="positionSelectRef" class="custom-select">
                            <button
                              type="button"
                              class="custom-select-trigger"
                              :class="{ open: positionSelectOpen }"
                              @click="toggleSelectMenu('position')"
                            >
                              <span>{{ selectedPositionLabel }}</span>
                              <Icon name="heroicons:chevron-down-16-solid" size="16" class="select-arrow" />
                            </button>
                            <div v-if="positionSelectOpen" class="custom-select-menu">
                              <button
                                v-for="item in positionSelectOptions"
                                :key="item.value"
                                type="button"
                                class="custom-select-option"
                                :class="{ active: form.position === item.value }"
                                :disabled="item.disabled"
                                @click="selectPosition(item.value, item.disabled)"
                              >
                                {{ item.label }}
                              </button>
                            </div>
                          </div>
                        </div>

                        <div v-if="currentPositionPitOptions.length > 0" class="form-item">
                          <label>申请坑位 <span class="required">*</span></label>
                          <div class="pit-options">
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
                          <div class="datetime-proxy">
                            <input :value="startDateDisplay" class="datetime-display-input" type="text" readonly>
                            <input
                              v-model="form.startDate"
                              class="datetime-native-input"
                              type="datetime-local"
                              step="60"
                              @change="handleStartDateChange"
                            >
                            <span class="datetime-icon">
                              <Icon name="heroicons:calendar-days-20-solid" size="18" />
                            </span>
                          </div>
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
                        </div>

                        <div class="form-item">
                          <label>结束日期（自动计算）</label>
                          <p class="date-hint date-hint-before">结束日期会随开始日期和时效自动更新。</p>
                          <input :value="endDateDisplay" type="text" readonly>
                        </div>
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

                      <div v-if="form.position === 'post_list_card'" class="form-item">
                        <label>拟态文案</label>
                        <textarea v-model="form.mimicContent" rows="2" maxlength="120" placeholder="用于文章列表拟态卡，不填则默认“品牌推广”" />
                      </div>

                      <div class="step-actions step-actions--form-panel">
                        <button class="action-btn" type="button" @click="closeModal">取消</button>
                        <button class="action-btn primary" type="submit" :disabled="submitting || !selectedRule">
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
                    </section>
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
                  <div v-if="myApplication.status !== 'rejected'" class="notice" :class="statusNoticeClass(myApplication.status)">
                    <p>{{ statusLabel(myApplication.status) }}</p>
                    <p v-if="myApplication.status === 'pending'">管理员审核中，请耐心等待。</p>
                    <p v-else-if="myApplication.status === 'active'">审核通过，广告已处于投放状态；若审核晚于申请开始，系统会自动顺延保障完整时长。</p>
                    <p v-else-if="myApplication.status === 'expired'">当前申请已过期，可重新提交新的投放申请。</p>
                  </div>

                  <div class="preview-card">
                    <div v-if="myApplicationImageUrl" class="preview-image-box">
                      <img :src="myApplicationImageUrl" alt="申请广告图片" class="preview-image">
                    </div>
                    <div v-else class="preview-image-box">
                      <div class="preview-image-empty">暂无图片预览</div>
                    </div>
                    <div class="preview-info-list">
                      <div class="preview-row"><span>类型</span><strong>图片广告</strong></div>
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
                  </div>

                  <div class="step-actions">
                    <button
                      v-if="canResubmitApplication"
                      class="action-btn primary"
                      type="button"
                      @click="currentStep = 1"
                    >
                      去修改
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
import { useConfirm } from '~/composables/useConfirm'
import { useLoginModal } from '~/composables/useLoginModal'
import { lockScroll, unlockScroll } from '~/composables/useScrollLock'
import { useUserStore } from '~/stores/user'

interface AdApplyFormState {
  type: 'image'
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
const { confirm } = useConfirm()
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
const positionSelectRef = ref<HTMLElement | null>(null)
const positionSelectOpen = ref(false)
const cropperVisible = ref(false)
const cropperImageSrc = ref('')
const pendingImageFile = ref<File | null>(null)

const form = reactive<AdApplyFormState>({
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
  const current = getCurrentDateTime()
  return [
    { key: 'today', label: '现在开始', value: current },
    { key: 'tomorrow', label: '明天同一时间', value: addDays(current, 1) },
    { key: 'next-monday', label: '下周一 09:00', value: getNextWeekdayDate(1) },
    { key: 'next-month', label: '下月1号 09:00', value: getNextMonthFirstDate() },
  ]
})

const endDateDisplay = computed(() => {
  if (!form.endDate) return '请选择时效后自动生成'
  return formatDateTimeDisplay(form.endDate)
})

const startDateDisplay = computed(() => {
  if (!form.startDate) return ''
  return formatDateTimeDisplay(form.startDate)
})

const positionSelectOptions = computed(() => {
  return positionOptions.map(item => ({ ...item, disabled: false }))
})

const selectedPositionLabel = computed(() => {
  return positionSelectOptions.value.find(item => item.value === form.position)?.label || '请选择投放位置'
})

const currentCropRatio = computed<[number, number]>(() => {
  return cropRatioMap[form.position] || [16, 9]
})

const canResubmitApplication = computed(() => {
  const status = myApplication.value?.status
  return status === 'rejected' || status === 'expired'
})

const resubmitNoticeText = computed(() => {
  return myApplication.value?.status === 'expired'
    ? '当前申请已过期，可重新填写并提交新的投放申请。'
    : '当前申请已被拒绝，可修改后重新提交。'
})

const currentRatioText = computed(() => {
  const [width, height] = currentCropRatio.value
  return `${width}:${height}`
})

const imagePreviewUrl = computed(() => {
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

const myApplicationImageUrl = computed(() => {
  const raw = myApplication.value?.content?.trim() || ''
  if (!raw) return ''
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

function getCurrentDateTime() {
  const now = new Date()
  now.setSeconds(0, 0)
  return formatDateTimeInput(now)
}

function getNextWeekdayDate(targetWeekday: number) {
  const now = new Date()
  const currentWeekday = now.getDay()
  let diff = (targetWeekday - currentWeekday + 7) % 7
  if (diff === 0) {
    diff = 7
  }
  const next = new Date(now.getTime() + diff * 86400000)
  next.setHours(9, 0, 0, 0)
  return formatDateTimeInput(next)
}

function getNextMonthFirstDate() {
  const now = new Date()
  const next = new Date(now.getFullYear(), now.getMonth() + 1, 1)
  next.setHours(9, 0, 0, 0)
  return formatDateTimeInput(next)
}

function formatDateTimeInput(date: Date) {
  const pad = (num: number) => String(num).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function formatDateTimeDisplay(value: string) {
  if (!value) return ''
  return value.replace('T', ' ')
}

function parseDateInput(value: string): Date | null {
  if (!value) return null
  const normalized = value.includes('T')
    ? `${value}:00`
    : value.includes(' ')
      ? value.replace(' ', 'T')
      : `${value}T00:00:00`
  const date = new Date(normalized)
  if (Number.isNaN(date.getTime())) return null
  return date
}

function addDays(startDate: string, days: number) {
  const date = parseDateInput(startDate)
  if (!date) return ''
  const next = new Date(date.getTime() + days * 86400000)
  return formatDateTimeInput(next)
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
  if (!dateValue) return ''
  if (dateValue.includes('T')) {
    return `${dateValue.replace('T', ' ')}:00`
  }
  if (dateValue.includes(' ')) {
    return `${dateValue}:00`
  }
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
    form.startDate = getCurrentDateTime()
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
    form.startDate = getCurrentDateTime()
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

function closeSelectMenus() {
  positionSelectOpen.value = false
}

function toggleSelectMenu(target: 'position') {
  positionSelectOpen.value = !positionSelectOpen.value
}

function selectPosition(position: string, disabled: boolean) {
  if (disabled) return
  form.position = position
  closeSelectMenus()
}

function handleSelectOutsideClick(event: MouseEvent) {
  const target = event.target as Node | null
  if (!target) return
  const clickedPosition = positionSelectRef.value?.contains(target)
  if (clickedPosition) return
  closeSelectMenus()
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
  form.type = 'image'
  form.position = normalizePosition(ad.position)
  form.pitAdId = typeof ad.pitAdId === 'number' ? ad.pitAdId : null
  form.content = ad.content || ''
  form.linkUrl = ad.linkUrl || ''
  form.adInfo = ad.adInfo || ''
  form.mimicContent = ad.mimicContent || ''
  form.startDate = ad.startTime ? formatDateTimeInput(new Date(ad.startTime.replace(' ', 'T'))) : getCurrentDateTime()
  form.endDate = ad.endTime ? formatDateTimeInput(new Date(ad.endTime.replace(' ', 'T'))) : ''
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
  if (!positionOrder.includes(form.position)) return '广告位置不合法'
  if (!form.pitAdId || form.pitAdId <= 0) return '请选择申请坑位'
  if (!form.startDate || !form.endDate) return '请选择起止日期'
  const selectedDays = calcDurationDaysByDate(form.startDate, form.endDate)
  if (selectedDays < 1) return '结束日期必须晚于开始日期'
  if (!selectedRule.value || selectedRule.value.durationDays !== selectedDays) {
    return '起止日期与价格规则不匹配，请按规则时效选择'
  }

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
  if (myApplication.value && !canResubmitApplication.value) {
    message.warning('已提交申请，当前不支持修改')
    currentStep.value = 2
    return
  }

  const confirmed = await confirm({
    title: '确认提交',
    message: canResubmitApplication.value
      ? '重新提交后将覆盖当前申请内容，并重新进入审核流程，确认提交吗？'
      : '提交后将进入审核流程，当前不支持修改申请内容，确认提交吗？',
    type: 'warning',
    confirmText: '确认提交',
    cancelText: '再检查下',
  })
  if (!confirmed) {
    return
  }

  const error = validateForm()
  if (error) {
    message.warning(error)
    return
  }

  submitting.value = true
  try {
    let content = form.content.trim()
    if (pendingImageFile.value) {
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
      type: 'image',
      position: form.position,
      pitAdId: form.pitAdId || undefined,
      content,
      linkUrl: form.linkUrl.trim() || undefined,
      adInfo: form.adInfo.trim() || undefined,
      mimicContent: form.position === 'post_list_card' ? (form.mimicContent.trim() || undefined) : undefined,
      insertAfter: form.position === 'post_list_card' ? 4 : undefined,
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

async function initWhenOpen() {
  form.startDate = getCurrentDateTime()

  await loadAdApplyConfig(true)
  pickPreferredPositionAndPit()
  ensurePitSelectedForPosition()
  ensureDurationSelected()

  if (!applyEnabled.value) {
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
    currentStep.value = 2
    return
  } else {
    ensurePitSelectedForPosition()
  }

  const preferredStep = adApplyModal.preferredStep.value
  if (preferredStep === 3 && myApplication.value) {
    currentStep.value = 2
    return
  }

  currentStep.value = 1
}

function handleEscClose(event: KeyboardEvent) {
  if (!visible.value) return
  if (event.key !== 'Escape') return
  if (positionSelectOpen.value) {
    closeSelectMenus()
    return
  }
  closeModal()
}

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
  document.addEventListener('mousedown', handleSelectOutsideClick)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscClose)
  document.removeEventListener('mousedown', handleSelectOutsideClick)
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
  background: rgba(15, 23, 42, 0.56);
}

.ad-apply-modal {
  --flat-surface: #ffffff;
  --flat-surface-subtle: #f8fafc;
  --flat-border: #dbe3ee;
  --flat-text: #0f172a;
  --flat-text-muted: #64748b;
  --flat-primary: #2563eb;

  width: min(1120px, 100%);
  max-height: calc(100vh - 2rem);
  border-radius: 12px;
  border: 1px solid var(--flat-border);
  background: var(--flat-surface);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.22);
  overflow: hidden;

  .dark & {
    --flat-surface: #111827;
    --flat-surface-subtle: #1f2937;
    --flat-border: #334155;
    --flat-text: #e5e7eb;
    --flat-text-muted: #94a3b8;
    --flat-primary: #60a5fa;

    border-color: var(--flat-border);
    background: var(--flat-surface);
    box-shadow: 0 14px 36px rgba(2, 6, 23, 0.5);
  }
}

.modal-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  padding: 1rem 1.1rem 0.82rem;
  border-bottom: 1px solid var(--flat-border);
  background: var(--flat-surface-subtle);

  h3 {
    margin: 0;
    font-size: 1.08rem;
    color: var(--flat-text);
  }

  p {
    margin: 0.3rem 0 0;
    color: var(--flat-text-muted);
    font-size: 0.82rem;
  }
}

.close-btn {
  width: 30px;
  height: 30px;
  border-radius: 6px;
  border: 1px solid var(--flat-border);
  background: transparent;
  color: var(--flat-text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s, color 0.2s, background-color 0.2s;

  &:hover {
    border-color: var(--flat-primary);
    color: var(--flat-primary);
    background: var(--flat-surface-subtle);
  }
}

.stepper {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 0.85rem 1.1rem;
  border-bottom: 1px solid var(--flat-border);
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.35rem;
  color: var(--flat-text-muted);

  &.active {
    color: var(--flat-primary);
  }

  &.active .step-dot {
    border-color: var(--flat-primary);
    background: var(--flat-primary);
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
  border: 2px solid var(--flat-border);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 0.78rem;
  font-weight: 700;
}

.step-label {
  font-size: 0.76rem;
  font-weight: 600;
}

.step-line {
  flex: 1;
  height: 2px;
  background: var(--flat-border);
  margin: 0 0.7rem 1.2rem;
}

.modal-body {
  max-height: calc(100vh - 10.5rem);
  overflow: auto;
  padding: 1.05rem 1.2rem 1.2rem;
}

.step-content {
  min-height: 220px;
}

.loading-text {
  text-align: center;
  padding: 1.2rem 0;
  color: var(--flat-text-muted);
}

.notice {
  border-left: 3px solid;
  border-radius: 6px;
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
  background: #eff6ff;
  border-color: #3b82f6;
  color: #1d4ed8;
}

.notice-success {
  background: #ecfdf5;
  border-color: #10b981;
  color: #047857;
}

.notice-warning {
  background: #fffbeb;
  border-color: #f59e0b;
  color: #b45309;
}

.notice-error {
  background: #fef2f2;
  border-color: #ef4444;
  color: #b91c1c;
}

.inline-btn {
  margin-left: 0.46rem;
  border: none;
  background: transparent;
  color: var(--flat-primary);
  font-weight: 700;
  cursor: pointer;
}

.apply-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.apply-layout {
  display: grid;
  grid-template-columns: minmax(280px, 0.9fr) minmax(0, 1.5fr);
  gap: 0.95rem;
  align-items: start;
}

.preview-panel,
.form-panel {
  border: 1px solid var(--flat-border);
  border-radius: 10px;
  background: var(--flat-surface-subtle);
  padding: 0.76rem;
}

.preview-panel-title {
  margin: 0 0 0.62rem;
  font-size: 0.84rem;
  color: var(--flat-text);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.8rem;
}

.form-item {
  display: flex;
  flex-direction: column;

  label {
    margin-bottom: 0.34rem;
    font-size: 0.84rem;
    font-weight: 600;
    color: var(--flat-text);
  }

  .required {
    color: #ef4444;
  }

  input,
  textarea,
  select {
    width: 100%;
    border: 1px solid var(--flat-border);
    border-radius: 8px;
    padding: 0.6rem 0.76rem;
    font-size: 0.88rem;
    background: var(--flat-surface);
    color: var(--flat-text);
    transition: border-color 0.2s, box-shadow 0.2s;

    &::placeholder {
      color: var(--flat-text-muted);
    }

    &:focus {
      outline: none;
      border-color: var(--flat-primary);
      box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
    }
  }

  textarea {
    resize: vertical;
  }
}

.custom-select {
  position: relative;
  border-radius: 10px;
  background: var(--flat-surface);
}

.custom-select-trigger {
  width: 100%;
  min-height: 40px;
  border: 1px solid var(--flat-border);
  border-radius: 10px;
  background: var(--flat-surface);
  color: var(--flat-text);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.6rem 2.15rem 0.6rem 0.76rem;
  font-size: 0.88rem;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;

  &:hover {
    border-color: var(--flat-primary);
  }

  &:focus-visible {
    outline: none;
    border-color: var(--flat-primary);
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
  }

  &.open {
    border-color: var(--flat-primary);
    box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.16);
  }
}

.custom-select-menu {
  position: absolute;
  z-index: 12;
  top: calc(100% + 0.28rem);
  left: 0;
  right: 0;
  border: 1px solid var(--flat-border);
  border-radius: 10px;
  background: var(--flat-surface);
  padding: 0.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  box-shadow: none;
}

.custom-select-option {
  width: 100%;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--flat-text);
  padding: 0.46rem 0.58rem;
  text-align: left;
  font-size: 0.84rem;
  cursor: pointer;
  transition: background-color 0.18s, color 0.18s;

  &:hover {
    background: rgba(37, 99, 235, 0.08);
    color: var(--flat-primary);
  }

  &.active {
    background: rgba(37, 99, 235, 0.12);
    color: var(--flat-primary);
    font-weight: 600;
  }

  &:disabled {
    color: var(--flat-text-muted);
    background: transparent;
    cursor: not-allowed;
  }
}

.select-arrow {
  position: absolute;
  right: 0.66rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--flat-text-muted);
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
  border: 1px solid var(--flat-border);
  border-radius: 999px;
  background: var(--flat-surface-subtle);
  color: var(--flat-text-muted);
  font-size: 0.78rem;
  padding: 0.28rem 0.62rem;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: var(--flat-primary);
    color: var(--flat-primary);
  }

  &.active {
    border-color: var(--flat-primary);
    background: rgba(37, 99, 235, 0.12);
    color: var(--flat-primary);
    font-weight: 600;
  }
}

.pit-tip {
  margin: 0.42rem 0 0;
  font-size: 0.78rem;
  color: #0f766e;
  font-weight: 600;
}

.duration-chip {
  border: 1px solid var(--flat-border);
  border-radius: 999px;
  padding: 0.33rem 0.66rem;
  background: var(--flat-surface-subtle);
  color: var(--flat-text-muted);
  font-size: 0.8rem;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &.active {
    border-color: var(--flat-primary);
    color: var(--flat-primary);
    background: rgba(37, 99, 235, 0.12);
    font-weight: 600;
  }
}

.price-tip {
  margin: 0.42rem 0 0;
  font-size: 0.8rem;
  color: var(--flat-primary);
  font-weight: 700;
}

.date-preset-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.45rem;
  margin-top: 0.5rem;
}

.date-preset-btn {
  border: 1px solid var(--flat-border);
  border-radius: 999px;
  background: var(--flat-surface-subtle);
  color: var(--flat-text-muted);
  font-size: 0.76rem;
  padding: 0.24rem 0.58rem;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: var(--flat-primary);
    color: var(--flat-primary);
  }

  &.active {
    border-color: var(--flat-primary);
    color: var(--flat-primary);
    background: rgba(37, 99, 235, 0.12);
    font-weight: 600;
  }
}


.date-hint {
  margin: 0.45rem 0 0;
  font-size: 0.76rem;
  color: var(--flat-text-muted);
}

.date-hint-before {
  margin: 0 0 0.45rem;
}

.datetime-proxy {
  position: relative;
  border-radius: 10px;
  cursor: pointer;
}

.datetime-display-input {
  width: 100%;
  font-variant-numeric: tabular-nums;
  min-height: 40px;
  padding-right: 2.6rem;
  border-radius: 10px;
  background: var(--flat-surface);
  cursor: pointer;
}

.datetime-native-input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  z-index: 2;
}

.datetime-icon {
  position: absolute;
  right: 0.62rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--flat-text-muted);
  pointer-events: none;
  width: 24px;
  height: 24px;
  border-radius: 8px;
  background: var(--flat-surface-subtle);
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.datetime-display-input {
  pointer-events: none;
}

.datetime-proxy:focus-within .datetime-display-input {
  border-color: var(--flat-primary);
  box-shadow: 0 0 0 2px rgba(37, 99, 235, 0.2);
}

.datetime-proxy:hover .datetime-display-input {
  border-color: var(--flat-primary);
}

.image-upload-box {
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
}

.image-upload-layout {
  display: grid;
  grid-template-columns: 1fr;
  align-items: start;
}

.image-upload-layout--single {
  gap: 0;
}

.image-preview-shell {
  position: relative;
  border-radius: 12px;
  overflow: hidden;
  aspect-ratio: 16 / 9;
  width: min(100%, 520px);
  max-height: 220px;
  cursor: pointer;
  border: 1px solid var(--flat-border);
  background: var(--flat-surface-subtle);
  display: flex;
  align-items: center;
  justify-content: center;

  &.slot-home_left {
    aspect-ratio: 5 / 8;
    width: min(100%, 190px);
    max-height: 300px;
  }

  &.slot-post_top,
  &.slot-post_bottom {
    aspect-ratio: 16 / 5;
    max-height: 170px;
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
  color: var(--flat-text-muted);
  text-align: center;

  strong {
    font-size: 0.86rem;
    color: var(--flat-text);
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
  background: rgba(15, 23, 42, 0.72);
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
  background: rgba(15, 23, 42, 0.48);
  opacity: 0;
  transition: opacity 0.2s;
}

.image-preview-shell:hover .image-preview-mask {
  opacity: 1;
}

.hidden-input {
  display: none;
}

.upload-hint {
  margin: 0;
  font-size: 0.76rem;
  color: var(--flat-text-muted);
}

.preview-card {
  border: 1px solid var(--flat-border);
  border-radius: 8px;
  padding: 0.62rem;
  background: var(--flat-surface-subtle);
  display: grid;
  grid-template-columns: minmax(200px, 0.75fr) minmax(0, 1.25fr);
  column-gap: 0.72rem;
  align-items: start;
}

.preview-row {
  display: flex;
  justify-content: space-between;
  gap: 0.52rem;
  padding: 0.22rem 0;
  font-size: 0.8rem;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.45);

  span {
    color: var(--flat-text-muted);
  }

  strong {
    font-weight: 600;
    text-align: right;
    color: var(--flat-text);
  }
}

.preview-image-box {
  border: 1px solid var(--flat-border);
  border-radius: 8px;
  background: transparent;
  padding: 0.35rem;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-info-list {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
}

.preview-image {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: min(56vh, 380px);
  object-fit: contain;
}

.preview-image-empty {
  font-size: 0.8rem;
  color: var(--flat-text-muted);
}

.preview-reason {
  margin-top: 0.3rem;
  padding: 0.42rem 0.52rem;
  border-radius: 6px;
  border: 1px solid rgba(239, 68, 68, 0.4);
  background: rgba(254, 242, 242, 0.92);

  span {
    font-size: 0.78rem;
    color: #dc2626;
    font-weight: 700;
  }

  p {
    margin: 0.22rem 0 0;
    font-size: 0.78rem;
    line-height: 1.42;
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

.step-actions--form-panel {
  justify-content: flex-end;
  margin-top: 0.4rem;
  padding-top: 0.2rem;
  border-top: 1px solid var(--flat-border);
}

.action-btn {
  border: 1px solid var(--flat-border);
  border-radius: 8px;
  padding: 0.52rem 1.2rem;
  background: var(--flat-surface-subtle);
  color: var(--flat-text);
  font-size: 0.84rem;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: var(--flat-primary);
    color: var(--flat-primary);
  }

  &:disabled {
    opacity: 0.58;
    cursor: not-allowed;
  }

  &.primary {
    border-color: var(--flat-primary);
    background: var(--flat-primary);
    color: #fff;

    &:hover {
      border-color: var(--flat-primary);
      background: var(--flat-primary);
      color: #fff;
    }
  }
}

@media (max-width: $breakpoint-md) {
  .ad-apply-modal {
    width: 100%;
    max-height: calc(100vh - 1rem);
  }

  .modal-body {
    max-height: calc(100vh - 9.8rem);
    padding: 0.92rem 0.88rem 1rem;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .apply-layout {
    grid-template-columns: 1fr;
  }

  .preview-panel,
  .form-panel {
    padding: 0.62rem;
  }

  .preview-card {
    grid-template-columns: 1fr;
  }

  .preview-image-box {
    padding: 0.28rem;
  }

  .preview-row {
    border-bottom-style: solid;
  }

  .step-actions--form-panel {
    justify-content: stretch;
    margin-top: 0.9rem;
    padding-top: 0;
    border-top: none;
    background: transparent;
  }

  .image-preview-shell.slot-home_left {
    width: min(100%, 200px);
  }

  .image-preview-shell {
    width: 100%;
    max-height: none;
  }

  .step-actions {
    flex-wrap: wrap;
  }

  .action-btn {
    width: 100%;
  }
}
</style>
