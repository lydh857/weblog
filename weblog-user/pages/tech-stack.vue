<template>
  <div class="tech-stack-page" :class="{ 'page-entered': pageEntered }">
    <section class="hero-card">
      <p class="hero-kicker">Tech Stack</p>
      <h1 class="hero-title">技术栈</h1>
      <p class="hero-desc">zhhhkl 是一个前后端分离的全栈博客系统，聚焦 SSR 渲染、内容管理、全文检索与互动能力。</p>
      <div class="hero-meta">
        <span>Spring Boot 3.2 + Nuxt 4</span>
        <span>Java 21 + TypeScript</span>
        <span>MySQL 8 + Redis 7</span>
      </div>
    </section>

    <section class="stack-grid">
      <article
        v-for="(group, index) in stackGroups"
        :key="group.title"
        class="stack-card"
        :style="{ '--enter-index': index }"
      >
        <header class="stack-card__head">
          <span class="stack-card__icon">
            <Icon :name="group.icon" size="18" />
          </span>
          <h2>{{ group.title }}</h2>
        </header>
        <p class="stack-card__intro">{{ group.intro }}</p>
        <ul class="stack-list">
          <li v-for="item in group.items" :key="item.name">
            <span class="stack-name">{{ item.name }}</span>
            <span class="stack-note">{{ item.note }}</span>
          </li>
        </ul>
      </article>
    </section>
  </div>
</template>

<script setup lang="ts">
interface StackItem {
  name: string
  note: string
}

interface StackGroup {
  title: string
  icon: string
  intro: string
  items: StackItem[]
}

const pageEntered = ref(false)

const stackGroups: StackGroup[] = [
  {
    title: '后端服务',
    icon: 'heroicons:server-stack-20-solid',
    intro: '多模块 Spring Boot 架构，覆盖内容、系统、互动与基础设施能力。',
    items: [
      { name: 'Java 21', note: '核心运行时与并发能力' },
      { name: 'Spring Boot 3.2', note: '应用框架与自动配置' },
      { name: 'MyBatis-Plus', note: '数据访问与实体映射' },
      { name: 'Sa-Token', note: '认证鉴权与会话管理' },
      { name: 'Lucene', note: '全文检索与搜索索引' },
    ],
  },
  {
    title: '用户端前端',
    icon: 'heroicons:device-phone-mobile-20-solid',
    intro: '以 SSR 为核心，兼顾 SEO、首屏性能与交互体验。',
    items: [
      { name: 'Nuxt 4', note: 'SSR 页面渲染与路由' },
      { name: 'Vue 3 + TypeScript', note: '组件化开发与类型安全' },
      { name: 'SCSS', note: '主题变量与样式体系' },
      { name: 'VueUse', note: '组合式工具能力复用' },
    ],
  },
  {
    title: '管理端前端',
    icon: 'heroicons:window-20-solid',
    intro: '面向内容与运营后台的 SPA 管理体验。',
    items: [
      { name: 'Nuxt 4 SPA', note: '后台路由与权限页面' },
      { name: 'Element Plus', note: '管理后台 UI 组件库' },
      { name: 'Pinia', note: '状态管理与数据流组织' },
    ],
  },
  {
    title: '数据与基础设施',
    icon: 'heroicons:circle-stack-20-solid',
    intro: '缓存、存储、反向代理与部署链路完整。',
    items: [
      { name: 'MySQL 8.0', note: '业务数据持久化' },
      { name: 'Redis 7.2', note: '缓存与会话加速' },
      { name: 'Docker Compose', note: '本地与服务端容器编排' },
      { name: 'Nginx + PM2', note: '代理转发与进程守护' },
    ],
  },
]

useHead({
  title: '技术栈 - zhhhkl',
  meta: [
    { name: 'description', content: 'zhhhkl 技术栈说明：后端、前端、数据库与部署基础设施。' },
  ],
})

onMounted(() => {
  if (!import.meta.client) {
    return
  }

  window.requestAnimationFrame(() => {
    pageEntered.value = true
  })
})
</script>

<style scoped lang="scss">
.tech-stack-page {
  max-width: var(--layout-max-width);
  margin: 0 auto;
  padding: var(--layout-page-padding-y) var(--layout-page-padding-x) calc(var(--layout-page-padding-y) + 1rem);
  opacity: 0;
  transform: translate3d(0, 10px, 0);
  transition:
    opacity 680ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 760ms cubic-bezier(0.22, 1, 0.36, 1);
}

.tech-stack-page.page-entered {
  opacity: 1;
  transform: translate3d(0, 0, 0);
}

.hero-card {
  border-radius: 14px;
  padding: 1.4rem 1.45rem;
  margin-bottom: 1rem;
  border: 1px solid rgba(148, 163, 184, 0.26);
  background:
    radial-gradient(circle at 0% 0%, rgba(37, 99, 235, 0.08), transparent 45%),
    radial-gradient(circle at 100% 100%, rgba(14, 165, 233, 0.1), transparent 50%),
    #fff;

  .dark & {
    border-color: rgba(148, 163, 184, 0.14);
    background:
      radial-gradient(circle at 0% 0%, rgba(59, 130, 246, 0.12), transparent 45%),
      radial-gradient(circle at 100% 100%, rgba(56, 189, 248, 0.1), transparent 50%),
      #171b20;
  }
}

.hero-kicker {
  margin: 0;
  font-size: 0.76rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: #64748b;

  .dark & {
    color: #94a3b8;
  }
}

.hero-title {
  margin: 0.4rem 0 0;
  font-size: clamp(1.55rem, 2.6vw, 2rem);
  line-height: 1.25;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.hero-desc {
  margin: 0.6rem 0 0;
  font-size: 0.92rem;
  line-height: 1.7;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 0.42rem;
  margin-top: 0.85rem;

  span {
    display: inline-flex;
    align-items: center;
    padding: 0.28rem 0.62rem;
    border-radius: 999px;
    font-size: 0.75rem;
    color: #475569;
    background: rgba(148, 163, 184, 0.15);

    .dark & {
      color: #cbd5e1;
      background: rgba(148, 163, 184, 0.2);
    }
  }
}

.stack-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 1rem;
}

.stack-card {
  border: 1px solid rgba(148, 163, 184, 0.24);
  border-radius: 12px;
  padding: 1rem;
  background: #fff;
  opacity: 0;
  transform: translate3d(0, 12px, 0);
  transition:
    border-color 0.2s ease,
    box-shadow 0.2s ease,
    opacity 560ms cubic-bezier(0.22, 1, 0.36, 1),
    transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
  transition-delay: calc(40ms + min(var(--enter-index), 7) * 50ms);

  .page-entered & {
    opacity: 1;
    transform: translate3d(0, 0, 0);
  }

  &:hover {
    border-color: rgba(59, 130, 246, 0.35);
    box-shadow: 0 10px 22px rgba(15, 23, 42, 0.08);
  }

  .dark & {
    border-color: rgba(148, 163, 184, 0.14);
    background: #171b20;

    &:hover {
      border-color: rgba(96, 165, 250, 0.4);
      box-shadow: 0 12px 26px rgba(2, 6, 23, 0.3);
    }
  }
}

.stack-card__head {
  display: flex;
  align-items: center;
  gap: 0.55rem;

  h2 {
    margin: 0;
    font-size: 1rem;
    color: $color-text;

    .dark & {
      color: $color-dark-text;
    }
  }
}

.stack-card__icon {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: rgba(59, 130, 246, 0.14);
  color: #2563eb;

  .dark & {
    background: rgba(96, 165, 250, 0.18);
    color: #93c5fd;
  }
}

.stack-card__intro {
  margin: 0.62rem 0 0.72rem;
  font-size: 0.84rem;
  line-height: 1.62;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.stack-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.52rem;

  li {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.8rem;
    padding: 0.5rem 0.62rem;
    border-radius: 8px;
    background: #f8fafc;

    .dark & {
      background: #101215;
    }
  }
}

.stack-name {
  font-size: 0.84rem;
  font-weight: 600;
  color: $color-text;

  .dark & {
    color: $color-dark-text;
  }
}

.stack-note {
  font-size: 0.76rem;
  color: #64748b;
  text-align: right;

  .dark & {
    color: #94a3b8;
  }
}

@media (max-width: $breakpoint-md) {
  .stack-grid {
    grid-template-columns: 1fr;
  }

  .hero-card {
    padding: 1rem;
  }

  .stack-card {
    padding: 0.88rem;
  }

  .stack-list li {
    flex-direction: column;
    align-items: flex-start;
  }

  .stack-note {
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .tech-stack-page,
  .tech-stack-page.page-entered,
  .stack-card,
  .page-entered .stack-card {
    opacity: 1;
    transform: none;
    transition: none;
  }
}
</style>
