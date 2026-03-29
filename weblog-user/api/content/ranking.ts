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
const RECENT_FALLBACK_REASON = 'daily_empty_fallback_recent_posts'
const RECENT_FALLBACK_EMPTY_REASON = 'daily_empty_no_recent_posts'

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
  return {
    items,
    source: 'recent',
    meta: {
      requestedRankType: rankType,
      servedRankType: rankType,
      servedStatDate: null,
      fallbackUsed: true,
      fallbackReason: items.length > 0 ? RECENT_FALLBACK_REASON : RECENT_FALLBACK_EMPTY_REASON,
    },
  }
}

export const rankingApi = {
  /** 查询排行榜 */
  get: (params: { rankType?: number; categoryId?: number; limit?: number; offset?: number }) =>
    http.get<unknown, { data: RankingItem[] }>('/portal/ranking', { params }),

  /** 查询智能排行榜（含回退信息） */
  getSmart: (params: { rankType?: number; categoryId?: number; limit?: number; offset?: number }) =>
    http.get<unknown, { data: RankingSmartResponse }>('/portal/ranking/smart', { params }),

  /** 查询智能排行榜，日榜空时自动降级为最新发布 */
  getSmartWithRecentFallback: async (
    params: { rankType?: number; categoryId?: number; limit?: number; offset?: number },
  ): Promise<RankingResolvedResult> => {
    const rankType = params.rankType ?? 4

    try {
      const res = await rankingApi.getSmart(params)
      const data = res.data
      const items = data?.items || []
      const meta = data?.meta || createDefaultMeta(rankType)

      if (rankType !== DAY_RANK_TYPE || items.length > 0) {
        return {
          items,
          meta,
          source: 'ranking',
        }
      }

      return await loadRecentFallback(rankType, params.limit)
    } catch (error) {
      if (rankType !== DAY_RANK_TYPE) {
        throw error
      }
      return await loadRecentFallback(rankType, params.limit)
    }
  },
}
