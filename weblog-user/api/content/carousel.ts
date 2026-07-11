import { http } from '~/utils/network/http'

/** 轮播项视图对象 */
export interface CarouselVO {
  id: number
  type: 'article' | 'image'
  title: string | null
  description: string | null
  imageUrl: string
  linkUrl: string | null
  articleId: number | null
  slug: string | null
  sortOrder: number
  startTime: string | null
  endTime: string | null
}

export const carouselApi = {
  /** 门户端：获取启用的轮播列表 */
  listPortal: () =>
    http.get<unknown, { data: CarouselVO[] }>('/portal/carousel'),
}
