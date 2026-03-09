<template>
  <!-- 悬浮球（可拖动） -->
  <Teleport to="body">
    <div
      v-show="!chatVisible"
      ref="fabRef"
      class="ai-fab"
      :style="fabStyle"
      @mousedown="onFabMouseDown"
      @click="onFabClick"
    >
      <svg viewBox="0 0 24 24" width="24" height="24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/>
      </svg>
    </div>

    <!-- 对话弹窗 -->
    <Transition name="ai-chat-pop">
      <div v-show="chatVisible" class="ai-chat-window" :style="chatWindowStyle">
        <!-- 头部（可拖动） -->
        <div class="ai-chat-header" @mousedown="onChatHeaderMouseDown" style="cursor: grab;">
          <div class="ai-chat-header-left">
            <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"/></svg>
            <span>AI 助手</span>
          </div>
          <div class="ai-chat-header-actions">
            <!-- 问题跳转下拉 -->
            <el-popover
              placement="bottom"
              :width="240"
              trigger="click"
              :disabled="userMsgIndices.length === 0"
              :popper-style="{ zIndex: 9999, maxWidth: '390px' }"
              :teleported="true"
            >
              <template #reference>
                <button
                  class="ai-chat-header-btn"
                  title="消息列表"
                  :disabled="userMsgIndices.length === 0"
                >
                  <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M8 6h13M8 12h13M8 18h13M3 6h.01M3 12h.01M3 18h.01"/></svg>
                </button>
              </template>
              <div class="ai-question-list">
                <div class="ai-question-list-title">消息列表</div>
                <div
                  v-for="(qIdx, i) in userMsgIndices"
                  :key="qIdx"
                  :class="['ai-question-item', { active: currentQuestionIdx === i }]"
                  @click="jumpToQuestion(i)"
                >
                  <span class="ai-question-num">{{ i + 1 }}</span>
                  <span class="ai-question-text">{{ getQuestionPreview(qIdx) }}</span>
                </div>
              </div>
            </el-popover>
            <button class="ai-chat-header-btn" title="清空对话" @click="handleClearChat">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg>
            </button>
            <button class="ai-chat-header-btn" title="关闭" @click="chatVisible = false">
              <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"><path d="M18 6L6 18M6 6l12 12"/></svg>
            </button>
          </div>
        </div>

        <!-- 消息区域 -->
        <div class="ai-chat-body" ref="chatBodyRef">
          <!-- 欢迎消息 -->
          <div v-if="messages.length === 0" class="ai-chat-welcome">
            <p>你好，我是 AI 写作助手。</p>
            <p>选中编辑器中的文字，或使用下方快捷功能开始。</p>
          </div>
          <div
            v-for="(msg, idx) in messages"
            :key="idx"
            :ref="el => setMsgRef(el as HTMLElement | null, idx)"
            :class="['ai-msg', msg.role]"
          >
            <div class="ai-msg-bubble">
              <template v-if="msg.role === 'assistant'">
                <AiStreamRenderer
                  :text="getActiveContent(msg)"
                  :loading="idx === messages.length - 1 && streaming"
                />
                <!-- 多结果切换 + 操作按钮 -->
                <div v-if="getActiveContent(msg) && !(idx === messages.length - 1 && streaming)" class="ai-msg-actions">
                  <!-- 重新生成 -->
                  <button @click="handleRegenerate(idx)" title="重新生成" :disabled="streaming">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 4v6h6"/><path d="M3.51 15a9 9 0 1 0 2.13-9.36L1 10"/></svg>
                    重新生成
                  </button>
                  <!-- 多结果切换 -->
                  <div v-if="msg.contents && msg.contents.length > 1" class="ai-result-switcher">
                    <button
                      class="ai-switch-btn"
                      :disabled="msg.activeIndex === 0"
                      @click="switchResult(msg, -1)"
                    >‹</button>
                    <span class="ai-switch-label">{{ (msg.activeIndex ?? 0) + 1 }}/{{ msg.contents.length }}</span>
                    <button
                      class="ai-switch-btn"
                      :disabled="msg.activeIndex === msg.contents.length - 1"
                      @click="switchResult(msg, 1)"
                    >›</button>
                  </div>
                  <span class="ai-action-sep" />
                  <button @click="handleInsertResult(getActiveContent(msg))" title="插入到光标处">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
                    插入
                  </button>
                  <button @click="handleReplaceResult(getActiveContent(msg))" title="替换选中文本">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><path d="M17 1l4 4-4 4"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><path d="M7 23l-4-4 4-4"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg>
                    替换
                  </button>
                  <button @click="handleCopy(getActiveContent(msg))" title="复制">
                    <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"/><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"/></svg>
                    复制
                  </button>
                </div>
              </template>
              <template v-else>
                <div class="ai-msg-user-text">{{ msg.content }}</div>
              </template>
            </div>
          </div>
        </div>

        <!-- 底部输入区 -->
        <div class="ai-chat-footer">
          <!-- 快捷功能按钮 -->
          <div class="ai-quick-actions">
            <button
              v-for="action in quickActions"
              :key="action.key"
              :class="['ai-quick-btn', { disabled: action.needSelection && !hasSelection }]"
              :title="action.needSelection && !hasSelection ? '请先选中编辑器中的文字' : action.label"
              @click="handleQuickAction(action.key)"
            >
              <component :is="action.icon" />
              {{ action.label }}
            </button>
          </div>
          <!-- 翻译语言选择 -->
          <div v-if="showTranslateLang" class="ai-translate-picker">
            <button :class="{ active: targetLang === 'en' }" @click="targetLang = 'en'; doTranslate()">中 → 英</button>
            <button :class="{ active: targetLang === 'zh' }" @click="targetLang = 'zh'; doTranslate()">英 → 中</button>
          </div>
          <!-- 输入框 -->
          <div class="ai-chat-input-wrap">
            <el-input
              ref="inputRef"
              v-model="inputText"
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 4 }"
              placeholder="输入创作要求，AI 帮你写"
              :disabled="streaming"
              @keydown="handleInputKeydown"
              resize="none"
            />
            <!-- streaming 时显示停止按钮，否则显示发送按钮 -->
            <button
              v-if="streaming"
              class="ai-stop-btn"
              title="停止生成"
              @click="handleStop"
            >
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><rect x="6" y="6" width="12" height="12" rx="2"/></svg>
            </button>
            <button
              v-else
              class="ai-send-btn"
              :disabled="!inputText.trim()"
              @click="handleSend"
            >
              <svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor"><path d="M2.01 21L23 12 2.01 3 2 10l15 2-15 2z"/></svg>
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { aiApi, type ChatMessage } from '~/api/ai'
import { handleAiError } from '~/utils/aiError'

// 扩展消息类型：assistant 消息支持多结果
interface BubbleMessage extends ChatMessage {
  contents?: string[]   // 多次生成的结果
  activeIndex?: number  // 当前展示的结果索引
  // 记录生成该消息时的动作和参数，用于重新生成
  _action?: string
  _actionParams?: Record<string, unknown>
}

// 快捷功能图标
const IconContinue = () => h('svg', { viewBox: '0 0 24 24', width: 14, height: 14, fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [h('path', { d: 'M5 12h14M12 5l7 7-7 7' })])
const IconPolish = () => h('svg', { viewBox: '0 0 24 24', width: 14, height: 14, fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [h('path', { d: 'M12 20h9M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z' })])
const IconRewrite = () => h('svg', { viewBox: '0 0 24 24', width: 14, height: 14, fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [h('path', { d: 'M17 1l4 4-4 4' }), h('path', { d: 'M3 11V9a4 4 0 0 1 4-4h14' }), h('path', { d: 'M7 23l-4-4 4-4' }), h('path', { d: 'M21 13v2a4 4 0 0 1-4 4H3' })])
const IconTranslate = () => h('svg', { viewBox: '0 0 24 24', width: 14, height: 14, fill: 'none', stroke: 'currentColor', 'stroke-width': 2 }, [h('path', { d: 'M5 8l6 6M4 14l6-6 2-3M2 5h12M7 2h1' }), h('path', { d: 'M22 22l-5-10-5 10M14 18h6' })])

const quickActions = [
  { key: 'continue', label: '续写', icon: IconContinue, needSelection: false },
  { key: 'polish', label: '润色', icon: IconPolish, needSelection: true },
  { key: 'rewrite', label: '改写', icon: IconRewrite, needSelection: true },
  { key: 'translate', label: '翻译', icon: IconTranslate, needSelection: true },
]

const props = defineProps<{
  editorContext: string
  selectedText: string
}>()

const emit = defineEmits<{
  insert: [text: string]
  replace: [text: string]
}>()

const chatVisible = ref(false)
const inputText = ref('')
const messages = ref<BubbleMessage[]>([])
const streaming = ref(false)
const chatBodyRef = ref<HTMLElement>()
const inputRef = ref<InstanceType<typeof import('element-plus')['ElInput']>>()
const targetLang = ref<'zh' | 'en'>('en')
const showTranslateLang = ref(false)

let currentSse: ReturnType<typeof aiApi.writingContinue> | null = null

const hasSelection = computed(() => !!props.selectedText?.trim())

// ========== 获取当前展示内容 ==========
function getActiveContent(msg: BubbleMessage): string {
  let text = ''
  if (msg.contents && msg.contents.length > 0) {
    text = msg.contents[msg.activeIndex ?? 0]
  } else {
    text = msg.content
  }
  return normalizeMarkdown(text)
}

// 规范化 Markdown：修复标题 # 后缺少空格的问题
function normalizeMarkdown(text: string): string {
  if (!text) return text
  // 匹配行首 1-6 个 # 后直接跟非空格非#字符的情况，插入空格
  return text.replace(/^(#{1,6})(?=[^\s#])/gm, '$1 ')
}

// ========== 多结果切换 ==========
function switchResult(msg: BubbleMessage, delta: number) {
  if (!msg.contents) return
  const idx = (msg.activeIndex ?? 0) + delta
  if (idx < 0 || idx >= msg.contents.length) return
  msg.activeIndex = idx
  msg.content = msg.contents[idx]
}

// ========== 悬浮球拖拽 ==========
const fabRef = ref<HTMLElement>()
const fabPos = reactive({ x: -1, y: -1 })
let isDragging = false
let dragStartX = 0
let dragStartY = 0
let fabStartX = 0
let fabStartY = 0
let hasMoved = false
let rafId = 0
let pendingX = 0
let pendingY = 0

const fabStyle = computed(() => {
  if (fabPos.x < 0) return {}
  return {
    right: 'auto',
    bottom: 'auto',
    left: `${fabPos.x}px`,
    top: `${fabPos.y}px`,
  }
})

// 聊天窗口位置：支持拖拽独立定位
const chatWindowPos = reactive({ x: -1, y: -1 })
let chatDragging = false
let chatDragStartX = 0
let chatDragStartY = 0
let chatStartX = 0
let chatStartY = 0
let chatHasMoved = false
let chatRafId = 0
let chatPendingX = 0
let chatPendingY = 0

// 计算弹窗初始位置（基于悬浮球位置，支持上下左右四个方向）
function calcInitialChatPos(): { left: number; top: number; maxHeight?: number } {
  const winW = 420
  const winH = 600
  const vw = window.innerWidth
  const vh = window.innerHeight
  const gap = 12
  const edge = 8
  const fabSize = 48

  if (fabPos.x < 0) {
    // 默认位置：右下角
    return { left: vw - winW - 24, top: vh - winH - 24 }
  }

  const fabCx = fabPos.x + fabSize / 2
  const fabCy = fabPos.y + fabSize / 2

  // 计算四个方向的可用空间
  const spaceAbove = fabPos.y - edge
  const spaceBelow = vh - (fabPos.y + fabSize) - edge
  const spaceLeft = fabPos.x - edge
  const spaceRight = vw - (fabPos.x + fabSize) - edge

  // 找出最佳方向：优先上/下，空间不够再考虑左/右
  type Direction = 'above' | 'below' | 'left' | 'right'
  const candidates: { dir: Direction; space: number }[] = [
    { dir: 'above', space: spaceAbove },
    { dir: 'below', space: spaceBelow },
    { dir: 'left', space: spaceLeft },
    { dir: 'right', space: spaceRight },
  ]

  // 优先选择能完整放下窗口的方向（上 > 下 > 左 > 右）
  const verticalFit = candidates.filter(c =>
    (c.dir === 'above' || c.dir === 'below') && c.space >= winH + gap
  )
  const horizontalFit = candidates.filter(c =>
    (c.dir === 'left' || c.dir === 'right') && c.space >= winW + gap
  )

  let bestDir: Direction
  if (verticalFit.length > 0) {
    bestDir = verticalFit[0].dir
  } else if (horizontalFit.length > 0) {
    bestDir = horizontalFit[0].dir
  } else {
    // 都放不下，选空间最大的方向
    bestDir = candidates.sort((a, b) => b.space - a.space)[0].dir
  }

  let left: number
  let top: number
  let maxHeight: number | undefined

  if (bestDir === 'above') {
    left = Math.max(edge, Math.min(vw - winW - edge, fabCx - winW / 2))
    top = fabPos.y - gap - winH
    if (top < edge) { maxHeight = fabPos.y - gap - edge; top = edge }
  } else if (bestDir === 'below') {
    left = Math.max(edge, Math.min(vw - winW - edge, fabCx - winW / 2))
    top = fabPos.y + fabSize + gap
    if (top + winH > vh - edge) { maxHeight = vh - edge - top }
  } else if (bestDir === 'left') {
    left = fabPos.x - gap - winW
    top = Math.max(edge, Math.min(vh - winH - edge, fabCy - winH / 2))
    if (left < edge) { left = edge }
  } else {
    // right
    left = fabPos.x + fabSize + gap
    top = Math.max(edge, Math.min(vh - winH - edge, fabCy - winH / 2))
    if (left + winW > vw - edge) { left = vw - winW - edge }
  }

  return maxHeight ? { left, top, maxHeight } : { left, top }
}

const chatWindowStyle = computed(() => {
  if (chatWindowPos.x >= 0) {
    // 拖拽后使用独立位置
    return {
      right: 'auto',
      bottom: 'auto',
      left: `${chatWindowPos.x}px`,
      top: `${chatWindowPos.y}px`,
    }
  }
  const pos = calcInitialChatPos()
  const style: Record<string, string> = {
    right: 'auto',
    bottom: 'auto',
    left: `${pos.left}px`,
    top: `${pos.top}px`,
  }
  if (pos.maxHeight) style.maxHeight = `${pos.maxHeight}px`
  return style
})

// 聊天窗口拖拽（通过 header 拖动）
function onChatHeaderMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  // 如果点击的是按钮，不启动拖拽
  if ((e.target as HTMLElement).closest('button, .el-popover__reference, .ai-chat-header-btn')) return
  chatDragging = true
  chatHasMoved = false
  chatDragStartX = e.clientX
  chatDragStartY = e.clientY
  // 获取当前窗口实际位置
  const chatEl = (e.currentTarget as HTMLElement).parentElement
  if (chatEl) {
    const rect = chatEl.getBoundingClientRect()
    chatStartX = rect.left
    chatStartY = rect.top
  }
  document.addEventListener('mousemove', onChatMouseMove)
  document.addEventListener('mouseup', onChatMouseUp)
  e.preventDefault()
}

function onChatMouseMove(e: MouseEvent) {
  if (!chatDragging) return
  const dx = e.clientX - chatDragStartX
  const dy = e.clientY - chatDragStartY
  if (!chatHasMoved && Math.abs(dx) < 4 && Math.abs(dy) < 4) return
  chatHasMoved = true
  const winW = 420
  const winH = 600
  chatPendingX = Math.max(0, Math.min(window.innerWidth - winW, chatStartX + dx))
  chatPendingY = Math.max(0, Math.min(window.innerHeight - winH, chatStartY + dy))
  if (!chatRafId) {
    chatRafId = requestAnimationFrame(() => {
      chatWindowPos.x = chatPendingX
      chatWindowPos.y = chatPendingY
      chatRafId = 0
    })
  }
}

function onChatMouseUp() {
  chatDragging = false
  document.removeEventListener('mousemove', onChatMouseMove)
  document.removeEventListener('mouseup', onChatMouseUp)
  if (chatRafId) { cancelAnimationFrame(chatRafId); chatRafId = 0 }
  if (chatHasMoved) {
    chatWindowPos.x = chatPendingX
    chatWindowPos.y = chatPendingY
  }
}

function onFabMouseDown(e: MouseEvent) {
  if (e.button !== 0) return
  isDragging = true
  hasMoved = false
  dragStartX = e.clientX
  dragStartY = e.clientY
  const rect = fabRef.value!.getBoundingClientRect()
  fabStartX = rect.left
  fabStartY = rect.top
  document.addEventListener('mousemove', onFabMouseMove)
  document.addEventListener('mouseup', onFabMouseUp)
  e.preventDefault()
}

function onFabMouseMove(e: MouseEvent) {
  if (!isDragging) return
  const dx = e.clientX - dragStartX
  const dy = e.clientY - dragStartY
  if (!hasMoved && Math.abs(dx) < 4 && Math.abs(dy) < 4) return
  hasMoved = true
  const size = 48
  pendingX = Math.max(0, Math.min(window.innerWidth - size, fabStartX + dx))
  pendingY = Math.max(0, Math.min(window.innerHeight - size, fabStartY + dy))
  if (!rafId) {
    rafId = requestAnimationFrame(() => {
      fabPos.x = pendingX
      fabPos.y = pendingY
      rafId = 0
    })
  }
}

function onFabMouseUp() {
  isDragging = false
  document.removeEventListener('mousemove', onFabMouseMove)
  document.removeEventListener('mouseup', onFabMouseUp)
  if (rafId) { cancelAnimationFrame(rafId); rafId = 0 }
  if (hasMoved) {
    fabPos.x = pendingX
    fabPos.y = pendingY
  }
}

function onFabClick() {
  if (hasMoved) return
  chatWindowPos.x = -1
  chatWindowPos.y = -1
  chatVisible.value = true
}

// ========== 问题跳转 ==========
const msgRefs = ref<Map<number, HTMLElement>>(new Map())

function setMsgRef(el: HTMLElement | null, idx: number) {
  if (el) msgRefs.value.set(idx, el)
  else msgRefs.value.delete(idx)
}

const userMsgIndices = computed(() =>
  messages.value.reduce<number[]>((acc, msg, idx) => {
    if (msg.role === 'user') acc.push(idx)
    return acc
  }, [])
)

const currentQuestionIdx = ref(-1)

function getQuestionPreview(msgIdx: number): string {
  const text = messages.value[msgIdx]?.content ?? ''
  return text.length > 40 ? text.slice(0, 40) + '…' : text
}

function jumpToQuestion(qIdx: number) {
  currentQuestionIdx.value = qIdx
  const msgIdx = userMsgIndices.value[qIdx]
  const el = msgRefs.value.get(msgIdx)
  if (el && chatBodyRef.value) {
    el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }
}

watch(() => messages.value.length, () => {
  currentQuestionIdx.value = userMsgIndices.value.length - 1
})

// ========== 外部调用 ==========
function openWithAction(action: string) {
  chatWindowPos.x = -1
  chatWindowPos.y = -1
  chatVisible.value = true
  nextTick(() => {
    if (action === 'translate') {
      showTranslateLang.value = true
    } else {
      showTranslateLang.value = false
      executeWritingAction(action)
    }
  })
}

function handleQuickAction(action: string) {
  if (action !== 'continue' && !hasSelection.value) {
    ElMessage.warning('请先在编辑器中选中文字')
    return
  }
  if (action === 'translate') {
    showTranslateLang.value = true
    return
  }
  showTranslateLang.value = false
  executeWritingAction(action)
}

function doTranslate() {
  showTranslateLang.value = false
  executeWritingAction('translate')
}

// ========== 创建 SSE 连接并绑定回调 ==========
function startSseStream(
  sse: ReturnType<typeof aiApi.writingContinue>,
  assistantIdx: number,
  contentIdx: number,
) {
  currentSse = sse
  sse
    .onMessage((chunk: string) => {
      const msg = messages.value[assistantIdx]
      if (msg.contents) {
        msg.contents[contentIdx] += chunk
        msg.content = msg.contents[contentIdx]
      } else {
        msg.content += chunk
      }
      scrollToBottom()
    })
    .onError((err: Error) => {
      streaming.value = false
      handleAiError(err)
    })
    .onDone(() => {
      streaming.value = false
    })
}

// ========== 创建写作 SSE ==========
function createWritingSse(action: string, params: Record<string, unknown>): ReturnType<typeof aiApi.writingContinue> | null {
  switch (action) {
    case 'continue':
      return aiApi.writingContinue({ context: params.context as string })
    case 'polish':
      return aiApi.writingPolish({ text: params.text as string })
    case 'rewrite':
      return aiApi.writingRewrite({ text: params.text as string })
    case 'translate':
      return aiApi.writingTranslate({ text: params.text as string, targetLang: params.targetLang as 'zh' | 'en' })
    case 'chat':
      return aiApi.writingChat(params as { articleContext: string; history: ChatMessage[]; userMessage: string })
    default:
      return null
  }
}

function executeWritingAction(action: string) {
  if (streaming.value) return

  const text = props.selectedText || ''
  const context = props.editorContext || ''

  const actionLabels: Record<string, string> = {
    continue: '请续写以下内容',
    polish: '请润色以下文字',
    rewrite: '请改写以下文字',
    translate: targetLang.value === 'en' ? '请将以下中文翻译为英文' : '请将以下英文翻译为中文',
  }

  const userMsg = action === 'continue'
    ? actionLabels[action]
    : `${actionLabels[action]}：\n\n${text}`

  // 构建动作参数（用于重新生成）
  const actionParams: Record<string, unknown> = action === 'continue'
    ? { context }
    : action === 'translate'
      ? { text, targetLang: targetLang.value }
      : { text }

  messages.value.push({ role: 'user', content: userMsg })
  messages.value.push({
    role: 'assistant',
    content: '',
    contents: [''],
    activeIndex: 0,
    _action: action,
    _actionParams: actionParams,
  })
  const assistantIdx = messages.value.length - 1
  streaming.value = true
  scrollToBottom()

  const sse = createWritingSse(action, actionParams)
  if (!sse) { streaming.value = false; return }
  startSseStream(sse, assistantIdx, 0)
}

function handleSend() {
  if (!inputText.value.trim() || streaming.value) return

  const userMsg = inputText.value.trim()
  messages.value.push({ role: 'user', content: userMsg })
  inputText.value = ''
  streaming.value = true

  const history = messages.value.slice(0, -1).map(m => ({
    role: m.role,
    content: m.content,
  })) as ChatMessage[]

  const actionParams = {
    articleContext: props.editorContext || '',
    history,
    userMessage: userMsg,
  }

  messages.value.push({
    role: 'assistant',
    content: '',
    contents: [''],
    activeIndex: 0,
    _action: 'chat',
    _actionParams: actionParams,
  })
  const assistantIdx = messages.value.length - 1

  const sse = aiApi.writingChat(actionParams)
  startSseStream(sse, assistantIdx, 0)
}

// ========== 重新生成 ==========
function handleRegenerate(msgIdx: number) {
  if (streaming.value) return
  const msg = messages.value[msgIdx]
  if (msg.role !== 'assistant' || !msg._action) return

  // 初始化 contents 数组（兼容旧消息）
  if (!msg.contents) {
    msg.contents = [msg.content]
    msg.activeIndex = 0
  }

  // 添加新的空结果
  msg.contents.push('')
  msg.activeIndex = msg.contents.length - 1
  msg.content = ''
  streaming.value = true
  scrollToBottom()

  // 对 chat 类型，需要重建 history（截取到该消息之前）
  let params = { ...msg._actionParams }
  if (msg._action === 'chat') {
    const history = messages.value.slice(0, msgIdx).map(m => ({
      role: m.role,
      content: m.content,
    })) as ChatMessage[]
    params = { ...params, history }
  }

  const sse = createWritingSse(msg._action, params)
  if (!sse) { streaming.value = false; return }
  startSseStream(sse, msgIdx, msg.activeIndex!)
}

function handleInputKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    handleSend()
  }
}

function handleStop() {
  currentSse?.abort()
  streaming.value = false
}

function handleInsertResult(text: string) {
  emit('insert', text)
}

function handleReplaceResult(text: string) {
  emit('replace', text)
}

async function handleCopy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success('已复制')
  } catch {
    ElMessage.error('复制失败')
  }
}

function handleClearChat() {
  handleStop()
  messages.value = []
  currentQuestionIdx.value = -1
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatBodyRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(chatVisible, (val) => {
  if (val) nextTick(() => inputRef.value?.focus())
})

defineExpose({ openWithAction, chatVisible })
</script>

<style scoped>
/* 悬浮球 */
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 9000;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--el-color-primary, #409eff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: grab;
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.4);
  transition: box-shadow 0.2s;
  user-select: none;
  touch-action: none;
}
.ai-fab:hover {
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.5);
}
.ai-fab:active {
  cursor: grabbing;
}

/* 对话弹窗 */
.ai-chat-window {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 9001;
  width: 420px;
  height: 600px;
  max-height: calc(100vh - 48px);
  background: var(--el-bg-color, #fff);
  border-radius: 16px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--el-border-color-lighter, #e4e7ed);
}

/* 头部 */
.ai-chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 14px;
  border-bottom: 1px solid var(--el-border-color-lighter, #e4e7ed);
  flex-shrink: 0;
  user-select: none;
}
.ai-chat-header:active {
  cursor: grabbing;
}
.ai-chat-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary, #303133);
}
.ai-chat-header-left svg {
  color: var(--el-color-primary, #409eff);
}
.ai-chat-header-actions {
  display: flex;
  gap: 4px;
}
.ai-chat-header-btn {
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
  transition: background-color 0.2s, color 0.2s;
}
.ai-chat-header-btn:hover:not(:disabled) {
  background: var(--el-fill-color-light, #f5f7fa);
  color: var(--el-text-color-primary, #303133);
}
.ai-chat-header-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* 消息区域 */
.ai-chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px 14px;
  scroll-behavior: smooth;
}
.ai-chat-welcome {
  text-align: center;
  color: var(--el-text-color-secondary, #909399);
  font-size: 13px;
  padding: 30px 16px;
  line-height: 1.8;
}
.ai-chat-welcome p {
  margin: 0;
}

/* 消息气泡 */
.ai-msg {
  margin-bottom: 12px;
}
.ai-msg.user {
  display: flex;
  justify-content: flex-end;
}
.ai-msg.assistant {
  display: flex;
  justify-content: flex-start;
}
.ai-msg-bubble {
  max-width: 90%;
  border-radius: 12px;
  overflow: hidden;
}
.ai-msg.user .ai-msg-bubble {
  background: var(--el-color-primary-light-9, #ecf5ff);
  padding: 8px 12px;
}
.ai-msg.assistant .ai-msg-bubble {
  background: var(--el-fill-color-light, #f5f7fa);
  padding: 2px 12px;
}
.ai-msg-user-text {
  font-size: 14px;
  line-height: 1.6;
  color: var(--el-text-color-primary, #303133);
  white-space: pre-wrap;
  word-break: break-word;
}

/* AI 回复操作按钮 */
.ai-msg-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  padding-top: 6px;
  border-top: 1px solid var(--el-border-color-extra-light, #f0f0f0);
  flex-wrap: wrap;
}
.ai-msg-actions button {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  font-size: 12px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.2s, color 0.2s;
}
.ai-msg-actions button:hover:not(:disabled) {
  background: var(--el-fill-color, #f0f2f5);
  color: var(--el-color-primary, #409eff);
}
.ai-msg-actions button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ai-action-sep {
  width: 1px;
  height: 14px;
  background: var(--el-border-color-lighter, #e4e7ed);
  margin: 0 2px;
}

/* 多结果切换 */
.ai-result-switcher {
  display: inline-flex;
  align-items: center;
  gap: 2px;
  margin-left: 2px;
}
.ai-switch-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border: none;
  background: transparent;
  color: var(--el-text-color-secondary, #909399);
  font-size: 14px;
  font-weight: 600;
  border-radius: 4px;
  cursor: pointer;
  padding: 0;
  transition: background-color 0.2s, color 0.2s;
}
.ai-switch-btn:hover:not(:disabled) {
  background: var(--el-fill-color, #f0f2f5);
  color: var(--el-color-primary, #409eff);
}
.ai-switch-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}
.ai-switch-label {
  font-size: 11px;
  color: var(--el-text-color-secondary, #909399);
  min-width: 24px;
  text-align: center;
}

/* 问题列表弹出 */
.ai-question-list {
  max-height: 300px;
  overflow-y: auto;
}
.ai-question-list-title {
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
  padding: 0 0 8px;
  border-bottom: 1px solid var(--el-border-color-extra-light, #f0f0f0);
  margin-bottom: 4px;
}
.ai-question-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 6px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.15s;
}
.ai-question-item:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}
.ai-question-item.active {
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.ai-question-num {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--el-color-primary-light-8, #d9ecff);
  color: var(--el-color-primary, #409eff);
  font-size: 11px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
}
.ai-question-text {
  font-size: 13px;
  color: var(--el-text-color-regular, #606266);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

/* 底部输入区 */
.ai-chat-footer {
  flex-shrink: 0;
  padding: 10px 14px 12px;
  border-top: 1px solid var(--el-border-color-lighter, #e4e7ed);
}
.ai-quick-actions {
  display: flex;
  gap: 6px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.ai-quick-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 12px;
  border: 1px solid var(--el-border-color-light, #dcdfe6);
  background: var(--el-bg-color, #fff);
  color: var(--el-text-color-regular, #606266);
  font-size: 13px;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.2s;
  white-space: nowrap;
}
.ai-quick-btn:hover {
  border-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.ai-quick-btn.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
.ai-quick-btn.disabled:hover {
  border-color: var(--el-border-color-light, #dcdfe6);
  color: var(--el-text-color-regular, #606266);
  background: var(--el-bg-color, #fff);
}
.ai-translate-picker {
  display: flex;
  gap: 6px;
  margin-bottom: 10px;
}
.ai-translate-picker button {
  padding: 4px 14px;
  border: 1px solid var(--el-border-color-light, #dcdfe6);
  background: var(--el-bg-color, #fff);
  color: var(--el-text-color-regular, #606266);
  font-size: 13px;
  border-radius: 14px;
  cursor: pointer;
  transition: all 0.2s;
}
.ai-translate-picker button.active,
.ai-translate-picker button:hover {
  border-color: var(--el-color-primary, #409eff);
  color: var(--el-color-primary, #409eff);
  background: var(--el-color-primary-light-9, #ecf5ff);
}
.ai-chat-input-wrap {
  display: flex;
  align-items: flex-end;
  gap: 8px;
}
.ai-chat-input-wrap :deep(.el-textarea__inner) {
  box-shadow: none !important;
  border: 1px solid var(--el-border-color-light, #dcdfe6);
  border-radius: 10px;
  padding: 8px 12px;
  font-size: 14px;
  transition: border-color 0.2s;
}
.ai-chat-input-wrap :deep(.el-textarea__inner:focus) {
  border-color: var(--el-color-primary, #409eff);
}
.ai-send-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: var(--el-color-primary, #409eff);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: opacity 0.2s;
}
.ai-send-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
.ai-send-btn:not(:disabled):hover {
  opacity: 0.85;
}
.ai-stop-btn {
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 10px;
  background: var(--el-color-danger, #f56c6c);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: opacity 0.2s;
}
.ai-stop-btn:hover {
  opacity: 0.85;
}

/* 弹窗动画 */
.ai-chat-pop-enter-active {
  transition: opacity 0.25s ease-out, transform 0.25s ease-out;
}
.ai-chat-pop-leave-active {
  transition: opacity 0.15s ease-in, transform 0.15s ease-in;
}
.ai-chat-pop-enter-from {
  opacity: 0;
  transform: translateY(20px) scale(0.95);
}
.ai-chat-pop-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}

/* 暗黑模式 */
:root.dark .ai-chat-window {
  border-color: var(--el-border-color-darker, #414243);
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4);
}
:root.dark .ai-fab {
  box-shadow: 0 4px 16px rgba(64, 158, 255, 0.3);
}
</style>
