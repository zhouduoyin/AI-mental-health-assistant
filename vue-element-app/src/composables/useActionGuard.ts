import { getCurrentScope, onScopeDispose, ref } from 'vue'
import type { Ref } from 'vue'

import { debounce, throttle } from '@/utils/timing'
import type { Debounced } from '@/utils/timing'

interface UseActionGuardOptions {
  /** 触发间隔，默认 400ms */
  wait?: number
  /**
   * throttle：首次立即执行，间隔内忽略连点（按钮默认）
   * debounce：等安静下来再执行（适合可合并的轻操作）
   */
  mode?: 'throttle' | 'debounce'
}

/**
 * 给按钮动作加「防连点 + loading」。
 * 组件里只关心 pending 与 trigger，时序细节留在 useActionGuard。
 */
export function useActionGuard<Args extends unknown[]>(
  action: (...args: Args) => unknown | Promise<unknown>,
  { wait = 400, mode = 'throttle' }: UseActionGuardOptions = {},
): { pending: Ref<boolean>; trigger: (...args: Args) => void } {
  const pending = ref(false)
  // 异步动作未结束时不再进入，避免重复提交
  let running = false

  const run = async (...args: Args): Promise<void> => {
    if (running) return

    running = true
    pending.value = true
    try {
      await action(...args)
    } finally {
      running = false
      pending.value = false
    }
  }

  const trigger = mode === 'debounce' ? debounce(run, wait) : throttle(run, wait)

  // 防抖模式下组件卸载时还有未执行的调用：撤销它，避免离开页面后动作才触发
  if (mode === 'debounce' && getCurrentScope()) {
    const cancel = (trigger as Debounced<Args>).cancel
    onScopeDispose(cancel)
  }

  return { pending, trigger }
}
