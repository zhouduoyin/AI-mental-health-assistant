<script setup lang="ts">
import type { IconName } from '@/types'

/**
 * 内联 SVG 图标（1.7px 描边、圆角端点），避免引入图标库。
 * 用法：<AppIcon name="chat" :size="18" />
 */
const props = withDefaults(defineProps<{ name: IconName; size?: number | string }>(), {
  size: 18,
})

const icons: Record<IconName, string[]> = {
  home: ['M3 10.5 12 3l9 7.5', 'M5 9.5V20h4v-5h6v5h4V9.5'],
  chat: ['M4 5h16v11H8l-4 4z', 'M8 9.5h8', 'M8 12.5h5'],
  book: ['M4 4h9v16H4z', 'M13 4h7v16h-7', 'M7 8h3'],
  mood: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M8.5 14.5a4 4 0 0 0 7 0', 'M9 9.5h.01', 'M15 9.5h.01'],
  chart: ['M4 4v16h16', 'M8 16v-4', 'M12 16V8', 'M16 16v-6'],
  users: ['M16 19v-2a4 4 0 0 0-4-4H7a4 4 0 0 0-4 4v2', 'M9.5 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6z', 'M17 11a3 3 0 0 0 0-6', 'M21 19v-2a4 4 0 0 0-3-3.8'],
  search: ['M10.5 18a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15z', 'M16 16l4.5 4.5'],
  bell: ['M18 9a6 6 0 1 0-12 0c0 5-2 6-2 6h16s-2-1-2-6', 'M10 20a2 2 0 0 0 4 0'],
  arrowRight: ['M4 12h15', 'M13 6l6 6-6 6'],
  shield: ['M12 3l7 3v5.5c0 4.5-3 7.6-7 9.5-4-1.9-7-5-7-9.5V6z', 'M9 12l2 2 4-4'],
  sparkles: ['M12 3l1.7 4.8 4.8 1.7-4.8 1.7L12 16l-1.7-4.8L5.5 9.5l4.8-1.7z', 'M18.5 15.5l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z'],
  plus: ['M12 5v14', 'M5 12h14'],
  send: ['M4 12l16-8-6 16-3-6z'],
  close: ['M6 6l12 12', 'M18 6L6 18'],
  menu: ['M4 7h16', 'M4 12h16', 'M4 17h16'],
  check: ['M5 12.5l4.5 4.5L19 7'],
  clock: ['M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z', 'M12 7v5l3 2'],
  heart: ['M12 20s-7-4.4-7-9.2A4 4 0 0 1 12 7a4 4 0 0 1 7 3.8C19 15.6 12 20 12 20z'],
  filter: ['M4 5h16', 'M7 12h10', 'M10 19h4'],
  download: ['M12 4v11', 'M8 11l4 4 4-4', 'M5 20h14'],
  leaf: ['M5 19c0-8 5-13 14-14 1 9-4 14-12 14', 'M5 19c3-5 7-8 11-9'],
  refresh: ['M20 12a8 8 0 1 1-2.3-5.6', 'M20 4v4h-4'],
  alert: ['M12 4l9 16H3z', 'M12 10v4', 'M12 17h.01'],
  play: ['M8 5l11 7-11 7z'],
  pause: ['M8 5v14', 'M16 5v14'],
  chevronLeft: ['M14 6l-6 6 6 6'],
  chevronRight: ['M10 6l6 6-6 6'],
  logout: ['M9 4H5v16h4', 'M16 8l4 4-4 4', 'M20 12H9'],
  sun: [
    'M12 16.5a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9z',
    'M12 2.5v2',
    'M12 19.5v2',
    'M2.5 12h2',
    'M19.5 12h2',
    'M5.2 5.2l1.4 1.4',
    'M17.4 17.4l1.4 1.4',
    'M18.8 5.2l-1.4 1.4',
    'M6.6 17.4l-1.4 1.4',
  ],
  moon: ['M20 14.2A8.5 8.5 0 0 1 9.8 4 8.5 8.5 0 1 0 20 14.2z', 'M17 4.5l.6 1.6 1.6.6-1.6.6-.6 1.6-.6-1.6-1.6-.6 1.6-.6z'],
  bookmark: ['M6.5 4h11v17l-5.5-4-5.5 4z'],
  flame: ['M12 21c3.6 0 6-2.3 6-5.6 0-2.6-1.6-4.6-3.1-6.2-.9-1-1.6-1.9-1.6-3.2-2.4 1.1-4.2 3.3-4.2 5.3 0 .8.2 1.4.6 2-1.2-.4-2.2-1.3-2.7-2.5-.7 1.2-1 2.5-1 3.9C6 18.6 8.4 21 12 21z'],
  wind: ['M3 8h11a3 3 0 1 0-3-3', 'M3 12h15a3 3 0 1 1-3 3', 'M3 16h7'],
  copy: ['M9 9h10v11H9z', 'M15 9V4H4v11h5'],
  volume: ['M11 5 6 9H3v6h3l5 4z', 'M15.5 8.5a5 5 0 0 1 0 7', 'M18 6a8 8 0 0 1 0 12'],
  thumbUp: ['M7 11v9H4v-9z', 'M7 11l4-7a2 2 0 0 1 2 2v4h5a2 2 0 0 1 2 2.3l-1.2 5.7A2 2 0 0 1 16.8 20H7'],
  thumbDown: ['M7 13V4H4v9z', 'M17 13l-4 7a2 2 0 0 1-2-2v-4H6a2 2 0 0 1-2-2.3l1.2-5.7A2 2 0 0 1 7.2 4H17'],
}

const paths = icons[props.name] ?? icons.sparkles
</script>

<template>
  <svg
    class="app-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
  >
    <path v-for="(d, index) in paths" :key="index" :d="d" />
  </svg>
</template>

<style scoped>
.app-icon {
  flex: none;
  display: block;
}
</style>
