import { onBeforeUnmount, onMounted, ref } from 'vue'

export interface TypewriterOptions {
  /** 每个字的间隔（毫秒） */
  speed?: number
  /** 开始前的停顿（毫秒） */
  startDelay?: number
}

/**
 * 逐字输出一段文案，用于首屏对话预览。
 * 降低动效偏好下会直接显示完整文本，避免依赖动画才能读到内容。
 */
export function useTypewriter(text: string, options: TypewriterOptions = {}) {
  const { speed = 42, startDelay = 0 } = options

  const output = ref('')
  const done = ref(false)

  let timer: ReturnType<typeof setTimeout> | undefined

  onMounted(() => {
    const reduced =
      typeof window === 'undefined' ||
      window.matchMedia('(prefers-reduced-motion: reduce)').matches

    if (reduced) {
      output.value = text
      done.value = true
      return
    }

    let index = 0

    const tick = (): void => {
      index += 1
      output.value = text.slice(0, index)

      if (index >= text.length) {
        done.value = true
        return
      }

      timer = setTimeout(tick, speed)
    }

    timer = setTimeout(tick, startDelay)
  })

  onBeforeUnmount(() => {
    if (timer) clearTimeout(timer)
  })

  return { output, done }
}
