<template>
  <div class="release-notes-page" :class="{ 'page-entered': pageEntered }">
    <section class="release-hero">
      <div class="release-hero__content">
        <p class="release-kicker">Release Notes</p>
        <h1>版本更新</h1>
        <p class="release-desc">集中记录用户端最近上线、优化和修复的功能，方便你了解当前系统状态。</p>
        <div class="version-rule" aria-label="版本号规则">
          <span>版本规则</span>
          <strong>主版本.次版本.修订号</strong>
          <em>新增功能升次版本，修复和优化升修订号。</em>
        </div>
      </div>
      <div class="version-card" aria-label="当前版本信息">
        <span class="version-card__label">当前版本</span>
        <strong>{{ appReleaseInfo.currentVersion }}</strong>
        <span>更新于 {{ appReleaseInfo.updatedAt }}</span>
        <div class="latest-highlights">
          <p>本次更新重点</p>
          <ul>
            <li v-for="item in latestHighlights" :key="item.title">{{ item.title }}</li>
          </ul>
        </div>
      </div>
    </section>

    <section class="release-summary" aria-label="版本概览">
      <article class="summary-card">
        <span class="summary-card__value">{{ appReleaseInfo.releaseNotes.length }}</span>
        <span class="summary-card__label">已记录版本</span>
      </article>
      <article class="summary-card">
        <span class="summary-card__value">{{ totalItemCount }}</span>
        <span class="summary-card__label">更新条目</span>
      </article>
      <article class="summary-card">
        <span class="summary-card__value">{{ latestRelease.releaseDate }}</span>
        <span class="summary-card__label">最近更新</span>
      </article>
    </section>

    <section class="release-browser" aria-label="更新记录筛选">
      <div class="browser-head">
        <div>
          <p class="release-kicker">Browse</p>
          <h2>按类型浏览</h2>
        </div>
        <p>当前显示 {{ filteredItemCount }} 条记录</p>
      </div>
      <div class="filter-tabs" role="list" aria-label="更新类型筛选">
        <button
          v-for="option in filterOptions"
          :key="option.type"
          type="button"
          class="filter-tab"
          :class="{ 'filter-tab--active': selectedType === option.type }"
          @click="selectedType = option.type"
        >
          <span>{{ option.label }}</span>
          <strong>{{ option.count }}</strong>
        </button>
      </div>
    </section>

    <section class="timeline" aria-label="版本更新记录">
      <article
        v-for="(release, releaseIndex) in filteredReleaseNotes"
        :key="release.version"
        class="release-card"
        :style="{ '--enter-index': releaseIndex }"
      >
        <header class="release-card__head">
          <div>
            <p class="release-date">{{ release.releaseDate }}</p>
            <h2>{{ release.title }}</h2>
          </div>
          <span class="release-version">{{ release.version }}</span>
        </header>
        <p class="release-summary-text">{{ release.summary }}</p>
        <ul class="release-items">
          <li v-for="item in release.items" :key="`${release.version}-${item.title}`" class="release-item">
            <span class="release-item__badge" :class="`release-item__badge--${item.type}`">
              {{ releaseNoteTypeLabels[item.type] }}
            </span>
            <div class="release-item__body">
              <h3>{{ item.title }}</h3>
              <p>{{ item.description }}</p>
            </div>
          </li>
        </ul>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
import type { ReleaseNoteType, ReleaseVersion } from '~/utils/release/appRelease'
import { appReleaseInfo, releaseNoteTypeLabels } from '~/utils/release/appRelease'

type ReleaseFilterType = 'all' | ReleaseNoteType

interface ReleaseFilterOption {
  type: ReleaseFilterType
  label: string
  count: number
}

const pageEntered = ref(false)
const selectedType = ref<ReleaseFilterType>('all')
const latestRelease = computed(() => appReleaseInfo.releaseNotes[0])
const latestHighlights = computed(() => latestRelease.value.items.slice(0, 3))
const totalItemCount = computed(() => appReleaseInfo.releaseNotes.reduce((sum, release) => sum + release.items.length, 0))
const filterTypes: ReleaseNoteType[] = ['feature', 'fix', 'security', 'performance']
const filterOptions = computed<ReleaseFilterOption[]>(() => {
  const options: ReleaseFilterOption[] = [
    { type: 'all', label: '全部', count: totalItemCount.value },
  ]

  filterTypes.forEach((type) => {
    const count = appReleaseInfo.releaseNotes.reduce((sum, release) => {
      return sum + release.items.filter(item => item.type === type).length
    }, 0)
    options.push({ type, label: releaseNoteTypeLabels[type], count })
  })

  return options
})
const filteredReleaseNotes = computed<ReleaseVersion[]>(() => {
  if (selectedType.value === 'all') {
    return appReleaseInfo.releaseNotes
  }

  return appReleaseInfo.releaseNotes
    .map((release) => ({
      ...release,
      items: release.items.filter(item => item.type === selectedType.value),
    }))
    .filter(release => release.items.length > 0)
})
const filteredItemCount = computed(() => filteredReleaseNotes.value.reduce((sum, release) => sum + release.items.length, 0))

useHead({
  title: '版本更新',
  meta: [
    { name: 'description', content: `查看 ${appReleaseInfo.productName} ${appReleaseInfo.currentVersion} 的功能更新、修复和优化记录。` },
  ],
})

onMounted(() => {
  if (!import.meta.client) {
    return
  }

  window.requestAnimationFrame(() => {
    pageEntered.value = true
  })
})
</script>

<style scoped lang="scss">
.release-notes-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x) calc(var(--layout-page-padding-y) + 1rem);
  opacity: 0;
  transform: translate3d(0, 10px, 0);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.release-notes-page.page-entered {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

.release-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: 1rem;
  align-items: stretch;
  margin-bottom: 1rem;
}

.release-hero__content,
.version-card,
.summary-card,
.release-card {
  border: 1px solid rgba(148, 163, 184, 0.24);
  background: #fff;

  .dark & {
    border-color: rgba(148, 163, 184, 0.14);
    background: #171b20;
  }
}

.release-hero__content {
  min-height: 190px;
  border-radius: 16px;
  padding: 1.55rem;
  background:
    radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.12), transparent 44%),
    radial-gradient(circle at 100% 100%, rgba(14, 165, 233, 0.12), transparent 52%),
    #fff;

  .dark & {
    background:
      radial-gradient(circle at 0% 0%, rgba(59, 130, 246, 0.16), transparent 44%),
      radial-gradient(circle at 100% 100%, rgba(56, 189, 248, 0.12), transparent 52%),
      #171b20;
  }
}

.release-kicker {
  margin: 0;
  font-size: 0.76rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;

  .dark & {
    color: #94a3b8;
  }
}

h1 {
  margin: 0.45rem 0 0;
  font-size: clamp(1.8rem, 3.4vw, 2.7rem);
  line-height: 1.16;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.release-desc {
  max-width: 46rem;
  margin: 0.8rem 0 0;
  font-size: 0.95rem;
  line-height: 1.75;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.version-rule {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.45rem;
  margin-top: 1rem;

  span,
  strong,
  em {
    display: inline-flex;
    align-items: center;
    border-radius: 999px;
    font-size: 0.78rem;
    line-height: 1.3;
  }

  span {
    padding: 0.28rem 0.58rem;
    background: rgba(15, 23, 42, 0.07);
    color: #475569;
  }

  strong {
    padding: 0.3rem 0.68rem;
    background: rgba(37, 99, 235, 0.1);
    color: #2563eb;
  }

  em {
    font-style: normal;
    color: #64748b;
  }

  .dark & {
    span {
      background: rgba(148, 163, 184, 0.16);
      color: #cbd5e1;
    }

    strong {
      background: rgba(96, 165, 250, 0.18);
      color: #93c5fd;
    }

    em {
      color: #94a3b8;
    }
  }
}

.version-card {
  border-radius: 16px;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 0.5rem;
  background:
    linear-gradient(135deg, rgba(37, 99, 235, 0.1), rgba(14, 165, 233, 0.06)),
    #fff;

  strong {
    font-size: clamp(1.55rem, 3vw, 2.2rem);
    line-height: 1;
    color: #2563eb;
  }

  span {
    font-size: 0.84rem;
    color: #64748b;
  }

  .dark & {
    background:
      linear-gradient(135deg, rgba(59, 130, 246, 0.16), rgba(56, 189, 248, 0.08)),
      #171b20;

    strong {
      color: #93c5fd;
    }

    span {
      color: #94a3b8;
    }
  }
}

.latest-highlights {
  margin-top: 0.3rem;
  padding-top: 0.75rem;
  border-top: 1px dashed rgba(148, 163, 184, 0.32);

  p {
    margin: 0 0 0.42rem;
    font-size: 0.78rem;
    color: #64748b;
  }

  ul {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 0.35rem;
  }

  li {
    position: relative;
    padding-left: 0.82rem;
    font-size: 0.82rem;
    line-height: 1.5;
    color: #475569;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0.58em;
      width: 0.34rem;
      height: 0.34rem;
      border-radius: 999px;
      background: #2563eb;
    }
  }

  .dark & {
    border-top-color: rgba(100, 116, 139, 0.34);

    p {
      color: #94a3b8;
    }

    li {
      color: #cbd5e1;

      &::before {
        background: #93c5fd;
      }
    }
  }
}

.version-card__label {
  width: fit-content;
  padding: 0.28rem 0.62rem;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);

  .dark & {
    background: rgba(96, 165, 250, 0.18);
  }
}

.release-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0.75rem;
  margin-bottom: 1rem;
}

.summary-card {
  border-radius: 12px;
  padding: 0.95rem 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}

.summary-card__value {
  font-size: 1.1rem;
  font-weight: 800;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.summary-card__label,
.release-date {
  font-size: 0.78rem;
  color: #64748b;

  .dark & {
    color: #94a3b8;
  }
}

.release-browser {
  margin-bottom: 1rem;
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 14px;
  padding: 1rem;
  background: #fff;

  .dark & {
    border-color: rgba(148, 163, 184, 0.14);
    background: #171b20;
  }
}

.browser-head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.75rem;

  h2 {
    margin: 0.28rem 0 0;
    font-size: 1.08rem;
    color: $color-text;

    .dark & {
      color: $color-dark-text;
    }
  }

  p {
    margin: 0;
    font-size: 0.82rem;
    color: #64748b;

    .dark & {
      color: #94a3b8;
    }
  }
}

.filter-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.filter-tab {
  min-height: 40px;
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  border: 1px solid rgba(148, 163, 184, 0.28);
  border-radius: 999px;
  padding: 0.35rem 0.72rem;
  background: #f8fafc;
  color: #475569;
  cursor: pointer;
  transition:
    border-color 0.2s ease,
    background-color 0.2s ease,
    color 0.2s ease;

  strong {
    min-width: 1.35rem;
    height: 1.35rem;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    border-radius: 999px;
    background: rgba(148, 163, 184, 0.18);
    font-size: 0.72rem;
  }

  &:hover,
  &--active {
    border-color: rgba(37, 99, 235, 0.38);
    background: rgba(37, 99, 235, 0.1);
    color: #2563eb;
  }

  &:focus-visible {
    outline: 2px solid rgba(37, 99, 235, 0.45);
    outline-offset: 2px;
  }

  .dark & {
    border-color: rgba(148, 163, 184, 0.16);
    background: #101215;
    color: #cbd5e1;

    strong {
      background: rgba(148, 163, 184, 0.16);
    }

    &:hover,
    &--active {
      border-color: rgba(96, 165, 250, 0.38);
      background: rgba(96, 165, 250, 0.16);
      color: #93c5fd;
    }
  }
}

.timeline {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.95rem;
}

.release-card {
  border-radius: 14px;
  padding: 1.1rem;
  opacity: 0;
  transform: translate3d(0, 12px, 0);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    opacity 560ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
  transition-delay: calc(40ms + min(var(--enter-index), 7) * 55ms);

  .page-entered & {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }

  &:hover {
    border-color: rgba(59, 130, 246, 0.34);
    box-shadow: 0 12px 26px rgba(15, 23, 42, 0.08);
  }

  .dark &:hover {
    border-color: rgba(96, 165, 250, 0.38);
    box-shadow: 0 12px 28px rgba(2, 6, 23, 0.32);
  }
}

.release-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;

  h2 {
    margin: 0.25rem 0 0;
    font-size: 1.12rem;
    color: $color-text;

    .dark & {
      color: $color-dark-text;
    }
  }
}

.release-date {
  margin: 0;
}

.release-version {
  flex: 0 0 auto;
  padding: 0.32rem 0.7rem;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #2563eb;
  font-size: 0.82rem;
  font-weight: 700;

  .dark & {
    background: rgba(96, 165, 250, 0.18);
    color: #93c5fd;
  }
}

.release-summary-text {
  margin: 0.72rem 0 0;
  font-size: 0.9rem;
  line-height: 1.68;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.release-items {
  list-style: none;
  margin: 0.9rem 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.62rem;
}

.release-item {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 0.7rem;
  padding: 0.72rem;
  border-radius: 10px;
  background: #f8fafc;

  .dark & {
    background: #101215;
  }
}

.release-item__badge {
  align-self: flex-start;
  min-width: 2.55rem;
  padding: 0.22rem 0.5rem;
  border-radius: 999px;
  text-align: center;
  font-size: 0.72rem;
  font-weight: 700;
}

.release-item__badge--feature {
  background: rgba(37, 99, 235, 0.12);
  color: #2563eb;

  .dark & {
    background: rgba(96, 165, 250, 0.18);
    color: #93c5fd;
  }
}

.release-item__badge--fix {
  background: rgba(14, 165, 233, 0.14);
  color: #0284c7;

  .dark & {
    background: rgba(56, 189, 248, 0.16);
    color: #67e8f9;
  }
}

.release-item__badge--security {
  background: rgba(22, 163, 74, 0.13);
  color: #15803d;

  .dark & {
    background: rgba(74, 222, 128, 0.16);
    color: #86efac;
  }
}

.release-item__badge--performance {
  background: rgba(124, 58, 237, 0.12);
  color: #7c3aed;

  .dark & {
    background: rgba(167, 139, 250, 0.16);
    color: #c4b5fd;
  }
}

.release-item__body {
  h3 {
    margin: 0;
    font-size: 0.92rem;
    color: $color-text;

    .dark & {
      color: $color-dark-text;
    }
  }

  p {
    margin: 0.28rem 0 0;
    font-size: 0.82rem;
    line-height: 1.58;
    color: #64748b;

    .dark & {
      color: #94a3b8;
    }
  }
}

@media (max-width: $breakpoint-md) {
  .release-hero,
  .release-summary {
    grid-template-columns: 1fr;
  }

  .browser-head {
    align-items: flex-start;
    flex-direction: column;
    gap: 0.45rem;
  }

  .release-hero__content,
  .version-card,
  .release-card,
  .release-browser {
    padding: 1rem;
  }

  .release-card__head {
    flex-direction: column;
  }

  .release-item {
    grid-template-columns: 1fr;
  }

  .release-item__badge {
    width: fit-content;
  }
}

@media (prefers-reduced-motion: reduce) {
  .release-notes-page,
  .release-notes-page.page-entered,
  .release-card,
  .page-entered .release-card {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
