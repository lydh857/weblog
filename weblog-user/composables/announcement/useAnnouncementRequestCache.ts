import { announcementApi, type AnnouncementVO } from '~/api/marketing/announcement'

interface FetchAllOptions {
  force?: boolean
}

let allAnnouncementsCache: AnnouncementVO[] | null = null
let allAnnouncementsRequest: Promise<AnnouncementVO[]> | null = null

function normalizeAnnouncementList(value: unknown): AnnouncementVO[] {
  return Array.isArray(value) ? (value as AnnouncementVO[]) : []
}

/**
 * 公告全量请求共享缓存：
 * - 同一时刻只发一个请求，避免多个组件并发拉取同一接口
 * - 默认优先复用最近一次成功结果，force=true 时强制刷新
 */
export async function fetchAllAnnouncements(options: FetchAllOptions = {}): Promise<AnnouncementVO[]> {
  const force = options.force === true
  if (!force && allAnnouncementsCache) {
    return allAnnouncementsCache
  }

  if (allAnnouncementsRequest) {
    return allAnnouncementsRequest
  }

  allAnnouncementsRequest = announcementApi.getAll()
    .then((res) => {
      const list = normalizeAnnouncementList(res.data)
      allAnnouncementsCache = list
      return list
    })
    .finally(() => {
      allAnnouncementsRequest = null
    })

  return allAnnouncementsRequest
}

export function clearAllAnnouncementsCache() {
  allAnnouncementsCache = null
}
