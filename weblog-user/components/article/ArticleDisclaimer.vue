<template>
  <div class="article-disclaimer">
    <div class="disclaimer-header">
      <Icon name="heroicons:information-circle-20-solid" size="18" />
      <span>免责声明</span>
    </div>
    <div class="disclaimer-content">
      <p v-for="(line, index) in disclaimerLines" :key="`${index}-${line}`">{{ line }}</p>
    </div>
  </div>
</template>

<script setup lang="ts">
const siteConfig = useSiteConfigState()

const disclaimerLines = computed(() => {
  const text = siteConfig.value.siteDisclaimerContent || DEFAULT_SITE_DISCLAIMER_CONTENT
  return text
    .split(/\r?\n/)
    .map(line => line.trim())
    .filter(Boolean)
})
</script>

<style scoped lang="scss">
.article-disclaimer {
  margin: 2rem 0 1rem;
  border: 1px solid #e0e7ff;
  border-radius: 8px;
  background: #f5f7ff;
  overflow: hidden;
  .dark & { background: $color-dark-bg-secondary; border-color: $color-dark-border; }
}
.disclaimer-header {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.75rem 1rem;
  background: #e0e7ff;
  color: #4f46e5;
  font-weight: 600;
  font-size: 0.95rem;
  .dark & { background: rgba(59, 130, 246, 0.2); color: #bfdbfe; }
}
.disclaimer-content {
  padding: 1rem;
  color: #4b5563;
  font-size: 0.875rem;
  .dark & { color: $color-dark-text-muted; }
  p {
    margin-bottom: 0.5rem;
    line-height: 1.6;
    &:last-child { margin-bottom: 0; }
  }
}
</style>
