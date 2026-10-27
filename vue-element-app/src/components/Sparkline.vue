<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{ data?: number[]; color?: string; height?: number }>(), {
  data: () => [],
  color: 'var(--warm-brand)',
  height: 32,
})

const width = 100
const uid = `spark-${Math.random().toString(36).slice(2, 8)}`

const points = computed(() => {
  const values = props.data.length > 1 ? props.data : [0, 0]
  const max = Math.max(...values)
  const min = Math.min(...values)
  const range = max - min || 1
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * width
      const y = props.height - ((value - min) / range) * (props.height - 4) - 2
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
})

const areaPoints = computed(() => `${points.value} ${width},${props.height} 0,${props.height}`)
</script>

<template>
  <svg
    class="spark"
    :viewBox="`0 0 ${width} ${height}`"
    preserveAspectRatio="none"
    :style="{ height: `${height}px` }"
    aria-hidden="true"
  >
    <defs>
      <linearGradient :id="uid" x1="0" y1="0" x2="0" y2="1">
        <stop offset="0%" :stop-color="color" stop-opacity="0.3" />
        <stop offset="100%" :stop-color="color" stop-opacity="0" />
      </linearGradient>
    </defs>

    <polygon :points="areaPoints" :fill="`url(#${uid})`" class="spark__area" />
    <polyline
      :points="points"
      :stroke="color"
      fill="none"
      stroke-width="2"
      stroke-linecap="round"
      stroke-linejoin="round"
      vector-effect="non-scaling-stroke"
      class="spark__line"
      :style="{ filter: `drop-shadow(0 2px 4px ${color}55)` }"
    />
  </svg>
</template>

<style scoped>
.spark {
  display: block;
  width: 100%;
  overflow: visible;
}

.spark__area {
  opacity: 0.9;
}
</style>
