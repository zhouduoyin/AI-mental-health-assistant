import type { Directive } from 'vue'

export interface TiltOptions {
  /** 最大旋转角度，默认 5deg */
  max?: number
  /** 指针移动时的跟随缓动（毫秒），默认 120 */
  follow?: number
}

type TiltElement = HTMLElement & { __tiltCleanup?: () => void }

const canTilt = (): boolean =>
  typeof window !== 'undefined' &&
  window.matchMedia('(hover: hover) and (pointer: fine)').matches &&
  !window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * v-tilt：给容器一层轻微 3D 倾斜，跟随指针移动。
 * 只用于首屏主视觉这类「大件」，不要给列表项批量使用。
 * 用法：v-tilt="{ max: 5 }"
 *
 * 性能处理：
 * - 进入容器时缓存 rect，移动过程中不再读布局；
 * - 同一帧只写一次 transform，且 transition 只在动画开始时设一次；
 * - 只改 transform，不碰宽高 / 定位，交给合成层。
 */
export const tilt: Directive<TiltElement, TiltOptions | undefined> = {
  mounted(el, binding) {
    if (!canTilt()) return

    const max = binding.value?.max ?? 5
    const follow = binding.value?.follow ?? 120

    el.style.transformStyle = 'preserve-3d'
    el.style.willChange = 'transform'
    el.style.transition = 'transform 700ms cubic-bezier(0.16, 1, 0.3, 1)'

    let rect: DOMRect | null = null
    let pending: string | null = null
    let frame = 0
    let following = false

    const flush = (): void => {
      frame = 0
      // 连续移动期间复用线性过渡；松手时再切回慢速回正
      if (!following) {
        following = true
        el.style.transition = `transform ${follow}ms linear`
      }
      if (pending) el.style.transform = pending
    }

    const measure = (): void => {
      rect = el.getBoundingClientRect()
    }

    const invalidate = (): void => {
      rect = null
    }

    const move = (event: PointerEvent): void => {
      if (!rect) measure()
      if (!rect) return

      const px = (event.clientX - rect.left) / rect.width - 0.5
      const py = (event.clientY - rect.top) / rect.height - 0.5
      pending = `perspective(1100px) rotateX(${(-py * max * 2).toFixed(2)}deg) rotateY(${(px * max * 2).toFixed(2)}deg)`
      if (!frame) frame = window.requestAnimationFrame(flush)
    }

    const leave = (): void => {
      if (frame) {
        window.cancelAnimationFrame(frame)
        frame = 0
      }
      following = false
      rect = null
      pending = null
      el.style.transition = 'transform 700ms cubic-bezier(0.16, 1, 0.3, 1)'
      el.style.transform = 'perspective(1100px) rotateX(0deg) rotateY(0deg)'
    }

    el.addEventListener('pointerenter', measure)
    el.addEventListener('pointermove', move, { passive: true })
    el.addEventListener('pointerleave', leave)
    window.addEventListener('scroll', invalidate, { passive: true, capture: true })
    window.addEventListener('resize', invalidate, { passive: true })

    el.__tiltCleanup = () => {
      el.removeEventListener('pointerenter', measure)
      el.removeEventListener('pointermove', move)
      el.removeEventListener('pointerleave', leave)
      window.removeEventListener('scroll', invalidate, true)
      window.removeEventListener('resize', invalidate)
      if (frame) window.cancelAnimationFrame(frame)
      frame = 0
    }
  },

  unmounted(el) {
    el.__tiltCleanup?.()
    delete el.__tiltCleanup
  },
}
