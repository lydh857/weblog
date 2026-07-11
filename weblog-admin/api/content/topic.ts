import { http } from '~/utils/network/http'
import type { PageResult } from '~/api/content/post'

// ========== 类型定义 ==========

/** 专题列表项 */
export interface TopicVO {
  id: number
  title: string
  cover: string
  summary: string
  weight: number
  isPublish: boolean
  isTop: boolean
  /** 专题内关联的文章总数 */
  articleCount: number
  createTime: string
  updateTime: string
  /** 运行时临时状态（切换开关时防止重复点击） */
  _topLoading?: boolean
  _publishLoading?: boolean
}

/** 创建/更新专题请求 */
export interface SaveTopicParams {
  title: string
  cover?: string
  summary?: string
}

/** 目录树节点（前后端共用） */
export interface CatalogNode {
  /** 节点 ID，新建节点时为 null */
  id: number | null
  /** 关联文章 ID，null 表示纯目录节点 */
  articleId: number | null
  title: string
  level: number
  parentId: number
  sort: number
  children: CatalogNode[]
}

/** 专题分页查询参数 */
export interface TopicPageParams {
  pageNum?: number
  pageSize?: number
  keyword?: string
  isPublish?: boolean
  isTop?: boolean
}

/** 批量状态操作请求 */
export interface BatchStatusParams {
  ids: number[]
  isTop?: boolean
  isPublish?: boolean
}

// ========== API 函数 ==========

export const topicApi = {
  /** 分页查询专题列表 */
  page: (params: TopicPageParams) =>
    http.get<unknown, { data: PageResult<TopicVO> }>('/admin/topic', { params }),

  /** 创建专题 */
  create: (data: SaveTopicParams) =>
    http.post<unknown, { data: TopicVO }>('/admin/topic', data),

  /** 更新专题 */
  update: (id: number, data: SaveTopicParams) =>
    http.put<unknown, { data: TopicVO }>(`/admin/topic/${id}`, data),

  /** 切换置顶状态 */
  toggleTop: (id: number) =>
    http.put(`/admin/topic/${id}/toggle-top`),

  /** 切换发布状态 */
  togglePublish: (id: number) =>
    http.put(`/admin/topic/${id}/toggle-publish`),

  /** 删除专题（逻辑删除，级联删除目录） */
  delete: (id: number) =>
    http.delete(`/admin/topic/${id}`),

  /** 批量删除 */
  batchDelete: (ids: number[]) =>
    http.delete('/admin/topic/batch', { data: ids }),

  /** 批量设置置顶 */
  batchSetTop: (ids: number[], isTop: boolean) =>
    http.put('/admin/topic/batch/top', { ids, isTop }),

  /** 批量设置发布状态 */
  batchSetPublish: (ids: number[], isPublish: boolean) =>
    http.put('/admin/topic/batch/publish', { ids, isPublish }),

  /** 获取专题目录树 */
  getCatalogs: (topicId: number) =>
    http.get<unknown, { data: CatalogNode[] }>(`/admin/topic/${topicId}/catalogs`),

  /** 保存专题目录树（整体替换） */
  saveCatalogs: (topicId: number, catalogs: CatalogNode[]) =>
    http.put(`/admin/topic/${topicId}/catalogs`, catalogs),

  /** 获取专题已引用的文章 ID 列表 */
  getArticleIds: (topicId: number) =>
    http.get<unknown, { data: number[] }>(`/admin/topic/${topicId}/article-ids`),

  /** 分页查询回收站专题 */
  trashPage: (params: { pageNum?: number; pageSize?: number; keyword?: string }) =>
    http.get<unknown, { data: PageResult<TopicVO> }>('/admin/topic/trash', { params }),

  /** 批量恢复专题 */
  batchRestore: (ids: number[]) =>
    http.put<unknown, { data: number }>('/admin/topic/trash/batch-restore', { ids }),

  /** 批量永久删除专题 */
  batchPermanentDelete: (ids: number[]) =>
    http.delete<unknown, { data: number }>('/admin/topic/trash/batch-permanent', { data: { ids } }),

  /** 清空回收站 */
  clearTrash: () =>
    http.delete<unknown, { data: number }>('/admin/topic/trash/clear'),
}
