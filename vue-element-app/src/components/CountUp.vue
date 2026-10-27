<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 数字滚动：进入视口后从 0 计数到目标值。
 * 降低动效偏好或无 IntersectionObserver 时直接显示最终值。
 */
const props = withDefaults(
  defineProps<{
    value: number
    duration?: number
    decimals?: number
    prefix?: string
    suffix?: string
  }>(),
  { duration: 1500, decimals: 0, prefix: '', suffix: '' },
)

const root = ref<HTMLElement | null>(null)

const prefersReduced =
  typeof window !== 'undefined' &&
  window.matchMedia('(prefers-reduced-motion: reduce)').matches

const display = ref(prefersReduced ? props.value : 0)

let frame = 0
let observer: IntersectionObserver | undefined

onMounted(() => {
  if (prefersReduced || typeof IntersectionObserver === 'undefined' || !root.value) {
    display.value = props.value
    return
  }

  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      observer?.disconnect()

      const startedAt = performance.now()

      const step = (now: number): void => {
        const progress = Math.min(1, (now - startedAt) / props.duration)
        // easeOutCubic：前段快、末段稳稳落下
        display.value = props.value * (1 - Math.pow(1 - progress, 3))

        if (progress < 1) {
          frame = requestAnimationFrame(step)
        } else {
          display.value = props.value
        }
      }

      frame = requestAnimationFrame(step)
    },
    { threshold: 0.35 },
  )

  observer.observe(root.value)
})

onBeforeUnmount(() => {
  if (frame) cancelAnimationFrame(frame)
  observer?.disconnect()
})

const formatted = computed(() =>
  display.value.toLocaleString('zh-CN', {
    minimumFractionDigits: props.decimals,
    maximumFractionDigits: props.decimals,
  }),
)
</script>

<template>
  <span ref="root" class="count-up">{{ props.prefix }}{{ formatted }}{{ props.suffix }}</span>
</template>

<style scoped>
.count-up {
  font-variant-numeric: tabular-nums;
}
</style>
