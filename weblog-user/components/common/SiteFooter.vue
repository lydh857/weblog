<template>
  <footer class="site-footer">
    <div class="footer-shell">
      <section class="footer-brand">
        <NuxtLink to="/" class="brand-head">
          <span class="brand-mark">
            <img
              src="/brand/logo.png"
              :alt="`${siteName} logo`"
              class="brand-logo-img"
              width="34"
              height="34"
              style="width:34px;height:34px;max-width:34px;display:block;object-fit:cover;"
            >
          </span>
          <strong>{{ siteName }}</strong>
        </NuxtLink>
        <p class="brand-slogan">{{ siteDescription }}</p>
      </section>

      <div class="footer-links">
        <section class="link-group">
          <h4>快速导航</h4>
          <NuxtLink to="/">首页</NuxtLink>
          <NuxtLink to="/category">分类</NuxtLink>
          <NuxtLink to="/topic">专题</NuxtLink>
          <NuxtLink to="/tags">标签</NuxtLink>
        </section>

        <section class="link-group">
          <h4>内容与服务</h4>
          <NuxtLink to="/ranking">热门排行</NuxtLink>
          <NuxtLink to="/tech-stack">技术栈</NuxtLink>
          <NuxtLink to="/friend-links">友情链接</NuxtLink>
          <button type="button" class="link-btn" @click="openSearchModal">站内搜索</button>
          <button type="button" class="link-btn" @click="openAdApplyModal">广告投放</button>
        </section>

        <section class="link-group">
          <h4>联系与说明</h4>
          <a href="mailto:zhhhkl857@gmail.com">商务合作</a>
          <NuxtLink to="/user">个人中心</NuxtLink>
          <p class="tips">如有侵权或内容问题，请邮件联系处理。</p>
        </section>
      </div>
    </div>

    <div class="footer-bottom">
      <p>本站内容仅供学习与交流，商业使用请联系原作者授权。</p>
      <p>&copy; {{ currentYear }} {{ siteName }}. All rights reserved.</p>
    </div>
  </footer>
</template>

<script setup lang="ts">
const currentYear = new Date().getFullYear()
const searchModal = useSearchModal()
const loginModal = useLoginModal()
const adApplyModal = useAdApplyModal()
const userStore = useUserStore()
const siteConfig = useSiteConfigState()
const siteName = computed(() => siteConfig.value.siteName || DEFAULT_SITE_NAME)
const siteDescription = computed(() => siteConfig.value.siteDescription || DEFAULT_SITE_DESCRIPTION)

function openSearchModal() {
  searchModal.open()
}

function openAdApplyModal() {
  if (!userStore.isLoggedIn) {
    loginModal.open('code', () => {
      adApplyModal.open()
    })
    return
  }

  adApplyModal.open()
}
</script>

<style scoped lang="scss">
.site-footer {
  position: relative;
  overflow: hidden;
  border-top: 1px solid $color-border;
  background:
    radial-gradient(120% 120% at 0% 0%, rgba(37, 99, 235, 0.09), transparent 46%),
    radial-gradient(120% 120% at 100% 100%, rgba(14, 165, 233, 0.11), transparent 52%),
    linear-gradient(180deg, #ffffff, #f8fafc);
  color: $color-text;

  .dark & {
    border-top-color: $color-dark-border;
    background:
      radial-gradient(120% 120% at 0% 0%, rgba(59, 130, 246, 0.13), transparent 45%),
      radial-gradient(120% 120% at 100% 100%, rgba(56, 189, 248, 0.1), transparent 52%),
      linear-gradient(180deg, #171b20, #101215);
    color: $color-dark-text;
  }
}

.footer-shell {
  width: min(1200px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 2.2rem 0 1.4rem;
  display: grid;
  grid-template-columns: minmax(240px, 1.1fr) minmax(0, 1.6fr);
  gap: 2.2rem;
}

.brand-head {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  text-decoration: none;
  color: inherit;

  strong {
    font-size: 1.2rem;
    font-weight: 800;
    letter-spacing: 0.02em;
  }
}

.brand-mark {
  width: 34px;
  height: 34px;
  border-radius: 10px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.brand-logo-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.brand-slogan {
  margin: 0.85rem 0 0;
  max-width: 28ch;
  font-size: 0.9rem;
  line-height: 1.6;
  color: $color-text-muted;

  .dark & {
    color: #94a3b8;
  }
}

.footer-links {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
}

.link-group {
  h4 {
    margin: 0 0 0.72rem;
    font-size: 0.95rem;
    font-weight: 700;
    color: $color-text;

    .dark & {
      color: #e2e8f0;
    }
  }

  a,
  .link-btn {
    display: block;
    width: fit-content;
    margin-bottom: 0.48rem;
    font-size: 0.86rem;
    color: $color-text-muted;
    text-decoration: none;
    transition: color 0.2s ease, transform 0.2s ease;

    &:hover {
      color: $color-primary;
      transform: translateX(2px);
    }

    .dark & {
      color: #94a3b8;
    }
  }
}

.link-btn {
  border: none;
  padding: 0;
  background: transparent;
  font: inherit;
  cursor: pointer;
}

.tips {
  margin: 0.5rem 0 0;
  font-size: 0.76rem;
  line-height: 1.6;
  color: #64748b;

  .dark & {
    color: #64748b;
  }
}

.footer-bottom {
  width: min(1200px, calc(100% - 2rem));
  margin: 0 auto;
  padding: 0.9rem 0 1.3rem;
  border-top: 1px solid rgba(148, 163, 184, 0.28);
  text-align: center;

  .dark & {
    border-top-color: rgba(100, 116, 139, 0.34);
  }

  p {
    margin: 0.2rem 0;
    font-size: 0.78rem;
    color: #64748b;

    .dark & {
      color: #64748b;
    }
  }
}

@media (max-width: $breakpoint-md) {
  .footer-shell {
    width: min(1200px, calc(100% - 1.1rem));
    grid-template-columns: 1fr;
    gap: 0.9rem;
    padding: 1.1rem 0 0.72rem;
    justify-items: center;
    text-align: center;
  }

  .footer-brand {
    display: flex;
    flex-direction: column;
    align-items: center;
  }

  .brand-head {
    gap: 0.48rem;
    justify-content: center;

    strong {
      font-size: 1.05rem;
    }
  }

  .brand-mark {
    width: 30px;
    height: 30px;
    border-radius: 8px;
  }

  .brand-slogan {
    max-width: none;
    margin-top: 0.52rem;
    font-size: 0.82rem;
    line-height: 1.45;
    text-align: center;
  }

  .footer-links {
    grid-template-columns: 1fr;
    gap: 0.5rem;
    width: 100%;
  }

  .link-group {
    display: flex;
    flex-wrap: wrap;
    align-items: center;
    justify-content: center;
    column-gap: 0.42rem;
    row-gap: 0.36rem;
    padding-top: 0.52rem;
    border-top: 1px dashed rgba(148, 163, 184, 0.3);
    text-align: center;

    h4 {
      flex: 0 0 100%;
      margin: 0 0 0.16rem;
      font-size: 0.86rem;
      text-align: center;
    }

    a,
    .link-btn {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      width: auto;
      margin-bottom: 0;
      padding: 0.22rem 0.56rem;
      border-radius: 999px;
      border: none;
      background: transparent;
      font-size: 0.78rem;
      line-height: 1.2;
      transition: color 0.2s ease;

      &:hover {
        transform: none;
        background: transparent;
      }

      .dark & {
        background: transparent;
      }
    }

    .tips {
      flex: 0 0 100%;
      margin-top: 0.22rem;
      font-size: 0.72rem;
      line-height: 1.45;
      text-align: center;
    }

    .dark & {
      border-top-color: rgba(100, 116, 139, 0.32);
    }
  }

  .footer-bottom {
    width: min(1200px, calc(100% - 1.1rem));
    padding: 0.62rem 0 calc(0.85rem + env(safe-area-inset-bottom));

    p {
      margin: 0.14rem 0;
      font-size: 0.72rem;
      line-height: 1.4;
    }
  }
}
</style>
