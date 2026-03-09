<template>
  <div class="friend-links-page">
    <div class="page-header">
      <div class="header-text">
        <h1 class="page-title">友情链接</h1>
        <p class="page-desc">感谢以下站点的友情支持</p>
      </div>
      <button class="apply-btn" :disabled="statusLoading" @click="handleApplyClick">
        <Icon name="heroicons:link-20-solid" size="18" />
        <span>{{ applyBtnText }}</span>
      </button>
    </div>

    <div v-if="loading" class="loading-state">
      <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
      <span>加载中...</span>
    </div>

    <template v-else-if="links.length">
      <div class="links-grid">
        <a v-for="link in pagedLinks" :key="link.id" :href="link.url" target="_blank" rel="noopener noreferrer" class="link-card">
          <img v-if="link.logo && !logoErrors[link.id]" :src="link.logo" :alt="link.name" class="link-logo" loading="lazy" @error="onLogoError(link.id)" />
          <div v-else class="link-logo-placeholder">{{ link.name.charAt(0) }}</div>
          <div class="link-info">
            <span class="link-name">{{ link.name }}</span>
            <span v-if="link.description" class="link-desc">{{ link.description }}</span>
          </div>
          <Icon name="heroicons:arrow-top-right-on-square-16-solid" size="14" class="link-external" />
        </a>
      </div>

      <Pagination :total="links.length" :current-page="currentPage" :page-size="pageSize" @update:current-page="currentPage = $event" />
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
function onLogoError(id: number) { logoErrors[id] = true }

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
.friend-links-page { max-width: 960px; margin: 0 auto; padding: 2rem 1.5rem; }
.page-header { display: flex; align-items: center; justify-content: space-between; margin-bottom: 2rem; }
.page-title { margin: 0 0 .25rem; font-size: 1.75rem; font-weight: 700; }
.page-desc { margin: 0; color: $color-text-muted; font-size: .9rem; }
.apply-btn {
  display: inline-flex; align-items: center; gap: 6px; padding: .6rem 1.25rem; border-radius: 8px;
  background: $color-primary; color: #fff; font-weight: 500; font-size: .9rem; border: none; cursor: pointer;
  transition: all .2s; box-shadow: 0 2px 8px rgba(59,130,246,.2);
}
.apply-btn:hover { background: $color-primary-dark; transform: translateY(-1px); box-shadow: 0 4px 12px rgba(59,130,246,.3); }
.apply-btn:disabled { opacity: .6; cursor: not-allowed; transform: none; }

.links-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.link-card {
  display: flex; align-items: center; gap: 12px; padding: 16px; border: 1px solid $color-border; border-radius: 10px;
  text-decoration: none; color: inherit; transition: all .2s; cursor: pointer; position: relative;
}
.link-card:hover { border-color: $color-primary; box-shadow: 0 4px 16px rgba(59,130,246,.1); transform: translateY(-2px); }
.link-card:hover .link-external { opacity: 1; }
.link-logo { width: 48px; height: 48px; border-radius: 10px; object-fit: cover; flex-shrink: 0; }
.link-logo-placeholder {
  width: 48px; height: 48px; border-radius: 10px; background: linear-gradient(135deg, $color-primary, $color-primary-dark);
  color: #fff; display: flex; align-items: center; justify-content: center; font-size: 1.25rem; font-weight: 700; flex-shrink: 0;
}
.link-info { display: flex; flex-direction: column; min-width: 0; flex: 1; }
.link-name { font-weight: 600; font-size: .95rem; }
.link-desc { font-size: .8rem; color: $color-text-muted; margin-top: 2px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.link-external { position: absolute; top: 12px; right: 12px; color: $color-text-muted; opacity: 0; transition: opacity .2s; }

.loading-state { display: flex; align-items: center; justify-content: center; gap: .5rem; padding: 4rem; color: $color-text-muted; }
.empty-state { text-align: center; padding: 4rem; color: $color-text-muted; }
.empty-state p { margin-top: 1rem; }

.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

@media (max-width: $breakpoint-md) {
  .friend-links-page { padding: 1.5rem 1rem; }
  .page-header { flex-direction: column; align-items: flex-start; gap: 1rem; }
  .apply-btn { width: 100%; justify-content: center; }
}
</style>
