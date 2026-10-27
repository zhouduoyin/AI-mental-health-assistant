<script setup lang="ts">
import AppIcon from './AppIcon.vue'

withDefaults(defineProps<{ tone?: 'light' | 'dark' }>(), { tone: 'light' })
</script>

<template>
  <span class="brand" :class="`brand--${tone}`">
    <span class="brand__mark"><AppIcon name="leaf" :size="18" /></span>
    <span class="brand__text">
      <strong>心理健康</strong>
      <small>AI 助手</small>
    </span>
  </span>
</template>

<style scoped lang="scss">
.brand {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: var(--warm-text);
}

.brand__mark {
  display: inline-grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 12px;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-sage-light));
  color: var(--warm-on-brand);
  box-shadow:
    0 8px 18px -10px rgba(112, 80, 86, 0.55),
    inset 0 1px 0 var(--warm-paper-40);
}

.brand__text {
  display: grid;
  line-height: 1.15;

  strong {
    font-family: $font-display;
    font-size: 15.5px;
    font-weight: 600;
    letter-spacing: 0.08em;
  }

  small {
    margin-top: 2px;
    font-size: 11px;
    color: var(--warm-text-muted);
    letter-spacing: 0.02em;
  }
}

.brand--dark {
  color: #fdf9f0;

  .brand__mark {
    background: linear-gradient(135deg, #d6a5af, var(--warm-brand));
    color: var(--warm-on-brand);
    box-shadow:
      0 8px 20px -10px rgba(32, 70, 50, 0.65),
      inset 0 1px 0 var(--warm-rim-35);
  }

  small {
    color: rgba(253, 249, 240, 0.62);
  }
}
</style>
