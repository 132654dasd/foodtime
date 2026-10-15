import type { CSSProperties } from 'vue'

/**
 * 无图菜谱的确定性渐变封面。
 *
 * 种子数据 `coverUrl` 全是 null，这里用菜名做哈希选一组固定渐变：
 * 同一个菜名永远得到同一个颜色（刷新、换页面、换浏览器都一致）。
 */

/** 暖色系渐变对（呼应美食主题） */
const PALETTE: ReadonlyArray<readonly [string, string]> = [
  ['#ff9a5a', '#ff5f6d'],
  ['#ffb347', '#ff7b54'],
  ['#f78ca0', '#f9748f'],
  ['#ffd36e', '#fc9842'],
  ['#ff7e5f', '#feb47b'],
  ['#f6d365', '#fda085'],
  ['#ff8a65', '#d84315'],
  ['#ffb199', '#ff0844'],
  ['#f0932b', '#eb4d4b'],
  ['#ffa751', '#ffe259'],
  ['#ee9ca7', '#ff6b6b'],
  ['#fa709a', '#fee140'],
]

/** FNV-1a：短字符串上分布够均匀，且实现稳定（不依赖 JS 引擎的随机性） */
export function hashCode(text: string): number {
  let hash = 2166136261
  for (let index = 0; index < text.length; index += 1) {
    hash ^= text.charCodeAt(index)
    hash = Math.imul(hash, 16777619)
  }
  return hash >>> 0
}

/** 取菜名首字（按码点取，避免把 emoji 切成半个） */
export function coverInitial(title: string): string {
  const trimmed = (title ?? '').trim()
  if (!trimmed) {
    return '菜'
  }
  const chars = Array.from(trimmed)
  return chars[0] ?? '菜'
}

/** 确定性渐变：颜色组合与角度都由菜名哈希决定 */
export function coverGradient(title: string): CSSProperties {
  const seed = hashCode((title ?? '').trim() || 'foodie')
  const pair = PALETTE[seed % PALETTE.length] ?? PALETTE[0]!
  const angle = 115 + (seed % 6) * 12
  return { backgroundImage: `linear-gradient(${angle}deg, ${pair[0]}, ${pair[1]})` }
}
