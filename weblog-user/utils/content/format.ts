/**
 * 格式化相对时间
 * 7天内显示相对时间，超过7天显示具体日期（同年省略年份）
 */
export function formatRelativeTime(dateStr: string): string {
  const now = Date.now()
  const date = new Date(dateStr)
  const diff = now - date.getTime()
  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mm = String(date.getMinutes()).padStart(2, '0')
  return y === new Date().getFullYear() ? `${m}-${d} ${hh}:${mm}` : `${y}-${m}-${d} ${hh}:${mm}`
}

/**
 * 数量友好展示
 * < 1000 → 原数字，1k~9.9k 用 k，≥1w 用 w
 */
export function formatCount(count: number): string {
  if (count < 1000) return String(count)
  if (count < 10000) {
    const val = count / 1000
    return (val % 1 === 0 ? val.toFixed(0) : val.toFixed(1)) + 'k'
  }
  const val = count / 10000
  return (val % 1 === 0 ? val.toFixed(0) : val.toFixed(1)) + 'w'
}
