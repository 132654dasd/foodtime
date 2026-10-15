/** 后端时间形如 `2026-10-15 19:00:00`，也可能是带时区的 ISO 串；统一展示成 `YYYY-MM-DD HH:mm` */
const DATE_TIME_RE = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

export function formatDateTime(value?: string | null): string {
  if (!value) {
    return '—'
  }
  const matched = DATE_TIME_RE.exec(value)
  if (matched) {
    return `${matched[1]}-${matched[2]}-${matched[3]} ${matched[4]}:${matched[5]}`
  }
  const parsed = new Date(value)
  if (Number.isNaN(parsed.getTime())) {
    return value
  }
  return `${parsed.getFullYear()}-${pad(parsed.getMonth() + 1)}-${pad(parsed.getDate())} ${pad(
    parsed.getHours(),
  )}:${pad(parsed.getMinutes())}`
}

/** 只到日期：`YYYY-MM-DD` */
export function formatDate(value?: string | null): string {
  const formatted = formatDateTime(value)
  return formatted === '—' ? formatted : formatted.slice(0, 10)
}

/** 耗时展示：120 → `2 小时`，45 → `45 分钟` */
export function formatMinutes(minutes?: number | null): string {
  if (minutes === null || minutes === undefined) {
    return '—'
  }
  if (minutes < 60) {
    return `${minutes} 分钟`
  }
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分`
}

/** 数字兜底，避免 null 直接渲染成 "null" */
export function formatNumber(value?: number | null, fallback = '—'): string {
  return value === null || value === undefined ? fallback : String(value)
}

/** 评分展示，保留一位小数 */
export function formatRating(value?: number | null): string {
  if (!value) {
    return '暂无评分'
  }
  return Number(value).toFixed(1)
}

/** 难度 → Element Plus tag 类型（低=success 中=warning 高=danger） */
export function difficultyTagType(
  difficulty?: string | null,
): 'success' | 'warning' | 'danger' | 'info' {
  switch (difficulty) {
    case 'EASY':
      return 'success'
    case 'MEDIUM':
      return 'warning'
    case 'HARD':
      return 'danger'
    default:
      return 'info'
  }
}
