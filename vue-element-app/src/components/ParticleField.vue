<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 全屏粒子星图：缓慢漂浮的微光粒子 + 近距连线 + 指针轻微扰动。
 * 只作为氛围层存在（pointer-events: none），颜色从 CSS 变量读取，
 * 因此日间 / 夜间主题切换时会自动换色。
 *
 * 性能处理：
 * - 最大粒子数 62，连线距离随视口宽度收窄；
 * - 20fps 渲染，高位像素比封顶 1.5，页面隐藏时暂停；
 * - 页面滚动期间不绘制（背景是 fixed 的，滚动时本来也看不清），
 *   滚动停下 160ms 后恢复，避免和滚动合成抢帧；
 * - prefers-reduced-motion 下只绘制一帧静态星图。
 */
interface Props {
  /** 每平方像素的粒子密度，默认 0.000045 */
  density?: number
  /** 安静模式：更淡、更少，用于登录页 / 后台 */
  quiet?: boolean
  /** 是否响应指针，触屏设备自动忽略 */
  interactive?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  density: 0.000045,
  quiet: false,
  interactive: true,
})

interface Particle {
  x: number
  y: number
  vx: number
  vy: number
  radius: number
  alpha: number
  alt: boolean
  phase: number
}

const canvasRef = ref<HTMLCanvasElement | null>(null)

let ctx: CanvasRenderingContext2D | null = null
let particles: Particle[] = []
let width = 0
let height = 0
let dpr = 1
let frame = 0
let running = false
let lastPaint = 0
let resizeFrame = 0
let reduced = false
let visible = true
let pointerActive = false
let scrolling = false
let scrollIdleTimer = 0
const pointer = { x: -9999, y: -9999 }
const colors = {
  dot: 'rgba(117, 66, 79, 0.55)',
  dotAlt: 'rgba(196, 143, 154, 0.42)',
  line: 'rgba(117, 66, 79, 0.14)',
}

let motionQuery: MediaQueryList | null = null
let themeObserver: MutationObserver | null = null

function readVar(name: string, fallback: string): string {
  if (typeof window === 'undefined') return fallback
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim()
  return value || fallback
}

function syncColors(): void {
  colors.dot = readVar('--fx-particle', colors.dot)
  colors.dotAlt = readVar('--fx-particle-alt', colors.dotAlt)
  colors.line = readVar('--fx-line', colors.line)
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value))
}

function seedParticles(): void {
  const base = clamp(Math.round(width * height * props.density), 16, props.quiet ? 34 : 62)
  const target = props.quiet ? Math.round(base * 0.62) : base

  particles = Array.from({ length: target }, () => {
    const speed = props.quiet ? 0.08 : 0.14
    return {
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * speed,
      vy: (Math.random() - 0.5) * speed,
      radius: Math.random() * 1.5 + 0.6,
      alpha: (props.quiet ? 0.22 : 0.28) + Math.random() * (props.quiet ? 0.26 : 0.44),
      alt: Math.random() > 0.78,
      phase: Math.random() * Math.PI * 2,
    }
  })
}

function resize(): void {
  const canvas = canvasRef.value
  if (!canvas) return

  width = window.innerWidth
  height = window.innerHeight
  // 2 倍图在这个尺寸的全屏画布上几乎看不出差别，却要多画一倍像素
  dpr = Math.min(window.devicePixelRatio || 1, 1.5)

  canvas.width = Math.round(width * dpr)
  canvas.height = Math.round(height * dpr)
  canvas.style.width = `${width}px`
  canvas.style.height = `${height}px`

  ctx = canvas.getContext('2d')
  if (!ctx) return

  ctx.setTransform(dpr, 0, 0, dpr, 0, 0)
  seedParticles()

  if (reduced) paint(true)
}

function paint(staticFrame = false): void {
  if (!ctx) return
  ctx.clearRect(0, 0, width, height)

  const linkDistance = width < 680 ? 92 : 128
  const linkDistanceSq = linkDistance * linkDistance
  const glow = props.quiet ? 0.6 : 1

  for (let i = 0; i < particles.length; i += 1) {
    const particle = particles[i]
    if (!particle) continue

    if (!staticFrame) {
      particle.phase += 0.008
      particle.x += particle.vx + Math.cos(particle.phase) * 0.035
      particle.y += particle.vy + Math.sin(particle.phase * 0.8) * 0.03

      if (pointerActive && props.interactive) {
        const dx = particle.x - pointer.x
        const dy = particle.y - pointer.y
        const distanceSq = dx * dx + dy * dy
        const reach = 168

        if (distanceSq < reach * reach && distanceSq > 1) {
          const distance = Math.sqrt(distanceSq)
          const force = (1 - distance / reach) * 0.035
          particle.vx += (dx / distance) * force
          particle.vy += (dy / distance) * force
        }
      }

      // 阻尼，避免粒子被指针越推越快
      particle.vx *= 0.992
      particle.vy *= 0.992

      if (particle.x < -12) particle.x = width + 12
      if (particle.x > width + 12) particle.x = -12
      if (particle.y < -12) particle.y = height + 12
      if (particle.y > height + 12) particle.y = -12
    }

    const twinkle = staticFrame ? 1 : 0.78 + Math.sin(particle.phase * 1.6) * 0.22

    ctx.globalAlpha = particle.alpha * twinkle * glow
    ctx.fillStyle = particle.alt ? colors.dotAlt : colors.dot
    ctx.beginPath()
    ctx.arc(particle.x, particle.y, particle.radius, 0, Math.PI * 2)
    ctx.fill()

    // 少量大粒子加一圈极淡光晕，远看像星尘而不是噪点
    if (particle.radius > 1.65) {
      ctx.globalAlpha = particle.alpha * 0.12 * glow
      ctx.beginPath()
      ctx.arc(particle.x, particle.y, particle.radius * 4.2, 0, Math.PI * 2)
      ctx.fill()
    }
  }

  // 近距连线：形成若有若无的星图
  ctx.lineWidth = 0.7
  ctx.strokeStyle = colors.line

  for (let i = 0; i < particles.length; i += 1) {
    const a = particles[i]
    if (!a) continue

    for (let j = i + 1; j < particles.length; j += 1) {
      const b = particles[j]
      if (!b) continue

      const dx = a.x - b.x
      const dy = a.y - b.y
      const distanceSq = dx * dx + dy * dy
      if (distanceSq > linkDistanceSq) continue

      ctx.globalAlpha = (1 - distanceSq / linkDistanceSq) * (props.quiet ? 0.18 : 0.3)
      ctx.beginPath()
      ctx.moveTo(a.x, a.y)
      ctx.lineTo(b.x, b.y)
      ctx.stroke()
    }
  }

  ctx.globalAlpha = 1
}

function tick(time: number): void {
  if (!running) return

  // 20fps 足够表现缓慢漂浮，同时把 CPU / GPU 占用压下来。
  // 滚动期间整帧跳过：粒子在 fixed 画布上，滚动时看不出停顿，
  // 但省下的绘制时间会让主线程的滚动合成明显更稳。
  if (!scrolling && time - lastPaint >= 50) {
    lastPaint = time
    paint()
  }

  frame = window.requestAnimationFrame(tick)
}

function start(): void {
  if (running || reduced || !visible) return
  running = true
  lastPaint = 0
  frame = window.requestAnimationFrame(tick)
}

function stop(): void {
  running = false
  if (frame) {
    window.cancelAnimationFrame(frame)
    frame = 0
  }
}

function onPointerMove(event: PointerEvent): void {
  if (event.pointerType === 'touch') return
  pointer.x = event.clientX
  pointer.y = event.clientY
  pointerActive = true
}

function onPointerLeave(): void {
  pointerActive = false
}

// 捕获阶段监听整页（含聊天列表等内部滚动容器）的滚动
function onPageScroll(): void {
  scrolling = true
  window.clearTimeout(scrollIdleTimer)
  scrollIdleTimer = window.setTimeout(() => {
    scrolling = false
  }, 160)
}

function onResize(): void {
  if (resizeFrame) return
  resizeFrame = window.requestAnimationFrame(() => {
    resizeFrame = 0
    resize()
  })
}

function onVisibilityChange(): void {
  visible = !document.hidden
  if (visible) {
    syncColors()
    start()
  } else {
    stop()
  }
}

function onMotionChange(event: MediaQueryListEvent): void {
  reduced = event.matches
  if (reduced) {
    stop()
    paint(true)
  } else {
    start()
  }
}

onMounted(() => {
  motionQuery = window.matchMedia('(prefers-reduced-motion: reduce)')
  reduced = motionQuery.matches

  syncColors()
  resize()

  if (reduced) {
    paint(true)
  } else {
    start()
  }

  window.addEventListener('resize', onResize, { passive: true })
  document.addEventListener('scroll', onPageScroll, { passive: true, capture: true })
  document.addEventListener('visibilitychange', onVisibilityChange)
  motionQuery.addEventListener('change', onMotionChange)

  if (props.interactive && window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
    window.addEventListener('pointermove', onPointerMove, { passive: true })
    document.addEventListener('pointerleave', onPointerLeave)
  }

  // 主题切换只改 data-theme，颜色需要重新读取一次
  themeObserver = new MutationObserver(() => {
    syncColors()
    if (reduced) paint(true)
  })
  themeObserver.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['data-theme'],
  })
})

onBeforeUnmount(() => {
  stop()
  themeObserver?.disconnect()
  themeObserver = null
  motionQuery?.removeEventListener('change', onMotionChange)
  window.removeEventListener('resize', onResize)
  document.removeEventListener('scroll', onPageScroll, { capture: true })
  document.removeEventListener('visibilitychange', onVisibilityChange)
  window.clearTimeout(scrollIdleTimer)
  scrolling = false
  window.removeEventListener('pointermove', onPointerMove)
  document.removeEventListener('pointerleave', onPointerLeave)
  if (resizeFrame) window.cancelAnimationFrame(resizeFrame)
  particles = []
})
</script>

<template>
  <canvas
    ref="canvasRef"
    class="particle-field"
    :class="{ 'particle-field--quiet': quiet }"
    aria-hidden="true"
  />
</template>

<style scoped lang="scss">
.particle-field {
  position: fixed;
  inset: 0;
  z-index: 0;
  width: 100%;
  height: 100%;
  opacity: 0.85;
  pointer-events: none;
}

.particle-field--quiet {
  opacity: 0.55;
}

@media (prefers-reduced-motion: reduce) {
  .particle-field {
    opacity: 0.6;
  }
}
</style>
