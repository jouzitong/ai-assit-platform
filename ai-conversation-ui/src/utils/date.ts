export function formatDate(input: string | number | Date) {
  return new Date(input).toLocaleString()
}

export function formatRelativeTime(input?: string | number | Date | null) {
  if (!input) {
    return ''
  }

  const time = new Date(input).getTime()
  if (Number.isNaN(time)) {
    return ''
  }

  const diffMs = Date.now() - time
  const diffMinutes = Math.floor(diffMs / (1000 * 60))

  if (diffMinutes < 2) {
    return '刚刚'
  }
  if (diffMinutes < 60) {
    return `${diffMinutes} 分钟前`
  }

  const diffHours = Math.floor(diffMinutes / 60)
  if (diffHours < 24) {
    return `${diffHours} 小时前`
  }

  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 30) {
    return `${diffDays} 天前`
  }

  return formatDate(input)
}
