import { http } from '~/utils/network/http'

export interface CategoryTreeVO {
  id: number
  name: string
  slug: string
  description: string | null
  postCount: number
  children: CategoryTreeVO[]
}

export const categoryApi = {
  tree: () => http.get<unknown, { data: CategoryTreeVO[] }>('/portal/category/tree'),
}
