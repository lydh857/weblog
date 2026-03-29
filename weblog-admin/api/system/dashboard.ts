import { http } from '~/utils/http'

/** 统计数据基础结构 */
export interface StatisticsVO {
  total: number
  today: number
  yesterday: number
  week: number
  month: number
  monthLabels: string[]
  monthData: number[]
  weekLabels: string[]
  weekData: number[]
}

/** 热门文章 */
export interface HotPostVO {
  rank: number
  postId: number
  title: string
  categoryName: string
  subCategoryName: string | null
  tagNames: string | null
  viewCount: number
  likeCount: number
  commentCount: number
  score: number
}

/** 待处理事项 */
export interface PendingVO {
  draftPosts: number
  pendingComments: number
  pendingAds: number
  pendingProfileReviews: number
  pendingFriendLinks: number
}

/** 分类文章分布 */
export interface CategoryDistVO {
  name: string
  count: number
}

/** 评论统计 */
export interface CommentStatsVO {
  total: number
  pending: number
  approved: number
  todayNew: number
}

export function getArticleStatistics() {
  return http.get<unknown, { data: StatisticsVO }>('/admin/dashboard/article-statistics')
}

export function getPvStatistics() {
  return http.get<unknown, { data: StatisticsVO }>('/admin/dashboard/pv-statistics')
}

export function getUserStatistics() {
  return http.get<unknown, { data: StatisticsVO }>('/admin/dashboard/user-statistics')
}

export function getHotPosts() {
  return http.get<unknown, { data: HotPostVO[] }>('/admin/dashboard/hot-posts')
}

export function getPending() {
  return http.get<unknown, { data: PendingVO }>('/admin/dashboard/pending')
}

export function getCategoryDistribution() {
  return http.get<unknown, { data: CategoryDistVO[] }>('/admin/dashboard/category-distribution')
}

export function getCommentStats() {
  return http.get<unknown, { data: CommentStatsVO }>('/admin/dashboard/comment-stats')
}

/** AI Token 用量 */
export interface AiTokenUsageVO {
  month: string
  totalInput: number
  totalOutput: number
  totalTokens: number
  totalRequests: number
  todayInput: number
  todayOutput: number
  todayTokens: number
  todayRequests: number
  monthlyLimit: number
  limitUsagePercent: number
  dailyTrend: Array<{
    date: string
    inputTokens: number
    outputTokens: number
    totalTokens: number
    requests: number
  }>
  featureBreakdown: Record<string, { inputTokens: number; outputTokens: number }>
}

export function getAiTokenUsage() {
  return http.get<unknown, { data: AiTokenUsageVO }>('/admin/ai/token-usage')
}

/** 批量查询仪表盘数据（一次请求获取所有数据） */
export interface BatchDashboardVO {
  articleStats: StatisticsVO
  pvStats: StatisticsVO
  userStats: StatisticsVO
  hotPosts: HotPostVO[]
  pending: PendingVO
  categoryDist: CategoryDistVO[]
  commentStats: CommentStatsVO
}

export function getBatchDashboard() {
  return http.get<unknown, { data: BatchDashboardVO }>('/admin/dashboard/batch')
}
