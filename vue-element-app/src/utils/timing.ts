/**
 * 事件时序工具：高频事件（搜索输入、按钮连点）只保留必要的一次。
 * 刻意不引入 lodash，保持依赖极简。
 */

export type Debounced<Args extends unknown[]> = ((...args: Args) => void) & {
  /** 取消尚未执行的调用（组件卸载时用） */
  cancel: () => void
}

/**
 * 防抖：停止触发 wait 毫秒后才执行。
 * 适合搜索框输入这类「等用户打完字再查」的场景。
 */
export function debounce<Args extends unknown[]>(
  handler: (...args: Args) => void,
  wait = 300,
): Debounced<Args> {
  let timer: ReturnType<typeof setTimeout> | null = null

  const cancel = (): void => {
    if (timer) {
      clearTimeout(timer)
      timer = null
    }
  }

  const run = (...args: Args): void => {
    cancel()
    timer = setTimeout(() => {
      timer = null
      handler(...args)
    }, wait)
  }

  return Object.assign(run, { cancel })
}

/**
 * 节流（首次立即执行）：wait 毫秒内只响应第一次调用。
 * 适合提交、导出、发送这类必须立即给出反馈、又不能重复触发的按钮。
 */
export function throttle<Args extends unknown[]>(
  handler: (...args: Args) => void,
  wait = 400,
): (...args: Args) => void {
  let last = 0

  return (...args: Args): void => {
    const now = Date.now()
    if (now - last < wait) return
    last = now
    handler(...args)
  }
}
