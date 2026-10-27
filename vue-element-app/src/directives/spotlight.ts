import type { Directive } from 'vue'

type SpotlightElement = HTMLElement & { __spotlightCleanup?: () => void }

/**
 * v-spotlight：让卡片跟随指针出现一层柔和光斑。
 * 只写入 CSS 变量，样式由全局 .spotlight 负责，卡片可自由覆盖。
 * 用法：<article v-spotlight> 或 v-spotlight="false" 关闭。
 *
 * 性能处理：
 * - 进入卡片时量一次 rect 并缓存，pointermove 只做减法，不再触发同步布局；
 * - 滚动 / 缩放后缓存失效，下一次移动再量；
 * - 同一帧内多次移动合并成一次样式写入。
 */
export const spotlight: Directive<SpotlightElement, boolean | undefined> = {
  mounted(el, binding) {
    if (binding.value === false) return

    el.classList.add('spotlight')

    let rect: DOMRect | null = null
    let pendingX = 0
    let pendingY = 0
    let frame = 0

    const flush = (): void => {
      frame = 0
      el.style.setProperty('--spot-x', `${pendingX}px`)
      el.style.setProperty('--spot-y', `${pendingY}px`)
    }

    const move = (event: PointerEvent): void => {
      // 缓存命中时这里没有任何布局读取
      if (!rect) rect = el.getBoundingClientRect()
      pendingX = event.clientX - rect.left
      pendingY = event.clientY - rect.top
      if (!frame) frame = window.requestAnimationFrame(flush)
    }

    const invalidate = (): void => {
      rect = null
    }

    const enter = (): void => {
      rect = el.getBoundingClientRect()
      el.classList.add('is-spotlit')
    }

    const leave = (): void => {
      rect = null
      el.classList.remove('is-spotlit')
    }

    el.addEventListener('pointerenter', enter)
    el.addEventListener('pointermove', move, { passive: true })
    el.addEventListener('pointerleave', leave)

    // 滚动可能来自页面或任意内层容器，用捕获阶段统一监听
    window.addEventListener('scroll', invalidate, { passive: true, capture: true })
    window.addEventListener('resize', invalidate, { passive: true })

    el.__spotlightCleanup = () => {
      el.removeEventListener('pointerenter', enter)
      el.removeEventListener('pointermove', move)
      el.removeEventListener('pointerleave', leave)
      window.removeEventListener('scroll', invalidate, true)
      window.removeEventListener('resize', invalidate)
      if (frame) window.cancelAnimationFrame(frame)
      frame = 0
    }
  },

  unmounted(el) {
    el.__spotlightCleanup?.()
    delete el.__spotlightCleanup
  },
}
