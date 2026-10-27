<script setup lang="ts">
import type { Tone } from '@/types'

withDefaults(defineProps<{ initials: string; tone?: Tone; size?: number }>(), {
  tone: 'teal',
  size: 28,
})
</script>

<template>
  <span
    class="user-badge"
    :class="`user-badge--${tone}`"
    :style="{ width: `${size}px`, height: `${size}px`, fontSize: `${Math.round(size * 0.4)}px` }"
  >
    {{ initials }}
  </span>
</template>

<style scoped lang="scss">
.user-badge {
  display: inline-grid;
  place-items: center;
  flex: none;
  border-radius: 50%;
  color: var(--warm-on-brand);
  font-weight: 600;
  line-height: 1;
}

.user-badge--teal {
  background: linear-gradient(135deg, var(--warm-sage-light), var(--warm-brand-strong));
}

.user-badge--lilac {
  background: linear-gradient(135deg, #d09fa9, #b06f7c);
}

.user-badge--sand {
  background: linear-gradient(135deg, #e0aa7f, var(--warm-apricot));
}
</style>
