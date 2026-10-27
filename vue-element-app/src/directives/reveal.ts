import type { Directive } from 'vue'

export type RevealVariant = 'up' | 'scale' | 'left' | 'right' | 'blur' | 'depth'

interface RevealOptions {
  /** 延迟毫秒数，用于同区块内错开 */
  delay?: number
  /** 跳过 IntersectionObserver，立即显示 */
  immediate?: boolean
  /** 入场形态，默认 up */
  variant?: RevealVariant
}

type RevealElement = HTMLElement & { __revealObserver?: IntersectionObserver }

/**
 * v-reveal：进入视口时添加 .is-visible，配合全局 .reveal 过渡做滚动揭示。
 * 用法：v-reveal / v-reveal="{ delay: 120 }" / v-reveal="{ variant: 'scale' }"
 */
export const reveal: Directive<RevealElement, RevealOptions | undefined> = {
  mounted(el, binding) {
    el.classList.add('reveal')

    const { delay = 0, immediate = false, variant = 'up' } = binding.value ?? {}

    if (delay) {
      el.style.setProperty('--reveal-delay', `${delay}ms`)
    }

    if (variant !== 'up') {
      el.dataset.reveal = variant
    }

    if (immediate || typeof IntersectionObserver === 'undefined') {
      el.classList.add('is-visible')
      return
    }

    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            el.classList.add('is-visible')
            observer.disconnect()
          }
        }
      },
      { threshold: 0.12, rootMargin: '0px 0px -32px' },
    )

    observer.observe(el)
    el.__revealObserver = observer
  },

  unmounted(el) {
    el.__revealObserver?.disconnect()
    delete el.__revealObserver
  },
}
