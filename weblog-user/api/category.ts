import { http } from '~/utils/http'

export interface CategoryTreeVO {
  id: number
  name: string
  slug: string
  description: string | null
  postCount: number
  children: CategoryTreeVO[]
}

export const categoryApi = {
  tree: () => http.get<any, { data: CategoryTreeVO[] }>('/portal/category/tree'),
  getBySlug: (slug: string) => http.get<any, { data: CategoryTreeVO }>(`/portal/category/slug/${slug}`),
}
