<template>
  <div class="unified-skeleton" :class="`variant-${variant}`" aria-hidden="true">
    <div v-for="index in count" :key="`sk-${variant}-${index}`" class="skeleton-item">
      <template v-if="variant === 'article'">
        <div class="skeleton-block skeleton-cover" />
        <div class="skeleton-content">
          <div class="skeleton-line w-55" />
          <div class="skeleton-line w-88" />
          <div class="skeleton-line w-72" />
          <div class="skeleton-meta-row">
            <span class="skeleton-line w-24" />
            <span class="skeleton-line w-18" />
          </div>
        </div>
      </template>

      <template v-else-if="variant === 'topic'">
        <div class="skeleton-block skeleton-topic-cover" />
        <div class="skeleton-content skeleton-topic-content">
          <div class="skeleton-line w-64" />
          <div class="skeleton-line w-92" />
          <div class="skeleton-line w-80" />
          <div class="skeleton-meta-row">
            <span class="skeleton-line w-36" />
            <span class="skeleton-line w-24" />
          </div>
        </div>
      </template>

      <template v-else-if="variant === 'friend-link'">
        <div class="skeleton-avatar" />
        <div class="skeleton-content skeleton-link-content">
          <div class="skeleton-line w-46" />
          <div class="skeleton-line w-72" />
        </div>
      </template>

      <template v-else>
        <div class="skeleton-content skeleton-category-content">
          <div class="skeleton-line w-66" />
          <div class="skeleton-line w-90" />
          <div class="skeleton-line w-76" />
          <div class="skeleton-meta-row">
            <span class="skeleton-line w-20" />
            <span class="skeleton-line w-14" />
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  variant: 'article' | 'category' | 'topic' | 'friend-link'
  count?: number
}>(), {
  count: 6,
})
</script>

<style scoped lang="scss">
.unified-skeleton {
  --sk-base: rgba(148, 163, 184, 0.18);
  --sk-shine: rgba(255, 255, 255, 0.72);
  --sk-border: rgba(148, 163, 184, 0.24);
  display: grid;
  gap: 1rem;
}

.variant-article {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.variant-topic {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.variant-friend-link {
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
}

.variant-category {
  grid-template-columns: 1fr;
}

.skeleton-item {
  position: relative;
  display: flex;
  align-items: stretch;
  gap: 0.8rem;
  border: 1px solid var(--sk-border);
  border-radius: 12px;
  overflow: hidden;
  background: rgba(248, 250, 252, 0.92);
}

.variant-topic .skeleton-item {
  flex-direction: column;
}

.variant-category .skeleton-item {
  border: none;
  border-radius: 0;
  border-bottom: 1px solid var(--sk-border);
  background: transparent;
  padding: 0;
}

.skeleton-item::after {
  content: '';
  position: absolute;
  inset: 0;
  transform: translateX(-100%);
  background: linear-gradient(105deg, transparent 35%, var(--sk-shine) 50%, transparent 65%);
  animation: skeletonSweep 1.35s ease-in-out infinite;
}

.skeleton-block,
.skeleton-line,
.skeleton-avatar {
  background: var(--sk-base);
}

.skeleton-cover {
  width: 240px;
  flex-shrink: 0;
  aspect-ratio: 16 / 9;
}

.skeleton-topic-cover {
  width: 100%;
  aspect-ratio: 16 / 9;
}

.skeleton-avatar {
  width: 52px;
  height: 52px;
  border-radius: 12px;
  margin: 0.88rem 0 0.88rem 0.88rem;
  flex-shrink: 0;
}

.skeleton-content {
  flex: 1;
  min-width: 0;
  padding: 0.7rem 0.8rem;
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.skeleton-topic-content {
  padding: 0.95rem 1rem 1.05rem;
}

.skeleton-link-content {
  justify-content: center;
  padding: 0.88rem 0.88rem 0.88rem 0;
}

.skeleton-category-content {
  padding: 1rem 0;
}

.skeleton-line {
  height: 11px;
  border-radius: 999px;
}

.skeleton-meta-row {
  margin-top: auto;
  display: flex;
  gap: 0.5rem;
}

.w-14 { width: 14%; }
.w-18 { width: 18%; }
.w-20 { width: 20%; }
.w-24 { width: 24%; }
.w-36 { width: 36%; }
.w-46 { width: 46%; }
.w-55 { width: 55%; }
.w-64 { width: 64%; }
.w-66 { width: 66%; }
.w-72 { width: 72%; }
.w-80 { width: 80%; }
.w-88 { width: 88%; }
.w-90 { width: 90%; }
.w-92 { width: 92%; }

.dark .unified-skeleton {
  --sk-base: rgba(71, 85, 105, 0.32);
  --sk-shine: rgba(148, 163, 184, 0.2);
  --sk-border: rgba(71, 85, 105, 0.34);
}

.dark .skeleton-item {
  background: rgba(15, 23, 42, 0.72);
}

@keyframes skeletonSweep {
  0% {
    transform: translateX(-100%);
  }
  100% {
    transform: translateX(100%);
  }
}

@media (max-width: $breakpoint-md) {
  .variant-article,
  .variant-friend-link {
    grid-template-columns: 1fr;
  }

  .variant-topic {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .skeleton-cover {
    width: 180px;
  }
}

@media (max-width: 480px) {
  .variant-topic {
    grid-template-columns: 1fr;
  }

  .variant-article .skeleton-item {
    flex-direction: column;
  }

  .variant-article .skeleton-cover {
    width: 100%;
  }

  .variant-article .skeleton-content {
    padding: 0.8rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .skeleton-item::after {
    animation: none;
  }
}
</style>
