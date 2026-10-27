<script setup lang="ts">
import { computed } from 'vue'

import type { StatItem } from '@/types'
import AppIcon from './AppIcon.vue'
import Sparkline from './Sparkline.vue'

const props = defineProps<{ stat: StatItem }>()

const display = computed(() =>
  Number.isInteger(props.stat.value) ? props.stat.value.toLocaleString('zh-CN') : props.stat.value,
)

const isDown = computed(() => props.stat.delta < 0)

// 四张指标卡各用一个主色，和暖光主题的苔绿 / 丁香 / 杏橘 / 蜂蜜呼应
const TONES: Record<string, string> = {
  users: 'var(--warm-brand)',
  moods: 'var(--warm-lilac)',
  consults: 'var(--warm-apricot)',
  moodAvg: 'var(--warm-butter)',
}

const tone = computed(() => TONES[props.stat.key] ?? 'var(--warm-brand)')
const sparkColor = computed(() => (isDown.value ? 'var(--warm-rose)' : tone.value))
</script>

<template>
  <article class="stat" v-spotlight :style="{ '--stat-tone': tone }">
    <header class="stat__head">
      <span class="stat__label">{{ stat.label }}</span>
      <span class="stat__icon"><AppIcon :name="stat.icon" :size="16" /></span>
    </header>

    <p class="stat__value">
      {{ display }}<small v-if="stat.unit">{{ stat.unit }}</small>
    </p>

    <footer class="stat__foot">
      <span class="stat__delta" :class="{ 'is-down': isDown }">
        {{ isDown ? '↓' : '↑' }} {{ Math.abs(stat.delta) }}%
      </span>
      <span class="stat__hint">较上周</span>
      <Sparkline class="stat__spark" :data="stat.series" :color="sparkColor" />
    </footer>
  </article>
</template>

<style scoped lang="scss">
.stat {
  position: relative;
  overflow: hidden;
  padding: 20px 22px 18px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-card;
  background: linear-gradient(158deg, var(--warm-paper) 0%, var(--warm-paper-alt) 68%);
  box-shadow:
    0 26px 58px -46px rgba(74, 64, 46, 0.34),
    inset 0 1px 0 var(--warm-paper-85);
  transition:
    transform 0.38s $ease-out-expo,
    border-color 0.38s $ease-out,
    box-shadow 0.38s $ease-out;

  // 当前指标色在右上角散开的一点氛围光
  &::after {
    content: '';
    position: absolute;
    top: -58px;
    right: -46px;
    width: 190px;
    height: 190px;
    border-radius: 50%;
    background: radial-gradient(circle, var(--stat-tone), transparent 68%);
    opacity: 0.13;
    pointer-events: none;
    transition: opacity 0.38s $ease-out;
  }

  &:hover {
    transform: translateY(-4px);
    border-color: rgba(136, 104, 110, 0.26);
    box-shadow:
      0 36px 78px -48px rgba(74, 64, 46, 0.42),
      0 0 46px -34px var(--stat-tone),
      inset 0 1px 0 var(--warm-paper-90);

    &::after {
      opacity: 0.22;
    }

    .stat__icon {
      box-shadow: 0 8px 18px -12px var(--stat-tone);
    }
  }
}

.stat__head {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat__label {
  color: var(--warm-text-muted);
  font-size: 12.5px;
  letter-spacing: 0.04em;
}

.stat__icon {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border: 1px solid color-mix(in srgb, var(--stat-tone) 26%, transparent);
  border-radius: 11px;
  background: color-mix(in srgb, var(--stat-tone) 12%, transparent);
  color: var(--stat-tone);
  transition: box-shadow 0.38s $ease-out;
}

.stat__value {
  position: relative;
  z-index: 1;
  margin: 14px 0 12px;
  color: var(--warm-text);
  font-family: $font-display;
  font-size: 34px;
  font-weight: 600;
  letter-spacing: -0.03em;
  line-height: 1;
  font-variant-numeric: tabular-nums;

  small {
    margin-left: 4px;
    color: var(--warm-text-muted);
    font-family: $font-body;
    font-size: 13px;
    font-weight: 400;
    letter-spacing: 0;
  }
}

.stat__foot {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 6px;
}

.stat__delta {
  color: var(--warm-brand-strong);
  font-size: 12px;
  font-weight: 600;

  &.is-down {
    color: #b85c4c;
  }
}

.stat__hint {
  color: var(--warm-text-muted);
  font-size: 12px;
}

.stat__spark {
  width: 82px;
  margin-left: auto;
  opacity: 0.95;
}
</style>
