import { http } from '~/utils/http'

export interface TagCloudVO {
  id: number
  name: string
  slug: string
  color: string
  postCount: number
}

export interface TagCloudParams {
  categoryId?: number | null
  subCategoryId?: number | null
}

export const tagApi = {
  cloud: (params?: TagCloudParams) => {
    const query: Record<string, string> = {}
    if (params?.categoryId) query.categoryId = String(params.categoryId)
    if (params?.subCategoryId) query.subCategoryId = String(params.subCategoryId)
    const qs = new URLSearchParams(query).toString()
    const url = qs ? `/portal/tag/cloud?${qs}` : '/portal/tag/cloud'
    return http.get<TagCloudVO[], { data: TagCloudVO[] }>(url)
  },
  getBySlug: (slug: string) => http.get<TagCloudVO, { data: TagCloudVO }>(`/portal/tag/slug/${slug}`),
}
