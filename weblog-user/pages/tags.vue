<template>
  <div class="tags-page-bg">
    <div class="tags-page">
      <div class="page-header">
        <div class="page-title-wrap">
          <h1 class="page-title">
            <span class="page-title-main">
              <Icon name="heroicons:tag-20-solid" size="22" />
              <span>标签云</span>
            </span>
            <span class="title-count-inline">共 {{ tags.length }} 个标签</span>
          </h1>
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
          class="cloud-stage"
          :class="{ 'enter-active': entered }"
        >
          <div
            ref="cloudRef"
            class="cloud-wrapper"
            @mouseenter="onCloudMouseMove"
            @mousemove="onCloudMouseMove"
            @mouseleave="onCloudMouseLeave"
            @mousedown.prevent="onDragStart"
            @touchstart="onTouchStart"
            @touchmove="onTouchMove"
            @touchend="onTouchEnd"
            @touchcancel="onTouchCancel"
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
        </div>

        <div v-show="viewMode === '3d'" class="cloud-gesture-tip" aria-hidden="true">
          <span>单指拖动旋转 · 双指缩放</span>
          <span class="cloud-scale-chip">{{ Math.round(sphereScale * 100) }}%</span>
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

const DESKTOP_RADIUS = 210
const DESKTOP_MIN_SPHERE_SCALE = 0.5
const DESKTOP_MAX_SPHERE_SCALE = 1.8
const DESKTOP_DEFAULT_SPHERE_SCALE = 0.86
const MOBILE_MIN_SPHERE_SCALE = 0.66
const MOBILE_MAX_SPHERE_SCALE = 1.28
const MOBILE_DEFAULT_SPHERE_SCALE = 0.8
const VIEWPORT_MOBILE_BREAKPOINT = 768
const DESKTOP_HOVER_PAUSE_RADIUS_RATIO = 0.96
const MOUSE_ROTATE_SPEED = 0.28
const TOUCH_ROTATE_SPEED = 0.34
const sphereScale = ref(1)
const rotateX = ref(0)
const rotateY = ref(0)
const cloudStageSide = ref(0)
const isInsideCloud = ref(false)
const isDragging = ref(false)
const isMobileViewport = ref(false)
let touchMode: 'none' | 'drag' | 'pinch' = 'none'

let dragStartX = 0
let dragStartY = 0
let dragStartRX = 0
let dragStartRY = 0
let dragLatestX = 0
let dragLatestY = 0
let dragRafId = 0
let dragRotateSpeed = MOUSE_ROTATE_SPEED
let pinchStartDistance = 0
let pinchStartScale = 1

let rafId = 0
let autoAngle = 0
let lastTime = 0
let viewportMediaQuery: MediaQueryList | null = null

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
  const r = getSphereRadius() * sphereScale.value
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
        transform: `translate(-50%, -50%) translate3d(${x1}px, ${y1}px, ${z2}px) scale(${scale})`,
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

if (import.meta.client) {
  isMobileViewport.value = window.innerWidth <= VIEWPORT_MOBILE_BREAKPOINT
  sphereScale.value = isMobileViewport.value ? MOBILE_DEFAULT_SPHERE_SCALE : DESKTOP_DEFAULT_SPHERE_SCALE
}

function getSphereScaleRange() {
  if (isMobileViewport.value) {
    return {
      min: MOBILE_MIN_SPHERE_SCALE,
      max: MOBILE_MAX_SPHERE_SCALE,
    }
  }

  return {
    min: DESKTOP_MIN_SPHERE_SCALE,
    max: DESKTOP_MAX_SPHERE_SCALE,
  }
}

function getSphereRadius() {
  const stageSide = cloudStageSide.value

  if (stageSide > 0) {
    if (isMobileViewport.value) {
      return Math.round(Math.min(240, Math.max(96, stageSide * 0.44)))
    }

    return Math.round(Math.min(330, Math.max(136, stageSide * 0.42)))
  }

  return isMobileViewport.value ? 160 : DESKTOP_RADIUS
}

function handleViewportChange(event: MediaQueryListEvent) {
  const wasMobileViewport = isMobileViewport.value
  isMobileViewport.value = event.matches

  if (wasMobileViewport !== isMobileViewport.value) {
    sphereScale.value = isMobileViewport.value ? MOBILE_DEFAULT_SPHERE_SCALE : DESKTOP_DEFAULT_SPHERE_SCALE
  }

  if (isMobileViewport.value) {
    isInsideCloud.value = false
  }

  clampSphereScale(sphereScale.value)
  updateCloudStageSize()
}

function updateCloudStageSize() {
  if (!import.meta.client) return
  if (!cloudRef.value) return

  const side = Math.min(cloudRef.value.clientWidth, cloudRef.value.clientHeight)
  if (!Number.isFinite(side) || side <= 0) return
  cloudStageSide.value = side
}

function onCloudMouseMove(event: MouseEvent) {
  if (isMobileViewport.value) {
    isInsideCloud.value = false
    return
  }

  const cloudElement = cloudRef.value
  if (!cloudElement) return

  const rect = cloudElement.getBoundingClientRect()
  const centerX = rect.left + rect.width / 2
  const centerY = rect.top + rect.height / 2
  const distance = Math.hypot(event.clientX - centerX, event.clientY - centerY)
  const pauseRadius = Math.max(96, getSphereRadius() * sphereScale.value * DESKTOP_HOVER_PAUSE_RADIUS_RATIO)

  isInsideCloud.value = distance <= pauseRadius
}

function onCloudMouseLeave() {
  isInsideCloud.value = false
}

function handleWindowResize() {
  updateCloudStageSize()
}

function clampSphereScale(nextScale: number) {
  const range = getSphereScaleRange()
  sphereScale.value = Math.min(range.max, Math.max(range.min, nextScale))
}

function beginDrag(clientX: number, clientY: number, speed: number) {
  isDragging.value = true
  dragStartX = clientX
  dragStartY = clientY
  dragLatestX = clientX
  dragLatestY = clientY
  dragStartRX = rotateX.value
  dragStartRY = rotateY.value
  dragRotateSpeed = speed
}

function scheduleDragRotate() {
  if (dragRafId) return

  dragRafId = requestAnimationFrame(() => {
    dragRafId = 0
    rotateY.value = dragStartRY + (dragLatestX - dragStartX) * dragRotateSpeed
    rotateX.value = dragStartRX - (dragLatestY - dragStartY) * dragRotateSpeed
    autoAngle = rotateY.value
  })
}

function finishDrag() {
  if (dragRafId) {
    cancelAnimationFrame(dragRafId)
    dragRafId = 0
  }

  isDragging.value = false
  autoAngle = rotateY.value
}

function getTouchDistance(touches: TouchList) {
  if (touches.length < 2) return 0
  const first = touches[0]
  const second = touches[1]
  const dx = first.clientX - second.clientX
  const dy = first.clientY - second.clientY
  return Math.hypot(dx, dy)
}

function beginTouchDrag(touch: Touch) {
  touchMode = 'drag'
  beginDrag(touch.clientX, touch.clientY, TOUCH_ROTATE_SPEED)
}

function beginPinch(touches: TouchList) {
  const distance = getTouchDistance(touches)
  if (!distance) return

  touchMode = 'pinch'
  pinchStartDistance = distance
  pinchStartScale = sphereScale.value
  isDragging.value = true

  if (dragRafId) {
    cancelAnimationFrame(dragRafId)
    dragRafId = 0
  }
}

function endTouchInteraction() {
  touchMode = 'none'
  pinchStartDistance = 0
  pinchStartScale = sphereScale.value
  finishDrag()
}

function onDragStart(e: MouseEvent) {
  beginDrag(e.clientX, e.clientY, MOUSE_ROTATE_SPEED)
  window.addEventListener('mousemove', onDragMove)
  window.addEventListener('mouseup', onDragEnd)
}

function onDragMove(e: MouseEvent) {
  if (!isDragging.value) return
  dragLatestX = e.clientX
  dragLatestY = e.clientY

  scheduleDragRotate()
}

function onDragEnd() {
  finishDrag()
  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
}

function onTouchStart(e: TouchEvent) {
  if (e.touches.length >= 2) {
    beginPinch(e.touches)
    return
  }

  const touch = e.touches[0]
  if (!touch) return
  beginTouchDrag(touch)
}

function onTouchMove(e: TouchEvent) {
  if (touchMode === 'pinch') {
    if (e.touches.length < 2 || !pinchStartDistance) return
    const distance = getTouchDistance(e.touches)
    if (!distance) return
    const ratio = distance / pinchStartDistance
    clampSphereScale(pinchStartScale * ratio)
    return
  }

  if (touchMode === 'drag') {
    if (e.touches.length >= 2) {
      beginPinch(e.touches)
      return
    }

    const touch = e.touches[0]
    if (!touch) return
    dragLatestX = touch.clientX
    dragLatestY = touch.clientY
    scheduleDragRotate()
  }
}

function onTouchEnd(e: TouchEvent) {
  if (e.touches.length >= 2) {
    beginPinch(e.touches)
    return
  }

  if (e.touches.length === 1) {
    const touch = e.touches[0]
    if (!touch) return
    beginTouchDrag(touch)
    return
  }

  endTouchInteraction()
}

function onTouchCancel() {
  endTouchInteraction()
}

function onWheel(e: WheelEvent) {
  const d = e.deltaY > 0 ? -0.06 : 0.06
  clampSphereScale(sphereScale.value + d)
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
  nextTick(() => {
    updateCloudStageSize()
  })
}

onMounted(async () => {
  if (import.meta.client) {
    viewportMediaQuery = window.matchMedia(`(max-width: ${VIEWPORT_MOBILE_BREAKPOINT}px)`)
    isMobileViewport.value = viewportMediaQuery.matches
    clampSphereScale(sphereScale.value)
    viewportMediaQuery.addEventListener('change', handleViewportChange)
    window.addEventListener('resize', handleWindowResize)
  }

  try {
    const res = await tagApi.cloud()
    tags.value = res.data
  } catch {
    tags.value = []
  } finally {
    loading.value = false
    await nextTick()
    updateCloudStageSize()
    requestAnimationFrame(() => {
      entered.value = true
      updateCloudStageSize()
    })
  }
  rafId = requestAnimationFrame(animateLoop)
})

onUnmounted(() => {
  cancelAnimationFrame(rafId)
  endTouchInteraction()

  if (viewportMediaQuery) {
    viewportMediaQuery.removeEventListener('change', handleViewportChange)
    viewportMediaQuery = null
  }

  if (import.meta.client) {
    window.removeEventListener('resize', handleWindowResize)
  }

  window.removeEventListener('mousemove', onDragMove)
  window.removeEventListener('mouseup', onDragEnd)
})
</script>

<style scoped lang="scss">
.tags-page-bg {
  min-height: calc(100vh - 120px);
  background: #f5f5f5;
  .dark & { background: $color-dark-bg; }
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

.page-title-wrap {
  position: relative;
}

.page-title {
  display: flex;
  align-items: flex-end;
  flex-wrap: wrap;
  gap: 0.22rem 0.62rem;
  font-size: var(--layout-page-title-size);
  font-weight: 700;
  line-height: 1.2;
  min-height: 2rem;
  margin: 0;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}

.page-title-main {
  display: inline-flex;
  align-items: center;
  gap: var(--layout-page-title-gap);
  min-height: 2rem;
}

.title-count-inline {
  display: inline-flex;
  align-items: flex-end;
  flex: 0 0 auto;
  margin: 0;
  font-size: 0.92rem;
  font-weight: 400;
  line-height: 1.4;
  color: $color-text-muted;
  white-space: nowrap;

  @media (max-width: $breakpoint-md) {
    white-space: normal;
  }

  .dark & {
    color: #94a3b8;
  }
}

.view-toggle {
  display: flex;
  gap: 4px;
  background: rgba(0, 0, 0, 0.04);
  border-radius: 10px;
  padding: 3px;

  @media (max-width: $breakpoint-md) {
    width: 100%;
  }

  .dark & { background: rgba(255, 255, 255, 0.06); }
}

.toggle-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
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

  @media (max-width: $breakpoint-md) {
    flex: 1;
    min-height: 36px;
    font-size: 0.86rem;
  }

  .dark & { color: $color-dark-text-muted; }
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

.cloud-stage {
  position: relative;
  width: 100%;
  aspect-ratio: 16 / 10;
  height: auto;
  min-height: 360px;
  max-height: calc(100dvh - 250px);
  margin: 0 auto;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 0;
  background: transparent;
  overflow: hidden;

  @media (max-width: $breakpoint-md) {
    width: 100%;
    aspect-ratio: auto;
    height: clamp(300px, calc(100vw - (var(--layout-page-padding-x) * 2)), 620px);
    min-height: 280px;
    max-height: calc(100dvh - 208px);
    border-radius: 0;
  }
}

.cloud-stage.enter-active {
  animation: tagsPanelEnter 0.36s ease-out both;
}

.cloud-wrapper {
  position: relative;
  width: 100%;
  height: 100%;
  perspective: 900px;
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
  touch-action: none;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  &:active { cursor: grabbing; }
}

.cloud-glow {
  position: absolute;
  width: min(62%, 620px);
  height: min(62%, 620px);
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
  width: 94%;
  height: 94%;
  margin: 0 auto;
  transform-style: preserve-3d;

  @media (max-width: $breakpoint-md) {
    width: 96%;
    height: 96%;
  }
}

.cloud-gesture-tip {
  display: none;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  margin: 0.52rem auto 0.08rem;
  color: $color-text-muted;
  font-size: 0.76rem;
  line-height: 1;

  .dark & {
    color: #94a3b8;
  }

  @media (max-width: $breakpoint-md) {
    display: inline-flex;
    position: static;
    transform: none;
    margin-top: 0.58rem;
    padding: 0.34rem 0.58rem;
    border-radius: 999px;
    background: rgba(241, 245, 249, 0.78);
    border: 1px solid rgba(148, 163, 184, 0.28);
    backdrop-filter: blur(4px);
    z-index: 3;
    white-space: nowrap;

    .dark & {
      background: rgba(15, 23, 42, 0.78);
      border-color: rgba(100, 116, 139, 0.42);
    }
  }
}

.cloud-scale-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 2.8rem;
  padding: 0.16rem 0.42rem;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.14);
  color: $color-primary;
  font-size: 0.7rem;
  font-weight: 700;
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

  @media (max-width: $breakpoint-md) {
    gap: 8px;
  }
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

  @media (max-width: $breakpoint-md) {
    padding: 6px 12px;
    font-size: 0.84rem;
  }

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
  .cloud-stage.enter-active,
  .empty-state.enter-active,
  .flat-tags.is-entering .flat-tag.enter-item {
    animation: none !important;
    opacity: 1 !important;
    transform: none !important;
  }
}
</style>
