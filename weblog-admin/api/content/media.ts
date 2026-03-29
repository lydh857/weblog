import { http } from '~/utils/network/http'

export interface MediaVO {
  id: number
  fileName: string
  filePath: string
  fileType: string
  fileSize: number
  mimeType: string
  url: string
  /** 缩略图 URL（OSS 模式带图片处理参数，本地模式等于 url） */
  thumbnailUrl: string
  uploaderId: number
  usageType: string
  createTime: string
  /** 引用状态：true=已引用，false=未引用 */
  referenced: boolean
  /** 引用来源详情 */
  referenceDetails: ReferenceDetail[]
}

export interface ReferenceDetail {
  postId: number
  postTitle: string
  /** cover=封面图, content=内容图 */
  refType: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
  pages: number
}

export interface MediaUsageTypeStatVO {
  usageType: string
  fileCount: number
  totalSize: number
}

export interface MediaStatsVO {
  totalCount: number
  totalSize: number
  usageTypeStats: MediaUsageTypeStatVO[]
}

export const mediaApi = {
  /** 分页查询媒体资源，支持按 usageType 和 referenceStatus 筛选 */
  page: (params: { pageNum?: number; pageSize?: number; usageType?: string; referenceStatus?: string }) =>
    http.get<unknown, { data: PageResult<MediaVO> }>('/admin/media', { params }),

  delete: (id: number) =>
    http.delete(`/admin/media/${id}`),

  batchDelete: (ids: number[]) =>
    http.post('/admin/media/batch-delete', { ids }),

  /** 获取未引用资源数量 */
  getUnreferencedCount: () =>
    http.get<unknown, { data: number }>('/admin/media/unreferenced-count'),

  /** 批量清理未引用资源，返回删除数量 */
  cleanupUnreferenced: () =>
    http.post<unknown, { data: number }>('/admin/media/cleanup-unreferenced'),

  /** 获取媒体统计信息 */
  stats: () =>
    http.get<unknown, { data: MediaStatsVO }>('/admin/media/stats'),
}
