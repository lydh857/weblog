import { h } from 'vue'
import type { MenuOption } from 'naive-ui'

export const menuOptions: MenuOption[] = [
  { label: '任务管理', key: '/tasks' },
  { label: '候选池', key: '/candidates' },
  { label: '媒体资源', key: '/assets' },
  { label: '站点规则', key: '/site-profiles' },
  { label: '推送目标', key: '/push-targets' },
  { label: '后端清理', key: '/backend-cleanup' }
]

export const renderMenuLabel = (option: MenuOption) =>
  h(
    'a',
    {
      href: `#${String(option.key ?? '')}`
    },
    String(option.label ?? '')
  )
