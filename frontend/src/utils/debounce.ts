export interface Debounced<A extends unknown[]> {
  (...args: A): void
  /** 立即取消尚未触发的调用（组件卸载时用） */
  cancel(): void
}

/**
 * 尾触发的防抖函数。
 * 搜索框用它做 300ms 防抖，避免每敲一个字都打一次接口。
 */
export function debounce<A extends unknown[]>(
  fn: (...args: A) => void,
  wait = 300,
): Debounced<A> {
  let timer: ReturnType<typeof setTimeout> | undefined

  const wrapped = ((...args: A) => {
    if (timer !== undefined) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      timer = undefined
      fn(...args)
    }, wait)
  }) as Debounced<A>

  wrapped.cancel = () => {
    if (timer !== undefined) {
      clearTimeout(timer)
      timer = undefined
    }
  }

  return wrapped
}
