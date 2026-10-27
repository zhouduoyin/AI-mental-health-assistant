import type { Directive } from 'vue'

export interface ParallaxOptions {
  /** 视差强度（px）：正值向下、负值向上；建议 10–60 */
  speed?: number
  /** 位移轴，默认 y */
  axis?: 'x' | 'y'
}

interface ParallaxEntry {
  el: HTMLElement
  speed: number
  axis: 'x' | 'y'
}

type ParallaxElement = HTMLElement & { __parallaxSpeed?: number }

const entries = new Set<ParallaxEntry>()
let frame = 0

function update(): void {
  frame = 0
  const viewport = window.innerHeight || 1
  // 先读完所有 rect，再统一写样式：读 / 写混在一起会让每次写后都强制同步布局
  const measured: { entry: ParallaxEntry; progress: number }[] = []

  for (const entry of entries) {
    const rect = entry.el.getBoundingClientRect()
    // 元素中心相对视口中心的位置：-1（在上方）→ 1（在下方）
    measured.push({ entry, progress: (viewport / 2 - (rect.top + rect.height / 2)) / viewport })
  }

  for (const { entry, progress } of measured) {
    const offset = Math.max(-120, Math.min(120, progress * entry.speed))
    const value = `${offset.toFixed(2)}px`

    if (entry.axis === 'x') {
      entry.el.style.setProperty('--parallax-x', value)
      entry.el.style.setProperty('--parallax-y', '0px')
    } else {
      entry.el.style.setProperty('--parallax-y', value)
      entry.el.style.setProperty('--parallax-x', '0px')
    }
  }
}

function queue(): void {
  if (frame) return
  frame = window.requestAnimationFrame(update)
}

function start(): void {
  window.addEventListener('scroll', queue, { passive: true })
  window.addEventListener('resize', queue, { passive: true })
  queue()
}

function stop(): void {
  window.removeEventListener('scroll', queue)
  window.removeEventListener('resize', queue)
  if (frame) {
    window.cancelAnimationFrame(frame)
    frame = 0
  }
}

function prefersReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  )
}

/**
 * v-parallax：滚动时写入 --parallax-x / --parallax-y，由元素自己的 CSS 消费。
 * 只写变量、不直接改 transform，避免和 v-tilt / v-reveal 的 transform 冲突。
 *
 * 用法：
 * <div v-parallax="{ speed: 24 }" class="orb" />
 * .orb { translate: var(--parallax-x, 0) var(--parallax-y, 0); }
 */
export const parallax: Directive<ParallaxElement, ParallaxOptions | undefined> = {
  mounted(el, binding) {
    if (prefersReducedMotion()) {
      el.style.setProperty('--parallax-x', '0px')
      el.style.setProperty('--parallax-y', '0px')
      return
    }

    const entry: ParallaxEntry = {
      el,
      speed: binding.value?.speed ?? 24,
      axis: binding.value?.axis ?? 'y',
    }

    entries.add(entry)
    el.__parallaxSpeed = entry.speed

    if (entries.size === 1) start()
    else queue()
  },

  unmounted(el) {
    for (const entry of entries) {
      if (entry.el === el) entries.delete(entry)
    }

    delete el.__parallaxSpeed
    if (entries.size === 0) stop()
  },
}
