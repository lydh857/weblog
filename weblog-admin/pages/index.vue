<template>
  <div class="dashboard-page">
    <!-- 统计卡片 -->
    <div v-loading="loading" class="stat-cards">
      <template v-if="!loading">
        <div v-for="card in cardConfigs" :key="card.key" class="stat-card">
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
    <div v-loading="chartsLoading" class="chart-row">
      <div v-for="chart in chartConfigs" :key="chart.key" class="chart-card">
        <div class="chart-header">
          <span class="chart-title">{{ chart.getTitle(chart.period) }}</span>
          <el-radio-group v-model="chart.period" size="small" @change="renderChart(chart)">
            <el-radio-button value="week">周视图</el-radio-button>
            <el-radio-button value="month">月视图</el-radio-button>
          </el-radio-group>
        </div>
        <div :ref="(el: unknown) => setChartRef(chart.key, el as HTMLDivElement)" class="chart-container" />
      </div>
    </div>

    <!-- 底部区域 -->
    <div class="bottom-section">
      <!-- 左侧：待处理 + 评论统计 -->
      <div class="bottom-left">
        <div v-loading="pendingLoading" class="info-card">
          <template v-if="!pendingLoading">
            <div class="info-card-title">待处理事项</div>
            <div class="pending-list">
              <div class="pending-item">
                <span class="pending-label">草稿文章</span>
                <span class="pending-value">{{ pending.draftPosts }}</span>
              </div>
              <div class="pending-item">
                <span class="pending-label">待审评论</span>
                <span class="pending-value warning">{{ pending.pendingComments }}</span>
              </div>
              <div class="pending-item">
                <span class="pending-label">待审广告</span>
                <span class="pending-value warning">{{ pending.pendingAds }}</span>
              </div>
            </div>
          </template>
        </div>
        <div v-loading="commentLoading" class="info-card">
          <template v-if="!commentLoading">
            <div class="info-card-title">评论统计</div>
            <div class="comment-stats">
              <div class="comment-stat-item">
                <span class="comment-stat-value">{{ commentStats.total }}</span>
                <span class="comment-stat-label">总评论</span>
              </div>
              <div class="comment-stat-item">
                <span class="comment-stat-value approved">{{ commentStats.approved }}</span>
                <span class="comment-stat-label">已通过</span>
              </div>
              <div class="comment-stat-item">
                <span class="comment-stat-value pending">{{ commentStats.pending }}</span>
                <span class="comment-stat-label">待审核</span>
              </div>
              <div class="comment-stat-item">
                <span class="comment-stat-value today">{{ commentStats.todayNew }}</span>
                <span class="comment-stat-label">今日新增</span>
              </div>
            </div>
          </template>
        </div>
        <!-- AI 用量统计 -->
        <div v-if="aiTokenUsage" v-loading="aiLoading" class="info-card">
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
            <div v-if="Object.keys(aiTokenUsage.featureBreakdown).length" class="ai-usage-breakdown">
              <div v-for="(usage, feature) in aiTokenUsage.featureBreakdown" :key="feature" class="ai-breakdown-item">
                <span class="ai-breakdown-label">{{ aiFeatureNameMap[feature] || feature }}</span>
                <span class="ai-breakdown-value">{{ formatNumber(usage.inputTokens + usage.outputTokens) }}</span>
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
                  <span class="hot-stat"><el-icon :size="12"><ChatDotSquare /></el-icon> {{ post.commentCount }}</span>
                </div>
              </div>
              <span class="hot-score">{{ post.score }}<small>分</small></span>
            </div>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed, watch, nextTick } from 'vue'
import { Document, View, User, ChatDotSquare } from '@element-plus/icons-vue'
import { use } from 'echarts/core'
import { BarChart, LineChart, PieChart } from 'echarts/charts'
import {
  TitleComponent, TooltipComponent, GridComponent,
  LegendComponent
} from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { init as echartsInit, graphic } from 'echarts/core'
import type { ECharts } from 'echarts/core'

// 注册 ECharts 模块
use([
  BarChart, LineChart, PieChart,
  TitleComponent, TooltipComponent, GridComponent, LegendComponent,
  CanvasRenderer
])
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
} from '~/api/dashboard'

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
const commentLoading = ref(true)
const categoryLoading = ref(true)
const hotPostsLoading = ref(true)
const aiLoading = ref(true)

// 统计数据
const emptyStats: StatisticsVO = {
  total: 0, today: 0, yesterday: 0, week: 0, month: 0,
  monthLabels: [], monthData: [], weekLabels: [], weekData: []
}
const articleStats = ref<StatisticsVO>({ ...emptyStats })
const pvStats = ref<StatisticsVO>({ ...emptyStats })
const userStats = ref<StatisticsVO>({ ...emptyStats })
const hotPosts = ref<HotPostVO[]>([])
const pending = ref<PendingVO>({ draftPosts: 0, pendingComments: 0, pendingAds: 0 })
const categoryDist = ref<CategoryDistVO[]>([])
const commentStats = ref<CommentStatsVO>({ total: 0, pending: 0, approved: 0, todayNew: 0 })
const aiTokenUsage = ref<AiTokenUsageVO | null>(null)

const aiFeatureNameMap: Record<string, string> = {
  writing: '写作助手',
  meta: '元信息生成',
  commentReview: '评论审核',
  review: '评论审核',
  recommend: '语义推荐',
  chat: 'AI 问答',
}

// 卡片配置
const cardConfigs = computed(() => [
  {
    key: 'article', title: '文章统计', icon: Document, color: '#5b8def',
    totalLabel: '总文章数', data: articleStats.value,
    labels: { today: '今日发布', yesterday: '昨日发布', week: '本周发布', month: '本月发布' }
  },
  {
    key: 'pv', title: '访问量统计', icon: View, color: '#67c23a',
    totalLabel: '总访问量', data: pvStats.value,
    labels: { today: '今日访问', yesterday: '昨日访问', week: '本周访问', month: '本月访问' }
  },
  {
    key: 'user', title: '用户统计', icon: User, color: '#e6a23c',
    totalLabel: '总用户数', data: userStats.value,
    labels: { today: '今日注册', yesterday: '昨日注册', week: '本周注册', month: '本月注册' }
  }
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
const chartRefs = new Map<string, HTMLDivElement>()
const chartInstances = new Map<string, ECharts>()
const categoryChartRef = ref<HTMLDivElement>()
let categoryChartInstance: ECharts | null = null

function setChartRef(key: string, el: HTMLDivElement) {
  if (el) chartRefs.set(key, el)
}

// 检测暗色模式
function isDarkMode(): boolean {
  return document.documentElement.classList.contains('dark')
}

// 渲染趋势图表
function renderChart(config: ChartConfig) {
  const el = chartRefs.get(config.key)
  if (!el) {
    console.error('[Dashboard] 图表容器不存在:', config.key)
    return
  }

  const dark = isDarkMode()
  let instance = chartInstances.get(config.key)
  if (!instance) {
    instance = echartsInit(el)
    chartInstances.set(config.key, instance)
  }

  // 通过函数获取最新数据
  const statsData = config.getData()
  const labels = config.period === 'week' ? statsData.weekLabels : statsData.monthLabels
  const data = config.period === 'week' ? statsData.weekData : statsData.monthData

  console.log('[Dashboard] 图表数据:', config.key, 'labels:', labels, 'data:', data)

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
function renderCategoryChart() {
  if (!categoryChartRef.value) {
    console.error('[Dashboard] 分类饼图容器不存在')
    return
  }
  
  // 检查容器尺寸
  const rect = categoryChartRef.value.getBoundingClientRect()
  console.log('[Dashboard] 分类饼图容器尺寸:', 'width:', rect.width, 'height:', rect.height)
  
  const dark = isDarkMode()
  if (categoryChartInstance) categoryChartInstance.dispose()
  categoryChartInstance = echartsInit(categoryChartRef.value)

  const data = categoryDist.value.map(item => ({ name: item.name, value: item.count }))
  console.log('[Dashboard] 分类饼图数据:', data)
  
  categoryChartInstance.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c}篇 ({d}%)' },
    legend: {
      orient: 'horizontal', bottom: 0,
      textStyle: { color: dark ? '#94a3b8' : '#64748b', fontSize: 12 }
    },
    series: [{
      type: 'pie', radius: ['40%', '70%'], center: ['50%', '45%'],
      data, label: { show: false },
      emphasis: { label: { show: true, fontSize: 14, fontWeight: 'bold' } },
      itemStyle: { borderRadius: 6, borderColor: dark ? '#1e293b' : '#fff', borderWidth: 2 }
    }]
  })
  console.log('[Dashboard] 分类饼图渲染完成')
}

// 窗口 resize 处理
function handleResize() {
  chartInstances.forEach(c => c.resize())
  categoryChartInstance?.resize()
}

// 暗色模式切换时重新渲染图表
const { isDark } = useDarkMode()
watch(isDark, () => {
  nextTick(() => {
    chartConfigs.forEach(c => renderChart(c))
    renderCategoryChart()
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
  commentLoading.value = value
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
    commentLoading.value = false
    categoryLoading.value = false
    hotPostsLoading.value = false
    loadingGuardTimer = null
    nextTick(() => clearStaleLoadingMasks())
  }, 3000)
}

function safeRenderAllCharts() {
  chartConfigs.forEach(c => {
    try {
      renderChart(c)
    } catch (e) {
      console.error('[Dashboard] 趋势图渲染失败:', c.key, e)
    }
  })

  try {
    renderCategoryChart()
  } catch (e) {
    console.error('[Dashboard] 分类饼图渲染失败:', e)
  }
}

// 加载数据 - 使用批量查询优化
async function loadDashboardStats() {
  setCoreLoadingState(true)

  try {
    // 尝试使用批量查询（一次请求获取所有数据）
    const batchResult = await getBatchDashboard()
    const data = batchResult.data

    if (data.articleStats) articleStats.value = data.articleStats
    if (data.pvStats) pvStats.value = data.pvStats
    if (data.userStats) userStats.value = data.userStats
    if (data.hotPosts) hotPosts.value = data.hotPosts
    if (data.pending) pending.value = data.pending
    if (Array.isArray(data.categoryDist)) categoryDist.value = data.categoryDist
    if (data.commentStats) commentStats.value = data.commentStats

    await nextTick()
    await nextTick()
    safeRenderAllCharts()
  } catch (error) {
    console.error('批量查询失败，降级为分批次查询:', error)
    await loadDashboardStatsFallback()
  } finally {
    // 无论接口或图表渲染是否异常，都不要让主区域一直转圈
    setCoreLoadingState(false)
  }

  // AI 用量独立加载（可选，失败不影响主面板）
  aiLoading.value = true
  try {
    const ai = await getAiTokenUsage()
    aiTokenUsage.value = ai.data
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

  // 第二批：待处理事项和评论统计
  const [pendingResult, commentResult] = await Promise.allSettled([
    getPending(),
    getCommentStats()
  ])
  if (pendingResult.status === 'fulfilled') pending.value = pendingResult.value.data
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

  await nextTick()
  await nextTick()
  safeRenderAllCharts()
}

onMounted(async () => {
  window.addEventListener('resize', handleResize)
  await loadDashboardStats()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
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
}

/* 统计卡片 */
.stat-cards {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  min-width: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 20px;
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
  font-size: 28px;
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
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 16px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
}
.chart-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.chart-header :deep(.el-radio-group) {
  width: 136px;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  flex-shrink: 0;
}
.chart-header :deep(.el-radio-button) {
  width: 100%;
}
.chart-header :deep(.el-radio-button__inner) {
  width: 100%;
  text-align: center;
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
  grid-template-columns: 280px 1fr 1fr;
  gap: 16px;
}
.info-card {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 10px;
  padding: 16px;
  transition: background-color 0.3s ease, border-color 0.3s ease;
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

/* 待处理事项 */
.pending-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.pending-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.pending-label {
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.pending-value {
  font-size: 18px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.pending-value.warning {
  color: #e6a23c;
}

/* 评论统计 */
.comment-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.comment-stat-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}
.comment-stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.comment-stat-value.approved { color: #67c23a; }
.comment-stat-value.pending { color: #e6a23c; }
.comment-stat-value.today { color: #5b8def; }
.comment-stat-label {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 分类饼图 */
.category-card {
  height: 100%;
  display: flex;
  flex-direction: column;
}
.category-chart-container {
  flex: 1;
  min-height: 280px;
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
  background: var(--el-fill-color-light);
}
.hot-post-item.top-item {
  background: var(--el-fill-color-extra-light);
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
  background: linear-gradient(135deg, #e6a23c, #d48806);
}
.hot-rank.rank-2 {
  color: #fff;
  background: linear-gradient(135deg, #a0aec0, #718096);
}
.hot-rank.rank-3 {
  color: #fff;
  background: linear-gradient(135deg, #d48806, #b7791f);
}
.hot-rank.rank-normal {
  color: var(--el-text-color-secondary);
  background: var(--el-fill-color-light);
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
  color: #5b8def;
  background: rgba(91, 141, 239, 0.08);
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
  color: #9b8ec4;
  background: rgba(155, 142, 196, 0.08);
  padding: 0 5px;
  border-radius: 3px;
  cursor: default;
  line-height: 18px;
}
.hot-score {
  font-size: 16px;
  font-weight: 600;
  color: #5b8def;
  flex-shrink: 0;
  line-height: 1;
}
.hot-score small {
  font-size: 11px;
  font-weight: 400;
  color: var(--el-text-color-secondary);
  margin-left: 1px;
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
  color: #9b8ec4;
}
.ai-usage-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}
.ai-usage-breakdown {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 10px;
  border-top: 1px solid var(--el-border-color-extra-light);
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
</style>
