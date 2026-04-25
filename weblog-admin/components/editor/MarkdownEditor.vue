<template>
  <div class="md-editor-wrap">
    <MdEditor
      ref="editorRef"
      v-model="content"
      :id="editorId"
      language="zh-CN"
      :theme="editorTheme"
      :preview-theme="currentPreviewTheme"
      :code-theme="currentCodeTheme"
      :show-code-row-number="true"
      :no-highlight="true"
      :no-mermaid="true"
      :no-katex="true"
      :toolbars="toolbarItems"
      :footers="footerItems"
      :style="{ height }"
      @on-upload-img="handleUploadImg"
      @on-save="handleSave"
      @on-html-changed="handleHtmlChanged"
    >
      <template #defToolbars>
        <DropdownToolbar title="预览主题" :visible="previewDropVisible" :onChange="(v: boolean) => previewDropVisible = v">
          <template #overlay>
            <ul class="theme-list">
              <li v-for="t in previewThemes" :key="t" :class="{ active: t === currentPreviewTheme }" @click="changePreviewTheme(t)">{{ t }}</li>
            </ul>
          </template>
          <svg class="md-editor-icon" viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M12 3c-4.97 0-9 4.03-9 9s4.03 9 9 9c.83 0 1.5-.67 1.5-1.5 0-.39-.15-.74-.39-1.01-.23-.26-.38-.61-.38-1 0-.83.67-1.5 1.5-1.5H16c2.76 0 5-2.24 5-5 0-4.42-4.03-8-9-8zm-5.5 9c-.83 0-1.5-.67-1.5-1.5S5.67 9 6.5 9 8 9.67 8 10.5 7.33 12 6.5 12zm3-4C8.67 8 8 7.33 8 6.5S8.67 5 9.5 5s1.5.67 1.5 1.5S10.33 8 9.5 8zm5 0c-.83 0-1.5-.67-1.5-1.5S13.67 5 14.5 5s1.5.67 1.5 1.5S15.33 8 14.5 8zm3 4c-.83 0-1.5-.67-1.5-1.5S16.67 9 17.5 9s1.5.67 1.5 1.5-.67 1.5-1.5 1.5z"/></svg>
        </DropdownToolbar>
        <DropdownToolbar title="代码主题" :visible="codeDropVisible" :onChange="(v: boolean) => codeDropVisible = v">
          <template #overlay>
            <ul class="theme-list">
              <li v-for="t in codeThemes" :key="t" :class="{ active: t === currentCodeTheme }" @click="changeCodeTheme(t)">{{ t }}</li>
            </ul>
          </template>
          <svg class="md-editor-icon" viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"/></svg>
        </DropdownToolbar>
        <NormalToolbar title="快捷键" @onClick="showShortcuts = true">
          <svg class="md-editor-icon" viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M20 5H4c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 12H4V7h16v10zM11 8h2v2h-2zm0 3h2v2h-2zM8 8h2v2H8zm0 3h2v2H8zM5 8h2v2H5zm0 3h2v2H5zm9 3h2v2h-2zm0-3h2v2h-2zm0-3h2v2h-2zm3 3h2v2h-2zm0-3h2v2h-2zM8 14h8v2H8z"/></svg>
        </NormalToolbar>
        <NormalToolbar title="分隔线" @onClick="insertHorizontalRule">
          <svg class="md-editor-icon" viewBox="0 0 24 24" width="20" height="20"><path fill="currentColor" d="M4 11h16v2H4v-2Z"/></svg>
        </NormalToolbar>
      </template>
      <template #defFooters>
        <div class="custom-status-bar">
          <span class="status-item" v-html="mdStatusText" />
          <span class="status-divider" />
          <span class="status-item" v-html="htmlStatusText" />
          <span class="status-divider" />
          <span class="status-item footer-time">{{ currentTime }}</span>
        </div>
      </template>
    </MdEditor>

    <!-- AI 写作助手悬浮球 -->
    <AiChatBubble
      ref="aiChatBubbleRef"
      :editor-context="content"
      :selected-text="currentSelectedText"
      @insert="handleAiInsert"
      @replace="handleAiReplace"
    />

    <Teleport to="body">
      <Transition name="ft-fade">
        <div
          v-show="showFloatingToolbar"
          ref="floatingToolbarRef"
          class="floating-toolbar"
          @mousedown.prevent
        >
          <button title="粗体 Ctrl+B" @mousedown.prevent="doFormat('bold')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M8 11h4.5a2.5 2.5 0 0 0 0-5H8v5Zm0 2v5h5a3 3 0 0 0 0-6H8ZM6 4h6.5a4.5 4.5 0 0 1 3.256 7.606A5 5 0 0 1 13 22H6V4Z"/></svg>
          </button>
          <button title="斜体 Ctrl+I" @mousedown.prevent="doFormat('italic')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M15 20H7v-2h2.927l2.116-12H10V4h8v2h-2.927l-2.116 12H15v2Z"/></svg>
          </button>
          <button title="删除线" @mousedown.prevent="doFormat('strike')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M17.154 14c.23.516.346 1.09.346 1.72 0 1.342-.524 2.392-1.571 3.147C14.88 19.622 13.433 20 11.586 20c-1.64 0-3.263-.381-4.586-1.144V16.6c1.52.877 3.075 1.4 4.586 1.4 2.56 0 3.844-.793 3.844-2.38 0-.588-.197-1.063-.586-1.42H3v-2h18v2h-3.846ZM7.556 11c-.14-.292-.21-.622-.21-.99 0-1.2.49-2.166 1.474-2.9C9.803 6.37 11.104 6 12.72 6c1.46 0 2.834.333 4.28 1v2.14c-1.382-.75-2.762-1.14-4.28-1.14-2.32 0-3.48.673-3.48 2.018 0 .398.12.735.356 1.012L7.556 11Z"/></svg>
          </button>
          <span class="ft-divider" />
          <button title="一级标题" class="ft-text-btn" @mousedown.prevent="doFormat('h1')">H1</button>
          <button title="二级标题" class="ft-text-btn" @mousedown.prevent="doFormat('h2')">H2</button>
          <button title="三级标题" class="ft-text-btn" @mousedown.prevent="doFormat('h3')">H3</button>
          <span class="ft-divider" />
          <button title="引用" @mousedown.prevent="doFormat('quote')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M4.583 17.321C3.553 16.227 3 15 3 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 0 1-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179Zm10 0C13.553 16.227 13 15 13 13.011c0-3.5 2.457-6.637 6.03-8.188l.893 1.378c-3.335 1.804-3.987 4.145-4.247 5.621.537-.278 1.24-.375 1.929-.311 1.804.167 3.226 1.648 3.226 3.489a3.5 3.5 0 0 1-3.5 3.5c-1.073 0-2.099-.49-2.748-1.179Z"/></svg>
          </button>
          <button title="行内代码" @mousedown.prevent="doFormat('code')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="m23 12-7.071 7.071-1.414-1.414L20.172 12l-5.657-5.657 1.414-1.414L23 12ZM3.828 12l5.657 5.657-1.414 1.414L1 12l7.071-7.071 1.414 1.414L3.828 12Z"/></svg>
          </button>
          <button title="代码块" @mousedown.prevent="doFormat('codeblock')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M3 3h18a1 1 0 0 1 1 1v16a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Zm1 2v14h16V5H4Z"/></svg>
          </button>
          <button title="链接 Ctrl+K" @mousedown.prevent="doFormat('link')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M18.364 15.536 16.95 14.12l1.414-1.414a5 5 0 1 0-7.071-7.071L9.879 7.05 8.464 5.636 9.88 4.222a7 7 0 1 1 9.9 9.9l-1.415 1.414Zm-2.828 2.828-1.415 1.414a7 7 0 0 1-9.9-9.9l1.415-1.414L7.05 9.88l-1.414 1.414a5 5 0 1 0 7.071 7.071l1.414-1.414 1.415 1.414Zm-.708-10.607 1.415 1.415-7.071 7.07-1.415-1.414 7.071-7.07Z"/></svg>
          </button>
          <span class="ft-divider" />
          <button title="AI 润色" class="ft-ai-btn" @mousedown.prevent="doAiAction('polish')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 20h9M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z"/></svg>
            润色
          </button>
          <button title="AI 改写" class="ft-ai-btn" @mousedown.prevent="doAiAction('rewrite')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
            改写
          </button>
          <button title="AI 翻译" class="ft-ai-btn" @mousedown.prevent="doAiAction('translate')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 8l6 6M4 14l6-6 2-3M2 5h12M7 2h1"/><path d="M22 22l-5-10-5 10M14 18h6"/></svg>
            翻译
          </button>
          <button title="AI 去重" class="ft-ai-btn" @mousedown.prevent="doAiAction('deduplicate')">
            <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M16 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/><path d="M8 13h6M8 17h4"/></svg>
            去重
          </button>
        </div>
      </Transition>
    </Teleport>
    <Teleport to="body">
      <Transition name="shortcuts-fade">
        <div v-if="showShortcuts" class="shortcuts-overlay" @click.self="showShortcuts = false">
          <div class="shortcuts-panel">
            <div class="shortcuts-header">
              <span class="shortcuts-title">快捷键</span>
              <button class="shortcuts-close" @click="showShortcuts = false">
                <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M12 10.586l4.95-4.95 1.414 1.414L13.414 12l4.95 4.95-1.414 1.414L12 13.414l-4.95 4.95-1.414-1.414L10.586 12 5.636 7.05l1.414-1.414L12 10.586z"/></svg>
              </button>
            </div>
            <div class="shortcuts-body">
              <div v-for="s in shortcutList" :key="s.key" class="shortcut-row">
                <span class="shortcut-desc">{{ s.desc }}</span>
                <kbd class="shortcut-key">{{ s.key }}</kbd>
              </div>
            </div>
          </div>
        </div>
      </Transition>
    </Teleport>
  </div>
</template>


<script setup lang="ts">
import { defineAsyncComponent } from 'vue'
import type { ExposeParam, Footers, ToolbarNames } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { uploadApi } from '~/api/system/upload'
import { calculateMdStats, calculateHtmlStats, countCharsAndLines } from '~/composables/editor/useEditorStats'
import { applyFormat } from '~/composables/editor/useEditorFormat'
import { ensureMdEditorConfigured } from '~/composables/editor/useMdEditor'
import { applyWatermarkToFile, shouldApplyWatermark, type ImageWatermarkConfig } from '~/utils/image/watermark'

const MdEditor = defineAsyncComponent(async () => {
  await ensureMdEditorConfigured()
  const module = await import('md-editor-v3/lib/es/MdEditor.mjs')
  return module.default
})

const DropdownToolbar = defineAsyncComponent(async () => {
  await ensureMdEditorConfigured()
  const module = await import('md-editor-v3/lib/es/DropdownToolbar.mjs')
  return module.default
})

const NormalToolbar = defineAsyncComponent(async () => {
  await ensureMdEditorConfigured()
  const module = await import('md-editor-v3/lib/es/NormalToolbar.mjs')
  return module.default
})

const props = withDefaults(defineProps<{
  modelValue: string
  height?: string
  editorId?: string
  previewTheme?: string
  codeTheme?: string
  imageWatermark?: ImageWatermarkConfig
}>(), {
  height: '600px',
  editorId: 'md-editor',
  previewTheme: 'default',
  codeTheme: 'atom',
})

const emit = defineEmits<{
  'update:modelValue': [value: string]
  'update:previewTheme': [value: string]
  'update:codeTheme': [value: string]
  'save': [value: string]
}>()

const editorRef = ref<ExposeParam>()

const content = computed({
  get: () => props.modelValue,
  set: (val: string) => emit('update:modelValue', val),
})

// ========== 主题双向绑定 ==========
const currentPreviewTheme = computed({
  get: () => props.previewTheme,
  set: (val: string) => emit('update:previewTheme', val),
})
const currentCodeTheme = computed({
  get: () => props.codeTheme,
  set: (val: string) => emit('update:codeTheme', val),
})

// ========== 暗黑模式 ==========
const colorMode = useColorMode()
const editorTheme = computed(() => colorMode.value === 'dark' ? 'dark' : 'light')

// ========== 主题选项 ==========
const previewThemes = ['default', 'github', 'vuepress', 'mk-cute', 'smart-blue', 'cyanosis'] as const
const codeThemes = ['atom', 'a11y', 'github', 'gradient', 'kimbie', 'paraiso', 'qtcreator', 'stackoverflow'] as const
const previewDropVisible = ref(false)
const codeDropVisible = ref(false)

function changePreviewTheme(theme: string) {
  currentPreviewTheme.value = theme
  previewDropVisible.value = false
}
function changeCodeTheme(theme: string) {
  currentCodeTheme.value = theme
  codeDropVisible.value = false
}


// ========== 工具栏配置 ==========
const toolbarItems: ToolbarNames[] = [
  'bold', 'underline', 'italic', 'strikeThrough',
  '-',
  'title', 'sub', 'sup', 'quote', 'unorderedList', 'orderedList', 'task',
  '-',
  'codeRow', 'code', 'link', 'image', 'table',
  '-',
  'revoke', 'next', 'save',
  '=',
  0, 1, 2, 3,
  'pageFullscreen', 'fullscreen', 'preview', 'previewOnly', 'catalog', 'github',
]

// ========== 页脚配置 ==========
const footerItems: Footers[] = [0, '=', 'scrollSwitch']

// ========== 统计信息 ==========
const stats = reactive({
  mdChars: 0, mdLines: 0,
  htmlChars: 0, htmlParagraphs: 0,
  cursorLine: 1, cursorCol: 1,
  hasSelection: false, selChars: 0, selLines: 0,
  hasPreviewSelection: false, previewSelChars: 0, previewSelLines: 0,
})

watch(content, (val) => {
  const md = calculateMdStats(val)
  stats.mdChars = md.chars
  stats.mdLines = md.lines
  nextTick(syncEditorCursor)
}, { immediate: true })

function handleHtmlChanged(html: string) {
  const h = calculateHtmlStats(html)
  stats.htmlChars = h.chars
  stats.htmlParagraphs = h.paragraphs
}

function updateCursorInfo(view: NonNullable<ReturnType<ExposeParam['getEditorView']>>) {
  const state = view.state
  const main = state.selection.main
  const line = state.doc.lineAt(main.head)
  stats.cursorLine = line.number
  stats.cursorCol = main.head - line.from + 1
  if (main.empty) {
    stats.hasSelection = false
    stats.selChars = 0
    stats.selLines = 0
  } else {
    stats.hasSelection = true
    const sel = state.sliceDoc(main.from, main.to)
    const r = countCharsAndLines(sel)
    stats.selChars = r.chars
    stats.selLines = r.lines
  }
}

let previewWrapperEl: Element | null = null
function checkPreviewSelection() {
  const sel = window.getSelection()
  if (!sel || sel.isCollapsed) {
    stats.hasPreviewSelection = false
    stats.previewSelChars = 0
    stats.previewSelLines = 0
    return
  }
  if (!previewWrapperEl) {
    previewWrapperEl = document.querySelector(`#${props.editorId} .md-editor-preview-wrapper`)
  }
  if (!previewWrapperEl?.contains(sel.anchorNode)) {
    stats.hasPreviewSelection = false
    stats.previewSelChars = 0
    stats.previewSelLines = 0
    return
  }
  const text = sel.toString()
  const r = countCharsAndLines(text)
  stats.hasPreviewSelection = true
  stats.previewSelChars = r.chars
  stats.previewSelLines = r.lines
}

function syncEditorCursor() {
  const view = editorRef.value?.getEditorView()
  if (view) updateCursorInfo(view)
}

let previewSelTimer: ReturnType<typeof setTimeout> | undefined
function handleSelectionChange() {
  if (previewSelTimer) return
  const active = document.activeElement
  if (active?.closest('.cm-content')) return
  previewSelTimer = setTimeout(() => {
    previewSelTimer = undefined
    checkPreviewSelection()
  }, 50)
}

const mdStatusText = computed(() => {
  if (stats.hasSelection) {
    return `Markdown 已选中 <b>${stats.selChars}</b> 字 <b>${stats.selLines}</b> 行`
  }
  return `Markdown <b>${stats.mdChars}</b> 字 <b>${stats.mdLines}</b> 行 <b>第 ${stats.cursorLine} 行, 第 ${stats.cursorCol} 列</b>`
})

const htmlStatusText = computed(() => {
  if (stats.hasPreviewSelection) {
    return `HTML 已选中 <b>${stats.previewSelChars}</b> 字 <b>${stats.previewSelLines}</b> 行`
  }
  return `HTML <b>${stats.htmlChars}</b> 字 <b>${stats.htmlParagraphs}</b> 段`
})


// ========== 浮动工具栏 ==========
const showFloatingToolbar = ref(false)
const floatingToolbarRef = ref<HTMLElement>()
let lastMousePos = { x: 0, y: 0 }
let cachedTbSize = { w: 0, h: 0 }

function positionToolbar(pos: { x: number; y: number }) {
  const el = floatingToolbarRef.value
  if (!el) return
  if (!cachedTbSize.w && el.offsetWidth) {
    cachedTbSize.w = el.offsetWidth
    cachedTbSize.h = el.offsetHeight
  }
  const tbW = cachedTbSize.w || 360
  const tbH = cachedTbSize.h || 34
  let left = pos.x - tbW / 2
  let top = pos.y - tbH - 8
  if (left + tbW > window.innerWidth - 8) left = window.innerWidth - tbW - 8
  if (left < 8) left = 8
  if (top < 4) top = pos.y + 18
  el.style.left = left + 'px'
  el.style.top = top + 'px'
}

function doFormat(action: string) {
  editorRef.value?.insert((selectedText) => applyFormat(selectedText, action))
  showFloatingToolbar.value = false
}

function insertHorizontalRule() {
  editorRef.value?.insert(() => ({ targetValue: '\n\n---\n\n', select: false, deviationStart: 0, deviationEnd: 0 }))
}

// ========== AI 写作助手 ==========
const aiChatBubbleRef = ref<InstanceType<typeof import('../ai/AiChatBubble.vue')['default']>>()
const currentSelectedText = ref('')

function doAiAction(action: string) {
  // 获取当前选中文本
  currentSelectedText.value = editorRef.value?.getSelectedText() || ''
  showFloatingToolbar.value = false
  aiChatBubbleRef.value?.openWithAction(action)
}

function syncFloatingToolbar(pos?: { x: number; y: number }) {
  syncEditorCursor()
  const sel = editorRef.value?.getSelectedText() || ''
  if (sel.length > 0) {
    currentSelectedText.value = sel
    showFloatingToolbar.value = true
    const fallback = pos || lastMousePos || { x: window.innerWidth / 2, y: 96 }
    nextTick(() => positionToolbar(fallback))
    return
  }
  currentSelectedText.value = ''
  showFloatingToolbar.value = false
}

function handleAiInsert(text: string) {
  editorRef.value?.insert(() => ({ targetValue: text, select: false, deviationStart: 0, deviationEnd: 0 }))
}

function handleAiReplace(text: string) {
  editorRef.value?.insert(() => ({ targetValue: text, select: true, deviationStart: 0, deviationEnd: 0 }))
}

// ========== 快捷键弹窗 ==========
const showShortcuts = ref(false)
const shortcutList = [
  { key: 'Ctrl + B', desc: '粗体' },
  { key: 'Ctrl + U', desc: '下划线' },
  { key: 'Ctrl + I', desc: '斜体' },
  { key: 'Ctrl + D', desc: '删除线' },
  { key: 'Ctrl + K', desc: '链接' },
  { key: 'Ctrl + S', desc: '保存' },
  { key: 'Ctrl + Z', desc: '撤销' },
  { key: 'Ctrl + Shift + Z', desc: '重做' },
  { key: 'Ctrl + 1~6', desc: '一~六级标题' },
  { key: 'Ctrl + ↑', desc: '上标' },
  { key: 'Ctrl + ↓', desc: '下标' },
  { key: 'Ctrl + Q', desc: '引用' },
  { key: 'Ctrl + O', desc: '有序列表' },
  { key: 'Ctrl + L', desc: '无序列表' },
  { key: 'Ctrl + Shift + C', desc: '行内代码' },
  { key: 'Ctrl + Shift + K', desc: '代码块' },
  { key: 'Ctrl + Shift + I', desc: '图片' },
  { key: 'Ctrl + Shift + T', desc: '表格' },
  { key: 'Ctrl + Shift + F', desc: '格式化 (Prettier)' },
  { key: 'Tab', desc: '缩进' },
  { key: 'Shift + Tab', desc: '取消缩进' },
]

// ========== 当前时间 ==========
const currentTime = ref('')
let timeTimer: ReturnType<typeof setInterval> | undefined

function updateTime() {
  const now = new Date()
  const pad = (n: number) => n.toString().padStart(2, '0')
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六']
  currentTime.value = `${now.getFullYear()}/${pad(now.getMonth() + 1)}/${pad(now.getDate())} ${pad(now.getHours())}:${pad(now.getMinutes())}:${pad(now.getSeconds())} ${weekdays[now.getDay()]}`
}


// ========== 图片上传 ==========

const IMAGE_UPLOAD_CONCURRENCY = 2

async function runWithConcurrency<T, R>(
  items: T[],
  limit: number,
  task: (item: T) => Promise<R>,
): Promise<Array<PromiseSettledResult<R>>> {
  const results: Array<PromiseSettledResult<R>> = new Array(items.length)
  let nextIndex = 0

  async function worker() {
    while (nextIndex < items.length) {
      const currentIndex = nextIndex
      nextIndex += 1
      try {
        results[currentIndex] = { status: 'fulfilled', value: await task(items[currentIndex] as T) }
      } catch (reason) {
        results[currentIndex] = { status: 'rejected', reason }
      }
    }
  }

  const workerCount = Math.min(Math.max(limit, 1), items.length)
  await Promise.all(Array.from({ length: workerCount }, () => worker()))
  return results
}

/** 将图片文件转换为 webp 格式 */
function convertToWebp(file: File, quality = 0.85): Promise<File> {
  return new Promise((resolve, reject) => {
    // 已经是 webp 格式则跳过转换
    if (file.type === 'image/webp') { resolve(file); return }
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => {
      const canvas = document.createElement('canvas')
      canvas.width = img.naturalWidth
      canvas.height = img.naturalHeight
      const ctx = canvas.getContext('2d')
      if (!ctx) { URL.revokeObjectURL(url); resolve(file); return }
      ctx.drawImage(img, 0, 0)
      canvas.toBlob(
        (blob) => {
          URL.revokeObjectURL(url)
          if (!blob) { resolve(file); return }
          // 保留原文件名，替换扩展名为 .webp
          const name = file.name.replace(/\.[^.]+$/, '.webp')
          resolve(new File([blob], name, { type: 'image/webp' }))
        },
        'image/webp',
        quality,
      )
    }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('图片加载失败')) }
    img.src = url
  })
}

async function handleUploadImg(
  files: File[],
  callback: (urls: Array<{ url: string; alt: string; title: string }>) => void,
) {
  const settled = await runWithConcurrency(
    files,
    IMAGE_UPLOAD_CONCURRENCY,
    async (file) => {
      const webpFile = await convertToWebp(file)
      const finalFile = shouldApplyWatermark(props.imageWatermark as ImageWatermarkConfig, 'content')
        ? await applyWatermarkToFile(webpFile, props.imageWatermark as ImageWatermarkConfig)
        : webpFile
      const res = await uploadApi.image(finalFile, 'content')
      return { url: res.data, alt: file.name, title: '' }
    },
  )
  const results = settled
    .filter((r): r is PromiseFulfilledResult<{ url: string; alt: string; title: string }> => r.status === 'fulfilled')
    .map(r => r.value)
  callback(results)
}

function handleSave(v: string) {
  emit('save', v)
}

// ========== 生命周期 ==========
const NAV_KEYS = new Set(['ArrowUp', 'ArrowDown', 'ArrowLeft', 'ArrowRight', 'Home', 'End', 'PageUp', 'PageDown'])
const cleanups: Array<() => void> = []

onMounted(() => {
  updateTime()
  timeTimer = setInterval(updateTime, 1000)

  const tryInit = () => {
    const view = editorRef.value?.getEditorView()
    if (view) updateCursorInfo(view)
  }
  nextTick(tryInit)
  setTimeout(tryInit, 500)

  document.addEventListener('selectionchange', handleSelectionChange)

  let isMouseDown = false
  let editorCursorTimer: ReturnType<typeof setTimeout> | undefined
  let editorHandlersBound = false

  function handleGlobalMouseUp(event: MouseEvent) {
    isMouseDown = false
    if (editorCursorTimer) { clearTimeout(editorCursorTimer); editorCursorTimer = undefined }
    lastMousePos = { x: event.clientX, y: event.clientY }
    syncFloatingToolbar(lastMousePos)
  }
  document.addEventListener('mouseup', handleGlobalMouseUp)
  cleanups.push(() => document.removeEventListener('mouseup', handleGlobalMouseUp))

  const bindEditorHandlers = () => {
    if (editorHandlersBound || !editorRef.value?.getEditorView()) return
    editorHandlersBound = true
    editorRef.value?.domEventHandlers({
      mousedown: () => {
        isMouseDown = true
        showFloatingToolbar.value = false
        return false
      },
      mousemove: () => {
        if (isMouseDown && !editorCursorTimer) {
          editorCursorTimer = setTimeout(() => { editorCursorTimer = undefined; syncEditorCursor() }, 50)
        }
        return false
      },
      keyup: (event: KeyboardEvent) => {
        const key = event.key.toLowerCase()
        if (NAV_KEYS.has(event.key) || event.shiftKey || ((event.ctrlKey || event.metaKey) && key === 'a')) {
          syncFloatingToolbar({ x: window.innerWidth / 2, y: 96 })
        }
        return false
      },
    })
  }

  nextTick(() => {
    let attempts = 0
    const bindTimer = setInterval(() => {
      attempts += 1
      bindEditorHandlers()
      if (editorHandlersBound || attempts >= 20) clearInterval(bindTimer)
    }, 150)
    cleanups.push(() => clearInterval(bindTimer))
  })
})

onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
  if (previewSelTimer) clearTimeout(previewSelTimer)
  document.removeEventListener('selectionchange', handleSelectionChange)
  cleanups.forEach(fn => fn())
})

function refresh() {
  editorRef.value?.rerender()
}

defineExpose({ refresh })
</script>


<style scoped>
/* 状态栏 */
.custom-status-bar {
  display: flex;
  align-items: center;
  height: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  gap: 0;
  white-space: nowrap;
}
.status-item {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  padding: 0 8px;
}
.status-item :deep(b) {
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
  margin: 0 1px;
}
.status-divider {
  width: 1px;
  height: 12px;
  background: var(--el-border-color, #dcdfe6);
  flex-shrink: 0;
}
/* 页脚时间 */
.footer-time {
  display: inline-flex;
  align-items: center;
  height: 100%;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  white-space: nowrap;
}
/* 主题下拉列表 */
.theme-list {
  list-style: none;
  margin: 0;
  padding: 4px 0;
  min-width: 120px;
}
.theme-list li {
  padding: 6px 16px;
  font-size: 13px;
  cursor: pointer;
  transition: background-color 200ms;
}
.theme-list li:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}
.theme-list li.active {
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}
</style>


<style>
/* 浮动工具栏（Teleport 到 body，不能 scoped） */
.floating-toolbar {
  position: fixed;
  z-index: 9999;
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 6px;
  background: #1e1e1e;
  border-radius: 6px;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.25);
  user-select: none;
}
.ft-fade-enter-active {
  transition: opacity 150ms ease-out, transform 150ms ease-out;
}
.ft-fade-leave-active {
  transition: opacity 100ms ease-in, transform 100ms ease-in;
}
.ft-fade-enter-from {
  opacity: 0;
  transform: translateY(4px);
}
.ft-fade-leave-to {
  opacity: 0;
  transform: translateY(-2px);
}
.floating-toolbar button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: none;
  background: transparent;
  color: #ccc;
  border-radius: 4px;
  cursor: pointer;
  transition: background-color 200ms, color 200ms;
}
.floating-toolbar button:hover {
  background: rgba(255, 255, 255, 0.12);
  color: #fff;
}
.floating-toolbar .ft-text-btn {
  font-size: 11px;
  font-weight: 600;
  width: auto;
  padding: 0 6px;
}
.floating-toolbar .ft-divider {
  width: 1px;
  height: 16px;
  background: rgba(255, 255, 255, 0.15);
  margin: 0 2px;
}
.floating-toolbar .ft-ai-btn {
  display: flex;
  align-items: center;
  gap: 3px;
  width: auto;
  padding: 0 8px;
  font-size: 11px;
  font-weight: 500;
  color: #a78bfa;
}
.floating-toolbar .ft-ai-btn:hover {
  background: rgba(167, 139, 250, 0.15);
  color: #c4b5fd;
}

/* 编辑器边框和暗黑模式适配 */
#md-editor {
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
  border-radius: 8px;
  overflow: hidden;
}
.md-editor-dark#md-editor {
  border-color: var(--el-border-color-darker, #414243);
}
/* 页脚项垂直居中 */
#md-editor .md-editor-footer-item {
  display: flex;
  align-items: center;
}

#md-editor .md-editor-preview blockquote {
  margin: 16px 0;
  padding: 16px 24px;
}
#md-editor .md-editor-preview blockquote > :first-child {
  margin-top: 0;
}
#md-editor .md-editor-preview blockquote > :last-child {
  margin-bottom: 0;
}

/* 快捷键弹窗 */
.shortcuts-overlay {
  position: fixed;
  inset: 0;
  z-index: 10000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.4);
}
.shortcuts-panel {
  width: 420px;
  max-height: 80vh;
  background: var(--el-bg-color, #fff);
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.2);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.shortcuts-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
}
.shortcuts-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.shortcuts-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 200ms, color 200ms;
}
.shortcuts-close:hover {
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-primary, #303133);
}
.shortcuts-body {
  padding: 8px 20px 16px;
  overflow-y: auto;
}
.shortcut-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
}
.shortcut-desc {
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
}
.shortcut-key {
  font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
  font-size: 12px;
  padding: 2px 8px;
  background: var(--el-fill-color, #f0f2f5);
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 4px;
  color: var(--el-text-color-primary, #303133);
  white-space: nowrap;
}
.shortcuts-fade-enter-active {
  transition: opacity 200ms ease-out;
}
.shortcuts-fade-leave-active {
  transition: opacity 150ms ease-in;
}
.shortcuts-fade-enter-from,
.shortcuts-fade-leave-to {
  opacity: 0;
}
</style>
