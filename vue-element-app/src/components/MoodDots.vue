<script setup lang="ts">
/** 5 级情绪点阵：亮起的点数 = 分数。 */
withDefaults(defineProps<{ score: number; size?: number }>(), { size: 6 })
</script>

<template>
  <span class="dots" role="img" :aria-label="`情绪 ${score} 分`">
    <i v-for="n in 5" :key="n" :style="{ width: `${size}px`, height: `${size}px` }" :class="{ 'is-on': n <= score }" />
  </span>
</template>

<style scoped lang="scss">
.dots {
  display: inline-flex;
  gap: 3px;

  i {
    border-radius: 50%;
    background: rgba(92, 77, 52, 0.14);
    transition:
      background 0.24s $ease-out,
      box-shadow 0.24s $ease-out;
  }

  .is-on {
    background: var(--warm-brand);
    box-shadow: 0 0 0 2px rgba(136, 104, 110, 0.12);
  }
}
</style>
