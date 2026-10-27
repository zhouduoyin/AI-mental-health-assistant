<script setup lang="ts">
import { computed } from 'vue'

import type { TopicBar } from '@/types'

const props = withDefaults(
  defineProps<{ items?: TopicBar[]; color?: string; panel?: boolean; live?: boolean }>(),
  {
    items: () => [],
    color: 'var(--warm-brand)',
    panel: false,
    live: false,
  },
)

const max = computed(() => Math.max(...props.items.map((item) => item.value), 1))
const total = computed(() => props.items.reduce((sum, item) => sum + item.value, 0))
</script>

<template>
  <ul class="bars" :class="{ 'bars--panel': panel, 'bars--live': live }">
    <li
      v-for="(item, index) in items"
      :key="item.label"
      class="bars__row"
      :style="{ '--bar-color': color, '--bar-delay': `${index * 80}ms`, '--bar-ratio': item.value / max }"
    >
      <span class="bars__rank">{{ String(index + 1).padStart(2, '0') }}</span>
      <span class="bars__label">{{ item.label }}</span>
      <span class="bars__track">
        <span
          class="bars__fill"
          :style="{
            width: `${(item.value / max) * 100}%`,
            animationDelay: `${index * 80}ms`,
          }"
        >
          <i class="bars__head" />
        </span>
        <span class="bars__ticks" aria-hidden="true">
          <i v-for="tick in 4" :key="tick" :style="{ left: `${tick * 20}%` }" />
        </span>
      </span>
      <span class="bars__value">
        {{ item.value }}
        <small v-if="total">{{ Math.round((item.value / total) * 100) }}%</small>
      </span>
    </li>
  </ul>
</template>

<style scoped lang="scss">
.bars {
  display: grid;
  gap: 15px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bars__row {
  display: grid;
  grid-template-columns: 24px 64px minmax(0, 1fr) 56px;
  align-items: center;
  gap: $space-3;
  font-size: 13px;

  &:hover {
    .bars__label {
      color: var(--bar-color, currentColor);
    }

    .bars__fill {
      filter: brightness(1.08) saturate(1.08);
    }

    .bars__head {
      opacity: 1;
      transform: translateY(-50%) scale(1);
    }
  }
}

.bars__rank {
  color: var(--warm-text-muted);
  font-family: $font-mono;
  font-size: 10.5px;
  letter-spacing: 0.05em;
  opacity: 0.7;
}

.bars__label {
  color: var(--warm-text-regular);
  transition: color 0.24s $ease-out;
  @include truncate;
}

.bars__track {
  position: relative;
  height: 9px;
  border-radius: $radius-pill;
  background: var(--warm-line);

  &::before {
    content: '';
    position: absolute;
    inset: -5px 0;
    border-radius: inherit;
    background: color-mix(in srgb, var(--bar-color) 6%, transparent);
    opacity: 0;
    transition: opacity 0.3s $ease-out;
  }
}

.bars__row:hover .bars__track::before {
  opacity: 1;
}

.bars__fill {
  position: relative;
  display: block;
  height: 100%;
  overflow: hidden;
  border-radius: inherit;
  background: linear-gradient(
    90deg,
    color-mix(in srgb, var(--bar-color, var(--warm-brand)) 34%, transparent),
    var(--bar-color, var(--warm-brand))
  );
  transform-origin: left;
  animation: grow 0.9s $ease-out-expo both;
  transition:
    width 0.9s $ease-out-expo,
    filter 0.24s $ease-out;

  // 着色条扫过一次高光，只在入场时运行
  &::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(100deg, transparent 24%, var(--warm-rim-35) 48%, transparent 72%);
    transform: translateX(-120%);
    animation: bar-sheen 1s calc(var(--bar-delay, 0ms) + 420ms) $ease-out both;
  }
}

// 条尾的亮点，像刚刷新到的数据点
.bars__head {
  position: absolute;
  top: 50%;
  right: 0;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--warm-paper);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--bar-color) 55%, transparent);
  opacity: 0;
  transform: translateY(-50%) scale(0.4);
  transition:
    opacity 0.3s $ease-out,
    transform 0.3s $ease-out-expo;
}

.bars__ticks {
  position: absolute;
  inset: 0;
  pointer-events: none;

  i {
    position: absolute;
    top: 50%;
    width: 1px;
    height: 3px;
    background: var(--warm-line);
    transform: translateY(-50%);
  }
}

.bars__value {
  display: flex;
  align-items: baseline;
  justify-content: flex-end;
  gap: 4px;
  color: var(--warm-text);
  font-family: $font-display;
  font-size: 15px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  text-align: right;
  transition: color 0.3s $ease-out;

  small {
    color: var(--warm-text-muted);
    font-family: $font-body;
    font-size: 9.5px;
    font-weight: 400;
  }
}

.bars__row:hover .bars__value {
  color: var(--bar-color);
}

// ---- 后台暖纸面板 ----
.bars--panel {
  .bars__label {
    color: var(--warm-text-muted);
  }

  .bars__row:hover .bars__label {
    color: var(--warm-text);
  }

  .bars__track {
    height: 10px;
    background: var(--warm-line);
    box-shadow: inset 0 0 0 1px var(--warm-line);
  }

  .bars__value {
    font-size: 15.5px;
  }
}

// ---- 实时模式：当前值的光点在呼吸 ----
.bars--live {
  .bars__row:first-child .bars__head {
    opacity: 1;
    transform: translateY(-50%) scale(1);
    animation: head-ping 2.4s ease-out infinite;
  }

  .bars__row:first-child .bars__fill::after {
    animation:
      bar-sheen 1s calc(var(--bar-delay, 0ms) + 420ms) $ease-out both,
      live-sheen 5.2s 1.6s linear infinite;
  }
}

@keyframes grow {
  from {
    transform: scaleX(0);
  }

  to {
    transform: scaleX(1);
  }
}

@keyframes bar-sheen {
  from {
    transform: translateX(-120%);
  }

  to {
    transform: translateX(120%);
  }
}

@keyframes live-sheen {
  0% {
    transform: translateX(-120%);
  }

  55%,
  100% {
    transform: translateX(140%);
  }
}

@keyframes head-ping {
  0% {
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--bar-color) 55%, transparent);
  }

  70% {
    box-shadow: 0 0 0 7px transparent;
  }

  100% {
    box-shadow: 0 0 0 2px color-mix(in srgb, var(--bar-color) 55%, transparent);
  }
}

@media (prefers-reduced-motion: reduce) {
  .bars__fill,
  .bars__fill::after,
  .bars__head {
    animation: none !important;
  }
}
</style>
