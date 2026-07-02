import { http } from '~/utils/network/http'

export type LinkDomainPolicyStatus = 'pending' | 'trusted' | 'blocked'

export interface LinkDomainPolicyVO {
  id: number
  domain: string
  status: LinkDomainPolicyStatus
  source: string
  reviewer?: string | null
  reviewReason?: string | null
  expireAt?: string | null
  createTime: string
  updateTime: string
}

export interface LinkDomainPolicyPageResult {
  records: LinkDomainPolicyVO[]
  total: number
  current: number
  pages: number
}

export interface LinkDomainPolicyReviewPayload {
  reviewer?: string
  reviewReason?: string
  expireAt?: string | null
}

export const linkDomainPolicyApi = {
  page: (params: { pageNum?: number; pageSize?: number; status?: string; keyword?: string }) =>
    http.get<unknown, { data: LinkDomainPolicyPageResult }>('/admin/link-domain-policy', { params }),

  trust: (id: number, payload?: LinkDomainPolicyReviewPayload) =>
    http.put<unknown, { data: LinkDomainPolicyVO }>(`/admin/link-domain-policy/${id}/trust`, payload || {}),

  block: (id: number, payload?: LinkDomainPolicyReviewPayload) =>
    http.put<unknown, { data: LinkDomainPolicyVO }>(`/admin/link-domain-policy/${id}/block`, payload || {}),

  unblock: (id: number, payload?: LinkDomainPolicyReviewPayload) =>
    http.put<unknown, { data: LinkDomainPolicyVO }>(`/admin/link-domain-policy/${id}/unblock`, payload || {}),

  updateNote: (id: number, payload: { status?: LinkDomainPolicyStatus; reviewer?: string; reviewReason?: string; expireAt?: string | null }) =>
    http.put<unknown, { data: LinkDomainPolicyVO }>(`/admin/link-domain-policy/${id}/note`, payload),
}
