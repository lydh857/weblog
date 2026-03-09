<template>
  <div class="ad-apply-page">
    <h1>广告投放申请</h1>
    <p class="subtitle">提交广告申请后，管理员审核通过即可展示。</p>

    <form v-if="!submitted" class="ad-form" @submit.prevent="handleSubmit">
      <div class="form-group">
        <label>广告标题 <span class="required">*</span></label>
        <input v-model="form.title" type="text" maxlength="100" placeholder="请输入广告标题" required />
      </div>

      <div class="form-group">
        <label>广告类型 <span class="required">*</span></label>
        <select v-model="form.type" required>
          <option value="image">图片广告</option>
          <option value="code">代码广告（HTML）</option>
        </select>
      </div>

      <div class="form-group">
        <label>广告位置 <span class="required">*</span></label>
        <select v-model="form.position" required>
          <option value="top">顶部横幅</option>
          <option value="sidebar">侧边栏</option>
          <option value="middle">文章中部</option>
          <option value="bottom">底部</option>
        </select>
      </div>

      <div class="form-group">
        <label>{{ form.type === 'image' ? '图片 URL' : 'HTML 代码' }} <span class="required">*</span></label>
        <textarea
          v-model="form.content"
          rows="4"
          :placeholder="form.type === 'image' ? '请输入图片 URL' : '请输入 HTML 广告代码'"
          required
        />
      </div>

      <div class="form-group">
        <label>跳转链接</label>
        <input v-model="form.linkUrl" type="url" placeholder="https://example.com" />
      </div>

      <div class="form-row">
        <div class="form-group">
          <label>开始时间</label>
          <input v-model="form.startTime" type="datetime-local" />
        </div>
        <div class="form-group">
          <label>结束时间</label>
          <input v-model="form.endTime" type="datetime-local" />
        </div>
      </div>

      <button type="submit" class="btn-submit" :disabled="submitting">
        {{ submitting ? '提交中...' : '提交申请' }}
      </button>
    </form>

    <div v-else class="success-msg">
      <p>广告申请已提交，请等待管理员审核。</p>
      <NuxtLink to="/">返回首页</NuxtLink>
    </div>
  </div>
</template>

<script setup lang="ts">
import { advertisementApi } from '~/api/advertisement'

useHead({ title: '广告投放申请' })

const form = reactive({
  title: '',
  type: 'image',
  content: '',
  linkUrl: '',
  position: 'sidebar',
  startTime: '',
  endTime: '',
})

const submitting = ref(false)
const submitted = ref(false)

async function handleSubmit() {
  submitting.value = true
  try {
    await advertisementApi.apply({
      title: form.title,
      type: form.type,
      content: form.content,
      linkUrl: form.linkUrl || undefined,
      position: form.position,
      startTime: form.startTime || undefined,
      endTime: form.endTime || undefined,
    })
    submitted.value = true
  } catch (e: any) {
    alert(e.message || '提交失败')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped lang="scss">
.ad-apply-page {
  max-width: 640px;
  margin: 40px auto;
  padding: 0 20px;

  h1 { font-size: 1.5rem; margin-bottom: 8px; }
  .subtitle { color: var(--text-secondary, #666); margin-bottom: 32px; }
}

.ad-form {
  .form-group {
    margin-bottom: 20px;
    label {
      display: block;
      margin-bottom: 6px;
      font-weight: 500;
      font-size: 0.9rem;
      .required { color: #e74c3c; }
    }

    input,
    select,
    textarea {
      width: 100%;
      padding: 10px 12px;
      border: 1px solid var(--border-color, #ddd);
      border-radius: 6px;
      font-size: 0.95rem;
      background: var(--bg-card, #fff);
      color: var(--text-primary, #1a1a1a);
      &:focus { outline: none; border-color: var(--color-primary, #409eff); }
    }

    textarea { resize: vertical; }
  }

  .form-row {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 16px;
  }

  .btn-submit {
    width: 100%;
    padding: 12px;
    background: var(--color-primary, #409eff);
    color: #fff;
    border: none;
    border-radius: 8px;
    font-size: 1rem;
    cursor: pointer;
    &:hover { opacity: 0.9; }
    &:disabled { opacity: 0.6; cursor: not-allowed; }
  }
}

.success-msg {
  text-align: center;
  padding: 40px 0;
  p { margin-bottom: 16px; color: #27ae60; font-size: 1.1rem; }
  a { color: var(--color-primary, #409eff); }
}
</style>
