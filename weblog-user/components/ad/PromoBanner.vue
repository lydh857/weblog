<template>
  <div v-if="promos.length" class="promo-banner" :class="[`promo-${position}`]">
    <div v-for="item in promos" :key="item.id" class="promo-item">
      <template v-if="item.type === 'image'">
        <a
          v-if="item.safeLinkUrl"
          :href="item.safeLinkUrl"
          target="_blank"
          rel="noopener noreferrer nofollow"
          class="promo-link"
          @click="recordClick(item.id)"
        >
          <img :src="item.content" :alt="item.title" class="promo-image" loading="lazy" >
        </a>
        <img v-else :src="item.content" :alt="item.title" class="promo-image" loading="lazy" >
      </template>
      <!-- 已经过 DOMPurify 白名单净化 -->
      <!-- eslint-disable-next-line vue/no-v-html -->
      <div v-else-if="item.type === 'code'" class="promo-code" v-html="sanitize(item.content)" />
      <span class="promo-label">推广</span>
    </div>
  </div>
</template>

<script setup lang="ts">
import { adApi, type AdvertisementVO } from '~/api/marketing/ad'
import DOMPurify from 'dompurify'
import { normalizeSafeHref } from '~/utils/security/urlSafety'

const props = defineProps<{ position: string }>()

interface PromoItem extends AdvertisementVO {
  safeLinkUrl: string | null
}

const promos = ref<PromoItem[]>([])

function sanitize(html: string) {
  return DOMPurify.sanitize(html, {
    ALLOWED_TAGS: ['a', 'img', 'span', 'div', 'p'],
    ALLOWED_ATTR: ['href', 'src', 'alt', 'target', 'rel', 'class'],
    ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|\/(?!\/)|[^a-z]|[a-z+.-]+(?:[^a-z+.\-:]|$))/i,
  })
}

function recordClick(id: number) {
  adApi.recordClick(id).catch(() => {})
}

onMounted(async () => {
  try {
    const res = await adApi.getByPosition(props.position)
    promos.value = (res.data || []).map((item) => ({
      ...item,
      safeLinkUrl: normalizeSafeHref(item.linkUrl),
    }))
  } catch { /* ignore */ }
})
</script>

<style scoped lang="scss">
.promo-banner { margin: 1rem 0; }
.promo-item {
  position: relative;
  border-radius: $radius-md;
  overflow: hidden;
  & + .promo-item { margin-top: 0.75rem; }
}
.promo-link { display: block; cursor: pointer; }
.promo-image { width: 100%; display: block; border-radius: $radius-md; }
.promo-code { border-radius: $radius-md; overflow: hidden; }
.promo-label {
  position: absolute;
  top: 0.375rem;
  right: 0.375rem;
  padding: 0.0625rem 0.375rem;
  font-size: 0.65rem;
  color: #fff;
  background: rgba(0, 0, 0, 0.4);
  border-radius: $radius-sm;
  pointer-events: none;
}
.promo-sidebar { .promo-image { border-radius: $radius-md; } }
</style>
