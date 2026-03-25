<template>
  <div class="friend-links-page">
    <div class="page-header">
      <div class="page-title-row">
        <h1 class="page-title">
          <Icon name="heroicons:link-20-solid" size="22" />
          友情链接
        </h1>
        <p class="page-desc">感谢以下站点的友情支持</p>
      </div>
      <button class="apply-btn" :disabled="statusLoading" @click="handleApplyClick">
        <Icon name="heroicons:link-20-solid" size="18" />
        <span>{{ applyBtnText }}</span>
      </button>
    </div>

    <UnifiedSkeleton v-if="loading" variant="friend-link" :count="pageSize" />

    <template v-else-if="links.length">
      <div class="links-grid">
        <a
          v-for="link in pagedLinks"
          :key="link.id"
          :href="getSafeHref(link.url) || '#'"
          target="_blank"
          rel="noopener noreferrer"
          class="link-card"
          @click="handleLinkClick($event, link.url)"
        >
          <img
            v-if="link.logo && !logoErrors[link.id]"
            :src="link.logo"
            :alt="link.name"
            class="link-logo"
            :class="{ 'link-logo--loaded': loadedLogoIds.has(link.id) }"
            loading="lazy"
            @load="onLogoLoad(link.id)"
            @error="onLogoError(link.id)"
          />
          <div v-else class="link-logo-placeholder">{{ link.name.charAt(0) }}</div>
          <div class="link-info">
            <span class="link-name">{{ link.name }}</span>
            <span v-if="link.description" class="link-desc">{{ link.description }}</span>
          </div>
          <Icon name="heroicons:arrow-top-right-on-square-16-solid" size="14" class="link-external" />
        </a>
      </div>

      <Pagination :total="links.length" :current-page="currentPage" :page-size="pageSize" @update:current-page="handlePageChange" />
    </template>

    <div v-else class="empty-state">
      <Icon name="heroicons:link-slash-20-solid" size="48" />
      <p>暂无友链</p>
    </div>

    <LinkApplyModal v-model:visible="showModal" @success="refreshData" />
  </div>
</template>

<script setup lang="ts">
import { friendLinkApi, type FriendLinkVO } from '~/api/friendLink'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'
import { normalizeSafeHref } from '~/utils/urlSafety'
import { scrollToTopOnMobilePagination } from '~/utils/paginationScroll'

useHead({
  title: '友情链接',
  meta: [{ name: 'description', content: '友情链接' }],
})

const userStore = useUserStore()
const loginModal = useLoginModal()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const loading = ref(true)
const links = ref<FriendLinkVO[]>([])
const showModal = ref(false)
const statusLoading = ref(true)
const myLink = ref<FriendLinkVO | null>(null)

const currentPage = ref(1)
const pageSize = 12

const pagedLinks = computed(() => {
  const start = (currentPage.value - 1) * pageSize
  return links.value.slice(start, start + pageSize)
})

const logoErrors = reactive<Record<number, boolean>>({})
const loadedLogoIds = reactive(new Set<number>())
function onLogoLoad(id: number) { loadedLogoIds.add(id) }
function onLogoError(id: number) { logoErrors[id] = true }

function getSafeHref(rawUrl: string | null | undefined) {
  return normalizeSafeHref(rawUrl)
}

function handleLinkClick(event: MouseEvent, rawUrl: string | null | undefined) {
  if (!getSafeHref(rawUrl)) {
    event.preventDefault()
  }
}

const applyBtnText = computed(() => {
  if (statusLoading.value) return '检查中...'
  if (!isLoggedIn.value || !myLink.value) return '申请友链'
  switch (myLink.value.status) {
    case 'pending': return '查看申请状态'
    case 'active': return '修改友链信息'
    case 'rejected': return '修改申请'
    default: return '申请友链'
  }
})

async function fetchLinks() {
  try {
    const res = await friendLinkApi.listActive()
    links.value = res.data
  } catch {}
  finally { loading.value = false }
}

async function fetchMyStatus() {
  if (!isLoggedIn.value) { statusLoading.value = false; return }
  statusLoading.value = true
  try {
    const res = await friendLinkApi.getMyLink()
    myLink.value = res.data
  } catch {
    myLink.value = null
  } finally {
    statusLoading.value = false
  }
}

function handleApplyClick() {
  if (!isLoggedIn.value) { loginModal.open(); return }
  showModal.value = true
}

function handlePageChange(page: number) {
  currentPage.value = page
  scrollToTopOnMobilePagination()
}

function refreshData() {
  fetchLinks()
  fetchMyStatus()
}

onMounted(() => {
  fetchLinks()
  fetchMyStatus()
})
</script>
<style scoped lang="scss">
.friend-links-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x);
}

.page-header {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.72rem;
  margin-bottom: var(--layout-page-header-margin-bottom);
}

.page-title-row {
  display: flex;
  align-items: flex-end;
  gap: 0.62rem;
  min-width: 0;
}

.page-title {
  display: flex;
  align-items: center;
  gap: var(--layout-page-title-gap);
  margin: 0;
  font-size: var(--layout-page-title-size);
  font-weight: 700;
  line-height: 1.2;
  min-height: 2rem;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}

.page-desc {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.4;
  color: $color-text-muted;
  white-space: nowrap;
  .dark & { color: $color-dark-text-muted; }
}

.apply-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.42rem;
  padding: 0.62rem 1.2rem;
  border: 1px solid transparent;
  border-radius: 999px;
  background: linear-gradient(135deg, #2563eb, #1d4ed8);
  color: #fff;
  font-weight: 600;
  font-size: 0.9rem;
  cursor: pointer;
  transition: transform 0.2s, box-shadow 0.2s, opacity 0.2s;
  box-shadow: 0 10px 22px rgba(37, 99, 235, 0.28);
}

.apply-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 14px 24px rgba(29, 78, 216, 0.32);
}

.apply-btn:disabled {
  opacity: 0.62;
  cursor: not-allowed;
  transform: none;
  box-shadow: none;
}

.links-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 0.9rem;
}

.link-card {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.72rem;
  padding: 0.88rem;
  border: 1px solid $color-border;
  border-radius: 12px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.8), rgba(248, 250, 252, 0.92));
  text-decoration: none;
  color: inherit;
  transition: border-color 0.2s, box-shadow 0.2s, transform 0.2s;
  .dark & {
    border-color: $color-dark-border;
    background: linear-gradient(180deg, rgba(15, 23, 42, 0.68), rgba(30, 41, 59, 0.82));
  }
}

.link-card:hover {
  border-color: rgba(59, 130, 246, 0.5);
  box-shadow: 0 10px 22px rgba(59, 130, 246, 0.14);
  transform: translateY(-2px);
}

.link-card:hover .link-external {
  opacity: 1;
  transform: translateY(0);
}

.link-logo {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  object-fit: cover;
  flex-shrink: 0;
  opacity: 0;
  transform: scale(1.03);
  filter: blur(2px);
  transition: opacity 0.28s ease, transform 0.35s ease, filter 0.28s ease;
}

.link-logo--loaded {
  opacity: 1;
  transform: scale(1);
  filter: blur(0);
}

.link-card:hover .link-logo--loaded {
  transform: scale(1.05);
}

.link-logo-placeholder {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 1.25rem;
  font-weight: 700;
  flex-shrink: 0;
  opacity: 0;
  transform: scale(1.03);
  filter: blur(2px);
  animation: link-logo-fade-in 0.28s ease forwards;
  transition: transform 0.35s ease;
}

.link-card:hover .link-logo-placeholder {
  transform: scale(1.05);
}

@keyframes link-logo-fade-in {
  to {
    opacity: 1;
    transform: scale(1);
    filter: blur(0);
  }
}

.link-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.link-name {
  font-weight: 700;
  font-size: 0.95rem;
  color: $color-text;
  .dark & { color: $color-dark-text; }
}

.link-desc {
  margin-top: 0.18rem;
  font-size: 0.8rem;
  line-height: 1.35;
  color: $color-text-muted;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.link-external {
  position: absolute;
  top: 0.7rem;
  right: 0.72rem;
  color: $color-text-muted;
  opacity: 0;
  transform: translateY(2px);
  transition: opacity 0.2s, transform 0.2s;
}

.empty-state {
  min-height: 300px;
  border: 1px dashed rgba(148, 163, 184, 0.48);
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.5rem;
  color: $color-text-muted;
}

.empty-state p {
  margin: 0;
  font-size: 0.95rem;
}

:deep(.pagination) {
  margin-top: 1.1rem;
}

@media (max-width: $breakpoint-md) {
  .page-title-row {
    flex-wrap: wrap;
    align-items: baseline;
    gap: 0.22rem 0.5rem;
  }

  .page-desc {
    white-space: normal;
  }

  .apply-btn {
    width: 100%;
    justify-content: center;
  }

  .links-grid {
    grid-template-columns: 1fr;
  }
}
</style>
