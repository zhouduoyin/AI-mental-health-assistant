<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import AppIcon from '@/components/AppIcon.vue'
import PageHeader from '@/components/PageHeader.vue'

/**
 * 性能实验室：把三条原则做成可运行的对照实验。
 * 1. 批量 DOM：逐个 appendChild vs DocumentFragment 一次性插入
 * 2. 动画：left / top vs transform（是否只走合成层）
 * 3. 布局读取：每帧 getBoundingClientRect vs 进入时缓存
 *
 * 页面本身也遵守这些原则：演示节点全部离线构建后一次挂载，
 * 指针跟随只写 transform，滚动 / 缩放时才让缓存失效。
 */

// ============ 实验一：批量 DOM ============
type BatchMode = 'loop' | 'fragment'

interface BatchResult {
  ms: number
  nodes: number
}

const batchSizes = [1000, 3000, 6000]
const batchSize = ref(3000)
const batchRunning = ref<BatchMode | null>(null)
const batchStage = ref<HTMLElement | null>(null)
const batchResults = ref<Record<BatchMode, BatchResult | null>>({ loop: null, fragment: null })

const batchMax = computed(() =>
  Math.max(batchResults.value.loop?.ms ?? 0, batchResults.value.fragment?.ms ?? 0, 1),
)

function makeChip(index: number): HTMLElement {
  const chip = document.createElement('span')
  chip.className = 'lab-chip'
  chip.textContent = String(index + 1)
  return chip
}

function runBatch(mode: BatchMode): void {
  const stage = batchStage.value
  if (!stage || batchRunning.value) return

  stage.replaceChildren()
  batchRunning.value = mode

  // 先让清空落到一帧，再把插入成本单独计时
  window.requestAnimationFrame(() => {
    const size = batchSize.value
    const startedAt = performance.now()

    if (mode === 'fragment') {
      const fragment = document.createDocumentFragment()
      for (let index = 0; index < size; index += 1) fragment.appendChild(makeChip(index))
      stage.appendChild(fragment)
    } else {
      for (let index = 0; index < size; index += 1) stage.appendChild(makeChip(index))
    }

    // 读一次尺寸，把样式与布局成本一起算进来，模拟真实首屏
    void stage.offsetHeight
    const ms = performance.now() - startedAt

    const next = { ...batchResults.value }
    next[mode] = { ms, nodes: size }
    batchResults.value = next
    batchRunning.value = null
  })
}

function clearBatch(): void {
  batchStage.value?.replaceChildren()
  batchResults.value = { loop: null, fragment: null }
}

function batchBar(mode: BatchMode): number {
  const result = batchResults.value[mode]
  if (!result) return 0
  return Math.max(4, (result.ms / batchMax.value) * 100)
}

function batchBest(mode: BatchMode): boolean {
  const { loop, fragment } = batchResults.value
  if (!loop || !fragment) return false
  return mode === 'loop' ? loop.ms <= fragment.ms : fragment.ms <= loop.ms
}

// ============ 实验二：合成层动画 ============
type AnimMode = 'layout' | 'composite'

interface AnimResult {
  fps: number
  frames: number
  dropped: number
}

const TILE_COUNT = 60
const animStage = ref<HTMLElement | null>(null)
const animRunning = ref<AnimMode | null>(null)
const animResults = ref<Record<AnimMode, AnimResult | null>>({ layout: null, composite: null })

let tileAnimations: Animation[] = []
let sampleFrame = 0

function buildTiles(): void {
  const stage = animStage.value
  if (!stage) return

  // 离线构建，一次性插入
  const fragment = document.createDocumentFragment()
  for (let index = 0; index < TILE_COUNT; index += 1) {
    const tile = document.createElement('i')
    tile.className = 'lab-tile'
    tile.style.left = `${(index % 10) * 10 + 1}%`
    tile.style.top = `${Math.floor(index / 10) * 16.6 + 2}%`
    fragment.appendChild(tile)
  }
  stage.appendChild(fragment)
}

function runAnimation(mode: AnimMode): void {
  const stage = animStage.value
  if (!stage || animRunning.value) return

  tileAnimations.forEach((animation) => animation.cancel())
  tileAnimations = []

  const tiles = Array.from(stage.children) as HTMLElement[]
  const duration = 2000
  const startedAt = performance.now()
  let frames = 0

  animRunning.value = mode

  const sample = (now: number): void => {
    frames += 1
    const elapsed = now - startedAt

    if (elapsed < duration) {
      sampleFrame = window.requestAnimationFrame(sample)
      return
    }

    // 以 60Hz 为基准估算掉帧数，只作直观参考
    const expected = Math.round((elapsed / 1000) * 60)
    const next = { ...animResults.value }
    next[mode] = {
      fps: Math.round((frames / elapsed) * 1000),
      frames,
      dropped: Math.max(0, expected - frames),
    }
    animResults.value = next
    animRunning.value = null
  }
  sampleFrame = window.requestAnimationFrame(sample)

  tiles.forEach((tile, index) => {
    const drift = ((index % 5) - 2) * 2.2
    const lift = ((Math.floor(index / 5) % 3) - 1) * 4

    const keyframes =
      mode === 'layout'
        ? [
            { left: tile.style.left, top: tile.style.top },
            {
              left: `${Number.parseFloat(tile.style.left) + drift}%`,
              top: `${Number.parseFloat(tile.style.top) + lift}%`,
            },
          ]
        : [
            { transform: 'translate3d(0, 0, 0)' },
            { transform: `translate3d(${drift * 3}px, ${lift * 3}px, 0)` },
          ]

    tileAnimations.push(
      tile.animate(keyframes, {
        duration,
        easing: 'cubic-bezier(0.45, 0, 0.55, 1)',
        fill: 'forwards',
      }),
    )
  })
}

function animBar(mode: AnimMode): number {
  const result = animResults.value[mode]
  if (!result) return 0
  return Math.max(4, Math.min(100, (result.fps / 60) * 100))
}

// ============ 实验三：缓存布局读取 ============
type LayoutMode = 'cached' | 'live'

const LAYOUT_CELLS = 18
const layoutStage = ref<HTMLElement | null>(null)
const followerRef = ref<HTMLElement | null>(null)
const layoutMode = ref<LayoutMode>('cached')
const hovering = ref(false)
const layoutReads = ref(0)
const layoutMoves = ref(0)
const lastCost = ref(0)
const peakCost = ref(0)
const totalCost = ref(0)

const avgCost = computed(() => (layoutMoves.value ? totalCost.value / layoutMoves.value : 0))

let stageRect: DOMRect | null = null
let centers: { x: number; y: number }[] = []
let cellEls: HTMLElement[] = []
let pointerFrame = 0
let pendingPoint: { x: number; y: number } | null = null

function buildCells(): void {
  const stage = layoutStage.value
  if (!stage) return

  const fragment = document.createDocumentFragment()
  for (let index = 0; index < LAYOUT_CELLS; index += 1) {
    const cell = document.createElement('i')
    cell.className = 'lab-cell'
    fragment.appendChild(cell)
  }
  stage.appendChild(fragment)
  cellEls = Array.from(stage.querySelectorAll<HTMLElement>('.lab-cell'))
}

function measureField(): void {
  const stage = layoutStage.value
  if (!stage) return

  stageRect = stage.getBoundingClientRect()
  // 进入时一次读完所有格子中心，移动过程不再碰布局
  centers = cellEls.map((cell) => ({
    x: cell.offsetLeft + cell.offsetWidth / 2,
    y: cell.offsetTop + cell.offsetHeight / 2,
  }))
}

function invalidateField(): void {
  stageRect = null
  centers = []
}

function flushPointer(): void {
  pointerFrame = 0
  const point = pendingPoint
  if (!point) return

  const startedAt = performance.now()

  if (layoutMode.value === 'cached') {
    for (let index = 0; index < cellEls.length; index += 1) {
      const cell = cellEls[index]
      const center = centers[index]
      if (!cell || !center) continue
      const distance = Math.hypot(center.x - point.x, center.y - point.y)
      cell.style.setProperty('--heat', Math.max(0, 1 - distance / 180).toFixed(2))
    }
  } else {
    // 反面教材：写样式后立刻读布局，循环里反复触发强制同步布局
    for (const cell of cellEls) {
      const centerX = cell.offsetLeft + cell.offsetWidth / 2
      const centerY = cell.offsetTop + cell.offsetHeight / 2
      const distance = Math.hypot(centerX - point.x, centerY - point.y)
      cell.style.setProperty('--heat', Math.max(0, 1 - distance / 180).toFixed(2))
      void cell.getBoundingClientRect()
      layoutReads.value += 1
    }
  }

  const cost = performance.now() - startedAt
  lastCost.value = cost
  peakCost.value = Math.max(peakCost.value, cost)
  totalCost.value += cost
  layoutMoves.value += 1

  const follower = followerRef.value
  if (follower) follower.style.transform = `translate3d(${point.x}px, ${point.y}px, 0)`
}

function onFieldEnter(event: PointerEvent): void {
  hovering.value = true
  measureField()
  if (!stageRect) return
  pendingPoint = { x: event.clientX - stageRect.left, y: event.clientY - stageRect.top }
}

function onFieldMove(event: PointerEvent): void {
  if (!stageRect || !centers.length) measureField()
  if (!stageRect) return

  pendingPoint = { x: event.clientX - stageRect.left, y: event.clientY - stageRect.top }
  if (!pointerFrame) pointerFrame = window.requestAnimationFrame(flushPointer)
}

function onFieldLeave(): void {
  hovering.value = false
  pendingPoint = null
  if (pointerFrame) {
    window.cancelAnimationFrame(pointerFrame)
    pointerFrame = 0
  }
  for (const cell of cellEls) cell.style.setProperty('--heat', '0')
  invalidateField()
}

function resetLayoutStats(): void {
  layoutReads.value = 0
  layoutMoves.value = 0
  lastCost.value = 0
  peakCost.value = 0
  totalCost.value = 0
}

function setLayoutMode(mode: LayoutMode): void {
  if (layoutMode.value === mode) return
  layoutMode.value = mode
  resetLayoutStats()
  invalidateField()
  if (hovering.value) measureField()
}

function onViewportChange(): void {
  invalidateField()
}

// ============ 生命周期 ============
onMounted(() => {
  buildTiles()
  buildCells()
  window.addEventListener('scroll', onViewportChange, { passive: true, capture: true })
  window.addEventListener('resize', onViewportChange, { passive: true })
})

onBeforeUnmount(() => {
  window.removeEventListener('scroll', onViewportChange, true)
  window.removeEventListener('resize', onViewportChange)

  tileAnimations.forEach((animation) => animation.cancel())
  tileAnimations = []

  if (sampleFrame) window.cancelAnimationFrame(sampleFrame)
  if (pointerFrame) window.cancelAnimationFrame(pointerFrame)
  sampleFrame = 0
  pointerFrame = 0
})
</script>

<template>
  <div class="lab container">
    <PageHeader
      eyebrow="Performance Lab"
      title="性能实验室"
      description="把三条前端性能原则放到同一页里跑一遍：先离线构建再插入、动画只走合成层、布局属性读一次就缓存。每项都给出可对比的数字。"
    />

    <section class="lab__principles" aria-label="三条原则">
      <article class="lab-note">
        <span class="lab-note__no">01</span>
        <h2>先离线，再插入</h2>
        <p>在 DocumentFragment 上把节点拼装完，只挂载一次，页面只发生一次 DOM 变更。</p>
        <code>stage.appendChild(fragment)</code>
      </article>
      <article class="lab-note">
        <span class="lab-note__no">02</span>
        <h2>只动 transform / opacity</h2>
        <p>这两类属性由合成层处理，不触发布局与重绘；left、top、width 每帧都会让浏览器重新排版。</p>
        <code>translate3d(x, y, 0)</code>
      </article>
      <article class="lab-note">
        <span class="lab-note__no">03</span>
        <h2>布局只读一次</h2>
        <p>getBoundingClientRect 会强制同步布局。进入容器时量一次，滚动与缩放时再让缓存失效。</p>
        <code>rect ??= el.getBoundingClientRect()</code>
      </article>
    </section>

    <!-- 实验一 -->
    <section class="lab-card">
      <header class="lab-card__head">
        <div>
          <p class="eyebrow">Experiment 01</p>
          <h2>批量 DOM 插入</h2>
          <p class="lab-card__desc">
            相同节点数下对比「逐个 appendChild」与「DocumentFragment 一次插入」。计时包含插入后第一次布局读取。
          </p>
        </div>
        <span class="lab-card__badge">批量</span>
      </header>

      <div class="lab-controls">
        <span class="lab-controls__label">节点数</span>
        <button
          v-for="size in batchSizes"
          :key="size"
          type="button"
          class="chip"
          :class="{ 'is-active': batchSize === size }"
          :aria-pressed="batchSize === size"
          @click="batchSize = size"
        >
          {{ size }}
        </button>
        <span class="lab-controls__sep" />
        <button type="button" class="lab-btn" :disabled="!!batchRunning" @click="runBatch('loop')">
          <AppIcon name="play" :size="14" />
          逐个插入
        </button>
        <button
          type="button"
          class="lab-btn lab-btn--primary"
          :disabled="!!batchRunning"
          @click="runBatch('fragment')"
        >
          <AppIcon name="sparkles" :size="14" />
          Fragment 批量
        </button>
        <button type="button" class="lab-btn lab-btn--ghost" @click="clearBatch">清空</button>
      </div>

      <div ref="batchStage" class="lab-stage" :aria-busy="!!batchRunning" />

      <dl class="lab-results">
        <div class="lab-result" :class="{ 'is-best': batchBest('loop') }">
          <dt>逐个 appendChild</dt>
          <dd>
            <span class="lab-result__track">
              <span class="lab-result__bar" :style="{ width: `${batchBar('loop')}%` }" />
            </span>
            <b>{{ batchResults.loop ? `${batchResults.loop.ms.toFixed(1)} ms` : '—' }}</b>
          </dd>
        </div>
        <div class="lab-result" :class="{ 'is-best': batchBest('fragment') }">
          <dt>Fragment 一次插入</dt>
          <dd>
            <span class="lab-result__track">
              <span class="lab-result__bar" :style="{ width: `${batchBar('fragment')}%` }" />
            </span>
            <b>{{ batchResults.fragment ? `${batchResults.fragment.ms.toFixed(1)} ms` : '—' }}</b>
          </dd>
        </div>
      </dl>
      <p class="lab-hint">重复运行会保留两种写法各自最近一次的结果；节点数越多，差距通常越明显。</p>
    </section>

    <!-- 实验二 -->
    <section class="lab-card">
      <header class="lab-card__head">
        <div>
          <p class="eyebrow">Experiment 02</p>
          <h2>动画走不走合成层</h2>
          <p class="lab-card__desc">
            {{ TILE_COUNT }} 个方块同时运动 2 秒。对比 left / top 与 transform 两种写法下的实际帧率与掉帧数。
          </p>
        </div>
        <span class="lab-card__badge">合成</span>
      </header>

      <div class="lab-controls">
        <span class="lab-controls__label">{{ TILE_COUNT }} 个方块 · 2 秒</span>
        <span class="lab-controls__sep" />
        <button
          type="button"
          class="lab-btn"
          :disabled="!!animRunning"
          @click="runAnimation('layout')"
        >
          <AppIcon name="play" :size="14" />
          跑 left / top
        </button>
        <button
          type="button"
          class="lab-btn lab-btn--primary"
          :disabled="!!animRunning"
          @click="runAnimation('composite')"
        >
          <AppIcon name="sparkles" :size="14" />
          跑 transform
        </button>
      </div>

      <div ref="animStage" class="lab-stage lab-stage--anim" :aria-busy="!!animRunning" />

      <dl class="lab-results">
        <div class="lab-result">
          <dt>left / top</dt>
          <dd>
            <span class="lab-result__track">
              <span class="lab-result__bar lab-result__bar--warn" :style="{ width: `${animBar('layout')}%` }" />
            </span>
            <b>
              {{
                animResults.layout
                  ? `${animResults.layout.fps} fps · 掉帧 ${animResults.layout.dropped}`
                  : '—'
              }}
            </b>
          </dd>
        </div>
        <div class="lab-result">
          <dt>transform</dt>
          <dd>
            <span class="lab-result__track">
              <span class="lab-result__bar" :style="{ width: `${animBar('composite')}%` }" />
            </span>
            <b>
              {{
                animResults.composite
                  ? `${animResults.composite.fps} fps · 掉帧 ${animResults.composite.dropped}`
                  : '—'
              }}
            </b>
          </dd>
        </div>
      </dl>
      <p class="lab-hint">
        如果两种都跑满 60fps，说明当前设备足够快；打开 DevTools 的 Performance 面板，仍能看到 left / top
        版本每帧都有 Layout。
      </p>
    </section>

    <!-- 实验三 -->
    <section class="lab-card">
      <header class="lab-card__head">
        <div>
          <p class="eyebrow">Experiment 03</p>
          <h2>布局读取：缓存 vs 每帧读</h2>
          <p class="lab-card__desc">
            把指针移入下方方格区。缓存模式只在进入时量一次矩形；每帧模式会在循环里反复「写样式 → 读布局」。
          </p>
        </div>
        <span class="lab-card__badge">布局</span>
      </header>

      <div class="lab-controls">
        <span class="lab-controls__label">读取方式</span>
        <button
          type="button"
          class="chip"
          :class="{ 'is-active': layoutMode === 'cached' }"
          :aria-pressed="layoutMode === 'cached'"
          @click="setLayoutMode('cached')"
        >
          缓存 rect
        </button>
        <button
          type="button"
          class="chip"
          :class="{ 'is-active': layoutMode === 'live' }"
          :aria-pressed="layoutMode === 'live'"
          @click="setLayoutMode('live')"
        >
          每帧读取
        </button>
        <span class="lab-controls__sep" />
        <button type="button" class="lab-btn lab-btn--ghost" @click="resetLayoutStats">
          重置计数
        </button>
      </div>

      <div
        ref="layoutStage"
        class="lab-field"
        @pointerenter="onFieldEnter"
        @pointermove="onFieldMove"
        @pointerleave="onFieldLeave"
      >
        <span ref="followerRef" class="lab-follower" :class="{ 'is-on': hovering }" aria-hidden="true" />
      </div>

      <ul class="lab-stats">
        <li>
          <span>强制布局读取</span>
          <b>{{ layoutReads }} 次</b>
        </li>
        <li>
          <span>移动采样</span>
          <b>{{ layoutMoves }} 帧</b>
        </li>
        <li>
          <span>最近一帧耗时</span>
          <b>{{ lastCost.toFixed(2) }} ms</b>
        </li>
        <li>
          <span>平均 / 峰值</span>
          <b>{{ avgCost.toFixed(2) }} / {{ peakCost.toFixed(2) }} ms</b>
        </li>
      </ul>
      <p class="lab-hint">
        切换模式会清零计数。缓存模式下「强制布局读取」应保持为 0；滚动页面或缩放窗口会主动让缓存失效。
      </p>
    </section>
  </div>
</template>

<style scoped lang="scss">
.lab {
  padding: 44px 0 64px;
}

// ---- 顶部三条原则 ----
.lab__principles {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.lab-note {
  position: relative;
  padding: 18px 18px 16px;
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: $radius-card;
  background: linear-gradient(158deg, var(--warm-paper-92), var(--warm-paper-72));
  box-shadow: 0 26px 60px -56px var(--warm-shadow);

  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 18px;
    width: 34px;
    height: 2px;
    border-radius: 0 0 2px 2px;
    background: linear-gradient(90deg, var(--warm-brand), var(--warm-apricot));
  }

  h2 {
    margin: 8px 0 6px;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 15px;
    font-weight: 600;
  }

  p {
    margin: 0 0 12px;
    color: var(--warm-text-muted);
    font-size: 11.5px;
    line-height: 1.8;
  }

  code {
    display: inline-block;
    max-width: 100%;
    padding: 3px 8px;
    overflow: hidden;
    border: 1px solid var(--warm-line);
    border-radius: 7px;
    background: var(--warm-paper-60);
    color: var(--warm-brand-strong);
    font-family: $font-mono;
    font-size: 10.5px;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

.lab-note__no {
  color: var(--warm-brand);
  font-family: $font-mono;
  font-size: 10px;
  letter-spacing: 0.16em;
}

// ---- 实验卡片 ----
.lab-card {
  margin-bottom: 18px;
  padding: 22px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-card;
  background: linear-gradient(160deg, var(--warm-paper-95), var(--warm-paper-78));
  box-shadow:
    0 34px 74px -64px var(--warm-shadow),
    inset 0 1px 0 var(--warm-paper-90);
}

.lab-card__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;

  h2 {
    margin: 4px 0 0;
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 19px;
    font-weight: 600;
    letter-spacing: -0.01em;
  }
}

.lab-card__desc {
  max-width: 680px;
  margin: 7px 0 0;
  color: var(--warm-text-muted);
  font-size: 12px;
  line-height: 1.8;
}

.lab-card__badge {
  flex: none;
  padding: 4px 10px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 28%, transparent);
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 9%, transparent);
  color: var(--warm-brand-strong);
  font-family: $font-mono;
  font-size: 9.5px;
  letter-spacing: 0.12em;
}

// ---- 控制条 ----
.lab-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 7px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px dashed var(--warm-line);
  border-radius: 14px;
  background: var(--warm-paper-50);
}

.lab-controls__label {
  margin-right: 2px;
  color: var(--warm-text-muted);
  font-size: 11px;
  letter-spacing: 0.02em;
}

.lab-controls__sep {
  width: 1px;
  height: 16px;
  margin: 0 3px;
  background: var(--warm-line);
}

.lab-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 12px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-80);
  color: var(--warm-text-regular);
  font: inherit;
  font-size: 11.5px;
  line-height: 1.3;
  white-space: nowrap;
  cursor: pointer;
  transition:
    transform 0.18s $ease-out,
    border-color 0.18s $ease-out,
    background 0.18s $ease-out,
    color 0.18s $ease-out;

  &:not(:disabled):hover {
    transform: translateY(-1px);
    border-color: color-mix(in srgb, var(--warm-brand) 38%, transparent);
    color: var(--warm-brand-strong);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &:disabled {
    opacity: 0.45;
    cursor: not-allowed;
  }
}

.lab-btn--primary {
  border-color: transparent;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-brand));
  color: var(--warm-on-brand);
  font-weight: 600;

  &:not(:disabled):hover {
    color: var(--warm-on-brand);
  }
}

.lab-btn--ghost {
  background: transparent;
  color: var(--warm-text-muted);
}

// ---- 演示区 ----
.lab-stage {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(46px, 1fr));
  gap: 4px;
  min-height: 176px;
  max-height: 210px;
  padding: 10px;
  overflow-y: auto;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background:
    radial-gradient(ellipse 70% 50% at 50% 0%, rgba(136, 104, 110, 0.06), transparent 70%),
    var(--warm-paper-60);
  scrollbar-width: thin;
  scrollbar-color: rgba(136, 104, 110, 0.24) transparent;
}

.lab-chip {
  display: grid;
  place-items: center;
  height: 22px;
  border: 1px solid color-mix(in srgb, var(--warm-brand) 16%, transparent);
  border-radius: 6px;
  background: color-mix(in srgb, var(--warm-brand) 8%, var(--warm-paper-80));
  color: var(--warm-brand-strong);
  font-family: $font-mono;
  font-size: 8px;
  line-height: 1;

  &:nth-child(4n + 2) {
    background: color-mix(in srgb, var(--warm-lilac) 10%, var(--warm-paper-80));
    color: var(--warm-lilac);
  }

  &:nth-child(4n + 4) {
    background: color-mix(in srgb, var(--warm-apricot) 9%, var(--warm-paper-80));
    color: var(--warm-apricot);
  }
}

.lab-stage--anim {
  position: relative;
  display: block;
  height: 224px;
  min-height: 0;
  max-height: none;
  padding: 0;
  overflow: hidden;
}

.lab-tile {
  position: absolute;
  width: 7.4%;
  height: 11.5%;
  border-radius: 8px;
  background: linear-gradient(
    140deg,
    color-mix(in srgb, var(--warm-brand) 62%, transparent),
    color-mix(in srgb, var(--warm-lilac) 48%, transparent)
  );
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.35);
}

// ---- 缓存布局演示 ----
.lab-field {
  position: relative;
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 7px;
  height: 236px;
  padding: 12px;
  overflow: hidden;
  border: 1px solid var(--warm-line);
  border-radius: 14px;
  background:
    radial-gradient(ellipse 70% 60% at 50% 0%, rgba(136, 104, 110, 0.05), transparent 72%),
    var(--warm-paper-60);
  cursor: crosshair;
  touch-action: none;
}

.lab-cell {
  border: 1px solid color-mix(in srgb, var(--warm-brand) 16%, transparent);
  border-radius: 10px;
  background:
    radial-gradient(
      circle at 50% 50%,
      rgba(136, 104, 110, calc(0.05 + var(--heat, 0) * 0.6)),
      rgba(136, 104, 110, 0.03)
    );
  transition: background 0.18s linear;
}

.lab-follower {
  position: absolute;
  top: 0;
  left: 0;
  width: 26px;
  height: 26px;
  margin: -13px 0 0 -13px;
  border: 1px solid var(--warm-brand);
  border-radius: 50%;
  background: color-mix(in srgb, var(--warm-brand) 14%, transparent);
  box-shadow:
    0 0 0 4px color-mix(in srgb, var(--warm-brand) 10%, transparent),
    0 10px 24px -14px var(--warm-brand);
  opacity: 0;
  transform: translate3d(-40px, -40px, 0);
  transition: opacity 0.2s $ease-out;
  pointer-events: none;
  will-change: transform;

  &.is-on {
    opacity: 1;
  }
}

// ---- 结果 ----
.lab-results {
  display: grid;
  gap: 7px;
  margin: 12px 0 0;
}

.lab-result {
  display: grid;
  grid-template-columns: minmax(112px, 170px) minmax(0, 1fr);
  align-items: center;
  gap: 10px;
  color: var(--warm-text-muted);
  font-size: 11.5px;

  dt {
    @include truncate;
  }

  dd {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    align-items: center;
    gap: 10px;
    min-width: 0;
    margin: 0;
  }

  b {
    color: var(--warm-text-regular);
    font-family: $font-mono;
    font-size: 10.5px;
    font-variant-numeric: tabular-nums;
    font-weight: 500;
    text-align: right;
    white-space: nowrap;
  }

  &.is-best b {
    color: var(--warm-brand-strong);
    font-weight: 600;
  }
}

.lab-result__track {
  display: block;
  height: 6px;
  overflow: hidden;
  border-radius: $radius-pill;
  background: color-mix(in srgb, var(--warm-brand) 8%, transparent);
}

.lab-result__bar {
  display: block;
  width: 0;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, var(--warm-brand), var(--warm-sage-light, #baa6aa));
  transition: width 0.5s $ease-out-expo;
}

.lab-result__bar--warn {
  background: linear-gradient(90deg, var(--warm-apricot), var(--warm-rose));
}

.lab-stats {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 7px;
  margin: 12px 0 0;
  padding: 0;
  list-style: none;

  li {
    display: grid;
    gap: 2px;
    padding: 9px 11px;
    border: 1px solid var(--warm-line);
    border-radius: 11px;
    background: var(--warm-paper-50);
  }

  span {
    color: var(--warm-text-muted);
    font-size: 9.5px;
    letter-spacing: 0.02em;
  }

  b {
    color: var(--warm-text-regular);
    font-family: $font-mono;
    font-size: 12px;
    font-variant-numeric: tabular-nums;
    font-weight: 500;
  }
}

.lab-hint {
  margin: 10px 0 0;
  color: var(--warm-text-muted);
  font-size: 10.5px;
  line-height: 1.7;
}

// ---- 响应式 ----
@include media-down($bp-lg) {
  .lab__principles {
    grid-template-columns: 1fr;
  }
}

@include media-down($bp-md) {
  .lab {
    padding: 30px 0 48px;
  }

  .lab-card {
    padding: 16px;
  }

  .lab-card__head {
    flex-direction: column;
    gap: 8px;
  }

  .lab-card__badge {
    align-self: flex-start;
  }

  .lab-stats {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .lab-result {
    grid-template-columns: 1fr;

    dt {
      font-size: 10.5px;
    }
  }

  .lab-field {
    grid-template-columns: repeat(4, minmax(0, 1fr));
    grid-template-rows: repeat(4, minmax(0, 1fr));
    height: 280px;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lab-result__bar,
  .lab-follower,
  .lab-cell {
    transition: none;
  }
}
</style>
