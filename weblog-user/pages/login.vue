<template>
  <div class="login-redirect">
    <p>正在打开登录...</p>
  </div>
</template>

<script setup lang="ts">
import { useLoginModal } from '~/composables/useLoginModal'

useHead({ title: '登录' })

const loginModal = useLoginModal()
const router = useRouter()

onMounted(() => {
  // 直接通过 URL 访问 /login 时，打开登录弹窗并回到首页
  loginModal.open()
  router.replace('/')
})
</script>

<style scoped>
.login-redirect {
  min-height: 60vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  font-size: 0.9rem;
}
</style>
