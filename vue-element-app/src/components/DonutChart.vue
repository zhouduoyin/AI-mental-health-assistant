<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'

import type { MoodSlice } from '@/types'

const props = withDefaults(
  defineProps<{
    items?: MoodSlice[]
    size?: number
    thickness?: number
    centerLabel?: string
    centerValue?: string | number
  }>(),
  { items: () => [], size: 168, thickness: 20, centerLabel: '总计', centerValue: '' },
)

const total = computed(() => props.items.reduce((sum, item) => sum + Number(item.value || 0), 0) || 1)

const VIEW = 100
const gap = 1.8 // 段与段之间的呼吸缝（viewBox 单位）
const radius = computed(() => (VIEW - (props.thickness / props.size) * VIEW) / 2)
const circumference = computed(() => 2 * Math.PI * radius.value)

// 分段弧：从 12 点方向开始，顺时针铺开
const segments = computed(() => {
  let acc = 0
  return props.items.map((item) => {
    const ratio = Number(item.value || 0) / total.value
    const dash = Math.max(0, ratio * circumference.value - gap)
    const offset = -acc * circumference.value
    acc += ratio
    return {
      ...item,
      dash,
      offset,
      percent: Math.round(ratio * 1000) / 10,
    }
  })
})

const uid = `donut-${Math.random().toString(36).slice(2, 8)}`
const ready = ref(false)
const active = ref<number | null>(null)

let enterFrame = 0

onMounted(async () => {
  await nextTick()
  enterFrame = requestAnimationFrame(() => {
    ready.value = true
  })
})

onBeforeUnmount(() => {
  if (enterFrame) cancelAnimationFrame(enterFrame)
})

const displayValue = computed(() => props.centerValue || total.value)

const activeItem = computed(() => {
  if (active.value === null) return null
  const item = segments.value[active.value]
  return item ?? null
})

const centerMain = computed(() => (activeItem.value ? `${activeItem.value.value}%` : displayValue.value))
const centerSub = computed(() => (activeItem.value ? activeItem.value.label : props.centerLabel))
</script>

<template>
  <div class="donut">
    <div
      class="donut__chart"
      :style="{ width: `${size}px`, height: `${size}px` }"
      @pointerleave="active = null"
    >
      <span class="donut__aura" aria-hidden="true" />

      <svg class="donut__svg" :viewBox="`0 0 ${VIEW} ${VIEW}`" aria-hidden="true">
        <defs>
          <filter :id="`${uid}-glow`" x="-30%" y="-30%" width="160%" height="160%">
            <feGaussianBlur stdDeviation="2.2" result="blur" />
            <feMerge>
              <feMergeNode in="blur" />
              <feMergeNode in="SourceGraphic" />
            </feMerge>
          </filter>
        </defs>

        <circle
          class="donut__track"
          :cx="VIEW / 2"
          :cy="VIEW / 2"
          :r="radius"
          fill="none"
          :stroke-width="(thickness / size) * VIEW * 0.72"
        />

        <circle
          v-for="(item, index) in segments"
          :key="item.label"
          class="donut__seg"
          :class="{ 'is-dim': active !== null && active !== index, 'is-active': active === index }"
          :cx="VIEW / 2"
          :cy="VIEW / 2"
          :r="radius"
          fill="none"
          :stroke="item.color"
          :stroke-width="(thickness / size) * VIEW * (active === index ? 1.02 : 0.72)"
          stroke-linecap="round"
          :stroke-dasharray="ready ? `${item.dash} ${circumference - item.dash}` : `0 ${circumference}`"
          :stroke-dashoffset="item.offset"
          :filter="active === index ? `url(#${uid}-glow)` : undefined"
          :style="{ transitionDelay: `${index * 90}ms` }"
          @pointerenter="active = index"
          @focus="active = index"
          tabindex="0"
        />
      </svg>

      <div class="donut__hole" :style="{ inset: `${thickness}px` }">
        <Transition name="donut-center" mode="out-in">
          <div :key="centerSub" class="donut__center">
            <strong>{{ centerMain }}</strong>
            <span>{{ centerSub }}</span>
          </div>
        </Transition>
      </div>
    </div>

    <ul class="donut__legend">
      <li
        v-for="(item, index) in segments"
        :key="item.label"
        :class="{ 'is-active': active === index }"
        @pointerenter="active = index"
        @pointerleave="active = null"
      >
        <i :style="{ background: item.color, color: item.color }" />
        <span class="donut__name">{{ item.label }}</span>
        <span class="donut__value">{{ item.value }}%</span>
        <span class="donut__bar" aria-hidden="true">
          <b :style="{ width: `${item.percent}%`, background: item.color }" />
        </span>
      </li>
    </ul>
  </div>
</template>

<style scoped lang="scss">
.donut {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: $space-5;
}

.donut__chart {
  position: relative;
  flex: none;
  border-radius: 50%;
  isolation: isolate;
}

// 中心背后的一圈柔光，让环看起来像浮在纸面上
.donut__aura {
  position: absolute;
  inset: 12%;
  z-index: -1;
  border-radius: 50%;
  background: radial-gradient(circle, var(--warm-glow), transparent 70%);
  filter: blur(18px);
  opacity: 0.7;
  animation: breathe 8s ease-in-out infinite;
}

.donut__svg {
  display: block;
  width: 100%;
  height: 100%;
  overflow: visible;
  transform: rotate(-90deg);
}

.donut__track {
  stroke: var(--warm-line);
}

.donut__seg {
  cursor: pointer;
  outline: none;
  transition:
    stroke-dasharray 1s $ease-out-expo,
    stroke-width 0.24s $ease-out,
    opacity 0.24s $ease-out,
    filter 0.3s $ease-out;

  &.is-dim {
    opacity: 0.32;
  }

  &:focus-visible {
    stroke-width: 9;
  }
}

.donut__hole {
  position: absolute;
  display: grid;
  place-content: center;
  border-radius: 50%;
  background:
    radial-gradient(circle at 50% 24%, color-mix(in srgb, var(--warm-butter) 12%, transparent), transparent 58%),
    var(--warm-paper);
  box-shadow: inset 0 0 0 1px var(--warm-line);
  text-align: center;
  overflow: hidden;
}

.donut__center {
  display: grid;
  place-items: center;
  min-width: 88px;

  strong {
    color: var(--warm-text);
    font-family: $font-display;
    font-size: 26px;
    font-weight: 600;
    line-height: 1.1;
    font-variant-numeric: tabular-nums;
  }

  span {
    max-width: 108px;
    margin-top: 3px;
    overflow: hidden;
    color: var(--warm-text-muted);
    font-size: 10.5px;
    letter-spacing: 0.06em;
    white-space: nowrap;
    text-overflow: ellipsis;
  }
}

.donut__legend {
  flex: 1;
  min-width: 150px;
  margin: 0;
  padding: 0;
  list-style: none;

  li {
    position: relative;
    display: grid;
    grid-template-columns: 8px minmax(0, 1fr) auto;
    align-items: center;
    gap: 9px;
    padding: 8px 8px 12px;
    border-radius: 10px;
    font-size: 13px;
    transition: background 0.22s $ease-out;

    &.is-active {
      background: var(--warm-paper-70);
    }
  }

  i {
    width: 8px;
    height: 8px;
    border-radius: 3px;
    box-shadow: 0 2px 8px -2px currentColor;
  }
}

.donut__name {
  color: var(--warm-text-regular);
  @include truncate;
}

.donut__value {
  color: var(--warm-text);
  font-family: $font-display;
  font-size: 14px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
}

.donut__bar {
  position: absolute;
  right: 8px;
  bottom: 5px;
  left: 25px;
  height: 2px;
  overflow: hidden;
  border-radius: 2px;
  background: var(--warm-line);

  b {
    display: block;
    height: 100%;
    border-radius: inherit;
    opacity: 0.75;
    transform-origin: left;
    animation: bar-in 1s $ease-out-expo both;
  }
}

.donut-center-enter-active,
.donut-center-leave-active {
  transition:
    opacity 0.18s $ease-out,
    transform 0.18s $ease-out;
}

.donut-center-enter-from,
.donut-center-leave-to {
  opacity: 0;
  transform: translateY(6px) scale(0.96);
}

@keyframes bar-in {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .donut__aura {
    animation: none;
  }

  .donut__seg {
    transition: none;
  }

  .donut__bar b {
    animation: none;
  }
}
</style>
