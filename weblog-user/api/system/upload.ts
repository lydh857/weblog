import { http } from '~/utils/network/http'

export const uploadApi = {
  image: (file: File, usageType = 'ad_apply') => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<unknown, { data: string }>(`/portal/upload/image?usageType=${usageType}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
