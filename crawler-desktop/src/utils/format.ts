export const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-'
  }
  const normalized = value.trim().replace('T', ' ')
  const plainMatch = normalized.match(/^(\d{4})-(\d{2})-(\d{2})\s+(\d{2}):(\d{2}):(\d{2})/)
  if (plainMatch) {
    return `${plainMatch[1]}-${plainMatch[2]}-${plainMatch[3]} ${plainMatch[4]}:${plainMatch[5]}:${plainMatch[6]}`
  }
  const dt = new Date(value)
  if (Number.isNaN(dt.getTime())) {
    return value.replace('T', ' ').replace(/\.\d+$/, '')
  }
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
    hour12: false
  })
    .format(dt)
    .replaceAll('/', '-')
}

export const normalizeCellText = (value?: string | null) => (value && value.trim() ? value : '-')
