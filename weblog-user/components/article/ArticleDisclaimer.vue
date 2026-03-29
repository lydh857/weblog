<template>
  <div class="article-disclaimer">
    <div class="disclaimer-header">
      <Icon name="heroicons:information-circle-20-solid" size="18" />
      <span>免责声明</span>
    </div>
    <div class="disclaimer-content">
      <p>1. 本站所有资源文章出自互联网收集整理，本站不参与制作，如果侵犯了您的合法权益，请联系本站我们会及时删除。</p>
      <p>2. 本站发布资源来源于互联网，可能存在水印或者引流等信息，请用户擦亮眼睛自行鉴别，做一个有主见和判断力的用户。</p>
      <p>3. 本站资源仅供研究、学习交流之用，若使用商业用途，请购买正版授权，否则产生的一切后果将由下载用户自行承担。</p>
    </div>
  </div>
</template>

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
