<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 实时脉冲条：一排会自己呼吸的小柱，用来表达「数据正在流动」。
 * 只动 transform / opacity；页面隐藏时暂停，避免后台空转。
 */
const props = withDefaults(
  defineProps<{ bars?: number; height?: number; color?: string }>(),
  { bars: 36, height: 40, color: 'var(--warm-brand)' },
)

// 用平滑的随机游走生成高度，避免每帧乱跳
function seed(): number[] {
  let level = 0.55
  return Array.from({ length: props.bars }, (_, index) => {
    level += (Math.random() - 0.45) * 0.22
    level = Math.min(0.96, Math.max(0.16, level))
    // 中间略高，两端收敛，像一段自然波形
    const envelope = 0.72 + Math.sin((index / props.bars) * Math.PI) * 0.28
    return Math.min(1, level * envelope)
  })
}

const levels = ref<number[]>(seed())
let timer = 0

function tick(): void {
  const next = levels.value.slice(1)
  const last = next[next.length - 1] ?? 0.5
  const value = Math.min(0.98, Math.max(0.12, last + (Math.random() - 0.5) * 0.34))
  next.push(value)
  levels.value = next
}

function start(): void {
  if (timer) return
  timer = window.setInterval(tick, 1400)
}

function stop(): void {
  window.clearInterval(timer)
  timer = 0
}

function onVisibility(): void {
  if (document.hidden) stop()
  else start()
}

onMounted(() => {
  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', onVisibility)
    if (!document.hidden) start()
  }
})

onBeforeUnmount(() => {
  stop()
  document.removeEventListener('visibilitychange', onVisibility)
})
</script>

<template>
  <div
    class="pulse"
    :style="{ height: `${height}px`, '--pulse-color': color }"
    aria-hidden="true"
  >
    <span
      v-for="(level, index) in levels"
      :key="index"
      class="pulse__bar"
      :class="{ 'is-head': index === levels.length - 1 }"
      :style="{ '--level': level, '--i': index }"
    >
      <i />
    </span>
  </div>
</template>

<style scoped lang="scss">
.pulse {
  display: flex;
  align-items: flex-end;
  gap: 2px;
  min-width: 0;
  width: 100%;
  overflow: hidden;
  mask-image: linear-gradient(90deg, transparent, #000 6%, #000 94%, transparent);
  -webkit-mask-image: linear-gradient(90deg, transparent, #000 6%, #000 94%, transparent);
}

.pulse__bar {
  position: relative;
  flex: 1;
  height: 100%;
  min-width: 1px;

  i {
    position: absolute;
    inset: 0;
    border-radius: 2px;
    background: linear-gradient(
      180deg,
      color-mix(in srgb, var(--pulse-color) 82%, transparent),
      color-mix(in srgb, var(--pulse-color) 24%, transparent)
    );
    opacity: 0.58;
    transform: scaleY(var(--level));
    transform-origin: bottom;
    transition:
      transform 1.3s $ease-out-expo,
      opacity 0.3s $ease-out;
  }

  &.is-head i {
    opacity: 1;
    box-shadow: 0 0 10px color-mix(in srgb, var(--pulse-color) 60%, transparent);
  }

  &:nth-child(3n) i {
    opacity: 0.42;
  }
}

@media (prefers-reduced-motion: reduce) {
  .pulse__bar i {
    transition: none;
  }
}
</style>
