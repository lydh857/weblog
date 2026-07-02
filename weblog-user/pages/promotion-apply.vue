<template>
  <div class="promotion-apply-compat">
    <p>正在打开广告申请弹窗...</p>
  </div>
</template>

<script setup lang="ts">
const route = useRoute()
const promotionApplicationModal = usePromotionApplicationModal()

definePageMeta({
  path: '/ad-apply',
})

useHead({
  title: '广告投放申请',
})

onMounted(async () => {
  const position = typeof route.query.position === 'string' ? route.query.position : undefined
  promotionApplicationModal.open(position)
  await navigateTo('/', { replace: true })
})
</script>

<style scoped lang="scss">
.promotion-apply-compat {
  min-height: 30vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-text-muted;
  font-size: 0.95rem;
}
</style>
