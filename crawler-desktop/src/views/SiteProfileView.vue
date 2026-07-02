<template>
  <div class="page">
    <n-card title="站点规则管理">
      <n-alert type="info" :show-icon="false" style="margin-bottom: 12px">
        用法：先在这里保存站点规则，再去“任务管理”切换到“站点批量”创建批量任务。
        列表选择器用于从列表页提取文章链接，正文选择器用于限制正文提取区域。
      </n-alert>
      <n-alert type="default" :show-icon="false" style="margin-bottom: 12px">
        快速定位技巧：在浏览器按 F12，先点文章列表中的任意链接，再看它外层容器类名（如 .post-list .post-item a）；
        正文同理，优先选最小且稳定的正文容器（如 article、.post-content、.entry-content）。
      </n-alert>
      <n-space style="margin-bottom: 12px" align="center">
        <n-select
          v-model:value="selectedPreset"
          :options="presetOptions"
          style="width: 360px"
          placeholder="选择预设模板（一键填充）"
        />
        <n-button @click="applyPreset">应用模板</n-button>
      </n-space>
      <n-form :model="form" label-placement="top">
        <n-grid :cols="2" :x-gap="12">
          <n-form-item-gi label="名称">
            <n-input v-model:value="form.name" placeholder="例如：少数派" />
          </n-form-item-gi>
          <n-form-item-gi label="域名">
            <n-input v-model:value="form.domain" placeholder="例如：sspai.com" />
          </n-form-item-gi>
          <n-form-item-gi label="列表选择器">
            <n-input v-model:value="form.listSelector" placeholder="可选，CSS 选择器" />
          </n-form-item-gi>
          <n-form-item-gi label="正文选择器">
            <n-input v-model:value="form.articleSelector" placeholder="可选，CSS 选择器" />
          </n-form-item-gi>
          <n-form-item-gi label="默认列表页链接（批量任务可自动带出）">
            <n-space vertical style="width: 100%">
              <n-input v-model:value="form.defaultListUrl" placeholder="可选，例如：https://juejin.cn/backend" />
              <n-space>
                <n-button size="small" @click="generateDefaultListSuggestions">生成建议</n-button>
                <n-button
                  v-for="url in defaultListSuggestions"
                  :key="url"
                  size="small"
                  secondary
                  @click="applyDefaultListUrl(url)"
                >
                  {{ url }}
                </n-button>
              </n-space>
            </n-space>
          </n-form-item-gi>
        </n-grid>
        <n-grid :cols="4" :x-gap="12" style="margin-bottom: 12px">
          <n-form-item-gi label="最大条数">
            <n-input-number v-model:value="form.maxItems" :min="1" :max="1000" />
          </n-form-item-gi>
          <n-form-item-gi label="抓取间隔(秒)">
            <n-input-number v-model:value="form.intervalSeconds" :min="1" :max="60" />
          </n-form-item-gi>
          <n-form-item-gi label="请求超时(秒)">
            <n-input-number v-model:value="form.timeoutSeconds" :min="1" :max="120" />
          </n-form-item-gi>
          <n-form-item-gi label="启用状态">
            <n-switch v-model:value="form.enabled" />
          </n-form-item-gi>
        </n-grid>
        <n-button type="primary" @click="save">保存规则</n-button>
      </n-form>

      <n-card title="规则测试（先提链再建任务）" size="small" style="margin-top: 16px">
        <n-space vertical>
          <n-input v-model:value="testListUrl" placeholder="输入列表页URL，例如：https://juejin.cn/" />
          <n-space>
            <n-button type="primary" @click="runExtractTest">测试提链</n-button>
            <n-button @click="copyFirstTestLinks" :disabled="testLinks.length === 0">复制前10条链接</n-button>
          </n-space>
          <n-alert v-if="testError" type="error" :show-icon="false">{{ testError }}</n-alert>
          <n-alert v-else-if="testLinks.length > 0" type="success" :show-icon="false">
            提链成功：共 {{ testLinks.length }} 条（域名过滤：{{ form.domain || '自动从 URL 解析' }}）
          </n-alert>
          <n-alert v-if="diagnosticTips.length > 0" type="warning" :show-icon="false">
            <div class="tips-text">{{ diagnosticTipsText }}</div>
          </n-alert>
          <n-grid v-if="testDiagnostics" :cols="2" :x-gap="12" style="margin-top: 8px">
            <n-form-item-gi label="是否使用动态回退">
              <span>{{ testDiagnostics.used_dynamic_fallback ? '是' : '否' }}</span>
            </n-form-item-gi>
            <n-form-item-gi label="静态提链（通过/原始）">
              <span>{{ testDiagnostics.static.accepted_count }} / {{ testDiagnostics.static.raw_link_count }}</span>
            </n-form-item-gi>
            <n-form-item-gi v-if="testDiagnostics.dynamic" label="动态提链（通过/原始）">
              <span>{{ testDiagnostics.dynamic.accepted_count }} / {{ testDiagnostics.dynamic.raw_link_count }}</span>
            </n-form-item-gi>
            <n-form-item-gi label="过滤统计（静态）">
              <span>
                域名: {{ testDiagnostics.static.domain_filtered }}，
                URL策略: {{ testDiagnostics.static.policy_filtered }}，
                重复: {{ testDiagnostics.static.duplicate_filtered }}
              </span>
            </n-form-item-gi>
            <n-form-item-gi v-if="testDiagnostics.dynamic" label="过滤统计（动态）">
              <span>
                域名: {{ testDiagnostics.dynamic.domain_filtered }}，
                URL策略: {{ testDiagnostics.dynamic.policy_filtered }}，
                重复: {{ testDiagnostics.dynamic.duplicate_filtered }}
              </span>
            </n-form-item-gi>
          </n-grid>
          <n-data-table
            v-if="testLinks.length > 0"
            :columns="testColumns"
            :data="testRows"
            :pagination="false"
            :max-height="280"
          />
        </n-space>
      </n-card>

      <n-card title="正文测试（抽一篇确认效果）" size="small" style="margin-top: 16px">
        <n-space vertical>
          <n-input v-model:value="testArticleUrl" placeholder="输入文章详情页 URL，例如：https://example.com/post/123" />
          <n-space>
            <n-button type="primary" @click="runArticleTest">测试正文抽取</n-button>
          </n-space>
          <n-alert v-if="articleTestError" type="error" :show-icon="false">{{ articleTestError }}</n-alert>
          <template v-else-if="articleTestResult">
            <n-grid :cols="2" :x-gap="12">
              <n-form-item-gi label="标题">
                <span>{{ articleTestResult.title || '-' }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="来源站点">
                <span>{{ articleTestResult.source_site || '-' }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="摘要">
                <span>{{ articleTestResult.summary || '-' }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="作者">
                <span>{{ articleTestResult.author || '-' }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="正文长度">
                <span>{{ articleTestResult.markdown_length }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="图片数量">
                <span>{{ articleTestResult.image_count }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="一级分类">
                <span>{{ articleTestResult.category_level1 || '-' }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="二级分类">
                <span>{{ articleTestResult.category_level2 || '-' }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="动态回退">
                <span>{{ articleTestResult.fallback_used ? '是' : '否' }}</span>
              </n-form-item-gi>
              <n-form-item-gi label="标签">
                <span>{{ articleTestResult.tags.join('、') || '-' }}</span>
              </n-form-item-gi>
            </n-grid>
            <n-input
              :value="articleTestResult.markdown"
              type="textarea"
              :autosize="{ minRows: 10, maxRows: 20 }"
              readonly
              placeholder="正文测试结果会显示在这里"
            />
          </template>
        </n-space>
      </n-card>
    </n-card>

    <n-card title="规则列表" style="margin-top: 16px">
      <n-data-table :columns="columns" :data="profiles" :pagination="false" />
    </n-card>
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useMutation, useQuery, useQueryClient } from '@tanstack/vue-query'
import { useMessage } from 'naive-ui'
import { workerApi } from '../api/http'

interface SiteProfileRow {
  id: number
  name: string
  domain: string
  defaultListUrl?: string | null
  maxItems: number
  intervalSeconds: number
  timeoutSeconds: number
  enabled: boolean
}

interface SiteProfilePreset {
  key: string
  label: string
  name: string
  domain: string
  listSelector: string
  articleSelector: string
  maxItems: number
  intervalSeconds: number
  timeoutSeconds: number
}

interface ExtractDiagnosticsRow {
  raw_link_count: number
  accepted_count: number
  invalid_scheme_filtered: number
  domain_filtered: number
  policy_filtered: number
  duplicate_filtered: number
  max_items: number
}

interface ExtractDiagnostics {
  used_dynamic_fallback: boolean
  static: ExtractDiagnosticsRow
  dynamic: ExtractDiagnosticsRow | null
}

interface ExtractTestResponse {
  count: number
  links: string[]
  diagnostics?: ExtractDiagnostics
}

interface ArticleTestResponse {
  title: string
  summary: string
  markdown: string
  markdown_length: number
  image_count: number
  tags: string[]
  category_level1?: string | null
  category_level2?: string | null
  source_site: string
  fallback_used: boolean
  author?: string | null
}

const queryClient = useQueryClient()
const message = useMessage()
const form = reactive({
  name: '',
  domain: '',
  defaultListUrl: '',
  listSelector: '',
  articleSelector: '',
  maxItems: 20,
  intervalSeconds: 2,
  timeoutSeconds: 20,
  enabled: true
})

const presets: SiteProfilePreset[] = [
  {
    key: 'generic-news',
    label: '通用新闻站',
    name: '通用新闻站',
    domain: '',
    listSelector: 'main a[href*="/"]',
    articleSelector: 'article, .article, .article-content, .post-content',
    maxItems: 30,
    intervalSeconds: 2,
    timeoutSeconds: 20
  },
  {
    key: 'wordpress-blog',
    label: 'WordPress 博客',
    name: 'WordPress 博客',
    domain: '',
    listSelector: '.post a[href], .entry-title a[href], .post-title a[href]',
    articleSelector: 'article .entry-content, .post .entry-content, .single-post .entry-content',
    maxItems: 30,
    intervalSeconds: 2,
    timeoutSeconds: 20
  },
  {
    key: 'discuz-forum',
    label: 'Discuz 论坛',
    name: 'Discuz 论坛',
    domain: '',
    listSelector: '#threadlisttableid .common a[href*="thread"], .threadlist a[href*="thread"]',
    articleSelector: '#postlist .pcb .t_f, .plhin .pcb .t_f',
    maxItems: 40,
    intervalSeconds: 2,
    timeoutSeconds: 25
  },
  {
    key: 'sspai',
    label: '少数派（示例）',
    name: '少数派',
    domain: 'sspai.com',
    listSelector: '.article-list .title a[href], .article-card a[href*="/post/"]',
    articleSelector: 'article, .article-content, .post-content',
    maxItems: 20,
    intervalSeconds: 2,
    timeoutSeconds: 20
  },
  {
    key: 'juejin',
    label: '掘金（示例）',
    name: '掘金',
    domain: 'juejin.cn',
    listSelector: '.article-list .title-row a[href*="/post/"], a[href*="/post/"]',
    articleSelector: 'article, .article-content, .markdown-body',
    maxItems: 20,
    intervalSeconds: 2,
    timeoutSeconds: 20
  }
]

const selectedPreset = ref<string | null>(null)
const presetOptions = presets.map((item) => ({ label: item.label, value: item.key }))
const testListUrl = ref('')
const testError = ref('')
const testLinks = ref<string[]>([])
const testDiagnostics = ref<ExtractDiagnostics | null>(null)
const testArticleUrl = ref('')
const articleTestError = ref('')
const articleTestResult = ref<ArticleTestResponse | null>(null)

const profilesQuery = useQuery({
  queryKey: ['siteProfiles'],
  queryFn: async () => (await workerApi.get<SiteProfileRow[]>('/site-profiles')).data
})

const saveMutation = useMutation({
  mutationFn: async () => workerApi.post('/site-profiles', {
    name: form.name,
    domain: form.domain,
    default_list_url: form.defaultListUrl.trim() || null,
    list_selector: form.listSelector || null,
    article_selector: form.articleSelector || null,
    max_items: form.maxItems,
    interval_seconds: form.intervalSeconds,
    timeout_seconds: form.timeoutSeconds,
    enabled: form.enabled
  }),
  onSuccess: async () => {
    await queryClient.invalidateQueries({ queryKey: ['siteProfiles'] })
  }
})

const testMutation = useMutation({
  mutationFn: async () =>
    workerApi.post<ExtractTestResponse>('/site-profiles/test-extract', {
      list_url: testListUrl.value.trim(),
      domain: form.domain.trim(),
      list_selector: form.listSelector.trim() || null,
      max_items: form.maxItems,
      timeout_seconds: form.timeoutSeconds
    })
})

const articleTestMutation = useMutation({
  mutationFn: async () =>
    workerApi.post<ArticleTestResponse>('/site-profiles/test-article-extract', {
      article_url: testArticleUrl.value.trim(),
      article_selector: form.articleSelector.trim() || null,
      timeout_seconds: form.timeoutSeconds
    })
})

const profiles = computed(() => profilesQuery.data.value ?? [])
const testRows = computed(() => testLinks.value.map((url, idx) => ({ idx: idx + 1, url })))
const diagnosticTips = computed(() => {
  const tips: string[] = []
  if (testError.value.includes('private/loopback/link-local')) {
    tips.push('当前列表页地址被 URL 安全策略拦截，请改用公网地址，或在 .env 中配置 CRAWLER_URL_POLICY_ALLOWLIST 后重启 worker。')
  }

  const diag = testDiagnostics.value
  if (!diag) {
    return tips
  }

  const staticDiag = diag.static
  const dynamicDiag = diag.dynamic

  if (form.listSelector.trim() && staticDiag.raw_link_count === 0 && (!dynamicDiag || dynamicDiag.raw_link_count === 0)) {
    tips.push('列表选择器没有命中任何节点：可先清空“列表选择器”测试全站链接，再逐步收紧选择器。')
  }

  if (staticDiag.domain_filtered > staticDiag.accepted_count && staticDiag.domain_filtered > 0) {
    tips.push('域名过滤数量较高：请检查 domain 是否过窄（建议填主域名，如 juejin.cn，而非完整 URL）。')
  }

  const totalPolicyFiltered = staticDiag.policy_filtered + (dynamicDiag?.policy_filtered ?? 0)
  if (totalPolicyFiltered > 0) {
    tips.push('存在 URL 策略拦截：部分链接命中内网/回环地址，必要时加入 CRAWLER_URL_POLICY_ALLOWLIST。')
  }

  if (diag.used_dynamic_fallback && staticDiag.accepted_count === 0 && (dynamicDiag?.accepted_count ?? 0) > 0) {
    tips.push('该站点偏前端渲染：静态提链为 0，动态回退可用。建议优先使用频道/列表页 URL。')
  }

  if ((dynamicDiag?.accepted_count ?? staticDiag.accepted_count) === 0 && staticDiag.duplicate_filtered > 0) {
    tips.push('链接重复过滤较多：可增加 max_items，或更换更垂直的列表页避免重复入口。')
  }

  if (tips.length === 0 && testLinks.value.length > 0) {
    tips.push('规则表现正常：建议抽查前 3 条链接详情页，确认正文选择器是否需要微调。')
  }

  return tips
})
const diagnosticTipsText = computed(() => diagnosticTips.value.map((tip, idx) => `${idx + 1}. ${tip}`).join('\n'))
const testColumns = [
  { title: '#', key: 'idx', width: 70 },
  {
    title: '提取到的链接',
    key: 'url',
    render: (row: { url: string }) => row.url
  }
]
const columns = [
  { title: 'ID', key: 'id' },
  { title: '名称', key: 'name' },
  { title: '域名', key: 'domain' },
  { title: '默认列表页链接', key: 'defaultListUrl' },
  { title: '最大条数', key: 'maxItems' },
  { title: '间隔(秒)', key: 'intervalSeconds' },
  { title: '超时(秒)', key: 'timeoutSeconds' },
  {
    title: '启用状态',
    key: 'enabled',
    render: (row: SiteProfileRow) => (row.enabled ? '启用' : '停用')
  }
]

const save = async () => {
  await saveMutation.mutateAsync()
  message.success('规则保存成功')
}

const defaultListSuggestions = ref<string[]>([])

const buildDefaultListSuggestions = (domain: string) => {
  const clean = domain.trim().toLowerCase().replace(/^https?:\/\//, '').replace(/\/$/, '')
  if (!clean) {
    return []
  }
  const suggestions = new Set<string>([`https://${clean}/`])
  if (clean === 'juejin.cn') {
    suggestions.add('https://juejin.cn/backend')
    suggestions.add('https://juejin.cn/frontend')
    suggestions.add('https://juejin.cn/ai')
  }
  if (clean === 'sspai.com') {
    suggestions.add('https://sspai.com/')
  }
  if (clean.includes('wordpress')) {
    suggestions.add(`https://${clean}/category/`)
  }
  return Array.from(suggestions)
}

const generateDefaultListSuggestions = () => {
  defaultListSuggestions.value = buildDefaultListSuggestions(form.domain)
  if (defaultListSuggestions.value.length === 0) {
    message.warning('请先填写域名，再生成默认列表页建议')
    return
  }
  if (!form.defaultListUrl) {
    form.defaultListUrl = defaultListSuggestions.value[0]
  }
  message.success('已生成默认列表页建议')
}

const applyDefaultListUrl = (url: string) => {
  form.defaultListUrl = url
  if (!testListUrl.value.trim()) {
    testListUrl.value = url
  }
}

const applyPreset = () => {
  const preset = presets.find((item) => item.key === selectedPreset.value)
  if (!preset) {
    message.warning('请先选择一个模板')
    return
  }
  form.name = preset.name
  form.domain = preset.domain
  form.defaultListUrl = form.domain ? `https://${form.domain}/` : ''
  form.listSelector = preset.listSelector
  form.articleSelector = preset.articleSelector
  form.maxItems = preset.maxItems
  form.intervalSeconds = preset.intervalSeconds
  form.timeoutSeconds = preset.timeoutSeconds
  if (!testListUrl.value && form.domain) {
    testListUrl.value = `https://${form.domain}/`
  }
  message.success('模板已填充，请按目标站点微调后保存')
}

const runExtractTest = async () => {
  if (!testListUrl.value.trim()) {
    message.warning('请先输入列表页URL')
    return
  }
  testError.value = ''
  testLinks.value = []
  testDiagnostics.value = null
  try {
    const resp = await testMutation.mutateAsync()
    testLinks.value = resp.data.links ?? []
    testDiagnostics.value = resp.data.diagnostics ?? null
    if (testLinks.value.length === 0) {
      testError.value = '未提取到链接，请调整列表选择器或确认页面结构。'
    }
  } catch (err: unknown) {
    const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
    testError.value = detail || '规则测试失败，请检查 URL/选择器/域名过滤。'
  }
}

const copyFirstTestLinks = async () => {
  if (testLinks.value.length === 0) {
    return
  }
  const text = testLinks.value.slice(0, 10).join('\n')
  if (typeof navigator !== 'undefined' && navigator.clipboard) {
    await navigator.clipboard.writeText(text)
    message.success('已复制前10条链接')
    return
  }
  message.warning('当前环境不支持剪贴板复制')
}

const runArticleTest = async () => {
  if (!testArticleUrl.value.trim()) {
    message.warning('请先输入文章 URL')
    return
  }
  articleTestError.value = ''
  articleTestResult.value = null
  try {
    const resp = await articleTestMutation.mutateAsync()
    articleTestResult.value = resp.data
  } catch (err: unknown) {
    const detail = (err as { response?: { data?: { detail?: string } } })?.response?.data?.detail
    articleTestError.value = detail || '正文测试失败，请检查 URL 或正文选择器。'
  }
}
</script>

<style scoped>
.page {
  max-width: 1200px;
  margin: 0 auto;
}

.tips-text {
  white-space: pre-line;
  line-height: 1.6;
}
</style>
