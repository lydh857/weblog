import { http } from '~/utils/network/http'
import { postApi, type PostVO } from '~/api/content/post'

export interface RankingItem {
  rank_num: number
  score: number
  post_id: number
  title: string
  slug: string
  cover_image: string | null
  view_count: number
  like_count: number
  collect_count: number
  comment_count: number
  category_name: string | null
  sub_category_name: string | null
}

export interface RankingMeta {
  requestedRankType: number
  servedRankType: number
  servedStatDate: string | null
  fallbackUsed: boolean
  fallbackReason: string | null
}

export interface RankingSmartResponse {
  items: RankingItem[]
  meta: RankingMeta
}

export interface RankingResolvedResult {
  items: RankingItem[]
  meta: RankingMeta
  source: 'ranking' | 'recent'
}

const DAY_RANK_TYPE = 1
const WEEK_RANK_TYPE = 2
const MONTH_RANK_TYPE = 3
const TOTAL_RANK_TYPE = 4
const DAILY_RECENT_FALLBACK_REASON = 'daily_empty_fallback_recent_posts'
const DAILY_RECENT_FALLBACK_EMPTY_REASON = 'daily_empty_no_recent_posts'
const RANK_RECENT_FALLBACK_REASON = 'rank_empty_fallback_recent_posts'
const RANK_RECENT_FALLBACK_EMPTY_REASON = 'rank_empty_no_recent_posts'
const RANK_TOTAL_FALLBACK_REASON = 'rank_empty_fallback_total'

type RankingQueryParams = { rankType?: number; categoryId?: number; limit?: number; offset?: number }

function createDefaultMeta(rankType: number): RankingMeta {
  return {
    requestedRankType: rankType,
    servedRankType: rankType,
    servedStatDate: null,
    fallbackUsed: false,
    fallbackReason: null,
  }
}

function mapPostToRankingItem(post: PostVO, index: number): RankingItem {
  const score = (post.viewCount || 0) + (post.likeCount || 0) * 2 + (post.collectCount || 0) * 3
  return {
    rank_num: index + 1,
    score,
    post_id: post.id,
    title: post.title,
    slug: post.slug,
    cover_image: post.coverImage,
    view_count: post.viewCount,
    like_count: post.likeCount,
    collect_count: post.collectCount,
    comment_count: post.commentCount,
    category_name: post.categoryName,
    sub_category_name: post.subCategoryName,
  }
}

async function loadRecentFallback(rankType: number, limit = 10): Promise<RankingResolvedResult> {
  const res = await postApi.listRecent(limit)
  const items = (res.data || []).map((post, index) => mapPostToRankingItem(post, index))
  const hasItems = items.length > 0
  const fallbackReason = rankType === DAY_RANK_TYPE
    ? (hasItems ? DAILY_RECENT_FALLBACK_REASON : DAILY_RECENT_FALLBACK_EMPTY_REASON)
    : (hasItems ? RANK_RECENT_FALLBACK_REASON : RANK_RECENT_FALLBACK_EMPTY_REASON)

  return {
    items,
    source: 'recent',
    meta: {
      requestedRankType: rankType,
      servedRankType: rankType,
      servedStatDate: null,
      fallbackUsed: true,
      fallbackReason,
    },
  }
}

async function loadTotalFallback(params: RankingQueryParams, requestedRankType: number): Promise<RankingResolvedResult | null> {
  const totalParams: RankingQueryParams = {
    ...params,
    rankType: TOTAL_RANK_TYPE,
  }
  const res = await rankingApi.getSmart(totalParams)
  const data = res.data
  const items = data?.items || []
  if (!items.length) {
    return null
  }

  return {
    items,
    source: 'ranking',
    meta: {
      requestedRankType,
      servedRankType: TOTAL_RANK_TYPE,
      servedStatDate: data?.meta?.servedStatDate || null,
      fallbackUsed: true,
      fallbackReason: RANK_TOTAL_FALLBACK_REASON,
    },
  }
}

export const rankingApi = {
  /** 查询排行榜 */
  get: (params: RankingQueryParams) =>
    http.get<unknown, { data: RankingItem[] }>('/portal/ranking', { params }),

  /** 查询智能排行榜（含回退信息） */
  getSmart: (params: RankingQueryParams) =>
    http.get<unknown, { data: RankingSmartResponse }>('/portal/ranking/smart', { params }),

  /** 查询智能排行榜，空榜时自动回退，避免展示空白 */
  getSmartWithRecentFallback: async (
    params: RankingQueryParams,
  ): Promise<RankingResolvedResult> => {
    const rankType = params.rankType ?? TOTAL_RANK_TYPE

    try {
      const res = await rankingApi.getSmart(params)
      const data = res.data
      const items = data?.items || []
      const meta = data?.meta || createDefaultMeta(rankType)

      if (items.length > 0) {
        return {
          items,
          meta,
          source: 'ranking',
        }
      }

      if (rankType === WEEK_RANK_TYPE || rankType === MONTH_RANK_TYPE) {
        const totalFallback = await loadTotalFallback(params, rankType)
        if (totalFallback) {
          return totalFallback
        }
      }

      return await loadRecentFallback(rankType, params.limit)
    } catch {
      if (rankType === WEEK_RANK_TYPE || rankType === MONTH_RANK_TYPE) {
        try {
          const totalFallback = await loadTotalFallback(params, rankType)
          if (totalFallback) {
            return totalFallback
          }
        } catch {
          // ignore and fallback to recent posts
        }
      }
      return await loadRecentFallback(rankType, params.limit)
    }
  },
}
