<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = withDefaults(
  defineProps<{
    labels?: string[]
    values?: number[]
    color?: string
    height?: number
    suffix?: string
    /** 放在后台暖纸面板里：网格与坐标文字换成更轻的暖灰 */
    panel?: boolean
    /** 实时模式：数据点会缓慢呼吸，末端带脉冲光点与扫描高光 */
    live?: boolean
  }>(),
  {
    labels: () => [],
    values: () => [],
    color: '#88686e',
    height: 220,
    suffix: '',
    panel: false,
    live: false,
  },
)

const W = 600
const H = 200
const PAD_TOP = 12
const PAD_BOTTOM = 8
const uid = `area-${Math.random().toString(36).slice(2, 8)}`
const glowId = `${uid}-glow`
const strokeId = `${uid}-stroke`

// ---- 数据：外部值作为基线，实时模式在其附近轻微呼吸 ----
const animated = ref<number[]>([])
const target = ref<number[]>([])

watch(
  () => props.values,
  (values) => {
    target.value = [...values]
    if (!animated.value.length) {
      animated.value = [...values]
      return
    }
    animateTo([...values])
  },
  { immediate: true, deep: true },
)

let frame = 0
let timer = 0

function animateTo(next: number[]): void {
  if (!next.length) return
  const from = animated.value.length ? [...animated.value] : [...next]
  const start = performance.now()
  const duration = 760
  cancelAnimationFrame(frame)

  let lastPaint = 0
  const step = (now: number): void => {
    const t = Math.min(1, (now - start) / duration)

    // 补间按 ~30fps 提交：路径重算 + DOM diff 是这里最贵的部分，
    // 曲线本身变化很缓，砍掉一半帧肉眼几乎看不出来。
    if (t < 1 && now - lastPaint < 32) {
      frame = requestAnimationFrame(step)
      return
    }
    lastPaint = now

    const eased = 1 - Math.pow(1 - t, 3)
    animated.value = next.map((value, index) => {
      const prev = from[index] ?? value
      return prev + (value - prev) * eased
    })
    if (t < 1) frame = requestAnimationFrame(step)
  }

  frame = requestAnimationFrame(step)
}

function liveTick(): void {
  const base = target.value
  if (!base.length) return
  const n = base.length
  const next = base.map((value, index) => {
    // 只让最后三个点轻微浮动，前面保持稳定，读起来才不像随机噪声
    if (index < n - 3) return value
    const amplitude = Math.max(0.05, Math.abs(value) * 0.012)
    return Math.max(0, value + (Math.random() - 0.5) * amplitude * 2)
  })
  animateTo(next)
}

onMounted(() => {
  if (props.live) timer = window.setInterval(liveTick, 2600)
  window.addEventListener('scroll', invalidatePlotRect, { passive: true, capture: true })
  window.addEventListener('resize', invalidatePlotRect, { passive: true })
})

onBeforeUnmount(() => {
  cancelAnimationFrame(frame)
  window.clearInterval(timer)
  window.removeEventListener('scroll', invalidatePlotRect, true)
  window.removeEventListener('resize', invalidatePlotRect)
  if (hoverFrame) window.cancelAnimationFrame(hoverFrame)
})

// 量程按传入值固定，实时浮动只在曲线内部呼吸，刻度不会跟着跳
const max = computed(() => Math.ceil(Math.max(...props.values, 1) * 1.18))

const points = computed<[number, number][]>(() => {
  const values = animated.value
  if (values.length < 2) return []
  const usable = H - PAD_TOP - PAD_BOTTOM
  return values.map((value, index) => {
    const x = (index / (values.length - 1)) * W
    const y = PAD_TOP + (1 - value / max.value) * usable
    return [Number(x.toFixed(2)), Number(y.toFixed(2))]
  })
})

// Catmull-Rom → 三次贝塞尔，让折线变成柔和的呼吸曲线
const linePath = computed(() => {
  const pts = points.value
  if (!pts.length) return ''
  if (pts.length === 2) return `M${pts[0][0]},${pts[0][1]} L${pts[1][0]},${pts[1][1]}`

  let d = `M${pts[0][0]},${pts[0][1]}`
  for (let index = 0; index < pts.length - 1; index += 1) {
    const p0 = pts[index - 1] ?? pts[index]
    const p1 = pts[index]
    const p2 = pts[index + 1]
    const p3 = pts[index + 2] ?? p2
    const c1x = p1[0] + (p2[0] - p0[0]) / 6
    const c1y = p1[1] + (p2[1] - p0[1]) / 6
    const c2x = p2[0] - (p3[0] - p1[0]) / 6
    const c2y = p2[1] - (p3[1] - p1[1]) / 6
    d += ` C${c1x.toFixed(2)},${c1y.toFixed(2)} ${c2x.toFixed(2)},${c2y.toFixed(2)} ${p2[0]},${p2[1]}`
  }
  return d
})

const areaPath = computed(() => {
  if (!linePath.value) return ''
  const pts = points.value
  const start = pts[0]
  const end = pts[pts.length - 1]
  return `${linePath.value} L${end[0]},${H} L${start[0]},${H} Z`
})

const gridLines = [0, 1, 2, 3].map((index) => PAD_TOP + ((H - PAD_TOP - PAD_BOTTOM) / 3) * index)

// 坐标刻度：小量程保留一位小数（3.3 / 1.7），大量程取整到 50，避免出现 1303 这种读不动的数字
const yTicks = computed(() =>
  [0, 1, 2, 3].map((index) => {
    const value = max.value - (max.value / 3) * index
    if (max.value <= 10) return String(Number(value.toFixed(1)))
    if (value >= 1000) return String(Math.round(value / 50) * 50)
    return String(Math.round(value / 10) * 10)
  }),
)

const step = computed(() => Math.max(1, Math.ceil(props.labels.length / 8)))

const lastPoint = computed(() => points.value[points.value.length - 1])

// ---- 悬停读数 ----
const plotRef = ref<HTMLElement | null>(null)
const hover = ref<{ index: number; x: number; y: number } | null>(null)

// 进入绘图区时量一次 rect，移动过程只做减法；按帧合并多次 pointermove
let plotRect: DOMRect | null = null
let hoverFrame = 0
let pendingX = 0

function measurePlot(): void {
  plotRect = plotRef.value?.getBoundingClientRect() ?? null
}

function invalidatePlotRect(): void {
  plotRect = null
}

function applyHover(): void {
  hoverFrame = 0
  if (!plotRect || !points.value.length) return

  const ratio = Math.min(1, Math.max(0, (pendingX - plotRect.left) / plotRect.width))
  const index = Math.round(ratio * (points.value.length - 1))
  const point = points.value[index]
  if (!point) return
  hover.value = { index, x: point[0], y: point[1] }
}

function onEnter(): void {
  measurePlot()
}

function onMove(event: PointerEvent): void {
  if (!plotRect) measurePlot()
  pendingX = event.clientX
  if (!hoverFrame) hoverFrame = window.requestAnimationFrame(applyHover)
}

function clearHover(): void {
  plotRect = null
  if (hoverFrame) {
    window.cancelAnimationFrame(hoverFrame)
    hoverFrame = 0
  }
  hover.value = null
}

const hoverLabel = computed(() => (hover.value ? props.labels[hover.value.index] ?? '' : ''))
const hoverValue = computed(() => {
  if (!hover.value) return ''
  const value = animated.value[hover.value.index] ?? 0
  return `${value.toFixed(props.suffix.includes('分') ? 1 : 0)}${props.suffix}`
})
</script>

<template>
  <div
    class="area"
    :class="{ 'area--panel': panel, 'area--live': live }"
    :style="{ height: `${height}px`, '--tip-color': color }"
  >
    <div class="area__axis">
      <span v-for="(tick, index) in yTicks" :key="index">{{ tick }}{{ suffix }}</span>
    </div>

    <div
      ref="plotRef"
      class="area__plot"
      @pointerenter="onEnter"
      @pointermove="onMove"
      @pointerleave="clearHover"
    >
      <svg :viewBox="`0 0 ${W} ${H}`" preserveAspectRatio="none" aria-hidden="true">
        <defs>
          <linearGradient :id="uid" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" :stop-color="color" :stop-opacity="panel ? 0.3 : 0.26" />
            <stop offset="58%" :stop-color="color" :stop-opacity="panel ? 0.09 : 0.07" />
            <stop offset="100%" :stop-color="color" stop-opacity="0" />
          </linearGradient>

          <linearGradient :id="strokeId" x1="0" y1="0" x2="1" y2="0">
            <stop offset="0%" :stop-color="color" stop-opacity="0.55" />
            <stop offset="48%" :stop-color="color" stop-opacity="1" />
            <stop offset="100%" :stop-color="color" stop-opacity="0.9" />
          </linearGradient>

          <filter :id="glowId" x="-20%" y="-40%" width="140%" height="180%">
            <feGaussianBlur stdDeviation="4.5" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <line
          v-for="(y, index) in gridLines"
          :key="index"
          x1="0"
          :y1="y"
          :x2="W"
          :y2="y"
          :stroke="panel ? 'var(--warm-line)' : 'rgba(40, 24, 27, 0.09)'"
          stroke-dasharray="3 7"
          vector-effect="non-scaling-stroke"
        />

        <path :d="areaPath" :fill="`url(#${uid})`" class="area__fill" />

        <path
          :d="linePath"
          fill="none"
          :stroke="color"
          stroke-width="5"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
          opacity="0.22"
          :filter="`url(#${glowId})`"
          class="area__glow"
        />
        <path
          :d="linePath"
          fill="none"
          :stroke="`url(#${strokeId})`"
          stroke-width="2.4"
          stroke-linecap="round"
          stroke-linejoin="round"
          vector-effect="non-scaling-stroke"
          class="area__line"
        />

        <!-- 悬停十字线：帮助读数，不干扰主曲线 -->
        <g v-if="hover" class="area__cross">
          <line
            :x1="hover.x"
            :y1="PAD_TOP"
            :x2="hover.x"
            :y2="H - PAD_BOTTOM"
            :stroke="color"
            stroke-opacity="0.32"
            stroke-dasharray="3 5"
            vector-effect="non-scaling-stroke"
          />
          <circle :cx="hover.x" :cy="hover.y" r="4.5" :fill="color" stroke="var(--warm-paper)" stroke-width="2" />
        </g>
      </svg>

      <span
        v-if="lastPoint && !hover"
        class="area__tip"
        :style="{
          left: `${(lastPoint[0] / W) * 100}%`,
          top: `${(lastPoint[1] / H) * 100}%`,
          '--tip-color': color,
        }"
        aria-hidden="true"
      >
        <i class="area__tip-halo" />
        <i class="area__tip-core" />
      </span>

      <div
        v-if="hover"
        class="area__readout"
        :class="{ 'is-flip': hover.x / W > 0.72 }"
        :style="{ left: `${(hover.x / W) * 100}%`, top: `${(hover.y / H) * 100}%` }"
      >
        <span>{{ hoverLabel }}</span>
        <strong>{{ hoverValue }}</strong>
      </div>

      <span v-if="live" class="area__live" aria-hidden="true">
        <i />
        实时
      </span>
    </div>

    <div class="area__labels">
      <span v-for="(label, index) in labels" :key="index">
        {{ index % step === 0 ? label : '' }}
      </span>
    </div>
  </div>
</template>

<style scoped lang="scss">
.area {
  display: grid;
  grid-template-columns: auto 1fr;
  grid-template-rows: 1fr auto;
  gap: 8px 14px;
}

.area__axis {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 6px 0 22px;
  color: var(--warm-text-muted);
  font-size: 11px;
  font-variant-numeric: tabular-nums;
  text-align: right;
}

.area__plot {
  position: relative;
  min-width: 0;
  touch-action: pan-y;
  cursor: crosshair;

  svg {
    display: block;
    width: 100%;
    height: 100%;
  }
}

.area__fill {
  opacity: 0;
  animation: area-fade 1s 0.16s $ease-out-expo forwards;
}

.area__line {
  stroke-dasharray: 1;
  stroke-dashoffset: 1;
  animation: draw 1.5s 0.12s $ease-out-expo forwards;
  filter: drop-shadow(0 3px 6px color-mix(in srgb, var(--tip-color, #88686e) 26%, transparent));
}

.area__glow {
  opacity: 0.28;
  animation: area-fade 1.2s 0.3s $ease-out-expo both;
}

.area__cross {
  pointer-events: none;
}

// 折线末端的发光点，让「当前值」有一个视觉落点
.area__tip {
  position: absolute;
  width: 0;
  height: 0;
  pointer-events: none;

  &-core,
  &-halo {
    position: absolute;
    top: 0;
    left: 0;
    border-radius: 50%;
    transform: translate(-50%, -50%);
  }

  &-core {
    width: 9px;
    height: 9px;
    border: 2px solid var(--warm-paper);
    background: var(--tip-color, var(--warm-brand));
    box-shadow:
      0 0 0 4px color-mix(in srgb, var(--tip-color, var(--warm-brand)) 16%, transparent),
      0 6px 14px -6px color-mix(in srgb, var(--tip-color, var(--warm-brand)) 70%, transparent);
  }

  &-halo {
    width: 9px;
    height: 9px;
    border: 1px solid var(--tip-color, var(--warm-brand));
    animation: tip-pulse 2.4s $ease-out infinite;
  }
}

.area__readout {
  position: absolute;
  z-index: 3;
  display: grid;
  gap: 1px;
  min-width: 84px;
  padding: 7px 10px;
  border: 1px solid var(--warm-line);
  border-radius: 10px;
  background: var(--warm-paper-96);
  box-shadow: 0 14px 30px -20px var(--warm-shadow);
  transform: translate(12px, calc(-100% - 10px));
  pointer-events: none;

  span {
    color: var(--warm-text-muted);
    font-size: 10px;
    letter-spacing: 0.06em;
    white-space: nowrap;
  }

  strong {
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 14px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }

  &.is-flip {
    transform: translate(calc(-100% - 12px), calc(-100% - 10px));
  }
}

.area__live {
  position: absolute;
  top: -6px;
  right: 0;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 3px 8px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 30%, transparent);
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  color: var(--warm-brand-strong);
  font-size: 9.5px;
  font-weight: 600;
  letter-spacing: 0.12em;

  i {
    width: 5px;
    height: 5px;
    border-radius: 50%;
    background: var(--warm-brand);
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--warm-brand) 70%, transparent);
    animation: live-ping 2s ease-out infinite;
  }
}

.area__labels {
  grid-column: 2;
  display: flex;
  justify-content: space-between;
  color: var(--warm-text-muted);
  font-size: 11px;

  span {
    flex: 1;
    text-align: center;
    white-space: nowrap;
  }
}

.area--panel {
  .area__axis,
  .area__labels {
    color: var(--warm-text-muted);
    font-size: 10.5px;
    letter-spacing: 0.02em;
  }

  .area__axis {
    padding-right: 2px;
  }
}

@keyframes area-fade {
  to {
    opacity: 1;
  }
}

@keyframes tip-pulse {
  0% {
    transform: translate(-50%, -50%) scale(0.8);
    opacity: 0.9;
  }

  70%,
  100% {
    transform: translate(-50%, -50%) scale(2.6);
    opacity: 0;
  }
}

@keyframes live-ping {
  0% {
    box-shadow: 0 0 0 0 color-mix(in srgb, var(--warm-brand) 60%, transparent);
  }

  70% {
    box-shadow: 0 0 0 6px transparent;
  }

  100% {
    box-shadow: 0 0 0 0 transparent;
  }
}

@media (prefers-reduced-motion: reduce) {
  .area__line {
    animation: none;
    stroke-dashoffset: 0;
  }

  .area__tip-halo,
  .area__live i {
    animation: none;
  }
}
</style>
