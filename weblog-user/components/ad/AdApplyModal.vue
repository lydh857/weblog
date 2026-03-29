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
              <span class="step-label">填写申请</span>
            </div>
            <div class="step-line" />
            <div class="step" :class="{ active: currentStep >= 3 }">
              <div class="step-dot"><span>3</span></div>
              <span class="step-label">申请状态</span>
            </div>
          </div>

          <div class="modal-body" :class="{ 'modal-body--dense': currentStep === 2 && form.position === 'post_list_card' }">
            <section v-if="currentStep === 1" class="step-content step-content--intro">
              <div v-if="configLoading" class="loading-text">正在加载申请配置...</div>
              <div v-else class="intro-layout">
                <div class="intro-main">
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
                    <button class="inline-btn" type="button" @click="currentStep = 3">查看申请状态</button>
                  </div>
                  <div v-else-if="!hasAvailablePit && !canResubmitApplication" class="notice notice-warning">当前暂无可申请坑位，请稍后再试。</div>
                  <div v-else class="step-actions step-actions--intro">
                    <button class="action-btn primary" type="button" @click="currentStep = 2">
                      {{ canResubmitApplication ? '去修改申请' : '我已了解，下一步' }}
                    </button>
                  </div>
                </div>

                <aside class="intro-side">
                  <h4>投放建议</h4>
                  <ul>
                    <li>优先上传清晰度较高的素材，避免压缩过度。</li>
                    <li>封面文案建议控制在 16 字内，移动端展示更完整。</li>
                    <li>开始时间建议预留审核缓冲，减少临期排期冲突。</li>
                    <li>文章列表拟态卡需填写标题，便于列表阅读与转化。</li>
                  </ul>
                </aside>
              </div>
            </section>

            <section v-if="currentStep === 2" class="step-content">
              <div v-if="configLoading" class="loading-text">正在加载申请配置...</div>
              <template v-else>
                <div v-if="!isLoggedIn" class="notice notice-warning">
                  登录后可提交广告申请。
                  <button class="inline-btn" type="button" @click="openLogin">去登录</button>
                </div>
                <div v-else-if="!applyEnabled" class="notice notice-error">广告申请入口当前未开放，请稍后再试。</div>
                <div v-else-if="!hasAvailablePit && !canResubmitApplication" class="notice notice-warning">当前暂无可申请坑位，请稍后再试。</div>
                <form v-else class="apply-form" @submit.prevent="handleSubmit">
                  <div v-if="canResubmitApplication" class="notice notice-warning">
                    {{ resubmitNoticeText }}
                    <button
                      v-if="myApplication?.status === 'rejected'"
                      class="inline-btn"
                      type="button"
                      @click="currentStep = 3"
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

                    <section class="form-panel" :class="{ 'form-panel--dense': form.position === 'post_list_card' }">
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
                          <div class="datetime-picker-wrap">
                            <button
                              ref="startDateTriggerRef"
                              class="datetime-proxy"
                              :class="{ open: startDatePickerOpen }"
                              type="button"
                              @click="toggleStartDatePicker"
                            >
                              <span class="datetime-display-input" :class="{ placeholder: !startDateDisplay }">
                                {{ startDateDisplay || '请选择开始时间' }}
                              </span>
                              <span class="datetime-icon">
                                <Icon name="heroicons:calendar-days-20-solid" size="18" />
                              </span>
                            </button>
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

                        <div class="form-item form-item--readonly">
                          <label>结束日期（自动计算）</label>
                          <p class="date-hint date-hint-before">结束日期会随开始日期和时效自动更新。</p>
                          <input :value="endDateDisplay" class="readonly-field" type="text" readonly aria-readonly="true">
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

                      <div v-if="form.position === 'post_list_card'" class="form-grid form-grid--post-card">
                        <div class="form-item">
                          <label>广告标题 <span class="required">*</span></label>
                          <input v-model="form.title" type="text" maxlength="100" placeholder="用于文章拟态卡展示标题">
                        </div>
                        <div class="form-item">
                          <label>拟态文案</label>
                          <textarea v-model="form.mimicContent" rows="2" maxlength="120" placeholder="用于文章列表拟态卡，不填则默认“品牌推广”" />
                        </div>
                      </div>

                      <div class="step-actions step-actions--form-panel">
                        <button class="action-btn" type="button" @click="currentStep = 1">上一步</button>
                        <button class="action-btn primary" type="submit" :disabled="submitting || !selectedRule">
                          {{ submitting ? '提交中...' : '提交投放申请' }}
                        </button>
                        <button
                          v-if="myApplication"
                          class="action-btn"
                          type="button"
                          @click="currentStep = 3"
                        >
                          查看当前申请
                        </button>
                      </div>
                    </section>
                  </div>
                </form>
              </template>
            </section>

            <section v-if="currentStep === 3" class="step-content">
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
                      @click="currentStep = 2"
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

  <Teleport to="body">
    <div
      v-if="startDatePickerOpen"
      class="datetime-picker-shell"
      :class="{ 'datetime-picker-shell--drawer': pickerDrawerMode }"
      @mousedown.stop.prevent="closeStartDatePicker"
    >
      <div
        ref="startDatePickerRef"
        class="datetime-picker-panel"
        :class="{ 'datetime-picker-panel--drawer': pickerDrawerMode }"
        :style="pickerPanelStyle"
        @mousedown.stop="handleDatePickerPanelMouseDown"
      >
        <div v-if="pickerDrawerMode" class="picker-drawer-handle" />
        <div class="picker-header">
          <button class="picker-nav-btn" type="button" @click="movePickerMonth(-1)">
            <Icon name="heroicons:chevron-left-16-solid" size="16" />
          </button>
          <button ref="pickerMonthTriggerRef" class="picker-month-trigger" type="button" @click="togglePickerYmPanel">
            {{ pickerMonthLabel }}
            <Icon name="heroicons:chevron-down-16-solid" size="14" />
          </button>
          <button class="picker-nav-btn" type="button" @click="movePickerMonth(1)">
            <Icon name="heroicons:chevron-right-16-solid" size="16" />
          </button>
        </div>
        <div class="picker-main">
          <section class="picker-calendar-main">
            <div class="picker-weekdays">
              <span v-for="weekday in pickerWeekdays" :key="weekday">{{ weekday }}</span>
            </div>
            <div class="picker-days">
              <button
                v-for="cell in pickerCalendarCells"
                :key="cell.dateKey"
                class="picker-day-btn"
                :class="{
                  muted: !cell.inCurrentMonth,
                  active: cell.dateKey === pickerSelectedDateKey,
                  today: cell.dateKey === todayDateKey,
                }"
                type="button"
                @click="selectPickerDate(cell.dateKey)"
              >
                {{ cell.day }}
              </button>
            </div>
          </section>
          <section class="picker-time-side">
            <section class="picker-time-column">
              <span class="picker-time-label">小时</span>
              <div ref="pickerHourListRef" class="picker-time-list" role="listbox" aria-label="小时选择">
                <button
                  v-for="hour in pickerHourOptions"
                  :key="hour"
                :data-value="hour"
                class="picker-time-option"
                :class="{ active: hour === pickerSelectedHour }"
                type="button"
                tabindex="-1"
                @mousedown.prevent
                @click="selectPickerHour(hour)"
              >
                  {{ formatTwoDigit(hour) }}
                </button>
              </div>
            </section>
            <section class="picker-time-column">
              <span class="picker-time-label">分钟</span>
              <div ref="pickerMinuteListRef" class="picker-time-list" role="listbox" aria-label="分钟选择">
                <button
                  v-for="minute in pickerMinuteOptions"
                  :key="minute"
                :data-value="minute"
                class="picker-time-option"
                :class="{ active: minute === pickerSelectedMinute }"
                type="button"
                tabindex="-1"
                @mousedown.prevent
                @click="selectPickerMinute(minute)"
              >
                  {{ formatTwoDigit(minute) }}
                </button>
              </div>
            </section>
          </section>
        </div>
        <div class="picker-actions">
          <button class="picker-action-btn" type="button" @click="applyPickerNow">此时</button>
          <button class="picker-action-btn primary" type="button" @click="closeStartDatePicker">完成</button>
        </div>
      </div>
    </div>

    <div
      v-if="startDatePickerOpen && pickerYmPanelOpen"
      ref="pickerYmPopupRef"
      class="picker-ym-popup"
      :class="{ 'picker-ym-popup--drawer': pickerYmDrawerMode }"
      :style="pickerYmPopupStyle"
      @mousedown.self="pickerYmPanelOpen = false"
    >
      <div class="picker-ym-popup-body">
        <section class="picker-ym-column">
          <span class="picker-time-label">年份</span>
          <div ref="pickerYearListRef" class="picker-time-list" role="listbox" aria-label="年份选择">
            <button
              v-for="year in pickerYearOptions"
              :key="year"
              :data-value="year"
              class="picker-time-option"
              :class="{ active: year === pickerViewDate.getFullYear() }"
              type="button"
              tabindex="-1"
              @mousedown.prevent
              @click="selectPickerYear(year)"
            >
              {{ year }}
            </button>
          </div>
        </section>
        <section class="picker-ym-column">
          <span class="picker-time-label">月份</span>
          <div ref="pickerMonthListRef" class="picker-time-list" role="listbox" aria-label="月份选择">
            <button
              v-for="month in pickerMonthOptions"
              :key="month"
              :data-value="month"
              class="picker-time-option"
              :class="{ active: month === pickerViewDate.getMonth() + 1 }"
              type="button"
              tabindex="-1"
              @mousedown.prevent
              @click="selectPickerMonth(month)"
            >
              {{ formatTwoDigit(month) }}
            </button>
          </div>
        </section>
      </div>
    </div>
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
  title: string
  mimicContent: string
}

interface PickerCalendarCell {
  dateKey: string
  day: number
  inCurrentMonth: boolean
}

interface ViewportMetrics {
  width: number
  height: number
  offsetLeft: number
  offsetTop: number
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
const startDateTriggerRef = ref<HTMLElement | null>(null)
const pickerMonthTriggerRef = ref<HTMLElement | null>(null)
const startDatePickerRef = ref<HTMLElement | null>(null)
const pickerYmPopupRef = ref<HTMLElement | null>(null)
const positionSelectRef = ref<HTMLElement | null>(null)
const positionSelectOpen = ref(false)
const startDatePickerOpen = ref(false)
const pickerViewDate = ref(new Date())
const pickerSelectedDateKey = ref('')
const pickerSelectedHour = ref(0)
const pickerSelectedMinute = ref(0)
const pickerYmPanelOpen = ref(false)
const pickerDrawerMode = ref(false)
const pickerYmDrawerMode = ref(false)
const pickerHourListRef = ref<HTMLElement | null>(null)
const pickerMinuteListRef = ref<HTMLElement | null>(null)
const pickerYearListRef = ref<HTMLElement | null>(null)
const pickerMonthListRef = ref<HTMLElement | null>(null)
const cropperVisible = ref(false)
const cropperImageSrc = ref('')
const pendingImageFile = ref<File | null>(null)

const pickerPanelPosition = reactive({ top: 0, left: 0 })
const pickerYmPopupPosition = reactive({ top: 0, left: 0 })

const pickerPanelStyle = computed(() => {
  if (pickerDrawerMode.value) return undefined
  return {
    top: `${pickerPanelPosition.top}px`,
    left: `${pickerPanelPosition.left}px`,
  }
})

const pickerYmPopupStyle = computed(() => {
  if (pickerYmDrawerMode.value) return undefined
  return {
    top: `${pickerYmPopupPosition.top}px`,
    left: `${pickerYmPopupPosition.left}px`,
  }
})

let pickerPositionRaf = 0

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
  title: '',
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

const pickerWeekdays = ['一', '二', '三', '四', '五', '六', '日']
const pickerHourOptions = Array.from({ length: 24 }, (_, index) => index)
const pickerMinuteOptions = Array.from({ length: 60 }, (_, index) => index)
const pickerMonthOptions = Array.from({ length: 12 }, (_, index) => index + 1)

const pickerYearOptions = computed(() => {
  const center = pickerViewDate.value.getFullYear()
  return Array.from({ length: 61 }, (_, index) => center - 30 + index)
})

const todayDateKey = computed(() => toDateOnlyKey(new Date()))

const pickerMonthLabel = computed(() => {
  const year = pickerViewDate.value.getFullYear()
  const month = pickerViewDate.value.getMonth() + 1
  return `${year}年${formatTwoDigit(month)}月`
})

const pickerCalendarCells = computed<PickerCalendarCell[]>(() => {
  const year = pickerViewDate.value.getFullYear()
  const month = pickerViewDate.value.getMonth()
  const firstDay = new Date(year, month, 1)
  const offset = (firstDay.getDay() + 6) % 7
  const start = new Date(year, month, 1 - offset)

  return Array.from({ length: 42 }, (_, index) => {
    const current = new Date(start)
    current.setDate(start.getDate() + index)
    return {
      dateKey: toDateOnlyKey(current),
      day: current.getDate(),
      inCurrentMonth: current.getMonth() === month,
    }
  })
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
  return `${date.getFullYear()}-${formatTwoDigit(date.getMonth() + 1)}-${formatTwoDigit(date.getDate())}T${formatTwoDigit(date.getHours())}:${formatTwoDigit(date.getMinutes())}`
}

function formatTwoDigit(value: number) {
  return String(value).padStart(2, '0')
}

function toDateOnlyKey(date: Date) {
  return `${date.getFullYear()}-${formatTwoDigit(date.getMonth() + 1)}-${formatTwoDigit(date.getDate())}`
}

function parseDateOnlyKey(value: string): Date | null {
  const matched = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!matched) return null
  const year = Number(matched[1])
  const month = Number(matched[2]) - 1
  const day = Number(matched[3])
  const date = new Date(year, month, day)
  if (Number.isNaN(date.getTime())) return null
  return date
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
    const firstOption = options[0]
    form.pitAdId = firstOption ? firstOption.pitAdId : null
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
    const firstRule = currentPositionRules.value[0]
    form.durationDays = firstRule ? firstRule.durationDays : 0
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
    if (startDatePickerOpen.value) {
      syncPickerFromStartDate()
    }
    return
  }
  if (currentPositionRules.value.length > 0) {
    const firstRule = currentPositionRules.value[0]
    form.durationDays = firstRule ? firstRule.durationDays : 0
    form.endDate = addDays(form.startDate, form.durationDays)
  }
  if (startDatePickerOpen.value) {
    syncPickerFromStartDate()
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
    const firstRule = currentPositionRules.value[0]
    form.durationDays = firstRule ? firstRule.durationDays : 0
    form.endDate = addDays(form.startDate, form.durationDays)
  }
}

function syncPickerFromStartDate() {
  const parsed = parseDateInput(form.startDate) || new Date()
  parsed.setSeconds(0, 0)
  pickerViewDate.value = new Date(parsed.getFullYear(), parsed.getMonth(), 1)
  pickerSelectedDateKey.value = toDateOnlyKey(parsed)
  pickerSelectedHour.value = parsed.getHours()
  pickerSelectedMinute.value = parsed.getMinutes()
}

function scrollPickerListToSelected(listRef: HTMLElement | null, selectedValue: number) {
  if (!listRef) return
  const selected = listRef.querySelector<HTMLElement>(`[data-value="${selectedValue}"]`)
  if (!selected) return
  selected.scrollIntoView({ block: 'center', inline: 'nearest', behavior: 'auto' })

  const listRect = listRef.getBoundingClientRect()
  const selectedRect = selected.getBoundingClientRect()
  const currentCenter = selectedRect.top + selectedRect.height / 2
  const targetCenter = listRect.top + listRect.height / 2
  const delta = currentCenter - targetCenter

  if (Math.abs(delta) > 1) {
    const maxScrollTop = Math.max(0, listRef.scrollHeight - listRef.clientHeight)
    const nextTop = Math.min(maxScrollTop, Math.max(0, listRef.scrollTop + delta))
    listRef.scrollTop = nextTop
  }
}

function blurActivePickerOption() {
  const activeElement = document.activeElement
  if (activeElement instanceof HTMLElement && activeElement.classList.contains('picker-time-option')) {
    activeElement.blur()
  }
}

function syncPickerListScrollAfterRender(callback: () => void) {
  nextTick(() => {
    callback()
    requestAnimationFrame(() => {
      blurActivePickerOption()
      callback()
      requestAnimationFrame(() => {
        callback()
      })
    })
  })
}

function syncPickerTimeListScroll() {
  syncPickerListScrollAfterRender(() => {
    scrollPickerListToSelected(pickerHourListRef.value, pickerSelectedHour.value)
    scrollPickerListToSelected(pickerMinuteListRef.value, pickerSelectedMinute.value)
  })
}

function syncPickerYmListScroll() {
  syncPickerListScrollAfterRender(() => {
    scrollPickerListToSelected(pickerYearListRef.value, pickerViewDate.value.getFullYear())
    scrollPickerListToSelected(pickerMonthListRef.value, pickerViewDate.value.getMonth() + 1)
  })
}

function clampValue(value: number, min: number, max: number) {
  return Math.min(max, Math.max(min, value))
}

function syncPickerYmViewportMode() {
  const isMobileViewport = window.matchMedia('(max-width: 768px)').matches
  pickerDrawerMode.value = isMobileViewport
  pickerYmDrawerMode.value = isMobileViewport
}

function getViewportMetrics(): ViewportMetrics {
  const visualViewport = window.visualViewport
  if (!visualViewport) {
    return {
      width: window.innerWidth,
      height: window.innerHeight,
      offsetLeft: 0,
      offsetTop: 0,
    }
  }

  return {
    width: Math.max(0, visualViewport.width),
    height: Math.max(0, visualViewport.height),
    offsetLeft: visualViewport.offsetLeft,
    offsetTop: visualViewport.offsetTop,
  }
}

function updateStartDatePickerPosition() {
  if (!startDatePickerOpen.value) return
  syncPickerYmViewportMode()
  if (pickerDrawerMode.value) return
  const trigger = startDateTriggerRef.value
  const panel = startDatePickerRef.value
  if (!trigger || !panel) return

  const viewport = getViewportMetrics()
  const triggerRect = trigger.getBoundingClientRect()
  const panelRect = panel.getBoundingClientRect()
  const panelWidth = panelRect.width || 700
  const panelHeight = panelRect.height || 500
  const edgePadding = 8
  const offset = 8

  const minLeft = viewport.offsetLeft + edgePadding
  const maxLeft = Math.max(minLeft, viewport.offsetLeft + viewport.width - panelWidth - edgePadding)
  const left = clampValue(triggerRect.left, minLeft, maxLeft)

  let top = triggerRect.bottom + offset
  const viewportBottom = viewport.offsetTop + viewport.height
  if (top + panelHeight + edgePadding > viewportBottom) {
    top = triggerRect.top - panelHeight - offset
  }
  const minTop = viewport.offsetTop + edgePadding
  const maxTop = Math.max(minTop, viewportBottom - panelHeight - edgePadding)

  pickerPanelPosition.left = Math.round(left)
  pickerPanelPosition.top = Math.round(clampValue(top, minTop, maxTop))
}

function updatePickerYmPopupPosition() {
  if (!startDatePickerOpen.value || !pickerYmPanelOpen.value) return
  syncPickerYmViewportMode()
  if (pickerYmDrawerMode.value) return

  const trigger = pickerMonthTriggerRef.value
  const popup = pickerYmPopupRef.value
  if (!trigger || !popup) return

  const viewport = getViewportMetrics()
  const triggerRect = trigger.getBoundingClientRect()
  const popupRect = popup.getBoundingClientRect()
  const popupWidth = popupRect.width || 340
  const popupHeight = popupRect.height || 300
  const edgePadding = 8
  const offset = 8

  const minLeft = viewport.offsetLeft + edgePadding
  const maxLeft = Math.max(minLeft, viewport.offsetLeft + viewport.width - popupWidth - edgePadding)
  const centerByTrigger = triggerRect.left + (triggerRect.width - popupWidth) / 2
  const left = clampValue(centerByTrigger, minLeft, maxLeft)

  let top = triggerRect.bottom + offset
  const minTop = viewport.offsetTop + edgePadding
  const viewportBottom = viewport.offsetTop + viewport.height
  if (top + popupHeight + edgePadding > viewportBottom) {
    top = triggerRect.top - popupHeight - offset
  }
  const maxTop = Math.max(minTop, viewportBottom - popupHeight - edgePadding)

  pickerYmPopupPosition.left = Math.round(left)
  pickerYmPopupPosition.top = Math.round(clampValue(top, minTop, maxTop))
}

function syncFloatingPickerPosition() {
  nextTick(() => {
    updateStartDatePickerPosition()
    if (pickerYmPanelOpen.value) {
      updatePickerYmPopupPosition()
    }
  })
}

function handleFloatingPanelViewportChange() {
  if (!startDatePickerOpen.value) return
  syncPickerYmViewportMode()
  if (pickerPositionRaf) {
    cancelAnimationFrame(pickerPositionRaf)
  }
  pickerPositionRaf = requestAnimationFrame(() => {
    pickerPositionRaf = 0
    updateStartDatePickerPosition()
    if (pickerYmPanelOpen.value) {
      updatePickerYmPopupPosition()
    }
  })
}

function buildPickerStartDate() {
  if (!pickerSelectedDateKey.value) return ''
  return `${pickerSelectedDateKey.value}T${formatTwoDigit(pickerSelectedHour.value)}:${formatTwoDigit(pickerSelectedMinute.value)}`
}

function applyPickerValueToForm() {
  const next = buildPickerStartDate()
  if (!next) return
  form.startDate = next
  handleStartDateChange()
}

function openStartDatePicker() {
  syncPickerYmViewportMode()
  syncPickerFromStartDate()
  startDatePickerOpen.value = true
  pickerYmPanelOpen.value = false
  syncFloatingPickerPosition()
  syncPickerTimeListScroll()
}

function closeStartDatePicker() {
  startDatePickerOpen.value = false
  pickerYmPanelOpen.value = false
}

function toggleStartDatePicker() {
  if (startDatePickerOpen.value) {
    closeStartDatePicker()
    return
  }
  openStartDatePicker()
}

function movePickerMonth(offset: number) {
  const base = pickerViewDate.value
  pickerViewDate.value = new Date(base.getFullYear(), base.getMonth() + offset, 1)
  if (pickerYmPanelOpen.value) {
    syncPickerYmListScroll()
    nextTick(updatePickerYmPopupPosition)
  }
}

function togglePickerYmPanel() {
  syncPickerYmViewportMode()
  pickerYmPanelOpen.value = !pickerYmPanelOpen.value
  if (pickerYmPanelOpen.value) {
    syncFloatingPickerPosition()
    syncPickerYmListScroll()
    nextTick(updatePickerYmPopupPosition)
  }
}

function selectPickerYear(year: number) {
  if (!Number.isInteger(year)) return
  if (year === pickerViewDate.value.getFullYear()) return
  const month = pickerViewDate.value.getMonth()
  pickerViewDate.value = new Date(year, month, 1)
  syncPickerYmListScroll()
}

function selectPickerMonth(month: number) {
  if (!Number.isInteger(month) || month < 1 || month > 12) return
  const year = pickerViewDate.value.getFullYear()
  pickerViewDate.value = new Date(year, month - 1, 1)
  pickerYmPanelOpen.value = false
}

function selectPickerDate(dateKey: string) {
  const parsed = parseDateOnlyKey(dateKey)
  if (!parsed) return
  pickerSelectedDateKey.value = toDateOnlyKey(parsed)
  applyPickerValueToForm()
}

function selectPickerHour(hour: number) {
  if (!Number.isInteger(hour) || hour < 0 || hour > 23) return
  pickerSelectedHour.value = hour
  applyPickerValueToForm()
  syncPickerTimeListScroll()
}

function selectPickerMinute(minute: number) {
  if (!Number.isInteger(minute) || minute < 0 || minute > 59) return
  pickerSelectedMinute.value = minute
  applyPickerValueToForm()
  syncPickerTimeListScroll()
}

function applyPickerNow() {
  const now = new Date()
  now.setSeconds(0, 0)
  form.startDate = formatDateTimeInput(now)
  handleStartDateChange()
  syncPickerFromStartDate()
  pickerYmPanelOpen.value = false
  syncPickerTimeListScroll()
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
  if (!clickedPosition) {
    closeSelectMenus()
  }

  const clickedYmPopup = pickerYmPopupRef.value?.contains(target)
  const clickedYmTrigger = pickerMonthTriggerRef.value?.contains(target)
  if (pickerYmPanelOpen.value && !clickedYmPopup && !clickedYmTrigger) {
    pickerYmPanelOpen.value = false
    return
  }

  const clickedDateTrigger = startDateTriggerRef.value?.contains(target)
  const clickedDatePanel = startDatePickerRef.value?.contains(target)
  if (!clickedDateTrigger && !clickedDatePanel && !clickedYmPopup) {
    closeStartDatePicker()
  }
}

function handleDatePickerPanelMouseDown(event: MouseEvent) {
  if (!pickerYmPanelOpen.value) return
  const target = event.target as Node | null
  if (!target) return
  const clickedYmPopup = pickerYmPopupRef.value?.contains(target)
  const clickedYmTrigger = pickerMonthTriggerRef.value?.contains(target)
  if (!clickedYmPopup && !clickedYmTrigger) {
    pickerYmPanelOpen.value = false
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
  form.type = 'image'
  form.position = normalizePosition(ad.position)
  form.pitAdId = typeof ad.pitAdId === 'number' ? ad.pitAdId : null
  form.content = ad.content || ''
  form.linkUrl = ad.linkUrl || ''
  form.adInfo = ad.adInfo || ''
  form.title = ad.title || ''
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

  if (form.position === 'post_list_card') {
    const adTitle = form.title.trim()
    if (!adTitle) {
      return '请输入广告标题'
    }
    if (adTitle.length > 100) {
      return '广告标题长度不能超过100个字符'
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
    currentStep.value = 3
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
      title: form.position === 'post_list_card' ? form.title.trim() : undefined,
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
    currentStep.value = 3
    adApplyModal.notifyApplicationChanged()
  } catch (error) {
    const messageText = error && typeof error === 'object' && 'message' in error
      ? String((error as { message?: unknown }).message || '').trim()
      : ''
    message.error(messageText || '提交失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}

async function initWhenOpen() {
  closeSelectMenus()
  closeStartDatePicker()
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
  const preferredStep = adApplyModal.preferredStep.value

  if (myApplication.value) {
    syncFormFromApplication(myApplication.value)
    if (preferredStep === 2 && canResubmitApplication.value) {
      currentStep.value = 2
      return
    }
    currentStep.value = 3
    return
  } else {
    ensurePitSelectedForPosition()
  }

  if (preferredStep === 2) {
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
  if (pickerYmPanelOpen.value) {
    pickerYmPanelOpen.value = false
    return
  }
  if (startDatePickerOpen.value) {
    closeStartDatePicker()
    return
  }
  closeModal()
}

watch(() => form.position, async () => {
  ensurePitSelectedForPosition()
  ensureDurationSelected()
  if (!visible.value || !isLoggedIn.value) return
  await refreshMyApplication()
  if (startDatePickerOpen.value) {
    syncFloatingPickerPosition()
  }
})

watch(() => pitOptions.value, () => {
  if (!hasPitForPosition(form.position)) {
    pickPreferredPositionAndPit()
  }
  ensurePitSelectedForPosition()
}, { deep: true })

watch(() => form.startDate, () => {
  if (!startDatePickerOpen.value) return
  syncPickerFromStartDate()
  syncPickerTimeListScroll()
  if (pickerYmPanelOpen.value) {
    syncPickerYmListScroll()
    nextTick(updatePickerYmPopupPosition)
  }
  syncFloatingPickerPosition()
})

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
  closeSelectMenus()
  closeStartDatePicker()
})

onMounted(() => {
  syncPickerYmViewportMode()
  document.addEventListener('keydown', handleEscClose)
  document.addEventListener('mousedown', handleSelectOutsideClick)
  window.addEventListener('resize', handleFloatingPanelViewportChange)
  window.addEventListener('scroll', handleFloatingPanelViewportChange, true)
  window.visualViewport?.addEventListener('resize', handleFloatingPanelViewportChange)
  window.visualViewport?.addEventListener('scroll', handleFloatingPanelViewportChange)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscClose)
  document.removeEventListener('mousedown', handleSelectOutsideClick)
  window.removeEventListener('resize', handleFloatingPanelViewportChange)
  window.removeEventListener('scroll', handleFloatingPanelViewportChange, true)
  window.visualViewport?.removeEventListener('resize', handleFloatingPanelViewportChange)
  window.visualViewport?.removeEventListener('scroll', handleFloatingPanelViewportChange)
  if (pickerPositionRaf) {
    cancelAnimationFrame(pickerPositionRaf)
    pickerPositionRaf = 0
  }
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
  transition: opacity 0.25s ease;
}

.ad-apply-fade-enter-active .ad-apply-modal,
.ad-apply-fade-leave-active .ad-apply-modal {
  transition: transform 0.25s ease, opacity 0.25s ease;
}

.ad-apply-fade-enter-from,
.ad-apply-fade-leave-to {
  opacity: 0;

  .ad-apply-modal {
    transform: translateY(20px) scale(0.96);
    opacity: 0;
  }
}

.ad-apply-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-modal);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background: rgba(0, 0, 0, 0.5);
}

.ad-apply-modal {
  --flat-surface: #ffffff;
  --flat-surface-subtle: #f8fafc;
  --flat-border: #dbe3ee;
  --flat-text: #0f172a;
  --flat-text-muted: #64748b;
  --flat-primary: #2563eb;
  --flat-primary-soft: rgba(37, 99, 235, 0.12);
  --flat-media-surface: linear-gradient(180deg, #f3f6fb, #e8edf5);
  --flat-media-dark-overlay: rgba(15, 23, 42, 0.48);

  width: min(1120px, 100%);
  height: min(840px, calc(100vh - 1rem));
  max-height: calc(100vh - 1rem);
  border-radius: 12px;
  border: 1px solid var(--flat-border);
  background: var(--flat-surface);
  box-shadow: 0 14px 36px rgba(15, 23, 42, 0.22);
  overflow: hidden;
  display: flex;
  flex-direction: column;

  .dark & {
    --flat-surface: #171b20;
    --flat-surface-subtle: #1d232a;
    --flat-border: #2a313a;
    --flat-text: #d6dbe4;
    --flat-text-muted: #9aa5b5;
    --flat-primary: #60a5fa;
    --flat-primary-soft: rgba(96, 165, 250, 0.18);
    --flat-media-surface: linear-gradient(180deg, #1d232a, #171b20);
    --flat-media-dark-overlay: rgba(2, 6, 23, 0.58);

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
    border-color: var(--status-success);
    background: var(--status-success);
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
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 0.94rem 1rem 1rem;
  overscroll-behavior: contain;
  scrollbar-gutter: stable both-edges;
  scrollbar-width: thin;
  scrollbar-color: rgba(100, 116, 139, 0.42) transparent;
}

.modal-body--dense {
  padding-bottom: 0.74rem;
}

.modal-body::-webkit-scrollbar {
  width: 8px;
}

.modal-body::-webkit-scrollbar-track {
  background: transparent;
}

.modal-body::-webkit-scrollbar-thumb {
  background: rgba(100, 116, 139, 0.42);
  border-radius: 999px;
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
  background: var(--status-info-soft-bg);
  border-color: var(--status-info-soft-border);
  color: var(--status-info);

  .dark & {
    background: rgba(24, 144, 255, 0.1);
    border-color: rgba(147, 197, 253, 0.34);
    color: #93c5fd;
  }
}

.notice-success {
  background: var(--status-success-soft-bg);
  border-color: var(--status-success-soft-border);
  color: var(--status-success);

  .dark & {
    background: rgba(82, 196, 26, 0.1);
    border-color: rgba(134, 239, 172, 0.42);
    color: #86efac;
  }
}

.notice-warning {
  background: var(--status-warning-soft-bg);
  border-color: var(--status-warning-soft-border);
  color: var(--status-warning);

  .dark & {
    background: rgba(250, 173, 20, 0.1);
    border-color: rgba(251, 191, 36, 0.42);
    color: #fbbf24;
  }
}

.notice-error {
  background: var(--status-danger-soft-bg);
  border-color: var(--status-danger-soft-border);
  color: var(--status-danger);

  .dark & {
    background: rgba(245, 34, 45, 0.1);
    border-color: rgba(251, 113, 133, 0.42);
    color: #fb7185;
  }
}

.step-content--intro {
  min-height: 520px;
}

.intro-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.15fr) minmax(220px, 0.85fr);
  gap: 0.86rem;
  min-height: 100%;
}

.intro-main,
.intro-side {
  border: 1px solid var(--flat-border);
  border-radius: 10px;
  background: var(--flat-surface-subtle);
  padding: 0.72rem;
}

.intro-side h4 {
  margin: 0 0 0.46rem;
  font-size: 0.86rem;
  color: var(--flat-text);
}

.intro-side ul {
  margin: 0;
  padding-left: 1.05rem;
  display: flex;
  flex-direction: column;
  gap: 0.46rem;
  color: var(--flat-text-muted);
  font-size: 0.8rem;
  line-height: 1.55;
}

.step-actions--intro {
  margin-top: 1.4rem;
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
  gap: 0.86rem;
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
  padding: 0.68rem;
}

.preview-panel-title {
  margin: 0 0 0.62rem;
  font-size: 0.84rem;
  color: var(--flat-text);
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.68rem;
}

.form-grid--post-card {
  margin-top: 0.08rem;
}

.form-panel--dense {
  padding: 0.62rem;
}

.form-panel--dense .form-grid {
  gap: 0.58rem;
}

.form-panel--dense .date-preset-row {
  margin-top: 0.42rem;
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
    font-family: inherit;
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
    line-height: 1.45;

    &::placeholder {
      font-family: inherit;
      font-size: inherit;
      line-height: 1.45;
    }
  }
}

.form-item--readonly {
  label {
    color: var(--flat-text-muted);
  }

  .date-hint {
    color: rgba(100, 116, 139, 0.86);
  }
}

.readonly-field {
  background: linear-gradient(180deg, rgba(148, 163, 184, 0.08), rgba(148, 163, 184, 0.04));
  border-color: rgba(148, 163, 184, 0.45);
  color: var(--flat-text-muted);
  cursor: not-allowed;
  user-select: text;
}

.readonly-field:focus,
.readonly-field:focus-visible {
  outline: none;
  border-color: rgba(148, 163, 184, 0.45);
  box-shadow: none;
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
  max-height: min(280px, 48vh);
  overflow: auto;
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
  border-radius: 6px;
  background: var(--flat-surface);
  color: var(--flat-text-muted);
  font-size: 0.75rem;
  padding: 0.32rem 0.62rem;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease, transform 0.12s ease;

  &:hover {
    border-color: var(--flat-primary);
    color: var(--flat-primary);
    background: var(--flat-primary-soft);
  }

  &:focus-visible {
    outline: none;
    border-color: var(--flat-primary);
    box-shadow: 0 0 0 2px var(--flat-primary-soft);
  }

  &:active {
    transform: translateY(1px);
  }

  &.active {
    border-color: var(--flat-primary);
    color: var(--flat-surface);
    background: var(--flat-primary);
    font-weight: 600;
    box-shadow: 0 1px 0 rgba(15, 23, 42, 0.08);
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

.datetime-picker-wrap {
  position: relative;
}

.datetime-proxy {
  width: 100%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  text-align: left;
  padding: 0;
  border: 1px solid var(--flat-border);
  border-radius: 8px;
  background: var(--flat-surface);
  cursor: pointer;
  transition: border-color 0.2s ease, box-shadow 0.2s ease, background-color 0.2s ease;

  &:hover,
  &:focus-visible {
    border-color: var(--flat-primary);
    background: var(--flat-primary-soft);
  }

  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 2px var(--flat-primary-soft);
  }

  &.open {
    border-color: var(--flat-primary);
    box-shadow: 0 0 0 2px var(--flat-primary-soft);
    background: var(--flat-primary-soft);
  }
}

.datetime-display-input {
  flex: 1;
  font-variant-numeric: tabular-nums;
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  padding: 0.58rem 2.6rem 0.58rem 0.76rem;
  color: var(--flat-text);

  &.placeholder {
    color: var(--flat-text-muted);
  }
}

.datetime-icon {
  position: absolute;
  right: 0.62rem;
  top: 50%;
  transform: translateY(-50%);
  color: var(--flat-text-muted);
  pointer-events: none;
  width: 20px;
  height: 20px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.datetime-proxy:hover .datetime-icon,
.datetime-proxy:focus-visible .datetime-icon,
.datetime-proxy.open .datetime-icon {
  color: var(--flat-primary);
}

.datetime-picker-shell {
  --picker-surface: #ffffff;
  --picker-surface-subtle: #f8fafc;
  --picker-border: #dbe3ee;
  --picker-text: #0f172a;
  --picker-text-muted: #64748b;
  --picker-primary: #2563eb;
  --picker-primary-soft: rgba(37, 99, 235, 0.12);

  position: fixed;
  inset: 0;
  z-index: calc(var(--z-modal) + 119);
  background: transparent;
  overflow: visible;
  isolation: isolate;
  pointer-events: none;
}

.datetime-picker-shell--drawer {
  background: rgba(15, 23, 42, 0.38);
  pointer-events: auto;
  display: flex;
  align-items: flex-end;
  justify-content: stretch;
  padding-top: 18dvh;
}

:global(html.dark) .datetime-picker-shell--drawer,
:global(body.dark) .datetime-picker-shell--drawer,
:global(.dark) .datetime-picker-shell--drawer {
  background: rgba(2, 6, 23, 0.56);
}

:global(html.dark) .datetime-picker-shell,
:global(body.dark) .datetime-picker-shell,
:global(.dark) .datetime-picker-shell {
  --picker-surface: #171b20;
  --picker-surface-subtle: #1d232a;
  --picker-border: #2a313a;
  --picker-text: #d6dbe4;
  --picker-text-muted: #9aa5b5;
  --picker-primary: #60a5fa;
  --picker-primary-soft: rgba(96, 165, 250, 0.18);
}

.datetime-picker-panel {
  position: absolute;
  z-index: calc(var(--z-modal) + 120);
  width: min(520px, calc(100vw - 12px));
  max-height: calc(100dvh - 12px);
  border: 1px solid var(--picker-border);
  border-radius: 10px;
  background: var(--picker-surface);
  background-color: var(--picker-surface);
  background-image: none;
  background-clip: padding-box;
  isolation: isolate;
  color: var(--picker-text);
  opacity: 1;
  box-shadow: 0 18px 36px rgba(15, 23, 42, 0.2);
  padding: 0.56rem;
  overflow: auto;
  overscroll-behavior: contain;
  pointer-events: auto;
}

.datetime-picker-panel--drawer {
  position: relative;
  inset: auto;
  width: 100%;
  max-width: 100%;
  max-height: min(82dvh, 720px);
  margin-top: auto;
  border-radius: 20px 20px 0 0;
  border-bottom: none;
  box-shadow: 0 -18px 34px rgba(15, 23, 42, 0.28);
  padding: 0.56rem 0.88rem calc(0.9rem + env(safe-area-inset-bottom));
}

.picker-drawer-handle {
  width: 48px;
  height: 5px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.5);
  margin: 0 auto 0.72rem;
}

.picker-ym-popup {
  --picker-surface: #ffffff;
  --picker-surface-subtle: #f8fafc;
  --picker-border: #dbe3ee;
  --picker-text: #0f172a;
  --picker-text-muted: #64748b;
  --picker-primary: #2563eb;
  --picker-primary-soft: rgba(37, 99, 235, 0.12);

  position: fixed;
  z-index: calc(var(--z-modal) + 122);
  width: min(332px, calc(100vw - 16px));
  max-height: calc(100dvh - 16px);
  border: 1px solid var(--picker-border);
  border-radius: 10px;
  background: var(--picker-surface);
  background-color: var(--picker-surface);
  background-image: none;
  background-clip: padding-box;
  isolation: isolate;
  color: var(--picker-text);
  opacity: 1;
  box-shadow: 0 16px 34px rgba(15, 23, 42, 0.22);
  padding: 0.5rem;
  overflow: auto;
}

:global(html.dark) .picker-ym-popup,
:global(body.dark) .picker-ym-popup,
:global(.dark) .picker-ym-popup {
  --picker-surface: #171b20;
  --picker-surface-subtle: #1d232a;
  --picker-border: #2a313a;
  --picker-text: #d6dbe4;
  --picker-text-muted: #9aa5b5;
  --picker-primary: #60a5fa;
  --picker-primary-soft: rgba(96, 165, 250, 0.18);
}

.picker-ym-popup--drawer {
  width: 100%;
}

.picker-ym-popup-body {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.32rem;
}

.picker-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 0.45rem;
}

.picker-nav-btn {
  width: 30px;
  height: 30px;
  border: 1px solid var(--picker-border);
  border-radius: 8px;
  background: var(--picker-surface);
  color: var(--picker-text-muted);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: var(--picker-primary);
    color: var(--picker-primary);
    background: var(--picker-primary-soft);
  }
}

.picker-month-trigger {
  border: 1px solid var(--picker-border);
  border-radius: 8px;
  background: var(--picker-surface);
  color: var(--picker-text);
  min-height: 30px;
  padding: 0 0.56rem;
  font-size: 0.82rem;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.26rem;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: var(--picker-primary);
    color: var(--picker-primary);
    background: var(--picker-primary-soft);
  }
}

.picker-ym-column {
  display: flex;
  flex-direction: column;
  gap: 0.22rem;
}

.picker-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(154px, 0.5fr);
  gap: 0.36rem;
  align-items: start;
}

.picker-calendar-main {
  min-width: 0;
}

.picker-time-side {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 0.34rem;
  min-width: 0;
}

.picker-weekdays,
.picker-days {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 0.22rem;
}

.picker-weekdays {
  margin-bottom: 0.18rem;

  span {
    text-align: center;
    font-size: 0.72rem;
    color: var(--picker-text-muted);
    font-weight: 600;
    padding: 0.18rem 0;
  }
}

.picker-day-btn {
  min-height: 28px;
  border: 1px solid var(--picker-border);
  border-radius: 8px;
  background: var(--picker-surface-subtle);
  color: var(--picker-text);
  font-size: 0.76rem;
  cursor: pointer;
  transition: border-color 0.16s ease, color 0.16s ease, background-color 0.16s ease;

  &:hover {
    border-color: var(--picker-primary);
    color: var(--picker-primary);
    background: var(--picker-primary-soft);
  }

  &.muted {
    color: rgba(100, 116, 139, 0.7);
  }

  &.today {
    border-color: rgba(37, 99, 235, 0.45);
  }

  &.active {
    border-color: var(--picker-primary);
    background: var(--picker-primary);
    color: var(--picker-surface);
    font-weight: 700;
  }
}

.picker-time-column {
  display: flex;
  flex-direction: column;
  gap: 0.28rem;
}

.picker-time-label {
  font-size: 0.72rem;
  color: var(--picker-text-muted);
  font-weight: 600;
}

.picker-time-list {
  height: 150px;
  overflow-y: auto;
  border: 1px solid var(--picker-border);
  border-radius: 8px;
  background: var(--picker-surface-subtle);
  padding: 0.14rem;
  scrollbar-gutter: stable;
  scroll-padding-block: calc(50% - 14px);
}

.picker-time-side .picker-time-list {
  height: 220px;
}

.picker-ym-popup .picker-time-list {
  height: 176px;
}

.picker-time-option {
  width: 100%;
  min-height: 28px;
  border: 1px solid transparent;
  border-radius: 6px;
  appearance: none;
  -webkit-appearance: none;
  background: var(--picker-surface);
  color: var(--picker-text);
  font-size: 0.8rem;
  font-variant-numeric: tabular-nums;
  cursor: pointer;
  outline: none;
  box-shadow: none;
  transition: none;
  -webkit-tap-highlight-color: transparent;

  &:hover {
    border-color: var(--picker-primary);
    color: var(--picker-primary);
    background: var(--picker-primary-soft);
  }

  &.active {
    border-color: var(--picker-primary);
    background: var(--picker-primary);
    color: var(--picker-surface);
    font-weight: 700;
  }
}

.picker-time-option:focus,
.picker-time-option:focus-visible,
.picker-time-option:active {
  outline: none;
  box-shadow: none;
}

.picker-time-list::-webkit-scrollbar {
  width: 8px;
}

.picker-time-list::-webkit-scrollbar-track {
  background: transparent;
}

.picker-time-list::-webkit-scrollbar-thumb {
  background: rgba(100, 116, 139, 0.35);
  border-radius: 999px;
}

.picker-actions {
  display: flex;
  justify-content: flex-end;
  gap: 0.36rem;
  margin-top: 0.48rem;
}

.picker-action-btn {
  min-width: 58px;
  min-height: 30px;
  border: 1px solid var(--picker-border);
  border-radius: 8px;
  background: var(--picker-surface);
  color: var(--picker-text);
  font-size: 0.76rem;
  font-weight: 600;
  cursor: pointer;
  transition: border-color 0.2s ease, color 0.2s ease, background-color 0.2s ease;

  &:hover {
    border-color: var(--picker-primary);
    color: var(--picker-primary);
  }

  &.primary {
    border-color: var(--picker-primary);
    background: var(--picker-primary);
    color: var(--picker-surface);
  }
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
  background: var(--flat-media-surface);
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
  background: var(--flat-media-dark-overlay);
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
  background: var(--flat-media-surface);
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
  border: 1px solid var(--status-danger-soft-border);
  background: var(--status-danger-soft-bg);

  span {
    font-size: 0.78rem;
    color: var(--status-danger);
    font-weight: 700;
  }

  p {
    margin: 0.22rem 0 0;
    font-size: 0.78rem;
    line-height: 1.42;
    color: var(--status-danger);
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
  border-top: none;
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
  .ad-apply-overlay {
    padding: 0;
    align-items: stretch;
    justify-content: stretch;
  }

  .ad-apply-modal {
    width: 100%;
    max-height: 100dvh;
    height: 100dvh;
    border-radius: 0;
    border-left: none;
    border-right: none;
    border-bottom: none;
  }

  .modal-header {
    padding: max(0.86rem, env(safe-area-inset-top)) 0.88rem 0.66rem;
    gap: 0.72rem;

    h3 {
      font-size: 1rem;
      line-height: 1.28;
    }

    p {
      margin-top: 0.22rem;
      font-size: 0.75rem;
      line-height: 1.45;
    }
  }

  .close-btn {
    width: 36px;
    height: 36px;
    border-radius: 10px;
    flex-shrink: 0;
  }

  .stepper {
    padding: 0.68rem 0.88rem;
  }

  .step {
    gap: 0.3rem;
  }

  .step-dot {
    width: 24px;
    height: 24px;
    font-size: 0.74rem;
  }

  .step-label {
    font-size: 0.72rem;
  }

  .step-line {
    margin: 0 0.56rem 1rem;
  }

  .modal-body {
    padding: 0.86rem 0.88rem calc(0.92rem + env(safe-area-inset-bottom));
    overscroll-behavior: contain;
    scrollbar-gutter: auto;
  }

  .apply-form {
    gap: 0.86rem;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .step-content--intro {
    min-height: 0;
  }

  .intro-layout {
    grid-template-columns: 1fr;
  }

  .apply-layout {
    grid-template-columns: 1fr;
  }

  .preview-panel,
  .form-panel {
    padding: 0.62rem;
  }

  .form-item {
    label {
      margin-bottom: 0.38rem;
      font-size: 0.86rem;
    }

    input,
    textarea,
    select {
      min-height: 44px;
      font-size: 0.92rem;
      padding: 0.66rem 0.78rem;
    }

    textarea {
      min-height: 94px;
    }
  }

  .custom-select-trigger,
  .datetime-display-input {
    min-height: 44px;
    font-size: 0.92rem;
  }

  .datetime-picker-panel {
    width: calc(100vw - 12px);
    max-height: calc(100dvh - 8px);
    padding: 0.56rem;
  }

  .datetime-picker-shell--drawer {
    padding-top: 12dvh;
  }

  .datetime-picker-panel--drawer {
    width: 100%;
    max-height: min(84dvh, 760px);
    border-radius: 18px 18px 0 0;
    padding: 0.52rem 0.78rem calc(0.86rem + env(safe-area-inset-bottom));
  }

  .picker-drawer-handle {
    margin-bottom: 0.64rem;
  }

  .picker-ym-popup {
    left: 0 !important;
    right: 0 !important;
    top: auto !important;
    bottom: 0 !important;
    width: 100vw;
    max-width: 100vw;
    max-height: min(68dvh, 560px);
    border-radius: 14px 14px 0 0;
    border-bottom: none;
    padding: 0.64rem 0.88rem calc(0.82rem + env(safe-area-inset-bottom));
    box-shadow: 0 -16px 30px rgba(15, 23, 42, 0.28);
  }

  .picker-ym-popup-body {
    gap: 0.42rem;
  }

  .picker-main {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.58rem;
  }

  .picker-month-trigger {
    min-height: 32px;
    font-size: 0.8rem;
    padding: 0 0.46rem;
  }

  .picker-time-side {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 0.38rem;
  }

  .picker-day-btn {
    min-height: 34px;
    font-size: 0.82rem;
  }

  .picker-time-side .picker-time-list {
    height: min(28dvh, 224px);
  }

  .picker-ym-popup .picker-time-list {
    height: min(34dvh, 228px);
  }

  .picker-time-option {
    min-height: 32px;
    font-size: 0.84rem;
  }

  .picker-actions {
    justify-content: stretch;
  }

  .picker-action-btn {
    flex: 1;
    min-height: 38px;
    font-size: 0.82rem;
  }

  .duration-options,
  .pit-options,
  .date-preset-row {
    gap: 0.5rem;
  }

  .duration-chip,
  .pit-chip,
  .date-preset-btn {
    min-height: 36px;
    padding: 0.34rem 0.74rem;
    font-size: 0.78rem;
    display: inline-flex;
    align-items: center;
    justify-content: center;
  }

  .preview-card {
    grid-template-columns: 1fr;
  }

  .preview-image-box {
    padding: 0.28rem;
  }

  .preview-row {
    border-bottom-style: solid;
    flex-direction: column;
    align-items: flex-start;
    gap: 0.16rem;

    strong {
      text-align: left;
    }
  }

  .step-actions--form-panel {
    position: static;
    bottom: auto;
    justify-content: stretch;
    margin-top: 0.7rem;
    padding: 0.56rem 0 calc(0.2rem + env(safe-area-inset-bottom));
    border-top: none;
    background: var(--flat-surface-subtle);
    z-index: auto;
  }

  .image-preview-shell.slot-home_left {
    width: min(100%, 200px);
  }

  .image-preview-shell {
    width: 100%;
    max-height: none;
  }

  .step-actions {
    flex-direction: column;
    align-items: stretch;
    gap: 0.5rem;
    margin-top: 0;
  }

  .step-actions .action-btn.primary {
    order: -1;
  }

  .action-btn {
    width: 100%;
    min-height: 44px;
    font-size: 0.9rem;
  }
}

@media (max-width: 380px) {
  .modal-header p {
    display: none;
  }

  .step-label {
    font-size: 0.7rem;
  }
}
</style>
