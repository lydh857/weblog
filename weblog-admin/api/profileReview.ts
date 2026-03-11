import { http } from '~/utils/http'

export interface ProfileReviewVO {
  reviewId: number
  userId: number
  email: string
  currentNickname: string
  currentBio: string | null
  currentAvatar: string | null
  pendingNickname: string | null
  pendingBio: string | null
  pendingAvatar: string | null
  submitTime: string
}

export interface ProfileReviewPageResult {
  records: ProfileReviewVO[]
  total: number
  current: number
  pages: number
}

export const profileReviewApi = {
  page: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    http.get<unknown, { data: ProfileReviewPageResult }>('/admin/profile-review', { params }),

  approve: (reviewId: number) =>
    http.put(`/admin/profile-review/${reviewId}/approve`),

  reject: (reviewId: number, reason: string) =>
    http.put(`/admin/profile-review/${reviewId}/reject`, { reason }),
}
