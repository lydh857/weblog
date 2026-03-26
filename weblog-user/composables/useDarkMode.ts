import { useDark, useToggle } from '@vueuse/core'
import { watch } from 'vue'

export function useDarkMode() {
  const isDark = useDark({
    selector: 'html',
    attribute: 'class',
    valueDark: 'dark',
    valueLight: '',
  })
  const toggleDark = useToggle(isDark)

  if (import.meta.client) {
    watch(
      isDark,
      (value) => {
        const cookieValue = value ? 'dark' : 'light'
        const cookie = `weblog-theme=${cookieValue}; Path=/; Max-Age=31536000; SameSite=Lax`
        document.cookie = cookie
      },
      { immediate: true }
    )
  }

  return { isDark, toggleDark }
}
