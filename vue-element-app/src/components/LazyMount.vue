<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 视口懒挂载：滚动到附近后才渲染默认插槽里的重组件。
 *
 * 和路由懒加载的区别：路由懒加载只保证别的页面不下载这些代码，
 * 首屏页面里「屏幕外、初次渲染很贵」的模块（图表、画布、定时器）仍会在
 * 挂载时立刻执行。这里把它们推迟到真正快被看到时再挂载。
 *
 * 占位块保持与内容相当的高度，避免真正渲染时页面跳动。
 */
const props = withDefaults(
  defineProps<{
    /** 占位高度（px），尽量接近真实内容高度 */
    minHeight?: number
    /** 提前多少距离开始加载，默认上下各 240px */
    rootMargin?: string
  }>(),
  { minHeight: 160, rootMargin: '240px 0px' },
)

const host = ref<HTMLElement | null>(null)
const shown = ref(false)
let observer: IntersectionObserver | null = null

onMounted(() => {
  if (typeof IntersectionObserver === 'undefined') {
    shown.value = true
    return
  }

  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      shown.value = true
      observer?.disconnect()
      observer = null
    },
    { rootMargin: props.rootMargin },
  )

  if (host.value) observer.observe(host.value)
})

onBeforeUnmount(() => {
  observer?.disconnect()
  observer = null
})
</script>

<template>
  <div ref="host" class="lazy-mount">
    <slot v-if="shown" />
    <div
      v-else
      class="lazy-mount__placeholder"
      :style="{ minHeight: `${minHeight}px` }"
      aria-hidden="true"
    />
  </div>
</template>

<style scoped lang="scss">
.lazy-mount {
  display: block;
  width: 100%;
}

.lazy-mount__placeholder {
  width: 100%;
  border-radius: $radius-sm;
  background: var(--warm-paper-40);
  // 只动 opacity，避免占位块自己触发回流
  animation: lazy-pulse 1.8s ease-in-out infinite;
}

@keyframes lazy-pulse {
  0%,
  100% {
    opacity: 0.45;
  }

  50% {
    opacity: 0.9;
  }
}

@media (prefers-reduced-motion: reduce) {
  .lazy-mount__placeholder {
    animation: none;
    opacity: 0.6;
  }
}
</style>
