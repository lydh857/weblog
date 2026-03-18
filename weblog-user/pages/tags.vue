<template>
  <div class="tags-page-bg">
    <div class="tags-page">
      <div class="page-header">
        <div>
          <h1 class="page-title">
            <Icon name="heroicons:tag-20-solid" size="22" />
            标签云
          </h1>
          <p class="page-desc">共 {{ tags.length }} 个标签</p>
        </div>
        <div v-if="tags.length" class="view-toggle">
          <button
            class="toggle-btn"
            :class="{ active: viewMode === '3d' }"
            aria-label="3D 视图"
            @click="setViewMode('3d')"
          >
            <Icon name="heroicons:globe-alt-20-solid" size="18" />
            <span>3D</span>
          </button>
          <button
            class="toggle-btn"
            :class="{ active: viewMode === 'flat' }"
            aria-label="平面视图"
            @click="setViewMode('flat')"
          >
            <Icon name="heroicons:squares-2x2-20-solid" size="18" />
            <span>平面</span>
          </button>
        </div>
      </div>

      <template v-if="tags.length">
        <div
          v-show="viewMode === '3d'"
          ref="cloudRef"
          class="cloud-wrapper"
          :class="{ 'enter-active': entered }"
          @mouseenter="isInsideCloud = true"
          @mouseleave="isInsideCloud = false"
          @mousedown.prevent="onDragStart"
          @wheel.prevent="onWheel"
        >
          <div class="cloud-glow" />
          <div class="cloud-scene">
            <NuxtLink
              v-for="tag in renderedTags"
              :key="tag.id"
              :to="{ path: '/category', query: { tagId: String(tag.id) } }"
              class="cloud-tag"
              :style="tag.style"
            >
              {{ tag.name }}
              <sup class="tag-sup">{{ tag.postCount }}</sup>
            </NuxtLink>
          </div>
        </div>

        <div v-if="viewMode === 'flat'" class="flat-tags" :class="{ 'enter-active': entered, 'is-entering': flatEntering }">
          <NuxtLink
            v-for="(tag, i) in sortedTags"
            :key="tag.id"
            :to="{ path: '/category', query: { tagId: String(tag.id) } }"
            class="flat-tag enter-item"
            :style="{
              '--tag-color': tag.color || getTagColor(i),
              '--tag-bg': (tag.color || getTagColor(i)) + '15',
              '--tag-bg-hover': (tag.color || getTagColor(i)) + '25',
              '--enter-delay': `${Math.min(i, 18) * 0.03}s`,
            }"
          >
            <Icon name="heroicons:tag-16-solid" size="14" class="flat-tag__icon" />
            <span>{{ tag.name }}</span>
            <sup class="flat-tag__count">{{ tag.postCount }}</sup>
          </NuxtLink>
        </div>
      </template>

      <div v-else-if="!loading" class="empty-state" :class="{ 'enter-active': entered }">
        <Icon name="heroicons:inbox-20-solid" size="48" />
        <p>暂无标签</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { tagApi, type TagCloudVO } from '~/api/tag'
import { getTagColor } from '~/utils/tagColor'

useHead({
  title: '标签云 - Weblog',
  meta: [{ name: 'description', content: '博客标签云与标签导航' }],
})

const loading = ref(true)
const tags = ref<TagCloudVO[]>([])
const cloudRef = ref<HTMLElement | null>(null)
const viewMode = ref<'3d' | 'flat'>('3d')
const entered = ref(false)
const flatEntering = ref(false)

const RADIUS = 210
const sphereScale = ref(1)
const rotateX = ref(0)
const rotateY = ref(0)
const isInsideCloud = ref(false)
const isDragging = ref(false)

let dragStartX = 0
let dragStartY = 0
let dragStartRX = 0
let dragStartRY = 0
let dragLatestX = 0
let dragLatestY = 0
let dragRafId = 0

let rafId = 0
let autoAngle = 0
let lastTime = 0

function animateLoop(time: number) {
  if (lastTime) {
    const dt = time - lastTime
    if (!isInsideCloud.value && !isDragging.value) {
      autoAngle += dt * 0.015
      rotateY.value = autoAngle % 360
      if (Math.abs(rotateX.value) > 0.01) {
        rotateX.value *= 0.985
      } else {
        rotateX.value = 0
      }
    }
  }
  lastTime = time
  rafId = requestAnimationFrame(animateLoop)
}

interface TagSphere {
  id: number
  name: string
  slug: string
  postCount: number
  ux: number
  uy: number
  uz: number
  fontSize: number
  colorStr: string
}

const sphereTags = computed<TagSphere[]>(() => {
  const count = tags.value.length
  if (!count) return []
  const golden = Math.PI * (3 - Math.sqrt(5))
  const maxPost = Math.max(...tags.value.map(t => t.postCount), 1)

  return tags.value.map((tag, i) => {
    const y = 1 - ((i + 0.5) / count) * 2
    const rAtY = Math.sqrt(1 - y * y)
    const theta = golden * i
    const fontSize = 13 + (tag.postCount / maxPost) * 11
    const colorStr = tag.color || getTagColor(i)

    return {
      id: tag.id,
      name: tag.name,
      slug: tag.slug,
      postCount: tag.postCount,
      ux: Math.cos(theta) * rAtY,
      uy: y,
      uz: Math.sin(theta) * rAtY,
      fontSize,
      colorStr,
    }
  })
})

const renderedTags = computed(() => {
  const r = RADIUS * sphereScale.value
  const rx = (rotateX.value * Math.PI) / 180
  const ry = (rotateY.value * Math.PI) / 180
  const cosRx = Math.cos(rx)
  const sinRx = Math.sin(rx)
  const cosRy = Math.cos(ry)
  const sinRy = Math.sin(ry)

  return sphereTags.value.map(tag => {
    const x = tag.ux * r
    const y = tag.uy * r
    const z = tag.uz * r

    const x1 = x * cosRy + z * sinRy
    const z1 = -x * sinRy + z * cosRy
    const y1 = y * cosRx - z1 * sinRx
    const z2 = y * sinRx + z1 * cosRx

    const norm = (z2 + r) / (2 * r)
    const scale = 0.55 + norm * 0.45
    const opacity = 0.25 + norm * 0.75

    return {
      ...tag,
      style: {
        transform: `translate3d(${x1}px, ${y1}px, ${z2}px) scale(${scale})`,
        opacity,
        fontSize: tag.fontSize + 'px',
        color: tag.colorStr,
        zIndex: Math.round(z2 + 500),
        filter: isDragging.value
          ? 'none'
          : (norm < 0.3 ? `blur(${(1 - norm * 3.3).toFixed(1)}px)` : 'none'),
      } as Record<string, string | number>,
    }
  })
})

const sortedTags = computed(() => [...tags.value].sort((a, b) => b.postCount - a.postCount))

function onDragStart(e: MouseEvent) {
  isDragging.value = true
  dragStartX = e.clientX
  dragStartY = e.clientY
  dragLatestX = e.clientX
  dragLatestY = e.clientY
  dragStartRX = rotateX.value
  dragStartRY = rotateY.value
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  dragLatestX = e.clientX
  dragLatestY = e.clientY

  if (dragRafId) return
  dragRafId = requestAnimationFrame(() => {
    dragRafId = 0
    rotateY.value = dragStartRY + (dragLatestX - dragStartX) * 0.28
    rotateX.value = dragStartRX - (dragLatestY - dragStartY) * 0.28
    autoAngle = rotateY.value
  })
}

function onDragEnd() {
  if (dragRafId) {
    cancelAnimationFrame(dragRafId)
    dragRafId = 0
  }
  isDragging.value = false
  autoAngle = rotateY.value
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
}

function onWheel(e: WheelEvent) {
  const d = e.deltaY > 0 ? -0.06 : 0.06
  sphereScale.value = Math.min(1.8, Math.max(0.5, sphereScale.value + d))
}

function setViewMode(mode: '3d' | 'flat') {
  if (viewMode.value === mode) return

  if (mode === 'flat') {
    flatEntering.value = true
    viewMode.value = 'flat'
    window.setTimeout(() => {
      flatEntering.value = false
    }, 900)
    return
  }

  flatEntering.value = false
  viewMode.value = '3d'
}

onMounted(async () => {
  try {
    const res = await tagApi.cloud()
    tags.value = res.data
  } catch {
    tags.value = []
  } finally {
    loading.value = false
    await nextTick()
    requestAnimationFrame(() => {
      entered.value = true
    })
  }
  rafId = requestAnimationFrame(animateLoop)
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  if (dragRafId) {
    cancelAnimationFrame(dragRafId)
    dragRafId = 0
  }
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
})
</script>

<style scoped lang="scss">
.tags-page-bg {
  min-height: calc(100vh - 120px);
  background: #f5f5f5;
  .dark & { background: #f5f5f5; }
}

.tags-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
}

.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  margin-bottom: var(--layout-page-header-margin-bottom);
  @media (max-width: $breakpoint-md) {
    flex-direction: column;
    gap: 1rem;
  }
}

.page-title {
  display: flex;
  align-items: center;
  gap: var(--layout-page-title-gap);
  font-size: var(--layout-page-title-size);
  font-weight: 700;
  line-height: 1.2;
  min-height: 2rem;
  margin: 0;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}

.page-desc {
  margin-top: var(--layout-page-desc-margin-top);
  font-size: 0.92rem;
  line-height: 1.4;
  color: $color-text-muted;
  .dark & { color: #94a3b8; }
}

.view-toggle {
  display: flex;
  gap: 4px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 10px;
  padding: 3px;
  .dark & { background: rgba(255, 255, 255, 0.06); }
}

.toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: $color-text-muted;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
  .dark & { color: #94a3b8; }
  &:hover {
    color: $color-text;
    .dark & { color: $color-dark-text; }
  }
  &.active {
    background: #fff;
    color: $color-primary;
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
    .dark & {
      background: $color-dark-bg-secondary;
      color: $color-primary;
    }
  }
}

.cloud-wrapper {
  position: relative;
  max-width: 560px;
  height: 520px;
  margin: 0 auto 2rem;
  perspective: 900px;
  cursor: grab;
  user-select: none;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active { cursor: grabbing; }
  @media (max-width: $breakpoint-md) {
    max-width: 380px;
    height: 380px;
  }
}

.cloud-wrapper.enter-active {
  animation: tagsPanelEnter 0.36s ease-out both;
}

.cloud-glow {
  position: absolute;
  width: 320px;
  height: 320px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(99, 102, 241, 0.12) 0%, transparent 70%);
  pointer-events: none;
  animation: glowPulse 4s ease-in-out infinite;
  .dark & {
    background: radial-gradient(circle, rgba(99, 102, 241, 0.2) 0%, transparent 70%);
  }
}

@keyframes glowPulse {
  0%, 100% { transform: scale(1); opacity: 0.8; }
  50% { transform: scale(1.15); opacity: 1; }
}

.cloud-scene {
  position: relative;
  width: 420px;
  height: 420px;
  transform-style: preserve-3d;
  @media (max-width: $breakpoint-md) {
    width: 320px;
    height: 320px;
  }
}

.cloud-tag {
  position: absolute;
  left: 50%;
  top: 50%;
  white-space: nowrap;
  text-decoration: none;
  font-weight: 600;
  cursor: pointer;
  will-change: transform, opacity;
  transition: text-shadow 0.25s, filter 0.25s;
  text-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);

  &:hover {
    text-shadow:
      0 0 8px currentColor,
      0 0 20px currentColor,
      0 0 40px currentColor;
    opacity: 1 !important;
    filter: none !important;
  }

  .tag-sup {
    font-size: 0.6em;
    opacity: 0.5;
    margin-left: 1px;
  }
}

.flat-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: center;
}

.flat-tags.is-entering .flat-tag.enter-item {
  opacity: 0;
  transform: translate3d(0, 8px, 0);
  animation: tagsItemEnter 0.32s ease-out forwards;
  animation-delay: var(--enter-delay, 0s);
}

.flat-tag {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 14px;
  border-radius: 20px;
  text-decoration: none;
  font-size: 0.88rem;
  font-weight: 500;
  color: var(--tag-color);
  background: var(--tag-bg);
  border: 1px solid transparent;
  transition: all 0.2s;
  cursor: pointer;
  &:hover {
    background: var(--tag-bg-hover);
    border-color: var(--tag-color);
    transform: translateY(-1px);
  }

  &__icon {
    color: var(--tag-color);
    opacity: 0.7;
  }

  &__count {
    font-size: 0.7em;
    opacity: 0.6;
    margin-left: 1px;
  }
}

.empty-state {
  text-align: center;
  padding: 4rem;
  color: $color-text-muted;
  p { margin-top: 1rem; }
}

.empty-state.enter-active {
  animation: tagsPanelEnter 0.32s ease-out both;
}

@keyframes tagsPanelEnter {
  from {
    opacity: 0;
    transform: translate3d(0, 10px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@keyframes tagsItemEnter {
  from {
    opacity: 0;
    transform: translate3d(0, 8px, 0);
  }
  to {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }
}

@media (prefers-reduced-motion: reduce) {
  .cloud-wrapper.enter-active,
  .empty-state.enter-active,
  .flat-tags.is-entering .flat-tag.enter-item {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
  }
}
</style>
