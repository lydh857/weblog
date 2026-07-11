import { http } from '~/utils/network/http'

export const uploadApi = {
  /** 上传图片，返回 URL */
  image: (file: File, usageType = 'cover') => {
    const formData = new FormData()
    formData.append('file', file)
    return http.post<unknown, { data: string }>(`/admin/upload/image?usageType=${usageType}`, formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
  },
}
