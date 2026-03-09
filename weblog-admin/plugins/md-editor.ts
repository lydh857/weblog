// md-editor-v3 全局配置：启用 XSS 插件并允许 img 标签的 style 属性
import { config, XSSPlugin } from 'md-editor-v3'

config({
  markdownItPlugins(plugins) {
    return [
      ...plugins,
      {
        type: 'xss',
        plugin: XSSPlugin,
        options: {
          extendedWhiteList: {
            img: ['style', 'class', 'alt', 'src', 'width', 'height'],
          },
        },
      },
    ]
  },
})

export default defineNuxtPlugin(() => {
  // 配置已在模块顶层执行
})
