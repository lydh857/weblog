<template>
  <div class="user-center" :class="{ 'page-entered': pageEntered, 'content-entered': contentEntered }">
    <header class="page-header">
      <h1 class="page-title">
        <Icon name="heroicons:user-circle-20-solid" size="22" />
        个人中心
      </h1>
      <p class="page-desc">管理你的资料、收藏和评论记录</p>
    </header>

    <UnifiedPageLoader v-if="loading" plain :text="''" />

    <template v-else-if="profile">
      <section class="profile-card">
        <div class="profile-avatar">
          <img v-if="showProfileAvatar" :src="profile.avatar!" alt="用户头像" class="avatar-img" @error="handleProfileAvatarError" >
          <span v-else class="avatar-placeholder">{{ (profile.nickname || 'U').charAt(0) }}</span>
        </div>
        <div class="profile-info">
          <h1 class="profile-name">{{ profile.nickname || '未设置昵称' }}</h1>
          <p class="profile-email">{{ profile.email || '未绑定邮箱' }}</p>
          <p v-if="profile.bio" class="profile-bio">{{ profile.bio }}</p>
          <p v-else class="profile-bio muted">暂无简介</p>
          <p class="profile-joined">
            <Icon name="heroicons:calendar-16-solid" size="14" />
            {{ formatDate(profile.createTime) }} 加入
          </p>
        </div>
        <NuxtLink to="/user/edit" class="edit-btn">
          <Icon name="heroicons:pencil-square-16-solid" size="16" />
          编辑资料
        </NuxtLink>
      </section>

      <section class="quick-links">
        <NuxtLink to="/user/likes" class="quick-card">
          <Icon name="heroicons:bookmark-20-solid" size="24" class="quick-icon fav-icon" />
          <div>
            <span class="quick-label">我的收藏</span>
            <p class="quick-desc">查看并管理收藏文章</p>
          </div>
          <Icon name="heroicons:chevron-right-16-solid" size="16" class="quick-arrow" />
        </NuxtLink>
        <NuxtLink to="/user/comments" class="quick-card">
          <Icon name="heroicons:chat-bubble-left-ellipsis-20-solid" size="24" class="quick-icon comment-icon" />
          <div>
            <span class="quick-label">我的评论</span>
            <p class="quick-desc">查看并管理评论记录</p>
          </div>
          <Icon name="heroicons:chevron-right-16-solid" size="16" class="quick-arrow" />
        </NuxtLink>
      </section>
    </template>

    <div v-else class="empty-state">
      <p>请先登录</p>
      <button type="button" class="login-link" @click="openLogin">去登录</button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { userApi, type UserProfileVO } from '~/api/auth/user'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/modal/useLoginModal'

useHead({ title: '个人中心' })

const userStore = useUserStore()
const profile = ref<UserProfileVO | null>(null)
const loading = ref(true)
const profileAvatarLoadFailed = ref(false)
const pageEntered = ref(false)
const contentEntered = ref(false)

const showProfileAvatar = computed(() => !!profile.value?.avatar && !profileAvatarLoadFailed.value)

function handleProfileAvatarError() {
  profileAvatarLoadFailed.value = true
}

function openLogin() { useLoginModal().open() }

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
  if (import.meta.client) {
    void prefetchComponents('/user/edit')
    window.requestAnimationFrame(() => {
      pageEntered.value = true
    })
  }

  if (!userStore.isLoggedIn) {
    loading.value = false
    return
  }
  try {
    const res = await userApi.getProfile()
    profile.value = res.data
  } catch {
  } finally {
    loading.value = false
  }
})

watch(loading, (isLoading) => {
  if (isLoading) {
    contentEntered.value = false
    return
  }

  if (!import.meta.client) {
    contentEntered.value = true
    return
  }

  window.requestAnimationFrame(() => {
    contentEntered.value = true
  })
}, { immediate: true })

watch(() => profile.value?.avatar, () => {
  profileAvatarLoadFailed.value = false
})
</script>

<style scoped lang="scss">
.user-center { max-width: var(--layout-max-width); margin: 0 auto; padding: var(--layout-page-padding-y) var(--layout-page-padding-x); }
.user-center {
  opacity: 0;
  transform: translate3d(0, 10px, 0);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.user-center.page-entered {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}
.page-header { margin-bottom: var(--layout-page-header-margin-bottom); }
.page-title {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--layout-page-title-gap);
  font-size: var(--layout-page-title-size);
  line-height: 1.2;
  color: $color-text;

  .dark & { color: $color-dark-text; }
}
.page-desc {
  margin-top: var(--layout-page-desc-margin-top);
  color: $color-text-muted;
  font-size: .92rem;
  .dark & { color: $color-dark-text-muted; }
}

.profile-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: 1rem;
  padding: 1.1rem;
  border: 1px solid $color-border;
  border-radius: 14px;
  background: $color-bg;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
  }
}

.profile-avatar {
  width: 84px;
  height: 84px;
  border-radius: 50%;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eef2ff;

  .dark & { background: $color-dark-bg-elevated; }
}

.avatar-img { width: 84px; height: 84px; border-radius: 50%; object-fit: cover; }
.avatar-placeholder { display: flex; align-items: center; justify-content: center; width: 84px; height: 84px; border-radius: 50%; background: $color-primary; color: #fff; font-size: 2rem; font-weight: 700; }
.profile-name { font-size: 1.3rem; font-weight: 700; margin: 0; color: $color-text; .dark & { color: $color-dark-text; } }
.profile-email { font-size: .86rem; color: $color-text-muted; margin-top: .2rem; .dark & { color: $color-dark-text-muted; } }
.profile-bio { font-size: .9rem; margin-top: .65rem; line-height: 1.5; color: $color-text; .dark & { color: $color-dark-text; } }
.profile-bio.muted { color: $color-text-muted; font-style: italic; .dark & { color: $color-dark-text-muted; } }
.profile-joined { display: inline-flex; align-items: center; gap: .3rem; font-size: .8rem; color: $color-text-muted; margin-top: .7rem; .dark & { color: $color-dark-text-muted; } }
.edit-btn { display: inline-flex; align-items: center; gap: .375rem; padding: .5rem 1.1rem; border-radius: 10px; background: $color-primary; color: #fff; text-decoration: none; min-height: 40px; }

.quick-links { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem; margin-top: 1rem; }
.profile-card,
.quick-card {
  opacity: 0;
  transform: translate3d(0, 14px, 0);
}

.user-center.content-entered .profile-card,
.user-center.content-entered .quick-card {
  opacity: 1;
  transform: translate3d(0, 0, 0);
  transition:
    opacity 560ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
}

.user-center.content-entered .profile-card {
  transition-delay: 40ms;
}

.user-center.content-entered .quick-card:nth-child(1) {
  transition-delay: 90ms;
}

.user-center.content-entered .quick-card:nth-child(2) {
  transition-delay: 140ms;
}
.quick-card {
  display: grid;
  grid-template-columns: auto 1fr auto;
  align-items: center;
  gap: .7rem;
  padding: 1rem;
  border: 1px solid $color-border;
  border-radius: 12px;
  text-decoration: none;
  color: $color-text;
  transition: border-color .2s, transform .2s;

  .dark & {
    background: $color-dark-bg-secondary;
    border-color: $color-dark-border;
    color: $color-dark-text;
  }
}
.quick-card:hover {
  border-color: $color-primary;
  transform: translateY(-1px);

  .dark & {
    border-color: rgba(148, 163, 184, 0.46);
    box-shadow: 0 8px 20px rgba(2, 6, 23, 0.35);
  }
}
.quick-icon { opacity: .9; }
.fav-icon { color: #f59e0b; }
.comment-icon { color: $color-primary; }
.quick-label { display: block; font-size: .95rem; font-weight: 600; }
.quick-desc { margin-top: .1rem; font-size: .8rem; color: $color-text-muted; .dark & { color: $color-dark-text-muted; } }
.quick-arrow { color: $color-text-muted; .dark & { color: $color-dark-text-muted; } }

.empty-state { text-align: center; padding: 4rem; color: #94a3b8; .dark & { color: $color-dark-text-muted; } }
.login-link { display: inline-block; margin-top: 1rem; padding: .5rem 1.5rem; background: $color-primary; color: #fff; border-radius: $radius-md; border: none; cursor: pointer; }

@media (max-width: $breakpoint-md) {
  .profile-card { grid-template-columns: 1fr; text-align: center; }
  .profile-avatar, .edit-btn { justify-self: center; }
  .quick-links { grid-template-columns: 1fr; }
}

@media (prefers-reduced-motion: reduce) {
  .user-center,
  .profile-card,
  .quick-card {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
