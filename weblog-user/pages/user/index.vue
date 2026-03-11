<template>
  <div class="user-center">
    <div v-if="loading" class="loading-state">
      <Icon name="heroicons:arrow-path-20-solid" size="24" class="spin" />
      <span>加载中...</span>
    </div>

    <template v-else-if="profile">
      <section class="profile-card">
        <div class="profile-avatar">
          <img v-if="profile.avatar" :src="profile.avatar" :alt="profile.nickname" class="avatar-img" />
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
          <span class="quick-label">我的收藏</span>
        </NuxtLink>
        <NuxtLink to="/user/comments" class="quick-card">
          <Icon name="heroicons:chat-bubble-left-ellipsis-20-solid" size="24" class="quick-icon comment-icon" />
          <span class="quick-label">我的评论</span>
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
import { userApi, type UserProfileVO } from '~/api/user'
import { useUserStore } from '~/stores/user'
import { useLoginModal } from '~/composables/useLoginModal'

useHead({ title: '个人中心' })

const userStore = useUserStore()
const profile = ref<UserProfileVO | null>(null)
const loading = ref(true)

function openLogin() { useLoginModal().open() }

function formatDate(dateStr: string) {
  const d = new Date(dateStr)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(async () => {
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
</script>

<style scoped lang="scss">
.user-center { max-width: var(--layout-max-width); margin: 0 auto; padding: var(--layout-page-padding-y) var(--layout-page-padding-x); }
.profile-card { display: flex; flex-direction: column; align-items: center; text-align: center; padding: 2rem; border: 1px solid $color-border; border-radius: $radius-lg; background: $color-bg; position: relative; }
.profile-avatar { margin-bottom: 1rem; }
.avatar-img { width: 80px; height: 80px; border-radius: 50%; object-fit: cover; border: 3px solid $color-primary; }
.avatar-placeholder { display: flex; align-items: center; justify-content: center; width: 80px; height: 80px; border-radius: 50%; background: $color-primary; color: #fff; font-size: 2rem; font-weight: 700; }
.profile-name { font-size: 1.35rem; font-weight: 700; margin: 0; }
.profile-email { font-size: .85rem; color: $color-text-muted; margin-top: .25rem; }
.profile-bio { font-size: .9rem; margin-top: .75rem; line-height: 1.5; }
.profile-bio.muted { color: $color-text-muted; font-style: italic; }
.profile-joined { display: flex; align-items: center; gap: .3rem; font-size: .8rem; color: $color-text-muted; margin-top: .75rem; }
.edit-btn { display: inline-flex; align-items: center; gap: .375rem; margin-top: 1.25rem; padding: .5rem 1.25rem; border-radius: $radius-md; background: $color-primary; color: #fff; text-decoration: none; min-height: 44px; }
.quick-links { display: grid; grid-template-columns: repeat(2, 1fr); gap: 1rem; margin-top: 1.5rem; }
.quick-card { display: flex; flex-direction: column; align-items: center; gap: .5rem; padding: 1.5rem 1rem; border: 1px solid $color-border; border-radius: $radius-lg; text-decoration: none; color: $color-text; }
.quick-icon { opacity: .85; }
.fav-icon { color: #f59e0b; }
.comment-icon { color: $color-primary; }
.quick-label { font-size: .9rem; font-weight: 500; }
.loading-state { display: flex; align-items: center; justify-content: center; gap: .5rem; padding: 4rem; color: $color-text-muted; }
.empty-state { text-align: center; padding: 4rem; color: #94a3b8; }
.login-link { display: inline-block; margin-top: 1rem; padding: .5rem 1.5rem; background: $color-primary; color: #fff; border-radius: $radius-md; border: none; cursor: pointer; }
.spin { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: $breakpoint-sm) { .quick-links { grid-template-columns: 1fr; } }
@media (max-width: $breakpoint-md) { .user-center { padding: var(--layout-page-padding-y) var(--layout-page-padding-x); } }
</style>
