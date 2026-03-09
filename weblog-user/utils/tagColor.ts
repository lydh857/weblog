/**
 * 预设标签颜色 — 柔和色系，适合浅色/深色模式
 */
const TAG_COLORS = [
  '#3b82f6', // blue
  '#8b5cf6', // violet
  '#ec4899', // pink
  '#f59e0b', // amber
  '#10b981', // emerald
  '#06b6d4', // cyan
  '#f97316', // orange
  '#6366f1', // indigo
  '#14b8a6', // teal
  '#e11d48', // rose
  '#84cc16', // lime
  '#a855f7', // purple
]

/**
 * 根据索引或 ID 获取预设标签颜色
 */
export function getTagColor(indexOrId: number): string {
  return TAG_COLORS[Math.abs(indexOrId) % TAG_COLORS.length]
}
