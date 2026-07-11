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
          <div class="skeleton-topic-title-group">
            <div class="skeleton-line skeleton-topic-title w-64" />
            <div class="skeleton-line skeleton-topic-title w-52" />
          </div>
          <div class="skeleton-topic-summary-group">
            <div class="skeleton-line skeleton-topic-summary w-92" />
            <div class="skeleton-line skeleton-topic-summary w-84" />
          </div>
          <div class="skeleton-meta-row skeleton-topic-footer">
            <span class="skeleton-line skeleton-topic-count w-36" />
            <span class="skeleton-line skeleton-topic-time w-24" />
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
  --sk-shimmer-overlay: linear-gradient(
    90deg,
    rgba(148, 163, 184, 0.16) 0%,
    rgba(148, 163, 184, 0.3) 50%,
    rgba(148, 163, 184, 0.16) 100%
  );
  --sk-border: rgba(148, 163, 184, 0.24);
  display: grid;
  gap: 1rem;
}

.variant-article {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  --sk-article-cover-width: 240px;
  --sk-article-content-padding-y: 0.5rem;
  --sk-article-content-padding-x: 0.75rem;
}

.variant-topic {
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 1.25rem;
}

.variant-friend-link {
  grid-template-columns: repeat(auto-fill, minmax(270px, 1fr));
  gap: 0.9rem;
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
  gap: 0;
  border: none;
  border-radius: 10px;
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.06);
  height: 334px;
  min-height: 334px;
}

.variant-article .skeleton-item {
  height: calc(var(--sk-article-cover-width) * 9 / 16);
}

.variant-friend-link .skeleton-item {
  align-items: center;
  gap: 0.72rem;
  padding: 0.88rem;
}

.variant-category .skeleton-item {
  border: none;
  border-radius: 0;
  border-bottom: 1px solid var(--sk-border);
  background: transparent;
  padding: 0;
}

.skeleton-item::after {
  content: none;
}

.skeleton-block,
.skeleton-line,
.skeleton-avatar {
  background: rgba(148, 163, 184, 0.18);
}

.variant-article .skeleton-cover,
.variant-friend-link .skeleton-avatar {
  position: relative;
  overflow: hidden;
}

.variant-topic .skeleton-topic-cover,
.variant-topic .skeleton-line {
  position: relative;
  overflow: hidden;
}

.variant-article .skeleton-cover::after,
.variant-friend-link .skeleton-avatar::after,
.variant-topic .skeleton-topic-cover::after,
.variant-topic .skeleton-line::after {
  content: '';
  position: absolute;
  inset: 0;
  background: var(--sk-shimmer-overlay);
  transform: translate3d(-140%, 0, 0);
  animation: sk-shimmer-move 1.4s linear infinite;
  will-change: transform;
  pointer-events: none;
}

.variant-article .skeleton-line,
.variant-friend-link .skeleton-line {
  background: rgba(148, 163, 184, 0.2);
}

.variant-article .skeleton-meta-row .skeleton-line,
.variant-friend-link .skeleton-meta-row .skeleton-line {
  background: rgba(148, 163, 184, 0.18);
}

.skeleton-cover {
  width: var(--sk-article-cover-width, 240px);
  height: 100%;
  flex-shrink: 0;
}

.skeleton-topic-cover {
  width: 100%;
  height: 180px;
}

.skeleton-avatar {
  width: 52px;
  height: 52px;
  border-radius: 12px;
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

.variant-article .skeleton-content {
  padding: var(--sk-article-content-padding-y) var(--sk-article-content-padding-x);
}

.skeleton-topic-content {
  padding: 1rem 1.25rem 1.25rem;
  gap: 0;
  height: 154px;
  min-height: 154px;
}

.skeleton-topic-title-group {
  display: flex;
  flex-direction: column;
  gap: 0.42rem;
  margin-bottom: 0.5rem;
}

.skeleton-topic-summary-group {
  display: flex;
  flex-direction: column;
  gap: 0.38rem;
}

.skeleton-topic-title {
  height: 20px;
  border-radius: 6px;
}

.skeleton-topic-summary {
  height: 14px;
  border-radius: 999px;
}

.skeleton-topic-footer {
  margin-top: auto;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  padding-top: 0.75rem;
}

.skeleton-topic-count,
.skeleton-topic-time {
  height: 12px;
  border-radius: 999px;
}

.skeleton-link-content {
  justify-content: flex-start;
  gap: 0.22rem;
  padding: 0;
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
.w-52 { width: 52%; }
.w-55 { width: 55%; }
.w-64 { width: 64%; }
.w-66 { width: 66%; }
.w-72 { width: 72%; }
.w-80 { width: 80%; }
.w-84 { width: 84%; }
.w-88 { width: 88%; }
.w-90 { width: 90%; }
.w-92 { width: 92%; }

.dark .unified-skeleton {
  --sk-shell-dark:
    radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
    radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
    linear-gradient(180deg, #171b20, #101215);
  --sk-shimmer-overlay: linear-gradient(
    90deg,
    rgba(71, 85, 105, 0.24) 0%,
    rgba(100, 116, 139, 0.4) 50%,
    rgba(71, 85, 105, 0.24) 100%
  );
  --sk-border: rgba(71, 85, 105, 0.34);
}

.dark .skeleton-item {
  background: var(--sk-shell-dark);
}

.dark .variant-topic .skeleton-item {
  box-shadow: 0 1px 8px rgba(0, 0, 0, 0.2);
}

.dark .variant-article .skeleton-line,
.dark .variant-friend-link .skeleton-line {
  background: rgba(100, 116, 139, 0.3);
}

.dark .variant-article .skeleton-cover,
.dark .variant-friend-link .skeleton-avatar,
.dark .variant-topic .skeleton-topic-cover,
.dark .variant-topic .skeleton-line {
  background: rgba(71, 85, 105, 0.32);
}

.dark .variant-article .skeleton-meta-row .skeleton-line,
.dark .variant-friend-link .skeleton-meta-row .skeleton-line {
  background: rgba(100, 116, 139, 0.28);
}

@keyframes sk-shimmer-move {
  0% { transform: translate3d(-140%, 0, 0); }
  100% { transform: translate3d(140%, 0, 0); }
}

@media (max-width: $breakpoint-md) {
  .variant-article,
  .variant-friend-link {
    grid-template-columns: 1fr;
  }

  .skeleton-cover {
    width: var(--sk-article-cover-width, 180px);
  }

  .variant-article {
    --sk-article-cover-width: 180px;
    --sk-article-content-padding-y: 0.375rem;
    --sk-article-content-padding-x: 0.5rem;
  }
}

@media (max-width: $breakpoint-sm) {
  .variant-topic {
    grid-template-columns: 1fr;
  }
}

@media (min-width: calc(#{$breakpoint-md} + 1px)) and (max-width: 1180px) {
  .variant-article {
    --sk-article-cover-width: 200px;
    --sk-article-content-padding-y: 0.45rem;
    --sk-article-content-padding-x: 0.6rem;
  }
}

@media (max-width: 480px) {
  .variant-article .skeleton-item {
    flex-direction: column;
    height: auto;
  }

  .variant-article .skeleton-cover {
    width: 100%;
    height: auto;
    aspect-ratio: 16 / 9;
  }

  .variant-article .skeleton-content {
    padding: $spacing-md;
  }
}

@media (prefers-reduced-motion: reduce) {
  .variant-article .skeleton-cover::after,
  .variant-friend-link .skeleton-avatar::after,
  .variant-topic .skeleton-topic-cover::after,
  .variant-topic .skeleton-line::after {
    animation: none;
  }
}
</style>
