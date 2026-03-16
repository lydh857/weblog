import { http } from '~/utils/http'
import { ssePost } from '~/utils/sse'

// ========== 请求类型 ==========

export interface WritingRequest {
  text?: string
  context?: string
}

export interface TranslateRequest {
  text: string
  targetLang: 'zh' | 'en'
}

export interface ChatRequest {
  articleContext?: string
  history?: ChatMessage[]
  userMessage: string
}

export interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

export interface MetaRequest {
  title: string
  content: string
}

export interface SlugRequest {
  title: string
}

export interface PublicChatRequest {
  sessionId?: string
  question: string
  history?: ChatMessage[]
  deviceHash?: string
}

// ========== 响应类型 ==========

export interface AiMetaResult {
  summary: string
  seoTitle: string
  seoDescription: string
  seoKeywords: string[]
  tags: TagSuggestion[]
  categories: CategorySuggestion[]
  slug: string
}

export interface TagSuggestion {
  name: string
  isExisting: boolean
  tagId: number | null
}

export interface CategorySuggestion {
  name: string
  isExisting: boolean
  categoryId: number | null
  parentName: string | null
}

export interface SeoResult {
  seoTitle: string
  seoDescription: string
  seoKeywords: string[]
}

export interface AiConfig {
  enabled: boolean
  provider: string
  apiKey: string
  baseUrl: string
  model: string
  embeddingModel: string
  maxTokens: number
  timeout: number
  monthlyTokenLimit: number
  features: FeatureToggle
}

export interface FeatureToggle {
  writing: boolean
  meta: boolean
  commentReview: boolean
  recommend: boolean
  chat: boolean
}

export interface AiConfigUpdate {
  enabled?: boolean
  provider?: string
  apiKey?: string
  baseUrl?: string
  model?: string
  embeddingModel?: string
  maxTokens?: number
  timeout?: number
  monthlyTokenLimit?: number
  features?: Partial<FeatureToggle>
}

export interface TokenUsage {
  month: string
  totalInput: number
  totalOutput: number
  totalTokens: number
  totalRequests: number
  todayInput: number
  todayOutput: number
  todayTokens: number
  todayRequests: number
  monthlyLimit: number
  limitUsagePercent: number
  dailyTrend: Array<{
    date: string
    inputTokens: number
    outputTokens: number
    totalTokens: number
    requests: number
  }>
  featureBreakdown: Record<string, { inputTokens: number; outputTokens: number }>
}

export interface PromptTemplate {
  templateKey: string
  name: string
  description: string
  systemPrompt: string
  userPromptTemplate: string
  variables: string
  isCustomized: boolean
}

export interface PromptUpdate {
  systemPrompt: string
  userPromptTemplate?: string
}

export interface AiConfigUpdateResult {
  needRestart: boolean
  message: string
}

// ========== API ==========

export const aiApi = {
  // 写作助手（返回 SSE 流）
  writingContinue: (data: WritingRequest) =>
    ssePost('/admin/ai/writing/continue', data),
  writingPolish: (data: WritingRequest) =>
    ssePost('/admin/ai/writing/polish', data),
  writingRewrite: (data: WritingRequest) =>
    ssePost('/admin/ai/writing/rewrite', data),
  writingTranslate: (data: TranslateRequest) =>
    ssePost('/admin/ai/writing/translate', data),
  writingChat: (data: ChatRequest) =>
    ssePost('/admin/ai/writing/chat', data),

  // 元信息生成
  generateAll: (data: MetaRequest) =>
    http.post<unknown, { data: AiMetaResult }>('/admin/ai/meta/generate-all', data),
  regenerateSummary: (data: MetaRequest) =>
    http.post<unknown, { data: string }>('/admin/ai/meta/summary', data),
  regenerateSeo: (data: MetaRequest) =>
    http.post<unknown, { data: SeoResult }>('/admin/ai/meta/seo', data),
  regenerateTags: (data: MetaRequest) =>
    http.post<unknown, { data: TagSuggestion[] }>('/admin/ai/meta/tags', data),
  regenerateCategories: (data: MetaRequest) =>
    http.post<unknown, { data: CategorySuggestion[] }>('/admin/ai/meta/categories', data),
  regenerateSlug: (data: SlugRequest) =>
    http.post<unknown, { data: string }>('/admin/ai/meta/slug', data),

  // 评论审核
  reviewComment: (commentId: number) =>
    http.post('/admin/ai/comment/review', { commentId }),
  batchReview: (ids: number[]) =>
    http.post('/admin/ai/comment/batch-review', { ids }),

  // 配置管理
  getConfig: () =>
    http.get<unknown, { data: AiConfig }>('/admin/ai/config'),
  updateConfig: (data: AiConfigUpdate) =>
    http.put<unknown, { data: AiConfigUpdateResult }>('/admin/ai/config', data),
  testConnection: () =>
    http.post<unknown, { data: string }>('/admin/ai/config/test-connection'),
  getTokenUsage: (month?: string) =>
    http.get<unknown, { data: TokenUsage }>('/admin/ai/token-usage', { params: { month } }),

  // 提示词模板
  getPrompts: () =>
    http.get<unknown, { data: PromptTemplate[] }>('/admin/ai/prompts'),
  getPrompt: (key: string) =>
    http.get<unknown, { data: PromptTemplate }>(`/admin/ai/prompts/${key}`),
  updatePrompt: (key: string, data: PromptUpdate) =>
    http.put(`/admin/ai/prompts/${key}`, data),
  resetPrompt: (key: string) =>
    http.post(`/admin/ai/prompts/${key}/reset`),
}
