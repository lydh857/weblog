import { http } from '~/utils/http'

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

export const rankingApi = {
  /** 查询排行榜 */
  get: (params: { rankType?: number; categoryId?: number; limit?: number; offset?: number }) =>
    http.get<unknown, { data: RankingItem[] }>('/portal/ranking', { params }),
}
