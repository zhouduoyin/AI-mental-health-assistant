<script setup lang="ts">
import AppIcon from '@/components/AppIcon.vue'
import { useTheme } from '@/composables/useTheme'

withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })

const { isNight, label, setMode } = useTheme()
</script>

<template>
  <div
    class="theme-toggle"
    :class="{ 'theme-toggle--compact': compact, 'is-night': isNight }"
    role="group"
    aria-label="主题模式"
  >
    <span class="theme-toggle__knob" aria-hidden="true" />

    <button
      type="button"
      class="theme-toggle__btn"
      :class="{ 'is-active': !isNight }"
      :aria-pressed="!isNight"
      title="暖光日间"
      @click="setMode('day')"
    >
      <AppIcon name="sun" :size="14" />
      <span class="theme-toggle__text">日间</span>
    </button>

    <button
      type="button"
      class="theme-toggle__btn"
      :class="{ 'is-active': isNight }"
      :aria-pressed="isNight"
      :title="label"
      @click="setMode('night')"
    >
      <AppIcon name="moon" :size="13" />
      <span class="theme-toggle__text">夜间</span>
    </button>
  </div>
</template>

<style scoped lang="scss">
.theme-toggle {
  position: relative;
  display: inline-flex;
  flex: none;
  align-items: center;
  padding: 3px;
  border: 1px solid var(--warm-line);
  border-radius: $radius-pill;
  background: var(--warm-paper-70);
  box-shadow: inset 0 1px 0 var(--warm-rim-60);
}

.theme-toggle__knob {
  position: absolute;
  top: 3px;
  bottom: 3px;
  left: 3px;
  width: calc(50% - 3px);
  border-radius: $radius-pill;
  background: linear-gradient(135deg, var(--warm-brand-strong), var(--warm-brand));
  box-shadow:
    0 6px 14px -8px var(--warm-brand-strong),
    inset 0 1px 0 var(--warm-rim-28);
  transition:
    transform 0.38s $ease-out-expo,
    background 0.3s $ease-out;
}

.theme-toggle.is-night .theme-toggle__knob {
  transform: translateX(100%);
  // 夜间滑块的强调色同样用香槟金，和改过的主色保持一致
  background: linear-gradient(135deg, var(--warm-brand), #a9803f);
  box-shadow:
    0 6px 14px -8px rgba(169, 128, 63, 0.7),
    inset 0 1px 0 var(--warm-rim-20);
}

.theme-toggle__btn {
  position: relative;
  z-index: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-width: 58px;
  height: 26px;
  padding: 0 10px;
  border: 0;
  border-radius: $radius-pill;
  background: transparent;
  color: var(--warm-text-muted);
  font: inherit;
  font-size: 11px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.24s $ease-out;

  &:hover {
    color: var(--warm-text);
  }

  &:focus-visible {
    @include focus-ring($warm-brand);
  }

  &.is-active {
    color: var(--warm-on-brand);
    font-weight: 600;
  }

  &.is-active:hover {
    color: var(--warm-on-brand);
  }
}

.theme-toggle.is-night .theme-toggle__btn.is-active {
  color: #30181d;
}

.theme-toggle--compact .theme-toggle__text {
  display: none;
}

.theme-toggle--compact .theme-toggle__btn {
  min-width: 30px;
  width: 30px;
  padding: 0;
}

@include media-down($bp-sm) {
  .theme-toggle__text {
    display: none;
  }

  .theme-toggle__btn {
    min-width: 30px;
    width: 30px;
    padding: 0;
  }
}

@media (prefers-reduced-motion: reduce) {
  .theme-toggle__knob {
    transition: none;
  }
}
</style>
