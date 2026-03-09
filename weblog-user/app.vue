<template>
  <NuxtLayout>
    <NuxtPage />
  </NuxtLayout>
  <MessageContainer />
  <ConfirmDialog />
  <LoginModal />
  <BackToTop />
</template>

<script setup lang="ts">
import MessageContainer from '~/components/ui/message/MessageContainer.vue'
import ConfirmDialog from '~/components/ui/confirm/ConfirmDialog.vue'
import LoginModal from '~/components/LoginModal.vue'
import BackToTop from '~/components/BackToTop.vue'

// 全局图片懒加载淡入效果
useLazyImages()
</script>
