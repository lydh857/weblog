<template>
  <div class="dashboard-page">
    <!-- 统计卡片 -->
    <div v-loading="loading" class="stat-cards">
      <template v-if="!loading">
        <div v-for="card in cardConfigs" :key="card.key" class="stat-card" :class="`stat-card--${card.key}`">
          <div class="card-header">
            <div class="card-icon" :style="{ background: card.color }">
              <el-icon :size="20" color="#fff"><component :is="card.icon" /></el-icon>
            </div>
            <span class="card-title">{{ card.title }}</span>
          </div>
          <div class="card-body">
            <div class="total-row">
              <div class="total-value">{{ formatNumber(card.data.total) }}</div>
              <div class="total-label">{{ card.totalLabel }}</div>
            </div>
            <div class="detail-row">
              <div class="detail-item">
                <span class="detail-value">{{ formatNumber(card.data.today) }}</span>
                <span class="detail-label">{{ card.labels.today }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-value">{{ formatNumber(card.data.yesterday) }}</span>
                <span class="detail-label">{{ card.labels.yesterday }}</span>
              </div>
            </div>
            <div class="detail-row">
              <div class="detail-item">
                <span class="detail-value">{{ formatNumber(card.data.week) }}</span>
                <span class="detail-label">{{ card.labels.week }}</span>
              </div>
              <div class="detail-item">
                <span class="detail-value">{{ formatNumber(card.data.month) }}</span>
                <span class="detail-label">{{ card.labels.month }}</span>
              </div>
            </div>

          </div>
        </div>
      </template>
    </div>

    <!-- 趋势图表 -->
    <div ref="chartRowRef" v-loading="chartsLoading" class="chart-row">
      <div v-for="chart in chartConfigs" :key="chart.key" class="chart-card">
        <div class="chart-header">
          <span class="chart-title">{{ chart.getTitle(chart.period) }}</span>
          <div class="view-toggle period-switch" role="group" aria-label="视图切换">
            <button
              type="button"
              class="toggle-btn"
              :class="{ active: chart.period === 'week' }"
              aria-label="周视图"
              :aria-pressed="chart.period === 'week'"
              @click="handlePeriodChange(chart, 'week')"
            >
              <span>周视图</span>
            </button>
            <button
              type="button"
              class="toggle-btn"
              :class="{ active: chart.period === 'month' }"
              aria-label="月视图"
              :aria-pressed="chart.period === 'month'"
              @click="handlePeriodChange(chart, 'month')"
            >
              <span>月视图</span>
            </button>
          </div>
        </div>
        <div :ref="(el: unknown) => setChartRef(chart.key, el as HTMLDivElement)" class="chart-container" />
      </div>
    </div>

    <!-- 底部区域 -->
    <div class="bottom-section">
      <!-- 左侧：代办事项 + AI -->
      <div class="bottom-left">
        <div v-loading="pendingLoading" class="info-card todo-card">
          <template v-if="!pendingLoading">
            <div class="info-card-title todo-title-row">
              <span>代办事项</span>
              <span class="todo-title-total">总计 {{ formatNumber(todoTotal) }}</span>
            </div>

            <div class="todo-summary">
              <div class="todo-summary-item">
                <span class="todo-summary-label">待处理总数</span>
                <span class="todo-summary-value">{{ formatNumber(todoTotal) }}</span>
              </div>
              <div class="todo-summary-item">
                <span class="todo-summary-label">涉及类型</span>
                <span class="todo-summary-value">{{ todoActiveTypes }} 项</span>
              </div>
            </div>

            <div class="todo-grid">
              <button
                v-for="item in todoItems"
                :key="item.key"
                type="button"
                class="todo-task"
                :class="[`todo-task--${item.tone}`, { 'is-empty': item.count === 0, 'is-active': item.count > 0 }]"
                @click="handleQuickNavigate(item.path, item.query)"
              >
                <div class="todo-task-head">
                  <div class="todo-task-main">
                    <span class="todo-task-dot" />
                    <span class="todo-task-label">{{ item.label }}</span>
                  </div>
                  <span class="todo-task-count">{{ formatNumber(item.count) }}</span>
                </div>
                <span class="todo-task-action">{{ item.count > 0 ? '进入处理' : '查看列表' }}</span>
              </button>
            </div>
          </template>
        </div>
        <!-- AI 用量统计 -->
        <div v-if="aiTokenUsage" v-loading="aiLoading" class="info-card ai-card">
          <template v-if="!aiLoading">
            <div class="info-card-title">AI 当月用量</div>
            <div class="ai-usage-summary">
              <div class="ai-usage-item">
                <span class="ai-usage-value">{{ formatNumber(aiTokenUsage.totalInput) }}</span>
                <span class="ai-usage-label">输入 Token</span>
              </div>
              <div class="ai-usage-item">
                <span class="ai-usage-value">{{ formatNumber(aiTokenUsage.totalOutput) }}</span>
                <span class="ai-usage-label">输出 Token</span>
              </div>
              <div class="ai-usage-item">
                <span class="ai-usage-value ai-total">{{ formatNumber(aiTokenUsage.totalInput + aiTokenUsage.totalOutput) }}</span>
                <span class="ai-usage-label">合计</span>
              </div>
            </div>

            <div class="ai-usage-meta">
              <div class="ai-meta-item">
                <span class="ai-meta-label">本月请求</span>
                <span class="ai-meta-value">{{ formatNumber(aiTokenUsage.totalRequests) }}</span>
              </div>
              <div class="ai-meta-item">
                <span class="ai-meta-label">今日 Token</span>
                <span class="ai-meta-value">{{ formatNumber(aiTokenUsage.todayTokens) }}</span>
              </div>
              <div class="ai-meta-item">
                <span class="ai-meta-label">上限占用</span>
                <span class="ai-meta-value">{{ aiTokenUsage.monthlyLimit > 0 ? `${aiTokenUsage.limitUsagePercent.toFixed(1)}%` : '无限制' }}</span>
              </div>
            </div>

            <div v-if="aiTokenUsage.monthlyLimit > 0" class="ai-limit-progress">
              <el-progress
                :percentage="Math.min(100, Number(aiTokenUsage.limitUsagePercent.toFixed(1)))"
                :status="getAiUsageStatus(aiTokenUsage.limitUsagePercent)"
                :stroke-width="8"
              />
              <div class="ai-limit-text">
                已使用 {{ formatNumber(aiTokenUsage.totalTokens) }} / {{ formatNumber(aiTokenUsage.monthlyLimit) }} Token
              </div>
            </div>

            <div v-if="aiRecentTrend.length" class="ai-trend">
              <div v-for="item in aiRecentTrend" :key="item.date" class="ai-trend-item">
                <div class="ai-trend-bar-wrap">
                  <div
                    class="ai-trend-bar"
                    :style="{ height: `${Math.max(8, Math.round((getAiTrendTotal(item) / aiTrendMax) * 46))}px` }"
                    :title="`${item.date}：${formatNumber(getAiTrendTotal(item))}`"
                  />
                </div>
                <span class="ai-trend-label">{{ item.date.slice(5) }}</span>
              </div>
            </div>

            <div v-if="aiFeatureBreakdownList.length" class="ai-usage-breakdown">
              <div v-for="featureUsage in aiFeatureBreakdownList" :key="featureUsage.feature" class="ai-breakdown-item">
                <span class="ai-breakdown-label">{{ aiFeatureNameMap[featureUsage.feature] || featureUsage.feature }}</span>
                <span class="ai-breakdown-value">{{ formatNumber(featureUsage.totalTokens) }}</span>
              </div>
            </div>
          </template>
        </div>
      </div>

      <!-- 中间：分类分布饼图 -->
      <div class="bottom-center">
        <div v-loading="categoryLoading" class="info-card category-card">
          <div class="info-card-title">分类文章分布</div>
          <div ref="categoryChartRef" class="category-chart-container" />
        </div>
      </div>

      <!-- 右侧：热门文章 -->
      <div v-loading="hotPostsLoading" class="info-card hot-posts-card">
        <template v-if="!hotPostsLoading">
          <div class="info-card-title">热门文章 Top 10</div>
          <div class="hot-posts-list">
            <div v-for="post in hotPosts" :key="post.postId" class="hot-post-item" :class="{ 'top-item': post.rank <= 3 }">
              <span class="hot-rank" :class="[`rank-${post.rank <= 3 ? post.rank : 'normal'}`]">{{ post.rank }}</span>
              <div class="hot-content">
                <span class="hot-title" :title="post.title">{{ post.title }}</span>
                <div class="hot-meta">
                  <span class="hot-category">{{ post.categoryName }}<template v-if="post.subCategoryName"> / {{ post.subCategoryName }}</template></span>
                  <el-tooltip v-if="post.tagNames" :content="post.tagNames" placement="top" :show-after="300">
                    <span class="hot-tag-badge">标签</span>
                  </el-tooltip>
                  <span class="hot-stat"><el-icon :size="12"><View /></el-icon> {{ formatNumber(post.viewCount) }}</span>
                  <span class="hot-stat">赞 {{ formatNumber(post.likeCount) }}</span>
                  <span class="hot-stat">藏 {{ formatNumber(post.collectCount) }}</span>
                  <span class="hot-stat"><el-icon :size="12"><ChatDotSquare /></el-icon> {{ post.commentCount }}</span>
                </div>
              </div>
              <span class="hot-score">{{ post.score }}<small>分</small></span>
            </div>
          </div>
        </template>
      </div>
    </div>

    <div v-loading="recentCommentsLoading" class="info-card recent-comments-card">
      <template v-if="!recentCommentsLoading">
        <div class="info-card-title recent-comments-title-row">
          <span>最近评论</span>
          <el-button text type="primary" @click="handleQuickNavigate('/comment')">查看全部</el-button>
        </div>
        <div v-if="recentComments.length" class="recent-comments-list">
          <button
            v-for="comment in recentComments"
            :key="comment.id"
            type="button"
            class="recent-comment-item"
            @click="handleQuickNavigate('/comment')"
          >
            <div class="recent-comment-head">
              <span class="recent-comment-author">{{ comment.nickname || '匿名用户' }}</span>
              <span class="recent-comment-time">{{ formatDateTimeShort(comment.createTime) }}</span>
            </div>
            <div class="recent-comment-content">{{ comment.content }}</div>
            <div class="recent-comment-meta">
              <span class="recent-comment-post">文章：{{ comment.postTitle || '未知文章' }}</span>
              <span class="recent-comment-status" :class="`is-${comment.status}`">{{ formatCommentStatus(comment.status) }}</span>
            </div>
          </button>
        </div>
        <el-empty v-else description="暂无最新评论" :image-size="72" />
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch, nextTick, type Component } from 'vue'
import { Document, View, User, ChatDotSquare, PriceTag } from '@element-plus/icons-vue'
import { init as initEcharts, graphic, use as useEcharts, type ECharts } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TitleComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import {
  getArticleStatistics,
  getPvStatistics,
  getUserStatistics,
  getHotPosts,
  getPending,
  getCategoryDistribution,
  getCommentStats,
  getAiTokenUsage,
  getBatchDashboard,
  type StatisticsVO,
  type HotPostVO,
  type PendingVO,
  type CategoryDistVO,
  type CommentStatsVO,
  type AiTokenUsageVO
} from '~/api/system/dashboard'
import { tagApi, type TagVO } from '~/api/content/tag'
import { commentApi, type CommentVO } from '~/api/content/comment'

// 数据格式化
function formatNumber(n: number): string {
  if (n >= 10000) return (n / 10000).toFixed(1) + 'w'
  if (n >= 1000) return (n / 1000).toFixed(1) + 'k'
  return String(n)
}

// 数据加载状态
const loading = ref(true)
const chartsLoading = ref(true)
const pendingLoading = ref(true)
const categoryLoading = ref(true)
const hotPostsLoading = ref(true)
const aiLoading = ref(true)
const recentCommentsLoading = ref(true)

// 统计数据
const emptyStats: StatisticsVO = {
  total: 0, today: 0, yesterday: 0, week: 0, month: 0,
  monthLabels: [], monthData: [], weekLabels: [], weekData: []
}
const articleStats = ref<StatisticsVO>({ ...emptyStats })
const pvStats = ref<StatisticsVO>({ ...emptyStats })
const userStats = ref<StatisticsVO>({ ...emptyStats })
const tagStats = ref<StatisticsVO>({ ...emptyStats })
const hotPosts = ref<HotPostVO[]>([])
const pending = ref<PendingVO>({
  draftPosts: 0,
  pendingComments: 0,
  pendingAds: 0,
  pendingProfileReviews: 0,
  pendingFriendLinks: 0,
})
const categoryDist = ref<CategoryDistVO[]>([])
const commentStats = ref<CommentStatsVO>({ total: 0, pending: 0, approved: 0, todayNew: 0 })
const aiTokenUsage = ref<AiTokenUsageVO | null>(null)
const recentComments = ref<CommentVO[]>([])

function normalizePending(data?: Partial<PendingVO> | null): PendingVO {
  return {
    draftPosts: data?.draftPosts ?? 0,
    pendingComments: data?.pendingComments ?? 0,
    pendingAds: data?.pendingAds ?? 0,
    pendingProfileReviews: data?.pendingProfileReviews ?? 0,
    pendingFriendLinks: data?.pendingFriendLinks ?? 0,
  }
}

function normalizeAiUsage(data: Partial<AiTokenUsageVO> | null | undefined): AiTokenUsageVO {
  const totalInput = data?.totalInput ?? 0
  const totalOutput = data?.totalOutput ?? 0
  const totalTokens = data?.totalTokens ?? (totalInput + totalOutput)
  const todayInput = data?.todayInput ?? 0
  const todayOutput = data?.todayOutput ?? 0

  return {
    month: data?.month || '',
    totalInput,
    totalOutput,
    totalTokens,
    totalRequests: data?.totalRequests ?? 0,
    todayInput,
    todayOutput,
    todayTokens: data?.todayTokens ?? (todayInput + todayOutput),
    todayRequests: data?.todayRequests ?? 0,
    monthlyLimit: data?.monthlyLimit ?? 0,
    limitUsagePercent: data?.limitUsagePercent ?? 0,
    dailyTrend: data?.dailyTrend ?? [],
    featureBreakdown: data?.featureBreakdown ?? {},
  }
}

function countDatesInRange(items: string[], start: Date, end: Date): number {
  const startMs = start.getTime()
  const endMs = end.getTime()
  return items.reduce((count, value) => {
    const current = new Date(value).getTime()
    return current >= startMs && current < endMs ? count + 1 : count
  }, 0)
}

function buildTagStatistics(tags: TagVO[]): StatisticsVO {
  const now = new Date()
  const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate())
  const tomorrowStart = new Date(todayStart.getTime() + 86400000)
  const yesterdayStart = new Date(todayStart.getTime() - 86400000)
  const weekStart = new Date(todayStart)
  weekStart.setDate(weekStart.getDate() - ((weekStart.getDay() + 6) % 7))
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1)
  const createTimes = tags.map(tag => tag.createTime).filter(Boolean)

  return {
    ...emptyStats,
    total: tags.length,
    today: countDatesInRange(createTimes, todayStart, tomorrowStart),
    yesterday: countDatesInRange(createTimes, yesterdayStart, todayStart),
    week: countDatesInRange(createTimes, weekStart, tomorrowStart),
    month: countDatesInRange(createTimes, monthStart, tomorrowStart),
  }
}

function formatDateTimeShort(value: string): string {
  return value ? value.replace('T', ' ').slice(0, 16) : ''
}

function formatCommentStatus(status: string): string {
  return {
    pending: '待审核',
    approved: '已通过',
    rejected: '已拒绝',
    spam: '垃圾评论',
  }[status] || status
}

const aiFeatureNameMap: Record<string, string> = {
  writing: '写作助手',
  meta: '元信息生成',
  commentReview: '评论审核',
  review: '评论审核',
  chat: 'AI 问答',
}

const aiFeatureBreakdownList = computed(() => {
  if (!aiTokenUsage.value) {
    return [] as Array<{
      feature: string
      inputTokens: number
      outputTokens: number
      totalTokens: number
    }>
  }

  return Object.entries(aiTokenUsage.value.featureBreakdown || {})
    .map(([feature, usage]) => {
      const inputTokens = usage.inputTokens || 0
      const outputTokens = usage.outputTokens || 0
      return {
        feature,
        inputTokens,
        outputTokens,
        totalTokens: inputTokens + outputTokens,
      }
    })
    .sort((left, right) => right.totalTokens - left.totalTokens)
})

const aiRecentTrend = computed(() => {
  if (!aiTokenUsage.value?.dailyTrend?.length) {
    return [] as AiTokenUsageVO['dailyTrend']
  }
  return aiTokenUsage.value.dailyTrend.slice(-7)
})

const aiTrendMax = computed(() => {
  if (!aiRecentTrend.value.length) {
    return 1
  }
  return Math.max(...aiRecentTrend.value.map(item => getAiTrendTotal(item)), 1)
})

useEcharts([
  BarChart,
  LineChart,
  PieChart,
  TitleComponent,
  TooltipComponent,
  GridComponent,
  LegendComponent,
  CanvasRenderer,
])

async function ensureEchartsReady() {
  return Promise.resolve()
}

// 卡片配置
interface DashboardCardConfig {
  key: string
  title: string
  icon: Component
  color: string
  totalLabel: string
  data: Pick<StatisticsVO, 'total' | 'today' | 'yesterday' | 'week' | 'month'>
  labels: {
    today: string
    yesterday: string
    week: string
    month: string
  }
}

interface TodoTask {
  key: string
  label: string
  count: number
  path: string
  query?: Record<string, string | undefined>
  tone: 'primary' | 'warning' | 'success' | 'info'
}

const todoItems = computed<TodoTask[]>(() => [
  {
    key: 'profile-review',
    label: '个人信息审核',
    count: pending.value.pendingProfileReviews,
    path: '/user',
    query: { tab: 'reviews' },
    tone: 'primary',
  },
  {
    key: 'advertisement-review',
    label: '广告审核',
    count: pending.value.pendingAds,
    path: '/advertisement',
    query: { status: 'pending', tab: 'list' },
    tone: 'warning',
  },
  {
    key: 'friend-link-review',
    label: '友链审核',
    count: pending.value.pendingFriendLinks,
    path: '/friend-link',
    query: { status: 'pending' },
    tone: 'success',
  },
  {
    key: 'comment-review',
    label: '评论待审',
    count: pending.value.pendingComments,
    path: '/comment',
    tone: 'info',
  },
])

const todoTotal = computed(() => {
  return todoItems.value.reduce((sum, item) => sum + item.count, 0)
})

const todoActiveTypes = computed(() => {
  return todoItems.value.filter(item => item.count > 0).length
})

const commentCardStats = computed<Pick<StatisticsVO, 'total' | 'today' | 'yesterday' | 'week' | 'month'>>(() => {
  const total = commentStats.value.total || 0
  const todayNew = commentStats.value.todayNew || 0
  const pendingCount = commentStats.value.pending || 0
  const approvedCount = commentStats.value.approved || 0

  return {
    total,
    today: todayNew,
    yesterday: pendingCount,
    week: approvedCount,
    month: total,
  }
})

const cardConfigs = computed<DashboardCardConfig[]>(() => [
  {
    key: 'article',
    title: '文章统计',
    icon: Document,
    color: '#5b8def',
    totalLabel: '总文章数',
    data: articleStats.value,
    labels: { today: '今日发布', yesterday: '昨日发布', week: '本周发布', month: '本月发布' },
  },
  {
    key: 'pv',
    title: '访问量统计',
    icon: View,
    color: '#67c23a',
    totalLabel: '总访问量',
    data: pvStats.value,
    labels: { today: '今日访问', yesterday: '昨日访问', week: '本周访问', month: '本月访问' },
  },
  {
    key: 'user',
    title: '用户统计',
    icon: User,
    color: '#e6a23c',
    totalLabel: '总用户数',
    data: userStats.value,
    labels: { today: '今日注册', yesterday: '昨日注册', week: '本周注册', month: '本月注册' },
  },
  {
    key: 'comment',
    title: '评论统计',
    icon: ChatDotSquare,
    color: '#f56c6c',
    totalLabel: '总评论数',
    data: commentCardStats.value,
    labels: { today: '今日新增', yesterday: '待审核', week: '已通过', month: '总评论' },
  },
  {
    key: 'tag',
    title: '标签统计',
    icon: PriceTag,
    color: '#8b5cf6',
    totalLabel: '总标签数',
    data: tagStats.value,
    labels: { today: '今日新增', yesterday: '昨日新增', week: '本周新增', month: '本月新增' },
  },
])

// 图表配置
interface ChartConfig {
  key: string
  period: 'week' | 'month'
  chartType: 'bar' | 'line'
  color: string
  getData: () => StatisticsVO  // 改为函数，每次获取最新数据
  getTitle: (p: string) => string
  getSubtitle: (p: string) => string
}

const chartConfigs = reactive<ChartConfig[]>([
  {
    key: 'article', period: 'week', chartType: 'bar', color: '#5b8def',
    getData: () => articleStats.value,
    getTitle: (p: string) => p === 'week' ? '周度文章统计' : '月度文章统计',
    getSubtitle: (p: string) => p === 'week' ? '最近 7 天发布趋势' : '最近 30 天发布趋势'
  },
  {
    key: 'pv', period: 'week', chartType: 'line', color: '#67c23a',
    getData: () => pvStats.value,
    getTitle: (p: string) => p === 'week' ? '周度访问统计' : '月度访问统计',
    getSubtitle: (p: string) => p === 'week' ? '最近 7 天访问趋势' : '最近 30 天访问趋势'
  },
  {
    key: 'user', period: 'week', chartType: 'line', color: '#e6a23c',
    getData: () => userStats.value,
    getTitle: (p: string) => p === 'week' ? '周度用户统计' : '月度用户统计',
    getSubtitle: (p: string) => p === 'week' ? '最近 7 天注册趋势' : '最近 30 天注册趋势'
  }
])

// 图表实例管理
const chartRowRef = ref<HTMLDivElement | null>(null)
const chartRefs = new Map<string, HTMLDivElement>()
const chartInstances = new Map<string, ECharts>()
const categoryChartRef = ref<HTMLDivElement | null>(null)
let categoryChartInstance: ECharts | null = null
const chartsActivated = ref(false)
const statsReady = ref(false)
let chartObserver: IntersectionObserver | null = null

function setChartRef(key: string, el: HTMLDivElement) {
  if (el) chartRefs.set(key, el)
}

function handleQuickNavigate(path: string, query?: Record<string, string | undefined>) {
  if (query) {
    navigateTo({ path, query })
    return
  }
  navigateTo(path)
}

function getAiTrendTotal(item: AiTokenUsageVO['dailyTrend'][number]) {
  return (item?.inputTokens || 0) + (item?.outputTokens || 0)
}

function getAiUsageStatus(percent: number): '' | 'success' | 'warning' | 'exception' {
  if (percent >= 90) {
    return 'exception'
  }
  if (percent >= 70) {
    return 'warning'
  }
  return 'success'
}

function handlePeriodChange(chart: ChartConfig, period: 'week' | 'month') {
  if (chart.period === period) return
  chart.period = period
  void renderChart(chart)
}

async function activateCharts() {
  if (chartsActivated.value) return

  chartsActivated.value = true
  chartObserver?.disconnect()
  chartObserver = null

  if (!statsReady.value) return

  chartsLoading.value = true
  try {
    await safeRenderAllCharts()
  } finally {
    chartsLoading.value = false
  }
}

function observeChartArea() {
  if (typeof window === 'undefined' || chartsActivated.value || !chartRowRef.value) return

  if (!('IntersectionObserver' in window)) {
    void activateCharts()
    return
  }

  chartObserver = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting)) {
      void activateCharts()
    }
  }, {
    root: null,
    rootMargin: '160px 0px',
    threshold: 0.05,
  })

  chartObserver.observe(chartRowRef.value)
}

// 检测暗色模式
function isDarkMode(): boolean {
  return document.documentElement.classList.contains('dark')
}

// 渲染趋势图表
async function renderChart(config: ChartConfig) {
  if (!chartsActivated.value) return

  const el = chartRefs.get(config.key)
  if (!el) {
    return
  }

  try {
    await ensureEchartsReady()
  } catch (error) {
    console.error('[Dashboard] ECharts 加载失败:', error)
    return
  }

  const dark = isDarkMode()
  let instance = chartInstances.get(config.key)
  if (!instance) {
    instance = initEcharts(el)
    chartInstances.set(config.key, instance)
  }

  // 通过函数获取最新数据
  const statsData = config.getData()
  const labels = config.period === 'week' ? statsData.weekLabels : statsData.monthLabels
  const data = config.period === 'week' ? statsData.weekData : statsData.monthData

  const option = {
    tooltip: { trigger: 'axis' },
    grid: { top: 20, right: 16, bottom: 24, left: 48 },
    xAxis: {
      type: 'category', data: labels, boundaryGap: config.chartType === 'bar',
      axisLabel: { color: dark ? '#94a3b8' : '#64748b', fontSize: 11 },
      axisLine: { lineStyle: { color: dark ? '#334155' : '#e2e8f0' } }
    },
    yAxis: {
      type: 'value', minInterval: 1,
      axisLabel: { color: dark ? '#94a3b8' : '#64748b', fontSize: 11 },
      splitLine: { lineStyle: { color: dark ? '#1e293b' : '#f1f5f9' } }
    },
    series: [{
      type: config.chartType, data,
      itemStyle: { color: config.color, borderRadius: config.chartType === 'bar' ? [4, 4, 0, 0] : 0 },
      lineStyle: config.chartType === 'line' ? { width: 2, color: config.color } : undefined,
      areaStyle: config.chartType === 'line' ? {
        color: new graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: config.color + '40' },
          { offset: 1, color: config.color + '05' }
        ])
      } : undefined,
      smooth: config.chartType === 'line',
      barWidth: '60%'
    }]
  }
  
  instance.setOption(option, true)
  instance.resize()
}

// 渲染分类饼图
async function renderCategoryChart() {
  if (!chartsActivated.value || !categoryChartRef.value) return

  try {
    await ensureEchartsReady()
  } catch (error) {
    console.error('[Dashboard] ECharts 加载失败:', error)
    return
  }

  const dark = isDarkMode()
  if (categoryChartInstance) categoryChartInstance.dispose()
  categoryChartInstance = initEcharts(categoryChartRef.value)

  const data = categoryDist.value.map(item => ({ name: item.name, value: item.count }))

  categoryChartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}篇 ({d}%)' },
    legend: {
      orient: 'horizontal',
      bottom: 4,
      left: 'center',
      itemWidth: 14,
      itemHeight: 10,
      textStyle: { color: dark ? '#94a3b8' : '#64748b', fontSize: 12 }
    },
    series: [{
      type: 'pie', radius: ['34%', '62%'], center: ['50%', '38%'],
      data, label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      itemStyle: { borderRadius: 6, borderColor: dark ? '#1e293b' : '#fff', borderWidth: 2 }
    }]
  })
}

// 窗口 resize 处理
function handleResize() {
  chartInstances.forEach(c => c.resize())
  categoryChartInstance?.resize()
}

// 暗色模式切换时重新渲染图表
const { isDark } = useDarkMode()
watch(isDark, () => {
  if (!chartsActivated.value) return
  nextTick(() => {
    void safeRenderAllCharts()
  })
})

let loadingGuardTimer: ReturnType<typeof setTimeout> | null = null

function clearStaleLoadingMasks() {
  if (typeof document === 'undefined') return
  const root = document.querySelector('.dashboard-page') as HTMLElement | null
  if (!root) return

  const masks = root.querySelectorAll('.el-loading-mask')
  masks.forEach((mask) => {
    const parent = mask.parentElement as HTMLElement | null
    mask.remove()
    parent?.classList.remove('el-loading-parent--relative')
    parent?.classList.remove('el-loading-parent--hidden')
  })
}

function setCoreLoadingState(value: boolean) {
  if (loadingGuardTimer) {
    clearTimeout(loadingGuardTimer)
    loadingGuardTimer = null
  }

  loading.value = value
  chartsLoading.value = value
  pendingLoading.value = value
  categoryLoading.value = value
  hotPostsLoading.value = value

  if (!value) {
    nextTick(() => clearStaleLoadingMasks())
    return
  }

  // 兜底：避免路由切换异常导致 loading 遗留
  loadingGuardTimer = setTimeout(() => {
    loading.value = false
    chartsLoading.value = false
    pendingLoading.value = false
    categoryLoading.value = false
    hotPostsLoading.value = false
    loadingGuardTimer = null
    nextTick(() => clearStaleLoadingMasks())
  }, 3000)
}

async function safeRenderAllCharts() {
  if (!chartsActivated.value || !statsReady.value) return

  for (const config of chartConfigs) {
    try {
      await renderChart(config)
    } catch (error) {
      console.error('[Dashboard] 趋势图渲染失败:', config.key, error)
    }
  }

  try {
    await renderCategoryChart()
  } catch (error) {
    console.error('[Dashboard] 分类饼图渲染失败:', error)
  }
}

// 加载数据 - 使用批量查询优化
async function loadDashboardStats() {
  setCoreLoadingState(true)
  statsReady.value = false

  try {
    // 尝试使用批量查询（一次请求获取所有数据）
    const batchResult = await getBatchDashboard()
    const data = batchResult.data

    if (data.articleStats) articleStats.value = data.articleStats
    if (data.pvStats) pvStats.value = data.pvStats
    if (data.userStats) userStats.value = data.userStats
    if (data.hotPosts) hotPosts.value = data.hotPosts
    if (data.pending) pending.value = normalizePending(data.pending)
    if (Array.isArray(data.categoryDist)) categoryDist.value = data.categoryDist
    if (data.commentStats) commentStats.value = data.commentStats

    const [tagsResult, recentCommentsResult] = await Promise.allSettled([
      tagApi.listAll(),
      commentApi.list({ pageNum: 1, pageSize: 6 })
    ])
    if (tagsResult.status === 'fulfilled') {
      tagStats.value = buildTagStatistics(tagsResult.value.data)
    }
    if (recentCommentsResult.status === 'fulfilled') {
      recentComments.value = recentCommentsResult.value.data.records || []
    }

    await nextTick()
    await nextTick()
    statsReady.value = true

    if (chartsActivated.value) {
      await safeRenderAllCharts()
    }
  } catch (error) {
    console.error('批量查询失败，降级为分批次查询:', error)
    await loadDashboardStatsFallback()
  } finally {
    // 无论接口或图表渲染是否异常，都不要让主区域一直转圈
    setCoreLoadingState(false)
    recentCommentsLoading.value = false
  }

  // AI 用量独立加载（可选，失败不影响主面板）
  aiLoading.value = true
  try {
    const ai = await getAiTokenUsage()
    aiTokenUsage.value = normalizeAiUsage(ai.data)
  } catch {
    aiTokenUsage.value = null
  } finally {
    aiLoading.value = false
  }
}

// 降级方案：分批次加载
async function loadDashboardStatsFallback() {
  // 第一批：核心统计卡片
  const coreStats = await Promise.allSettled([
    getArticleStatistics(),
    getPvStatistics(),
    getUserStatistics()
  ])
  if (coreStats[0].status === 'fulfilled') articleStats.value = coreStats[0].value.data
  if (coreStats[1].status === 'fulfilled') pvStats.value = coreStats[1].value.data
  if (coreStats[2].status === 'fulfilled') userStats.value = coreStats[2].value.data

  // 第二批：代办统计和评论统计
  const [pendingResult, commentResult] = await Promise.allSettled([
    getPending(),
    getCommentStats()
  ])
  if (pendingResult.status === 'fulfilled') pending.value = normalizePending(pendingResult.value.data)
  if (commentResult.status === 'fulfilled') commentStats.value = commentResult.value.data

  // 第三批：分类分布和热门文章
  const [categoryResult, hotPostsResult] = await Promise.allSettled([
    getCategoryDistribution(),
    getHotPosts()
  ])
  if (categoryResult.status === 'fulfilled' && Array.isArray(categoryResult.value.data)) {
    categoryDist.value = categoryResult.value.data
  }
  if (hotPostsResult.status === 'fulfilled') hotPosts.value = hotPostsResult.value.data

  const [tagResult, recentCommentsResult] = await Promise.allSettled([
    tagApi.listAll(),
    commentApi.list({ pageNum: 1, pageSize: 6 })
  ])
  if (tagResult.status === 'fulfilled') {
    tagStats.value = buildTagStatistics(tagResult.value.data)
  }
  if (recentCommentsResult.status === 'fulfilled') {
    recentComments.value = recentCommentsResult.value.data.records || []
  }

  await nextTick()
  await nextTick()
  statsReady.value = true

  if (chartsActivated.value) {
    await safeRenderAllCharts()
  }
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await nextTick()
  observeChartArea()
  await loadDashboardStats()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  chartObserver?.disconnect()
  chartObserver = null
  chartInstances.forEach(c => c.dispose())
  categoryChartInstance?.dispose()
  if (loadingGuardTimer) {
    clearTimeout(loadingGuardTimer)
    loadingGuardTimer = null
  }
})
</script>

<style scoped>
.dashboard-page {
  padding: 16px;
  background: var(--admin-content-bg);
}

/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  min-width: 0;
  background: var(--admin-panel-bg);
  border: 1px solid var(--admin-panel-border);
  border-radius: 12px;
  padding: 18px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}
.card-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}
.card-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}
.card-title {
  font-size: 15px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.card-body .total-row {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 12px;
}
.total-value {
  font-size: 26px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.total-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.detail-row {
  display: flex;
  gap: 16px;
  margin-bottom: 6px;
}
.detail-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.detail-value {
  font-size: 16px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}
.detail-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 趋势图表 */
.chart-row {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.chart-card {
  min-width: 0;
  background: var(--admin-panel-bg);
  border: 1px solid var(--admin-panel-border);
  border-radius: 12px;
  padding: 16px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.period-switch {
  display: inline-flex;
  gap: 4px;
  flex-shrink: 0;
  background: var(--admin-panel-bg-soft);
  border-radius: 10px;
  padding: 3px;
}

.toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px 16px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  font-weight: 500;
  line-height: 1;
  cursor: pointer;
  transition: all 0.2s ease;

  &:hover {
    color: var(--el-text-color-primary);
  }

  &.active {
    background: var(--admin-panel-hover);
    color: var(--admin-aside-text-active);
    box-shadow: none;
  }
}
.chart-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  min-width: 0;
}
.chart-container {
  width: 100%;
  height: 240px;
}

/* 底部区域 */
.bottom-section {
  display: grid;
  grid-template-columns: minmax(0, 1.12fr) minmax(0, 1fr) minmax(0, 1.18fr);
  gap: 16px;
  align-items: stretch;
  margin-bottom: 16px;
}
.info-card {
  background: var(--admin-panel-bg);
  border: 1px solid var(--admin-panel-border);
  border-radius: 12px;
  padding: 16px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
  height: 100%;
  box-sizing: border-box;
}
.info-card-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  margin-bottom: 14px;
}
.bottom-left {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 代办事项 */
.todo-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.todo-title-total {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: var(--el-text-color-secondary);
  background: var(--admin-panel-bg-soft);
}

.todo-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.todo-summary-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 8px;
  background: var(--admin-panel-bg-soft);
}

.todo-summary-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.todo-summary-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.todo-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.todo-task {
  border: none;
  border-radius: 8px;
  background: var(--admin-panel-bg-soft);
  min-height: 72px;
  padding: 10px 12px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 6px;
  transition: background-color 0.2s ease, color 0.2s ease;
}

.todo-task:hover {
  background: var(--admin-panel-hover);
}

.todo-task:focus-visible {
  outline: 2px solid var(--el-color-primary-light-5);
  outline-offset: 1px;
}

.todo-task.is-empty {
  opacity: 0.72;
}

.todo-task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.todo-task-main {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.todo-task-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--el-text-color-placeholder);
}

.todo-task--primary .todo-task-dot {
  background: var(--el-color-primary);
}

.todo-task--warning .todo-task-dot {
  background: var(--el-color-warning);
}

.todo-task--success .todo-task-dot {
  background: var(--el-color-success);
}

.todo-task--info .todo-task-dot {
  background: var(--el-color-info);
}

.todo-task-label {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-regular);
}

.todo-task-count {
  font-size: 18px;
  line-height: 1;
  font-weight: 600;
  color: var(--el-text-color-secondary);
}

.todo-task.is-active .todo-task-count {
  color: var(--el-text-color-primary);
}

.todo-task--primary.is-active .todo-task-count {
  color: var(--el-color-primary);
}

.todo-task--warning.is-active .todo-task-count {
  color: var(--el-color-warning);
}

.todo-task--success.is-active .todo-task-count {
  color: var(--el-color-success);
}

.todo-task--info.is-active .todo-task-count {
  color: var(--el-color-info);
}

.todo-task-action {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.todo-task.is-active .todo-task-action {
  color: var(--el-text-color-regular);
}

/* 分类饼图 */
.category-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.category-chart-container {
  flex: 1;
  min-height: 320px;
}

/* 热门文章 */
.hot-posts-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.hot-posts-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hot-post-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: 6px;
  transition: background 0.2s;
  cursor: pointer;
}
.hot-post-item:hover {
  background: var(--admin-panel-hover);
}
.hot-post-item.top-item {
  background: var(--admin-panel-bg-soft);
}
.hot-rank {
  width: 24px;
  height: 24px;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 700;
  flex-shrink: 0;
}
.hot-rank.rank-1 {
  color: #fff;
  background: var(--el-color-primary);
}
.hot-rank.rank-2 {
  color: var(--el-text-color-primary);
  background: var(--admin-panel-hover);
}
.hot-rank.rank-3 {
  color: var(--el-text-color-primary);
  background: var(--admin-panel-bg-soft);
}
.hot-rank.rank-normal {
  color: var(--el-text-color-secondary);
  background: var(--admin-panel-bg-soft);
}
.hot-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.hot-title {
  font-size: 13px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  line-height: 1.4;
}
.hot-meta {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hot-category {
  font-size: 11px;
  color: var(--admin-aside-text-active);
  background: var(--admin-panel-bg-soft);
  padding: 1px 6px;
  border-radius: 3px;
}
.hot-stat {
  display: flex;
  align-items: center;
  gap: 2px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.hot-tag-badge {
  font-size: 10px;
  color: var(--admin-aside-text-active);
  background: var(--admin-panel-bg-soft);
  padding: 0 5px;
  border-radius: 3px;
  cursor: default;
  line-height: 18px;
}
.hot-score {
  font-size: 16px;
  font-weight: 600;
  color: var(--admin-aside-text-active);
  flex-shrink: 0;
  line-height: 1;
}
.hot-score small {
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  margin-left: 1px;
}

.recent-comments-card {
  display: flex;
  flex-direction: column;
}

.recent-comments-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.recent-comments-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}

.recent-comment-item {
  border: 1px solid var(--admin-panel-border);
  border-radius: 10px;
  background: var(--admin-panel-bg-soft);
  padding: 12px;
  text-align: left;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 8px;
  transition: background-color 0.2s ease, border-color 0.2s ease;
}

.recent-comment-item:hover {
  background: var(--admin-panel-hover);
}

.recent-comment-head,
.recent-comment-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.recent-comment-author {
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.recent-comment-time,
.recent-comment-post {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

.recent-comment-content {
  font-size: 13px;
  line-height: 1.6;
  color: var(--el-text-color-regular);
  min-height: 42px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.recent-comment-post {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-comment-status {
  flex-shrink: 0;
  padding: 2px 8px;
  border-radius: 999px;
  font-size: 11px;
  line-height: 1.4;
  background: var(--admin-panel-bg);
  color: var(--el-text-color-secondary);
}

.recent-comment-status.is-pending {
  color: var(--el-color-warning);
}

.recent-comment-status.is-approved {
  color: var(--el-color-success);
}

.recent-comment-status.is-rejected,
.recent-comment-status.is-spam {
  color: var(--el-color-danger);
}

/* AI 用量统计 */
.ai-usage-summary {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin-bottom: 12px;
}
.ai-usage-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}
.ai-usage-value {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.ai-usage-value.ai-total {
  color: var(--el-color-primary);
}
.ai-usage-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.ai-usage-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 10px;
}

.ai-meta-item {
  padding: 6px 8px;
  border-radius: 8px;
  background: var(--admin-panel-bg-soft);
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ai-meta-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.ai-meta-value {
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.ai-limit-progress {
  margin-bottom: 10px;
}

.ai-limit-text {
  margin-top: 6px;
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

.ai-trend {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 6px;
  margin-bottom: 10px;
  align-items: end;
}

.ai-trend-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.ai-trend-bar-wrap {
  width: 100%;
  height: 48px;
  display: flex;
  align-items: flex-end;
  justify-content: center;
}

.ai-trend-bar {
  width: 74%;
  min-height: 8px;
  border-radius: 6px 6px 2px 2px;
  background: var(--el-color-primary);
  opacity: 0.78;
}

.ai-trend-label {
  font-size: 10px;
  color: var(--el-text-color-secondary);
}

.ai-usage-breakdown {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid var(--admin-panel-border);
}
.ai-breakdown-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ai-breakdown-label {
  font-size: 12px;
  color: var(--el-text-color-regular);
}
.ai-breakdown-value {
  font-size: 13px;
  font-weight: 500;
  color: var(--el-text-color-primary);
}

@media (max-width: 1600px) {
  .stat-cards {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }

  .bottom-section {
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  }

  .hot-posts-card {
    grid-column: 1 / -1;
  }

  .recent-comments-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1280px) {
  .stat-cards {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .chart-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .recent-comments-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 960px) {
  .dashboard-page {
    padding: 12px;
  }

  .stat-cards,
  .chart-row,
  .bottom-section {
    grid-template-columns: 1fr;
  }

  .ai-usage-meta {
    grid-template-columns: 1fr;
  }

  .todo-summary,
  .todo-grid {
    grid-template-columns: 1fr;
  }
}
</style>
