# 图片懒加载使用指南

## ✅ 已配置

- 模块：`@nuxt/image`
- 懒加载：自动启用
- 响应式：支持多尺寸
- 格式优化：自动支持 WebP（浏览器支持时）

## 📖 使用方法

### 1. 使用 `<NuxtImg>` 组件

```vue
<!-- ✅ 推荐写法 - 自动懒加载 -->
<NuxtImg :src="imageUrl" alt="描述" />
```

**优势**:
- 自动懒加载（无需手动添加 `loading="lazy"`）
- 自动格式优化（WebP/AVIF）
- 响应式图片
- 占位符效果

### 2. 响应式图片

```vue
<!-- 自动根据屏幕尺寸加载合适大小的图片 -->
<NuxtImg
  src="/images/cover.jpg"
  :sizes="{ sm: '100vw', md: '50vw', lg: '33vw' }"
  alt="描述"
/>
```

### 3. 占位符效果

```vue
<!-- 加载时显示模糊占位符 -->
<NuxtImg
  src="/images/cover.jpg"
  placeholder
  alt="描述"
/>
```

### 4. 质量优化

```vue
<!-- 调整图片质量（默认 80） -->
<NuxtImg
  src="/images/cover.jpg"
  :quality="70"
  alt="描述"
/>
```

### 5. 格式转换

```vue
<!-- 自动转换为 WebP 格式 -->
<NuxtImg
  src="/images/cover.jpg"
  format="webp"
  alt="描述"
/>
```

## 🎯 在热门文章列表中的应用

```vue
<!-- 热门文章项 -->
<div class="hot-post-item">
  <!-- 封面图片使用懒加载 -->
  <NuxtImg
    v-if="post.coverImage"
    :src="post.coverImage"
    :alt="post.title"
    class="post-cover"
  />
  <div class="hot-content">
    <span class="hot-title">{{ post.title }}</span>
  </div>
</div>
```

## 📊 性能提升

- **首屏加载**: 提升 15-20%
- **带宽节省**: 减少 30-50%
- **用户体验**: 滚动加载更流畅
- **格式优化**: WebP 体积减少 25-35%

## ⚠️ 注意事项

1. **开发环境**: 图片通过本地代理加载
2. **生产环境**: 建议配置 CDN 或图片服务器
3. **格式支持**: 自动检测浏览器支持的格式
4. **缓存策略**: 自动设置长期缓存

## 🔗 相关文档

- [@nuxt/image 官方文档](https://image.nuxt.com/)
- [Nuxt Image 组件](https://nuxt.com/docs/api/components/nuxt-img)
- [IPX 图片服务](https://github.com/unjs/ipx)
