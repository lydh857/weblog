import type { AdvertisementVO } from '~/api/marketing/promotion'
import type { RankingItem, RankingResolvedResult } from '~/api/content/ranking'

interface CacheOptions {
  ttlMs?: number
  force?: boolean
}

interface MemoryCacheEntry<T> {
  payload: T
  expireAt: number
}

interface RankingQuery {
  rankType?: number
  categoryId?: number
  limit?: number
  offset?: number
}

const DEFAULT_NON_CRITICAL_TTL_MS = 45_000
const MAX_CACHE_ENTRIES = 40

const rankingCacheMap = new Map<string, MemoryCacheEntry<RankingItem[]>>()
const rankingPendingMap = new Map<string, Promise<RankingItem[]>>()
const smartRankingCacheMap = new Map<string, MemoryCacheEntry<RankingResolvedResult>>()
const smartRankingPendingMap = new Map<string, Promise<RankingResolvedResult>>()

const adSlotCacheMap = new Map<string, MemoryCacheEntry<AdvertisementVO[]>>()
const adSlotPendingMap = new Map<string, Promise<AdvertisementVO[]>>()

type RankingApi = typeof import('~/api/content/ranking')['rankingApi']
type PromotionApi = typeof import('~/api/marketing/promotion')['promotionApi']

let rankingApiPromise: Promise<RankingApi> | null = null
let promotionApiPromise: Promise<PromotionApi> | null = null

async function getRankingApi(): Promise<RankingApi> {
  if (!rankingApiPromise) {
    rankingApiPromise = import('~/api/content/ranking').then(module => module.rankingApi)
  }
  return rankingApiPromise
}

async function getPromotionApi(): Promise<PromotionApi> {
  if (!promotionApiPromise) {
    promotionApiPromise = import('~/api/marketing/promotion').then(module => module.promotionApi)
  }
  return promotionApiPromise
}

function normalizeTtl(ttlMs?: number): number {
  if (typeof ttlMs === 'number' && Number.isFinite(ttlMs) && ttlMs > 0) {
    return ttlMs
  }
  return DEFAULT_NON_CRITICAL_TTL_MS
}

function buildRankingCacheKey(query: RankingQuery): string {
  const rankType = Number.isFinite(Number(query.rankType)) ? Number(query.rankType) : 0
  const categoryId = Number.isFinite(Number(query.categoryId)) ? Number(query.categoryId) : 0
  const limit = Number.isFinite(Number(query.limit)) ? Number(query.limit) : 0
  const offset = Number.isFinite(Number(query.offset)) ? Number(query.offset) : 0
  return `rank:${rankType}:${categoryId}:${limit}:${offset}`
}

function readValidCache<T>(
  cacheMap: Map<string, MemoryCacheEntry<T>>,
  key: string,
  now: number,
): T | null {
  const cached = cacheMap.get(key)
  if (!cached) {
    return null
  }
  if (cached.expireAt <= now) {
    cacheMap.delete(key)
    return null
  }
  return cached.payload
}

function pruneCacheMap<T>(cacheMap: Map<string, MemoryCacheEntry<T>>, now: number) {
  cacheMap.forEach((entry, key) => {
    if (entry.expireAt <= now) {
      cacheMap.delete(key)
    }
  })

  if (cacheMap.size <= MAX_CACHE_ENTRIES) {
    return
  }

  const overflowCount = cacheMap.size - MAX_CACHE_ENTRIES
  const keys = Array.from(cacheMap.keys())
  for (let i = 0; i < overflowCount; i += 1) {
    const key = keys[i]
    if (key) {
      cacheMap.delete(key)
    }
  }
}

function cloneRankingItems(items: RankingItem[]): RankingItem[] {
  return items.map(item => ({ ...item }))
}

function cloneRankingResolvedResult(result: RankingResolvedResult): RankingResolvedResult {
  return {
    source: result.source,
    meta: { ...result.meta },
    items: cloneRankingItems(result.items),
  }
}

function cloneAdvertisements(items: AdvertisementVO[]): AdvertisementVO[] {
  return items.map(item => ({ ...item }))
}

function normalizeRankingItems(value: unknown): RankingItem[] {
  return Array.isArray(value) ? (value as RankingItem[]) : []
}

function normalizeAdvertisements(value: unknown): AdvertisementVO[] {
  return Array.isArray(value) ? (value as AdvertisementVO[]) : []
}

/**
 * 首页非关键排行榜接口短期缓存：
 * - 同一 key 并发请求自动合并
 * - 短时间重复进入页面时复用内存结果，减少重复网络请求
 */
export async function fetchCachedRanking(
  query: RankingQuery,
  options: CacheOptions = {},
): Promise<RankingItem[]> {
  const rankingApi = await getRankingApi()

  if (import.meta.server) {
    const res = await rankingApi.get(query)
    return cloneRankingItems(normalizeRankingItems(res.data))
  }

  const key = buildRankingCacheKey(query)
  const ttlMs = normalizeTtl(options.ttlMs)
  const now = Date.now()
  pruneCacheMap(rankingCacheMap, now)

  if (!options.force) {
    const cached = readValidCache(rankingCacheMap, key, now)
    if (cached) {
      return cloneRankingItems(cached)
    }
  }

  const pending = rankingPendingMap.get(key)
  if (pending) {
    return cloneRankingItems(await pending)
  }

  const request = rankingApi.get(query)
    .then((res) => {
      const items = normalizeRankingItems(res.data)
      rankingCacheMap.set(key, {
        payload: cloneRankingItems(items),
        expireAt: Date.now() + ttlMs,
      })
      return items
    })
    .finally(() => {
      rankingPendingMap.delete(key)
    })

  rankingPendingMap.set(key, request)
  return cloneRankingItems(await request)
}

/**
 * 首页智能榜单（含回退元信息）短期缓存：
 * - 同一 key 的并发请求合并
 * - 复用短 TTL 结果，减少短时间重复拉取
 */
export async function fetchCachedSmartRanking(
  query: RankingQuery,
  options: CacheOptions = {},
): Promise<RankingResolvedResult> {
  const rankingApi = await getRankingApi()

  if (import.meta.server) {
    const result = await rankingApi.getSmartWithRecentFallback(query)
    return cloneRankingResolvedResult(result)
  }

  const key = `smart:${buildRankingCacheKey(query)}`
  const ttlMs = normalizeTtl(options.ttlMs)
  const now = Date.now()
  pruneCacheMap(smartRankingCacheMap, now)

  if (!options.force) {
    const cached = readValidCache(smartRankingCacheMap, key, now)
    if (cached) {
      return cloneRankingResolvedResult(cached)
    }
  }

  const pending = smartRankingPendingMap.get(key)
  if (pending) {
    return cloneRankingResolvedResult(await pending)
  }

  const request = rankingApi.getSmartWithRecentFallback(query)
    .then((result) => {
      smartRankingCacheMap.set(key, {
        payload: cloneRankingResolvedResult(result),
        expireAt: Date.now() + ttlMs,
      })
      return result
    })
    .finally(() => {
      smartRankingPendingMap.delete(key)
    })

  smartRankingPendingMap.set(key, request)
  return cloneRankingResolvedResult(await request)
}

/**
 * 首页非关键推广位接口短期缓存：
 * - 以 slot 为 key 进行去重
 * - 仅缓存短时间，避免影响推广位实时变更生效
 */
export async function fetchCachedAdSlot(
  slot: string,
  options: CacheOptions = {},
): Promise<AdvertisementVO[]> {
  const normalizedSlot = slot.trim()
  if (!normalizedSlot) {
    return []
  }

  const promotionApi = await getPromotionApi()

  if (import.meta.server) {
    const res = await promotionApi.getBySlot(normalizedSlot)
    return cloneAdvertisements(normalizeAdvertisements(res.data))
  }

  const ttlMs = normalizeTtl(options.ttlMs)
  const now = Date.now()
  pruneCacheMap(adSlotCacheMap, now)

  if (!options.force) {
    const cached = readValidCache(adSlotCacheMap, normalizedSlot, now)
    if (cached) {
      return cloneAdvertisements(cached)
    }
  }

  const pending = adSlotPendingMap.get(normalizedSlot)
  if (pending) {
    return cloneAdvertisements(await pending)
  }

  const request = promotionApi.getBySlot(normalizedSlot)
    .then((res) => {
      const ads = normalizeAdvertisements(res.data)
      adSlotCacheMap.set(normalizedSlot, {
        payload: cloneAdvertisements(ads),
        expireAt: Date.now() + ttlMs,
      })
      return ads
    })
    .finally(() => {
      adSlotPendingMap.delete(normalizedSlot)
    })

  adSlotPendingMap.set(normalizedSlot, request)
  return cloneAdvertisements(await request)
}
